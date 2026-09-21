package xyz.fieldatlas.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.fieldatlas.research.Evidence

@Composable
fun SourcesScreen(evidence: Evidence, packLicense: String?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Source passage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(evidence.title, style = MaterialTheme.typography.titleLarge)
        Text(evidence.source, color = MaterialTheme.colorScheme.primary)
        Text("Document ${evidence.documentId} · Chunk ${evidence.chunkId}", style = MaterialTheme.typography.labelMedium)
        packLicense?.let { Text("Pack license: $it", style = MaterialTheme.typography.labelMedium) }
        Text(evidence.text, style = MaterialTheme.typography.bodyLarge)
    }
}
