package cz.pid.golemiocompanion.data.remote.api

import cz.pid.golemiocompanion.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GolemioApiService {

    @GET("v2/pid/departureboards")
    suspend fun getDepartureBoards(
        @Header("X-Access-Token") apiKey: String,
        @Query("names[]") stopNames: List<String>? = null,
        @Query("ids[]") stopIds: List<String>? = null,
        @Query("minutesBefore") minutesBefore: Int = 5,
        @Query("minutesAfter") minutesAfter: Int = 60,
        @Query("includeMetroTrains") includeMetroTrains: Boolean = true,
        @Query("airCondition") airCondition: Boolean = true,
        @Query("preferredTimezone") preferredTimezone: String = "Europe/Prague",
        @Query("mode") mode: String = "departures",
        @Query("order") order: String = "real",
        @Query("limit") limit: Int = 20
    ): PidDepartureBoardResponseDto

    @GET("v2/pid/vehiclepositions")
    suspend fun getVehiclePositions(
        @Header("X-Access-Token") apiKey: String,
        @Query("routeShortName") routeShortName: String? = null,
        @Query("includeNotTracking") includeNotTracking: Boolean = false,
        @Query("limit") limit: Int = 100
    ): VehiclePositionsGeoJsonDto

    @GET("pid/input-gateway/v1/vehiclepositions/raw")
    suspend fun getRawTelemetryStream(
        @Header("X-Access-Token") apiKey: String,
        @Query("routeShortName") routeShortName: String? = null
    ): List<PidInputGatewayTelemetryDto>

    @GET("v2/sharedbikes")
    suspend fun getSharedBikes(
        @Header("X-Access-Token") apiKey: String,
        @Query("latlng") latLng: String? = null,
        @Query("range") rangeMeters: Int = 650,
        @Query("limit") limit: Int = 30
    ): SharedBikesGeoJsonDto

    @GET("v2/parkings")
    suspend fun getParkingsPR(
        @Header("X-Access-Token") apiKey: String,
        @Query("limit") limit: Int = 50
    ): ParkingsGeoJsonDto

    @GET("v2/airqualitystations")
    suspend fun getAirQualityStations(
        @Header("X-Access-Token") apiKey: String,
        @Query("latlng") latLng: String? = null,
        @Query("range") rangeMeters: Int = 5000,
        @Query("limit") limit: Int = 5
    ): AirQualityGeoJsonDto
}