package xyz.fieldatlas.ui.research

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.sourceDisplayName
import xyz.fieldatlas.ui.sourcePresentation
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasFactRow
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone

/** A map listing ("Place: …", "Latitude: …") laid out for someone standing in the street. */
internal data class PlaceSheetContent(
    val name: String,
    val facts: List<Pair<String, String>>,
    val address: String?,
    val phone: String?,
    val lat: Double,
    val lon: Double,
)

private val hiddenPlaceFields = setOf("Place", "Latitude", "Longitude", "Map data snapshot", "Phone")

internal fun placeSheetContent(text: String): PlaceSheetContent? {
    val fields = text.lines().mapNotNull { line ->
        val split = line.indexOf(": ")
        if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 2).trim()
    }
    val byLabel = fields.toMap()
    val lat = byLabel["Latitude"]?.toDoubleOrNull() ?: return null
    val lon = byLabel["Longitude"]?.toDoubleOrNull() ?: return null
    val name = byLabel["Place"] ?: return null
    val facts = fields.filter { (label, _) -> label !in hiddenPlaceFields } +
        ("Coordinates" to "%.5f, %.5f".format(java.util.Locale.ROOT, lat, lon))
    return PlaceSheetContent(name, facts, byLabel["Address"], byLabel["Phone"], lat, lon)
}

/** Text shared by "Copy answer with sources": the answer, then each source with its link. */
internal fun answerWithSources(question: String, answer: String, sources: List<Evidence>): String = buildString {
    if (question.isNotBlank()) append(question.trim()).append("\n\n")
    append(answer.trim())
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
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(36.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(place?.name ?: evidence.title, style = MaterialTheme.typography.titleLarge,
                        fontFamily = FieldAtlasEditorial, fontWeight = FontWeight.SemiBold)
                    Text(
                        sourcePresentation(evidence).takeIf { it.isAttachment }?.metadata
                            ?: sourceDisplayName(evidence.source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(FieldAtlasIcons.Close, contentDescription = "Close preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cited) FieldAtlasStatusPill("Cited in this answer", StatusTone.Positive, icon = FieldAtlasIcons.Check)
                else FieldAtlasStatusPill("Provided · not cited")
            }
            if (place != null) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    place.facts.forEach { (label, value) -> FieldAtlasFactRow(label, value) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    place.address?.let { address ->
                        SheetChipButton("Copy address", FieldAtlasIcons.Copy) { copy("Address", address) }
                    }
                    SheetChipButton("Copy coordinates", FieldAtlasIcons.Copy) {
                        copy("Coordinates", "%.6f, %.6f".format(java.util.Locale.ROOT, place.lat, place.lon))
                    }
                    place.phone?.let { phone ->
                        SheetChipButton("Call", FieldAtlasIcons.Call) {
                            launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })),
                                "No phone app is available.")
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    Icon(FieldAtlasIcons.Collection, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("Open in a maps app", Modifier.padding(start = 8.dp))
                }
                Text("Opens an offline maps app if one is installed. Hours and menus can be out of date; check before you go.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = MaterialTheme.shapes.medium) {
                    // Plain text, never Markdown: saved passages cannot add links or citations.
                    Text(evidence.text.trim(), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FieldAtlasEditorial, maxLines = 9, overflow = TextOverflow.Ellipsis)
                }
                Text("Original saved text. The model's wording is not verified against it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (nextIndex != null) {
                    OutlinedButton(onClick = { onNext(nextIndex) }, shape = FieldAtlasButtonShape,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Next source") }
                }
                val openFull: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    Icon(FieldAtlasIcons.OpenExternal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Full source", Modifier.padding(start = 8.dp))
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

@Composable
private fun SheetChipButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.heightIn(min = 40.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}
