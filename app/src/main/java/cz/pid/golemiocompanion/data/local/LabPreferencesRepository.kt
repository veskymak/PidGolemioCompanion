package cz.pid.golemiocompanion.data.local

import android.content.Context
import android.content.SharedPreferences
import cz.pid.golemiocompanion.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LabSettings(
    val golemioApiKey: String = "",
    val cartoApiKey: String = "cb1_4ll2_1_f0afdd71b59d8a4f0f10563a",
    val enablePipCountdown: Boolean = true,
    val enableSmartDepartureAlerts: Boolean = true,
    val showExactSecondsInBoard: Boolean = false,
    val autoSelectNearestStopOnStart: Boolean = true,
    val showExtendedMapBadges: Boolean = false,
    val nearbyRadiusKm: Int = 10,
    val enableIslandNotification: Boolean = true
)

class LabPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pid_golemio_lab_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<LabSettings> = _settings.asStateFlow()

    private fun loadSettings(): LabSettings {
        val buildConfigKey = runCatching { BuildConfig.GOLEMIO_API_KEY }.getOrNull().orEmpty()
        val savedGolemioKey = prefs.getString(KEY_GOLEMIO_API_KEY, null)
        val resolvedGolemioKey = savedGolemioKey ?: buildConfigKey

        return LabSettings(
            golemioApiKey = resolvedGolemioKey,
            cartoApiKey = prefs.getString(KEY_CARTO_API_KEY, DEFAULT_CARTO_KEY) ?: DEFAULT_CARTO_KEY,
            enablePipCountdown = prefs.getBoolean(KEY_PIP_COUNTDOWN, true),
            enableSmartDepartureAlerts = prefs.getBoolean(KEY_SMART_ALERTS, true),
            showExactSecondsInBoard = prefs.getBoolean(KEY_EXACT_SECONDS_BOARD, false),
            autoSelectNearestStopOnStart = prefs.getBoolean(KEY_AUTO_NEAREST_STOP, true),
            showExtendedMapBadges = prefs.getBoolean(KEY_EXTENDED_MAP_BADGES, false),
            nearbyRadiusKm = prefs.getInt(KEY_NEARBY_RADIUS_KM, 10),
            enableIslandNotification = prefs.getBoolean(KEY_ISLAND_NOTIFICATION, true)
        )
    }

    fun setGolemioApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GOLEMIO_API_KEY, apiKey.trim()).apply()
        _settings.value = loadSettings()
    }

    fun setCartoApiKey(apiKey: String) {
        prefs.edit().putString(KEY_CARTO_API_KEY, apiKey.trim()).apply()
        _settings.value = loadSettings()
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

    fun setEnableIslandNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ISLAND_NOTIFICATION, enabled).apply()
        _settings.value = loadSettings()
    }

    companion object {
        const val DEFAULT_CARTO_KEY = "cb1_4ll2_1_f0afdd71b59d8a4f0f10563a"
        private const val KEY_GOLEMIO_API_KEY = "golemio_api_key"
        private const val KEY_CARTO_API_KEY = "carto_api_key"
        private const val KEY_PIP_COUNTDOWN = "enable_pip_countdown"
        private const val KEY_SMART_ALERTS = "enable_smart_alerts"
        private const val KEY_EXACT_SECONDS_BOARD = "show_exact_seconds_board"
        private const val KEY_AUTO_NEAREST_STOP = "auto_nearest_stop_on_start"
        private const val KEY_EXTENDED_MAP_BADGES = "show_extended_map_badges"
        private const val KEY_NEARBY_RADIUS_KM = "nearby_radius_km"
        private const val KEY_ISLAND_NOTIFICATION = "enable_island_notification"
    }
}