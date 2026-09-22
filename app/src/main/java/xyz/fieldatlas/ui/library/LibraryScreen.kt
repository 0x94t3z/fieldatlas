package xyz.fieldatlas.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.assets.InstalledAsset
import xyz.fieldatlas.assets.PackType
import xyz.fieldatlas.ui.presentation.AssetCardModel
import xyz.fieldatlas.ui.presentation.toAssetCardModel
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasHeader

@Composable
fun LibraryScreen(packs: List<InstalledAsset>, onImportPack: () -> Unit) {
    val models = packs.filter { it.type == PackType.MODEL }
    val knowledge = packs.filter { it.type == PackType.KNOWLEDGE }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FieldAtlasHeader(
                    title = "Library",
                    subtitle = "Installed knowledge sets the boundary for evidence-backed answers.",
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            item { SectionLabel("Model") }
            if (models.isEmpty()) item { EmptyLibraryNote("No local model installed") }
            items(models.size, key = { "model:${models[it].id}:${models[it].version}" }) { index ->
                AssetCard(models[index].toAssetCardModel())
            }
            item { SectionLabel("Knowledge") }
            if (knowledge.isEmpty()) item { EmptyLibraryNote("No knowledge pack installed") }
            items(knowledge.size, key = { "knowledge:${knowledge[it].id}:${knowledge[it].version}" }) { index ->
                AssetCard(knowledge[index].toAssetCardModel())
            }
            item {
                OutlinedButton(onClick = onImportPack, modifier = Modifier.fillMaxWidth()) {
                    Text("Import another pack")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun AssetCard(model: AssetCardModel) {
    var expanded by rememberSaveable(model.title, model.version) { mutableStateOf(false) }
    FieldAtlasCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                model.title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            Text("${model.kind} · ${model.size} · version ${model.version}")
            model.coverageLabel?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            model.coverageSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide verification details" else "Verification details")
            }
            if (expanded) {
                Text("License: ${model.license}", style = MaterialTheme.typography.bodySmall)
                Text("SHA-256: ${model.manifestSha256}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyLibraryNote(text: String) {
    FieldAtlasCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
