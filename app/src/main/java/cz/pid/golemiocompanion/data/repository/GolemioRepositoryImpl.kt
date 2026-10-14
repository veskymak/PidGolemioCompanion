package cz.pid.golemiocompanion.data.repository

import cz.pid.golemiocompanion.data.remote.api.GolemioApiService
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.domain.repository.GolemioRepository
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.math.*

class GolemioRepositoryImpl(
    private val apiService: GolemioApiService
) : GolemioRepository {

    private val lastKnownVehicleCache = mutableMapOf<String, LiveVehicleMarker>()

    override suspend fun getDeparturesForStop(
        stopQuery: String,
        lineFilter: String?,
        labSettings: LabSettingsState,
        currentWeather: AirQualityWeatherInfo?
    ): List<StopConnection> {
        val nowMs = System.currentTimeMillis()
        val phantomExtraSeconds = if (labSettings.phantomAiDelayPrediction) {
            calculatePhantomAiDelaySeconds(currentWeather)
        } else 0

        if (labSettings.golemioApiKey.isNotBlank()) {
            runCatching {
                val response = apiService.getDepartureBoards(
                    apiKey = labSettings.golemioApiKey,
                    stopNames = listOf(stopQuery.ifBlank { "Anděl" })
                )
                val primaryStop = response.stops.firstOrNull()
                val mapped = response.departures.map { dto ->
                    val scheduledMs = parseIsoTimestampToEpochMs(dto.departureTimestamp.scheduled, nowMs + 90_000L)
                    val officialDelaySec = dto.delay?.seconds ?: ((dto.delay?.minutes ?: 0) * 60)
                    val predictedMs = parseIsoTimestampToEpochMs(
                        dto.departureTimestamp.predicted,
                        scheduledMs + (officialDelaySec * 1000L)
                    )
                    val scheduledDestMs = scheduledMs + (18 * 60 * 1000L)
                    val predictedDestMs = predictedMs + (18 * 60 * 1000L) + (phantomExtraSeconds * 1000L)

                    StopConnection(
                        tripId = dto.trip.id.ifBlank { "PID_${dto.route.shortName}_${scheduledMs}" },
                        routeShortName = dto.route.shortName,
                        routeType = dto.route.type,
                        headsign = dto.trip.headsign,
                        stopName = primaryStop?.stopName ?: stopQuery,
                        platformCode = dto.stop.platformCode ?: primaryStop?.platformCode ?: "A",
                        stopLat = primaryStop?.stopLat ?: 50.0715,
                        stopLon = primaryStop?.stopLon ?: 14.4035,
                        scheduledDepartureEpochMs = scheduledMs,
                        predictedDepartureEpochMs = predictedMs,
                        scheduledArrivalDestinationEpochMs = scheduledDestMs,
                        predictedArrivalDestinationEpochMs = predictedDestMs,
                        destinationName = dto.lastStop?.name ?: dto.trip.headsign,
                        officialDelaySeconds = officialDelaySec,
                        phantomAiExtraDelaySeconds = phantomExtraSeconds,
                        isAirConditioned = dto.trip.isAirConditioned ?: true,
                        isWheelchairAccessible = dto.trip.isWheelchairAccessible
                    )
                }.filter { conn ->
                    lineFilter.isNullOrBlank() || conn.routeShortName.equals(lineFilter.trim(), ignoreCase = true)
                }

                if (mapped.isNotEmpty()) return mapped
            }
        }

        val cleanStop = stopQuery.ifBlank { "Anděl" }
        val baseLines = listOf(
            Triple("191", "Letiště Václava Havla", "B"),
            Triple("167", "Nemocnice Na Homolce", "A"),
            Triple("125", "Smíchovské nádraží → Skalka", "C"),
            Triple("9", "Spojovací", "1"),
            Triple("191", "Letiště Václava Havla (Následující spoj)", "B"),
            Triple("20", "Dědina", "2")
        )
        val offsetsSeconds = listOf(18L, 75L, 190L, 310L, 480L, 640L)
        val delaysSeconds = listOf(12, 45, 0, 95, 15, 0)

        return baseLines.mapIndexed { idx, (line, headsign, platform) ->
            val schedMs = nowMs + (offsetsSeconds[idx] * 1000L) - (delaysSeconds[idx] * 1000L)
            val predMs = nowMs + (offsetsSeconds[idx] * 1000L)
            StopConnection(
                tripId = "PID_${line}_${idx}",
                routeShortName = line,
                routeType = if (line.length <= 2) 0 else 3,
                headsign = headsign,
                stopName = cleanStop,
                platformCode = platform,
                stopLat = 50.0715,
                stopLon = 14.4035,
                scheduledDepartureEpochMs = schedMs,
                predictedDepartureEpochMs = predMs,
                scheduledArrivalDestinationEpochMs = schedMs + (14 * 60_000L),
                predictedArrivalDestinationEpochMs = predMs + (14 * 60_000L) + (phantomExtraSeconds * 1000L),
                destinationName = headsign.substringBefore(" ("),
                officialDelaySeconds = delaysSeconds[idx],
                phantomAiExtraDelaySeconds = phantomExtraSeconds
            )
        }.filter { conn ->
            lineFilter.isNullOrBlank() || conn.routeShortName.contains(lineFilter.trim(), ignoreCase = true)
        }
    }

    override suspend fun getAirQualityAndWeather(
        lat: Double,
        lon: Double,
        apiKey: String
    ): AirQualityWeatherInfo {
        if (apiKey.isNotBlank()) {
            runCatching {
                val dto = apiService.getAirQualityStations(
                    apiKey = apiKey,
                    latLng = "$lat,$lon"
                )
                val first = dto.features.firstOrNull()?.properties
                if (first != null) {
                    val comps = first.measurement?.components ?: emptyList()
                    val temp = comps.find { it.type.equals("temp", true) }?.value ?: 16.4
                    val pm10 = comps.find { it.type.contains("PM10", true) }?.value ?: 18.2
                    val no2 = comps.find { it.type.contains("NO2", true) }?.value ?: 21.5
                    return AirQualityWeatherInfo(
                        stationName = first.name.ifBlank { "Praha - Golemio Senzor" },
                        temperatureCelsius = temp,
                        aqiIndex = first.measurement?.aqHourlyIndex ?: 2,
                        pm10UgM3 = pm10,
                        no2UgM3 = no2
                    )
                }
            }
        }
        return AirQualityWeatherInfo(
            stationName = "Stanice Praha 5 - Smíchov",
            temperatureCelsius = 15.8,
            aqiIndex = 2,
            pm10UgM3 = 19.4,
            no2UgM3 = 24.1
        )
    }

    override suspend fun getNearbySharedBikes(
        lat: Double,
        lon: Double,
        apiKey: String
    ): List<SharedBikeStation> {
        if (apiKey.isNotBlank()) {
            runCatching {
                val response = apiService.getSharedBikes(
                    apiKey = apiKey,
                    latLng = "$lat,$lon",
                    rangeMeters = 650
                )
                val stations = response.features.mapNotNull { feature ->
                    val coords = feature.geometry.coordinates
                    if (coords.size < 2) return@mapNotNull null
                    val bikeLon = coords[0]
                    val bikeLat = coords[1]
                    val dist = haversineMeters(lat, lon, bikeLat, bikeLon)
                    SharedBikeStation(
                        id = feature.properties.id,
                        name = feature.properties.name,
                        provider = feature.properties.companyName,
                        freeBikes = feature.properties.numBikesAvailable,
                        distanceMeters = dist,
                        walkingMinutes = max(1, (dist / 75.0).roundToInt()),
                        lat = bikeLat,
                        lon = bikeLon
                    )
                }.filter { it.freeBikes > 0 }.sortedBy { it.distanceMeters }

                if (stations.isNotEmpty()) return stations
            }
        }

        return listOf(
            SharedBikeStation(
                id = "nb_andel_1",
                name = "Anděl - Zlatý Anděl (Sever)",
                provider = "Nextbike",
                freeBikes = 7,
                distanceMeters = 65,
                walkingMinutes = 1,
                lat = lat + 0.0005,
                lon = lon + 0.0004
            ),
            SharedBikeStation(
                id = "rekola_andel_2",
                name = "Štefánikova u metra B",
                provider = "Rekola",
                freeBikes = 4,
                distanceMeters = 140,
                walkingMinutes = 2,
                lat = lat + 0.0011,
                lon = lon - 0.0006
            ),
            SharedBikeStation(
                id = "nb_nadr_3",
                name = "Nádražní - Pěší zóna",
                provider = "Nextbike",
                freeBikes = 5,
                distanceMeters = 260,
                walkingMinutes = 3,
                lat = lat - 0.0014,
                lon = lon + 0.0008
            )
        )
    }

    override suspend fun getEmergencyTransfersWithin600m(
        originLat: Double,
        originLon: Double,
        targetDestination: String,
        excludedLine: String,
        apiKey: String
    ): List<EmergencyTransferOption> {
        return listOf(
            EmergencyTransferOption(
                nearbyStopName = "Na Knížecí",
                platformCode = "Nást. 4",
                distanceMeters = 320,
                routeShortName = "137",
                headsign = "$targetDestination (přes Jinonice)",
                departureInMinutes = 3,
                delaySeconds = 0
            ),
            EmergencyTransferOption(
                nearbyStopName = "Zborovská",
                platformCode = "B",
                distanceMeters = 440,
                routeShortName = "176",
                headsign = "$targetDestination (Strahov)",
                departureInMinutes = 5,
                delaySeconds = 25
            ),
            EmergencyTransferOption(
                nearbyStopName = "Arbesovo náměstí",
                platformCode = "A",
                distanceMeters = 570,
                routeShortName = "9",
                headsign = "Přestup směr $targetDestination",
                departureInMinutes = 4,
                delaySeconds = 10
            )
        ).filter { it.routeShortName != excludedLine }
    }

    override suspend fun getLiveVehicles(
        lineFilter: String?,
        labSettings: LabSettingsState
    ): List<LiveVehicleMarker> {
        val nowMs = System.currentTimeMillis()

        if (labSettings.rawInputGatewayStream && labSettings.golemioApiKey.isNotBlank()) {
            runCatching {
                val rawList = apiService.getRawTelemetryStream(
                    apiKey = labSettings.golemioApiKey,
                    routeShortName = lineFilter
                )
                if (rawList.isNotEmpty()) {
                    return rawList.map { dto ->
                        val marker = LiveVehicleMarker(
                            tripId = dto.tripId,
                            routeShortName = dto.routeShortName,
                            headsign = "Input Gateway Raw Stream",
                            lat = dto.lat,
                            lon = dto.lng,
                            bearing = dto.bearing,
                            delaySeconds = dto.delaySeconds,
                            speedKmh = dto.speedKmh,
                            lastUpdatedEpochMs = nowMs,
                            isDeadReckoningEstimated = false,
                            isFromRawInputGateway = true
                        )
                        lastKnownVehicleCache[marker.tripId] = marker
                        marker
                    }
                }
            }
        }

        if (labSettings.golemioApiKey.isNotBlank()) {
            runCatching {
                val geoJson = apiService.getVehiclePositions(
                    apiKey = labSettings.golemioApiKey,
                    routeShortName = lineFilter?.takeIf { it.isNotBlank() }
                )
                val liveMarkers = geoJson.features.mapNotNull { feature ->
                    val coords = feature.geometry.coordinates
                    if (coords.size < 2) return@mapNotNull null
                    val lon = coords[0]
                    val lat = coords[1]
                    val gtfs = feature.properties.trip.gtfs
                    val lastPos = feature.properties.lastPosition
                    val originMs = parseIsoTimestampToEpochMs(lastPos.originTimestamp, nowMs)
                    val ageSec = ((nowMs - originMs) / 1000L).coerceAtLeast(0L)

                    val baseMarker = LiveVehicleMarker(
                        tripId = gtfs.tripId,
                        routeShortName = gtfs.routeShortName,
                        headsign = gtfs.tripHeadsign,
                        lat = lat,
                        lon = lon,
                        bearing = lastPos.bearing ?: 0f,
                        delaySeconds = lastPos.delay?.actual ?: 0,
                        speedKmh = lastPos.speed ?: 34.0,
                        lastUpdatedEpochMs = originMs
                    )

                    val finalMarker = if (labSettings.deadReckoningInTunnels && ageSec > 25) {
                        extrapolateDeadReckoningPosition(baseMarker, ageSec)
                    } else {
                        baseMarker
                    }
                    lastKnownVehicleCache[finalMarker.tripId] = finalMarker
                    finalMarker
                }

                if (liveMarkers.isNotEmpty()) return liveMarkers
            }
        }

        val tSec = (nowMs / 1000.0) % 360.0
        val simulatedVehicles = listOf(
            LiveVehicleMarker(
                tripId = "191_BUS_A",
                routeShortName = "191",
                headsign = "Letiště Václava Havla",
                lat = 50.0735 + sin(tSec * 0.03) * 0.006,
                lon = 14.3980 + cos(tSec * 0.03) * 0.008,
                bearing = ((tSec * 12) % 360).toFloat(),
                delaySeconds = 25,
                speedKmh = 38.0,
                lastUpdatedEpochMs = nowMs - 22_000L,
                isFromRawInputGateway = labSettings.rawInputGatewayStream
            ),
            LiveVehicleMarker(
                tripId = "167_BUS_B",
                routeShortName = "167",
                headsign = "Na Knížecí",
                lat = 50.0705 + cos(tSec * 0.04) * 0.005,
                lon = 14.4040 + sin(tSec * 0.04) * 0.007,
                bearing = ((tSec * 18 + 90) % 360).toFloat(),
                delaySeconds = 140,
                speedKmh = 29.0,
                lastUpdatedEpochMs = nowMs,
                isFromRawInputGateway = labSettings.rawInputGatewayStream
            ),
            LiveVehicleMarker(
                tripId = "125_BUS_C",
                routeShortName = "125",
                headsign = "Skalka",
                lat = 50.0620 + sin(tSec * 0.025) * 0.007,
                lon = 14.4150 + cos(tSec * 0.025) * 0.009,
                bearing = ((tSec * 9 + 180) % 360).toFloat(),
                delaySeconds = 0,
                speedKmh = 46.0,
                lastUpdatedEpochMs = nowMs - 28_000L,
                isFromRawInputGateway = labSettings.rawInputGatewayStream
            ),
            LiveVehicleMarker(
                tripId = "119_BUS_D",
                routeShortName = "119",
                headsign = "Nádraží Veleslavín",
                lat = 50.0955 + cos(tSec * 0.035) * 0.004,
                lon = 14.3450 + sin(tSec * 0.035) * 0.009,
                bearing = 115f,
                delaySeconds = 75,
                speedKmh = 52.0,
                lastUpdatedEpochMs = nowMs,
                isFromRawInputGateway = labSettings.rawInputGatewayStream
            )
        )

        return simulatedVehicles
            .filter { v ->
                lineFilter.isNullOrBlank() || v.routeShortName.contains(lineFilter.trim(), ignoreCase = true)
            }
            .map { marker ->
                val ageSec = (nowMs - marker.lastUpdatedEpochMs) / 1000L
                if (labSettings.deadReckoningInTunnels && ageSec >= 20L) {
                    extrapolateDeadReckoningPosition(marker, ageSec)
                } else {
                    marker
                }
            }
    }

    override suspend fun getParkingsPR(apiKey: String): List<ParkingLotPR> {
        if (apiKey.isNotBlank()) {
            runCatching {
                val dto = apiService.getParkingsPR(apiKey = apiKey)
                val list = dto.features.mapNotNull { f ->
                    val coords = f.geometry.coordinates
                    if (coords.size < 2) return@mapNotNull null
                    ParkingLotPR(
                        id = f.properties.id,
                        name = f.properties.name,
                        freePlaces = f.properties.numOfFreePlaces,
                        totalPlaces = f.properties.totalNumOfPlaces,
                        lat = coords[1],
                        lon = coords[0]
                    )
                }
                if (list.isNotEmpty()) return list
            }
        }
        return listOf(
            ParkingLotPR("pr_zlicin", "P+R Zličín I", freePlaces = 42, totalPlaces = 310, lat = 50.0531, lon = 14.2912),
            ParkingLotPR("pr_chodov", "P+R Chodov", freePlaces = 118, totalPlaces = 655, lat = 50.0315, lon = 14.4910),
            ParkingLotPR("pr_letnany", "P+R Letňany", freePlaces = 290, totalPlaces = 630, lat = 50.1260, lon = 14.5150),
            ParkingLotPR("pr_radotin", "P+R Radotín", freePlaces = 9, totalPlaces = 152, lat = 49.9835, lon = 14.3610)
        )
    }

    private fun extrapolateDeadReckoningPosition(
        vehicle: LiveVehicleMarker,
        elapsedSecondsSinceLastGps: Long
    ): LiveVehicleMarker {
        val earthRadiusMeters = 6_371_000.0
        val speedMetersPerSecond = (vehicle.speedKmh / 3.6).coerceIn(5.0, 22.0)
        val distanceMeters = speedMetersPerSecond * elapsedSecondsSinceLastGps.coerceAtMost(120L)

        val bearingRad = Math.toRadians(vehicle.bearing.toDouble())
        val lat1 = Math.toRadians(vehicle.lat)
        val lon1 = Math.toRadians(vehicle.lon)

        val angularDistance = distanceMeters / earthRadiusMeters

        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
                cos(lat1) * sin(angularDistance) * cos(bearingRad)
        )
        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )

        return vehicle.copy(
            lat = Math.toDegrees(lat2),
            lon = Math.toDegrees(lon2),
            isDeadReckoningEstimated = true
        )
    }

    private fun calculatePhantomAiDelaySeconds(weather: AirQualityWeatherInfo?): Int {
        val currentHour = LocalTime.now().hour
        val isRushHour = currentHour in 7..9 || currentHour in 16..18
        var heuristicPenaltySec = if (isRushHour) 35 else 10

        if (weather != null) {
            if (weather.temperatureCelsius < 2.0) heuristicPenaltySec += 25
            if (weather.aqiIndex >= 4) heuristicPenaltySec += 20
        }
        return heuristicPenaltySec
    }

    private fun parseIsoTimestampToEpochMs(iso: String?, fallbackMs: Long): Long {
        if (iso.isNullOrBlank()) return fallbackMs
        return runCatching {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(fallbackMs)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).roundToInt()
    }
}