package xyz.fieldatlas.ui.research

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.sourceDisplayName
import xyz.fieldatlas.ui.sourcePresentation
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasBareIconButton
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone
import xyz.fieldatlas.ui.theme.FieldAtlasReadingStyle

/** A map listing ("Place: …", "Latitude: …") laid out for someone standing in the street. */
internal data class PlaceSheetContent(
    val name: String,
    val facts: List<Pair<String, String>>,
    val address: String?,
    val phone: String?,
    val lat: Double,
    val lon: Double,
)

private val hiddenPlaceFields = setOf("Place", "Category", "Latitude", "Longitude", "Map data snapshot", "Phone")
private val shortPlaceLabels = mapOf("Listing last checked" to "Last checked", "Hours in source" to "Hours")

internal fun placeSheetContent(text: String): PlaceSheetContent? {
    val fields = text.lines().mapNotNull { line ->
        val split = line.indexOf(": ")
        if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 2).trim()
    }
    val byLabel = fields.toMap()
    val lat = byLabel["Latitude"]?.toDoubleOrNull() ?: return null
    val lon = byLabel["Longitude"]?.toDoubleOrNull() ?: return null
    val name = byLabel["Place"] ?: return null
    val facts = fields.filter { (label, _) -> label !in hiddenPlaceFields }
        .map { (label, value) ->
            val shown = when (label) {
                "Hours in source", "Hours" -> readableHours(value)
                // Map tags are lowercase ("restaurant", "vietnamese"); names and addresses keep their case.
                "Type", "Vegan", "Cuisine", "Diet" -> value.replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
                // Map tags answer yes or no in lowercase ("Emergency department: no").
                else -> if (value == "yes" || value == "no") value.replaceFirstChar(Char::uppercaseChar) else value
            }
            (shortPlaceLabels[label] ?: label) to shown
        } +
        ("Coordinates" to "%.5f, %.5f".format(java.util.Locale.ROOT, lat, lon))
    return PlaceSheetContent(name, facts, byLabel["Address"], byLabel["Phone"], lat, lon)
}

/** Text shared by "Copy answer with sources": the answer, then each source with its link. */
internal fun answerWithSources(question: String, answer: String, sources: List<Evidence>): String = buildString {
    if (question.isNotBlank()) append(question.trim()).append("\n\n")
    append(xyz.fieldatlas.text.MarkdownLiteral.decode(answer.trim()))
    if (sources.isNotEmpty()) {
        append("\n\nSources\n")
        sources.forEachIndexed { index, source -> append("[S${index + 1}] ${source.title} — ${source.source}\n") }
    }
}.trimEnd()

/**
 * Opened by a citation or source tap: a quick look at the passage without leaving the answer.
 * Map listings get their practical actions here, since that is where someone decides to go.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SourcePreviewSheet(
    sources: List<Evidence>,
    index: Int,
    cited: Boolean,
    nextIndex: Int?,
    onDismiss: () -> Unit,
    onOpenFull: ((Int) -> Unit)?,
    onNext: (Int) -> Unit,
    /** The answer was written by the app from this text (a guide, a number, a listing), not by the model. */
    appWritten: Boolean = false,
) {
    val evidence = sources.getOrNull(index) ?: return
    val place = placeSheetContent(evidence.text)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    fun copy(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
    fun launch(intent: Intent, missing: String) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, missing, Toast.LENGTH_LONG).show()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.SheetShape,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)
                .navigationBarsPadding(),
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A place shows its kind (solid red for health, the one place in focus); any other source its number.
                val placeKind = if (place != null) placeCardModel(index, evidence)?.kind else null
                if (placeKind != null) {
                    val (icon, tone) = placeTile(placeKind, focused = true)
                    xyz.fieldatlas.ui.theme.FieldAtlasIconTile(icon, size = 40.dp, tone = tone)
                } else Surface(Modifier.size(36.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(place?.name ?: evidence.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (sourcePresentation(evidence).isAttachment) 1 else 2,
                        overflow = if (sourcePresentation(evidence).isAttachment) androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis
                            else androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        sourcePresentation(evidence).takeIf { it.isAttachment }?.metadata
                            ?: sourceDisplayName(evidence.source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                xyz.fieldatlas.ui.theme.FieldAtlasCloseButton(onDismiss, "Close preview")
            }
            key(index, evidence) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (cited) FieldAtlasStatusPill("Cited in this answer", StatusTone.Positive, icon = FieldAtlasIcons.Citation)
                        else FieldAtlasStatusPill("Provided · not cited")
                    }
                    if (place != null) {
                        place.address?.let { address -> PlaceAddressCard(place.name, address) { copy("Address", address) } }
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // The address has its own card above, large and with copy.
                                place.facts.filterNot { (label, _) -> label == "Address" && place.address != null }.forEach { (label, value) ->
                                    val copyValue = when (label) {
                                        "Address" -> place.address
                                        "Coordinates" -> "%.6f, %.6f".format(java.util.Locale.ROOT, place.lat, place.lon)
                                        else -> null
                                    }
                                    PlaceDetailRow(label, value, copyValue?.let { { copy(label, it) } })
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val label = Uri.encode(place.name)
                                launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}($label)")),
                                    "No maps app is installed. Copy the coordinates instead.")
                            },
                            shape = FieldAtlasButtonShape,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(FieldAtlasIcons.Map, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("Open in maps", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        place.phone?.let { phone ->
                            OutlinedButton(onClick = {
                                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })),
                                    "No phone app is available.")
                            }, shape = FieldAtlasButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Icon(FieldAtlasIcons.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Call this place", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        // Never "install this app": at the moment of need the phone is often offline.
                        Text("Opens in a maps app on this phone, if one is installed; with its maps downloaded, directions work offline. " +
                            "Check hours and listing details before you go.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = MaterialTheme.shapes.medium) {
                            // Plain text, never Markdown: saved passages cannot add links or citations.
                            Text(evidence.text.trim(), Modifier.padding(16.dp), style = FieldAtlasReadingStyle,
                                maxLines = 9, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (appWritten) "Original saved text. The answer quotes it or is worked out from it on this phone."
                            else "Original saved text. The model's wording is not verified against it.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (nextIndex != null) {
                            OutlinedButton(onClick = { onNext(nextIndex) }, shape = FieldAtlasButtonShape,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Next source", style = MaterialTheme.typography.labelLarge) }
                        }
                        val openFull: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                            Icon(FieldAtlasIcons.Document, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Full source", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        // A place already has its main action (maps) above; one filled button per sheet.
                        if (onOpenFull == null) Unit
                        else if (place != null) OutlinedButton(onClick = { onOpenFull(index) }, shape = FieldAtlasButtonShape,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp), content = openFull)
                        else Button(onClick = { onOpenFull(index) }, shape = FieldAtlasButtonShape,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp), content = openFull)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailRow(label: String, value: String, onCopy: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = onCopy != null || maxWidth < 260.dp || LocalDensity.current.fontScale > 1.3f
        Row(Modifier.fillMaxWidth().heightIn(min = if (onCopy != null) 56.dp else 36.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stacked) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(label, Modifier.weight(0.38f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium)
            }
            if (onCopy != null) FieldAtlasBareIconButton(onCopy, "Copy ${label.lowercase(java.util.Locale.ROOT)}", FieldAtlasIcons.Copy)
        }
    }
}

/**
 * The address set large enough to show someone, a driver or a passer-by, with a full-screen view for arm's length and a
 * copy for a maps app. Offline in a strange city, the address is often the one thing to hand over.
 */
@Composable
private fun PlaceAddressCard(name: String, address: String, onCopy: () -> Unit) {
    var fullScreen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Address · show it to a driver", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(address, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.padding(start = 0.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { fullScreen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(FieldAtlasIcons.FullScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Full screen", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onCopy, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Copy address" }) {
                    Icon(FieldAtlasIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Copy", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (fullScreen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().clickable(onClickLabel = "Close") { fullScreen = false },
                color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                    Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(address, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                    Text("Tap anywhere to close", Modifier.padding(top = 28.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
