package cz.pid.golemiocompanion.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Immutable
data class StopConnection(
    val tripId: String,
    val routeShortName: String,
    val routeType: Int,
    val headsign: String,
    val stopName: String,
    val platformCode: String,
    val stopLat: Double,
    val stopLon: Double,
    val scheduledDepartureEpochMs: Long,
    val predictedDepartureEpochMs: Long,
    val scheduledArrivalDestinationEpochMs: Long,
    val predictedArrivalDestinationEpochMs: Long,
    val destinationName: String,
    val officialDelaySeconds: Int,
    val phantomAiExtraDelaySeconds: Int = 0,
    val isAirConditioned: Boolean = true,
    val isWheelchairAccessible: Boolean = true
) {
    val totalDelaySeconds: Int
        get() = officialDelaySeconds + phantomAiExtraDelaySeconds

    val effectiveDepartureEpochMs: Long
        get() = maxOf(predictedDepartureEpochMs, scheduledDepartureEpochMs + (totalDelaySeconds * 1000L))

    val effectiveDestinationArrivalEpochMs: Long
        get() = maxOf(predictedArrivalDestinationEpochMs, scheduledArrivalDestinationEpochMs + (totalDelaySeconds * 1000L))
}

@Immutable
data class PidStopSuggestion(
    val name: String,
    val icon: String,
    val typeDescription: String,
    val subtitle: String
)

@Immutable
data class FavoriteConnection(
    val id: String,
    val stopName: String,
    val routeShortName: String,
    val headsign: String,
    val preferredDepartureTimeHHmm: String
)

@Immutable
data class AirQualityWeatherInfo(
    val stationName: String,
    val temperatureCelsius: Double,
    val aqiIndex: Int,
    val pm10UgM3: Double,
    val no2UgM3: Double
) {
    val aqiLabelCs: String
        get() = when (aqiIndex) {
            1 -> "1 • Výborná"
            2 -> "2 • Dobrá"
            3 -> "3 • Přijatelná"
            4 -> "4 • Zhoršená"
            5 -> "5 • Špatná"
            else -> "6 • Velmi špatná"
        }
}

@Immutable
data class SharedBikeStation(
    val id: String,
    val name: String,
    val provider: String,
    val freeBikes: Int,
    val distanceMeters: Int,
    val walkingMinutes: Int,
    val lat: Double,
    val lon: Double
)

@Immutable
data class EmergencyTransferOption(
    val nearbyStopName: String,
    val platformCode: String,
    val distanceMeters: Int,
    val routeShortName: String,
    val headsign: String,
    val departureInMinutes: Int,
    val delaySeconds: Int
)

@Immutable
data class VehicleUpcomingStop(
    val stopId: String,
    val stopName: String,
    val sequence: Int,
    val minutesUntilArrival: Long,
    val scheduledTime: String,
    val predictedTime: String,
    val isDestination: Boolean = false
)

@Immutable
data class LiveVehicleMarker(
    val tripId: String,
    val routeShortName: String,
    val headsign: String,
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val delaySeconds: Int,
    val speedKmh: Double,
    val lastUpdatedEpochMs: Long,
    val isDeadReckoningEstimated: Boolean = false,
    val isFromRawInputGateway: Boolean = false,
    val isTracking: Boolean = true,
    val nextStopId: String? = null,
    val nextStopName: String? = null,
    val nextStopSequence: Int? = null,
    val nextStopArrivalEpochMs: Long? = null,
    val lastStopName: String? = null,
    val vehicleRegistrationNumber: Long? = null,
    val isAirConditioned: Boolean? = null,
    val isWheelchairAccessible: Boolean? = null
)

@Immutable
data class ParkingLotPR(
    val id: String,
    val name: String,
    val freePlaces: Int,
    val totalPlaces: Int,
    val lat: Double,
    val lon: Double
) {
    val occupancyRatio: Float
        get() = if (totalPlaces > 0) (totalPlaces - freePlaces).toFloat() / totalPlaces else 0f
}

@Immutable
data class LabSettingsState(
    val deadReckoningInTunnels: Boolean = false,
    val rawInputGatewayStream: Boolean = false,
    val phantomAiDelayPrediction: Boolean = false,
    val forced120HzAggressiveRender: Boolean = false,
    val emergencyTransfer600m: Boolean = true,
    val floatingPipMiniCountdown: Boolean = false,
    val golemioApiKey: String = "",
    val googleMapsApiKey: String = ""
)

@Stable
sealed interface TripTrackingMode {
    data object Idle : TripTrackingMode
    data class CountingDownToDeparture(val connection: StopConnection) : TripTrackingMode
    data class ReachedZeroActionPrompt(val connection: StopConnection) : TripTrackingMode
    data class WaitingInNegativeTime(val connection: StopConnection) : TripTrackingMode
    data class BoardedCountingToDestination(val connection: StopConnection) : TripTrackingMode
    data class MissedConnectionRescue(
        val missedConnection: StopConnection,
        val nextConnection: StopConnection?,
        val nearbyBikes: List<SharedBikeStation>,
        val emergencyTransfers: List<EmergencyTransferOption>
    ) : TripTrackingMode
}