package cz.pid.golemiocompanion.data.remote.api

import cz.pid.golemiocompanion.data.remote.dto.DepartureBoardResponseDto
import cz.pid.golemiocompanion.data.remote.dto.ParkingLotsGeoJsonDto
import cz.pid.golemiocompanion.data.remote.dto.SharedBikesGeoJsonDto
import cz.pid.golemiocompanion.data.remote.dto.VehiclePositionsGeoJsonDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GolemioApiService {

    /**
     * minutesBefore = 0 zamezí Golemiu posílat staré odjezdy za posledních 30 minut.
     * stopName: přesný název zastávky (př. "Loukov u Mn.Hradiště", "Žďár, Doubrava").
     */
    @GET("v2/pid/departureboards")
    suspend fun getDepartureBoard(
        @Header("X-Access-Token") apiKey: String,
        @Header("Cache-Control") cacheControl: String = "no-cache",
        @Query("names") stopName: String,
        @Query("minutesBefore") minutesBefore: Int = 0,
        @Query("minutesAfter") minutesAfter: Int = 240,
        @Query("limit") limit: Int = 50,
        @Query("preferredTimezone") preferredTimezone: String = "Europe/Prague",
        @Query("mode") mode: String = "departures",
        @Query("order") order: String = "real"
    ): DepartureBoardResponseDto

    @GET("v2/vehiclepositions")
    suspend fun getVehiclePositions(
        @Header("X-Access-Token") apiKey: String,
        @Header("Cache-Control") cacheControl: String = "no-cache",
        @Query("limit") limit: Int = 5000,
        @Query("routeShortName") routeShortName: String? = null,
        @Query("includeNotTracking") includeNotTracking: Boolean = true
    ): VehiclePositionsGeoJsonDto

    @GET("v1/parkings")
    suspend fun getParkingLots(
        @Header("X-Access-Token") apiKey: String,
        @Query("limit") limit: Int = 100
    ): ParkingLotsGeoJsonDto

    @GET("v1/sharedbikes")
    suspend fun getSharedBikes(
        @Header("X-Access-Token") apiKey: String,
        @Query("limit") limit: Int = 150
    ): SharedBikesGeoJsonDto
}