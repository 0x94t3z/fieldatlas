package xyz.fieldatlas.ui.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.heading
import xyz.fieldatlas.ui.theme.FieldAtlasStatusPill
import xyz.fieldatlas.ui.theme.StatusTone
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.shape.RoundedCornerShape
import xyz.fieldatlas.ui.theme.FieldAtlasEditorial

@Composable
fun AnswerMarkdownRenderer(
    blocks: List<MarkdownBlock>,
    sourceCount: Int,
    onCitation: (zeroBasedSourceIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> answerSection(block)?.let { section ->
                    SectionHeading(section, headingStyle(block.level))
                } ?: InlineBlock(
                    content = block.content,
                    style = headingStyle(block.level),
                    sourceCount = sourceCount,
                    onCitation = onCitation,
                )
                is MarkdownBlock.Paragraph -> InlineBlock(
                    content = block.content,
                    style = bodyStyle,
                    sourceCount = sourceCount,
                    onCitation = onCitation,
                )
                is MarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    var number = 0
                    block.items.forEachIndexed { index, item ->
                        val depth = block.depths.getOrElse(index) { 0 }
                        if (depth == 0) number++
                        Row(Modifier.padding(start = (18 * depth).dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                when { depth > 0 -> "◦"; block.ordered -> "$number."; else -> "•" },
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            InlineBlock(
                                content = item,
                                style = bodyStyle,
                                sourceCount = sourceCount,
                                onCitation = onCitation,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                is MarkdownBlock.Quote -> {
                    // A trailing citation belongs to the whole quotation, so it becomes a
                    // source footer instead of a chip stranded on the quote's last line.
                    val (body, footer) = block.content.trailingCitations()
                    val linked = footer.filter { it in 1..sourceCount }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Box(
                                Modifier.fillMaxHeight().width(3.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            )
                            Column(
                                Modifier.padding(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                InlineBlock(
                                    content = if (linked.isEmpty()) block.content else body,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FieldAtlasEditorial,
                                        fontStyle = FontStyle.Italic,
                                    ),
                                    sourceCount = sourceCount,
                                    onCitation = onCitation,
                                )
                                if (linked.isNotEmpty()) QuoteSourceFooter(linked, onCitation)
                            }
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> SelectionContainer {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column {
                            block.language?.takeIf(String::isNotBlank)?.let { language ->
                                Text(
                                    text = language.uppercase(),
                                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = block.code,
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
                is MarkdownBlock.Table -> MarkdownTable(block, sourceCount, onCitation)
                MarkdownBlock.ThematicBreak -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MarkdownBlock.Table,
    sourceCount: Int,
    onCitation: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            TableRow(table.headers, header = true, sourceCount, onCitation)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            table.rows.forEachIndexed { index, row ->
                TableRow(row, header = false, sourceCount, onCitation)
                if (index != table.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<MarkdownInline>>,
    header: Boolean,
    sourceCount: Int,
    onCitation: (Int) -> Unit,
) {
    Row {
        cells.forEach { cell ->
            InlineBlock(
                content = cell,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                ),
                sourceCount = sourceCount,
                onCitation = onCitation,
                modifier = Modifier
                    .widthIn(min = 132.dp, max = 220.dp)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
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
    val text = annotatedText(content, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary, sourceCount)
    val inlineCitations = content.citations().filter { it.number in 1..sourceCount }.distinctBy { it.number }
        .associate { citation ->
            // A compact numbered badge, sized to its digits, so a citation reads as a footnote
            // mark beside its word rather than a button that pushes punctuation away.
            "citation:${citation.number}" to InlineTextContent(
                Placeholder((citation.number.toString().length * 0.5f + 1.05f).em, 1.2.em, PlaceholderVerticalAlign.TextCenter),
            ) { CitationChip(citation.number, onCitation) }
        }
    Column(modifier) {
        if (text.isNotBlank()) {
            SelectionContainer {
                Text(text = text, inlineContent = inlineCitations, style = style.copy(
                    lineBreak = LineBreak.Paragraph,
                    hyphens = Hyphens.None,
                ))
            }
        }
    }
}

@Composable
private fun SectionHeading(section: AnswerSection, style: TextStyle) {
    // One announcement ("Model explanation, Not verified") instead of two fragments.
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(section.title, style = style, modifier = Modifier.weight(1f, fill = false))
        FieldAtlasStatusPill(
            text = section.badge,
            state = if (section.quoted) StatusTone.Positive else StatusTone.Attention,
        )
    }
}

@Composable
private fun QuoteSourceFooter(numbers: List<Int>, onCitation: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        numbers.forEach { number ->
            Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Open source $number") { onCitation(number - 1) },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open source", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text("$number", Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CitationChip(number: Int, onCitation: (Int) -> Unit) {
    // Inset from the placeholder edges so adjacent badges ([1][2]) keep a hairline gap.
    Box(Modifier.fillMaxSize().padding(horizontal = 1.5.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "Open source $number") { onCitation(number - 1) }
                .semantics { contentDescription = "Open source $number" },
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(6.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleLarge
    2 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}.copy(fontFamily = xyz.fieldatlas.ui.theme.FieldAtlasSans, fontWeight = FontWeight.SemiBold)

internal fun annotatedText(content: List<MarkdownInline>, codeBackground: Color, linkColor: Color, sourceCount: Int): AnnotatedString {
    return buildAnnotatedString {
        // "boiling [1]." would leave the badge floating between a word and its full stop;
        // drop the space so the mark attaches to the claim it supports.
        fun tighten(items: List<MarkdownInline>): List<MarkdownInline> = items.mapIndexed { index, inline ->
            val next = items.getOrNull(index + 1)
            if (inline is MarkdownInline.Text && next is MarkdownInline.Citation && next.number in 1..sourceCount) {
                MarkdownInline.Text(inline.value.trimEnd(' ', '\t'))
            } else inline
        }
        fun appendInline(inline: MarkdownInline) {
            when (inline) {
                is MarkdownInline.Text -> append(inline.value)
                is MarkdownInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    tighten(inline.content).forEach(::appendInline)
                }
                is MarkdownInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    inline.content.forEach(::appendInline)
                }
                is MarkdownInline.Strikethrough -> withStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                ) { inline.content.forEach(::appendInline) }
                is MarkdownInline.Code -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                ) { append(inline.value) }
                is MarkdownInline.Link -> withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) { inline.label.forEach(::appendInline) }
                is MarkdownInline.Citation -> if (inline.number in 1..sourceCount) {
                    appendInlineContent("citation:${inline.number}", "[${inline.number}]")
                }
            }
        }
        tighten(content).forEach(::appendInline)
    }
}

private fun List<MarkdownInline>.citations(): List<MarkdownInline.Citation> = flatMap { inline ->
    when (inline) {
        is MarkdownInline.Citation -> listOf(inline)
        is MarkdownInline.Strong -> inline.content.citations()
        is MarkdownInline.Emphasis -> inline.content.citations()
        is MarkdownInline.Strikethrough -> inline.content.citations()
        is MarkdownInline.Link -> inline.label.citations()
        else -> emptyList()
    }
}

private fun AnnotatedString.isNotBlank(): Boolean = text.isNotBlank()
