package xyz.fieldatlas.ui.library

import androidx.compose.foundation.layout.Arrangement
import xyz.fieldatlas.ui.presentation.aboutSubtitle
import xyz.fieldatlas.ui.presentation.aboutExamples
import xyz.fieldatlas.ui.presentation.aboutDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import xyz.fieldatlas.assets.PackAttribution
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.formatAssetBytes
import xyz.fieldatlas.ui.presentation.knowledgeCategory
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons

/** What a collection holds and where it came from, opened from its menu in Library. Every pack reads the same way:
 * name, size and version; what it holds; one card of facts; questions to try. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PackAboutSheet(
    asset: InstalledAsset,
    title: String,
    visual: PackVisual,
    onDismiss: () -> Unit,
    onAskExample: ((String) -> Unit)?,
    catalogDescription: String? = null,
    category: String? = null,
) {
    val attribution by produceState<String?>(null, asset.rootPath, asset.manifestSha256) {
        value = withContext(Dispatchers.IO) {
            try { PackAttribution.read(File(asset.rootPath)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { "The saved licence notices could not be read. Your collection is unchanged." }
        }
    }
    var showAttribution by remember(asset.rootPath, asset.manifestSha256) { mutableStateOf(false) }
    val knowledge = asset.type == PackType.KNOWLEDGE
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.SheetShape,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 22.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(visual.icon, size = 40.dp, tone = visual.tone, iconSize = 22.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = xyz.fieldatlas.ui.theme.FieldAtlasSheetTitleStyle)
                    Text(aboutSubtitle(title, if (knowledge) category ?: knowledgeCategory(asset.id).label else null,
                        formatAssetBytes(asset.installedBytes), asset.version),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                xyz.fieldatlas.ui.theme.FieldAtlasCloseButton(onDismiss, "Close")
            }
            asset.aboutDescription(catalogDescription)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            AboutFacts {
                if (knowledge) AboutFact("Research", divider = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (asset.enabled) Icon(FieldAtlasIcons.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (asset.enabled) "On · searched offline" else "Off · kept on this phone",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (asset.enabled) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (asset.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AboutFact("Licence", divider = knowledge) { AboutValue(xyz.fieldatlas.ui.presentation.displayLicence(asset.license)) }
                AboutFact("Checksum") { AboutValue(asset.manifestSha256.take(12) + "… verified at install") }
                attribution?.let { notice ->
                    AboutFact("Credits", onClick = { showAttribution = !showAttribution },
                        stateDescription = if (showAttribution) "Expanded" else "Collapsed") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showAttribution) "Hide source credits" else "Show source credits", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                            xyz.fieldatlas.ui.theme.FieldAtlasExpandIcon(showAttribution, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (showAttribution) androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(notice, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val examples = asset.aboutExamples()
            if (examples.isNotEmpty() && onAskExample != null) {
                Text("Try asking", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.take(4).forEach { question ->
                        OutlinedButton(onClick = { onDismiss(); onAskExample(question) }, shape = FieldAtlasButtonShape,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(question, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

/**
 * A collection not yet on the phone, opened by tapping its row in Discover: the same order as About (name, size and
 * version; what it holds; one card of facts), ending in its Download, so the list itself can stay two short lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogPackSheet(
    pack: xyz.fieldatlas.assets.KnowledgeCatalogEntry,
    visual: PackVisual,
    sourceUrl: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = xyz.fieldatlas.ui.theme.FieldAtlasLayout.SheetShape,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 22.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(visual.icon, size = 40.dp, tone = visual.tone, iconSize = 22.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(pack.title, style = xyz.fieldatlas.ui.theme.FieldAtlasSheetTitleStyle)
                    Text(aboutSubtitle(pack.title, knowledgeCategory(pack.id, pack.category).label, formatAssetBytes(pack.bytes), pack.version),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                xyz.fieldatlas.ui.theme.FieldAtlasCloseButton(onDismiss, "Close")
            }
            xyz.fieldatlas.ui.presentation.catalogText(pack.description)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            AboutFacts {
                AboutFact("Licence", divider = false) { AboutValue(xyz.fieldatlas.ui.presentation.displayLicence(pack.license)) }
                AboutFact("Source") { AboutValue(runCatching { java.net.URI(sourceUrl).host }.getOrNull() ?: sourceUrl) }
            }
            xyz.fieldatlas.ui.theme.FieldAtlasPrimaryButton("Download · ${formatAssetBytes(pack.bytes)}", onClick = { onDismiss(); onDownload() },
                leadingIcon = FieldAtlasIcons.Download, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The facts as one card, a row each, so every pack's facts sit in the same place. */
@Composable
internal fun AboutFacts(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(content = content) }
}

@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.AboutFact(
    label: String,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    stateDescription: String? = null,
    value: @Composable () -> Unit,
) {
    // Rows after the first are divided from the one above; the card's border closes the first and last.
    if (divider) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .semantics { stateDescription?.let { this.stateDescription = it } } else Modifier)
            .heightIn(min = if (onClick != null) 48.dp else 0.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
internal fun AboutValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
