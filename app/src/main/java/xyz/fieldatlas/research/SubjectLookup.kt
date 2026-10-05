package xyz.fieldatlas.research

/**
 * The reference article a question is about, found by its title. "How does a heat pump heat a
 * house?" is answered by the article "Heat pump", but keyword search ranks passages that share
 * more of the question's words ("house", "heat") and the relevance gate then drops the article
 * for not saying "house". An article whose title is the question's subject is the strongest
 * evidence a reference holds, so it is looked up by that title and kept.
 */
object SubjectLookup {
    const val SECTION = "overview"
    private const val MAX_PHRASES = 8

    // Words that frame a question rather than name its subject.
    private val framing = setOf(
        "how", "does", "do", "did", "is", "are", "was", "were", "why", "what", "which", "who", "when", "where", "the", "a",
        "an", "of", "to", "in", "on", "for", "and", "or", "with", "from", "by", "between", "difference", "differences",
        "different", "differ", "compare", "compared", "comparison", "versus", "vs", "explain", "describe", "tell", "me", "about",
        "work", "works", "working", "happen", "happens", "mean", "means", "cause", "causes", "caused", "make", "makes", "use",
        "uses", "used", "my", "your", "our", "their", "its", "it", "this", "that", "these", "those", "can", "could", "should",
        "would", "will", "i", "we", "you", "they", "there", "so", "than", "into", "really", "actually", "exactly", "kind", "type",
        "types", "main", "between", "each", "when", "if", "without", "have", "has", "get", "gets", "turn", "turns",
        "keep", "keeps", "kept", "help", "helps", "protect", "protects", "need", "needs", "know",
        // An aspect asked about is not the subject: "the advantages and drawbacks of electric cars".
        "advantages", "disadvantages", "drawbacks", "benefits", "pros", "cons", "downsides", "upsides", "risks", "dangers",
        "effects", "effect", "impact", "impacts", "consequences",
    )
    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'-]*")

    /**
     * A phrase to try as a title, with the rest of the noun phrase it was taken from and the word
     * just before it there ("hardware" for "wallet" in "hardware wallet"), which narrows it.
     */
    data class Candidate(val text: String, val rest: List<String>, val modifier: String? = null)

    /**
     * Subject phrases in the order to try them, from the question's first noun phrase (its first
     * two for a comparison): the whole phrase, then shorter pieces, longest first and among equals
     * the one ending the phrase, where English puts the head noun ("ocean tides" -> "tides", not
     * "ocean"). A plural is also tried in the singular ("sunsets" -> "sunset").
     */
    fun candidates(question: String, subjects: Int = 1): List<Candidate> {
        val runs = ArrayList<List<String>>()
        var run = ArrayList<String>()
        for (m in word.findAll(question)) {
            val w = m.value.trim('\'', '-')
            if (w.lowercase() in framing || w.length < 2 || w.all(Char::isDigit)) {
                if (run.isNotEmpty()) { runs += run; run = ArrayList() }
            } else run += w
        }
        if (run.isNotEmpty()) runs += run
        data class Ranked(val candidate: Candidate, val length: Int, val endsRun: Boolean, val order: Int)
        val ranked = ArrayList<Ranked>()
        var order = 0
        for (r in runs.take(subjects)) for (n in minOf(3, r.size) downTo 1) for (start in 0..r.size - n) {
            val words = r.subList(start, start + n)
            val rest = r.subList(0, start) + r.subList(start + n, r.size)
            val ends = start + n == r.size
            val modifier = r.getOrNull(start - 1)
            ranked += Ranked(Candidate(words.joinToString(" "), rest, modifier), n, ends, order++)
            singular(words.last())?.let { ranked += Ranked(Candidate((words.dropLast(1) + it).joinToString(" "), rest, modifier), n, ends, order++) }
        }
        return ranked.sortedWith(compareBy({ -it.length }, { if (it.endsRun) 0 else 1 }, { it.order }))
            .map { it.candidate }.distinctBy { it.text.lowercase() }.take(MAX_PHRASES)
    }

    fun phrases(question: String): List<String> = candidates(question, 2).map { it.text }

    /**
     * A shorter piece of the noun phrase is the subject only if its article mentions the rest:
     * "Tide" for "ocean tides" mentions the ocean; "Address" for "stealth address" and "Wallet"
     * for "hot wallet" are other things that share a word. The word just before the piece is the
     * one that narrows it, so when there is one the article must mention that word: the leather
     * "Wallet" may say "keep" but never "hardware".
     */
    fun accepts(candidate: Candidate, article: Evidence): Boolean {
        if (candidate.rest.isEmpty()) return true
        val tokens = word.findAll(article.text.lowercase()).map { it.value }.toSet()
        fun mentions(w: String) = w.lowercase().take(maxOf(4, w.length - 2)).let { stem -> tokens.any { it.startsWith(stem) } }
        return candidate.modifier?.let(::mentions) ?: candidate.rest.any(::mentions)
    }

    private fun singular(w: String): String? = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
        else -> null
    }

    /** The article that lists [name] among its other names ("Global Positioning System" for "GPS"). */
    fun aliasOf(name: String, found: List<Evidence>): String? = found.firstOrNull { item ->
        item.title.endsWith(" — Overview") && item.text.lineSequence().any { line ->
            line.startsWith("Also known as:") && line.removePrefix("Also known as:").split(';').any { it.trim().equals(name, ignoreCase = true) }
        }
    }?.title?.removeSuffix(" — Overview")

    /**
     * The summary of a standard named by what it is called rather than its number: "stealth
     * address" is "ERC-5564: Stealth Addresses — Abstract".
     */
    fun standardOf(phrase: String, found: List<Evidence>): Evidence? {
        val names = listOf(phrase, phrase + "s", phrase + "es").map { Regex.escape(it) }.joinToString("|")
        val title = Regex("(?i)^(?:EIP|ERC)-\\d+: (?:$names) — (?:Abstract|Simple Summary)$")
        return found.firstOrNull { title.matches(it.title) }
    }

    /**
     * A section of a reference article headed with the subject: "hardware wallet" is "Cryptocurrency
     * wallet — Types of Wallets > Cold storage > Hardware wallets". Only a name of two or more words,
     * and not a travel guide, whose hundreds of towns share headings such as "Public transport".
     */
    fun sectionOf(phrase: String, found: List<Evidence>): Evidence? {
        if (' ' !in phrase.trim()) return null
        val names = listOf(phrase, phrase + "s", phrase + "es").map { it.lowercase() }
        return found.firstOrNull { item ->
            !item.source.contains("wikivoyage", ignoreCase = true) && !item.title.endsWith(" — Overview") &&
                item.title.substringAfterLast(" > ").substringAfterLast(" — ").trim().lowercase() in names
        }
    }

    /** The overview's first passage when its title is exactly [phrase] ("Heat pump — Overview"). */
    fun overviewOf(phrase: String, found: List<Evidence>): Evidence? =
        found.firstOrNull { it.title.endsWith(" — Overview") && it.title.removeSuffix(" — Overview").equals(phrase, ignoreCase = true) }

    // An explanation, comparison or history question is opened by what the saved article says of
    // its subject; a personal how-to ("how do I…") is not answered by a definition.
    private val explains = Regex("(?i)^\\s*(?:how (?:does|do|is|are|can|did) (?!i\\b|we\\b|you\\b)|why (?:does|do|is|are|did) |what (?:causes|caused|led to|is|are|was|were) |" +
        "explain |describe |what(?:'s| is) the difference between |compare |how (?:is|are|does|do) .+ (?:differ|different))")

    fun leads(question: String): Boolean = explains.containsMatchIn(question)

    /** The definition of each subject found, quoted from its overview, for the answer's opening. */
    fun lead(question: String, subjects: List<Evidence>, sources: List<PromptSource>): SourceLead.Lead? {
        if (!leads(question)) return null
        val quotes = subjects.mapNotNull { subject ->
            val source = sources.firstOrNull { it.evidence.documentId == subject.documentId && it.evidence.chunkId == subject.chunkId }
                ?: return@mapNotNull null
            // A standard's first passage opens with its name and status lines; its summary follows.
            val text = source.evidence.text.let { t ->
                if (Regex("^(?:EIP|ERC)-\\d+:").containsMatchIn(t)) t.split(Regex("\\n\\s*\\n")).drop(1).joinToString("\n\n") else t
            }
            SourceLead.leadText(EvidenceRelevance.passageText(text))?.let { source.citationId to it }
        }
        if (quotes.isEmpty()) return null
        return SourceLead.Lead(quotes.first().first, quotes.first().second, quotes.drop(1))
    }
}
