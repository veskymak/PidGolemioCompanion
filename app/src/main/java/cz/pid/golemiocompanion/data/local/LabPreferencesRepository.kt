package cz.pid.golemiocompanion.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cz.pid.golemiocompanion.domain.model.FavoriteConnection
import cz.pid.golemiocompanion.domain.model.LabSettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pid_golemio_lab_prefs")

class LabPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEAD_RECKONING = booleanPreferencesKey("dead_reckoning_in_tunnels")
        val RAW_INPUT_GATEWAY = booleanPreferencesKey("raw_input_gateway_stream")
        val PHANTOM_AI_DELAY = booleanPreferencesKey("phantom_ai_delay_prediction")
        val FORCED_120HZ = booleanPreferencesKey("forced_120hz_aggressive_render")
        val EMERGENCY_TRANSFER_600M = booleanPreferencesKey("emergency_transfer_600m")
        val FLOATING_PIP = booleanPreferencesKey("floating_pip_mini_countdown")
        val GOLEMIO_API_KEY = stringPreferencesKey("golemio_api_key")
        val GOOGLE_MAPS_API_KEY = stringPreferencesKey("google_maps_api_key")
        val FAVORITES_RAW = stringSetPreferencesKey("favorite_connections_set")
    }

    val labSettingsFlow: Flow<LabSettingsState> = context.dataStore.data.map { prefs ->
        val storedMapsKey = prefs[Keys.GOOGLE_MAPS_API_KEY]
        val effectiveMapsKey = if (!storedMapsKey.isNullOrBlank()) {
            storedMapsKey
        } else {
            cz.pid.golemiocompanion.BuildConfig.MAPS_API_KEY
        }

        LabSettingsState(
            deadReckoningInTunnels = prefs[Keys.DEAD_RECKONING] ?: false,
            rawInputGatewayStream = prefs[Keys.RAW_INPUT_GATEWAY] ?: false,
            phantomAiDelayPrediction = prefs[Keys.PHANTOM_AI_DELAY] ?: false,
            forced120HzAggressiveRender = prefs[Keys.FORCED_120HZ] ?: false,
            emergencyTransfer600m = prefs[Keys.EMERGENCY_TRANSFER_600M] ?: true,
            floatingPipMiniCountdown = prefs[Keys.FLOATING_PIP] ?: false,
            golemioApiKey = prefs[Keys.GOLEMIO_API_KEY] ?: "",
            googleMapsApiKey = effectiveMapsKey
        )
    }

    val favoritesFlow: Flow<List<FavoriteConnection>> = context.dataStore.data.map { prefs ->
        val rawSet = prefs[Keys.FAVORITES_RAW] ?: setOf(
            "fav_1|Anděl|191|Letiště Václava Havla|08:15",
            "fav_2|Dejvická|119|Terminál 1|09:30",
            "fav_3|Budějovická|170|Jižní Město|17:10"
        )
        rawSet.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 5) {
                FavoriteConnection(
                    id = parts[0],
                    stopName = parts[1],
                    routeShortName = parts[2],
                    headsign = parts[3],
                    preferredDepartureTimeHHmm = parts[4]
                )
            } else null
        }.sortedBy { it.preferredDepartureTimeHHmm }
    }

    suspend fun updateToggle(keyName: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (keyName) {
                "dead_reckoning" -> prefs[Keys.DEAD_RECKONING] = enabled
                "raw_gateway" -> prefs[Keys.RAW_INPUT_GATEWAY] = enabled
                "phantom_ai" -> prefs[Keys.PHANTOM_AI_DELAY] = enabled
                "forced_120hz" -> prefs[Keys.FORCED_120HZ] = enabled
                "emergency_600m" -> prefs[Keys.EMERGENCY_TRANSFER_600M] = enabled
                "floating_pip" -> prefs[Keys.FLOATING_PIP] = enabled
            }
        }
    }

    suspend fun updateApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOLEMIO_API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateGoogleMapsApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GOOGLE_MAPS_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveFavorite(stopName: String, routeShortName: String, headsign: String, timeHHmm: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES_RAW]?.toMutableSet() ?: mutableSetOf()
            val id = "fav_${System.currentTimeMillis()}"
            current.add("$id|$stopName|$routeShortName|$headsign|$timeHHmm")
            prefs[Keys.FAVORITES_RAW] = current
        }
    }

    suspend fun removeFavorite(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES_RAW]?.toMutableSet() ?: return@edit
            current.removeAll { it.startsWith("$id|") }
            prefs[Keys.FAVORITES_RAW] = current
        }
    }
}