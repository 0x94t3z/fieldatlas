package xyz.fieldatlas.ui.sources

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json

/** Format whitespace only, preserving duplicate keys, numeric spelling and string escapes. */
internal fun formattedSourceJson(text: String): String? {
    if (text.trimStart().firstOrNull() !in listOf('{', '[')) return null
    val formatted = buildString {
        var depth = 0
        var quoted = false
        var escaped = false
        fun newline() { append('\n'); repeat(depth) { append("  ") } }
        text.forEach { char ->
            if (length > 400_000) return null
            if (quoted) {
                append(char)
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> { quoted = true; append(char) }
                '{', '[' -> { append(char); depth++; if (depth > 64) return null; newline() }
                '}', ']' -> { depth--; if (depth < 0) return null; newline(); append(char) }
                ',' -> { append(char); newline() }
                ':' -> append(": ")
                else -> if (!char.isWhitespace()) append(char)
            }
        }
    }
    return formatted.takeIf { runCatching { Json.parseToJsonElement(text) }.isSuccess }
}

@Composable
internal fun SourceTextContent(text: String, language: String?, recognizedText: Boolean = false) {
    if (language == null) {
        var rawOcr by rememberSaveable(text) { mutableStateOf(false) }
        val readable = remember(text) { text.replace("\r\n", "\n").replace(Regex("\n[ \\t]*\n+"), "\n").trim() }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recognizedText && readable != text) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !rawOcr, onClick = { rawOcr = false }, label = { Text("Readable") })
                FilterChip(selected = rawOcr, onClick = { rawOcr = true }, label = { Text("Raw OCR") })
            }
            SelectionContainer {
                Text(if (recognizedText && !rawOcr) readable else text,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp))
            }
            if (recognizedText && !rawOcr && readable != text) Text("Extra blank lines hidden. Copy keeps the original recognized text.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val formatted = remember(text, language) {
        if (language in setOf("JSON", "Notebook JSON")) formattedSourceJson(text) else null
    }
    var showFormatted by rememberSaveable(text) { mutableStateOf(true) }
    var wrap by rememberSaveable(text) { mutableStateOf(true) }
    val displayed = if (showFormatted) formatted ?: text else text
    val canUnwrap = remember(displayed) { displayed.lineSequence().all { it.length <= 2000 } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(language, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("Wrap lines", style = MaterialTheme.typography.labelMedium)
            Checkbox(checked = wrap || !canUnwrap, onCheckedChange = { wrap = it }, enabled = canUnwrap,
                modifier = Modifier.semantics { contentDescription = "Wrap source lines" })
        }
        if (formatted != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showFormatted, onClick = { showFormatted = true }, label = { Text("Formatted") })
                FilterChip(selected = !showFormatted, onClick = { showFormatted = false }, label = { Text("Raw") })
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
            SelectionContainer {
                Text(displayed,
                    modifier = Modifier.fillMaxWidth().then(
                        if (wrap || !canUnwrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
                    ).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp),
                    softWrap = wrap || !canUnwrap)
            }
        }
        if (formatted != null && showFormatted) Text("Indentation added for readability. Copy keeps the original text.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
