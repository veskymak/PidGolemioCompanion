package cz.pid.golemiocompanion.ui.trip

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.pid.golemiocompanion.data.local.LabPreferencesRepository
import cz.pid.golemiocompanion.data.local.LabSettings
import cz.pid.golemiocompanion.data.repository.GolemioRepositoryImpl
import cz.pid.golemiocompanion.domain.model.*
import cz.pid.golemiocompanion.domain.repository.GolemioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TripUiState(
    val selectedStop: PidStop = PidStopDirectory.quickSelectStops.first(),
    val searchQuery: String = "",
    val searchSuggestions: List<PidStop> = emptyList(),
    val departures: List<DepartureInfo> = emptyList(),
    val trackedDeparture: DepartureInfo? = null,
    val nearbyStops: List<NearbyStop> = emptyList(),
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val isLocatingUser: Boolean = false,
    val locationStatusMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val liveVehicles: List<LiveVehiclePosition> = emptyList(),
    val parkingLots: List<ParkingLotInfo> = emptyList(),
    val sharedBikes: List<SharedBikeInfo> = emptyList(),
    val mapRouteFilter: String = ""
)

class TripViewModel(
    private val repository: GolemioRepository = GolemioRepositoryImpl(),
    private val labPreferencesRepository: LabPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripUiState())
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    val labSettings: StateFlow<LabSettings> = labPreferencesRepository.settings

    private var departuresPollingJob: Job? = null
    private var mapPollingJob: Job? = null
    private var hasAutoSelectedNearestStop = false
    private val triggeredAlertThresholds = mutableSetOf<String>()

    init {
        startDeparturesPolling()
        startOneSecondCleanupAndAlertsTicker()
        startLiveMapPolling()
    }

    fun onSearchQueryChanged(query: String) {
        val suggestions = if (query.isBlank()) {
            emptyList()
        } else {
            PidStopDirectory.searchStops(query).take(15)
        }
        _uiState.update {
            it.copy(searchQuery = query, searchSuggestions = suggestions)
        }
    }

    fun selectStop(stop: PidStop) {
        _uiState.update {
            it.copy(
                selectedStop = stop,
                searchQuery = "",
                searchSuggestions = emptyList(),
                errorMessage = null
            )
        }
        refreshDeparturesNow()
    }

    fun submitCustomStopQuery() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isEmpty()) return
        val match = _uiState.value.searchSuggestions.firstOrNull()
            ?: PidStopDirectory.findStopOrCreateCustom(q)
        selectStop(match)
    }

    fun toggleTrackDeparture(departure: DepartureInfo) {
        triggeredAlertThresholds.clear()
        _uiState.update { state ->
            val next = if (state.trackedDeparture?.id == departure.id) null else departure
            state.copy(trackedDeparture = next)
        }
    }

    fun clearTrackedDeparture() {
        triggeredAlertThresholds.clear()
        _uiState.update { it.copy(trackedDeparture = null) }
    }

    fun setMapRouteFilter(routeFilter: String) {
        _uiState.update { it.copy(mapRouteFilter = routeFilter) }
        refreshLiveVehiclesNow()
    }

    /**
     * Získá aktuální polohu při startu aplikace nebo po kliknutí na tlačítko GPS,
     * spočítá nejbližší zastávky a volitelně automaticky vybere tu nejbližší.
     */
    @SuppressLint("MissingPermission")
    fun fetchDeviceLocationAndRecommendStops(context: Context, forceSelectNearest: Boolean = false) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        _uiState.update {
            it.copy(isLocatingUser = true, locationStatusMessage = "Zjišťuji GPS polohu…")
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLastLocation: Location? = null
        for (provider in providers) {
            val loc = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null) {
                if (bestLastLocation == null || loc.time > bestLastLocation.time) {
                    bestLastLocation = loc
                }
            }
        }

        if (bestLastLocation != null) {
            applyUserLocation(
                lat = bestLastLocation.latitude,
                lon = bestLastLocation.longitude,
                forceSelectNearest = forceSelectNearest
            )
        }

        val activeProvider = when {
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (activeProvider == null) {
            _uiState.update {
                it.copy(
                    isLocatingUser = false,
                    locationStatusMessage = if (bestLastLocation != null) null else "GPS je vypnutá"
                )
            }
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                applyUserLocation(
                    lat = location.latitude,
                    lon = location.longitude,
                    forceSelectNearest = forceSelectNearest
                )
                runCatching { locationManager.removeUpdates(this) }
            }

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        runCatching {
            locationManager.requestSingleUpdate(activeProvider, listener, context.mainLooper)
        }
    }

    private fun applyUserLocation(lat: Double, lon: Double, forceSelectNearest: Boolean) {
        val radiusMeters = labSettings.value.nearbyRadiusKm * 1000
        val nearest = PidStopDirectory.findNearestStops(
            userLat = lat,
            userLon = lon,
            maxRadiusMeters = radiusMeters,
            limit = 8
        )

        val shouldAutoSelect = forceSelectNearest ||
            (!hasAutoSelectedNearestStop && labSettings.value.autoSelectNearestStopOnStart)

        val newSelectedStop = if (shouldAutoSelect && nearest.isNotEmpty()) {
            hasAutoSelectedNearestStop = true
            nearest.first().stop
        } else {
            _uiState.value.selectedStop
        }

        val stopChanged = newSelectedStop.golemioPrimaryName != _uiState.value.selectedStop.golemioPrimaryName

        _uiState.update {
            it.copy(
                userLatitude = lat,
                userLongitude = lon,
                nearbyStops = nearest,
                selectedStop = newSelectedStop,
                isLocatingUser = false,
                locationStatusMessage = null
            )
        }

        if (stopChanged) {
            refreshDeparturesNow()
        }
    }

    fun refreshDeparturesNow() {
        viewModelScope.launch {
            val stop = _uiState.value.selectedStop
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getDeparturesForStop(stop)
                .onSuccess { list ->
                    val now = System.currentTimeMillis()
                    val fresh = list.filter { it.exactDepartureEpochMillis >= now - 5_000L }

                    _uiState.update { state ->
                        val updatedTracked = state.trackedDeparture?.let { tracked ->
                            fresh.firstOrNull {
                                it.routeShortName == tracked.routeShortName &&
                                    it.scheduledEpochMillis == tracked.scheduledEpochMillis
                            } ?: tracked.takeIf { it.exactDepartureEpochMillis >= now }
                        }
                        state.copy(
                            departures = fresh,
                            trackedDeparture = updatedTracked,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Nepodařilo se načíst odjezdy: ${err.localizedMessage ?: "Chyba sítě"}"
                        )
                    }
                }
        }
    }

    fun refreshLiveVehiclesNow() {
        viewModelScope.launch {
            val filter = _uiState.value.mapRouteFilter
            repository.getLiveVehicles(filter).onSuccess { vehicles ->
                _uiState.update { it.copy(liveVehicles = vehicles) }
            }
            repository.getParkingLots().onSuccess { lots ->
                _uiState.update { it.copy(parkingLots = lots) }
            }
            repository.getSharedBikes().onSuccess { bikes ->
                _uiState.update { it.copy(sharedBikes = bikes) }
            }
        }
    }

    private fun startDeparturesPolling() {
        departuresPollingJob?.cancel()
        departuresPollingJob = viewModelScope.launch {
            while (true) {
                refreshDeparturesNow()
                delay(15_000L)
            }
        }
    }

    /**
     * Každou sekundu odfiltruje spoje, které právě odjely,
     * aby na obrazovce nikdy nezůstávaly staré spoje s nápisem "< 1 min".
     */
    private fun startOneSecondCleanupAndAlertsTicker() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    val filtered = state.departures.filter {
                        it.exactDepartureEpochMillis >= now - 5_000L
                    }
                    val activeTracked = state.trackedDeparture?.takeIf {
                        it.exactDepartureEpochMillis >= now - 2_000L
                    }
                    if (filtered.size != state.departures.size || activeTracked != state.trackedDeparture) {
                        state.copy(departures = filtered, trackedDeparture = activeTracked)
                    } else {
                        state
                    }
                }
                delay(1_000L)
            }
        }
    }

    private fun startLiveMapPolling() {
        mapPollingJob?.cancel()
        mapPollingJob = viewModelScope.launch {
            while (true) {
                refreshLiveVehiclesNow()
                delay(10_000L)
            }
        }
    }

    fun checkAndTriggerHapticAlert(context: Context, remainingSeconds: Long) {
        if (!labSettings.value.enableSmartDepartureAlerts) return
        val trackedId = _uiState.value.trackedDeparture?.id ?: return

        val thresholdKey = when (remainingSeconds) {
            180L -> "${trackedId}_180"
            60L -> "${trackedId}_60"
            30L -> "${trackedId}_30"
            else -> null
        } ?: return

        if (triggeredAlertThresholds.add(thresholdKey)) {
            vibrateDevice(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun vibrateDevice(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 250), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        }
    }
}