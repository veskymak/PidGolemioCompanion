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

        // Demo / offline data – realistic GTFS timetable schedule based on Prague time
        val cleanStop = stopQuery.ifBlank { "Anděl" }
        val isMb = cleanStop.contains("boleslav", ignoreCase = true) || cleanStop.contains("mlad", ignoreCase = true)
        val isTrain = cleanStop.contains("město", ignoreCase = true) || cleanStop.contains("mesto", ignoreCase = true) ||
            cleanStop.contains("hl.nádr", ignoreCase = true) || cleanStop.contains("hl.n", ignoreCase = true)

        val pragueZone = ZoneId.of("Europe/Prague")
        val nowZdt = ZonedDateTime.now(pragueZone)
        val nowHour = nowZdt.hour
        val nowMinute = nowZdt.minute

        data class TimetableItem(
            val line: String,
            val headsign: String,
            val platform: String,
            val hour: Int,
            val minute: Int,
            val routeType: Int = 3
        )

        val timetableItems: List<TimetableItem> = when {
            isMb && isTrain -> {
                listOf(
                    TimetableItem("S30", "Praha hl.n. (přes Všetaty)", "1", (nowHour + 1) % 24, 12, routeType = 2),
                    TimetableItem("R43", "Praha Masarykovo n.", "2", (nowHour + 1) % 24, 42, routeType = 2),
                    TimetableItem("S31", "Nymburk hl.n.", "1", (nowHour + 2) % 24, 8, routeType = 2),
                    TimetableItem("S30", "Turnov", "2", (nowHour + 2) % 24, 35, routeType = 2)
                )
            }
            isMb && !isTrain -> {
                if (nowHour >= 21 || nowHour < 5) {
                    // Reálný noční jízdní řád Mladá Boleslav, aut.st. z PID Lítačka:
                    // V noci jede pouze 315 ve 23:25 a další spoje až brzy ráno (od 1:10)
                    listOf(
                        TimetableItem("315", "Mnichovo Hradiště,dopravní terminál", "7", 23, 25),
                        TimetableItem("345", "Liberec,aut.nádr.", "27", 1, 10),
                        TimetableItem("315", "Černý Most", "8", 4, 10),
                        TimetableItem("315", "Mnichovo Hradiště,dopravní terminál", "7", 4, 30),
                        TimetableItem("345", "Černý Most", "8", 4, 35),
                        TimetableItem("315", "Černý Most", "8", 4, 40),
                        TimetableItem("315", "Mnichovo Hradiště,dopravní terminál", "7", 4, 55),
                        TimetableItem("712", "Katusice", "2", 5, 5),
                        TimetableItem("412", "Černý Most", "8", 5, 10),
                        TimetableItem("763", "Mladá Boleslav,Zalužany ŠKODA", "3", 5, 14)
                    )
                } else {
                    // Denní jízdní řád Mladá Boleslav, aut.st. (pravidelné denní spoje)
                    listOf(
                        TimetableItem("412", "Praha, Černý Most (Expres)", "8", nowHour, (nowMinute + 12) % 60),
                        TimetableItem("315", "Mnichovo Hradiště,dopravní terminál", "7", nowHour, (nowMinute + 25) % 60),
                        TimetableItem("345", "Liberec,aut.nádr.", "27", nowHour, (nowMinute + 38) % 60),
                        TimetableItem("467", "Mělník, žel.st.", "3", (nowHour + 1) % 24, (nowMinute + 5) % 60),
                        TimetableItem("715", "Bělá pod Bezdězem, nám.", "4", (nowHour + 1) % 24, (nowMinute + 20) % 60),
                        TimetableItem("720", "Mnichovo Hradiště, nám.", "2", (nowHour + 1) % 24, (nowMinute + 35) % 60),
                        TimetableItem("722", "Dolní Bousov, nám.", "5", (nowHour + 1) % 24, (nowMinute + 50) % 60)
                    )
                }
            }
            else -> {
                // Praha (Anděl a ostatní stanice)
                if (nowHour >= 24 || nowHour < 5) {
                    listOf(
                        TimetableItem("98", "Spojovací", "1", nowHour, (nowMinute + 14) % 60, routeType = 0),
                        TimetableItem("99", "Nádraží Hostivař", "2", nowHour, (nowMinute + 29) % 60, routeType = 0),
                        TimetableItem("904", "Sídliště Stodůlky", "A", (nowHour + 1) % 24, (nowMinute + 10) % 60),
                        TimetableItem("907", "Přední Kopanina", "B", (nowHour + 1) % 24, (nowMinute + 40) % 60)
                    )
                } else {
                    listOf(
                        TimetableItem("9", "Spojovací", "1", nowHour, (nowMinute + 4) % 60, routeType = 0),
                        TimetableItem("20", "Dědina", "2", nowHour, (nowMinute + 9) % 60, routeType = 0),
                        TimetableItem("191", "Letiště Václava Havla", "B", nowHour, (nowMinute + 14) % 60),
                        TimetableItem("167", "Nemocnice Na Homolce", "A", nowHour, (nowMinute + 21) % 60),
                        TimetableItem("125", "Smíchovské nádraží → Skalka", "C", nowHour, (nowMinute + 31) % 60)
                    )
                }
            }
        }

        val stopLat = if (isMb) 50.4114 else 50.0715
        val stopLon = if (isMb) 14.9032 else 14.4035
        val displayStopName = if (isMb && !cleanStop.contains(",")) "Mladá Boleslav,aut.st." else cleanStop

        val generatedConnections = timetableItems.mapNotNull { item ->
            val depZdt = if (item.hour < nowHour || (item.hour == nowHour && item.minute < nowMinute)) {
                // Spoj jede až následující den (např. 01:10 ráno, když je 22:52)
                nowZdt.plusDays(1).withHour(item.hour).withMinute(item.minute).withSecond(0).withNano(0)
            } else {
                // Spoj jede ještě dnes
                nowZdt.withHour(item.hour).withMinute(item.minute).withSecond(0).withNano(0)
            }
            val schedMs = depZdt.toInstant().toEpochMilli()
            val diffMs = schedMs - nowMs

            // Vyfiltrovat spoje, které by byly mimo časové okno (v minulosti nebo za více než 14 hodin)
            if (diffMs < -60_000L || diffMs > 14 * 3600 * 1000L) return@mapNotNull null

            StopConnection(
                tripId = "PID_TT_${item.line}_${item.hour}_${item.minute}",
                routeShortName = item.line,
                routeType = item.routeType,
                headsign = item.headsign,
                stopName = displayStopName,
                platformCode = item.platform,
                stopLat = stopLat,
                stopLon = stopLon,
                scheduledDepartureEpochMs = schedMs,
                predictedDepartureEpochMs = schedMs,
                scheduledArrivalDestinationEpochMs = schedMs + (22 * 60_000L),
                predictedArrivalDestinationEpochMs = schedMs + (22 * 60_000L) + (phantomExtraSeconds * 1000L),
                destinationName = item.headsign.substringBefore(" ("),
                officialDelaySeconds = 0,
                phantomAiExtraDelaySeconds = phantomExtraSeconds,
                isAirConditioned = true,
                isWheelchairAccessible = true
            )
        }.sortedBy { it.scheduledDepartureEpochMs }
        .filter { conn ->
            lineFilter.isNullOrBlank() || conn.routeShortName.equals(lineFilter.trim(), ignoreCase = true)
        }

        return generatedConnections
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
            ),
            LiveVehicleMarker(
                tripId = "412_BUS_MB1",
                routeShortName = "412",
                headsign = "Praha, Černý Most",
                lat = 50.4114 + sin(tSec * 0.03) * 0.005,
                lon = 14.9032 + cos(tSec * 0.03) * 0.007,
                bearing = 210f,
                delaySeconds = 15,
                speedKmh = 54.0,
                lastUpdatedEpochMs = nowMs - 12_000L,
                isFromRawInputGateway = labSettings.rawInputGatewayStream
            ),
            LiveVehicleMarker(
                tripId = "715_BUS_MB2",
                routeShortName = "715",
                headsign = "Bělá pod Bezdězem, nám.",
                lat = 50.4220 + cos(tSec * 0.02) * 0.004,
                lon = 14.9150 + sin(tSec * 0.02) * 0.006,
                bearing = 340f,
                delaySeconds = 40,
                speedKmh = 42.0,
                lastUpdatedEpochMs = nowMs,
                isFromRawInputGateway = labSettings.rawInputGatewayStream
            ),
            LiveVehicleMarker(
                tripId = "467_BUS_MB3",
                routeShortName = "467",
                headsign = "Mělník, žel.st.",
                lat = 50.4060 + sin(tSec * 0.025) * 0.004,
                lon = 14.8960 + cos(tSec * 0.025) * 0.005,
                bearing = 250f,
                delaySeconds = 0,
                speedKmh = 48.0,
                lastUpdatedEpochMs = nowMs - 18_000L,
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
}