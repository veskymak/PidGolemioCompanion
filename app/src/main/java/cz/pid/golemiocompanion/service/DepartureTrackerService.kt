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
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import cz.pid.golemiocompanion.MainActivity
import cz.pid.golemiocompanion.R

class DepartureTrackerService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIF_ID = 404
        private const val CHANNEL_ID = "departure_tracking_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val line = intent.getStringExtra("LINE") ?: "Bus"
                val dest = intent.getStringExtra("DESTINATION") ?: ""
                val targetTime = intent.getLongExtra("TARGET_TIME", System.currentTimeMillis())

                createNotificationChannel()
                val notification = buildTrackingNotification(line, dest, targetTime)

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

    private fun buildTrackingNotification(line: String, dest: String, targetTime: Long): Notification {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DepartureTrackerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remainingMillis = (targetTime - System.currentTimeMillis()).coerceAtLeast(0L)
        val chronometerBase = SystemClock.elapsedRealtime() + remainingMillis

        // 1. Zmenšený náhled notifikace (v liště)
        val remoteCollapsed = RemoteViews(packageName, R.layout.notification_departure_island).apply {
            setTextViewText(R.id.notif_line_badge, line)
            setTextViewText(R.id.notif_destination, "směr $dest")
            setTextViewText(R.id.notif_status_subtitle, "PID Lítačka • Živý odpočet")
            setChronometer(R.id.notif_chronometer, chronometerBase, null, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setChronometerCountDown(R.id.notif_chronometer, true)
            }
        }

        // 2. Rozbalený náhled notifikace
        val remoteExpanded = RemoteViews(packageName, R.layout.notification_departure_island_expanded).apply {
            setTextViewText(R.id.notif_line_badge_exp, line)
            setTextViewText(R.id.notif_destination_exp, "směr $dest")
            setChronometer(R.id.notif_chronometer_exp, chronometerBase, null, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setChronometerCountDown(R.id.notif_chronometer_exp, true)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle("Linka $line → $dest")
            .setContentText("Odjezd za")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(targetTime)
            .setColor(0xFF0284C7.toInt())
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteCollapsed)
            .setCustomBigContentView(remoteExpanded)
            .addAction(0, "Zrušit sledování", stopPendingIntent)
            .addAction(0, "Otevřít spoj", pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Živý odpočet spoje v notifikaci",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zobrazuje živý odpočet odjezdu sledovaného spoje v notifikační liště"
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
