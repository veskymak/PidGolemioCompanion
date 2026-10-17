package cz.pid.golemiocompanion.ui.map

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pomocník pro výpočet skutečné silniční trasy mezi zastávkami / vozidlem pomocí OSRM,
 * aby modrá čára na mapě kopírovala silnice a nevedla vzduchem přes budovy.
 */
object RoadRouteHelper {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val routeCache = object : LruCache<String, List<GeoPoint>>(150) {}

    private fun buildCacheKey(waypoints: List<GeoPoint>): String {
        return waypoints.joinToString(";") { pt ->
            String.format(Locale.US, "%.4f,%.4f", pt.longitude, pt.latitude)
        }
    }

    fun getCachedRoute(waypoints: List<GeoPoint>): List<GeoPoint>? {
        if (waypoints.size < 2) return waypoints
        return routeCache.get(buildCacheKey(waypoints))
    }

    suspend fun fetchRoadRoute(waypoints: List<GeoPoint>): List<GeoPoint> {
        if (waypoints.size < 2) return waypoints

        // Omezíme maximální počet průjezdních bodů pro OSRM dotaz na 25
        val effectiveWaypoints = if (waypoints.size > 25) {
            val step = (waypoints.size - 1).toDouble() / 24.0
            (0..24).map { i ->
                val idx = (i * step).toInt().coerceIn(0, waypoints.lastIndex)
                waypoints[idx]
            }.distinct()
        } else {
            waypoints
        }

        val key = buildCacheKey(effectiveWaypoints)
        routeCache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val coordsParam = effectiveWaypoints.joinToString(";") { pt ->
                    String.format(Locale.US, "%.5f,%.5f", pt.longitude, pt.latitude)
                }
                val url = "https://router.project-osrm.org/route/v1/driving/$coordsParam?overview=full&geometries=geojson"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PidGolemioCompanion/2.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching effectiveWaypoints
                    val bodyStr = response.body?.string() ?: return@runCatching effectiveWaypoints
                    val json = JSONObject(bodyStr)
                    val routes = json.optJSONArray("routes") ?: return@runCatching effectiveWaypoints
                    if (routes.length() == 0) return@runCatching effectiveWaypoints

                    val geometry = routes.getJSONObject(0).optJSONObject("geometry")
                        ?: return@runCatching effectiveWaypoints
                    val coordinates = geometry.optJSONArray("coordinates")
                        ?: return@runCatching effectiveWaypoints

                    val points = ArrayList<GeoPoint>(coordinates.length())
                    for (i in 0 until coordinates.length()) {
                        val pair = coordinates.optJSONArray(i) ?: continue
                        if (pair.length() >= 2) {
                            val lon = pair.getDouble(0)
                            val lat = pair.getDouble(1)
                            points.add(GeoPoint(lat, lon))
                        }
                    }

                    if (points.size >= 2) {
                        routeCache.put(key, points)
                        points
                    } else {
                        effectiveWaypoints
                    }
                }
            }.getOrElse { effectiveWaypoints }
        }
    }
}
