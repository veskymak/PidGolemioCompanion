package cz.pid.golemiocompanion.ui.trip

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.pid.golemiocompanion.data.local.LabPreferencesRepository
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.domain.repository.GolemioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class TripUiState(
    val stopSearchQuery: String = "Anděl",
    val lineFilterQuery: String = "",
    val isLoading: Boolean = false,
    val departures: List<StopConnection> = emptyList(),
    val weatherAndAqi: AirQualityWeatherInfo = AirQualityWeatherInfo(
        stationName = "Praha 5 - Smíchov",
        temperatureCelsius = 16.0,
        aqiIndex = 2,
        pm10UgM3 = 18.0,
        no2UgM3 = 22.0
    ),
    val trackingMode: TripTrackingMode = TripTrackingMode.Idle,
    val liveVehicles: List<LiveVehicleMarker> = emptyList(),
    val parkingsPR: List<ParkingLotPR> = emptyList(),
    val mapSharedBikes: List<SharedBikeStation> = emptyList(),
    val showParkingsLayer: Boolean = true,
    val showBikesLayer: Boolean = true,
    val mapLineFilter: String = ""
)

class TripViewModel(
    private val repository: GolemioRepository,
    private val preferencesRepository: LabPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    private val _remainingSecondsFlow = MutableStateFlow(0L)
    val remainingSecondsFlow: StateFlow<Long> = _remainingSecondsFlow.asStateFlow()

    val labSettingsFlow: StateFlow<LabSettingsState> = preferencesRepository.labSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabSettingsState())

    val favoritesFlow: StateFlow<List<FavoriteConnection>> = preferencesRepository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var tickerJob: Job? = null
    private var pollingJob: Job? = null
    private var mapPollingJob: Job? = null

    init {
        refreshStopData()
        startIndependentCountdownTicker()
        startLiveMapPolling()
    }

    fun onStopQueryChanged(query: String) {
        _uiState.update { it.copy(stopSearchQuery = query) }
    }

    fun onLineFilterChanged(line: String) {
        _uiState.update { it.copy(lineFilterQuery = line) }
    }

    fun refreshStopData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val currentState = _uiState.value
            val lab = labSettingsFlow.value

            val weatherDeferred = async {
                repository.getAirQualityAndWeather(50.0715, 14.4035, lab.golemioApiKey)
            }
            val weather = weatherDeferred.await()

            val connections = repository.getDeparturesForStop(
                stopQuery = currentState.stopSearchQuery,
                lineFilter = currentState.lineFilterQuery,
                labSettings = lab,
                currentWeather = weather
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    departures = connections,
                    weatherAndAqi = weather
                )
            }
        }
    }

    fun startTrackingConnection(connection: StopConnection) {
        _uiState.update {
            it.copy(trackingMode = TripTrackingMode.CountingDownToDeparture(connection))
        }
        updateRemainingSecondsNow()
        startBackgroundDelayPolling(connection.tripId)
    }

    fun onActionStillNotHere() {
        val currentMode = _uiState.value.trackingMode
        val conn = extractActiveConnection(currentMode) ?: return
        _uiState.update {
            it.copy(trackingMode = TripTrackingMode.WaitingInNegativeTime(conn))
        }
        updateRemainingSecondsNow()
    }

    fun onActionBoarded() {
        val currentMode = _uiState.value.trackingMode
        val conn = extractActiveConnection(currentMode) ?: return
        _uiState.update {
            it.copy(trackingMode = TripTrackingMode.BoardedCountingToDestination(conn))
        }
        updateRemainingSecondsNow()
    }

    fun onActionMissedConnection() {
        val currentMode = _uiState.value.trackingMode
        val missedConn = extractActiveConnection(currentMode) ?: return
        val lab = labSettingsFlow.value

        viewModelScope.launch {
            val allDepartures = repository.getDeparturesForStop(
                stopQuery = missedConn.stopName,
                lineFilter = missedConn.routeShortName,
                labSettings = lab,
                currentWeather = _uiState.value.weatherAndAqi
            )

            val nowMs = System.currentTimeMillis()
            val nextSameLineConn = allDepartures.firstOrNull {
                it.tripId != missedConn.tripId && it.effectiveDepartureEpochMs > nowMs + 15_000L
            } ?: missedConn.copy(
                tripId = "${missedConn.tripId}_NEXT",
                scheduledDepartureEpochMs = nowMs + (8 * 60_000L),
                predictedDepartureEpochMs = nowMs + (8 * 60_000L),
                scheduledArrivalDestinationEpochMs = nowMs + (24 * 60_000L),
                predictedArrivalDestinationEpochMs = nowMs + (24 * 60_000L),
                officialDelaySeconds = 0
            )

            val bikesDeferred = async {
                repository.getNearbySharedBikes(
                    lat = missedConn.stopLat,
                    lon = missedConn.stopLon,
                    apiKey = lab.golemioApiKey
                )
            }

            val transfersDeferred = async {
                if (lab.emergencyTransfer600m) {
                    repository.getEmergencyTransfersWithin600m(
                        originLat = missedConn.stopLat,
                        originLon = missedConn.stopLon,
                        targetDestination = missedConn.destinationName,
                        excludedLine = missedConn.routeShortName,
                        apiKey = lab.golemioApiKey
                    )
                } else emptyList()
            }

            _uiState.update {
                it.copy(
                    trackingMode = TripTrackingMode.MissedConnectionRescue(
                        missedConnection = missedConn,
                        nextConnection = nextSameLineConn,
                        nearbyBikes = bikesDeferred.await(),
                        emergencyTransfers = transfersDeferred.await()
                    )
                )
            }
            updateRemainingSecondsNow()
        }
    }

    fun stopTracking() {
        pollingJob?.cancel()
        _uiState.update { it.copy(trackingMode = TripTrackingMode.Idle) }
    }

    fun saveFavoriteConnection(connection: StopConnection, customTimeHHmm: String) {
        viewModelScope.launch {
            preferencesRepository.saveFavorite(
                stopName = connection.stopName,
                routeShortName = connection.routeShortName,
                headsign = connection.headsign,
                timeHHmm = customTimeHHmm
            )
        }
    }

    fun removeFavoriteConnection(id: String) {
        viewModelScope.launch {
            preferencesRepository.removeFavorite(id)
        }
    }

    fun selectFavorite(fav: FavoriteConnection) {
        _uiState.update {
            it.copy(
                stopSearchQuery = fav.stopName,
                lineFilterQuery = fav.routeShortName
            )
        }
        refreshStopData()
    }

    fun onMapLineFilterChanged(filter: String) {
        _uiState.update { it.copy(mapLineFilter = filter) }
        refreshMapLayersNow()
    }

    fun toggleParkingsLayer() {
        _uiState.update { it.copy(showParkingsLayer = !it.showParkingsLayer) }
    }

    fun toggleBikesLayer() {
        _uiState.update { it.copy(showBikesLayer = !it.showBikesLayer) }
    }

    private fun startLiveMapPolling() {
        mapPollingJob?.cancel()
        mapPollingJob = viewModelScope.launch {
            while (isActive) {
                refreshMapLayersNow()
                val intervalMs = if (labSettingsFlow.value.rawInputGatewayStream) 2500L else 6000L
                delay(intervalMs)
            }
        }
    }

    private fun refreshMapLayersNow() {
        viewModelScope.launch {
            val lab = labSettingsFlow.value
            val filter = _uiState.value.mapLineFilter
            val vehiclesDeferred = async { repository.getLiveVehicles(filter, lab) }
            val parkingsDeferred = async { repository.getParkingsPR(lab.golemioApiKey) }
            val bikesDeferred = async { repository.getNearbySharedBikes(50.0755, 14.4378, lab.golemioApiKey) }

            _uiState.update {
                it.copy(
                    liveVehicles = vehiclesDeferred.await(),
                    parkingsPR = parkingsDeferred.await(),
                    mapSharedBikes = bikesDeferred.await()
                )
            }
        }
    }

    fun onLabToggleChanged(key: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateToggle(key, enabled)
            refreshStopData()
            refreshMapLayersNow()
        }
    }

    fun onApiKeyUpdated(newKey: String) {
        viewModelScope.launch {
            preferencesRepository.updateApiKey(newKey)
            refreshStopData()
            refreshMapLayersNow()
        }
    }

    private fun startIndependentCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                updateRemainingSecondsNow()
                delay(200L)
            }
        }
    }

    private fun updateRemainingSecondsNow() {
        val nowMs = System.currentTimeMillis()
        val mode = _uiState.value.trackingMode

        val targetEpochMs: Long = when (mode) {
            is TripTrackingMode.CountingDownToDeparture -> mode.connection.effectiveDepartureEpochMs
            is TripTrackingMode.ReachedZeroActionPrompt -> mode.connection.effectiveDepartureEpochMs
            is TripTrackingMode.WaitingInNegativeTime -> mode.connection.effectiveDepartureEpochMs
            is TripTrackingMode.BoardedCountingToDestination -> mode.connection.effectiveDestinationArrivalEpochMs
            is TripTrackingMode.MissedConnectionRescue -> {
                (mode.nextConnection ?: mode.missedConnection).effectiveDepartureEpochMs
            }
            TripTrackingMode.Idle -> return
        }

        val diffSec = (targetEpochMs - nowMs) / 1000L
        _remainingSecondsFlow.value = diffSec

        if (mode is TripTrackingMode.CountingDownToDeparture && diffSec <= 0L) {
            _uiState.update {
                it.copy(trackingMode = TripTrackingMode.ReachedZeroActionPrompt(mode.connection))
            }
        }
    }

    private fun startBackgroundDelayPolling(tripId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(8_000L)
                val currentMode = _uiState.value.trackingMode
                val activeConn = extractActiveConnection(currentMode) ?: break
                val lab = labSettingsFlow.value

                val latestList = repository.getDeparturesForStop(
                    stopQuery = activeConn.stopName,
                    lineFilter = activeConn.routeShortName,
                    labSettings = lab,
                    currentWeather = _uiState.value.weatherAndAqi
                )

                val matchedFromApi = latestList.find { it.tripId == tripId }
                val updatedConnection = if (matchedFromApi != null) {
                    matchedFromApi
                } else if (currentMode is TripTrackingMode.WaitingInNegativeTime) {
                    activeConn.copy(
                        officialDelaySeconds = activeConn.officialDelaySeconds + 45
                    )
                } else {
                    activeConn
                }

                val nowMs = System.currentTimeMillis()
                val newRemainingSec = (updatedConnection.effectiveDepartureEpochMs - nowMs) / 1000L

                _uiState.update { state ->
                    val nextMode = when (currentMode) {
                        is TripTrackingMode.WaitingInNegativeTime -> {
                            if (newRemainingSec > 0L) {
                                TripTrackingMode.CountingDownToDeparture(updatedConnection)
                            } else {
                                TripTrackingMode.WaitingInNegativeTime(updatedConnection)
                            }
                        }
                        is TripTrackingMode.CountingDownToDeparture -> {
                            TripTrackingMode.CountingDownToDeparture(updatedConnection)
                        }
                        is TripTrackingMode.ReachedZeroActionPrompt -> {
                            TripTrackingMode.ReachedZeroActionPrompt(updatedConnection)
                        }
                        is TripTrackingMode.BoardedCountingToDestination -> {
                            TripTrackingMode.BoardedCountingToDestination(updatedConnection)
                        }
                        else -> currentMode
                    }
                    state.copy(trackingMode = nextMode)
                }
                updateRemainingSecondsNow()
            }
        }
    }

    private fun extractActiveConnection(mode: TripTrackingMode): StopConnection? = when (mode) {
        is TripTrackingMode.CountingDownToDeparture -> mode.connection
        is TripTrackingMode.ReachedZeroActionPrompt -> mode.connection
        is TripTrackingMode.WaitingInNegativeTime -> mode.connection
        is TripTrackingMode.BoardedCountingToDestination -> mode.connection
        is TripTrackingMode.MissedConnectionRescue -> mode.nextConnection ?: mode.missedConnection
        TripTrackingMode.Idle -> null
    }
}