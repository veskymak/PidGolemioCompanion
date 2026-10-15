package cz.pid.golemiocompanion.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LabSettings(
    val enablePipCountdown: Boolean = true,
    val enableSmartDepartureAlerts: Boolean = true,
    val showExactSecondsInBoard: Boolean = false,
    val autoSelectNearestStopOnStart: Boolean = true,
    val showExtendedMapBadges: Boolean = false,
    val nearbyRadiusKm: Int = 10
)

class LabPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pid_golemio_lab_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<LabSettings> = _settings.asStateFlow()

    private fun loadSettings(): LabSettings {
        return LabSettings(
            enablePipCountdown = prefs.getBoolean(KEY_PIP_COUNTDOWN, true),
            enableSmartDepartureAlerts = prefs.getBoolean(KEY_SMART_ALERTS, true),
            showExactSecondsInBoard = prefs.getBoolean(KEY_EXACT_SECONDS_BOARD, false),
            autoSelectNearestStopOnStart = prefs.getBoolean(KEY_AUTO_NEAREST_STOP, true),
            showExtendedMapBadges = prefs.getBoolean(KEY_EXTENDED_MAP_BADGES, false),
            nearbyRadiusKm = prefs.getInt(KEY_NEARBY_RADIUS_KM, 10)
        )
    }

    fun setEnablePipCountdown(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIP_COUNTDOWN, enabled).apply()
        _settings.value = loadSettings()
    }

    fun setEnableSmartDepartureAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_ALERTS, enabled).apply()
        _settings.value = loadSettings()
    }

    fun setShowExactSecondsInBoard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXACT_SECONDS_BOARD, enabled).apply()
        _settings.value = loadSettings()
    }

    fun setAutoSelectNearestStopOnStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_NEAREST_STOP, enabled).apply()
        _settings.value = loadSettings()
    }

    fun setShowExtendedMapBadges(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXTENDED_MAP_BADGES, enabled).apply()
        _settings.value = loadSettings()
    }

    fun setNearbyRadiusKm(radiusKm: Int) {
        prefs.edit().putInt(KEY_NEARBY_RADIUS_KM, radiusKm.coerceIn(1, 30)).apply()
        _settings.value = loadSettings()
    }

    companion object {
        private const val KEY_PIP_COUNTDOWN = "enable_pip_countdown"
        private const val KEY_SMART_ALERTS = "enable_smart_alerts"
        private const val KEY_EXACT_SECONDS_BOARD = "show_exact_seconds_board"
        private const val KEY_AUTO_NEAREST_STOP = "auto_nearest_stop_on_start"
        private const val KEY_EXTENDED_MAP_BADGES = "show_extended_map_badges"
        private const val KEY_NEARBY_RADIUS_KM = "nearby_radius_km"
    }
}