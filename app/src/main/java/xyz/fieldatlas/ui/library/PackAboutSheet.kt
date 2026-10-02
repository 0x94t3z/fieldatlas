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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.formatAssetBytes
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FieldAtlasIconTile(visual.icon, size = 52.dp, tone = visual.tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FieldAtlasEditorial,
                        fontWeight = FontWeight.SemiBold)
                    if (asset.type == PackType.KNOWLEDGE) {
                        Text(knowledgeCategory(asset.id).label, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(FieldAtlasIcons.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (asset.type == PackType.KNOWLEDGE) {
                    if (asset.enabled) FieldAtlasStatusPill("In research", StatusTone.Positive, icon = FieldAtlasIcons.Check)
                    else FieldAtlasStatusPill("Not used in research")
                }
                FieldAtlasStatusPill(formatAssetBytes(asset.installedBytes))
            }
            asset.discovery?.coverageSummary?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                FieldAtlasFactRow("Version", asset.version)
                FieldAtlasFactRow("Licence", asset.license)
                FieldAtlasFactRow("Checksum", asset.manifestSha256.take(12) + "… verified at install")
                if (asset.type == PackType.KNOWLEDGE) FieldAtlasFactRow("Searched", "On this phone, no connection needed")
            }
            val examples = asset.discovery?.exampleQuestions.orEmpty()
            if (examples.isNotEmpty() && onAskExample != null) {
                Text("Try asking", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.take(4).forEach { question ->
                        OutlinedButton(onClick = { onDismiss(); onAskExample(question) }, shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.heightIn(min = 40.dp)) { Text(question, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, shape = FieldAtlasButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Done")
            }
        }
    }
}
