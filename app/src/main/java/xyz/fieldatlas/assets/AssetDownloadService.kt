package xyz.fieldatlas.assets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.fieldatlas.FieldAtlasApplication
import xyz.fieldatlas.MainActivity
import xyz.fieldatlas.R
import xyz.fieldatlas.ui.presentation.downloadLine

/** Visible, user-started data transfer while Setup/Library are in the background. */
class AssetDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Offline downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        running = true
        val container = (application as FieldAtlasApplication).container
        val downloads = container.downloads
        startForeground(NOTIFICATION_ID, notification(downloads.state.value))
        when (intent?.action) {
            ACTION_CANCEL -> downloads.queue.pause()
            ACTION_PAUSE_ALL -> downloads.queue.pauseAll()
        }
        if (observer == null) {
            observer = scope.launch {
                var lastShown: Triple<String?, Int, Boolean>? = null
                // The queue adopts a download resumed at launch a moment after it starts; until then the
                // background transfer alone says there is work.
                kotlinx.coroutines.flow.combine(downloads.state, container.backgroundDownloads.state) { queue, background -> queue to background }
                    .collect { (status, background) ->
                        if (status.active == null && !background.active) {
                            stopSelfResult(latestStartId)
                            return@collect
                        }
                        val item = status.active ?: return@collect
                        val percent = ((status.bytes * 100) / item.bytes.coerceAtLeast(1)).coerceIn(0, 100).toInt()
                        val shown = Triple(item.key, percent, status.waitingForWifi)
                        if (shown != lastShown) {
                            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                                .notify(NOTIFICATION_ID, notification(status))
                            lastShown = shown
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android's limit for background transfers: stop, keeping what was saved so it resumes on the next launch.
        val container = (application as FieldAtlasApplication).container
        container.downloads.queue.pauseAll()
        container.backgroundDownloads.cancel(keepPending = true)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(status: DownloadQueueState): Notification {
        val item = status.active
        val update = item is DownloadItem.Knowledge &&
            (application as FieldAtlasApplication).container.packs.value.any { it.id == item.pack.id && it.version != item.pack.version }
        val title = when {
            item == null -> "Preparing download"
            status.waitingForWifi -> "Waiting for Wi-Fi: ${item.title}"
            update -> "Updating ${item.title}"
            else -> "Downloading ${item.title}"
        }
        val text = item?.let { downloadLine(status, it) } ?: ""
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pause = PendingIntent.getService(
            this, 1, Intent(this, AssetDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseAll = PendingIntent.getService(
            this, 2, Intent(this, AssetDownloadService::class.java).setAction(ACTION_PAUSE_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, item?.let { ((status.bytes * 100) / it.bytes.coerceAtLeast(1)).coerceIn(0, 100).toInt() } ?: 0,
                item == null || status.checking)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Pause", pause).build())
        if (status.queued.isNotEmpty()) builder.addAction(Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Pause all", pauseAll).build())
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "offline-downloads"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_CANCEL = "xyz.fieldatlas.action.CANCEL_ASSET_DOWNLOAD"
        private const val ACTION_PAUSE_ALL = "xyz.fieldatlas.action.PAUSE_ALL_DOWNLOADS"
        private const val READY_ID_BASE = 2000

        /** True between the service starting and stopping; a second start would be refused from the background. */
        @Volatile var running: Boolean = false
            private set

        /** "Vegan places is on your phone": posted when a download finishes while the app is out of sight. */
        fun notifyReady(context: Context, item: DownloadItem) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Offline downloads", NotificationManager.IMPORTANCE_LOW))
            val tap = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val detail = when (item) {
                is DownloadItem.Knowledge -> "Checked and switched on for research."
                is DownloadItem.Model -> "Checked and ready to write answers."
                DownloadItem.Voice -> "Answers can now be read aloud in this voice."
            }
            runCatching {
                manager.notify(READY_ID_BASE + (item.key.hashCode() and 0xfff), Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("${item.title} is on your phone")
                    .setContentText(detail)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build())
            }
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context,
                Intent(context, AssetDownloadService::class.java))
        }
    }
}
