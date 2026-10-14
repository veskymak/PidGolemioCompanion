package cz.pid.golemiocompanion.ui.trip

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.pid.golemiocompanion.data.local.LabPreferencesRepository
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.domain.repository.GolemioRepository
import kotlinx.coroutines.CancellationException
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
    val mapLineFilter: String = "",
    val departuresError: String? = null,
    val isLiveData: Boolean = false,
    val isApiKeyConfigured: Boolean = false,
    val stopSuggestions: List<PidStopSuggestion> = emptyList(),
    val selectedVehicle: LiveVehicleMarker? = null,
    val selectedVehicleUpcomingStops: List<VehicleUpcomingStop> = emptyList(),
    val isLoadingVehicleDetail: Boolean = false
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
    private var refreshJob: Job? = null

    init {
        observeApiKeyChanges()
        startIndependentCountdownTicker()
        startLiveMapPolling()
    }

    /**
     * Always read the latest persisted settings directly from DataStore.
     * [labSettingsFlow].value may still hold a stale (or initial empty) value right after
     * the API key is saved or right after app start.
     */
    private suspend fun currentLabSettings(): LabSettingsState =
        preferencesRepository.labSettingsFlow.first()

    /** Reloads departures + map layers on start and whenever the API key changes. */
    private fun observeApiKeyChanges() {
        viewModelScope.launch {
            preferencesRepository.labSettingsFlow
                .map { it.golemioApiKey }
                .distinctUntilChanged()
                .collect { apiKey ->
                    _uiState.update { it.copy(isApiKeyConfigured = apiKey.isNotBlank()) }
                    refreshStopData()
                    refreshMapLayersNow()
                }
        }
    }

    private var stopQueryDebounceJob: Job? = null

    fun onStopQueryChanged(query: String) {
        val trimmed = query.trim()
        val suggestions = if (trimmed.length >= 2) {
            PidStopDirectory.search(trimmed)
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                stopSearchQuery = query,
                stopSuggestions = suggestions
            )
        }

        stopQueryDebounceJob?.cancel()
        if (trimmed.length >= 2) {
            stopQueryDebounceJob = viewModelScope.launch {
                delay(3500L) // Automatické spuštění načtení po cca 3.5s nečinnosti po psaní
                refreshStopData()
            }
        }
    }

    fun onSelectSuggestedStop(suggestedStopName: String) {
        stopQueryDebounceJob?.cancel()
        _uiState.update {
            it.copy(
                stopSearchQuery = suggestedStopName,
                stopSuggestions = emptyList()
            )
        }
        refreshStopData()
    }

    fun onLineFilterChanged(line: String) {
        _uiState.update { it.copy(lineFilterQuery = line) }
    }

    fun refreshStopData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, departuresError = null) }
            val currentState = _uiState.value
            val lab = currentLabSettings()

            val weather = repository.getAirQualityAndWeather(50.0715, 14.4035, lab.golemioApiKey)

            val result = runCatching {
                repository.getDeparturesForStop(
                    stopQuery = currentState.stopSearchQuery,
                    lineFilter = currentState.lineFilterQuery,
                    labSettings = lab,
                    currentWeather = weather
                )
            }
            if (result.exceptionOrNull() is CancellationException) return@launch

            val connections = result.getOrDefault(emptyList())
            val error = result.exceptionOrNull()?.let { it.message ?: "Neznámá chyba při načítání odjezdů." }
                ?: if (lab.golemioApiKey.isNotBlank() && connections.isEmpty()) {
                    if (currentState.lineFilterQuery.isNotBlank()) {
                        "Pro zastávku „${currentState.stopSearchQuery}“ s filtrem linky „${currentState.lineFilterQuery}“ nebyly nalezeny žádné odjezdy."
                    } else {
                        "Pro zastávku „${currentState.stopSearchQuery}“ nejsou v nejbližších hodinách žádné odjezdy."
                    }
                } else null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    departures = connections,
                    weatherAndAqi = weather,
                    departuresError = error,
                    isLiveData = lab.golemioApiKey.isNotBlank() && result.isSuccess && connections.isNotEmpty(),
                    isApiKeyConfigured = lab.golemioApiKey.isNotBlank()
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
        viewModelScope.launch {
            val lab = currentLabSettings()
            val allDepartures = try {
                repository.getDeparturesForStop(
                    stopQuery = missedConn.stopName,
                    lineFilter = missedConn.routeShortName,
                    labSettings = lab,
                    currentWeather = _uiState.value.weatherAndAqi
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }

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
                val intervalMs = if (labSettingsFlow.value.rawInputGatewayStream) 4000L else 15000L
                delay(intervalMs)
            }
        }
    }

    private fun refreshMapLayersNow() {
        viewModelScope.launch {
            val lab = currentLabSettings()
            val filter = _uiState.value.mapLineFilter
            val vehiclesDeferred = async { repository.getLiveVehicles(filter, lab) }
            val parkingsDeferred = async {
                if (_uiState.value.parkingsPR.isEmpty()) repository.getParkingsPR(lab.golemioApiKey)
                else _uiState.value.parkingsPR
            }
            val bikesDeferred = async {
                if (_uiState.value.mapSharedBikes.isEmpty()) repository.getNearbySharedBikes(50.0755, 14.4378, lab.golemioApiKey)
                else _uiState.value.mapSharedBikes
            }

            val updatedVehicles = vehiclesDeferred.await()
            val updatedParkings = parkingsDeferred.await()
            val updatedBikes = bikesDeferred.await()

            _uiState.update { state ->
                val updatedSelected = if (state.selectedVehicle != null) {
                    updatedVehicles.find { it.tripId == state.selectedVehicle.tripId } ?: state.selectedVehicle
                } else null
                state.copy(
                    liveVehicles = updatedVehicles,
                    parkingsPR = updatedParkings,
                    mapSharedBikes = updatedBikes,
                    selectedVehicle = updatedSelected
                )
            }
        }
    }

    fun selectVehicleForDetail(tripId: String?) {
        if (tripId.isNullOrBlank()) {
            dismissVehicleDetail()
            return
        }
        val vehicle = _uiState.value.liveVehicles.find { it.tripId == tripId }
            ?: return
        _uiState.update {
            it.copy(
                selectedVehicle = vehicle,
                selectedVehicleUpcomingStops = emptyList(),
                isLoadingVehicleDetail = vehicle.isTracking
            )
        }
        if (vehicle.isTracking) {
            viewModelScope.launch {
                val lab = currentLabSettings()
                val stops = repository.getVehicleUpcomingStops(vehicle, lab.golemioApiKey)
                _uiState.update {
                    if (it.selectedVehicle?.tripId == tripId) {
                        it.copy(
                            selectedVehicleUpcomingStops = stops,
                            isLoadingVehicleDetail = false
                        )
                    } else it
                }
            }
        }
    }

    fun dismissVehicleDetail() {
        _uiState.update {
            it.copy(
                selectedVehicle = null,
                selectedVehicleUpcomingStops = emptyList(),
                isLoadingVehicleDetail = false
            )
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
            // refreshStopData() reads the key straight from DataStore, so it sees the new value.
            refreshStopData()
            refreshMapLayersNow()
        }
    }

    fun onGoogleMapsApiKeyUpdated(newKey: String) {
        viewModelScope.launch {
            preferencesRepository.updateGoogleMapsApiKey(newKey)
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

        if (mode is TripTrackingMode.CountingDownToDeparture && diffSec <= -180L) {
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
                val lab = currentLabSettings()

                val latestList = try {
                    repository.getDeparturesForStop(
                        stopQuery = activeConn.stopName,
                        lineFilter = activeConn.routeShortName,
                        labSettings = lab,
                        currentWeather = _uiState.value.weatherAndAqi
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }

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