package cz.pid.golemiocompanion.domain.repository

import cz.pid.golemiocompanion.domain.model.*

interface GolemioRepository {
    suspend fun getDeparturesForStop(
        stopQuery: String,
        lineFilter: String?,
        labSettings: LabSettingsState,
        currentWeather: AirQualityWeatherInfo?
    ): List<StopConnection>

    suspend fun getAirQualityAndWeather(
        lat: Double,
        lon: Double,
        apiKey: String
    ): AirQualityWeatherInfo

    suspend fun getNearbySharedBikes(
        lat: Double,
        lon: Double,
        apiKey: String
    ): List<SharedBikeStation>

    suspend fun getEmergencyTransfersWithin600m(
        originLat: Double,
        originLon: Double,
        targetDestination: String,
        excludedLine: String,
        apiKey: String
    ): List<EmergencyTransferOption>

    suspend fun getLiveVehicles(
        lineFilter: String?,
        labSettings: LabSettingsState
    ): List<LiveVehicleMarker>

    suspend fun getParkingsPR(apiKey: String): List<ParkingLotPR>
    suspend fun getVehicleUpcomingStops(vehicle: LiveVehicleMarker, apiKey: String): List<VehicleUpcomingStop>
}