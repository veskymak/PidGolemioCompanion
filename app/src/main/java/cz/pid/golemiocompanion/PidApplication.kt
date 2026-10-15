package cz.pid.golemiocompanion

import android.app.Application
import cz.pid.golemiocompanion.domain.model.PidStopDirectory

class PidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PidStopDirectory.initialize(this)
    }
}

