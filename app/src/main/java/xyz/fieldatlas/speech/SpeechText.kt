package xyz.fieldatlas.speech

import xyz.fieldatlas.emergency.EmergencyGuide

/**
 * What reading aloud says, sentence by sentence. Each [Segment] keeps the sentence as it is shown, so the screen can
 * mark it while it is read, and the words the voice says: a voice reads "112" as "one hundred twelve" and skips "€",
 * so numbers people dial are said digit by digit and symbols are said as words. Citation marks, Markdown and the
 * excerpts saved under an answer are never read.
 */
object SpeechText {
    data class Segment(val shown: String, val spoken: String)

    /** The line every emergency guide opens with, as the answer shows it. */
    const val CALL_FIRST = "If this is an emergency, call your local emergency number first (112 works on mobile phones in many countries)."

    /** An emergency guide as read aloud: the call line, then every heading, paragraph and step in order. */
    fun guide(guide: EmergencyGuide): List<Segment> {
        val out = mutableListOf(segment(CALL_FIRST))
        for (section in guide.sections) {
            if (section.heading.isNotBlank()) out += segment(section.heading.trim())
            section.paragraphs.forEach { paragraph -> sentences(paragraph).forEach { out += segment(it) } }
            section.steps.forEach { step -> sentences(step).forEach { out += segment(it) } }
        }
        return out.filter { it.spoken.isNotBlank() }
    }

    /** An answer as read aloud: its text without the saved excerpts, app labels, citation marks or Markdown. */
    fun answer(markdown: String): List<Segment> {
        val out = mutableListOf<Segment>()
        for (raw in markdown.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(">") || line.startsWith("|") || line.startsWith("```") || line == "---") continue
            if (line.startsWith("#")) {
                val heading = plain(line.trimStart('#').trim())
                // Below these, the answer only lists what it read; above them, app labels say how it was made.
                if (excerptHeading.containsMatchIn(heading)) break
                if (labelHeading.containsMatchIn(heading)) continue
                if (heading.isNotBlank()) out += segment(heading)
                continue
            }
            val text = plain(line.replace(listMarker, ""))
            sentences(text).forEach { out += segment(it) }
        }
        return out.filter { it.spoken.isNotBlank() }
    }

    private val excerptHeading = Regex("(?i)^(saved (excerpts|passages|sources)|sources|other sources)\\b")
    private val labelHeading = Regex("(?i)^(model explanation|from the saved|quoted from|source excerpt|shown as published)")
    private val listMarker = Regex("^(?:[-*+•]|\\d+[.)])\\s+")

    /** Text as the screen shows it: no Markdown markers, links read as their words, citation marks gone. */
    fun plain(text: String): String = text
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(citation, "")
        .replace(Regex("(\\*\\*|__|`)"), "")
        .replace(Regex("(?<![\\p{L}\\p{N}])[*_](?=\\S)|(?<=\\S)[*_](?![\\p{L}\\p{N}])"), "")
        .replace('⁠', ' ')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex(" ([.,;:!?])"), "$1")
        .trim()

    private val citation = Regex("\\s?\\u2060?\\[(?:S)?\\d+(?:\\s*[,–-]\\s*(?:S)?\\d+)*]")

    private fun segment(shown: String) = Segment(shown, spoken(shown))

    // Abbreviations a full stop does not end a sentence after.
    private val abbreviations = setOf("e.g", "i.e", "etc", "vs", "dr", "mr", "mrs", "ms", "st", "mt", "no", "approx", "u.s", "u.k", "ft", "jr", "sr", "fig")

    /** Sentences in order: a full stop, question or exclamation mark followed by a space and a capital or number. */
    fun sentences(text: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        for (m in Regex("[.!?]+[\"”’)]?\\s+(?=[\"“‘(]?[\\p{Lu}\\p{N}])").findAll(text)) {
            val before = text.substring(start, m.range.first).substringAfterLast(' ').trimStart('(', '"', '“').lowercase()
            if (m.value.startsWith(".") && before in abbreviations) continue
            out += text.substring(start, m.range.last + 1).trim()
            start = m.range.last + 1
        }
        if (start < text.length) out += text.substring(start).trim()
        return out.filter { it.isNotBlank() }
    }

    /**
     * A spoken sentence cut at its commas, semicolons and colons into pieces a voice can make one at a time. A piece is
     * cut only where both sides keep at least [MIN_WORDS] words, so a list of short items stays whole and no piece is a
     * lone word; a very long run with no such break stays whole too.
     */
    fun clauses(spoken: String): List<String> {
        val words = spoken.trim().split(' ').filter { it.isNotEmpty() }
        if (words.size < MIN_WORDS * 2) return listOf(spoken.trim()).filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var from = 0
        for (i in words.indices) {
            val breaks = words[i].endsWith(',') || words[i].endsWith(';') || words[i].endsWith(':')
            if (breaks && i + 1 - from >= MIN_WORDS && words.size - (i + 1) >= MIN_WORDS) {
                out += words.subList(from, i + 1).joinToString(" ")
                from = i + 1
            }
        }
        out += words.subList(from, words.size).joinToString(" ")
        return out
    }

    private const val MIN_WORDS = 5

    private val ones = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private fun digits(number: String) = number.filter(Char::isDigit).map { ones[it - '0'] }.joinToString(" ")

    private val currencies = listOf("€" to "euros", "$" to "dollars", "£" to "pounds", "¥" to "yen", "₫" to "dong", "฿" to "baht", "₹" to "rupees",
        "₩" to "won", "₱" to "pesos", "₺" to "lira")
    private val units = linkedMapOf("km/h" to "kilometres per hour", "mph" to "miles per hour", "mg" to "milligrams", "kg" to "kilograms",
        "km" to "kilometres", "cm" to "centimetres", "mm" to "millimetres", "ml" to "millilitres", "mL" to "millilitres", "L" to "litres",
        "g" to "grams", "m" to "metres", "ft" to "feet", "mi" to "miles", "hrs" to "hours", "hr" to "hours", "min" to "minutes", "mins" to "minutes")

    /** The words a voice should say for [text]. */
    fun spoken(text: String): String {
        var t = plain(text)
        // Numbers people dial, said digit by digit: always the common emergency numbers, any number after "call" or "dial".
        t = t.replace(Regex("(?i)\\b(call|dial|phone|ring|text|number)( (?:the |a |an )?(?:\\w+ ){0,2}?)(\\d[\\d -]{1,14}\\d)\\b")) {
            it.groupValues[1] + it.groupValues[2] + digits(it.groupValues[3])
        }
        t = t.replace(Regex("\\b(112|911|999|000)\\b")) { digits(it.value) }
        // Times: 17:00 -> 5 pm, 9:30 -> 9 30; a range of them reads "to".
        t = t.replace(Regex("\\b(\\d{1,2})[.:](\\d{2})\\s*[–-]\\s*(\\d{1,2})[.:](\\d{2})\\b")) { "${time(it.groupValues[1], it.groupValues[2])} to ${time(it.groupValues[3], it.groupValues[4])}" }
        t = t.replace(Regex("\\b(\\d{1,2}):(\\d{2})\\b")) { time(it.groupValues[1], it.groupValues[2]) }
        // Thousands separators, then money, temperature, percentages and units.
        t = t.replace(Regex("(?<=\\d),(?=\\d{3}\\b)"), "")
        for ((sign, word) in currencies) {
            t = t.replace(Regex(Regex.escape(sign) + "\\s?(\\d[\\d.]*)(\\s*(?:million|billion|thousand))?")) { it.groupValues[1] + it.groupValues[2] + " " + word }
        }
        t = t.replace(Regex("(\\d)\\s?°\\s?C\\b"), "$1 degrees Celsius").replace(Regex("(\\d)\\s?°\\s?F\\b"), "$1 degrees Fahrenheit")
            .replace(Regex("(\\d)\\s?°"), "$1 degrees")
        t = t.replace(Regex("(\\d)\\s?%"), "$1 percent")
        t = t.replace(Regex("(\\d)\\s*[–-]\\s*(\\d)"), "$1 to $2")
        for ((unit, word) in units) t = t.replace(Regex("(\\d)\\s?" + Regex.escape(unit) + "(?![\\p{L}/])"), "$1 $word")
        // Proposals and plain symbols.
        t = t.replace(Regex("\\b(EIP|ERC|BIP)-(\\d+)")) { it.groupValues[1].toList().joinToString(" ") + " " + it.groupValues[2] }
        t = t.replace(Regex("(?i)\\be\\.g\\.,?"), "for example").replace(Regex("(?i)\\bi\\.e\\.,?"), "that is")
            .replace(Regex("(?i)\\betc\\."), "et cetera").replace(Regex("\\bvs\\.?(?= )"), "versus").replace(Regex("(?i)\\bapprox\\."), "about")
        t = t.replace("&", " and ").replace(Regex("[()\\[\\]]"), ", ").replace(Regex("https?://\\S+"), "")
            .replace(Regex("[–—]"), ", ").replace("/", " or ").replace(Regex("[#*_~|<>]"), " ")
        return t.replace(Regex("\\s+"), " ").replace(Regex("\\s*,(\\s*,)+"), ",").replace(Regex(" ([.,;:!?])"), "$1")
            .replace(Regex("^[,\\s]+"), "").replace(Regex(",\\s*([.!?])"), "$1").trim()
    }

    private fun time(h: String, m: String): String {
        val hour = h.toInt(); val minute = m.toInt()
        if (hour > 23 || minute > 59) return "$h $m"
        val (spokenHour, suffix) = when {
            hour == 0 -> 12 to " am"
            hour == 12 -> 12 to " pm"
            hour > 12 -> (hour - 12) to " pm"
            else -> hour to ""
        }
        return when {
            minute == 0 && suffix.isEmpty() -> "$spokenHour o'clock"
            minute == 0 -> "$spokenHour$suffix"
            minute < 10 -> "$spokenHour oh $minute$suffix"
            else -> "$spokenHour $minute$suffix"
        }
    }
}
