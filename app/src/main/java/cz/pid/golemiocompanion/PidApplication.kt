package cz.pid.golemiocompanion

import android.app.Application
import cz.pid.golemiocompanion.domain.model.PidStopDirectory
import org.osmdroid.config.Configuration
import java.io.File

class PidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PidStopDirectory.initialize(this)
        cz.pid.golemiocompanion.ui.map.RoadRouteHelper.initialize(this)

        // Inicializace osmdroid s persistentní diskovou mezipamětí pro mapové dlaždice
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        osmConfig.userAgentValue = "$packageName/2.0"

        // Nastavení diskové keše do dedikované složky v cache dir
        val tileCacheDir = File(cacheDir, "osmdroid/tiles")
        if (!tileCacheDir.exists()) {
            tileCacheDir.mkdirs()
        }
        osmConfig.osmdroidTileCache = tileCacheDir
        osmConfig.tileFileSystemCacheMaxBytes = 300L * 1024 * 1024 // 300 MB max
        osmConfig.tileFileSystemCacheTrimBytes = 250L * 1024 * 1024 // trim na 250 MB při překročení
        osmConfig.expirationExtendedDuration = 1000L * 60 * 60 * 24 * 30 // prodloužit platnost dlaždic o 30 dní offline
    }
}


