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
        val downloads = (application as FieldAtlasApplication).container.backgroundDownloads
        startForeground(NOTIFICATION_ID, notification(downloads.state.value))
        if (intent?.action == ACTION_CANCEL) downloads.cancel()
        if (observer == null) {
            observer = scope.launch {
                var lastShown: Pair<AssetDownloadKind?, Int>? = null
                downloads.state.collect { status ->
                    if (!status.active) {
                        stopSelfResult(latestStartId)
                        return@collect
                    }
                    val percent = if (status.totalBytes > 0) {
                        ((status.downloadedBytes * 100) / status.totalBytes).coerceIn(0, 100).toInt()
                    } else 0
                    val shown = status.kind to percent
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
        (application as FieldAtlasApplication).container.backgroundDownloads.cancel(keepPending = true)
        stopSelf()
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(status: AssetDownloadStatus): Notification {
        val title = when (status.kind) {
            AssetDownloadKind.MODEL -> "Downloading model"
            AssetDownloadKind.KNOWLEDGE -> "Downloading ${status.pack?.title ?: "collection"}"
            null -> "Preparing download"
        }
        val installing = status.totalBytes > 0 && status.downloadedBytes >= status.totalBytes
        val detail = if (installing) "Verifying and installing…" else
            "${status.downloadedBytes / 1_000_000} / ${status.totalBytes / 1_000_000} MB"
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, AssetDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(tap)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100,
                if (status.totalBytes > 0) ((status.downloadedBytes * 100) / status.totalBytes).coerceIn(0, 100).toInt() else 0,
                installing || status.totalBytes <= 0)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Pause", cancel,
            ).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "offline-downloads"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_CANCEL = "xyz.fieldatlas.action.CANCEL_ASSET_DOWNLOAD"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context,
                Intent(context, AssetDownloadService::class.java))
        }
    }
}
