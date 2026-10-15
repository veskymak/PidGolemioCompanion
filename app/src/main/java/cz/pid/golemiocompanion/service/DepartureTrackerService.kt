package cz.pid.golemiocompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cz.pid.golemiocompanion.MainActivity
import cz.pid.golemiocompanion.R

class DepartureTrackerService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIF_ID = 404
        private const val CHANNEL_ID = "departure_island_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val line = intent.getStringExtra("LINE") ?: "Bus"
                val dest = intent.getStringExtra("DESTINATION") ?: ""
                val targetTime = intent.getLongExtra("TARGET_TIME", System.currentTimeMillis())

                createNotificationChannel()
                val notification = buildIslandNotification(line, dest, targetTime)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIF_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIF_ID, notification)
                }
            }
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildIslandNotification(line: String, dest: String, targetTime: Long): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle("Linka $line → $dest")
            .setContentText("Odjezd za")
            .setContentIntent(pendingIntent)
            // Klíčové pro HyperOS Dynamic/Super Island:
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Vestavěný odpočet, který si HyperOS vykresluje přímo do bubliny u kamery:
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(targetTime)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Živý odpočet spoje (HyperOS Island)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zobrazuje živý odpočet odjezdu spoje v notifikační liště a ostrůvku"
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

