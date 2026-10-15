package cz.pid.golemiocompanion.data.repository

import cz.pid.golemiocompanion.BuildConfig
import cz.pid.golemiocompanion.data.remote.api.GolemioApiService
import cz.pid.golemiocompanion.domain.model.DepartureInfo
import cz.pid.golemiocompanion.domain.model.LiveVehiclePosition
import cz.pid.golemiocompanion.domain.model.ParkingLotInfo
import cz.pid.golemiocompanion.domain.model.PidStop
import cz.pid.golemiocompanion.domain.model.SharedBikeInfo
import cz.pid.golemiocompanion.domain.model.VehicleType
import cz.pid.golemiocompanion.domain.repository.GolemioRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class GolemioRepositoryImpl(
    private val apiKeyProvider: (() -> String)? = null,
    private val customApiKey: String? = null
) : GolemioRepository {

    private val pragueZone = ZoneId.of("Europe/Prague")
    private val hmFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val hmsFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val apiKey: String
        get() = apiKeyProvider?.invoke()?.trim()?.takeIf { it.isNotBlank() }
            ?: customApiKey?.takeIf { it.isNotBlank() }
            ?: runCatching { BuildConfig.GOLEMIO_API_KEY }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: DEFAULT_FALLBACK_KEY

    private val api: GolemioApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.golemio.cz/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GolemioApiService::class.java)
    }

    override suspend fun getDeparturesForStop(stop: PidStop): Result<List<DepartureInfo>> {
        return runCatching {
            var rawDepartures = emptyList<cz.pid.golemiocompanion.data.remote.dto.DepartureItemDto>()

            // 1. Přednostně vyzkoušet ASW uzel (např. Loukov uzel 9441 vrátí vlaky i náhradní autobusy XS35):
            if (!stop.aswNode.isNullOrBlank()) {
                val aswRes = runCatching {
                    api.getDepartureBoard(
                        apiKey = apiKey,
                        aswIds = stop.aswNode,
                        minutesBefore = 0,
                        minutesAfter = 240,
                        limit = 50
                    )
                }.getOrNull()

                if (!aswRes?.departures.isNullOrEmpty()) {
                    rawDepartures = aswRes?.departures.orEmpty()
                }
            }

            // 2. Pokud se nic nenašlo podle uzlu, dotázat se podle variant názvu zastávky:
            if (rawDepartures.isEmpty()) {
                val candidateNames = (listOf(stop.golemioPrimaryName, stop.displayName) + stop.getAllQueryNames()).distinct()
                for (name in candidateNames) {
                    val res = runCatching {
                        api.getDepartureBoard(
                            apiKey = apiKey,
                            stopName = name,
                            minutesBefore = 0,
                            minutesAfter = 240,
                            limit = 50
                        )
                    }.getOrNull()

                    if (!res?.departures.isNullOrEmpty()) {
                        rawDepartures = res?.departures.orEmpty()
                        break
                    }
                }
            }

            // 3. Záložní dotaz podle CIS stanice (např. pro vlakové stanice s vlastním CIS):
            if (rawDepartures.isEmpty() && !stop.cis.isNullOrBlank()) {
                val cisRes = runCatching {
                    api.getDepartureBoard(
                        apiKey = apiKey,
                        cisIds = stop.cis,
                        minutesBefore = 0,
                        minutesAfter = 240,
                        limit = 50
                    )
                }.getOrNull()

                if (!cisRes?.departures.isNullOrEmpty()) {
                    rawDepartures = cisRes?.departures.orEmpty()
                }
            }

            val nowMillis = System.currentTimeMillis()

            rawDepartures.mapNotNull { dto ->
                val scheduledIso = dto.departureTimestamp?.scheduled ?: return@mapNotNull null
                val scheduledOdt = runCatching { OffsetDateTime.parse(scheduledIso) }.getOrNull()
                    ?: return@mapNotNull null

                val scheduledEpochMillis = scheduledOdt.toInstant().toEpochMilli()

                // 1. Přednostně použít přesný predikovaný čas odjezdu z Golemia, pokud existuje:
                val predictedIso = dto.departureTimestamp?.predicted
                val predictedOdt = predictedIso?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
                val predictedEpochMillis = predictedOdt?.toInstant()?.toEpochMilli()

                val delayAvailable = dto.delay?.isAvailable == true
                val delaySeconds = when {
                    delayAvailable && dto.delay?.seconds != null -> dto.delay.seconds
                    predictedEpochMillis != null -> ((predictedEpochMillis - scheduledEpochMillis) / 1000L).toInt().coerceAtLeast(0)
                    dto.delay?.minutes != null -> dto.delay.minutes * 60
                    else -> 0
                }

                // Pokud Golemio aktualizuje zpoždění (např. z 8 na 6 min), čas odjezdu se ihned přepočítá:
                val exactDepartureEpochMillis = predictedEpochMillis ?: (scheduledEpochMillis + (delaySeconds * 1000L))

                // Zamezení zobrazení starých odjezdů:
                if (exactDepartureEpochMillis < nowMillis - 5_000L) {
                    return@mapNotNull null
                }

                val scheduledLocal = scheduledOdt.atZoneSameInstant(pragueZone)
                val exactLocal = OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(exactDepartureEpochMillis),
                    pragueZone
                )

                val routeShortName = dto.route?.shortName ?: "?"
                val headsign = dto.trip?.headsign ?: "Neznámý směr"
                val platform = dto.stop?.platformCode?.takeIf { it.isNotBlank() }

                DepartureInfo(
                    id = "${dto.trip?.id ?: routeShortName}_${scheduledEpochMillis}_${platform ?: ""}",
                    routeShortName = routeShortName,
                    headsign = headsign,
                    platformCode = platform,
                    scheduledTimeFormatted = scheduledLocal.format(hmFormatter),
                    exactDepartureTimeFormatted = exactLocal.format(hmsFormatter),
                    scheduledEpochMillis = scheduledEpochMillis,
                    exactDepartureEpochMillis = exactDepartureEpochMillis,
                    delaySeconds = delaySeconds,
                    isDelayAvailable = delayAvailable || (predictedEpochMillis != null && predictedEpochMillis != scheduledEpochMillis),
                    vehicleType = mapRouteType(dto.route?.type, routeShortName),
                    tripId = dto.trip?.id,
                    isWheelchairAccessible = dto.trip?.isWheelchairAccessible == true,
                    isAirConditioned = dto.trip?.isAirConditioned == true
                )
            }
                .distinctBy { "${it.routeShortName}_${it.headsign}_${it.scheduledEpochMillis}" }
                .sortedBy { it.exactDepartureEpochMillis }
        }
    }

    override suspend fun getLiveVehicles(routeFilter: String?): Result<List<LiveVehiclePosition>> {
        return runCatching {
            val cleanFilter = routeFilter?.trim()?.takeIf { it.isNotEmpty() }
            val response = api.getVehiclePositions(
                apiKey = apiKey,
                limit = 5000,
                routeShortName = cleanFilter,
                includeNotTracking = true
            )

            response.features.orEmpty().mapNotNull { feature ->
                val coords = feature.geometry?.coordinates
                if (coords == null || coords.size < 2) return@mapNotNull null
                val lon = coords[0]
                val lat = coords[1]
                if (lat == 0.0 && lon == 0.0) return@mapNotNull null

                val gtfs = feature.properties?.trip?.gtfs
                val lastPos = feature.properties?.lastPosition
                val routeName = gtfs?.routeShortName ?: return@mapNotNull null

                if (cleanFilter != null && !routeName.contains(cleanFilter, ignoreCase = true)) {
                    return@mapNotNull null
                }

                val vehicleTypeId = feature.properties?.trip?.vehicleType?.id ?: gtfs.routeType
                val delaySec = lastPos?.delay?.actual ?: 0

                LiveVehiclePosition(
                    id = gtfs.tripId ?: "${routeName}_${lat}_${lon}",
                    routeShortName = routeName,
                    headsign = gtfs.tripHeadsign ?: "",
                    latitude = lat,
                    longitude = lon,
                    bearing = lastPos?.bearing ?: 0f,
                    delaySeconds = delaySec,
                    speedKmh = (lastPos?.speed ?: 0f).toInt(),
                    vehicleType = mapRouteType(vehicleTypeId, routeName),
                    originTimestamp = lastPos?.originTimestamp,
                    isInactive = lastPos?.statePosition == "inactive" || lastPos?.originTimestamp == null
                )
            }
        }
    }

    override suspend fun getParkingLots(): Result<List<ParkingLotInfo>> {
        return runCatching {
            // 1. Přednostně použít Golemio v3 s live měřeními obsazenosti pro P+R (tsk-offstreet):
            val v3Lots = runCatching {
                api.getParkingLotsV3(apiKey = apiKey, primarySource = "tsk-offstreet", limit = 100)
            }.getOrNull()?.features.orEmpty()

            if (v3Lots.isNotEmpty()) {
                val measurementsMap = runCatching {
                    api.getParkingMeasurements(apiKey = apiKey, primarySource = "tsk-offstreet", limit = 100)
                        .associateBy { it.parkingId }
                }.getOrDefault(emptyMap())

                return@runCatching v3Lots.mapNotNull { feature ->
                    val props = feature.properties ?: return@mapNotNull null
                    val coords = props.centroid?.coordinates
                        ?: feature.geometry?.coordinates
                        ?: return@mapNotNull null

                    if (coords.size < 2 || (coords[0] == 0.0 && coords[1] == 0.0)) return@mapNotNull null

                    val pId = props.id.orEmpty()
                    val measurement = measurementsMap[pId]
                    val free = measurement?.freeSpotNumber ?: 0
                    val total = measurement?.totalSpotNumber?.takeIf { it > 0 } ?: (props.capacity ?: 0)

                    ParkingLotInfo(
                        id = pId.ifEmpty { props.name.orEmpty() },
                        name = props.name ?: "P+R Parkoviště",
                        latitude = coords[1],
                        longitude = coords[0],
                        freeSpaces = free,
                        totalCapacity = total
                    )
                }
            }

            // 2. Záložní endpoint v2/parking:
            val response = api.getParkingLots(apiKey = apiKey)
            response.features.orEmpty().mapNotNull { feature ->
                val coords = feature.geometry?.coordinates
                if (coords == null || coords.size < 2) return@mapNotNull null
                val props = feature.properties ?: return@mapNotNull null
                ParkingLotInfo(
                    id = props.id ?: props.name.orEmpty(),
                    name = props.name ?: "P+R Parkoviště",
                    latitude = coords[1],
                    longitude = coords[0],
                    freeSpaces = props.numOfFreePlaces ?: 0,
                    totalCapacity = props.totalNumOfPlaces ?: 0
                )
            }
        }
    }

    override suspend fun getSharedBikes(): Result<List<SharedBikeInfo>> {
        return runCatching {
            val response = runCatching { api.getSharedBikes(apiKey = apiKey) }.getOrNull()
            response?.features.orEmpty().mapNotNull { feature ->
                val coords = feature.geometry?.coordinates
                if (coords == null || coords.size < 2) return@mapNotNull null
                val props = feature.properties ?: return@mapNotNull null
                SharedBikeInfo(
                    id = props.id ?: "${coords[1]}_${coords[0]}",
                    companyName = props.company?.name ?: props.name ?: "Sdílené kolo",
                    latitude = coords[1],
                    longitude = coords[0],
                    availableBikes = 1
                )
            }
        }
    }

    private fun mapRouteType(gtfsType: Int?, routeShortName: String): VehicleType {
        return when (gtfsType) {
            0 -> VehicleType.TRAM
            1 -> VehicleType.METRO
            2 -> VehicleType.TRAIN
            11 -> VehicleType.TROLLEYBUS
            4 -> VehicleType.FERRY
            else -> {
                when {
                    routeShortName in listOf("A", "B", "C") -> VehicleType.METRO
                    routeShortName.startsWith("S", ignoreCase = true) ||
                        routeShortName.startsWith("R", ignoreCase = true) ||
                        routeShortName.startsWith("Os", ignoreCase = true) -> VehicleType.TRAIN
                    routeShortName.toIntOrNull() in 1..99 && !routeShortName.startsWith("X") -> VehicleType.TRAM
                    else -> VehicleType.BUS
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_FALLBACK_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MjU4MywiaWF0IjoxNzE1MDAwMDAwfQ.placeholder"
    }
}