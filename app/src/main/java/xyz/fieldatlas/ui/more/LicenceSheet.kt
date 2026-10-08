package xyz.fieldatlas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.fieldatlas.ui.markdown.AnswerMarkdownRenderer
import xyz.fieldatlas.ui.markdown.parseAnswerMarkdown
import xyz.fieldatlas.ui.theme.*

/** An index first, complete notices/texts on demand. No bundled attribution is removed. */
@Composable
internal fun LicenceSheet(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val paths by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.IO) {
            assets.list("licenses").orEmpty().filter { it.endsWith(".txt") && it != "NOTICE.txt" }.sorted()
                .map { "licenses/$it" } + assets.list("ocr/licenses").orEmpty().sorted().map { "ocr/licenses/$it" }
        }
    }
    val path = selected
    val title = when (path) {
        null -> "Licences"; "licenses/NOTICE.txt" -> "App notices"
        "licenses/THIRD_PARTY_NOTICES.md" -> "Third-party notices"; else -> licenceLabel(path)
    }
    FieldAtlasInfoSheet(title, onDismiss, closeDescription = "Close Licences", contentKey = path ?: "index",
        onBack = if (path == null) null else ({ selected = null }), backDescription = "Back to licences") {
        if (path == null) {
            MoreNote("Open-source components and data. Full texts are saved on this phone.")
            Column {
                LicenceChoice("App notices", "Credits and attribution", { selected = "licenses/NOTICE.txt" })
                LicenceChoice("Third-party notices", "Components, versions and data sources", { selected = "licenses/THIRD_PARTY_NOTICES.md" })
            }
            Text("Full licence texts", style = MaterialTheme.typography.titleSmall)
            if (paths == null) MoreNote("Loading licences…")
            Column { paths.orEmpty().forEach { file -> LicenceChoice(licenceLabel(file), null, { selected = file }) } }
        } else {
            val text by produceState<String?>(null, path) {
                value = withContext(Dispatchers.IO) {
                    runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrElse { "Couldn't read this bundled licence." }
                }
            }
            if (text == null) MoreNote("Loading licence…")
            else SelectionContainer {
                if (path.endsWith(".md")) AnswerMarkdownRenderer(parseAnswerMarkdown(readableNotices(text!!)), 0, {},
                    bodyStyle = MaterialTheme.typography.bodySmall)
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    remember(text) { licenceReadingBlocks(text!!) }.forEach { block ->
                        when (block) {
                            is LicenceReadingBlock.Heading -> Text(block.text,
                                modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleSmall)
                            is LicenceReadingBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                            is LicenceReadingBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(block.marker, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Text(block.body, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                            is LicenceReadingBlock.Rule -> HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenceChoice(title: String, detail: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FieldAtlasButtonShape)
        .clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        detail?.let { MoreNote(it) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
}
