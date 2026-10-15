package cz.pid.golemiocompanion.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DepartureBoardResponseDto(
    @SerializedName("departures") val departures: List<DepartureItemDto>? = emptyList()
)

data class DepartureItemDto(
    @SerializedName("departure_timestamp") val departureTimestamp: TimestampDto? = null,
    @SerializedName("arrival_timestamp") val arrivalTimestamp: TimestampDto? = null,
    @SerializedName("delay") val delay: DelayDto? = null,
    @SerializedName("route") val route: RouteDto? = null,
    @SerializedName("trip") val trip: TripDto? = null,
    @SerializedName("stop") val stop: StopDto? = null
)

data class TimestampDto(
    @SerializedName("scheduled") val scheduled: String? = null,
    @SerializedName("predicted") val predicted: String? = null,
    @SerializedName("minutes") val minutes: String? = null
)

data class DelayDto(
    @SerializedName("is_available") val isAvailable: Boolean? = false,
    @SerializedName("minutes") val minutes: Int? = 0,
    @SerializedName("seconds") val seconds: Int? = 0
)

data class RouteDto(
    @SerializedName("short_name") val shortName: String? = null,
    @SerializedName("type") val type: Int? = null,
    @SerializedName("is_night") val isNight: Boolean? = false
)

data class TripDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("headsign") val headsign: String? = null,
    @SerializedName("is_wheelchair_accessible") val isWheelchairAccessible: Boolean? = false,
    @SerializedName("is_air_conditioned") val isAirConditioned: Boolean? = false
)

data class StopDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("platform_code") val platformCode: String? = null
)

// Vehicle positions GeoJSON
data class VehiclePositionsGeoJsonDto(
    @SerializedName("features") val features: List<VehicleFeatureDto>? = emptyList()
)

data class VehicleFeatureDto(
    @SerializedName("geometry") val geometry: GeometryDto? = null,
    @SerializedName("properties") val properties: VehiclePropertiesDto? = null
)

data class GeometryDto(
    @SerializedName("coordinates") val coordinates: List<Double>? = null
)

data class VehiclePropertiesDto(
    @SerializedName("trip") val trip: VehicleTripPropertiesDto? = null,
    @SerializedName("last_position") val lastPosition: LastPositionDto? = null
)

data class VehicleTripPropertiesDto(
    @SerializedName("gtfs") val gtfs: GtfsTripDto? = null,
    @SerializedName("vehicle_type") val vehicleType: VehicleTypeObjDto? = null
)

data class GtfsTripDto(
    @SerializedName("route_short_name") val routeShortName: String? = null,
    @SerializedName("trip_headsign") val tripHeadsign: String? = null,
    @SerializedName("trip_id") val tripId: String? = null,
    @SerializedName("route_type") val routeType: Int? = null
)

data class VehicleTypeObjDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("description_cs") val descriptionCs: String? = null
)

data class LastPositionDto(
    @SerializedName("bearing") val bearing: Float? = 0f,
    @SerializedName("speed") val speed: Float? = 0f,
    @SerializedName("delay") val delay: VehicleDelayDto? = null,
    @SerializedName("origin_timestamp") val originTimestamp: String? = null,
    @SerializedName("state_position") val statePosition: String? = null
)

data class VehicleDelayDto(
    @SerializedName("actual") val actual: Int? = 0
)

// Parking lots GeoJSON
data class ParkingLotsGeoJsonDto(
    @SerializedName("features") val features: List<ParkingFeatureDto>? = emptyList()
)

data class ParkingFeatureDto(
    @SerializedName("geometry") val geometry: GeometryDto? = null,
    @SerializedName("properties") val properties: ParkingPropertiesDto? = null
)

data class ParkingPropertiesDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("num_of_free_places") val numOfFreePlaces: Int? = 0,
    @SerializedName("total_num_of_places") val totalNumOfPlaces: Int? = 0
)

// Shared bikes GeoJSON
data class SharedBikesGeoJsonDto(
    @SerializedName("features") val features: List<SharedBikeFeatureDto>? = emptyList()
)

data class SharedBikeFeatureDto(
    @SerializedName("geometry") val geometry: GeometryDto? = null,
    @SerializedName("properties") val properties: SharedBikePropertiesDto? = null
)

data class SharedBikePropertiesDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("company") val company: BikeCompanyDto? = null
)

data class BikeCompanyDto(
    @SerializedName("name") val name: String? = null
)