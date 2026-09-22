package xyz.fieldatlas.ui.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun AnswerMarkdownRenderer(
    blocks: List<MarkdownBlock>,
    sourceCount: Int,
    onCitation: (zeroBasedSourceIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> InlineBlock(
                    content = block.content,
                    style = headingStyle(block.level),
                    sourceCount = sourceCount,
                    onCitation = onCitation,
                )
                is MarkdownBlock.Paragraph -> InlineBlock(
                    content = block.content,
                    style = MaterialTheme.typography.bodyLarge,
                    sourceCount = sourceCount,
                    onCitation = onCitation,
                )
                is MarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (block.ordered) "${index + 1}." else "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            InlineBlock(
                                content = item,
                                style = MaterialTheme.typography.bodyLarge,
                                sourceCount = sourceCount,
                                onCitation = onCitation,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    InlineBlock(
                        content = block.content,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        sourceCount = sourceCount,
                        onCitation = onCitation,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                is MarkdownBlock.CodeBlock -> SelectionContainer {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = block.code,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineBlock(
    content: List<MarkdownInline>,
    style: TextStyle,
    sourceCount: Int,
    onCitation: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = annotatedText(content)
    val citations = content.citations()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (text.isNotBlank()) {
            SelectionContainer { Text(text = text, style = style) }
        }
        if (citations.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                citations.forEach { citation ->
                    CitationChip(citation.number, sourceCount, onCitation)
                }
            }
        }
    }
}

@Composable
private fun CitationChip(number: Int, sourceCount: Int, onCitation: (Int) -> Unit) {
    val available = number in 1..sourceCount
    val semantics = if (available) "Open source $number" else "Source $number unavailable"
    Surface(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .then(if (available) Modifier.clickable { onCitation(number - 1) } else Modifier)
            .semantics { contentDescription = semantics },
        color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (available) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "[$number]",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineLarge
    2 -> MaterialTheme.typography.headlineMedium
    3 -> MaterialTheme.typography.titleLarge
    else -> MaterialTheme.typography.titleMedium
}

@Composable
private fun annotatedText(content: List<MarkdownInline>): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        fun appendInline(inline: MarkdownInline) {
            when (inline) {
                is MarkdownInline.Text -> append(inline.value)
                is MarkdownInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    inline.content.forEach(::appendInline)
                }
                is MarkdownInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    inline.content.forEach(::appendInline)
                }
                is MarkdownInline.Code -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                ) { append(inline.value) }
                is MarkdownInline.Citation -> Unit
            }
        }
        content.forEach(::appendInline)
    }
}

private fun List<MarkdownInline>.citations(): List<MarkdownInline.Citation> = flatMap { inline ->
    when (inline) {
        is MarkdownInline.Citation -> listOf(inline)
        is MarkdownInline.Strong -> inline.content.citations()
        is MarkdownInline.Emphasis -> inline.content.citations()
        else -> emptyList()
    }
}

private fun AnnotatedString.isNotBlank(): Boolean = text.isNotBlank()
