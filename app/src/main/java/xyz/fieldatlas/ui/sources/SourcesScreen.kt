package xyz.fieldatlas.ui.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.sourceDisplayName
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction
import xyz.fieldatlas.ui.theme.FieldAtlasIconTile
import xyz.fieldatlas.ui.theme.FieldAtlasIcons
import xyz.fieldatlas.ui.theme.FieldAtlasTopBar

@Composable
fun SourcesScreen(
    evidence: Evidence,
    sourceNumber: Int,
    sourceCount: Int,
    packLicense: String?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            FieldAtlasTopBar(title = "Source $sourceNumber of $sourceCount", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(evidence.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "From ${sourceDisplayName(evidence.source)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text(
                    "Saved passage",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FieldAtlasCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = 16.dp,
                ) {
                    SelectionContainer {
                        Text(evidence.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                Text(
                    "This is a local copy. Check its date before treating changing details as current.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                FieldAtlasCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text("Source details", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (evidence.source.startsWith("http://") || evidence.source.startsWith("https://")) {
                            "Original URL"
                        } else {
                            "Source"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FieldAtlasIconTile(FieldAtlasIcons.Document, size = 32.dp)
                        SelectionContainer {
                            Text(evidence.source, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    FieldAtlasInformationAction(
                        label = if (showDetails) "Hide technical identifiers" else "Technical identifiers",
                        onClick = { showDetails = !showDetails },
                        expanded = showDetails,
                    )
                    if (showDetails) {
                        Text("Document ${evidence.documentId}", style = MaterialTheme.typography.bodySmall)
                        Text("Chunk ${evidence.chunkId}", style = MaterialTheme.typography.bodySmall)
                        packLicense?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            }
        }
    }
}
