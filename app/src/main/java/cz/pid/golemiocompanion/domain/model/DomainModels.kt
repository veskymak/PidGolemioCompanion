package cz.pid.golemiocompanion.domain.model

data class DepartureInfo(
    val id: String,
    val routeShortName: String,
    val headsign: String,
    val platformCode: String?,
    val scheduledTimeFormatted: String,
    val exactDepartureTimeFormatted: String,
    val scheduledEpochMillis: Long,
    val exactDepartureEpochMillis: Long,
    val delaySeconds: Int,
    val isDelayAvailable: Boolean,
    val vehicleType: VehicleType = VehicleType.BUS,
    val tripId: String? = null,
    val isWheelchairAccessible: Boolean = false,
    val isAirConditioned: Boolean = false
) {
    /**
     * Vrací přesný počet zbývajících sekund: (JŘ + zpoždění Golemio) - aktuální čas.
     */
    fun remainingSecondsAt(nowMillis: Long): Long {
        return (exactDepartureEpochMillis - nowMillis) / 1000L
    }
}

enum class VehicleType {
    BUS,
    TRAM,
    METRO,
    TRAIN,
    TROLLEYBUS,
    FERRY
}

data class LiveVehiclePosition(
    val id: String,
    val routeShortName: String,
    val headsign: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val delaySeconds: Int,
    val speedKmh: Int,
    val vehicleType: VehicleType,
    val originTimestamp: String?,
    val isInactive: Boolean = false
)

data class ParkingLotInfo(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val freeSpaces: Int,
    val totalCapacity: Int
)

data class SharedBikeInfo(
    val id: String,
    val companyName: String,
    val latitude: Double,
    val longitude: Double,
    val availableBikes: Int
)

data class NearbyStop(
    val stop: PidStop,
    val distanceMeters: Int
) {
    val formattedDistance: String
        get() = if (distanceMeters < 1000) {
            "$distanceMeters m"
        } else {
            String.format("%.1f km", distanceMeters / 1000.0)
        }
}