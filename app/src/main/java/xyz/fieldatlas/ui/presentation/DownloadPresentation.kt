package xyz.fieldatlas.ui.presentation

import xyz.fieldatlas.assets.DownloadItem
import xyz.fieldatlas.assets.DownloadQueueState

/**
 * One way of saying where a download is, used by Library's rows, the downloads bar and sheet, and the notification,
 * so the words are the same everywhere: "118 of 215 MB · 1 min left · then 1 more".
 */
internal fun downloadLine(status: DownloadQueueState, item: DownloadItem): String = when {
    status.waitingForWifi -> "Waiting for Wi-Fi · carries on by itself"
    status.checking -> "Checking the file…"
    else -> buildString {
        append(progressBytes(status.bytes, item.bytes))
        status.etaSeconds?.let { append(" · ").append(timeLeft(it)).append(" left") }
        if (status.queued.isNotEmpty()) append(" · then ").append(status.queued.size).append(" more")
    }
}

/** "118 of 215 MB": the part done is rounded down, so it never reads as finished before it is. */
internal fun progressBytes(done: Long, total: Long): String {
    if (total < 1_000_000_000) return "${done / 1_000_000} of ${formatAssetBytes(total)}"
    return "${String.format(java.util.Locale.US, "%.1f", done / 1_000_000_000.0)} of ${formatAssetBytes(total)}"
}

/** "20 s", "1 min", "1 h 5 min": rounded the way a person would say it. */
internal fun timeLeft(seconds: Long): String = when {
    seconds < 60 -> "${((seconds + 9) / 10 * 10).coerceAtLeast(10)} s"
    seconds < 3600 -> "${(seconds + 30) / 60} min"
    else -> "${seconds / 3600} h" + ((seconds % 3600 + 30) / 60).let { if (it == 0L) "" else " $it min" }
}

/** "2nd in line". */
internal fun inLine(position: Int): String {
    val n = position + 2  // the running download is first
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix in line"
}
