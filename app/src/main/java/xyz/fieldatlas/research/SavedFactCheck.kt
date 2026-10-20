package xyz.fieldatlas.research

import java.util.Locale
import xyz.fieldatlas.text.MarkdownLiteral

/**
 * An answer written from the model's own knowledge is checked against the passages found for the question: where a
 * passage about the same subject gives a different figure for the same unit, or a different year for the same event,
 * that passage's sentence is quoted under the answer with its citation. The answer itself is not changed, so the
 * check can only add, never thin an answer or lengthen it with the model's reading of the passage: on set 34, giving
 * the model three passages as notes made its answers three times longer and wronger (1.31 to 1.68 errors per answer).
 *
 * Lexical only, and conservative: the same unit on both sides (no conversions), the question's words in both
 * sentences, ranges and rounding allowed for, and at most two notes. It does not decide which side is right.
 */
internal object SavedFactCheck {
    const val HEADING = "_Your saved sources give a different figure:_"
    /** Opens the one line under a from-memory answer; the screen knows the route by it, on old answers too. */
    const val KNOWLEDGE_PREFIX = "_From the model's knowledge"
    /** Earlier wordings of that line, still in saved answers. */
    val OLD_KNOWLEDGE_NOTES = listOf("_Written from the model's knowledge; a bracketed number marks a figure your saved passages state too._",
        "_Written from the model's knowledge, without the saved passages listed below._")
    private val cited = Regex("\\[S?\\d+]")
    /** "From 1961 to 1989", "between 1961 and 1989", "1961–1989": the years a thing began and ended. */
    private val yearSpan = Regex("(?i)\\b(?:from|between)\\s+(1[0-9]{3}|20[0-9]{2})\\s+(?:to|and|until|till)\\s+(1[0-9]{3}|20[0-9]{2})\\b|" +
        "\\b(1[0-9]{3}|20[0-9]{2})\\s*[-–]\\s*(1[0-9]{3}|20[0-9]{2})\\b")
    private val startGroups: Set<Int> by lazy { events.withIndex().filter { (_, r) -> listOf("open\\w*", "found\\w*", "built|", "began|").any { it in r.pattern } }.map { it.index }.toSet() }
    private val endGroups: Set<Int> by lazy { events.withIndex().filter { (_, r) -> "ended|" in r.pattern }.map { it.index }.toSet() }

    /** The years a passage sentence spans, when it gives both. */
    private fun spanOf(sentence: String): Pair<Int, Int>? = yearSpan.find(sentence)?.let { m ->
        val v = m.groupValues.drop(1).filter { it.isNotEmpty() }.map(String::toInt)
        if (v.size == 2) v[0] to v[1] else null
    }
    private val sentenceTail = Regex("[.!?)*_\\s]*$")
    private val bulletStart = Regex("^\\s*(?:[-*•]|\\d+[.)])\\s")

    /**
     * The line under a from-memory answer: where it came from and how many of its figures a saved passage states too;
     * [cited] sentences the model took from the passages it was given to read, for a subject it barely knows.
     */
    fun knowledgeNote(marks: Int, passages: Int, cited: Int = 0): String = when {
        cited > 0 && marks > 0 -> "$KNOWLEDGE_PREFIX and your saved sources; ${cited + marks} sentences cite them._"
        cited > 0 -> "$KNOWLEDGE_PREFIX and your saved sources; $cited sentence${if (cited == 1) "" else "s"} cite${if (cited == 1) "s" else ""} them._"
        passages == 0 -> "$KNOWLEDGE_PREFIX; nothing saved on this phone covers it._"
        marks == 0 -> "$KNOWLEDGE_PREFIX; not checked against your sources._"
        marks == 1 -> "$KNOWLEDGE_PREFIX; 1 figure matches your sources._"
        else -> "$KNOWLEDGE_PREFIX; $marks figures match your sources._"
    }
    /** A line [knowledgeNote] or an earlier wording wrote: the answer was written from the model's knowledge. */
    fun isKnowledgeNote(line: String): Boolean = line.trim().startsWith(KNOWLEDGE_PREFIX) || line.trim() in OLD_KNOWLEDGE_NOTES

    /**
     * [answer] with " [Sn]" after each sentence about the asked subject whose figure, or whose year of an event, a
     * passage about that subject states the same ([Quantity.agrees]): the mark says a saved passage backs that
     * sentence, so the passages read as sources at the foot of the answer and not as a stack of excerpts above it.
     * One mark per sentence, the lowest-numbered passage; the same gates as [note], and sentences already cited,
     * headings, quotes and the app's own notes are left alone.
     */
    fun marks(question: String, answer: String, sources: List<PromptSource>): String {
        if (sources.isEmpty() || answer.isBlank()) return answer
        if (EvidenceRelevance.conceptualComparisonSubjects(question).isNotEmpty() || comparison.containsMatchIn(question)) return answer
        val terms = subjectTerms(question)
        if (terms.size < 2) return answer
        if (answerSentences(answer).count { arithmetic.containsMatchIn(it) } >= 2) return answer
        // A passage about the subject, then its sentences that name it or follow one that does: "The building has 163
        // floors" states the Burj Khalifa's floors when the sentence before it named the tower.
        val passages = sources.mapNotNull { source ->
            val citation = source.citationId.takeIf { Regex("S[1-9][0-9]*").matches(it) } ?: return@mapNotNull null
            val text = EvidenceRelevance.passageText(source.evidence.text)
            if (EvidenceRelevance.termHits(text, terms) < MIN_PASSAGE_HITS) return@mapNotNull null
            var named = false
            citation to text.split(sentenceEnd).map(String::trim).filter { sentence ->
                val names = EvidenceRelevance.termHits(sentence, terms) >= 1
                val about = names || named
                named = names
                about && wholeSentence(sentence) && sentence.length in 12..MAX_QUOTE && !enumeration.containsMatchIn(sentence) &&
                    quantities(sentence).map { q -> q.unit }.toSet().size <= MAX_UNITS_IN_PASSAGE
            }
        }
        if (passages.all { it.second.isEmpty() }) return answer
        var previousOnSubject = false
        var inBody = true
        val lines = answer.lines().map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("## Saved excerpts") || trimmed.startsWith("## From your saved sources")) inBody = false
            if (!inBody || trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(">") || trimmed.startsWith("_")) return@map line
            val out = StringBuilder()
            var cursor = 0
            val ends = sentenceEnd.findAll(line).map { it.range.first }.toList() + line.length
            for (end in ends) {
                if (end <= cursor) continue
                val segment = line.substring(cursor, end)
                val gap = line.substring(end, ends.getOrNull(ends.indexOf(end) + 1)?.let { minOf(it, line.length) } ?: line.length)
                cursor = end
                val clean = stripMarks.replace(markup.replace(segment.trim(), ""), "").trim()
                val onSubject = EvidenceRelevance.termHits(clean, terms) >= 1
                val eligible = (onSubject || previousOnSubject) && clean.length >= 8 && !cited.containsMatchIn(segment) &&
                    !enumeration.containsMatchIn(clean) && quantities(clean).map { q -> q.unit }.toSet().size <= MAX_UNITS_IN_CLAIM
                // The bullets under a lead about the subject are about it too ("**Floors:** It has 163 floors").
                previousOnSubject = onSubject || bulletStart.containsMatchIn(segment) && previousOnSubject
                val citation = if (eligible) backing(question, clean, passages) else null
                if (citation == null) out.append(segment) else {
                    val tail = sentenceTail.find(segment)!!.range.first
                    out.append(segment, 0, tail).append(" [").append(citation).append("]").append(segment, tail, segment.length)
                }
            }
            out.toString()
        }
        return lines.joinToString("\n")
    }

    /** The first passage (by number) with a sentence stating the same figure or event year as [clean]. */
    private fun backing(question: String, clean: String, passages: List<Pair<String, List<String>>>): String? {
        val claimed = placed(clean).filter { p ->
            val q = p.quantity
            q.unit !in shareUnits && q.unit !in countUnits || q.unit in shareUnits && askedShare.containsMatchIn(question) ||
                q.unit in countUnits && askedCount.containsMatchIn(question)
        }
        val ourYear = years(clean).singleOrNull()
        if (claimed.isEmpty() && ourYear == null) return null
        for ((citation, sentences) in passages) {
            for (sentence in sentences) {
                val stated = placed(sentence)
                val figure = claimed.any { ours ->
                    val same = stated.filter { it.quantity.unit == ours.quantity.unit }
                    val comparable = if (ours.quantity.unit in durations) same.filter { theirs ->
                        context(clean, ours.range).intersect(context(sentence, theirs.range)).isNotEmpty() } else same
                    comparable.any { it.quantity.agrees(ours.quantity) }
                }
                val ourEvent = ourYear?.let { eventOf(clean, it) }
                val year = ourYear != null && ourYear in years(sentence) && !relativeYear.containsMatchIn(sentence) &&
                    ourEvent != null && ourEvent == eventOf(sentence, ourYear)
                // "Start date: 1961" against "from 1961 to 1989": the span's first year is a start, its last an end.
                val span = ourYear?.let { y -> spanOf(sentence)?.let { (first, last) ->
                    ourEvent in startGroups && y == first || ourEvent in endGroups && y == last } } ?: false
                if (figure || year || span) return citation
            }
        }
        return null
    }
    private const val MAX_NOTES = 2
    private const val MAX_QUOTE = 320
    private const val MIN_PASSAGE_HITS = 2
    private const val SAME = 0.02
    private const val ABOUT = 0.10

    /** A quantity as a sentence states it, by its unit family: metres and feet are not compared. */
    internal data class Quantity(val value: Double, val unit: String, val about: Boolean, val range: Pair<Double, Double>? = null) {
        fun agrees(other: Quantity): Boolean {
            if (unit != other.unit) return true
            fun within(v: Double, r: Pair<Double, Double>) = v >= r.first * (1 - SAME) && v <= r.second * (1 + SAME)
            range?.let { if (within(other.value, it)) return true }
            other.range?.let { if (within(value, it)) return true }
            val tolerance = if (about || other.about) ABOUT else SAME
            val scale = maxOf(kotlin.math.abs(value), kotlin.math.abs(other.value), 1e-9)
            return kotlin.math.abs(value - other.value) / scale <= tolerance
        }
    }

    private val units: Map<String, String> = buildMap {
        fun family(key: String, vararg names: String) = names.forEach { put(it, key) }
        family("m", "m", "metre", "metres", "meter", "meters")
        family("km", "km", "kilometre", "kilometres", "kilometer", "kilometers")
        family("cm", "cm", "centimetre", "centimetres", "centimeter", "centimeters")
        family("mm", "mm", "millimetre", "millimetres", "millimeter", "millimeters")
        family("ft", "ft", "feet", "foot")
        family("mi", "mi", "mile", "miles")
        family("km/h", "km/h", "kph", "kmh", "kilometres per hour", "kilometers per hour")
        family("mph", "mph", "miles per hour")
        family("m/s", "m/s", "metres per second", "meters per second")
        family("knots", "knots", "knot", "kn")
        family("kg", "kg", "kilogram", "kilograms", "kilo", "kilos")
        family("g", "g", "gram", "grams")
        family("lb", "lb", "lbs", "pound", "pounds")
        family("t", "tonne", "tonnes", "ton", "tons")
        family("°C", "°c", "° c", "degrees celsius", "celsius", "c")
        family("°F", "°f", "° f", "degrees fahrenheit", "fahrenheit", "f")
        family("%", "%", "percent", "per cent")
        family("floors", "floors", "floor", "storeys", "storey", "stories", "story")
        family("years", "years", "year", "yrs", "yr")
        family("months", "months", "month")
        family("weeks", "weeks", "week")
        family("days", "days", "day")
        family("hours", "hours", "hour", "hrs", "hr")
        family("minutes", "minutes", "minute", "mins", "min")
        family("seconds", "seconds", "second", "secs", "sec")
        family("people", "people", "inhabitants", "residents", "population", "passengers", "visitors", "members", "speakers")
        family("km²", "km²", "km2", "sq km", "square kilometres", "square kilometers", "square km")
        family("mi²", "mi²", "sq mi", "square miles")
        family("hectares", "hectares", "hectare", "ha")
        family("acres", "acres", "acre")
        family("litres", "litres", "litre", "liters", "liter", "l")
        family("ml", "ml", "millilitres", "milliliters")
        family("species", "species")
        family("countries", "countries", "country", "nations", "states")
        family("languages", "languages", "language")
        family("islands", "islands", "island")
        family("episodes", "episodes", "episode")
        family("seats", "seats", "seat")
        family("players", "players", "player")
        family("moons", "moons", "moon")
        family("calories", "calories", "kcal", "calorie")
        family("mg", "mg", "milligrams", "milligram")
        family("mcg", "mcg", "µg", "micrograms", "microgram")
        family("mAh", "mah")
        family("GB", "gb", "gigabytes", "gigabyte")
        family("MB", "mb", "megabytes", "megabyte")
        family("Hz", "hz", "hertz")
        family("MHz", "mhz")
        family("GHz", "ghz")
        family("W", "watts", "watt")
        family("kW", "kw", "kilowatts", "kilowatt")
        family("V", "volts", "volt")
        family("dB", "db", "decibels", "decibel")
        family("bpm", "bpm", "beats per minute")
        family("mmHg", "mmhg")
    }
    private val unitNames = units.keys.sortedByDescending { it.length }
    private val unitPattern = unitNames.joinToString("|") { Regex.escape(it) }
    /** "828 m", "2,717 ft", "163 floors", "5,000-year", "2.1 million people", "320–390 km/h", "829.8m". */
    private val quantity = Regex("(?i)(?<![\\p{L}\\p{N}.,])(\\d{1,3}(?:,\\d{3})+|\\d+|\\ba|\\ban|\\bone)(?:\\.(\\d+))?" +
        "(?:\\s*(?:-|–|—|to)\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?)?" +
        "(?:\\s*(thousand|million|billion|trillion))?[\\s\\u00a0-]*(" + unitPattern + ")(?![\\p{L}\\p{N}])")
    private val approximate = Regex("(?i)\\b(?:about|around|roughly|approximately|approx\\.?|nearly|almost|some|close to|just (?:over|under)|" +
        "over|under|more than|less than|up to|at least|at most|estimated|an estimated|circa|c\\.)\\s*$|~\\s*$")
    private val multipliers = mapOf("thousand" to 1e3, "million" to 1e6, "billion" to 1e9, "trillion" to 1e12)
    /** "100 years of the French Revolution", "446 km of trail": a measure of something else, not of the subject. */
    private val ofSomething = Regex("^\\s*of\\b")
    /** "19 years old", "a 19-year-old", "aged 19": an age, not a length of time. */
    private val ageAfter = Regex("^[\\s-]*old\\b")
    private val ageBefore = Regex("(?i)\\b(?:aged|age of|at the age of)\\s*$")
    private val durations = setOf("years", "months", "weeks", "days", "hours", "minutes", "seconds")
    private val contextWords = Regex("[\\p{L}]{3,}")
    private val contextStop = setOf("the", "and", "for", "with", "that", "this", "from", "into", "than", "then", "when", "which", "while",
        "about", "over", "under", "after", "before", "within", "least", "most", "only", "just", "very", "more", "less", "some",
        "been", "being", "were", "was", "are", "has", "have", "had", "its", "his", "her", "their", "they", "them", "you", "your",
        "not", "but", "also", "around", "roughly", "approximately", "nearly", "almost", "between")

    /** A calendar year, alone: not "1,990 m" and not part of a longer number or a date range's second half. */
    private val year = Regex("(?<![\\p{N}.,/-])(1[0-9]{3}|20[0-9]{2})(?![\\p{N}]|\\s*(?:" + "%|m\\b|km\\b|ft\\b|kg\\b|g\\b|mg\\b" + "))")
    /** What a year is the year of; an event in both sentences, from the same group, ties their years together. */
    private val events: List<Regex> = listOf(
        "happen\\w*|occur\\w*|took place|takes place|taking place|struck|hit|erupt\\w*|exploded|explosion|disaster|accident|crash\\w*|sank|sinking",
        "open\\w*|inaugurat\\w*|unveil\\w*",
        "launch\\w*|lift(?:ed)? off|blast(?:ed)? off|took off",
        "born|birth",
        "died|death|dies|killed|assassinat\\w*|executed",
        "found\\w*|establish\\w*|set up|created|creation|formed|formation|incorporat\\w*",
        "built|build\\w*|construct\\w*|complet\\w*|finish\\w*|erected",
        "began|begin\\w*|start\\w*|commenc\\w*|broke out|outbreak",
        "ended|end\\w*|ceas\\w*|conclud\\w*|abolish\\w*|repeal\\w*|dissolv\\w*|fell|fall of|collapse\\w*|taken down|torn down|" +
            "demolish\\w*|dismantl\\w*|destroy\\w*|razed|shut down|closed down",
        "discover\\w*|invent\\w*|first (?:described|observed|made|flown|used|synthesi[sz]ed)|isolated|identified",
        "publish\\w*|releas\\w*|premier\\w*|aired|broadcast\\w*|came out|printed",
        "sign\\w*|ratif\\w*|treaty|agreement|adopted|enacted|passed",
        "independen\\w*|liberat\\w*|unif\\w*|partition\\w*",
        "won|win\\w*|victory|defeat\\w*|champion\\w*|medal\\w*|record",
        "elect\\w*|became|crown\\w*|coronat\\w*|took office|inaugurated as|appointed",
        "married|marriage|wedding|divorce\\w*",
        "first (?:ascent|climbed|summit\\w*|reached|crossed|landed|landing)|summited|climbed|reached the summit|landed",
        "arrest\\w*|captur\\w*|imprison\\w*|jailed|detained|deport\\w*|exiled|sentenced|convicted",
    ).map { Regex("(?i)\\b(?:$it)\\b") }

    /** Two things set against each other: a passage about one is no check on a figure about the other. */
    private val comparison = Regex("(?i)\\b(?:vs\\.?|versus|compar\\w*|differences? between|which (?:is|one is|ones are|are) (?:\\w+er|more|less|best|worst)|" +
        "better than|worse than|or (?:the|a|an) \\w+\\?)\\b")
    /** Words of a question that name no subject: measures, time words, and what every question says. */
    private val generic = setOf("day", "days", "time", "times", "first", "last", "next", "every", "each", "give", "gave", "given", "take",
        "took", "exactly", "total", "about", "long", "tall", "high", "fast", "far", "big", "old", "many", "much", "number", "today",
        "tomorrow", "yesterday", "same", "held", "hold", "actually", "actualy", "really", "true", "count", "counting", "world", "largest",
        "smallest", "biggest", "highest", "longest", "fastest", "oldest", "tallest", "deepest", "best", "worst", "know", "mean", "means",
        "work", "works", "way", "thing", "things", "people", "person", "country", "city", "place", "year", "years", "month", "months",
        "week", "weeks", "hour", "hours", "minute", "minutes", "second", "seconds", "per", "half", "whole", "like", "just", "still",
        "ever", "also", "get", "got", "need", "needs", "use", "used", "make", "made", "say", "said", "called", "does", "did", "done",
        "going", "went", "come", "came", "start", "started", "end", "ended", "new", "good", "bad", "right", "left", "back")
    /** The question's words that name its subject: not numbers, not units, not the words every question has. */
    private fun subjectTerms(question: String): List<String> = FtsQuery.from(question)?.terms.orEmpty()
        .filter { t -> !t.all(Char::isDigit) && t !in units && t !in generic && t.length >= 3 }
    /** An answer that works a sum from the question's own figures: those figures are the person's, not facts to check. */
    private val arithmetic = Regex("(?i)(?:=|×|÷|\\bdivided by\\b|\\bmultiplied by\\b|\\btimes\\b.*=|\\bsubtract\\w*\\b|\\badd(?:ing)?\\b.*\\bhours?\\b)")
    /** "The following year", "a year later": the sentence's year is not the year of every event in it. */
    private val relativeYear = Regex("(?i)\\b(?:the (?:following|next|previous|same) year|years? (?:later|earlier|before|after)|a year (?:later|earlier)|" +
        "the year (?:before|after)|later that year|earlier that year|within (?:a|the) year)\\b")
    /** "2, 3, 5, or 7-year", "1990, 1995 and 2001": a run of numbers compares or lists, it states no single figure. */
    private val enumeration = Regex("\\d[\\d,.]*\\s*,\\s*\\d[\\d,.]*\\s*,?\\s*(?:,\\s*\\d[\\d,.]*\\s*)*(?:or|and|&)\\s*\\d|\\d[\\d,.]*(?:\\s*,\\s*\\d[\\d,.]*){2,}")
    private const val MAX_UNITS_IN_CLAIM = 3
    /** Shares and counts of things: "60% of seaborne trade" against "90% capacity" compares nothing, unless a share was asked. */
    private val askedShare = Regex("(?i)\\b(?:percent\\w*|per cent|share|proportion|fraction|how much of|what part of|rate)\\b|%")
    private val askedCount = Regex("(?i)\\b(?:how many|number of|population|how big|how large|count)\\b")
    private val shareUnits = setOf("%")
    private val countUnits = setOf("people", "countries", "languages", "species", "islands", "episodes", "seats", "players", "moons")
    private const val MAX_UNITS_IN_PASSAGE = 3
    private val sentenceEnd = Regex("(?<=[.!?])\\s+|\\n+")
    /** A chunk cut mid-sentence opens with a fragment ("he sector boundary in Berlin"): not a sentence to quote or match. */
    private fun wholeSentence(sentence: String): Boolean = sentence.firstOrNull()?.let { it.isUpperCase() || it.isDigit() || it in "\"“(«¿¡" } == true
    private val markup = Regex("^(?:#{1,6}\\s.*|[-*•]\\s*|>\\s*|\\d+[.)]\\s*)")
    private val stripMarks = Regex("[*_`]")

    /** The note, or null when no passage about the asked subject disagrees with the answer. */
    fun note(question: String, answer: String, sources: List<PromptSource>): String? {
        if (sources.isEmpty() || answer.isBlank()) return null
        if (EvidenceRelevance.conceptualComparisonSubjects(question).isNotEmpty() || comparison.containsMatchIn(question)) return null
        val terms = subjectTerms(question)
        if (terms.size < 2) return null
        val sentences = answerSentences(answer)
        if (sentences.count { arithmetic.containsMatchIn(it) } >= 2) return null
        // Sentences about the asked subject, or right after one that is ("The Burj Khalifa … It stands 828 m tall"), with a
        // figure or two: a run of numbers compares things and states none.
        val onSubject = sentences.map { EvidenceRelevance.termHits(it, terms) >= 1 }
        val said = sentences.filterIndexed { i, it -> (onSubject[i] || i > 0 && onSubject[i - 1]) &&
            !enumeration.containsMatchIn(it) && quantities(it).map { q -> q.unit }.toSet().size <= MAX_UNITS_IN_CLAIM }
        val claimed = said.flatMap { s -> placed(s).map { s to it } }.filter { (_, p) ->
            val q = p.quantity
            q.unit !in shareUnits && q.unit !in countUnits || q.unit in shareUnits && askedShare.containsMatchIn(question) ||
                q.unit in countUnits && askedCount.containsMatchIn(question)
        }
        val claimedYears = said.mapNotNull { s -> years(s).singleOrNull()?.let { s to it } }
        if (claimed.isEmpty() && claimedYears.isEmpty()) return null
        val notes = LinkedHashMap<String, String>()
        for (source in sources) {
            val citation = source.citationId.takeIf { Regex("S[1-9][0-9]*").matches(it) } ?: continue
            val sentences = EvidenceRelevance.passageText(source.evidence.text).split(sentenceEnd).map(String::trim)
                .filter { wholeSentence(it) && it.length in 12..MAX_QUOTE && EvidenceRelevance.termHits(it, terms) >= MIN_PASSAGE_HITS &&
                    !enumeration.containsMatchIn(it) && quantities(it).map { q -> q.unit }.toSet().size <= MAX_UNITS_IN_PASSAGE }
            for (sentence in sentences) {
                if (notes.size >= MAX_NOTES) break
                val stated = placed(sentence)
                val disagrees = claimed.any { (ourSentence, ours) ->
                    val same = stated.filter { it.quantity.unit == ours.quantity.unit }
                    // A length of time belongs to an event: "ruled for 9 years" says nothing about "20 years" of a permit.
                    // The two sentences must say it of the same thing, in a word beside the figure.
                    val comparable = if (ours.quantity.unit in durations) same.filter { theirs ->
                        context(ourSentence, ours.range).intersect(context(sentence, theirs.range)).isNotEmpty() } else same
                    comparable.isNotEmpty() && comparable.none { it.quantity.agrees(ours.quantity) }
                } || claimedYears.any { (ourSentence, ourYear) ->
                    val theirs = years(sentence)
                    theirs.size == 1 && theirs.single() != ourYear && !relativeYear.containsMatchIn(sentence) &&
                        eventOf(ourSentence, ourYear) != null && eventOf(ourSentence, ourYear) == eventOf(sentence, theirs.single())
                }
                if (disagrees) notes.putIfAbsent(sentence.lowercase(Locale.ROOT), "“${MarkdownLiteral.encode(sentence)}” [$citation]")
            }
            if (notes.size >= MAX_NOTES) break
        }
        if (notes.isEmpty()) return null
        return HEADING + " " + notes.values.joinToString(" ") + " _Check which is right._"
    }

    /** [note] placed under the answer and above the saved excerpts, so the quote sits by what it disagrees with. */
    fun insert(shown: String, note: String): String {
        val marker = "\n\n## Saved excerpts\n\n"
        val at = shown.indexOf(marker)
        return if (at < 0) shown.trimEnd() + "\n\n" + note else shown.substring(0, at) + "\n\n" + note + shown.substring(at)
    }

    /** The answer's own sentences, without markdown marks, headings and the excerpts the app lists under it. */
    private fun answerSentences(answer: String): List<String> {
        val body = answer.substringBefore("\n## Saved excerpts").substringBefore("\n## From your saved sources")
        return body.lines().map { markup.replace(it.trim(), "") }.filter { it.isNotEmpty() && !it.startsWith("Model explanation") }
            .flatMap { it.split(sentenceEnd) }.map { stripMarks.replace(it, "").trim() }.filter { it.length >= 8 }
    }

    private fun number(whole: String, fraction: String?): Double =
        (whole.replace(",", "") + (fraction?.let { ".$it" } ?: "")).toDouble()

    /** A quantity with where it sits, so its neighbours can be compared across sentences. */
    internal data class Placed(val quantity: Quantity, val range: IntRange)

    internal fun quantities(sentence: String): List<Quantity> = placed(sentence).map { it.quantity }

    internal fun placed(sentence: String): List<Placed> = quantity.findAll(sentence).mapNotNull { m ->
        var unit = units[m.groupValues[6].lowercase(Locale.ROOT).replace("\u00a0", " ")] ?: return@mapNotNull null
        val after = sentence.substring(m.range.last + 1)
        val before = sentence.substring(0, m.range.first)
        // A share is of something by nature ("20% of the trade"); any other measure "of" a thing measures that thing.
        if (unit != "%" && ofSomething.containsMatchIn(after)) return@mapNotNull null
        if (unit == "years" && (ageAfter.containsMatchIn(after) || ageBefore.containsMatchIn(before))) unit = "age"
        val wordNumber = m.groupValues[1].lowercase(Locale.ROOT) in setOf("a", "an", "one")
        val scale = multipliers[m.groupValues[5].lowercase(Locale.ROOT)] ?: 1.0
        // A one-letter unit is read only when written as one: "5 g" yes, "the 5 G network" no; a bare year with "c" is not Celsius.
        if (unit in setOf("g", "°C", "°F", "t", "litres") && m.groupValues[6].length == 1 && !m.value.contains(Regex("\\d\\s?[gcflCFL]$"))) return@mapNotNull null
        val first = (if (wordNumber) 1.0 else number(m.groupValues[1], m.groupValues[2].ifEmpty { null })) * scale
        val second = m.groupValues[3].ifEmpty { null }?.let { number(it, m.groupValues[4].ifEmpty { null }) * scale }
        val about = approximate.containsMatchIn(before.takeLast(24))
        Placed(if (second != null && second > first) Quantity(first, unit, about, first to second) else Quantity(first, unit, about), m.range)
    }.toList()

    /** The words of a sentence around a quantity: four before it and two after, without the small words. */
    private fun context(sentence: String, at: IntRange): Set<String> {
        val before = contextWords.findAll(sentence.substring(0, at.first)).map { it.value.lowercase(Locale.ROOT) }.toList().takeLast(4)
        val after = contextWords.findAll(sentence.substring(at.last + 1)).map { it.value.lowercase(Locale.ROOT) }.take(2).toList()
        return (before + after).filter { it !in contextStop && it !in units }.toSet()
    }

    internal fun years(sentence: String): List<Int> = year.findAll(sentence).map { it.groupValues[1].toInt() }.distinct().toList()

    /** The event a sentence's year belongs to: the event word nearest the year, as a group index; null when none. */
    internal fun eventOf(sentence: String, year: Int): Int? {
        val at = sentence.indexOf(year.toString()).takeIf { it >= 0 } ?: return null
        return events.withIndex().flatMap { (i, group) -> group.findAll(sentence).map { i to kotlin.math.abs(it.range.first - at) } }
            .minByOrNull { it.second }?.first
    }
}
