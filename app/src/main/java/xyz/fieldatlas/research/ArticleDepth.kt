package xyz.fieldatlas.research

/**
 * More of the article an explanation is about. Once a subject's article has been found by its title
 * or another of its names, the rest of its lead and the sections that fit the question are read from
 * the encyclopedia collections holding that article: English Wikipedia's longer lead and its
 * sections, and the Simple English encyclopedia's "Details". Never another article that a keyword
 * search happened to rank first ("Hot air balloon" for a question about a hot wallet), and never a
 * travel guide or map listing that shares the subject's name.
 */
object ArticleDepth {
    /** Reading time the extra passages add before the first word, at the phone's learned speed. */
    private const val SECONDS = 20.0
    private const val MIN_CHARS = 1_000
    private const val MAX_CHARS = 3_600
    private const val UNKNOWN_CHARS = 2_000
    /**
     * Rows read per article, the retriever's maximum. A vital English Wikipedia article runs to
     * well over a hundred chunks, and the section that answers ("Dissolution" of the Soviet Union)
     * is often far down it; rows are read by position, so the extra rows cost little.
     */
    const val ARTICLE_ROWS = 200
    /** Consecutive chunks of a passage repeat this many characters (packtool's chunk overlap). */
    private const val OVERLAP = 150
    /** Points for a section whose own heading answers the question's kind; a parent heading earns one less. */
    private const val HEADING_POINTS = 3

    /** Characters of depth that keep the extra reading near [SECONDS] on this phone. */
    fun budget(tokensPerSecond: Double?): Int {
        val rate = tokensPerSecond?.takeIf { it.isFinite() && it > 0 } ?: return UNKNOWN_CHARS
        return (rate * SECONDS * 4).toInt().coerceIn(MIN_CHARS, MAX_CHARS)
    }

    /**
     * Simple English ("swp-") and English Wikipedia ("enw-") passages, and the cryptography pack's
     * own Wikipedia articles ("wp-"), whose sections explain wallets and fees as the others do.
     */
    fun isEncyclopedia(item: Evidence): Boolean =
        item.documentId.startsWith("swp-") || item.documentId.startsWith("enw-") || item.documentId.startsWith("wp-")

    /**
     * An explanation can name its useful reference in a condition rather than its first noun
     * phrase: "why can an enclosure fail even when its sealing membrane ...". Read the rest of
     * an already-found article when its complete multiword title occurs in the question. This
     * is not permission to expand an arbitrary keyword hit such as "Water" or "Pressure".
     * No additional broad search is needed, and the ordinary subject cap still applies.
     */
    fun supportingSubjects(question: String, evidence: List<Evidence>, subjects: List<Evidence>): List<Evidence> {
        if (!SubjectLookup.explains(question)) return emptyList()
        val remaining = (SubjectLookup.MAX_SUBJECTS - subjects.size).coerceIn(0, 2)
        if (remaining == 0) return emptyList()
        fun words(text: String) = tokenPattern.findAll(text.lowercase()).map { it.value }.toList()
        val existing = subjects.map { words(it.title.substringBefore(" — ")) }.toSet()
        return evidence.asSequence().filter(::isEncyclopedia)
            .filter { " — " in it.title }
            .groupBy { words(it.title.substringBefore(" — ")) }
            .filter { (title, _) ->
                title.size in 2..8 && title !in existing &&
                    FtsQuery.from(title.joinToString(" "))?.terms.orEmpty().size >= 2 &&
                    QuestionSubject.containsMultiwordTitle(question, title.joinToString(" "))
            }
            .entries.sortedWith(compareByDescending<Map.Entry<List<String>, List<Evidence>>> { it.key.size }
                .thenBy { it.key.joinToString(" ") })
            .take(remaining)
            .map { (_, rows) ->
                rows.minWith(compareBy<Evidence> { !opensLead(it) }.thenBy { it.chunkId }.thenBy { it.source })
            }
    }
    /** Share of the depth budget the question's main subject reads; the others split the rest. */
    private const val PRIMARY_SHARE = 60
    /** A lead chunk cut to fit keeps at least this much, or nothing: a shorter cut says only what the subject is. */
    internal const val MIN_CUT = 240

    /** Section headings that answer each kind of question: "why" is answered under "Causes". */
    private val kinds = listOf(
        Regex("(?i)\\bwhy\\b|\\bcaus|\\bled to\\b|\\bhappen") to
            Regex("(?i)\\b(?:causes?|reasons?|background|origins?|mechanisms?)\\b"),
        Regex("(?i)\\bhow (?:does|do|is|are|can)\\b|\\bwork") to
            Regex("(?i)\\b(?:operation|principles?|mechanisms?|function(?:ing)?|theory|design|physics|process|features|structure|characteristics)\\b"),
        // "What is the difference between a hurricane and a typhoon" is answered where the terms are defined.
        Regex("(?i)\\bdiffer|\\bcompar|\\bversus\\b|\\bvs\\b|\\bwhich\\b|\\bbetter\\b") to
            Regex("(?i)\\b(?:comparison|types?|classification|differences?|definitions?|terminology|naming|nomenclature)\\b"),
        Regex("(?i)\\badvantage|\\bdrawback|\\bdisadvantage|\\bbenefit|\\brisks?\\b|\\blimitation|\\bpros\\b|\\bcons\\b") to
            Regex("(?i)\\b(?:advantages?|disadvantages?|drawbacks|benefits?|limitations?|criticism|risks?|safety)\\b"),
        Regex("(?i)\\bsymptom|\\bsigns?\\b") to Regex("(?i)\\b(?:signs and symptoms|symptoms|diagnosis)\\b"),
        Regex("(?i)\\btreat|\\bcure|\\bmanag") to Regex("(?i)\\b(?:treatment|management|therapy|prevention)\\b"),
        Regex("(?i)\\beffects?\\b|\\bhealth\\b|\\bevidence\\b|\\bresearch\\b") to
            Regex("(?i)\\b(?:effects?|health|research|evidence|safety)\\b"),
        Regex("(?i)\\bshape|\\btrade\\b|\\brise\\b") to
            Regex("(?i)\\b(?:history|background|causes?|trade|economy|legacy|impact|consequences)\\b"),
        // "Why did the Soviet Union collapse" is answered under "Dissolution"; "how did the steam
        // engine change society" under its legacy or consequences.
        Regex("(?i)\\bcollapse|\\bdecline|\\bfall\\b|\\bfell\\b|\\bdissol|\\bend(?:ed)?\\b|\\bsurviv") to
            Regex("(?i)\\b(?:dissolution|fall|decline|collapse|disintegration|breakup|end|history|background|causes?|legacy|consequences)\\b"),
        Regex("(?i)\\bchange|\\bsociety\\b|\\bsocial\\b|\\baffect|\\bimpact|\\binfluence") to
            Regex("(?i)\\b(?:legacy|impact|consequences|effects?|influence|society|social|economic|economy|history)\\b"),
        // "Why do onions make you cry" is answered under the article's adverse effects.
        Regex("(?i)\\bcry\\b|\\bcries\\b|\\btears?\\b|\\bsting|\\bitch|\\bburn|\\bhurt|\\bpain|\\bharm|\\bpoison|\\btoxic|\\ballerg") to
            Regex("(?i)\\b(?:irritation|adverse effects?|toxicity|side effects?|safety|poisoning|allerg\\w*|health)\\b"),
        Regex("(?i)\\bsalt|\\bsalin") to Regex("(?i)\\b(?:salinity|composition|chemistry|salt)\\b"),
        // "What happened to the Aral Sea" is told by its history and what was done about it.
        Regex("(?i)\\bhappen|\\bshr[ai]nk|\\bstory\\b|\\bdeal with\\b|\\bcome back\\b|\\brecover") to
            Regex("(?i)\\b(?:history|background|impact|consequences|solutions?|recovery|restoration|current situation)\\b"),
        // "Why are bananas so cheap", "why are gas fees high": the economics of the subject.
        Regex("(?i)\\bfees?\\b|\\bcost|\\bprice|\\bcheap|\\bexpensive") to
            Regex("(?i)\\b(?:economics?|fees?|gas|transaction fees?|market|trade|production|cultivation|pricing)\\b"),
        // "Is red wine good for your heart? What does the evidence say": the research on its effects.
        Regex("(?i)\\bevidence\\b|\\bstud(?:y|ies)\\b|\\bresearch\\b|\\brisks?\\b|\\bgood for\\b|\\bhealth") to
            Regex("(?i)\\b(?:health effects?|effects?|research|safety)\\b"),
        Regex("(?i)\\bphases?\\b") to Regex("(?i)\\b(?:phases?)\\b"),
    )
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    // Some older source chunks flatten the bibliography into "Details". Keep
    // source metadata in storage, but do not promote a citation list as an answer.
    private val bibliographyMarker = Regex("(?i)\\bdoi\\s*:\\s*10\\.|\\bPMID\\s+\\d|\\bPMC\\s+\\d|\\bS2CID\\s+\\d|\\{\\{cite journal|CS1 maint:")
    private fun isBibliography(item: Evidence): Boolean = bibliographyMarker.findAll(item.text).take(3).count() >= 3
    private val questionWords = setOf("how", "why", "what", "does", "do", "did", "is", "are", "was", "were", "can",
        "the", "difference", "between", "and", "when", "should", "use", "each", "my", "for", "of", "in", "on", "whats",
        "actual", "real", "which", "better", "i", "you", "we", "them", "it")

    /**
     * Up to [chars] characters from each encyclopedia subject's article in [articles] (title →
     * that article's rows from every collection, as [Retriever.article] returns them), taking turns
     * between subjects and leaving out what [have] already holds. Each subject's first lead chunk
     * comes first, English Wikipedia's before Simple English's, then the sections that fit the
     * question, best first, then the rest of the lead; when the lead chunk would leave no room for
     * the best section within the subject's share, that section comes first. Consecutive chunks of
     * one passage come back joined, without their repeated overlap.
     */
    fun pick(question: String, subjects: List<Evidence>, articles: Map<String, List<Evidence>>, have: List<Evidence>, chars: Int): List<Evidence> {
        if (chars <= 0) return emptyList()
        val held = have.map { it.documentId to it.chunkId }.toMutableSet()
        val titles = subjects.filter(::isEncyclopedia).map { it.title.substringBefore(" — ") }.distinct()
        // The question's main subject is the name it writes with capitals ("Lake Baikal"), else its
        // first phrase of more than one word, else its first subject. In a factual lookup a lone
        // common noun beside such a subject ("Water", "Depth") is a word of the question, not what
        // it is about, and reads no depth; a comparison's subjects ("Weather", "Climate") all do.
        val primary = titles.firstOrNull { isName(question, it) } ?: titles.firstOrNull { ' ' in it } ?: titles.firstOrNull() ?: return emptyList()
        val lookup = SubjectLookup.looksUp(question)
        val read = if (lookup && (' ' in primary || isName(question, primary))) titles.filter { it == primary || ' ' in it || isName(question, it) } else titles
        // The main subject reads most when it stands out from the others; equals share alike.
        val weighted = read.size > 1 && (lookup || isName(question, primary))
        val shares = read.associateWith { title ->
            when {
                read.size == 1 -> chars
                !weighted -> chars / read.size
                title == primary -> chars * PRIMARY_SHARE / 100
                else -> chars * (100 - PRIMARY_SHARE) / 100 / (read.size - 1)
            }
        }
        val queues = read.map { article ->
            ArrayDeque(ranked(question, article, articles[article].orEmpty().filter { isEncyclopedia(it) && (it.documentId to it.chunkId) !in held },
                shares.getValue(article)))
        }
        val allowance = read.map { shares.getValue(it) }.toIntArray()
        val picked = ArrayList<Evidence>()
        val deferred = ArrayList<Evidence>()
        var left = chars
        fun cost(next: Evidence) = next.text.length - if (picked.any { follows(it, next) }) OVERLAP else 0
        fun take(next: Evidence, cost: Int) {
            held += next.documentId to next.chunkId
            picked += next
            left -= cost
        }
        while (queues.any { it.isNotEmpty() }) {
            for ((slot, queue) in queues.withIndex()) {
                val next = queue.removeFirstOrNull() ?: continue
                if ((next.documentId to next.chunkId) in held) continue
                val cost = cost(next)
                if (cost > allowance[slot]) {
                    // The first lead chunk is cut at a sentence rather than lost; another chunk that
                    // does not fit its subject's share may still fit what the other subjects leave.
                    val cut = if (opensLead(next)) truncated(next, allowance[slot]) else null
                    if (cut != null) { take(cut, cut.text.length); allowance[slot] -= cut.text.length } else deferred += next
                    continue
                }
                take(next, cost)
                allowance[slot] -= cost
            }
        }
        for (next in deferred) {
            if ((next.documentId to next.chunkId) in held) continue
            val cost = cost(next)
            if (cost <= left) take(next, cost)
        }
        return joined(picked)
    }

    /** Prompt-sized complete units, retaining each full original Evidence for the
     * viewer/audit. Keyword candidates are not "already read": only the subjects
     * themselves have guaranteed prompt priority. Do not discard a whole original
     * chunk merely because its relevant complete paragraph fits but the chunk does not. */
    fun pickForPrompt(question: String, subjects: List<Evidence>, articles: Map<String, List<Evidence>>, chars: Int): List<Evidence> {
        if (chars <= 0) return emptyList()
        val titles = subjects.filter(::isEncyclopedia).map { it.title.substringBefore(" — ") }.distinct()
        if (titles.isEmpty()) return emptyList()
        val conditional = ConditionalReferenceFocus.from(question, subjects)
        val held = subjects.map { Triple(it.documentId, it.chunkId, it.source) }.toSet()
        val queues = titles.map { title ->
            val scorer = Scorer(question, title)
            ArrayDeque(articles[title].orEmpty().filter { isEncyclopedia(it) && !isBibliography(it) &&
                it.title.startsWith("$title — ") && Triple(it.documentId, it.chunkId, it.source) !in held }
                .sortedWith(compareByDescending<Evidence> { scorer.promptHasRequestedTerm(it) }
                    .thenByDescending { scorer.score(it) }.thenBy { it.chunkId }))
        }
        val picked = mutableListOf<Evidence>()
        val units = mutableSetOf<String>()
        var left = chars
        while (queues.any { it.isNotEmpty() }) {
            for (queue in queues) {
                val item = queue.removeFirstOrNull() ?: continue
                val unit = PromptBuilder.excerptForKnowledge(question, item)
                if (unit.isBlank() || unit in units) continue
                if (conditional != null && !conditional.matches(item, unit)) continue
                // The narrow conditional route quotes the original unit; it never sends it to
                // the model. Do not drop its first bounded, qualified passage merely because it
                // exceeds a soft prefill-time target. Focus caps it at 2,400 characters, and
                // PromptBuilder still enforces the hard context limit without cutting it.
                if (unit.length > left && (conditional == null || picked.isNotEmpty())) continue
                picked += item
                units += unit
                left -= unit.length
            }
        }
        return picked
    }

    /** Whether [title] is written as a name in [question]: with its capitals, or capitalised past its first word. */
    private fun isName(question: String, title: String): Boolean =
        question.contains(title) || title.split(' ').drop(1).any { it.first().isUpperCase() }

    private fun opensLead(item: Evidence): Boolean = item.documentId.endsWith("-0000") && chunkNumber(item) == 0

    /** [item] cut to its last sentence ending within [max] characters, or null when less than [MIN_CUT] would remain. */
    internal fun truncated(item: Evidence, max: Int): Evidence? {
        if (max < MIN_CUT) return null
        val text = item.text.lineSequence().filterNot { it.startsWith("Also known as:") }.joinToString("\n").trim()
        if (text.length <= max) return item.copy(text = text)
        val end = Regex("[.!?](?=\\s)").findAll(text.substring(0, max)).lastOrNull()?.range?.last ?: return null
        return if (end + 1 >= MIN_CUT) item.copy(text = text.substring(0, end + 1)) else null
    }

    /** How well each section of [article] answers [question]; see [score]. */
    private class Scorer(question: String, article: String) {
        private val own = tokens(article)
        private val terms = FtsQuery.from(question)?.terms.orEmpty()
            .filterNot { it in questionWords || own.any { word -> stemMatches(word, it) } }
        private val wanted = kinds.filter { (asks, _) -> asks.containsMatchIn(question) }.map { it.second }

        /** The section's own heading ("Eye irritation" of "Adverse effects > Eye irritation"). */
        fun heading(item: Evidence): String = item.title.substringAfter(" — ", "").substringAfterLast(" > ")
        private fun parents(item: Evidence): String = item.title.substringAfter(" — ", "").substringBeforeLast(" > ", "")

        fun headingAnswers(item: Evidence): Boolean = wanted.any { it.containsMatchIn(heading(item)) }

        /**
         * Points for a heading that answers the question's kind (its own heading over a parent's, so
         * "Principles" outranks "Principles > Non-navigation applications"), for the question's own
         * words in the heading, and for them in the text.
         */
        fun score(item: Evidence): Int {
            val headTokens = tokens(item.title.substringAfter(" — ", ""))
            val body = tokens(item.text)
            val heading = when {
                headingAnswers(item) -> HEADING_POINTS
                wanted.any { it.containsMatchIn(parents(item)) } -> HEADING_POINTS - 1
                else -> 0
            }
            return heading + 2 * terms.count { t -> headTokens.any { stemMatches(it, t) } } + terms.count { t -> body.any { stemMatches(it, t) } }
        }

        // A generic "Structure" heading must not beat a selected paragraph that
        // actually mentions the requested process/object. Score the prompt unit,
        // not just matching words in a different, omitted part of its full chunk.
        fun promptHasRequestedTerm(item: Evidence): Boolean =
            EvidenceRelevance.termHits(PromptBuilder.excerptForKnowledge(questionText, item), terms) > 0

        private val questionText = question
    }

    /**
     * The article's passages in reading order for [question]: the first lead chunk, the sections
     * that score (each section's chunks together, in order, best section first), the rest of the
     * lead, then the sections that do not score. When the first lead chunk and the best section
     * together exceed [chars], the section comes first: the subject's definition is already held,
     * and the one section that answers must fit.
     */
    internal fun ranked(question: String, article: String, passages: List<Evidence>, chars: Int = Int.MAX_VALUE): List<Evidence> {
        val scorer = Scorer(question, article)
        // A page's lead is its first document ("enw-68316-0000"); its chunks stay in order.
        val usable = passages.filterNot(::isBibliography)
        val lead = usable.filter { it.documentId.endsWith("-0000") }
            .sortedWith(compareBy<Evidence> { !it.documentId.startsWith("enw-") }.thenBy { it.chunkId })
        val sections = usable.filterNot { it.documentId.endsWith("-0000") }.groupBy { it.documentId }.values
            .map { chunks -> chunks.sortedBy { it.chunkId } to chunks.maxOf { scorer.score(it) } }
            .sortedWith(compareByDescending<Pair<List<Evidence>, Int>> { it.second }.thenBy { it.first.first().chunkId })
        val scored = sections.filter { it.second > 0 }.flatMap { it.first }
        val unscored = sections.filter { it.second <= 0 }.flatMap { it.first }
        val first = lead.take(1)
        val best = scored.firstOrNull()
        return if (best != null && first.isNotEmpty() && first[0].text.length + best.text.length > chars) {
            scored + first + lead.drop(1) + unscored
        } else first + scored + lead.drop(1) + unscored
    }

    /**
     * The section of [article] among [passages] that answers [question] by its own heading
     * ("Principles" for how GPS works, "Eye irritation" for why onions make you cry), best first;
     * only a section's first chunk, which opens with its first sentence. Null when no heading
     * answers the question's kind: a section that merely shares the question's words is evidence,
     * not an opening.
     */
    fun answering(question: String, article: String, passages: List<Evidence>): Evidence? {
        val scorer = Scorer(question, article)
        return passages.filter { item ->
            isEncyclopedia(item) && item.title.startsWith("$article — ") && !item.documentId.endsWith("-0000") &&
                chunkNumber(item) == 0 && scorer.headingAnswers(item)
        }.maxByOrNull { scorer.score(it) }
    }

    /** Whether [next] is the chunk right after [previous] in the same passage. */
    private fun follows(previous: Evidence, next: Evidence): Boolean =
        previous.documentId == next.documentId && chunkNumber(next) == chunkNumber(previous) + 1

    private fun chunkNumber(item: Evidence): Int = item.chunkId.substringAfterLast(':').toIntOrNull() ?: -1

    /**
     * Picked chunks with each run of consecutive chunks of one passage joined into one passage,
     * without the overlap the chunker repeats, and without the "Also known as:" line, which names
     * the article rather than explaining it.
     */
    internal fun joined(picked: List<Evidence>): List<Evidence> {
        // Each passage's chunks stay together, in order, where the passage first appeared.
        val firstSeen = HashMap<String, Int>()
        picked.forEachIndexed { index, item -> firstSeen.putIfAbsent(item.documentId, index) }
        val result = ArrayList<Evidence>()
        var previous = -2
        for (item in picked.sortedWith(compareBy({ firstSeen.getValue(it.documentId) }, { chunkNumber(it) }))) {
            val last = result.lastOrNull()
            if (last != null && last.documentId == item.documentId && chunkNumber(item) == previous + 1) {
                val overlap = item.text.take(OVERLAP)
                val rest = if (last.text.endsWith(overlap)) item.text.drop(OVERLAP) else " " + item.text
                result[result.lastIndex] = last.copy(text = last.text + rest)
            } else {
                result += item
            }
            previous = chunkNumber(item)
        }
        return result.map { item ->
            item.copy(text = item.text.lineSequence().filterNot { it.startsWith("Also known as:") }.joinToString("\n").trim())
        }
    }

    private fun tokens(value: String): Set<String> = tokenPattern.findAll(value.lowercase()).map { it.value }.toSet()

    // "pumps" and "pumping" are the word "pump"; a short word must match whole.
    private fun stemMatches(word: String, term: String): Boolean =
        if (term.length <= 4) word == term else word.startsWith(term.take(maxOf(4, term.length - 2)))
}
