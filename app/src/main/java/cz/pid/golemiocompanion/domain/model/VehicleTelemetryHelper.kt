package cz.pid.golemiocompanion.domain.model

import java.time.OffsetDateTime
import kotlin.math.abs

data class VehicleTrackingTelemetry(
    val vehicle: LiveVehiclePosition,
    val distanceMeters: Int,
    val pingAgeSeconds: Long,
    val speedKmh: Int,
    val delaySeconds: Int,
    val isAtStop: Boolean,
    val hasDeparted: Boolean,
    val estimatedArrivalSeconds: Int,
    val calculatedDepartureEpochMillis: Long,
    val formattedStatus: String
)

object VehicleTelemetryHelper {

    fun findMatchingLiveVehicle(
        departure: DepartureInfo,
        targetStop: PidStop,
        liveVehicles: List<LiveVehiclePosition>
    ): LiveVehiclePosition? {
        val activeVehicles = liveVehicles.filter { !it.isInactive }
        if (activeVehicles.isEmpty()) return null

        // 1. Pokud odjezd obsahuje GTFS tripId (standard v Golemio departureboards),
        // spárujeme VÝHRADNĚ tento konkrétní spoj (tripId), aby se nikdy nezobrazovala
        // vzdálenost jiného autobusu na stejné lince!
        val targetTripId = departure.tripId?.trim()
        if (!targetTripId.isNullOrBlank()) {
            val exactTrip = activeVehicles.firstOrNull {
                it.id.equals(targetTripId, ignoreCase = true)
            }
            if (exactTrip != null) return exactTrip

            // Záložní shoda pouze pro případ, že některý vůz z API nemá vyplněné GTFS trip_id
            val untaggedCandidates = activeVehicles.filter { veh ->
                veh.id == "${veh.routeShortName}_${veh.latitude}_${veh.longitude}" &&
                    veh.routeShortName.equals(departure.routeShortName, ignoreCase = true) &&
                    (veh.headsign.contains(departure.headsign, ignoreCase = true) ||
                        departure.headsign.contains(veh.headsign, ignoreCase = true)) &&
                    abs(veh.delaySeconds - departure.delaySeconds) <= 90
            }
            return untaggedCandidates.minByOrNull { veh ->
                PidStopDirectory.calculateDistanceMeters(
                    veh.latitude, veh.longitude,
                    targetStop.latitude, targetStop.longitude
                )
            }
        }

        // 2. Pouze pokud odjezd nemá žádné tripId: filtrovat dle čísla linky, směru a podobného zpoždění
        val lineAndDirMatches = activeVehicles.filter { veh ->
            veh.routeShortName.equals(departure.routeShortName, ignoreCase = true) &&
                (veh.headsign.contains(departure.headsign, ignoreCase = true) ||
                    departure.headsign.contains(veh.headsign, ignoreCase = true)) &&
                abs(veh.delaySeconds - departure.delaySeconds) <= 120
        }

        return lineAndDirMatches.minByOrNull { veh ->
            PidStopDirectory.calculateDistanceMeters(
                veh.latitude, veh.longitude,
                targetStop.latitude, targetStop.longitude
            )
        }
    }

    fun isHeadingTowardsStop(
        vehLat: Double,
        vehLon: Double,
        bearing: Float,
        stopLat: Double,
        stopLon: Double
    ): Boolean {
        val dLat = stopLat - vehLat
        val dLon = (stopLon - vehLon) * kotlin.math.cos(Math.toRadians((vehLat + stopLat) / 2.0))
        val bearingToStop = (Math.toDegrees(kotlin.math.atan2(dLon, dLat)) + 360.0) % 360.0
        val angleDiff = abs(bearing - bearingToStop)
        val normalizedDiff = if (angleDiff > 180.0) 360.0 - angleDiff else angleDiff
        return normalizedDiff < 100.0
    }

    fun calculateVehicleTelemetry(
        vehicle: LiveVehiclePosition,
        stop: PidStop,
        scheduledDepartureEpochMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): VehicleTrackingTelemetry {
        val distanceMeters = PidStopDirectory.calculateDistanceMeters(
            vehicle.latitude, vehicle.longitude,
            stop.latitude, stop.longitude
        )

        // Stáří polohy v sekundách (z origin_timestamp pingu palubního počítače vozu)
        val pingAgeSeconds = vehicle.originTimestamp?.let { iso ->
            runCatching {
                val odt = OffsetDateTime.parse(iso)
                ((nowMillis - odt.toInstant().toEpochMilli()) / 1000L).coerceAtLeast(0L)
            }.getOrNull()
        } ?: 0L

        val speed = vehicle.speedKmh
        val delaySec = vehicle.delaySeconds

        // Vůz je v zastávce, pokud je do 65 metrů od sloupku
        val isAtStop = distanceMeters <= 65

        // Oficiální čas odjezdu z PID (JŘ + nahlášené zpoždění Golemio).
        // Pokud je vůz přímo v zastávce, držíme odpočet aktivní během odbavování.
        val finalDepartureEpochMillis = if (isAtStop) {
            kotlin.math.max(nowMillis + 15_000L, scheduledDepartureEpochMillis)
        } else {
            scheduledDepartureEpochMillis
        }

        val estimatedArrivalSeconds = ((finalDepartureEpochMillis - nowMillis) / 1000L).coerceAtLeast(0L).toInt()

        val headingTowardsStop = isHeadingTowardsStop(
            vehicle.latitude, vehicle.longitude, vehicle.bearing,
            stop.latitude, stop.longitude
        )

        val hasDeparted = when {
            isAtStop -> false
            !headingTowardsStop && distanceMeters > 120 && (nowMillis >= scheduledDepartureEpochMillis - 10_000L) -> true
            nowMillis > scheduledDepartureEpochMillis + 180_000L && distanceMeters > 250 -> true
            else -> false
        }

        val formattedStatus = when {
            isAtStop -> "🟢 Vůz právě v zastávce (odbavování)"
            distanceMeters < 700 -> "🟡 Vůz přijíždí k zastávce (${distanceMeters} m)"
            else -> "🔵 Vůz na trase (${distanceMeters} m od zastávky)"
        }

        return VehicleTrackingTelemetry(
            vehicle = vehicle,
            distanceMeters = distanceMeters,
            pingAgeSeconds = pingAgeSeconds,
            speedKmh = speed,
            delaySeconds = delaySec,
            isAtStop = isAtStop,
            hasDeparted = hasDeparted,
            estimatedArrivalSeconds = estimatedArrivalSeconds,
            calculatedDepartureEpochMillis = finalDepartureEpochMillis,
            formattedStatus = formattedStatus
        )
    }
}

