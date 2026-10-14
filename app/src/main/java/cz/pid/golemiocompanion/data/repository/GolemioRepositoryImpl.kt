package cz.pid.golemiocompanion.data.repository

import cz.pid.golemiocompanion.data.remote.api.GolemioApiService
import cz.pid.golemiocompanion.data.remote.dto.*
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.domain.repository.GolemioRepository
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/** Thrown when live Golemio data was requested (API key set) but could not be loaded. */
class GolemioApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GolemioRepositoryImpl(
    private val apiService: GolemioApiService
) : GolemioRepository {

    private val lastKnownVehicleCache = mutableMapOf<String, LiveVehicleMarker>()
    private val departuresCache = ConcurrentHashMap<String, Pair<Long, List<StopConnection>>>()
    private val stopNamesByIdCache = ConcurrentHashMap<String, String>()

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
            val cacheKey = "${stopQuery.trim().lowercase()}|${lineFilter?.trim()?.lowercase() ?: ""}"
            val cached = departuresCache[cacheKey]
            if (cached != null && (nowMs - cached.first) < 20_000L) {
                return cached.second
            }

            val candidateStops = resolveStopQueries(stopQuery.trim().ifBlank { "Anděl" })
            var lastError: Throwable? = null
            var fallbackDeparturesWithoutFilter: List<StopConnection>? = null

            for (candidate in candidateStops) {
                try {
                    val res = apiService.getDepartureBoards(
                        apiKey = labSettings.golemioApiKey,
                        stopName = candidate
                    )

                    if (res.departures.isNotEmpty()) {
                        res.stops.forEach { stopNamesByIdCache[it.stopId] = it.stopName }
                        val stopsById = res.stops.associateBy { it.stopId }
                        val primaryStop = res.stops.firstOrNull()

                        val mapped = res.departures.map { dto ->
                            val depStop = stopsById[dto.stop.id] ?: primaryStop
                            val scheduledMs = parseIsoTimestampToEpochMs(dto.departureTimestamp.scheduled, nowMs + 90_000L)
                            val officialDelaySec = dto.delay?.seconds ?: ((dto.delay?.minutes ?: 0) * 60)
                            
                            val minutesNum = dto.departureTimestamp.minutes?.filter { it.isDigit() }?.toLongOrNull()
                            val predictedMs = if (minutesNum != null) {
                                nowMs + (minutesNum * 60_000L)
                            } else {
                                parseIsoTimestampToEpochMs(
                                    dto.departureTimestamp.predicted,
                                    scheduledMs + (officialDelaySec * 1000L)
                                )
                            }
                            val scheduledDestMs = scheduledMs + (18 * 60 * 1000L)
                            val predictedDestMs = predictedMs + (18 * 60 * 1000L) + (phantomExtraSeconds * 1000L)

                            StopConnection(
                                tripId = dto.trip.id.ifBlank { "PID_${dto.route.shortName}_${scheduledMs}" },
                                routeShortName = dto.route.shortName,
                                routeType = dto.route.type,
                                headsign = dto.trip.headsign,
                                stopName = depStop?.stopName?.ifBlank { null } ?: candidate,
                                platformCode = dto.stop.platformCode ?: depStop?.platformCode ?: "-",
                                stopLat = depStop?.stopLat ?: 50.0715,
                                stopLon = depStop?.stopLon ?: 14.4035,
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
                        }.distinctBy { it.tripId }

                        val filtered = mapped.filter { conn ->
                            lineFilter.isNullOrBlank() || conn.routeShortName.equals(lineFilter.trim(), ignoreCase = true)
                        }

                        if (filtered.isNotEmpty()) {
                            departuresCache[cacheKey] = Pair(nowMs, filtered)
                            return filtered
                        } else if (fallbackDeparturesWithoutFilter == null && mapped.isNotEmpty()) {
                            fallbackDeparturesWithoutFilter = mapped
                        }
                    }
                } catch (e: HttpException) {
                    lastError = e
                    if (e.code() == 429) {
                        // Rate limit exceeded: return cached data if available
                        if (cached != null) return cached.second
                        throw GolemioApiException("Překročen limit požadavků Golemio API, zkuste to za chvíli.", e)
                    } else if (e.code() != 404) {
                        throw GolemioApiException(
                            when (e.code()) {
                                401, 403 -> "Neplatný API klíč Golemio (HTTP ${e.code()})."
                                else -> "Chyba Golemio API (HTTP ${e.code()})."
                            },
                            e
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    if (cached != null) return cached.second
                    throw GolemioApiException("Nelze se připojit ke Golemio API: ${e.message ?: "chyba sítě"}", e)
                } catch (e: Exception) {
                    lastError = e
                }
            }

            // If a candidate stop was found and had departures, but the line filter matched none of them
            if (fallbackDeparturesWithoutFilter != null && !lineFilter.isNullOrBlank()) {
                return emptyList()
            }

            // In API mode, if Golemio returned 0 departures for all candidate stops,
            // return empty list so the app displays the actual state instead of fabricating fake buses.
            return emptyList()
        }

        // Žádná falešná / demo data! Pokud uživatel nezadal API klíč, vracíme prázdný seznam,
        // aby ho aplikace informovala o nutnosti zadat bezplatný klíč v Laboratoři.
        return emptyList()
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
                    routeShortName = lineFilter?.takeIf { it.isNotBlank() },
                    includeNotTracking = true
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

                    // Pokud vozidlo nevysílá telemetrii nebo má tracking false, tracker je vypnutý
                    val isTracked = lastPos.tracking != false && lastPos.delay?.actual != null

                    val nextStop = lastPos.nextStop
                    val lastStop = lastPos.lastStop
                    val nextStopArrivalEpochMs = nextStop?.arrivalTime?.let { parseIsoTimestampToEpochMs(it, 0L) }
                    val nextStopName = nextStop?.id?.let { id ->
                        stopNamesByIdCache[id] ?: PidStopDirectory.findStopNameById(id) ?: id
                    }
                    val lastStopName = lastStop?.id?.let { id ->
                        stopNamesByIdCache[id] ?: PidStopDirectory.findStopNameById(id) ?: id
                    }

                    val baseMarker = LiveVehicleMarker(
                        tripId = gtfs.tripId,
                        routeShortName = gtfs.routeShortName,
                        headsign = gtfs.tripHeadsign,
                        lat = lat,
                        lon = lon,
                        bearing = lastPos.bearing ?: 0f,
                        delaySeconds = if (isTracked) (lastPos.delay?.actual ?: 0) else 0,
                        speedKmh = lastPos.speed ?: 0.0,
                        lastUpdatedEpochMs = originMs,
                        isTracking = isTracked,
                        nextStopId = nextStop?.id,
                        nextStopName = nextStopName,
                        nextStopSequence = nextStop?.sequence,
                        nextStopArrivalEpochMs = nextStopArrivalEpochMs,
                        lastStopName = lastStopName,
                        vehicleRegistrationNumber = feature.properties.trip.vehicleRegistrationNumber,
                        isAirConditioned = feature.properties.trip.airConditioned,
                        isWheelchairAccessible = feature.properties.trip.wheelchairAccessible
                    )

                    val finalMarker = if (isTracked && labSettings.deadReckoningInTunnels && ageSec > 25) {
                        extrapolateDeadReckoningPosition(baseMarker, ageSec)
                    } else {
                        baseMarker
                    }
                    lastKnownVehicleCache[finalMarker.tripId] = finalMarker
                    finalMarker
                }

                return liveMarkers
            }
        }

        // Žádná falešná / demo vozidla! Pokud uživatel nezadal API klíč, vracíme prázdný seznam.
        return emptyList()
    }

    override suspend fun getVehicleUpcomingStops(
        vehicle: LiveVehicleMarker,
        apiKey: String
    ): List<VehicleUpcomingStop> {
        val nowMs = System.currentTimeMillis()
        if (!vehicle.isTracking) return emptyList()

        if (apiKey.isNotBlank() && vehicle.tripId.isNotBlank()) {
            runCatching {
                val tripDetails = apiService.getTripDetails(apiKey = apiKey, tripId = vehicle.tripId)
                if (tripDetails.stopTimes.isNotEmpty()) {
                    tripDetails.stops.forEach { stopNamesByIdCache[it.stopId] = it.stopName }
                    val stopsById = tripDetails.stops.associateBy { it.stopId }

                    val sortedTimes = tripDetails.stopTimes.sortedBy { it.stopSequence }
                    val upcomingTimes = if (vehicle.nextStopSequence != null) {
                        sortedTimes.filter { it.stopSequence >= vehicle.nextStopSequence }
                    } else {
                        sortedTimes
                    }

                    val result = upcomingTimes.map { st ->
                        val stopName = stopsById[st.stopId]?.stopName
                            ?: stopNamesByIdCache[st.stopId]
                            ?: PidStopDirectory.findStopNameById(st.stopId)
                            ?: "Zastávka ${st.stopSequence}"
                        val schedMs = parseGtfsTimeToTodayEpochMs(st.arrivalTime.ifBlank { st.departureTime })
                        val predMs = schedMs + (vehicle.delaySeconds * 1000L)
                        val minutes = maxOf(0L, (predMs - nowMs) / 60_000L)
                        val schedStr = formatEpochMsToHHmm(schedMs)
                        val predStr = formatEpochMsToHHmm(predMs)

                        VehicleUpcomingStop(
                            stopId = st.stopId,
                            stopName = stopName,
                            sequence = st.stopSequence,
                            minutesUntilArrival = minutes,
                            scheduledTime = schedStr,
                            predictedTime = predStr,
                            isDestination = (st == sortedTimes.lastOrNull())
                        )
                    }
                    if (result.isNotEmpty()) return result
                }
            }
        }

        // Fallback: Pokud static GTFS trip data nejsou dostupná, zobrazíme nextStop a cílovou stanici
        val fallbackList = mutableListOf<VehicleUpcomingStop>()
        if (vehicle.nextStopName != null && vehicle.nextStopArrivalEpochMs != null && vehicle.nextStopArrivalEpochMs > 0L) {
            val predMs = vehicle.nextStopArrivalEpochMs + (vehicle.delaySeconds * 1000L)
            val minutes = maxOf(0L, (predMs - nowMs) / 60_000L)
            fallbackList.add(
                VehicleUpcomingStop(
                    stopId = vehicle.nextStopId ?: "next_1",
                    stopName = vehicle.nextStopName,
                    sequence = vehicle.nextStopSequence ?: 1,
                    minutesUntilArrival = minutes,
                    scheduledTime = formatEpochMsToHHmm(vehicle.nextStopArrivalEpochMs),
                    predictedTime = formatEpochMsToHHmm(predMs),
                    isDestination = false
                )
            )
        }

        if (vehicle.headsign.isNotBlank()) {
            val nextMs = vehicle.nextStopArrivalEpochMs ?: nowMs
            val destMs = nextMs + (18 * 60_000L) + (vehicle.delaySeconds * 1000L)
            val minutes = maxOf(fallbackList.firstOrNull()?.minutesUntilArrival ?: 0L, (destMs - nowMs) / 60_000L)
            fallbackList.add(
                VehicleUpcomingStop(
                    stopId = "dest_final",
                    stopName = vehicle.headsign,
                    sequence = 999,
                    minutesUntilArrival = minutes,
                    scheduledTime = formatEpochMsToHHmm(destMs - (vehicle.delaySeconds * 1000L)),
                    predictedTime = formatEpochMsToHHmm(destMs),
                    isDestination = true
                )
            )
        }

        return fallbackList
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
        }.recoverCatching {
            Instant.parse(iso).toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(iso).atZone(ZoneId.of("Europe/Prague")).toInstant().toEpochMilli()
        }.getOrDefault(fallbackMs)
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

    private fun resolveStopQueries(input: String): List<String> {
        val clean = input.trim()
        val normalized = java.text.Normalizer.normalize(clean, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()

        // 1. Direct exact shortcuts: if user entered a specific known stop, return candidates to match Golemio feeds
        if (clean.equals("Mladá Boleslav město", ignoreCase = true) ||
            clean.equals("Mladá Boleslav, město", ignoreCase = true) ||
            clean.equals("MB město", ignoreCase = true) ||
            clean.equals("Mladá Boleslav mesto", ignoreCase = true)) {
            return listOf("Mladá Boleslav město", "Mladá Boleslav, hl.nádr.", "Mladá Boleslav")
        }
        if (clean.equals("Mladá Boleslav, aut. st.", ignoreCase = true) ||
            clean.equals("Mladá Boleslav, aut.st.", ignoreCase = true) ||
            clean.equals("Mladá Boleslav,aut.st.", ignoreCase = true) ||
            clean.equals("MB aut. st.", ignoreCase = true) ||
            clean.equals("MB aut.st.", ignoreCase = true)) {
            return listOf("Mladá Boleslav,aut.st.", "Mladá Boleslav, aut. st.", "Mladá Boleslav")
        }
        if (clean.equals("Mladá Boleslav, hl.nádr.", ignoreCase = true) ||
            clean.equals("Mladá Boleslav,hl.nádr.", ignoreCase = true) ||
            clean.equals("Mladá Boleslav, žel.st.", ignoreCase = true) ||
            clean.equals("MB hl.nádr.", ignoreCase = true)) {
            return listOf("Mladá Boleslav,hl.nádr.", "Mladá Boleslav, hl.nádr.", "Mladá Boleslav")
        }
        if (clean.equals("Mladá Boleslav, nám. Republiky", ignoreCase = true) ||
            clean.equals("Mladá Boleslav,nám.Republiky", ignoreCase = true)) {
            return listOf("Mladá Boleslav,nám.Republiky", "Mladá Boleslav, nám. Republiky")
        }
        if (clean.equals("Praha hl.n.", ignoreCase = true) || clean.equals("Hlavní nádraží", ignoreCase = true)) {
            return listOf("Praha hl.n.", "Hlavní nádraží")
        }

        val list = mutableListOf<String>()
        list.add(clean)

        // 2. Specific normalizations for Mladá Boleslav (at most 2 candidates to prevent 429)
        if (normalized.contains("boleslav") || normalized.contains("mlad") || normalized == "mb") {
            if (normalized.contains("mesto")) {
                list.add("Mladá Boleslav město")
            } else if (normalized.contains("zel") || normalized.contains("vlak") || normalized.contains("hl") || normalized.contains("nadr")) {
                list.add("Mladá Boleslav,hl.nádr.")
                list.add("Mladá Boleslav, hl.nádr.")
            } else if (normalized.contains("aut") || normalized.contains("bus") || normalized.contains("st")) {
                list.add("Mladá Boleslav,aut.st.")
                list.add("Mladá Boleslav, aut. st.")
            } else {
                list.add("Mladá Boleslav,aut.st.")
                list.add("Mladá Boleslav město")
            }
        } else if (clean.contains("aut. st", ignoreCase = true)) {
            list.add(clean.replace("aut. st.", "aut.st.", ignoreCase = true))
        } else if (clean.contains("žel.st", ignoreCase = true) || clean.contains("zel.st", ignoreCase = true)) {
            list.add(clean.replace("žel.st.", "hl.nádr.", ignoreCase = true))
        }

        return list.distinct().take(2)
    }

    private fun parseGtfsTimeToTodayEpochMs(timeStr: String): Long {
        val nowMs = System.currentTimeMillis()
        if (timeStr.isBlank()) return nowMs
        if (timeStr.contains("T") || timeStr.endsWith("Z")) {
            return parseIsoTimestampToEpochMs(timeStr, nowMs)
        }
        return runCatching {
            val parts = timeStr.trim().split(":")
            val rawH = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val s = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val dayOffset = rawH / 24
            val h = rawH % 24
            val pragueZone = ZoneId.of("Europe/Prague")
            val nowZdt = ZonedDateTime.now(pragueZone)
            val zdt = nowZdt.plusDays(dayOffset.toLong())
                .withHour(h)
                .withMinute(m)
                .withSecond(s)
                .withNano(0)
            zdt.toInstant().toEpochMilli()
        }.getOrDefault(nowMs)
    }

    private fun formatEpochMsToHHmm(epochMs: Long): String {
        val zdt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("Europe/Prague"))
        return String.format("%02d:%02d", zdt.hour, zdt.minute)
    }
}