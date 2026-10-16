package xyz.fieldatlas.ui.markdown

import androidx.compose.runtime.compositionLocalOf

/** The sentence being read aloud on this page, as shown; every paragraph that holds it marks it. */
val LocalSpokenSentence = compositionLocalOf<String?> { null }

private val citationMark = Regex("\\u2060?\\[(?:S)?\\d+]")

/**
 * Where [sentence] sits in [text]. Only letters and digits are compared, so citation marks, Markdown and spacing that
 * differ between the shown paragraph and the spoken sentence do not matter; null when the paragraph does not hold it.
 */
internal fun spokenRange(text: String, sentence: String): IntRange? {
    if (sentence.isBlank()) return null
    val skip = BooleanArray(text.length)
    citationMark.findAll(text).forEach { m -> for (i in m.range) skip[i] = true }
    val letters = StringBuilder(); val at = ArrayList<Int>(text.length)
    text.forEachIndexed { i, c -> if (!skip[i] && c.isLetterOrDigit()) { letters.append(c.lowercaseChar()); at += i } }
    val wanted = buildString { citationMark.replace(sentence, "").forEach { if (it.isLetterOrDigit()) append(it.lowercaseChar()) } }
    if (wanted.isEmpty()) return null
    val start = letters.indexOf(wanted)
    if (start < 0) return null
    return at[start]..at[start + wanted.length - 1]
}
