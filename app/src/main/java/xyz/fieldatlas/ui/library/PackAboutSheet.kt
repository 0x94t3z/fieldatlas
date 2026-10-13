package xyz.fieldatlas.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import xyz.fieldatlas.ui.presentation.collectionDescription
import xyz.fieldatlas.ui.presentation.knowledgeCategory
import xyz.fieldatlas.ui.theme.FieldAtlasButtonShape
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial
import xyz.fieldatlas.ui.theme.FieldAtlasFactRow
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone

/** What a collection holds and where it came from, opened from its menu in Library. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PackAboutSheet(
    asset: InstalledAsset,
    title: String,
    visual: PackVisual,
    onDismiss: () -> Unit,
    onAskExample: ((String) -> Unit)?,
) {
    val attribution by produceState<String?>(null, asset.rootPath, asset.manifestSha256) {
        value = withContext(Dispatchers.IO) {
            try { PackAttribution.read(File(asset.rootPath)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { "The saved licence notices could not be read. Your collection is unchanged." }
        }
    }
    var showAttribution by remember(asset.rootPath, asset.manifestSha256) { mutableStateOf(false) }
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
                FieldAtlasIconTile(visual.icon, size = 36.dp, tone = visual.tone, iconSize = 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = xyz.fieldatlas.ui.theme.FieldAtlasSheetTitleStyle)
                    // The category ("Places") adds nothing when it repeats the title ("Encyclopedia").
                    if (asset.type == PackType.KNOWLEDGE && !knowledgeCategory(asset.id).label.equals(title, ignoreCase = true)) {
                        Text(knowledgeCategory(asset.id).label, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                xyz.fieldatlas.ui.theme.FieldAtlasCloseButton(onDismiss, "Close")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asset.type == PackType.KNOWLEDGE) {
                    if (asset.enabled) FieldAtlasStatusPill("In research", StatusTone.Positive, icon = FieldAtlasIcons.Check)
                    else FieldAtlasStatusPill("Not used in research")
                }
                FieldAtlasStatusPill(formatAssetBytes(asset.installedBytes))
            }
            asset.collectionDescription()?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FieldAtlasFactRow("Version", xyz.fieldatlas.ui.presentation.displayVersion(asset.version))
                FieldAtlasFactRow("Licence", xyz.fieldatlas.ui.presentation.displayLicence(asset.license))
                FieldAtlasFactRow("Checksum", asset.manifestSha256.take(12) + "… verified at install")
                if (asset.type == PackType.KNOWLEDGE) FieldAtlasFactRow("Search", if (asset.enabled)
                    "On · works offline" else "Off · saved on this phone")
            }
            val examples = asset.discovery?.exampleQuestions.orEmpty()
            attribution?.let { notice ->
                androidx.compose.material3.TextButton(onClick = { showAttribution = !showAttribution }) {
                    Text(if (showAttribution) "Hide source credits" else "Source credits and licences")
                }
                if (showAttribution) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(notice, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
