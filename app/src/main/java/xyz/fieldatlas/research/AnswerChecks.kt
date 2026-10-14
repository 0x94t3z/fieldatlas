package xyz.fieldatlas.research

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
     * The claim is its whole sentence up to the marker; a colon does not end it, so "Revision B
     * service interval: 400 hours" keeps both "Revision B" and "400", and a list item also carries
     * the line that introduces its list. It changes nothing when the
     * sentence names details unique to the cited source, details from several sources, or none
     * unique to any source. A wrong claim therefore stays on the source it cited instead of being
     * moved to one that makes it look supported.
     */
    fun repairCitations(raw: String, sources: List<Evidence>): String {
        if (sources.size < 2) return raw
        val sourceTokens = sources.map { tokens(it.title + "\n" + it.text) }
        val owners = mutableMapOf<String, MutableSet<Int>>()
        sourceTokens.forEachIndexed { index, set -> set.forEach { owners.getOrPut(it) { mutableSetOf() } += index } }
        val unique = owners.filterValues { it.size == 1 }.mapValues { it.value.single() }
        fun pointedBy(text: String) = tokens(text.replace(citation, " ")).mapNotNull { unique[it] }.toSet()
        val bullet = Regex("^\\s*(?:[-*•+]|\\d+[.)])\\s")
        // A list item belongs to the line that introduces its list ("Revision B:" above
        // "- Service interval: 400 hours"), across blank lines and the list's other items.
        fun listLead(end: Int): String {
            val lines = raw.substring(0, end).split("\n")
            if (!bullet.containsMatchIn(lines.last())) return ""
            var i = lines.size - 2
            while (i >= 0 && (lines[i].isBlank() || bullet.containsMatchIn(lines[i]))) i--
            return if (i >= 0) lines[i] else ""
        }
        val out = StringBuilder()
        var cursor = 0
        for (match in citation.findAll(raw)) {
            val sentenceStart = Regex("(?<=[.!?])\\s+|\\n").findAll(raw.substring(0, match.range.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
            val claim = listLead(match.range.first) + "\n" + raw.substring(sentenceStart, match.range.first)
            val cited = match.groupValues[1].toIntOrNull()?.minus(1) ?: -1
            // "According to [1], revision A uses K-7": with nothing distinctive before the marker,
            // the claim is the rest of its sentence, up to the next marker.
            val after = raw.substring(match.range.last + 1).let { rest ->
                val end = Regex("[.!?](?=\\s|$)|\\n|\\[(?:S)?\\d+]", RegexOption.IGNORE_CASE).find(rest)?.range?.first ?: rest.length
                rest.substring(0, end)
            }
            val pointed = pointedBy(claim).ifEmpty { pointedBy(after) }
            val target = if (cited in sources.indices && pointed.size == 1 && cited !in pointed) pointed.single() else cited
            out.append(raw, cursor, match.range.first)
            out.append(if (target == cited) match.value else "[S${target + 1}]")
            cursor = match.range.last + 1
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

    private val asksTwelveHour = Regex("(?i)\\b12[- ]?hour|\\bam\\s*/\\s*pm\\b|\\ba\\.m\\.|\\bp\\.m\\.")
    private val twentyFour = Regex("(?<![\\d:.])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d:])(?!\\s*(?:[ap]\\.?\\s?m\\b|\\())", RegexOption.IGNORE_CASE)

    /**
     * When the question asks for 12-hour times, writes the conversion beside every 24-hour time
     * the answer gives ("21:30" becomes "21:30 (9:30 PM)"), so the conversion never depends on
     * the model. Times already followed by a 12-hour form or inside one are left alone.
     */
    fun addTwelveHour(text: String, question: String): String {
        if (!asksTwelveHour.containsMatchIn(question)) return text
        // A quotation must stay exactly as the file has it.
        val quoted = Regex("[\"“][^\"”\\n]*[\"”]").findAll(text).map { it.range }.toList()
        return twentyFour.replace(text) { m ->
            val before = text.substring(maxOf(0, m.range.first - 6), m.range.first)
            if (quoted.any { m.range.first in it } || Regex("(?i)[ap]\\.?\\s?m\\.?\\s*\\($").containsMatchIn(before)) m.value
            else "${m.value} (${to12(m.groupValues[1].toInt(), m.groupValues[2].toInt())})"
        }
    }

    private val decisionQuestion = Regex("(?i)\\b(should|decisions?|decide|allowed|permitted|eligible|qualif\\w*|can i|may i|must i|do i need)\\b")
    private val exceptionWording = Regex("(?i)\\b(exceptions?|except|unless|holds?|overrides?|overridden|exempt\\w*|waivers?)\\b")

    /**
     * The 2B model sometimes decides by a general rule and misses the exception that overrides
     * it (watering a plot under a maintenance hold). For a decision question about files that
     * contain exception wording, returns a note pointing the reader back to those exceptions.
     * It does not judge the answer; it only says where the answer is most likely to slip.
     */
    fun exceptionNote(question: String, sources: List<Evidence>): String? {
        if (sources.isEmpty() || !decisionQuestion.containsMatchIn(question)) return null
        val words = sources.flatMap { exceptionWording.findAll(it.text).map { m -> m.value.lowercase() }.toList() }.distinct()
        if (words.isEmpty()) return null
        val named = words.take(2).joinToString(" and ") { "\"$it\"" }
        return "Check each decision against the file's exceptions ($named): this model can miss one."
    }

    private val askedItem = Regex("(?i)\\b(?:what(?:'s|\\s+is|\\s+are|\\s+was)|tell me|give me|send me|share)\\s+(?:the|my|our|their|its|a|an)\\s+([\\p{L}][\\p{L}\\p{N}-]*(?:\\s+[\\p{L}][\\p{L}\\p{N}-]*){0,2}?)(?:\\s+(at|for|of|in|on|to|from)\\s+([^?.!\\n]{2,60}))?\\s*(?:[?.!]|$)")

    /**
     * Items a passage either states recognisably or does not have: a credential, a phone number,
     * an email address. Anything else (an address, a price, a time) can be written in too many
     * ways to conclude it is absent, so the check stays silent and the model answers. Each kind
     * is decided by the item's own noun: "free Wi-Fi in the lobby" is not a Wi-Fi password.
     */
    private enum class Kind(val asked: Regex, val present: Regex) {
        PASSWORD(Regex("(?i)\\b(pass ?words?|passcodes?|pass codes?|pins?|codes?)\\b"),
            Regex("(?i)\\b(pass ?words?|passcodes?|pass codes?|pin|network key|wpa2?|codes?)\\b")),
        PHONE(Regex("(?i)\\b(phone|telephone)(?: numbers?)?\\b|\\bnumbers? to call\\b"),
            Regex("(?i)\\b(phone|tel|telephone|call|mobile|whatsapp)\\b|\\+?\\d[\\d ().-]{6,}\\d")),
        EMAIL(Regex("(?i)\\b(e-?mails?)(?: address(?:es)?)?\\b"),
            Regex("(?i)\\be-?mail\\b|[\\w.+-]+@[\\w-]+\\.[\\w.]+")),
    }

    /**
     * When a question asks for a password, phone number or email address and no supplied passage
     * contains one, returns the asked-for phrase ("the Wi-Fi password at Cedar Lodge") so the app
     * can say the files don't mention it instead of offering other details.
     */
    fun missingItem(question: String, sources: List<Evidence>): String? {
        if (sources.isEmpty()) return null
        val match = askedItem.find(question) ?: return null
        val item = match.groupValues[1].trim()
        val kind = Kind.entries.firstOrNull { it.asked.containsMatchIn(item) } ?: return null
        val corpus = sources.joinToString("\n") { it.title + "\n" + it.text }
        if (kind.present.containsMatchIn(corpus)) return null
        val place = match.groupValues[3].trim().trimEnd(',', ';')
        return if (place.isEmpty()) "the $item" else "the $item ${match.groupValues[2].lowercase()} $place"
    }

    // Something the person can only do with a connection.
    private val onlineTarget = Regex(
        "(?i)\\b(online|on the internet|on the web|internet search|search engines?|google|websites?|web ?sites?|web ?pages?|" +
            "social media|instagram|facebook|tripadvisor|yelp|happycow|booking\\.com|online reviews?|reviews online|" +
            // "the app", "the Grab app", "a translation app".
            "(?:an?|the|this|that|your|their)\\s+(?:[\\p{L}-]+\\s+){0,2}app\\b|apps\\b|app stores?|play store|e-?mail them)\\b|https?://|www\\.",
    )
    // Phrasing that tells the person to do it, as opposed to describing it.
    private val advice = Regex(
        "(?i)(^|[\\s(\"“])(try|check|visit|search|look (?:it |this |them |that )?up|use|consult|download|install|book|buy|order|reserve|get|pay|register|" +
            "see|browse|open|sign up|subscribe|" +
            "consider|contact|email|read)\\b|\\b(?:you|they) (?:can|could|should|may want to|might want to|might|will need to|need to)\\b|" +
            "\\b(?:i |we )?recommend\\b|\\bit(?:'s| is) (?:best|advisable|recommended|a good idea) to\\b|\\b(?:be|make) sure to\\b",
    )
    private val offline = Regex("(?i)\\boff-?line\\b")
    private val later = Regex("(?i)\\b(?:when|once|next time|after) you(?:'re| are)? (?:back )?online\\b|\\b(?:when|once) you have (?:a )?(?:connection|signal|internet)\\b")
    // "To get to the centre without internet access, take..." narrates the person's situation back to them.
    private val narratedClause = Regex(
        "(?i)^((?:[-*] |\\d+\\. )?(?:\\*\\*)?(?:To|For) [^,.:;]{3,120}?)\\s+(?:without|with no)\\s+(?:an? )?(?:internet|online|network|data)(?: (?:access|connection|connectivity))?(?=,)",
    )
    // Only when the sentence then speaks about people: "Without internet access, travellers rely on
    // maps" loses the clause, but "Without data, the eSIM won't activate" keeps its condition.
    private val narratedOpening = Regex(
        "(?i)^((?:[-*] |\\d+\\. )?(?:\\*\\*)?)(?:(?:without|with no)\\s+|(?:since|as|because) you (?:have no|don't have|do not have) )(?:an? )?(?:internet|network|data|a connection)(?: (?:access|connection|connectivity))?,\\s*" +
            "((?:you|one|travell?ers|visitors|people|participants|users|tourists|verify|check|ask|use|look|try|find|keep|carry|bring)\\b)",
    )
    private val narratedHeading = Regex("(?i)\\s+(?:without|with no) (?:the )?internet(?: access)?(?=:?\\*\\*|:\\s*$)")
    private val heading = Regex("^\\s*(?:#{1,6} |\\*\\*[^*]+(?::\\*\\*|\\*\\*:)\\s*$)")
    private val noConnectionSentence = Regex(
        "(?i)^(?:[-*] |\\d+\\. )?(?:Since|As|Because|Without) (?:you (?:have no|don't have|do not have|are (?:offline|without))|(?:an? )?(?:internet|connection))\\b[^.!?]*\\b(?:cannot|can't|can not|unable|not possible|won't be able)\\b",
    )
    // The question is about the online service itself ("Do I need a SIM card?", "Is there Wi-Fi?"), so naming it is
    // the answer. A train "link" or visitor "data" is not.
    private val asksAboutOnline = Regex("(?i)\\b(online|internet|websites?|web ?sites?|apps?|google|social media|e-?mail|url|wi-?fi|" +
        "mobile data|data (?:plan|roaming|sim|package|bundle)|sim(?: card)?s?|e-?sim|roaming)\\b")
    // "Buy tickets at the station or online": the offline half is the advice; only the alternative goes.
    private val onlineAlternative = Regex("(?i),?\\s+or\\s+(?:book |buy |check |look |search )?(?:online|on (?:the|their|its) (?:official )?website|on google maps|via (?:the|an|their) app|in (?:the|an|their) app)\\b")
    // A sentence that offers something to do besides what can't be done keeps that part.
    private val alternative = Regex("(?i)(?:,\\s*(?:so|but|instead)|;\\s*|\\s+(?:so|but|instead)\\s+)")

    // An amount of medicine: "400 mg", "200-400 mg", "10 g (150 mg/kg) per day", "2 tablets", "5 ml".
    private val doseAmount = Regex("(?i)(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?\\s*(?:(?:-|–|to)\\s*\\d+(?:[.,]\\d+)?\\s*)?" +
        "(mg|mcg|µg|micrograms?|milligrams?|grams?|g|ml|millilit(?:re|er)s?|iu|tablets?|pills?|capsules?|drops?)(?:\\s*/\\s*kg)?(?![\\p{L}\\p{N}])")
    private val medicineWord = Regex("(?i)\\b(?:doses?|dosage|dosing|overdoses?|tablets?|pills?|capsules?|medicines?|medications?|drugs?|" +
        "painkillers?|ibuprofen|paracetamol|acetaminophen|aspirin|naproxen|antihistamines?|antibiotics?|insulin|codeine|morphine|syrup)\\b")
    // Units that only ever measure medicine here; grams and millilitres also measure food.
    private val medicineUnits = setOf("mg", "mcg", "µg", "microgram", "micrograms", "milligram", "milligrams", "iu")
    private val sentenceEnd = Regex("(?<=[.!?])\\s+")
    private val sourceHeading = Regex("(?i)^\\W*from saved sources\\W*$")

    // A kitchen measure in a home remedy: "6 level teaspoons", "half a teaspoon", "2 drops", "¼ cup".
    private val householdAmount = Regex(
        "(?i)(?<![\\p{L}\\p{N}])(\\d+(?:[.,/]\\d+)?|½|¼|¾|one|two|three|four|five|six|seven|eight|nine|ten|twelve|half(?: an?| of a)?|a quarter(?: of a)?|an?)\\s+" +
            "(?:(?:-|–|to)\\s*\\d+\\s+)?(?:level |heaped |heaping |small |large |big |flat )?" +
            "(teaspoons?|tsps?|tablespoons?|tbsps?|cups?|pinch(?:es)?|grams?|g|litres?|liters?|ml|drops?|capfuls?|caps?)(?![\\p{L}\\p{N}])",
    )
    private val remedyIngredient = Regex("(?i)\\b(sugar|salt|baking soda|bicarbonate(?: of soda)?|bicarb|bleach|chlorine|glucose|honey)\\b")
    // Where a home mixture is the treatment and an amount wrong by a factor can do harm.
    private val remedyContext = Regex(
        "(?i)\\b(dehydrat\\w*|rehydrat\\w*|diarrh?o?ea|vomit\\w*|throw(?:ing|n)? up|threw up|electrolytes?|ors|oral rehydration|" +
            "purif\\w*|disinfect\\w*|sterili[sz]\\w*|(?:make|making|treat\\w*) (?:the |stream |river |lake )?water (?:safe|drinkable)|drinking water|safe to drink)\\b",
    )
    private val quantityWords = mapOf(
        "a" to "1", "an" to "1", "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6", "seven" to "7",
        "eight" to "8", "nine" to "9", "ten" to "10", "twelve" to "12", "½" to "1/2", "¼" to "1/4", "¾" to "3/4",
    )
    private val unitWords = mapOf("tsp" to "teaspoon", "tbsp" to "tablespoon", "g" to "gram", "liter" to "litre", "capful" to "cap")

    /** "six level teaspoons" and "6 tsp" are the same amount: quantity and unit in one spelling. */
    private fun amount(match: MatchResult): String {
        val raw = match.groupValues[1].lowercase()
        val quantity = when {
            raw.startsWith("half") -> "1/2"
            raw.startsWith("a quarter") -> "1/4"
            else -> quantityWords[raw] ?: raw.replace(',', '.')
        }
        val unit = match.groupValues[2].lowercase().removeSuffix("es").removeSuffix("s").let { unitWords[it] ?: it }
        return "$quantity $unit"
    }

    /**
     * Removes amounts of medicine from the model's own explanation, sentence by sentence, and says
     * so. A 2B model turned a source's overdose figure ("150 mg/kg") into a recommended dose; the
     * number was in the source, so checking numbers against sources cannot catch it. Exact
     * quotations under "From saved sources" are the sources' own words and stay.
     *
     * For dehydration, diarrhoea or water treatment, a kitchen-measure amount of sugar, salt, soda
     * or bleach goes too unless the same amount is in [sources]: the model once gave a rehydration
     * drink one sixth of the sugar it needs and credited the WHO.
     */
    fun removeDoses(text: String, question: String, sources: List<Evidence> = emptyList()): String {
        val aboutMedicine = medicineWord.containsMatchIn(question)
        fun isDose(sentence: String) = doseAmount.findAll(sentence).any { m ->
            m.groupValues[1].lowercase() in medicineUnits || aboutMedicine || medicineWord.containsMatchIn(sentence)
        }
        val remedy = remedyContext.containsMatchIn(question)
        val stated = sources.flatMap { source -> householdAmount.findAll(source.text).map(::amount).toList() }.toSet()
        fun isRecipe(sentence: String) = (remedy || remedyContext.containsMatchIn(sentence)) && remedyIngredient.containsMatchIn(sentence) &&
            householdAmount.findAll(sentence).any { amount(it) !in stated }
        var quotes = false
        var removedDose = false
        var removedRecipe = false
        val kept = text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            // A saved source is quoted word for word under its heading; any other heading ("Model explanation")
            // ends that part, and only lines that are themselves quoted are left as written.
            if (sourceHeading.matches(trimmed)) quotes = true
            else if (otherHeading.matches(trimmed)) quotes = false
            val body = trimmed.replace(Regex("^(?:[-*•+]|\\d+[.)])\\s+"), "")
            val quoted = body.startsWith(">") || body.startsWith("\"") || body.startsWith("“")
            if ((quotes && quoted) || !(isDose(line) || isRecipe(line))) return@mapNotNull line
            val sentences = line.split(sentenceEnd)
            val rest = sentences.filterNot { isDose(it) || isRecipe(it) }
            if (sentences.any(::isDose)) removedDose = true
            if (sentences.any(::isRecipe)) removedRecipe = true
            // A list item or line that was only an amount goes; "1. Take 400 mg." leaves no step.
            rest.joinToString(" ").takeIf { it.trim().trimStart('-', '*', '•').trim().replace(Regex("^\\d+[.)]\\s*"), "").any(Char::isLetter) }
        }
        if (!removedDose && !removedRecipe) return text
        return kept.joinToString("\n").trimEnd() + listOfNotNull(DOSE_NOTE.takeIf { removedDose }, RECIPE_NOTE.takeIf { removedRecipe })
            .joinToString("") { "\n\n$it" }
    }

    private val otherHeading = Regex("^(?:#{1,6}\\s+.+|\\*\\*[^*]{1,60}\\*\\*:?|[A-Z][^.!?]{0,60}:)$")

    /** [text] without the notes [removeDoses] adds, and those notes in order. */
    fun withoutAppNotes(text: String): Pair<String, List<String>> {
        val notes = listOf(DOSE_NOTE, RECIPE_NOTE).filter { it in text }
        return notes.fold(text) { t, note -> t.replace(note, "") }.replace(Regex("\\n{3,}"), "\n\n").trim() to notes
    }

    const val DOSE_NOTE = "_Amounts of medicine are left out of this explanation: follow the package leaflet, or ask a pharmacist or doctor._"
    const val RECIPE_NOTE = "_Amounts for a home mixture are left out of this explanation because the saved sources do not give them: " +
        "use a packaged oral rehydration sachet if you have one, or ask a pharmacist or health worker for the mixture._"

    private val warningWord = Regex("(?i)\\b(do not|don'?t|never|avoid|must not|mustn'?t|should not|shouldn'?t)\\b")
    private val listMarker = Regex("^(\\s*)(?:([-*•+])|(\\d+)([.)]))\\s+")
    private val sentenceOrLine = Regex("(?<=[.!?])\\s+|\\n")

    /**
     * Keeps a "do not" only when the cited guide says it. The model adds warnings of its own to a
     * guide's steps ("do not give food or drink unless they can swallow", under a heat guide that
     * says not to give fluids at all) and, cited, they read as the guide's. A warning stays when
     * the best single sentence of the cited source (of any source, when it cites none) shares at
     * least three of its content words or at least half of them; otherwise the sentence or step
     * goes and the steps are renumbered.
     */
    fun removeUnsupportedWarnings(text: String, sources: List<Evidence>): String {
        if (sources.isEmpty() || !warningWord.containsMatchIn(text)) return text
        val sentences = sources.map { source -> sentenceOrLine.split(source.text).map { QueryTerms.of(it).toSet() } }
        fun supported(sentence: String): Boolean {
            // The warning words themselves are not content: "don't give fluids" is about fluids.
            val words = QueryTerms.of(warningWord.replace(sentence.replace(citation, " "), " ")).toSet()
            if (words.isEmpty()) return true
            val markers = citation.findAll(sentence).toList()
            val cited = markers.mapNotNull { it.groupValues[1].toIntOrNull()?.minus(1)?.takeIf { index -> index in sources.indices } }
            if (markers.isNotEmpty() && cited.isEmpty()) return false
            val pool = (if (markers.isEmpty()) sources.indices.toList() else cited).flatMap { sentences[it] }
            val shared = pool.maxOfOrNull { it.intersect(words).size } ?: 0
            return shared >= 3 || shared * 2 >= words.size
        }
        var changed = false
        val lines = text.split("\n").mapNotNull { line ->
            if (!warningWord.containsMatchIn(line)) return@mapNotNull line
            val marker = listMarker.find(line)?.value.orEmpty()
            val parts = sentenceEnd.split(line.substring(marker.length))
            val kept = parts.filter { !warningWord.containsMatchIn(it) || supported(it) }
            if (kept.size == parts.size) return@mapNotNull line
            changed = true
            (marker + kept.joinToString(" ")).takeIf { kept.any { part -> part.any(Char::isLetter) } }
        }
        if (!changed) return text
        return renumbered(lines).joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trimEnd()
    }

    // Every way of saying "get help", in one spelling, so "call emergency services" matches "call 911".
    private val callForHelp = Regex(
        "(?i)\\b(?:(?:call|contact|phone|ring|dial)\\s+(?:911|112|999|000|the emergency number|(?:your|the) local emergency number|" +
            "emergency services|an ambulance|emergency medical (?:help|services?|assistance|attention))|" +
            "(?:seek|get)(?: \\w+){0,3} (?:medical|emergency) (?:help|attention|care|assistance))\\b",
    )
    private const val CALL_FOR_HELP = " call emergency help "
    private val getHelp = Regex("(?i)\\bcall emergency help\\b")
    /** A sentence this short that says to get help says nothing else that could be invented. */
    private const val GET_HELP_WORDS = 6
    private val bodySite = Regex(
        "(?i)\\b(arms?|forearms?|lips?|thighs?|legs?|mouth|tongue|hands?|fingers?|toes?|foot|feet|chest|neck|stomach|abdomen|belly|" +
            "buttocks?|shoulders?|knees?|ankles?|wrists?|elbows?|eyes?|ears?|nose|throat|groin|hips?|cheeks?|jaw|shins?|calf|calves|forehead|scalp|torso|waist)\\b",
    )
    private val namedMedicine = Regex(
        "(?i)\\b(doses?|dosage|dosing|tablets?|pills?|capsules?|medicines?|medications?|drugs?|painkillers?|ibuprofen|paracetamol|acetaminophen|" +
            "aspirin|naproxen|antihistamines?|antibiotics?|insulin|codeine|morphine|syrup|epinephrine|adrenaline|epi-?pens?|auto-?injectors?|antivenom|" +
            "ointments?|creams?|lotions?|petroleum jelly|aloe vera|benadryl|diphenhydramine|charcoal|ors|electrolytes?|glucose|sugar|salt|honey|" +
            "inhalers?|ventolin|nitroglycerin|oxygen|iodine|chlorine|bleach)\\b",
    )
    private val bareNumber = Regex("^\\d+[.)]$")
    private val inlineNumber = Regex("^\\s*\\d+[.)]\\s+")
    private val headingLine = Regex("^\\s*(?:#{1,6} |\\*\\*[^*]+\\*\\*:?\\s*$|_[^_]+_\\s*$)")

    private fun canonical(text: String) = callForHelp.replace(text, CALL_FOR_HELP)

    /** "[S1]. 2. Hold..." splits as "2." then "Hold...": a bare marker rejoins its sentence. */
    private fun glueInlineNumbers(parts: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (part in parts) {
            if (out.isNotEmpty() && bareNumber.matches(out.last().trim())) out[out.lastIndex] = out.last() + " " + part else out += part
        }
        return out
    }

    private class Pool(val sentences: List<Set<String>>, val tokens: Set<String>, val numbers: Set<String>, val text: String)
    private class Grounded(val sentence: String, val invention: String?, val ratio: Double, val supported: Boolean)

    /**
     * Keeps a sentence or list item of the model's answer only when the cited guide section (any
     * source, when it cites none) grounds it: as for a warning, its best single sentence shares at
     * least three of the step's content words or at least half of them. Under a guide the model
     * invented "inject in the arm" (auto-injectors go in the thigh), "on the lip" and "take the
     * person's temperature first", each cited to a guide that says no such thing, so a sentence
     * that names a body site, a medicine or an amount of it, or a number or time the guide text
     * never contains goes whatever its overlap. Telling the person to get emergency help is never
     * an invention: the app's own note says it. When nothing passes, nothing from the model is
     * retained; the orchestrator already displays the official excerpt. This is a conservative
     * lexical filter, not a guarantee of clinical correctness. Steps are renumbered.
     */
    fun removeUnsupportedSteps(text: String, sources: List<Evidence>): String {
        if (sources.isEmpty()) return text
        val pools = sources.map { source ->
            val canon = canonical(source.text)
            Pool(
                sentenceOrLine.split(canon).map { QueryTerms.of(it).toSet() },
                QueryTerms.tokens(canon).toSet(),
                number.findAll(canon).map { it.groupValues[1].replace(",", "") }.toSet(),
                canon.lowercase(),
            )
        }
        fun poolFor(sentence: String): List<Pool> {
            val cited = citation.findAll(sentence).map { it.groupValues[1].toIntOrNull()?.minus(1) }.toList()
            return if (cited.isEmpty()) pools else cited.mapNotNull { it?.takeIf { index -> index in pools.indices }?.let(pools::get) }
        }
        fun judge(sentence: String): Grounded {
            val pool = poolFor(sentence)
            if (pool.isEmpty()) return Grounded(sentence, "unknown citation", 0.0, false)
            val plain = canonical(citation.replace(inlineNumber.replace(sentence, ""), " "))
            val tokens = pool.flatMap { it.tokens }.toSet()
            val numbers = pool.flatMap { it.numbers }.toSet()
            val corpus = pool.joinToString(" ") { it.text }
            val invention = bodySite.findAll(plain).firstOrNull { QueryTerms.stem(it.value.lowercase()) !in tokens }?.let { "body site \"${it.value}\"" }
                ?: namedMedicine.findAll(plain).firstOrNull { QueryTerms.stem(it.value.lowercase()) !in tokens && it.value.lowercase() !in corpus }?.let { "medicine \"${it.value}\"" }
                ?: doseAmount.findAll(plain).firstOrNull { it.value.lowercase() !in corpus }?.let { "dose \"${it.value}\"" }
                ?: number.findAll(plain).firstOrNull { it.groupValues[1].replace(",", "") !in numbers }?.let { "number \"${it.groupValues[1]}\"" }
            // The warning words themselves are not content: "don't give fluids" is about fluids.
            val words = QueryTerms.of(warningWord.replace(plain, " ")).toSet()
            if (words.isEmpty() || (getHelp.containsMatchIn(plain) && words.size <= GET_HELP_WORDS)) return Grounded(sentence, invention, 1.0, true)
            val shared = pool.flatMap { it.sentences }.maxOfOrNull { it.intersect(words).size } ?: 0
            return Grounded(sentence, invention, shared.toDouble() / words.size, shared >= 3 || shared * 2 >= words.size)
        }
        val lines = text.split("\n")
        val judged: List<Pair<String, List<Grounded>>?> = lines.map { line ->
            if (line.isBlank() || headingLine.containsMatchIn(line)) return@map null
            val marker = listMarker.find(line)?.value.orEmpty()
            marker to glueInlineNumbers(sentenceEnd.split(line.substring(marker.length)).filter(String::isNotBlank)).map(::judge)
        }
        val steps = judged.filterNotNull().flatMap { it.second }
        if (steps.isEmpty()) return text
        val survivors = steps.filter { it.invention == null && it.supported }.toSet()
        if (survivors.isEmpty()) return ""
        if (survivors.size == steps.size) return text
        val out = lines.zip(judged).mapNotNull { (line, parts) ->
            if (parts == null) return@mapNotNull line
            val kept = parts.second.filter { it in survivors }
            when {
                kept.size == parts.second.size -> line
                kept.any { step -> step.sentence.any(Char::isLetter) } -> parts.first + kept.joinToString(" ") { it.sentence }
                else -> null
            }
        }
        return renumbered(out).joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trimEnd()
    }

    /** Numbered steps counted again from 1 after a removal; a paragraph between lists starts a new count. */
    private fun renumbered(lines: List<String>): List<String> {
        var count = 0
        return lines.map { line ->
            val marker = listMarker.find(line)
            when {
                marker != null && marker.groupValues[3].isNotEmpty() -> {
                    count++
                    "${marker.groupValues[1]}$count${marker.groupValues[4]} ${line.substring(marker.range.last + 1)}"
                }
                marker == null && line.isNotBlank() -> { count = 0; line }
                else -> line
            }
        }
    }

    /**
     * The person asking has no connection, so an answer must not send them online. A sentence
     * or list item that tells them to search the web, visit a site, read online reviews or use
     * an app is removed; a sentence that only mentions such things ("Google Maps can't route
     * offline") stays. Questions about online services themselves are left alone.
     */
    // Where a sentence's clauses part: "; ", ", or …", ", but …", and "or with/through/using … app".
    private val onlineClauseBreak = Regex("(?i)\\s*;\\s*|,\\s*(?=(?:or|and|but|while|though|although)\\b)|\\s+(?=or\\s+(?:with|through|using|via|in|on|by|from)\\b)")
    private val citationRun = Regex("\\s*\\[S\\d+](?:\\s*\\[S\\d+])*")

    /**
     * "Tickets cost 2 EUR; pay by card at the machine or with the HSL app [S1]." keeps "Tickets cost 2 EUR; pay by
     * card at the machine [S1].": only the clauses that name the app or site go, with the sentence's citations kept.
     * Null when nothing with content is left.
     */
    private fun withoutOnlineClauses(part: String): String? {
        // A quoted line is the source's own words: it is kept or removed whole, never cut down.
        if (part.trimStart().startsWith(">")) return null
        val cites = citationRun.findAll(part).map { it.value.trim() }.distinct().toList()
        val lead = Regex("^\\s*(?:[-*•+]|\\d+[.)])\\s+").find(part)?.value.orEmpty()
        val body = citationRun.replace(part.removePrefix(lead), "").trim().trimEnd('.', '!', '?', ' ')
        val pieces = mutableListOf<Pair<String, String>>()
        var start = 0
        var separator = ""
        for (b in onlineClauseBreak.findAll(body)) {
            pieces += separator to body.substring(start, b.range.first)
            separator = b.value
            start = b.range.last + 1
        }
        pieces += separator to body.substring(start)
        if (pieces.size < 2) return null
        val kept = pieces.filter { (_, clause) -> clause.isNotBlank() && !onlineTarget.containsMatchIn(clause) }
        if (kept.isEmpty() || kept.sumOf { (_, clause) -> Regex("\\p{L}{2,}").findAll(clause).count() } < 3) return null
        val joined = kept.mapIndexed { i, (sep, clause) ->
            val text = clause.trim()
            if (i == 0) Regex("(?i)^(?:or|and|but|while|though|although)\\s+").replace(text, "").replaceFirstChar(Char::uppercaseChar)
            else (if (";" in sep) "; " else if ("," in sep) ", " else " ") + text
        }.joinToString("")
        return lead + joined + (if (cites.isEmpty()) "" else " " + cites.joinToString(" ")) + "."
    }

    fun removeOnlineAdvice(text: String, question: String): String {
        if (asksAboutOnline.containsMatchIn(question)) return text
        val lines = text.split("\n")
        val kept = lines.map { line ->
            // Split a line into sentences but keep list markers and citations with their sentence.
            val parts = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}*_\"“(])").split(line)
            // "Open it in an offline maps app" is exactly the advice wanted; keep anything that says offline.
            val survivors = parts.map { part ->
                val trimmed = onlineAlternative.replace(part, "")
                if (trimmed != part && !onlineTarget.containsMatchIn(trimmed)) trimmed else part
            }.mapNotNull { part ->
                val online = onlineTarget.containsMatchIn(part) && advice.containsMatchIn(part) && !offline.containsMatchIn(part) &&
                    // "...or check a current source when you're online" defers honestly to later; it stays.
                    !later.containsMatchIn(part)
                if (!online) part else withoutOnlineClauses(part)
            }
            when {
                survivors == parts -> line
                survivors.isEmpty() -> null
                else -> survivors.joinToString(" ")
            }
        }
        if (kept.none { it == null } && kept == lines) return text
        // Drop removed lines, and a heading or blank run left behind with nothing under it.
        return kept.filterNotNull().joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /**
     * The person knows they are offline. "To reach the centre without internet access, take the
     * metro" becomes "To reach the centre, take the metro", a "How to check without internet:"
     * heading loses the qualifier, and a sentence that only says what can't be done without a
     * connection is removed. A statement about a thing ("maps cannot route without a connection")
     * is not about the person and stays.
     */
    fun removeOfflineNarration(text: String, question: String): String {
        if (asksAboutOnline.containsMatchIn(question)) return text
        val lines = text.split("\n").map { line ->
            val parts = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}*_\"“(])").split(line)
            parts.mapNotNull { part ->
                if (!noConnectionSentence.containsMatchIn(part) || Regex("\\[S\\d+]").containsMatchIn(part)) return@mapNotNull part
                // "Without internet you can't check bus times, so ask at reception": keep the advice.
                alternative.find(part)?.let { split -> part.substring(split.range.last + 1).trim().replaceFirstChar(Char::uppercaseChar) }
            }
                .map { narratedClause.replace(it, "$1") }
                .map { part -> narratedOpening.replace(part) { m -> m.groupValues[1] + m.groupValues[2].replaceFirstChar(Char::uppercaseChar) } }
                .map { if (heading.containsMatchIn(it)) narratedHeading.replace(it, "") else it }
                .joinToString(" ")
        }
        if (lines == text.split("\n")) return text
        return lines.joinToString("\n").replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /* ---------- arithmetic audit ---------- */

    private enum class Figure { NUMBER, DURATION, CLOCK }

    /**
     * One figure in a step line, as written: a number with its currency sign and unit ("€180",
     * "140 km/h", "15%"), a duration ("3 h 20 min", "45 minutes") or a clock time ("08:15").
     * [value] is the number itself, or the minutes of a duration or clock time.
     */
    private class Quantity(
        val kind: Figure, val value: Double, val text: String,
        val prefix: String = "", val suffix: String = "", val money: Boolean = false, val percent: Boolean = false,
        val decimals: Int = 0, val grouped: Boolean = false,
        val hourUnit: String = "h", val minuteUnit: String = "min", val paddedHour: Boolean = false,
    )

    private const val HOURS = "h|hrs?|hours?"
    private const val MINUTES = "m|mins?|minutes?"
    private const val CURRENCY_SIGNS = "[$€£¥₹]"
    // A figure with its sign, unit and, for a duration, its minutes; "x" is the operator, not a unit.
    private val quantityToken = Regex(
        "(?<![\\p{L}\\p{N}.,:])(?:$CURRENCY_SIGNS\\s?)?\\d[\\d,.:]*(?:\\s?%)?" +
            "(?:\\s?(?!(?i:and|or|for|of|to|in|at|on|the|a|an|is|are|then|so|plus|minus|each|x|by|from|with|per|times|over)\\b)\\p{L}+(?:/\\p{L}+)?)?" +
            "(?:\\s?(?:and\\s+)?\\d+\\s?(?:$MINUTES)\\b)?",
    )
    private val step = Regex("(${quantityToken.pattern})(?:\\s*([×*+\\-−–÷/])\\s*|\\s+([xX])\\s+)(${quantityToken.pattern})\\s*=\\s*(${quantityToken.pattern})")
    private val clockQuantity = Regex("^(\\d{1,2}):(\\d{2})$")
    private val durationQuantity = Regex("(?i)^(\\d+(?:\\.\\d+)?)\\s?($HOURS)(?:\\s?(?:and\\s+)?(\\d+)\\s?($MINUTES))?$|^(\\d+)\\s?($MINUTES)$")
    private val plainQuantity = Regex("^($CURRENCY_SIGNS\\s?)?(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?((?:\\s?%)?(?:\\s?\\p{L}+(?:/\\p{L}+)?)?)$")
    private val currencyWord = Regex("(?i)^\\s?(?:euros?|eur|pounds?|gbp|dollars?|usd|aud|cad|nzd|yen|jpy|francs?|chf|rupees?|inr|pesos?|baht|lira|zloty|krona|kronor|kroner|rand|won|yuan|rmb|dirhams?|riyals?|ringgit|dong|rupiah|shillings?|real|reais|cents?|pence|p)$")
    private val answerLine = Regex("(?i)^[\\s>*#-]*(?:final\\s+)?answer\\W*:")
    // The result written first, as a label's value: "Total cost: 92 (8 pizzas × 11.50)". "Pizzas for 77 slices
    // (77 ÷ 8) = 9" names an input before its step, so a result needs the colon or equals sign and no "=" after.
    private val stepBefore = Regex("(?<=[=:]\\s{0,3})(${quantityToken.pattern})\\s*\\(\\s*(${quantityToken.pattern})(?:\\s*([×*+\\-−–÷/])\\s*|\\s+([xX])\\s+)(${quantityToken.pattern})\\s*\\)(?!\\s*=)")
    private val chained = Regex("(?:[×*+\\-−–÷/=]|\\s[xX])\\s*$")
    private const val CORRECTED = " (corrected on this phone)"
    const val STEP_CORRECTED_NOTE = "_A step above was corrected on this phone; check the answer against the corrected steps._"

    private fun parseQuantity(raw: String): Quantity? {
        val text = raw.trimEnd('.', ',', ':', ';')
        clockQuantity.matchEntire(text)?.let { m ->
            val hour = m.groupValues[1].toInt(); val minute = m.groupValues[2].toInt()
            if (hour > 23 || minute > 59) return null
            return Quantity(Figure.CLOCK, (hour * 60 + minute).toDouble(), text, paddedHour = m.groupValues[1].length == 2)
        }
        durationQuantity.matchEntire(text)?.let { m ->
            if (m.groupValues[5].isNotEmpty()) return Quantity(Figure.DURATION, m.groupValues[5].toDouble(), text, minuteUnit = m.groupValues[6])
            val minutes = (m.groupValues[1].toDouble() * 60).roundToInt() + (m.groupValues[3].toIntOrNull() ?: 0)
            return Quantity(Figure.DURATION, minutes.toDouble(), text, hourUnit = m.groupValues[2], minuteUnit = m.groupValues[4].ifEmpty { "min" })
        }
        val m = plainQuantity.matchEntire(text) ?: return null
        val decimals = m.groupValues[3]
        val value = (m.groupValues[2].replace(",", "") + (if (decimals.isEmpty()) "" else ".$decimals")).toDouble()
        val suffix = m.groupValues[4]
        return Quantity(Figure.NUMBER, value, text, prefix = m.groupValues[1], suffix = suffix,
            money = m.groupValues[1].isNotEmpty() || currencyWord.matches(suffix), percent = suffix.contains('%'),
            decimals = decimals.length, grouped = m.groupValues[2].contains(','))
    }

    private fun near(a: Double, b: Double) = abs(a - b) < 1e-6

    private fun unit(q: Quantity): String = (q.prefix.trim() + q.suffix.trim()).lowercase(Locale.ROOT).let { raw ->
        when (raw) {
            "€", "eur", "euro", "euros" -> "eur"
            "$", "usd", "dollar", "dollars" -> "usd"
            "£", "gbp", "pound", "pounds" -> "gbp"
            else -> raw
        }
    }
    private fun sameUnit(a: Quantity, b: Quantity) = a.kind == b.kind && unit(a) == unit(b)

    /** What the step should give, in the kind the result was written in; null when the step is not one the app can check. */
    private fun expected(a: Quantity, op: Char, b: Quantity, writtenResult: Quantity): Double? {
        val result = writtenResult.kind
        return when {
        a.kind == Figure.NUMBER && b.kind == Figure.NUMBER &&
            (op !in listOf('+', '−') || sameUnit(a, b) || b.percent && !a.percent) -> {
            val value = when (op) {
                '×' -> if (a.percent && b.percent) a.value * b.value / (if (writtenResult.percent) 100 else 10000)
                    else if (a.percent) a.value / 100 * b.value else if (b.percent) a.value * b.value / 100 else a.value * b.value
                '+' -> if (b.percent && !a.percent) a.value * (1 + b.value / 100) else a.value + b.value
                '−' -> if (b.percent && !a.percent) a.value * (1 - b.value / 100) else a.value - b.value
                else -> (if (b.percent) b.value / 100 else b.value).takeIf { it != 0.0 }?.let {
                    (if (a.percent) a.value / 100 else a.value) / it * (if (writtenResult.percent) 100 else 1)
                }
            }
            when (result) {
                Figure.NUMBER -> value
                // "420 km ÷ 140 km/h = 3 h": a figure in hours, written as a duration.
                Figure.DURATION -> value?.takeIf { op == '÷' && !a.money &&
                    b.suffix.trim().endsWith("/h") && a.suffix.trim() == b.suffix.trim().removeSuffix("/h") }
                    ?.let { (it * 60).roundToInt().toDouble() }
                Figure.CLOCK -> null
            }
        }
        a.kind == Figure.CLOCK && b.kind == Figure.DURATION && result == Figure.CLOCK && op == '+' -> ((a.value + b.value) % 1440 + 1440) % 1440
        a.kind == Figure.CLOCK && b.kind == Figure.DURATION && result == Figure.CLOCK && op == '−' -> ((a.value - b.value) % 1440 + 1440) % 1440
        a.kind == Figure.CLOCK && b.kind == Figure.CLOCK && result == Figure.DURATION && op == '−' -> ((a.value - b.value) % 1440 + 1440) % 1440
        a.kind == Figure.DURATION && b.kind == Figure.DURATION && result == Figure.DURATION && op == '+' -> a.value + b.value
        a.kind == Figure.DURATION && b.kind == Figure.DURATION && result == Figure.DURATION && op == '−' -> a.value - b.value
        a.kind == Figure.DURATION && b.kind == Figure.NUMBER && result == Figure.DURATION && op == '×' -> (a.value * b.value).roundToInt().toDouble()
        a.kind == Figure.DURATION && b.kind == Figure.NUMBER && result == Figure.DURATION && op == '÷' && b.value != 0.0 -> (a.value / b.value).roundToInt().toDouble()
        a.kind == Figure.NUMBER && b.kind == Figure.DURATION && result == Figure.DURATION && op == '×' -> (a.value * b.value).roundToInt().toDouble()
        else -> null
        }
    }

    private fun decimal(value: Double, places: Int, grouped: Boolean): String =
        String.format(Locale.US, if (grouped) "%,.${places}f" else "%.${places}f", value)

    /**
     * Whether the written figure is the expected one, allowing the rounding its own precision
     * implies ("685" for 685.44) but not a figure that only rounds to it from far away ("1" for 0.6).
     */
    private fun agrees(written: Quantity, expected: Double): Boolean = when (written.kind) {
        Figure.NUMBER -> decimal(expected, written.decimals, false).toDouble() == written.value &&
            abs(expected - written.value) <= maxOf(0.005, abs(expected) / 100)
        else -> near(written.value, expected)
    }

    /** [value] written the way [like] was: same sign, unit, grouping and, where it had them, decimals; money has at most two. */
    private fun written(value: Double, like: Quantity): String = when (like.kind) {
        Figure.CLOCK -> {
            val minutes = value.roundToInt()
            String.format(Locale.US, if (like.paddedHour) "%02d:%02d" else "%d:%02d", minutes / 60, minutes % 60)
        }
        Figure.DURATION -> {
            val minutes = value.roundToInt()
            val h = minutes / 60; val m = minutes % 60
            when {
                h == 0 -> "$m ${like.minuteUnit}"
                m == 0 -> "$h ${like.hourUnit}"
                else -> "$h ${like.hourUnit} $m ${like.minuteUnit}"
            }
        }
        Figure.NUMBER -> {
            val whole = near(value, Math.rint(value))
            val core = when {
                whole -> decimal(value, like.decimals, like.grouped)
                like.money -> decimal(value, if (like.decimals in 1..2) like.decimals else 2, like.grouped)
                like.decimals > 0 -> decimal(value, like.decimals, like.grouped)
                else -> decimal(value, 2, like.grouped).trimEnd('0').trimEnd('.')
            }
            like.prefix + core + like.suffix
        }
    }

    private class Step(val operands: List<Quantity>, val result: Quantity, val expected: Double?) {
        val corrected: Boolean get() = expected != null && !near(expected, result.value)
    }

    /**
     * Recomputes every step a word-problem answer writes as "a op b = c" (also "a x b", "a * b",
     * "a / b", a percentage factor, "08:15 + 3 h 20 min = 11:35" and "3 h + 20 min = 3 h 20 min"), and
     * replaces a wrong result with the app's own, marked "(corrected on this phone)". A later step
     * that starts from a corrected figure is recomputed from the corrected one. When the final
     * "Answer:" line repeats a corrected figure it is rewritten too, provided the steps form one
     * chain (each starts from the previous result); otherwise, or when the answer's figures come
     * from no written step, a note says a step was corrected. A 2B model can set a sum up right
     * and still get the product wrong; the figures it was given stay as written.
     */
    fun checkArithmetic(text: String, question: String? = null): String {
        val steps = ArrayList<Step>()
        val inputs = question?.let { quantityToken.findAll(it).mapNotNull { token -> parseQuantity(token.value) }.toList() }.orEmpty()
        // Operands equal to an earlier wrong result take its corrected value, so the chain stays consistent.
        fun resolved(q: Quantity): Quantity {
            // Equal numbers may be unrelated quantities. Never overwrite a given input or a
            // different currency/unit just because it equals an earlier wrong intermediate.
            if (inputs.any { sameUnit(it, q) && near(it.value, q.value) }) return q
            val fix = steps.lastOrNull { it.corrected && sameUnit(it.result, q) && near(it.result.value, q.value) } ?: return q
            return Quantity(q.kind, fix.expected!!, written(fix.expected, q), q.prefix, q.suffix, q.money, q.percent, q.decimals, q.grouped,
                q.hourUnit, q.minuteUnit, q.paddedHour)
        }
        val lines = text.split("\n").map { line ->
            var touched = false
            val checked = step.replace(line) { m ->
                // "2 × 4 nights × €3.50 = €28.00" is one longer chain, not "4 × 3.50 = 28": left alone.
                if (chained.containsMatchIn(line.substring(0, m.range.first))) return@replace m.value
                val a = parseQuantity(m.groupValues[1]); val b = parseQuantity(m.groupValues[4]); val c = parseQuantity(m.groupValues[5])
                if (a == null || b == null || c == null) return@replace m.value
                val op = when (m.groupValues[2].ifEmpty { m.groupValues[3] }) { "×", "*", "x", "X" -> '×'; "+" -> '+'; "÷", "/" -> '÷'; else -> '−' }
                val ra = resolved(a); val rb = resolved(b)
                val expected = expected(ra, op, rb, c) ?: return@replace m.value
                steps += Step(listOf(a, b), c, expected)
                if (ra === a && rb === b && agrees(c, expected)) return@replace m.value
                touched = true
                // The match is rebuilt around its three figures; the operator, spacing and any trailing
                // punctuation the token swallowed ("= €720.") stay as written.
                val base = m.range.first
                fun between(left: Int, right: Int) = m.value.substring(m.groups[left]!!.range.last + 1 - base, m.groups[right]!!.range.first - base)
                fun rest(group: Int, parsed: Quantity) = m.groupValues[group].substring(parsed.text.length)
                ra.text + rest(1, a) + between(1, 4) + rb.text + rest(4, b) + between(4, 5) +
                    (if (agrees(c, expected)) c.text else written(expected, c)) + CORRECTED + rest(5, c)
            }
            // The same check for a step written result first, "72 (8 pizzas × 11.50)": only the result is replaced.
            val reversed = stepBefore.replace(checked) { m ->
                val c = parseQuantity(m.groupValues[1]); val a = parseQuantity(m.groupValues[2]); val b = parseQuantity(m.groupValues[5])
                if (a == null || b == null || c == null) return@replace m.value
                val op = when (m.groupValues[3].ifEmpty { m.groupValues[4] }) { "×", "*", "x", "X" -> '×'; "+" -> '+'; "÷", "/" -> '÷'; else -> '−' }
                val expected = expected(resolved(a), op, resolved(b), c) ?: return@replace m.value
                steps += Step(listOf(a, b), c, expected)
                if (agrees(c, expected)) return@replace m.value
                touched = true
                written(expected, c) + CORRECTED + m.groupValues[1].substring(c.text.length) + m.value.substring(m.groupValues[1].length)
            }
            if (touched) reversed else line
        }.toMutableList()
        val corrected = steps.filter { it.corrected }
        if (corrected.isEmpty()) return text
        val linear = steps.indices.drop(1).all { i ->
            steps[i].operands.any { it.kind == steps[i - 1].result.kind && near(it.value, steps[i - 1].result.value) }
        }
        val answerAt = lines.indexOfLast { answerLine.containsMatchIn(it) }
        if (answerAt >= 0) {
            var stale = false
            val rewritten = quantityToken.replace(lines[answerAt]) { m ->
                val q = parseQuantity(m.value) ?: return@replace m.value
                val fix = corrected.lastOrNull { sameUnit(it.result, q) && near(it.result.value, q.value) }
                if (fix == null) {
                    // A figure no written step produced was worked out in the model's head; with a step
                    // wrong above it, it cannot be trusted either.
                    if (steps.none { s -> s.result.kind == q.kind && (near(s.result.value, q.value) || s.expected?.let { near(it, q.value) } == true) }) stale = true
                    return@replace m.value
                }
                if (!linear) { stale = true; return@replace m.value }
                written(fix.expected!!, q) + CORRECTED + m.value.substring(q.text.length)
            }
            lines[answerAt] = rewritten
            if (stale) lines += listOf("", STEP_CORRECTED_NOTE)
        }
        return lines.joinToString("\n")
    }
}
