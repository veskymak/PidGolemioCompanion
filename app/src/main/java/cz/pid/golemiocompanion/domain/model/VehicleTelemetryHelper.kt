package cz.pid.golemiocompanion.domain.model

import java.time.OffsetDateTime
import kotlin.math.roundToInt

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
        if (liveVehicles.isEmpty()) return null

        // 1. Zkusit přesnou shodu tripId
        if (!departure.tripId.isNullOrBlank()) {
            val exactTrip = liveVehicles.firstOrNull { it.id == departure.tripId }
            if (exactTrip != null) return exactTrip
        }

        // 2. Filtrovat dle čísla linky (např. "714", "715", "345")
        val lineMatches = liveVehicles.filter {
            it.routeShortName.equals(departure.routeShortName, ignoreCase = true)
        }
        if (lineMatches.isEmpty()) return null
        if (lineMatches.size == 1) return lineMatches.first()

        // 3. Filtrovat dle směru / headsign
        val headsignMatches = lineMatches.filter {
            it.headsign.contains(departure.headsign, ignoreCase = true) ||
                departure.headsign.contains(it.headsign, ignoreCase = true)
        }
        val candidatePool = if (headsignMatches.isNotEmpty()) headsignMatches else lineMatches

        // 4. Vybrat vůz, který je nejblíže sledované zastávce
        return candidatePool.minByOrNull { veh ->
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
        val angleDiff = kotlin.math.abs(bearing - bearingToStop)
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

        // Efektivní rychlost přibližování (zohledňuje zpomalování před zastávkou, křižovatky a manévry)
        val effectiveSpeedMps = when {
            speed > 12 -> ((speed / 3.6) * 0.75 + 7.0 * 0.25).coerceIn(5.5, 13.8) // 20 až 50 km/h
            else -> 7.0 // ~25 km/h výchozí městský/regionální průměr pro popojíždění
        }

        // Matematický odhad dojezdu podle reálné vzdálenosti na mapě
        val travelSeconds = if (isAtStop) 0 else (distanceMeters / effectiveSpeedMps).roundToInt()
        val dwellSeconds = if (isAtStop) 15 else 20
        val estimatedArrivalSeconds = travelSeconds + dwellSeconds

        // Dynamický čas odjezdu na základě reálné vzdálenosti (overlaye mapy)
        val calculatedDepartureEpochMillis = nowMillis + (estimatedArrivalSeconds * 1000L)

        // Zjistíme, zda vůz směřuje k zastávce nebo se od ní vzdaluje
        val headingTowardsStop = isHeadingTowardsStop(
            vehicle.latitude, vehicle.longitude, vehicle.bearing,
            stop.latitude, stop.longitude
        )

        // Odpočet nesmí zmizet, dokud vůz neopustí zastávku:
        // hasDeparted je true pouze tehdy, když vůz není v zastávce A (vzdaluje se od zastávky po plánovaném čase, nebo je daleko v minulosti)
        val hasDeparted = when {
            isAtStop -> false
            // Pokud jede pryč od zastávky a čas odjezdu již uplynul nebo je dál než 120m
            !headingTowardsStop && distanceMeters > 120 && (nowMillis >= scheduledDepartureEpochMillis - 10_000L) -> true
            // Pokud je víc než 3 minuty po čase a vůz je daleko
            nowMillis > scheduledDepartureEpochMillis + 180_000L && distanceMeters > 250 -> true
            else -> false
        }

        val formattedStatus = when {
            isAtStop -> "🟢 Vůz právě v zastávce (odbavování)"
            distanceMeters <= 350 -> "🟡 Vůz přijíždí do zastávky (${distanceMeters} m)"
            else -> "🔵 Vůz se blíží (${distanceMeters} m, ${speed} km/h)"
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
            calculatedDepartureEpochMillis = calculatedDepartureEpochMillis,
            formattedStatus = formattedStatus
        )
    }
}
