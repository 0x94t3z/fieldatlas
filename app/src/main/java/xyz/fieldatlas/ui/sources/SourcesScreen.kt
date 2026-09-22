package xyz.fieldatlas.ui.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.ui.theme.FieldAtlasCard
import xyz.fieldatlas.ui.theme.FieldAtlasInformationAction
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
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
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            FieldAtlasTopBar(title = "Source $sourceNumber of $sourceCount", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    FieldAtlasStatusPill("Source $sourceNumber")
                    Text(evidence.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        evidence.source,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                FieldAtlasCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    SelectionContainer {
                        Text(evidence.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                FieldAtlasCard(Modifier.fillMaxWidth()) {
                    FieldAtlasInformationAction(
                        label = if (showDetails) "Hide source details" else "Source details",
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
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}
