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
            val queryNames = stop.getAllQueryNames()
            val response = api.getDepartureBoard(
                apiKey = apiKey,
                stopNames = queryNames,
                minutesBefore = 0,
                minutesAfter = 180,
                limit = 40
            )

            val nowMillis = System.currentTimeMillis()

            response.departures.orEmpty().mapNotNull { dto ->
                val scheduledIso = dto.departureTimestamp?.scheduled ?: return@mapNotNull null
                val scheduledOdt = runCatching { OffsetDateTime.parse(scheduledIso) }.getOrNull()
                    ?: return@mapNotNull null

                val scheduledEpochMillis = scheduledOdt.toInstant().toEpochMilli()

                // PŘESNÝ VÝPOČET DLE ZADÁNÍ:
                // Pouze Jízdní řád (scheduled) + nahlášené zpoždění z Golemia v sekundách.
                // Nepřičítá se zaokrouhlené pole 'predicted' ani stáří pingu vozidla.
                val delayAvailable = dto.delay?.isAvailable == true
                val delaySeconds = if (delayAvailable) (dto.delay?.seconds ?: 0) else 0

                val exactDepartureEpochMillis = scheduledEpochMillis + (delaySeconds * 1000L)

                // Zamezení zobrazení starých odjezdů:
                // Spoje, které již odjely (s tolerancí max 5 sekund pro spoj na zastávce), se okamžitě zahodí.
                if (exactDepartureEpochMillis < nowMillis - 5_000L) {
                    return@mapNotNull null
                }

                val scheduledLocal = scheduledOdt.atZoneSameInstant(pragueZone)
                val exactLocal = scheduledOdt.plusSeconds(delaySeconds.toLong()).atZoneSameInstant(pragueZone)

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
                    isDelayAvailable = delayAvailable,
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
                limit = 1200,
                routeShortName = cleanFilter
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

                if (cleanFilter != null && !routeName.equals(cleanFilter, ignoreCase = true)) {
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
                    isInactive = lastPos?.statePosition == "inactive"
                )
            }
        }
    }

    override suspend fun getParkingLots(): Result<List<ParkingLotInfo>> {
        return runCatching {
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
            val response = api.getSharedBikes(apiKey = apiKey)
            response.features.orEmpty().mapNotNull { feature ->
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