package xyz.fieldatlas.ui.research

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import xyz.fieldatlas.MainActivity
import xyz.fieldatlas.R

/**
 * Foreground service that keeps the process alive while an answer is being researched, so
 * backgrounding the app (to check email, switch tasks...) no longer kills a run in flight —
 * previously MainActivity.onStop cancelled the research outright. The model stays loaded and
 * tokens keep streaming; the notification disappears the moment the run finishes.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Research", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Researching an answer…")
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "research"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "xyz.fieldatlas.action.STOP_KEEPALIVE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, KeepAliveService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
