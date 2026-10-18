package xyz.fieldatlas.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.AppDownloads
import xyz.fieldatlas.assets.DownloadItem
import xyz.fieldatlas.assets.DownloadQueueState
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.ui.presentation.downloadLine
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.presentation.inLine
import xyz.fieldatlas.ui.presentation.knowledgeCategory
import xyz.fieldatlas.ui.presentation.timeLeft
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasInfoSheet
import xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton
import xyz.fieldatlas.ui.theme.FieldAtlasProgressBar
import xyz.fieldatlas.ui.theme.FieldAtlasSwitch
import xyz.fieldatlas.ui.theme.TileTone

/** The app's downloads (models, collections, the voice), for any screen that offers or shows one. */
val LocalDownloads = compositionLocalOf<AppDownloads?> { null }

/** Where one downloadable thing stands, as its row shows it. */
internal sealed interface RowState {
    /** Not on the phone; [saved] bytes of a paused download are kept to resume. */
    data class Get(val saved: Long) : RowState
    data class Running(val line: String, val fraction: Float) : RowState
    data object Checking : RowState
    data class Waiting(val saved: Long) : RowState
    data class Queued(val position: Int, val after: String?) : RowState
    data class Failed(val message: String, val saved: Long) : RowState
    data object Done : RowState
}

internal fun rowState(item: DownloadItem, status: DownloadQueueState, installed: Boolean, saved: Long): RowState = when {
    installed -> RowState.Done
    status.isActive(item) && status.waitingForWifi -> RowState.Waiting(saved)
    status.isActive(item) && status.checking -> RowState.Checking
    status.isActive(item) -> RowState.Running(downloadLine(status, item).substringBefore(" · then"),
        (status.bytes.toFloat() / item.bytes.coerceAtLeast(1)).coerceIn(0f, 1f))
    status.queuePosition(item) >= 0 -> RowState.Queued(status.queuePosition(item), status.active?.title)
    status.failures[item.key] != null -> RowState.Failed(status.failures.getValue(item.key).message, saved)
    else -> RowState.Get(saved)
}

/** What a row does when its control or foot is tapped. Null hides that choice. */
internal class RowActions(
    val get: () -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val retry: () -> Unit,
    val removeFromLine: () -> Unit,
    val useMobileData: () -> Unit,
    val dismissFailure: () -> Unit,
    val discardSaved: (() -> Unit)? = null,
)

internal fun rowActions(downloads: AppDownloads, item: DownloadItem, onGet: () -> Unit, discardSaved: (() -> Unit)? = null) = RowActions(
    get = onGet,
    pause = downloads.queue::pause,
    resume = { downloads.queue.enqueue(item) },
    retry = { downloads.queue.enqueue(item) },
    removeFromLine = { downloads.queue.remove(item) },
    useMobileData = downloads.queue::useMobileDataNow,
    dismissFailure = { downloads.queue.dismissFailure(item) },
    discardSaved = discardSaved,
)

/** A small label under a row's title: "Smaller · faster search", "2nd in line". */
internal data class RowChip(val text: String, val positive: Boolean = false)

/**
 * One downloadable thing (a model, a collection, the voice), as a row of a [DownloadGroup]: its tile and title, a line
 * that always says where it stands, and one plain icon at the right edge that changes with the state: download to
 * get it, a ring to pause it, play to resume, a clock while it waits its turn, Wi-Fi while it waits for Wi-Fi, a
 * shield while the file is checked, retry after a failure, and a tick once it is on the phone. The whole row opens
 * the download (or [onClick]); what an item brings is said in words under its title, not in badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadRow(
    icon: ImageVector,
    tone: TileTone,
    title: String,
    /** The line under the title before anything is downloaded: "215 MB · version 2026.09.1". */
    idleMeta: String,
    state: RowState,
    /** On the size pill: "41 MB", or "Update". */
    getLabel: String,
    actions: RowActions,
    chips: List<RowChip> = emptyList(),
    onClick: (() -> Unit)? = null,
    note: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val meta = when (state) {
        is RowState.Get -> if (state.saved > 0) "Paused · ${formatAssetBytes(state.saved)} saved" else idleMeta
        is RowState.Running -> state.line
        RowState.Checking -> "Checking the file…"
        is RowState.Waiting -> "Waiting for Wi-Fi" + if (state.saved > 0) " · ${formatAssetBytes(state.saved)} saved" else ""
        is RowState.Queued -> "Starts when ${state.after ?: "the download before it"} is done"
        is RowState.Failed -> "Couldn't finish" + if (state.saved > 0) " · ${formatAssetBytes(state.saved)} kept" else ""
        RowState.Done -> "On this phone"
    }
    val allChips = chips + listOfNotNull((state as? RowState.Queued)?.let { RowChip(inLine(it.position)) })
    val open = onClick ?: actions.get.takeIf { state == RowState.Get(0) }
    Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()
                .then(if (open != null) Modifier.clickable(onClickLabel = if (onClick != null) "More about $title" else "$getLabel, $title", onClick = open) else Modifier)
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(icon, size = 36.dp, iconSize = 20.dp, tone = if (state is RowState.Failed) TileTone.Clay else tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (allChips.isNotEmpty()) Text(allChips.joinToString(" · ") { it.text }, style = MaterialTheme.typography.bodySmall,
                        color = if (allChips.any { it.positive }) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    // What it holds, right under its size, not set apart below the row.
                    note?.let { Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                DownloadControl(state, getLabel, title, actions)
            }
            val foot = state is RowState.Running || state is RowState.Waiting || state is RowState.Failed || state is RowState.Queued ||
                (state is RowState.Get && state.saved > 0 && actions.discardSaved != null) || extra != null
            if (foot) Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (state) {
                    is RowState.Running -> {
                        FieldAtlasProgressBar(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        FootNote("Keeps going if you leave Library or lock the phone.")
                    }
                    is RowState.Waiting -> {
                        FootNote("Only on Wi-Fi is on. It carries on by itself when the phone is on Wi-Fi.")
                        FootActions(listOf("Use mobile data now" to actions.useMobileData, "Pause" to actions.pause))
                    }
                    is RowState.Failed -> {
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        FootActions(listOf("Try again" to actions.retry, "Dismiss" to actions.dismissFailure))
                    }
                    is RowState.Queued -> FootActions(listOf("Remove from line" to actions.removeFromLine))
                    is RowState.Get -> if (state.saved > 0) actions.discardSaved?.let { FootActions(listOf("Discard saved part" to it)) }
                    else -> Unit
                }
                extra?.invoke(this)
            }
    }
}

/** Download rows in one card, divided like a settings list, so a page of them reads as a list and not a stack of cards. */
@Composable
internal fun DownloadGroup(rows: List<@Composable () -> Unit>) {
    if (rows.isEmpty()) return
    FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        rows.forEachIndexed { index, row ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            row()
        }
    }
}

@Composable
private fun FootNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FootActions(actions: List<Pair<String, () -> Unit>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { (label, onClick) ->
            TextButton(onClick = onClick, shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
        }
    }
}

/** The right edge of a download row. Its touch area is always 48dp, whatever it draws. */
@Composable
internal fun DownloadControl(state: RowState, getLabel: String, title: String, actions: RowActions) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        is RowState.Get -> if (state.saved > 0) {
            RingButton("Resume $title", 0f, colors.outline, colors.surfaceVariant, actions.resume) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp), tint = colors.primary)
            }
        } else Box(Modifier.size(48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "$getLabel, $title", onClick = actions.get)
            .semantics { contentDescription = "$getLabel, $title" }, contentAlignment = Alignment.Center) {
            Icon(FieldAtlasIcons.Download, null, Modifier.size(22.dp), tint = colors.primary)
        }
        is RowState.Running -> RingButton("Pause $title", state.fraction, colors.primary, colors.primaryContainer, actions.pause) {
            Icon(Icons.Filled.Pause, null, Modifier.size(20.dp), tint = colors.primary)
        }
        RowState.Checking -> SpinningRing { Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(19.dp), tint = colors.primary) }
        is RowState.Waiting -> RingButton("Waiting for Wi-Fi, pause $title", 0f, colors.outline, colors.surfaceVariant, actions.pause) {
            Icon(Icons.Outlined.Wifi, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
        }
        is RowState.Queued -> RingButton("In line, remove $title", 0f, colors.outline, colors.surfaceVariant, actions.removeFromLine) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(19.dp), tint = colors.onSurfaceVariant)
        }
        is RowState.Failed -> Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Try again", onClick = actions.retry)
            .semantics { contentDescription = "Try again, $title" }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(clay()), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(19.dp), tint = onClay())
            }
        }
        RowState.Done -> Box(Modifier.size(48.dp).semantics { contentDescription = "$title is on this phone" }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(FieldAtlasIcons.Check, null, Modifier.size(19.dp), tint = colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun clay(): Color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFFF2DED2) else Color(0xFF51372C)

@Composable
private fun onClay(): Color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF754333) else Color(0xFFEFC7B2)

@Composable
private fun RingButton(label: String, fraction: Float, color: Color, track: Color, onClick: () -> Unit, center: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label; stateDescription = "${(fraction * 100).toInt()} percent" },
        contentAlignment = Alignment.Center) {
        Ring(fraction, color, track, 40.dp)
        center()
    }
}

@Composable
private fun SpinningRing(center: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val turn by rememberInfiniteTransition(label = "checking").animateFloat(0f, 360f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Box(Modifier.size(48.dp).semantics { contentDescription = "Checking the file" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(colors.primaryContainer, 0f, 360f, false, style = Stroke(stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
            drawArc(colors.primary, turn - 90f, 90f, false, style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
        }
        center()
    }
}

@Composable
internal fun Ring(fraction: Float, color: Color, track: Color, diameter: Dp) {
    Canvas(Modifier.size(diameter)) {
        val stroke = 3.dp.toPx()
        val inset = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
        val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, topLeft = inset, size = arc, style = Stroke(stroke))
        if (fraction > 0f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft = inset, size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Icon and tone for an item wherever it is listed outside its own row (the bar, the downloads sheet). */
internal fun downloadVisual(item: DownloadItem): PackVisual = when (item) {
    is DownloadItem.Model -> PackVisual(FieldAtlasIcons.Model, TileTone.Sage)
    is DownloadItem.Knowledge -> subjectVisual(item.pack.id) ?: categoryVisual(knowledgeCategory(item.pack.id, item.pack.category))
    DownloadItem.Voice -> PackVisual(Icons.AutoMirrored.Outlined.VolumeUp, TileTone.Sage)
}

/**
 * Before a download starts: what it costs on this phone right now. The storage bar shows what is left afterwards,
 * the network line the likely wait (or that it will wait for Wi-Fi), and how the file is checked.
 */
@Composable
internal fun DownloadConfirmSheet(
    item: DownloadItem,
    verb: String,
    subtitle: String,
    /** Bytes of an earlier attempt already on the phone. */
    saved: Long,
    /** Free space installing needs on top of the download, beyond the bytes still to fetch. */
    installMargin: Long,
    sourceUrl: String,
    replaces: InstalledAsset? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloads = LocalDownloads.current
    val status = downloads?.state?.collectAsState()?.value
    val context = LocalContext.current
    val remaining = (item.bytes - saved).coerceAtLeast(0)
    val space = remember { runCatching { android.os.StatFs(context.filesDir.absolutePath).let { it.totalBytes to it.availableBytes } }.getOrNull() }
    val fits = space == null || space.second - remaining - installMargin >= 0
    val colors = MaterialTheme.colorScheme
    FieldAtlasInfoSheet("$verb ${item.title}", onDismiss) {
        val visual = downloadVisual(item)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldAtlasIconTile(visual.icon, size = 44.dp, tone = visual.tone)
            Text(subtitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        space?.let { (total, free) ->
            val used = (total - free).coerceAtLeast(0)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.surfaceVariant)
                    .semantics { contentDescription = "Storage: ${formatAssetBytes(used)} used, ${formatAssetBytes((free - remaining).coerceAtLeast(0))} free after this" }) {
                    if (used > 0) Box(Modifier.weight(used.toFloat()).height(14.dp).background(colors.outline))
                    if (remaining > 0) Box(Modifier.weight(remaining.toFloat().coerceAtLeast(total / 100f)).height(14.dp).background(colors.primary))
                    val after = (free - remaining).coerceAtLeast(0)
                    if (after > 0) Spacer(Modifier.weight(after.toFloat()))
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Legend(colors.outline, "Used ${formatAssetBytes(used)}")
                    Legend(colors.primary, "This ${formatAssetBytes(remaining)}")
                    Legend(colors.surfaceVariant, "${formatAssetBytes((free - remaining).coerceAtLeast(0))} free after")
                }
            }
        }
        if (!fits) Text("Not enough space: installing needs about ${formatAssetBytes(remaining + installMargin)} free. " +
            "Remove something in Library first.", style = MaterialTheme.typography.bodySmall, color = colors.error)
        val unmetered = downloads?.unmetered == true
        val speed = downloads?.knownSpeed
        SheetLine(if (unmetered) Icons.Outlined.Wifi else if (status != null) Icons.Outlined.SignalCellularAlt else Icons.Outlined.CloudOff,
            if (unmetered) "On Wi-Fi" else "On mobile data or no connection",
            when {
                unmetered && speed != null -> "About ${timeLeft(remaining / speed.coerceAtLeast(1))} at the last download's speed"
                unmetered -> "Starts right away"
                status?.onlyOnWifi == true -> "It waits for Wi-Fi, then downloads by itself"
                else -> "Uses ${formatAssetBytes(remaining)} of mobile data"
            })
        if (downloads != null && status != null) Row(Modifier.fillMaxWidth().clickable(role = Role.Switch) { downloads.queue.setOnlyOnWifi(!status.onlyOnWifi) }
            .heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Only on Wi-Fi", style = MaterialTheme.typography.titleSmall)
                Text("On mobile data it waits, then carries on by itself.", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            FieldAtlasSwitch(status.onlyOnWifi, downloads.queue::setOnlyOnWifi, compact = true,
                modifier = Modifier.semantics { contentDescription = "Only on Wi-Fi" })
        }
        SheetLine(Icons.Outlined.VerifiedUser, "Checked before use",
            xyz.fieldatlas.ui.presentation.downloadSourceDisclosure(sourceUrl).replace("\n", ". ") +
                ". Every file must match its fingerprint." + (replaces?.let {
                    " Your ${formatAssetBytes(it.installedBytes)} edition stays, switched off, until you remove it." } ?: ""))
        if (saved > 0) Text("${formatAssetBytes(saved)} is already saved; about ${formatAssetBytes(remaining)} remains.",
            style = MaterialTheme.typography.bodySmall, color = colors.primary)
        val start = when { saved > 0 -> "Resume"; else -> verb }
        FieldAtlasPrimaryButton("$start · ${formatAssetBytes(remaining)}", onClick = { onDismiss(); onConfirm() }, enabled = fits,
            leadingIcon = FieldAtlasIcons.Download, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onDismiss, shape = FieldAtlasButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Not now") }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetLine(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The bar above the tabs while anything downloads: what, how far, Pause, and the way to the whole list. It takes the
 * card colours of the theme, as the "Answer ready" note does, rather than turning dark on a light screen.
 */
@Composable
internal fun DownloadsBar(status: DownloadQueueState, onPause: () -> Unit, onOpen: () -> Unit) {
    val item = status.active ?: return
    val colors = MaterialTheme.colorScheme
    val fraction = (status.bytes.toFloat() / item.bytes.coerceAtLeast(1)).coerceIn(0f, 1f)
    // The name alone on top, so it is not cut short; where it stands below, in the same words as its row.
    val line = if (status.waitingForWifi || status.checking) downloadLine(status, item) else "${(fraction * 100).toInt()}% · ${downloadLine(status, item)}"
    Surface(color = colors.surface, contentColor = colors.onSurface, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
        shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Show downloads", onClick = onOpen).heightIn(min = 64.dp)
            .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Ring(fraction, colors.primary, colors.primary.copy(alpha = 0.16f), 36.dp)
                Icon(FieldAtlasIcons.Download, null, Modifier.size(17.dp), tint = colors.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!status.checking) Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Pause ${item.title}", onClick = onPause)
                .semantics { contentDescription = "Pause ${item.title}" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Pause, null, Modifier.size(22.dp), tint = colors.primary)
            }
            Icon(FieldAtlasIcons.ChevronRight, null, Modifier.padding(end = 8.dp).size(20.dp), tint = colors.onSurfaceVariant)
        }
    }
}

/** Everything downloading or waiting, and what needs the person: tapping the bar opens it. */
@Composable
internal fun DownloadsSheet(
    downloads: AppDownloads,
    /** Older editions of collections, switched off, whose space can be given back. */
    oldEditions: List<InstalledAsset>,
    onRemoveOldEditions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val status by downloads.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    FieldAtlasInfoSheet("Downloads", onDismiss) {
        val left = (status.active?.let { it.bytes - status.bytes } ?: 0) + status.queued.sumOf { it.bytes }
        val count = listOfNotNull(status.active).size + status.queued.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (count == 0) "Nothing is downloading." else
                "$count to go · ${formatAssetBytes(left)} left" + if (downloads.unmetered) " · on Wi-Fi" else "",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (count > 0) TextButton(onClick = downloads.queue::pauseAll, shape = FieldAtlasButtonShape,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Pause all") }
        }
        status.active?.let { item ->
            SectionHead("Now")
            DownloadGroup(listOf { SheetRow(downloads, item, status) })
        }
        if (status.queued.isNotEmpty()) {
            SectionHead("Next")
            DownloadGroup(status.queued.map { item -> @Composable { SheetRow(downloads, item, status) } })
        }
        if (status.failures.isNotEmpty()) {
            SectionHead("Needs you")
            DownloadGroup(status.failures.values.map { failure -> @Composable {
                val space = "space" in failure.message.lowercase() && oldEditions.isNotEmpty()
                SheetRow(downloads, failure.item, status, extra = if (!space) null else ({
                    TextButton(onClick = onRemoveOldEditions, shape = FieldAtlasButtonShape, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Remove old editions · free ${formatAssetBytes(oldEditions.sumOf { it.installedBytes })}")
                    }
                }))
            } })
        }
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SheetRow(downloads: AppDownloads, item: DownloadItem, status: DownloadQueueState, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    val visual = downloadVisual(item)
    DownloadRow(visual.icon, visual.tone, item.title, formatAssetBytes(item.bytes),
        rowState(item, status, installed = false, saved = 0), formatAssetBytes(item.bytes),
        rowActions(downloads, item, onGet = { downloads.queue.enqueue(item) }), extra = extra)
}
