package cz.pid.golemiocompanion.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PidDepartureBoardResponseDto(
    @SerialName("stops") val stops: List<PidStopDto> = emptyList(),
    @SerialName("departures") val departures: List<PidDepartureDto> = emptyList(),
    @SerialName("infotexts") val infoTexts: List<PidInfoTextDto> = emptyList()
)

@Serializable
data class PidStopDto(
    @SerialName("stop_id") val stopId: String = "",
    @SerialName("stop_name") val stopName: String = "",
    @SerialName("platform_code") val platformCode: String? = null,
    @SerialName("stop_lat") val stopLat: Double = 50.0755,
    @SerialName("stop_lon") val stopLon: Double = 14.4378
)

@Serializable
data class PidDepartureDto(
    @SerialName("arrival_timestamp") val arrivalTimestamp: PidTimestampDto = PidTimestampDto(),
    @SerialName("departure_timestamp") val departureTimestamp: PidTimestampDto = PidTimestampDto(),
    @SerialName("delay") val delay: PidDelayDto? = null,
    @SerialName("route") val route: PidRouteDto = PidRouteDto(),
    @SerialName("trip") val trip: PidTripDto = PidTripDto(),
    @SerialName("stop") val stop: PidDepartureStopRefDto = PidDepartureStopRefDto(),
    @SerialName("last_stop") val lastStop: PidLastStopDto? = null
)

@Serializable
data class PidTimestampDto(
    @SerialName("predicted") val predicted: String? = null,
    @SerialName("scheduled") val scheduled: String? = null,
    @SerialName("minutes") val minutes: String? = null
)

@Serializable
data class PidDelayDto(
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("minutes") val minutes: Int? = 0,
    @SerialName("seconds") val seconds: Int? = 0
)

@Serializable
data class PidRouteDto(
    @SerialName("short_name") val shortName: String = "",
    @SerialName("type") val type: Int = 3,
    @SerialName("is_night") val isNight: Boolean = false
)

@Serializable
data class PidTripDto(
    @SerialName("id") val id: String = "",
    @SerialName("headsign") val headsign: String = "",
    @SerialName("is_wheelchair_accessible") val isWheelchairAccessible: Boolean = true,
    @SerialName("is_air_conditioned") val isAirConditioned: Boolean? = true
)

@Serializable
data class PidDepartureStopRefDto(
    @SerialName("id") val id: String = "",
    @SerialName("platform_code") val platformCode: String? = null
)

@Serializable
data class PidLastStopDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class PidInfoTextDto(
    @SerialName("text") val text: String = "",
    @SerialName("display_type") val displayType: String? = null
)

@Serializable
data class VehiclePositionsGeoJsonDto(
    @SerialName("type") val type: String = "FeatureCollection",
    @SerialName("features") val features: List<VehicleFeatureDto> = emptyList()
)

@Serializable
data class VehicleFeatureDto(
    @SerialName("geometry") val geometry: GeoJsonPointDto,
    @SerialName("properties") val properties: VehiclePropertiesDto
)

@Serializable
data class GeoJsonPointDto(
    @SerialName("type") val type: String = "Point",
    @SerialName("coordinates") val coordinates: List<Double> = listOf(14.4378, 50.0755)
)

@Serializable
data class VehiclePropertiesDto(
    @SerialName("trip") val trip: VehicleTripPropertiesDto,
    @SerialName("last_position") val lastPosition: VehicleLastPositionDto
)

@Serializable
data class VehicleTripPropertiesDto(
    @SerialName("gtfs") val gtfs: VehicleGtfsDto,
    @SerialName("vehicle_type") val vehicleType: VehicleTypeDto? = null,
    @SerialName("vehicle_registration_number") val vehicleRegistrationNumber: Long? = null,
    @SerialName("wheelchair_accessible") val wheelchairAccessible: Boolean? = null,
    @SerialName("air_conditioned") val airConditioned: Boolean? = null
)

@Serializable
data class VehicleGtfsDto(
    @SerialName("route_short_name") val routeShortName: String = "",
    @SerialName("trip_id") val tripId: String = "",
    @SerialName("trip_headsign") val tripHeadsign: String = ""
)

@Serializable
data class VehicleTypeDto(
    @SerialName("id") val id: Int = 3,
    @SerialName("description_cs") val descriptionCs: String = "autobus"
)

@Serializable
data class VehicleLastPositionDto(
    @SerialName("bearing") val bearing: Float? = 0f,
    @SerialName("delay") val delay: VehicleDelayDetailDto? = null,
    @SerialName("origin_timestamp") val originTimestamp: String? = null,
    @SerialName("speed") val speed: Double? = null,
    @SerialName("is_canceled") val isCanceled: Boolean = false,
    @SerialName("tracking") val tracking: Boolean? = true,
    @SerialName("next_stop") val nextStop: VehicleStopDetailDto? = null,
    @SerialName("last_stop") val lastStop: VehicleStopDetailDto? = null
)

@Serializable
data class VehicleStopDetailDto(
    @SerialName("id") val id: String? = null,
    @SerialName("sequence") val sequence: Int? = null,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    @SerialName("departure_time") val departureTime: String? = null
)

@Serializable
data class GtfsTripDetailDto(
    @SerialName("trip_id") val tripId: String = "",
    @SerialName("trip_headsign") val tripHeadsign: String? = null,
    @SerialName("stop_times") val stopTimes: List<GtfsStopTimeDto> = emptyList(),
    @SerialName("stops") val stops: List<GtfsStopDto> = emptyList()
)

@Serializable
data class GtfsStopTimeDto(
    @SerialName("stop_id") val stopId: String = "",
    @SerialName("stop_sequence") val stopSequence: Int = 0,
    @SerialName("arrival_time") val arrivalTime: String = "",
    @SerialName("departure_time") val departureTime: String = ""
)

@Serializable
data class GtfsStopDto(
    @SerialName("stop_id") val stopId: String = "",
    @SerialName("stop_name") val stopName: String = "",
    @SerialName("platform_code") val platformCode: String? = null
)

@Serializable
data class VehicleDelayDetailDto(
    @SerialName("actual") val actual: Int? = 0,
    @SerialName("last_stop_arrival") val lastStopArrival: Int? = 0,
    @SerialName("last_stop_departure") val lastStopDeparture: Int? = 0
)

@Serializable
data class PidInputGatewayTelemetryDto(
    @SerialName("trip_id") val tripId: String = "",
    @SerialName("route_short_name") val routeShortName: String = "",
    @SerialName("lat") val lat: Double = 50.0755,
    @SerialName("lng") val lng: Double = 14.4378,
    @SerialName("bearing") val bearing: Float = 0f,
    @SerialName("speed_kmh") val speedKmh: Double = 32.0,
    @SerialName("delay_seconds") val delaySeconds: Int = 0,
    @SerialName("gateway_timestamp") val gatewayTimestamp: String = ""
)

@Serializable
data class SharedBikesGeoJsonDto(
    @SerialName("type") val type: String = "FeatureCollection",
    @SerialName("features") val features: List<SharedBikeFeatureDto> = emptyList()
)

@Serializable
data class SharedBikeFeatureDto(
    @SerialName("geometry") val geometry: GeoJsonPointDto,
    @SerialName("properties") val properties: SharedBikePropertiesDto
)

@Serializable
data class SharedBikePropertiesDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("company_name") val companyName: String = "Nextbike",
    @SerialName("num_bikes_available") val numBikesAvailable: Int = 0,
    @SerialName("num_docks_available") val numDocksAvailable: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ParkingsGeoJsonDto(
    @SerialName("type") val type: String = "FeatureCollection",
    @SerialName("features") val features: List<ParkingFeatureDto> = emptyList()
)

@Serializable
data class ParkingFeatureDto(
    @SerialName("geometry") val geometry: GeoJsonPointDto,
    @SerialName("properties") val properties: ParkingPropertiesDto
)

@Serializable
data class ParkingPropertiesDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("num_of_free_places") val numOfFreePlaces: Int = 0,
    @SerialName("total_num_of_places") val totalNumOfPlaces: Int = 100,
    @SerialName("parking_type") val parkingType: ParkingTypeDto? = null
)

@Serializable
data class ParkingTypeDto(
    @SerialName("id") val id: Int = 1,
    @SerialName("description") val description: String = "P+R"
)

@Serializable
data class AirQualityGeoJsonDto(
    @SerialName("type") val type: String = "FeatureCollection",
    @SerialName("features") val features: List<AirQualityFeatureDto> = emptyList()
)

@Serializable
data class AirQualityFeatureDto(
    @SerialName("geometry") val geometry: GeoJsonPointDto,
    @SerialName("properties") val properties: AirQualityPropertiesDto
)

@Serializable
data class AirQualityPropertiesDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("measurement") val measurement: AirQualityMeasurementDto? = null
)

@Serializable
data class AirQualityMeasurementDto(
    @SerialName("AQ_hourly_index") val aqHourlyIndex: Int? = 2,
    @SerialName("components") val components: List<AirQualityComponentDto> = emptyList()
)

@Serializable
data class AirQualityComponentDto(
    @SerialName("type") val type: String = "",
    @SerialName("value") val value: Double = 0.0
)