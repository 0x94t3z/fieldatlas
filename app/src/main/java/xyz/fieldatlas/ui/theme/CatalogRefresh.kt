package xyz.fieldatlas.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Explicit provisioning action; rendering never initiates a network request. */
@Composable
fun CatalogRefresh(refreshing: Boolean, error: String?, onRefresh: () -> Unit) {
    Column {
        TextButton(onClick = onRefresh, enabled = !refreshing) {
            Text(if (refreshing) "Refreshing collections…" else "Refresh collections")
        }
        // Saved collections keep working, so a failed catalog refresh is information, not an error.
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
