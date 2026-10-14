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
            // Work held again since the stop was sent, or a newer start is queued: stay. stopSelfResult only
            // stops when this is the latest request, so a queued start still gets its startForeground.
            if (isIdle()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (stopSelfResult(startId)) running = false
            }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(this, intent?.getStringExtra(EXTRA_TEXT)))
        running = true
        // The work that asked for this start already ended before it arrived.
        if (isIdle()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (stopSelfResult(startId)) running = false
        }
        // Restarted empty after the process died, it would pin a notification for work that is gone.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    // Swiped away from Recents: the activity and its run are gone, so is the reason to stay.
    override fun onTaskRemoved(rootIntent: Intent?) {
        clearHolds()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Android 15 limits dataSync services to six hours a day and expects them to stop at once.
    override fun onTimeout(startId: Int, fgsType: Int) {
        clearHolds()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "research"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "xyz.fieldatlas.action.STOP_KEEPALIVE"
        private const val EXTRA_TEXT = "xyz.fieldatlas.extra.KEEPALIVE_TEXT"

        // Each piece of work that needs the process holds the service by name and releases it when it
        // ends, so the service stops when nothing needs it, whatever the screen shows.
        private val holds = linkedMapOf<String, String?>()
        @Volatile private var running = false

        @Synchronized private fun isIdle() = holds.isEmpty()

        @Synchronized fun hold(context: Context, key: String, text: String? = null) {
            holds[key] = text
            show(context.applicationContext, text)
        }

        @Synchronized fun release(context: Context, key: String) {
            // Nothing held under this name: another piece of work may still need the service.
            if (!holds.containsKey(key)) return
            holds.remove(key)
            if (holds.isEmpty()) stop(context.applicationContext) else show(context.applicationContext, holds.values.last())
        }

        @Synchronized private fun clearHolds() = holds.clear()

        private fun notification(context: Context, text: String?): Notification {
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text ?: "Researching an answer…")
                .setContentIntent(tap)
                .setOngoing(true)
                .build()
        }

        // A running service only needs its text changed; starting it again from the background is refused on
        // Android 12 and later, and that refusal must not crash the app (the work goes on while it is open).
        private fun show(context: Context, text: String?) {
            if (running) {
                runCatching { (context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(context, text)) }
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KeepAliveService::class.java).apply { text?.let { putExtra(EXTRA_TEXT, it) } },
                )
            }
        }

        fun stop(context: Context) {
            if (!running) return
            runCatching { context.startService(Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
