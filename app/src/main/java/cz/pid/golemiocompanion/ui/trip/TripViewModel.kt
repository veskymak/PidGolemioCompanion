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
    val trackedVehicleTelemetry: VehicleTrackingTelemetry? = null,
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
    val mapRouteFilter: String = "",
    val mapCenterLat: Double? = null,
    val mapCenterLon: Double? = null,
    val mapZoomLevel: Double = 14.0
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

        viewModelScope.launch {
            labPreferencesRepository.settings
                .map { it.golemioApiKey }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    refreshDeparturesNow()
                    refreshLiveVehiclesNow()
                }
        }
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
        val isStopping = _uiState.value.trackedDeparture?.id == departure.id
        val next = if (isStopping) null else departure

        _uiState.update { state ->
            val telemetry = if (next != null) {
                val matching = VehicleTelemetryHelper.findMatchingLiveVehicle(
                    departure = next,
                    targetStop = state.selectedStop,
                    liveVehicles = state.liveVehicles
                )
                matching?.let { veh ->
                    VehicleTelemetryHelper.calculateVehicleTelemetry(
                        vehicle = veh,
                        stop = state.selectedStop,
                        scheduledDepartureEpochMillis = next.exactDepartureEpochMillis
                    )
                }
            } else null
            state.copy(trackedDeparture = next, trackedVehicleTelemetry = telemetry)
        }

        // Okamžitě dotáhnout živá vozidla konkrétní sledované linky pro 100% přesnou shodu tripId
        if (next != null) {
            viewModelScope.launch {
                repository.getLiveVehicles(next.routeShortName).onSuccess { routeVehicles ->
                    _uiState.update { state ->
                        val currentTracked = state.trackedDeparture ?: return@update state
                        val mergedVehicles = (routeVehicles + state.liveVehicles).distinctBy { it.id }
                        val matching = VehicleTelemetryHelper.findMatchingLiveVehicle(
                            departure = currentTracked,
                            targetStop = state.selectedStop,
                            liveVehicles = mergedVehicles
                        )
                        val telemetry = matching?.let { veh ->
                            VehicleTelemetryHelper.calculateVehicleTelemetry(
                                vehicle = veh,
                                stop = state.selectedStop,
                                scheduledDepartureEpochMillis = currentTracked.exactDepartureEpochMillis
                            )
                        }
                        state.copy(liveVehicles = mergedVehicles, trackedVehicleTelemetry = telemetry)
                    }
                }
            }
        }
    }

    fun clearTrackedDeparture() {
        triggeredAlertThresholds.clear()
        _uiState.update { it.copy(trackedDeparture = null, trackedVehicleTelemetry = null) }
    }

    fun setMapRouteFilter(routeFilter: String) {
        _uiState.update { it.copy(mapRouteFilter = routeFilter) }
        refreshLiveVehiclesNow()
    }

    fun saveMapPosition(lat: Double, lon: Double, zoom: Double) {
        _uiState.update {
            it.copy(
                mapCenterLat = lat,
                mapCenterLon = lon,
                mapZoomLevel = zoom
            )
        }
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
                                (it.tripId != null && it.tripId == tracked.tripId) ||
                                    (it.routeShortName == tracked.routeShortName &&
                                        it.scheduledEpochMillis == tracked.scheduledEpochMillis)
                            } ?: tracked.takeIf {
                                val telem = state.trackedVehicleTelemetry
                                if (telem != null && telem.isAtStop) {
                                    true
                                } else {
                                    it.exactDepartureEpochMillis >= now
                                }
                            }
                        }
                        val telem = if (updatedTracked != null) {
                            val matching = VehicleTelemetryHelper.findMatchingLiveVehicle(
                                departure = updatedTracked,
                                targetStop = state.selectedStop,
                                liveVehicles = state.liveVehicles
                            )
                            matching?.let { veh ->
                                VehicleTelemetryHelper.calculateVehicleTelemetry(
                                    vehicle = veh,
                                    stop = state.selectedStop,
                                    scheduledDepartureEpochMillis = updatedTracked.exactDepartureEpochMillis
                                )
                            } ?: state.trackedVehicleTelemetry
                        } else null

                        state.copy(
                            departures = fresh,
                            trackedDeparture = updatedTracked,
                            trackedVehicleTelemetry = telem,
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
            val trackedRoute = _uiState.value.trackedDeparture?.routeShortName

            val mainVehicles = repository.getLiveVehicles(filter).getOrNull().orEmpty()
            val trackedVehicles = if (!trackedRoute.isNullOrBlank() && !trackedRoute.equals(filter.trim(), ignoreCase = true)) {
                repository.getLiveVehicles(trackedRoute).getOrNull().orEmpty()
            } else {
                emptyList()
            }

            if (mainVehicles.isNotEmpty() || trackedVehicles.isNotEmpty()) {
                val mergedVehicles = (trackedVehicles + mainVehicles).distinctBy { it.id }
                _uiState.update { state ->
                    val updatedTelemetry = state.trackedDeparture?.let { dep ->
                        val matching = VehicleTelemetryHelper.findMatchingLiveVehicle(
                            departure = dep,
                            targetStop = state.selectedStop,
                            liveVehicles = mergedVehicles
                        )
                        matching?.let { veh ->
                            VehicleTelemetryHelper.calculateVehicleTelemetry(
                                vehicle = veh,
                                stop = state.selectedStop,
                                scheduledDepartureEpochMillis = dep.exactDepartureEpochMillis
                            )
                        }
                    }
                    state.copy(liveVehicles = mergedVehicles, trackedVehicleTelemetry = updatedTelemetry)
                }
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
     * Pokud je sledovaný vůz přímo v zastávce, odpočet zůstane aktivní, dokud zastávku neopustí.
     */
    private fun startOneSecondCleanupAndAlertsTicker() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    val filtered = state.departures.filter {
                        it.exactDepartureEpochMillis >= now - 5_000L
                    }
                    val telem = state.trackedVehicleTelemetry
                    val activeTracked = state.trackedDeparture?.takeIf { dep ->
                        if (telem != null && telem.isAtStop) {
                            true
                        } else {
                            dep.exactDepartureEpochMillis >= now - 2_000L
                        }
                    }
                    val activeTelem = if (activeTracked == null) null else telem
                    if (filtered.size != state.departures.size || activeTracked != state.trackedDeparture || activeTelem != state.trackedVehicleTelemetry) {
                        state.copy(departures = filtered, trackedDeparture = activeTracked, trackedVehicleTelemetry = activeTelem)
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