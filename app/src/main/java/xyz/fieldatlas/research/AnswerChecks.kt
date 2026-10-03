package xyz.fieldatlas.research

/**
 * Deterministic checks on evidence-only answers. A small model can name the right fact
 * and still attach it to the wrong source number, convert a time incorrectly, or answer a
 * question the files cannot answer with whatever details they do contain. These checks
 * compare the answer against the supplied passages instead of trusting the model.
 */
object AnswerChecks {
    private val citation = Regex("\\[(?:S)?(\\d+)]", RegexOption.IGNORE_CASE)
    // The label must be capitalised or numeric ("Revision B", "Plan 2"), so "plan to" is not one.
    private val labelled = Regex("\\b((?i:revision|rev\\.?|version|model|plan|option|section|part|appendix|annex|table|figure|form|schedule|plot|zone|room|gate|platform|phase|step))[\\s_-]+([A-Z0-9]{1,3})\\b")
    private val code = Regex("\\b([A-Za-z]{1,4})-?(\\d{1,5}[A-Za-z]?)\\b")
    private val number = Regex("(?<![\\p{L}\\p{N}.,])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?![\\p{L}\\p{N}])")

    /**
     * Distinctive tokens: labelled identifiers ("revision b"), part codes ("k9") and numbers.
     * Ordinary words are ignored because two sources on one subject share most of them.
     */
    internal fun tokens(text: String): Set<String> {
        val plain = text.replace('_', ' ')
        val out = linkedSetOf<String>()
        labelled.findAll(plain).forEach { out += it.groupValues[1].lowercase().trimEnd('.').let { k -> if (k == "rev") "revision" else k } + " " + it.groupValues[2].lowercase() }
        code.findAll(plain).forEach { out += (it.groupValues[1] + it.groupValues[2]).lowercase() }
        number.findAll(plain).forEach { out += it.groupValues[1].replace(",", "") }
        return out
    }

    /**
     * Moves a citation to the one other source that a claim's distinctive details point to.
     * A claim is the text between the previous sentence boundary (or citation) and its marker.
     * It changes nothing when the claim names details unique to the cited source, details from
     * several sources, or none unique to any source, so a supported citation is never moved.
     */
    fun repairCitations(raw: String, sources: List<Evidence>): String {
        if (sources.size < 2) return raw
        val sourceTokens = sources.map { tokens(it.title + "\n" + it.text) }
        val owners = mutableMapOf<String, MutableSet<Int>>()
        sourceTokens.forEachIndexed { index, set -> set.forEach { owners.getOrPut(it) { mutableSetOf() } += index } }
        val unique = owners.filterValues { it.size == 1 }.mapValues { it.value.single() }
        val out = StringBuilder()
        var claimStart = 0
        var cursor = 0
        for (match in citation.findAll(raw)) {
            val boundary = Regex("(?<=[.!?:])\\s+|\\n").findAll(raw.substring(0, match.range.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
            val claim = raw.substring(maxOf(claimStart, boundary), match.range.first)
            val cited = match.groupValues[1].toInt() - 1
            val pointed = tokens(claim).mapNotNull { unique[it] }.toSet()
            val target = if (cited in sources.indices && pointed.size == 1 && cited !in pointed) pointed.single() else cited
            out.append(raw, cursor, match.range.first)
            out.append(if (target == cited) match.value else "[S${target + 1}]")
            cursor = match.range.last + 1
            claimStart = cursor
        }
        out.append(raw, cursor, raw.length)
        return out.toString()
    }

    private val twelveThen24 = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?\\s?m\\.?\\s*\\((\\d{1,2}):(\\d{2})\\)")
    private val twentyFourThen12 = Regex("(?i)\\b(\\d{1,2}):(\\d{2})\\s*\\((\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?\\s?m\\.?\\)")

    private fun to12(hour: Int, minute: Int): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "$h:${"%02d".format(minute)} ${if (hour < 12) "AM" else "PM"}"
    }

    private fun to24(hour: Int, minute: Int, pm: Boolean): Int = (hour % 12 + if (pm) 12 else 0) * 60 + minute

    /**
     * Fixes a 12-hour time written beside a 24-hour one when the two disagree, keeping the
     * 24-hour time the sources actually state ("10:30 PM (21:30)" becomes "9:30 PM (21:30)").
     * Pairs whose 24-hour value is not in the sources are left alone.
     */
    fun repairTimes(text: String, sources: List<Evidence>): String {
        val corpus = sources.joinToString("\n") { it.text }
        fun stated(hour: Int, minute: Int) = Regex("(?<![\\d:])0?$hour:${"%02d".format(minute)}(?![\\d])").containsMatchIn(corpus)
        val first = twelveThen24.replace(text) { m ->
            val (h12, m12, ap, h24, m24) = m.destructured
            val hour = h24.toInt(); val minute = m24.toInt()
            val agrees = hour < 24 && minute < 60 && to24(h12.toInt(), m12.ifEmpty { "0" }.toInt(), ap.equals("p", true)) == hour * 60 + minute
            if (agrees || hour >= 24 || minute >= 60 || !stated(hour, minute)) m.value else "${to12(hour, minute)} ($h24:$m24)"
        }
        return twentyFourThen12.replace(first) { m ->
            val (h24, m24, h12, m12, ap) = m.destructured
            val hour = h24.toInt(); val minute = m24.toInt()
            val agrees = hour < 24 && minute < 60 && to24(h12.toInt(), m12.ifEmpty { "0" }.toInt(), ap.equals("p", true)) == hour * 60 + minute
            if (agrees || hour >= 24 || minute >= 60 || !stated(hour, minute)) m.value else "$h24:$m24 (${to12(hour, minute)})"
        }
    }

    private val askedItem = Regex("(?i)\\b(?:what(?:'s|\\s+is|\\s+are|\\s+was)|tell me|give me|send me|share)\\s+(?:the|my|our|their|its|a|an)\\s+([\\p{L}][\\p{L}\\p{N}-]*(?:\\s+[\\p{L}][\\p{L}\\p{N}-]*){0,2}?)\\s+(?:at|for|of|in|on|to|from)\\s+([^?.!\\n]{2,60})")
    private val alternatives = mapOf(
        "password" to listOf("passcode", "pass code", "pin", "login", "network key"),
        "wifi" to listOf("wireless", "network", "wlan"),
        "phone" to listOf("telephone", "tel", "call", "mobile", "+"),
        "number" to listOf("tel", "+"),
        "email" to listOf("e-mail", "mail", "@"),
        "address" to listOf("street", "road", "avenue", "located"),
        "cost" to listOf("price", "fee", "fare", "charge", "€", "$", "£", "rate"),
        "price" to listOf("cost", "fee", "fare", "charge", "€", "$", "£", "rate"),
        "fee" to listOf("price", "cost", "fare", "charge", "€", "$", "£", "free"),
        "time" to listOf(":", "am", "pm", "o'clock", "noon", "midnight"),
        "code" to listOf("pin", "passcode"),
    )

    /**
     * When a question asks for one specific item ("the Wi-Fi password at Cedar Lodge") and no
     * supplied passage mentions any of its words or common alternatives, returns the asked-for
     * phrase so the app can say the files do not contain it instead of offering other details.
     */
    fun missingItem(question: String, sources: List<Evidence>): String? {
        if (sources.isEmpty()) return null
        val match = askedItem.find(question) ?: return null
        val item = match.groupValues[1].trim()
        fun norm(value: String) = value.lowercase().replace(Regex("(?<=\\p{L})-(?=\\p{L})"), "")
        val corpus = norm(sources.joinToString("\n") { it.title + "\n" + it.text })
        val words = norm(item).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return null
        fun mentioned(word: String): Boolean {
            val stem = word.removeSuffix("es").removeSuffix("s").takeIf { it.length >= 3 } ?: word
            return Regex("\\b${Regex.escape(stem)}").containsMatchIn(corpus) ||
                alternatives[word].orEmpty().any { alt -> if (alt.first().isLetter()) Regex("\\b${Regex.escape(alt)}\\b").containsMatchIn(corpus) else corpus.contains(alt) }
        }
        if (words.any(::mentioned)) return null
        val place = match.groupValues[2].trim().trimEnd(',', ';')
        return "the $item ${match.value.substringAfter(match.groupValues[1]).trim().substringBefore(place).trim()} $place".replace(Regex("\\s+"), " ")
    }
}
