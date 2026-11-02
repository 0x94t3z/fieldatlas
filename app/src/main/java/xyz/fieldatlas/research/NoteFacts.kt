package xyz.fieldatlas.research

/**
 * Checks on an answer a model wrote after reading notes from the saved sources, before it is shown. The 4B, given
 * a note saying The Strokes released Is This It "in October 2001" in the US, wrote "October 27, 2001" and cited a
 * note about a 2004 live album for it, and added "Label: Epitaph Records" (it was RCA) as a key fact (2 Nov). A
 * citation is a promise that the note says it; a figure no note gives is the model's own, and a key fact the notes
 * do not hold is where it invents. Leaving such key facts out was tried and rejected: it removed true facts as often as
 * false ones, and blind graders scored the shorter answers lower (set 61, 2 Nov).
 */
object NoteFacts {
    private const val MONTHS = "January|February|March|April|May|June|July|August|September|October|November|December"
    /** "October 27, 2001", "27 October 2001", with "on" before it when there is one. */
    private val dayDate = Regex("(?i)(\\bon\\s+)?\\b(?:($MONTHS)\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})|(\\d{1,2})(?:st|nd|rd|th)?\\s+($MONTHS),?\\s+(\\d{4}))\\b")
    private val citation = Regex("\\[S(\\d+)]")
    private val digits = Regex("\\d+(?:[.,]\\d+)*")

    private fun plainNumber(text: String) = text.replace(",", "")

    /**
     * A date given to the day where every note that dates it gives only the month: "October 27, 2001" becomes
     * "October 2001" when a note says "October 2001" and none says the 27th. A date no note gives is left alone.
     */
    fun toNotedPrecision(answer: String, notes: List<String>): String {
        val noted = notes.joinToString("\n")
        return dayDate.replace(answer) { m ->
            val month = (m.groupValues[2].ifEmpty { m.groupValues[6] })
            val day = m.groupValues[3].ifEmpty { m.groupValues[5] }
            val year = m.groupValues[4].ifEmpty { m.groupValues[7] }
            val monthYear = Regex("(?i)\\b$month,?\\s+$year\\b")
            val withDay = Regex("(?i)\\b$month\\s+$day(?:st|nd|rd|th)?,?\\s+$year\\b|\\b$day(?:st|nd|rd|th)?\\s+$month,?\\s+$year\\b")
            if (monthYear.containsMatchIn(noted) && !withDay.containsMatchIn(noted)) {
                (if (m.groupValues[1].isNotEmpty()) "in " else "") + month.replaceFirstChar(Char::uppercaseChar) + " " + year
            } else m.value
        }
    }

    private val sentenceEnd = Regex("(?<=[.!?])[ \\t]+")

    /**
     * Each [Sn] kept only where passage n states every figure of its sentence: a citation of a passage that does not
     * say it is dropped. [noteText] gives passage n's text, null for a number that opens none.
     */
    fun withoutUnbackedCitations(answer: String, noteText: (Int) -> String?): String = answer.lines().joinToString("\n") { line ->
        if (!citation.containsMatchIn(line)) line else line.split(sentenceEnd).joinToString(" ") { sentence ->
            val figures = digits.findAll(citation.replace(sentence, "")).map { plainNumber(it.value) }.filter { it.length >= 2 }.toList()
            if (figures.isEmpty()) sentence else citation.replace(sentence) { m ->
                val text = noteText(m.groupValues[1].toInt())?.let(::plainNumber)
                if (text != null && figures.all { Regex("(?<![\\d.])" + Regex.escape(it) + "(?![\\d])").containsMatchIn(text) }) m.value else ""
            }.replace(Regex("[ \\t]+(?=[.,;:!?]|$)"), "")
        }
    }
}
