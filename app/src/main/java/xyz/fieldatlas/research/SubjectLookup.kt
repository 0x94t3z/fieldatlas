package xyz.fieldatlas.research

/**
 * The reference article a question is about, found by its title. "How does a heat pump heat a
 * house?" is answered by the article "Heat pump", but keyword search ranks passages that share
 * more of the question's words ("house", "heat") and the relevance gate then drops the article
 * for not saying "house". An article whose title is the question's subject is the strongest
 * evidence a reference holds, so it is looked up by that title and kept.
 */
object SubjectLookup {
    private fun discoveryQuestion(question: String): String {
        val text = QuestionText.normalised(QuestionSubject.text(question))
        // The role is what is requested, not the entity to look up. Keep this
        // grammatical rewrite scoped so titles containing "Role" remain intact.
        return text.replace(Regex("(?i)^what (?:role|function) (?:do|does) (.+?) (?:play|serve|have)\\b"), "How does $1 work")
    }
    const val SECTION = "overview"
    private const val MAX_PHRASES = 8
    /** A comparison names up to this many subjects: "a hurricane, a typhoon and a cyclone". */
    const val MAX_SUBJECTS = 3
    /** Later clauses tried for an explanation whose first noun phrase names no article. */
    private const val FALLBACK_RUNS = 2
    /** An overview this short that only says what kind of thing its subject is points at a fuller article. */
    private const val STUB_CHARS = 160

    // Words that frame a question rather than name its subject.
    private val framing = setOf(
        "how", "does", "do", "did", "is", "are", "was", "were", "why", "what", "which", "who", "when", "where", "the", "a",
        "an", "of", "to", "in", "on", "for", "and", "or", "with", "from", "by", "between", "difference", "differences",
        "different", "differ", "distinguish", "distinguishes", "distinguishing", "compare", "compared", "comparison", "versus", "vs", "explain", "describe", "tell", "me", "about",
        "work", "works", "working", "happen", "happens", "mean", "means", "cause", "causes", "caused", "make", "makes", "use",
        "uses", "used", "my", "your", "our", "their", "its", "it", "this", "that", "these", "those", "can", "could", "should",
        "would", "will", "may", "might", "must", "shall", "i", "we", "you", "they", "there", "so", "than", "into", "really", "actually", "exactly", "kind", "type",
        "types", "main", "between", "each", "when", "if", "without", "have", "has", "get", "gets", "turn", "turns",
        "keep", "keeps", "kept", "help", "helps", "protect", "protects", "need", "needs", "know",
        // An aspect asked about is not the subject: "the advantages and drawbacks of electric cars".
        "advantages", "disadvantages", "drawbacks", "benefits", "pros", "cons", "downsides", "upsides", "risks", "dangers",
        "effects", "effect", "impact", "impacts", "consequences",
        // Casual spellings and emphasis: "whats the actual difference", "while the Eastern half survived".
        "whats", "while", "supposed",
        // An adverb narrows nothing: a "fully electric car" is an electric car.
        "fully", "completely", "totally", "entirely", "purely", "simply", "mainly", "mostly", "usually", "truly", "just",
        "very", "quite", "rather",
        // The people and verbs of a scenario are not its subject: "how can someone send me a message".
        "someone", "something", "anyone", "anything", "somebody", "everybody", "everyone", "nobody", "nothing",
        "ever", "never", "send", "sends", "sent", "meet", "met", "pay", "pays", "paid",
        // A choice's judgement: "which is better", "should I take".
        "better", "best", "worse", "worst", "cheaper", "cheapest", "safer", "safest", "faster", "fastest", "stronger",
        "healthier", "preferable", "pick", "choose", "take",
        // A simile compares the subject with something else: "a hardware wallet like a Ledger".
        "like", "unlike",
        // Quantifiers, adverbs and the other side of a contrast: "some people get seasick and others
        // don't", "why is the sky blue but sunsets are red", "what can still go wrong".
        "some", "other", "others", "another", "still", "wrong", "else", "sometimes", "often", "always", "already", "yet", "even",
        "but", "though", "although", "because", "happened",
        // Contractions the word pattern keeps whole.
        "don't", "doesn't", "didn't", "isn't", "aren't", "wasn't", "weren't", "can't", "won't", "shouldn't", "wouldn't", "couldn't",
    )
    // A measure of the subject is not the subject: "a glass of red wine", "a cup of coffee".
    private val measures = setOf("glass", "cup", "bottle", "bowl", "slice", "piece", "pinch", "drop", "lot", "bit")
    // Connectors inside a name: "Great Wall of China", "Statue of Liberty", "Dissolution of the Soviet
    // Union", "Ludwig von Beethoven". Between lowercase words they still end the phrase ("glass of red wine").
    private val connectors = setOf("of", "the", "de", "von", "and")
    // Adjectives of time, order and judgement describe the subject without narrowing which article
    // it is: "the next bitcoin halving" is about Bitcoin, a "typical heat pump" is a heat pump.
    private val loose = setOf("next", "last", "first", "current", "latest", "upcoming", "new", "old", "original", "modern",
        "early", "late", "whole", "entire", "typical", "average", "normal", "regular", "classic", "classical")
    // Conditions named by the adjective people use for them; the encyclopedia heads its section with the noun.
    private val conditions = mapOf("seasick" to "seasickness", "airsick" to "airsickness", "sunburnt" to "sunburn",
        "sunburned" to "sunburn", "carsick" to "motion sickness", "jetlagged" to "jet lag", "homesick" to "homesickness",
        "frostbitten" to "frostbite", "dehydrated" to "dehydration", "concussed" to "concussion")
    // What a subject does, named as the property the encyclopedia explains: "why does water boil at a
    // lower temperature" is answered by "Boiling point".
    private val nominals = mapOf("boil" to listOf("boiling point", "boiling"), "boils" to listOf("boiling point", "boiling"),
        "freeze" to listOf("freezing point", "freezing"), "freezes" to listOf("freezing point", "freezing"),
        "melt" to listOf("melting point", "melting"), "melts" to listOf("melting point", "melting"),
        "rust" to listOf("rust"), "rusts" to listOf("rust"), "evaporate" to listOf("evaporation"), "evaporates" to listOf("evaporation"),
        "condense" to listOf("condensation"), "condenses" to listOf("condensation"))
    // Words that frame a question unless they are part of a name: "the real difference" but "Real
    // Madrid"; "without us ever meeting" but "the US".
    private val emphasis = setOf("actual", "real", "us")
    // In a factual lookup, the result asked about is not a subject either: "who won the First
    // World War", "are lions or tigers heavier", "did Germany invade the Soviet Union".
    private val lookupFraming = setOf(
        "won", "win", "wins", "winner", "winners", "invade", "invaded", "invasion", "heavier", "heaviest", "bigger", "biggest",
        "larger", "largest", "smaller", "smallest", "taller", "tallest", "higher", "highest", "longer", "longest", "older",
        "oldest", "faster", "fastest", "deeper", "deepest", "deep", "wide", "high", "heavy", "hold", "holds", "some", "exceed",
        "exceeds", "healthy", "start", "started", "end",
        "ended", "take", "took", "place", "happened", "live", "eat", "most", "more", "less", "least", "only", "much", "many",
        "weigh", "weighs", "big", "tall", "old", "far", "long", "located", "famous", "known", "populated", "populous",
        // A judgement asked about is not a subject either: "is it safe to drink the tap water".
        "safe", "good", "bad", "better", "best", "worse", "worst", "true", "false", "real", "possible", "dangerous", "legal",
        "illegal", "normal", "common", "rare", "important", "necessary", "enough", "free", "cheap", "expensive",
    )
    // What a question says its subject does or is: the words after "the Black Death" in "how did
    // the Black Death change wages" describe the Black Death and never narrow which article it is.
    private val predicates = setOf(
        "collapse", "collapsed", "collapses", "change", "changed", "changes", "shape", "shaped", "shapes", "fall", "fell",
        "falls", "rise", "rose", "rises", "go", "goes", "went", "happen", "happened", "happens", "spread", "spreads", "affect",
        "affects", "affected", "lead", "leads", "led", "end", "ends", "ended", "begin", "began", "begins", "start", "started",
        "starts", "lasted", "last", "become", "became", "matter", "matters", "exist", "exists", "survive", "survived",
        "survives", "decline", "declined", "fail", "failed", "failing", "leak", "leaks", "leaked", "differ", "differs", "cause", "causes", "caused",
        "pop", "pops", "popped", "hurt", "hurts", "work", "works", "worked", "form", "forms", "formed", "float", "floats",
        "fly", "flies", "sink", "sinks", "melt", "melts", "freeze", "freezes", "boil", "boils", "burn", "burns", "glow",
        "glows", "shine", "shines", "spin", "spins", "rotate", "rotates", "orbit", "orbits", "grow", "grows", "die", "dies",
        "sleep", "sleeps", "taste", "tastes", "smell", "smells", "look", "looks", "feel", "feels", "appear", "appears",
        "move", "moves", "flow", "flows", "rain", "rains", "snow", "snows", "cry", "cries", "sting", "stings", "itch",
        "itches", "bleed", "bleeds", "heal", "heals", "elect", "elects", "elected",
        "obtain", "obtains", "acquire", "acquires", "absorb", "absorbs", "transport", "transports", "supply", "supplies",
        "salty", "red", "orange", "blue", "green", "yellow", "white", "black", "hot", "cold", "warm", "cool", "wet", "dry",
    )
    // Too general to be what a lookup is about on their own: "the tallest animal in the world" is a
    // giraffe and "the population of Canada" is about Canada, though "World population" is an article.
    private val generic = setOf("world", "animal", "animals", "country", "countries", "people", "person", "city", "cities",
        "thing", "things", "year", "years", "time", "number", "part", "way", "day", "days", "nation", "nations",
        "population", "populations", "capital", "currency", "height", "area", "size", "length", "depth", "weight",
        "language", "languages")
    // The people of a scenario are not what an explanation is about either: "why do some people get seasick".
    private val genericAlways = setOf("people", "person", "thing", "things")
    // A noun phrase after one of these opens a new clause, which may hold the subject when the
    // first clause does not: "How can someone send me a message…? How does public key cryptography work?"
    private val copulas = setOf("is", "are", "was", "were")
    private val determiners = setOf("the", "a", "an", "my", "your", "our", "their", "its", "this", "that", "these", "those")
    private val clauseStarts = setOf("does", "do", "did", "is", "are", "was", "were", "can", "could", "would", "should",
        "will", "may", "might", "must", "shall", "why", "how", "what", "which", "who", "when", "where", "but")
    // The kind of thing a title's qualifier names: "Georgia (country)", "Mercury (planet)".
    private val qualifierWords = setOf("country", "state", "city", "town", "province", "region", "island", "river", "lake",
        "mountain", "planet", "star", "moon", "language", "film", "movie", "band", "song", "album", "book", "novel", "play",
        "game", "series", "company", "car", "ship", "animal", "plant", "fish", "bird", "insect", "fruit", "drug", "medication",
        "element", "metal", "mineral", "mythology", "god", "goddess", "actor", "actress", "singer", "musician", "writer",
        "author", "politician", "footballer", "boxer", "artist", "painter", "composer", "philosopher", "scientist",
        "software", "programming", "unit", "disease", "color", "colour", "dance", "food", "dish", "drink", "sport")
    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")

    /**
     * A phrase to try as a title, with the rest of the noun phrase it was taken from and the word
     * just before it there ("hardware" for "wallet" in "hardware wallet"), which narrows it. [run]
     * is the noun phrase's position among those tried: a comparison's second subject is in run 1.
     */
    data class Candidate(val text: String, val rest: List<String>, val modifier: String? = null, val run: Int = 0)

    /** A noun phrase of the question, with the word just before it ("does" for the subject of a clause). */
    private class Run(val words: List<String>, val before: String?)

    private fun runs(originalQuestion: String, lookup: Boolean, joinAnd: Boolean = false): List<Run> {
        val question = QuestionSubject.text(originalQuestion)
        val matches = word.findAll(question).toList()
        val words = matches.map { it.value.trim('\'', '’', '-') }
        // Whether only spaces separate word [index] from the next.
        fun adjacent(index: Int) = index + 1 < matches.size &&
            question.substring(matches[index].range.last + 1, matches[index + 1].range.first).isBlank()
        // A capitalised word that does not open a sentence is a name's word.
        fun named(index: Int) = index > 0 && words[index].first().isUpperCase() &&
            question.substring(matches[index - 1].range.last + 1, matches[index].range.first).none { it in "?.!;:" }
        val runs = ArrayList<Run>()
        var run = ArrayList<String>()
        var before: String? = null
        var previous: MatchResult? = null
        var index = 0
        while (index < matches.size) {
            val m = matches[index]
            // Possessives stay in a name ("Ohm's law"); a single word loses it below ("Japan's" is Japan).
            val w = words[index]
            val bare = possessor(w).lowercase()
            // "Great Wall of China": connectors between a name's capitalised words stay inside it. "And"
            // joins only when one subject is wanted; a comparison's subjects are chained by it.
            if (bare in connectors && (bare != "and" || joinAnd) && run.isNotEmpty() && named(index - 1) && adjacent(index - 1)) {
                var end = index
                while (end < matches.size && words[end].lowercase() in connectors && adjacent(end)) end++
                if (end > index && end < matches.size && named(end)) {
                    run += words.subList(index, end)
                    previous = matches[end - 1]
                    index = end
                    continue
                }
            }
            val numeric = w.all(Char::isDigit)
            val next = words.getOrNull(index + 1)
            val nextNames = next != null && next.lowercase() !in framing && !next.all(Char::isDigit) && next.length >= 2 && adjacent(index)
            // A year stays inside a lookup's phrase ("2018 FIFA World Cup final") or opens a name
            // ("2008 financial crisis"); a lone number inside a name stays too ("layer 2 rollup").
            val keepsNumber = numeric && (lookup && w.length == 4 || w.length == 4 && run.isEmpty() && nextNames ||
                run.isNotEmpty() && nextNames)
            val isNamed = m.range.first > 0 && w.first().isUpperCase()
            val measure = bare in measures && next?.lowercase() == "of"
            val breaks = bare in framing || bare in emphasis && !isNamed || lookup && bare in lookupFraming && !isNamed ||
                numeric && !keepsNumber || w.length < 2 && !keepsNumber && !(w.singleOrNull()?.isLetter() == true && nextNames) || measure
            if (breaks) {
                if (run.isNotEmpty()) { runs += Run(run, before); run = ArrayList() }
            } else {
                if (run.isEmpty()) {
                    // A new sentence opens with no word before its noun phrase; a determiner is looked
                    // past, so "why is the sky blue" puts "is" before "sky blue".
                    var at = index - 1
                    while (at > 0 && words[at].lowercase() in determiners && adjacent(at - 1)) at--
                    val gap = if (at >= 0) question.substring(matches[at].range.last + 1, matches[at + 1].range.first) else ""
                    before = if (at < 0 || gap.any { it in "?.!;:" }) null else words[at].lowercase()
                }
                run += w
            }
            previous = m
            index++
        }
        if (run.isNotEmpty()) runs += Run(run, before)
        return runs
    }

    /**
     * Subject phrases in the order to try them, from the question's first noun phrase (its first
     * two or three for a comparison): the whole phrase, then shorter pieces, longest first and among
     * equals the one ending the phrase, where English puts the head noun ("ocean tides" -> "tides",
     * not "ocean"). A plural is also tried in the singular ("sunsets" -> "sunset"). When an
     * explanation's first noun phrase names no article, the phrases that open its later clauses
     * follow, each in its own [Candidate.run]; a phrase after a preposition ("in Ethereum") only
     * places the first and is never tried.
     */
    fun candidates(question: String, subjects: Int = 1, lookup: Boolean = false): List<Candidate> {
        val asked = discoveryQuestion(question)
        // A choice's subjects follow its first option: "is the high-speed train or a flight the better option".
        val choice = if (!lookup && subjects > 1) choice(asked) else null
        val all = runs(choice?.let { asked.substring(it.start) } ?: asked, lookup, joinAnd = !lookup && subjects == 1)
        val chosen = when {
            lookup || subjects > 1 -> all.take(subjects)
            else -> explained(all)
        }
        // When neither option of a choice names an article ("a Ledger or Trezor"), the question's
        // own noun phrases are tried after them, as for any explanation.
        val extraQuestion = choice?.let { selected ->
            val tail = asked.substring(selected.start)
            Regex("[?!]|\\.(?=\\s)").find(tail)?.let { tail.substring(it.range.last + 1) }.orEmpty()
        }.orEmpty()
        val fallback = if (extraQuestion.isNotBlank()) explained(runs(extraQuestion, lookup))
            .filter { run -> chosen.none { it.words == run.words } } else emptyList()
        data class Ranked(val candidate: Candidate, val length: Int, val endsRun: Boolean, val order: Int)
        val ranked = ArrayList<Ranked>()
        var order = 0
        for ((index, run) in (chosen + fallback).withIndex()) {
            // "why is the sky blue", "why are sunsets red": after a copula the phrase's last word says
            // what the subject is, and neither the colour's article nor "Sky blue" is the subject.
            val phrase = if (run.words.firstOrNull()?.firstOrNull()?.isUpperCase() == true)
                run.words.takeWhile { it.first().isUpperCase() || it.lowercase() !in predicates } else run.words
            // After an auxiliary, a known predicate ends the leading subject. Do not spend
            // the bounded phrase slots on its object ("batteries store energy" -> "energy").
            // A predicate-looking first word can still modify a noun ("warm fronts").
            val predicateAt = if (!lookup && run.before in clauseStarts)
                phrase.indices.drop(1).firstOrNull { phrase[it].first().isLowerCase() && phrase[it].lowercase() in predicates }
            else null
            // Nominal routes such as "water boil" -> "boiling point" still need
            // their predicate in the candidate loop below.
            val r = if (predicateAt != null && phrase[predicateAt].lowercase() !in nominals) phrase.take(predicateAt) else phrase
            val maxWords = if (lookup || r.any { it.lowercase() in connectors }) 6 else 3
            for (n in minOf(maxWords, r.size) downTo 1) for (start in 0..r.size - n) {
                val words = if (n == 1) listOf(possessor(r[start])) else r.subList(start, start + n)
                // A piece of a name never opens or closes with its connector ("of China").
                if (words.first().lowercase() in connectors || words.last().lowercase() in connectors) continue
                if (words.all { it.all(Char::isDigit) } || n == 1 && (lookup && words[0].lowercase() in generic || words[0].lowercase() in genericAlways)) continue
                val trailing = r.subList(start + n, r.size)
                // The words after a name that opens its phrase are its predicate ("the Black Death
                // change wages", "ears pop"), not a narrowing of which article it is.
                val predicate = start == 0 && trailing.isNotEmpty() &&
                    (trailing.first().lowercase() in predicates || words.first().first().isUpperCase() && trailing.all { it.first().isLowerCase() })
                val rest = if (predicate) emptyList() else r.subList(0, start) + trailing
                val ends = start + n == r.size
                val modifier = r.getOrNull(start - 1)
                ranked += Ranked(Candidate(words.joinToString(" "), rest, modifier, index), n, ends, order++)
                // "water boil" is explained by "Boiling point": the property is tried before the bare subject.
                if (start == 0 && trailing.isNotEmpty()) nominals[trailing.first().lowercase()]?.forEach { nominal ->
                    ranked += Ranked(Candidate(nominal, emptyList(), null, index), r.size, true, order++)
                }
                singular(words.last())?.let { ranked += Ranked(Candidate((words.dropLast(1) + it).joinToString(" "), rest, modifier, index), n, ends, order++) }
                // "seasick" is the condition "seasickness", which heads a section of "Motion sickness".
                if (n == 1) conditions[words[0].lowercase()]?.let { ranked += Ranked(Candidate(it, rest, modifier, index), n, ends, order++) }
            }
        }
        return ranked.sortedWith(compareBy({ it.candidate.run }, { -it.length }, { if (it.endsRun) 0 else 1 }, { it.order }))
            .map { it.candidate }.distinctBy { it.text.lowercase() }
            .groupBy { it.run }.values.flatMap { candidates ->
                val bounded = candidates.take(MAX_PHRASES).toMutableList()
                // Retain the head noun even when a long predicate consumes the phrase budget.
                candidates.firstOrNull { ' ' !in it.text }?.let { head ->
                    if (head !in bounded) { bounded.removeAt(bounded.lastIndex); bounded += head }
                }
                bounded
            }
    }

    /** An explanation's first noun phrase, then those opening its later clauses; see [candidates]. */
    private fun explained(runs: List<Run>): List<Run> {
        // "Why do some people get seasick": the people of the scenario open no subject; the next phrase does.
        val all = runs.dropWhile { run -> run.words.all { it.lowercase() in genericAlways } }
        return if (all.isEmpty()) all else listOf(all.first()) + all.drop(1).filter { run ->
            // "meeting" in "without us ever meeting" is a verb; a clause's subject follows its auxiliary.
            (run.before == null || run.before in clauseStarts) &&
                !(run.words.size == 1 && run.words[0].first().isLowerCase() && run.words[0].endsWith("ing"))
        }.take(FALLBACK_RUNS)
    }

    fun phrases(question: String): List<String> = candidates(question, 2).map { it.text }

    private fun possessor(word: String) = word.removeSuffix("'s").removeSuffix("’s")

    /**
     * A shorter piece of the noun phrase is the subject only if its article mentions the rest:
     * "Tide" for "ocean tides" mentions the ocean; "Address" for "stealth address" and "Wallet"
     * for "hot wallet" are other things that share a word. The word just before the piece is the
     * one that narrows it, so when there is one the article must mention that word: the leather
     * "Wallet" may say "keep" but never "hardware".
     */
    fun accepts(candidate: Candidate, article: Evidence, headings: List<String> = emptyList()): Boolean {
        if (candidate.rest.isEmpty()) return true
        // The article's section headings say what it covers when its first passage does not:
        // "Headphones — Ambient noise reduction" for "noise-cancelling headphones".
        val tokens = word.findAll((listOf(article.text) + headings.map { it.substringAfter(" — ", "") }).joinToString(" ").lowercase()).map { it.value }.toSet()
        fun mentionsWord(lower: String): Boolean {
            // A stem under four letters is in too many words ("go" in "government"); a short word must appear whole.
            if (lower.length < 4) return lower in tokens || lower + "s" in tokens
            val stem = lower.take(maxOf(4, lower.length - 2))
            // A compound holds the word too: "smartphones" mentions "phone", "headsets" mentions "set" only by stem.
            return tokens.any { it.startsWith(stem) || lower.length >= 5 && lower in it }
        }
        // A hyphenated modifier is mentioned by any of its words: "noise" for "noise-cancelling".
        fun mentions(w: String): Boolean {
            val lower = w.lowercase()
            val parts = lower.split('-', '’', '\'').filter { it.length >= 4 }
            return mentionsWord(lower) || parts.size > 1 && parts.any(::mentionsWord) || parts.size == 1 && '-' in lower && mentionsWord(parts[0])
        }
        val modifier = candidate.modifier ?: return candidate.rest.any(::mentions)
        if (modifier.first().isUpperCase() && narrows(modifier, candidate.text)) {
            // Dropping the beginning of a proper name can select another entity:
            // "Formula One championships" is not "ONE Championship". Named
            // qualifiers are exact words, not FTS stems; if the retained phrase
            // also opens with a name, keep the connecting name words together.
            val words = word.findAll((listOf(article.title, article.text) + headings).joinToString(" "))
                .map { possessor(it.value).lowercase() }.toList()
            val first = word.find(candidate.text)?.value ?: return false
            val required = listOf(modifier, first).map { possessor(it).lowercase() }
            return if (first.first().isUpperCase()) words.windowed(2).any { it == required }
                else required.first() in words
        }
        return !narrows(modifier, candidate.text) || mentions(modifier)
    }

    /**
     * Whether the word before a phrase narrows which article it names. "hot" narrows "wallet"; "next"
     * says when the halving is, not which Bitcoin. A known period adjective can
     * frame a broader named subject ("Classic Maya civilization"); arbitrary
     * capitalized name words cannot be silently discarded.
     */
    private fun narrows(modifier: String, phrase: String): Boolean = when {
        modifier.first().isUpperCase() -> !(modifier.lowercase() in periodModifiers && phrase.first().isUpperCase() && ' ' in phrase)
        else -> modifier.lowercase() !in loose
    }

    private val periodModifiers = setOf("early", "late", "modern", "classic", "classical")

    private fun singular(w: String): String? = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
        else -> null
    }

    /**
     * The article that lists [name] among its other names ("Global Positioning System" for "GPS"),
     * in the singular or the plural ("Tropical cyclone" lists "Hurricanes" for "hurricane"). An
     * acronym is matched as written: "GP" is not "GPS".
     */
    fun aliasOf(name: String, found: List<Evidence>): String? {
        fun plain(value: String) = value.lowercase().replace('-', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = plain(name)
        val names = if (name.length < 3 || name.all { !it.isLowerCase() }) setOf(lower)
        else setOfNotNull(lower, lower + "s", lower + "es", singular(lower))
        return found.firstOrNull { item ->
            item.title.endsWith(" — Overview") && item.text.lineSequence().any { line ->
                line.startsWith("Also known as:") && line.removePrefix("Also known as:").split(';').any { plain(it) in names }
            }
        }?.title?.removeSuffix(" — Overview")
    }

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
        if (!headsSection(phrase)) return null
        val names = listOf(phrase, phrase + "s", phrase + "es").map { it.lowercase() }
        return found.firstOrNull { item ->
            !item.source.contains("wikivoyage", ignoreCase = true) && !item.title.endsWith(" — Overview") &&
                item.title.substringAfterLast(" > ").substringAfterLast(" — ").trim().lowercase() in names
        }
    }

    /**
     * Whether [phrase] may head a section rather than an article: a name of two or more words, or a
     * condition's noun ("seasickness" under "Motion sickness"); a lone common word heads too many.
     */
    fun headsSection(phrase: String): Boolean = ' ' in phrase.trim() || phrase.trim().lowercase() in conditions.values

    /** The condition a question's adjective names: "seasick" is "seasickness". */
    fun conditionOf(phrase: String): String? = conditions[phrase.trim().lowercase()]

    private val qualified = Regex("^(.*?) \\(([^()]+)\\)$")

    /**
     * The overview's first passage when its title is exactly [phrase] ("Heat pump — Overview"),
     * apostrophes aside: "Thirty Years War" is the article "Thirty Years' War". Given the
     * [question], a title that qualifies the name ("Georgia (country) — Overview") is the subject
     * when the question uses the qualifier's word, or when it is the only qualified title found.
     */
    fun overviewOf(phrase: String, found: List<Evidence>, question: String? = null): Evidence? {
        // "ha long bay" names "Hạ Long Bay", "reykjavik" names "Reykjavík": people rarely type the accents.
        fun plain(value: String) = QuestionText.foldAccents(value).replace(Regex("['’]s\\b"), "").replace("'", "").replace("’", "")
            .replace('-', ' ').replace(Regex("\\s+"), " ").trim()
        val overviews = found.filter { it.title.endsWith(" — Overview") }
        overviews.firstOrNull { plain(it.title.removeSuffix(" — Overview")).equals(plain(phrase), ignoreCase = true) }?.let { return it }
        if (question == null) return null
        val qualifiedTitles = overviews.mapNotNull { item ->
            qualified.find(item.title.removeSuffix(" — Overview"))?.takeIf { plain(it.groupValues[1]).equals(plain(phrase), ignoreCase = true) }
                ?.let { item to it.groupValues[2] }
        }
        val asked = word.findAll(question.lowercase()).map { it.value.trim('.') }.toSet()
        return qualifiedTitles.firstOrNull { (_, qualifier) -> word.findAll(qualifier.lowercase()).any { it.value.trim('.') in asked } }?.first
            ?: qualifiedTitles.singleOrNull()?.first
    }

    /**
     * The kinds of thing the question says its subject is ("country", "state"), to look a title
     * up with: "Georgia (country)". A listing by title length rarely reaches the qualified title
     * among the many that merely contain the name.
     */
    fun qualifiers(question: String): List<String> =
        word.findAll(question.lowercase()).map { it.value }.filter { it in qualifierWords }.distinct().toList()

    /**
     * Whether an overview only says what kind of thing its subject is, in under [STUB_CHARS]:
     * "A Hurricane is a cyclone type that is formed in the Atlantic Ocean". Such a page points at
     * the article that explains it, found under another of the subject's names.
     */
    fun isStub(overview: Evidence): Boolean {
        val passage = overview.text.lineSequence().filterNot { it.startsWith("Also known as:") }.joinToString("\n").trim()
        if (passage.length >= STUB_CHARS) return false
        val first = passage.split(Regex("(?<=[.!?])\\s")).first()
        return Regex("(?i)^(?:a |an |the )?[^.]{1,60}?\\b(?:is|was|are|were) (?:a|an|the|one|any)\\b").containsMatchIn(first)
    }

    private fun isEncyclopedia(item: Evidence) = ArticleDepth.isEncyclopedia(item)
    private fun isTravel(item: Evidence) = item.source.contains("wikivoyage", ignoreCase = true) || item.documentId.startsWith("wvs-")

    /**
     * The article to read for a subject found as [found], given the [alternatives] found under the
     * subject's own name in another collection and under its other names, in that order. An
     * explanation is read from an encyclopedia article rather than a travel guide of the same name
     * ("Jet lag"), and from a full article rather than a stub ("Hurricane" is "Tropical cyclone");
     * otherwise [found] stays. Each alternative must still pass [accepts] for the [candidate].
     */
    fun fuller(candidate: Candidate, found: Evidence, alternatives: List<Evidence?>): Evidence {
        // The same article in another collection, or under another of its names, is already the subject.
        val others = alternatives.filterNotNull().filter { it.documentId != found.documentId && (sameArticle(found, it) || accepts(candidate, it)) }
        if (others.isEmpty()) return found
        if (isTravel(found)) others.firstOrNull(::isEncyclopedia)?.let { return it }
        if (isStub(found)) {
            fun length(item: Evidence) = item.text.lineSequence().filterNot { it.startsWith("Also known as:") }.sumOf { it.length }
            others.firstOrNull { length(it) > length(found) }?.let { return it }
        }
        return found
    }

    // A question about how its subject ended, which a second article often tells better than the
    // subject's own: "Dissolution of the Soviet Union", "Classic Maya collapse".
    private val asksEnd = Regex("(?i)\\bcollaps|\\bfall\\b|\\bfell\\b|\\bdissol|\\bdeclin|\\bend(?:ed)?\\b|\\bhistory\\b|\\bwhat(?:ever)? happened\\b")
    /** Title words of such an article, for [Retriever.headed] with the subject's name. */
    val RELATED_WORDS = listOf("dissolution", "collapse", "fall", "decline", "history", "crisis")
    private val namesCause = Regex("(?i)\\b(?:collapse[sd]?|dissol\\w+|declin\\w+|fall|fell|caus\\w+|because|result\\w*|reasons?|crisis|stagnation|abandon\\w*|conquer\\w*|defeat\\w*)\\b")

    /** The title words to pair with the subject in a title query, when [question] asks how it ended; null otherwise. */
    fun relatedWords(question: String): List<String>? =
        RELATED_WORDS.takeIf { asksEnd.containsMatchIn(discoveryQuestion(question)) }

    /**
     * The names to query for an article on [subject]'s end: the subject, then the capitalised words
     * alone when they are a name of their own ("Maya" of "Maya civilization" titles "Classic Maya
     * collapse"). A lowercase single word is too common a title word to query.
     */
    fun relatedNames(subject: String): List<String> {
        val name = subject.trim()
        val proper = word.findAll(name).map { it.value }.filter { it.first().isUpperCase() }.joinToString(" ")
        return when {
            ' ' !in name && name.first().isLowerCase() -> emptyList()
            proper.isNotEmpty() && !proper.equals(name, ignoreCase = true) -> listOf(name, proper)
            else -> listOf(name)
        }
    }

    /**
     * Among [found], the rows [Retriever.headed] gave for [name] and [RELATED_WORDS], the overview of
     * the article on how the subject ended: one titled "<Words> of (the) Name", "Name <word>",
     * "<Adjective> Name <word>" or "History of Name", the name whole. An overview that names a cause
     * is preferred, then the shorter title; a title with a qualifier in brackets ("History of the
     * Soviet Union (1985–1991)") only when no plain title fits.
     */
    fun relatedTitle(name: String, found: List<Evidence>): Evidence? {
        val n = Regex.escape(name.trim())
        val w = "[\\p{L}\\p{N}'’-]+"
        val shapes = listOf(Regex("(?i)^(?:$w )+(?:of|de|von) (?:the )?$n$"), Regex("(?i)^$n $w$"), Regex("(?i)^$w $n $w$"))
        val overviews = found.filter { it.title.endsWith(" — Overview") && chunkNumber(it) == 0 && isEncyclopedia(it) }
            .filter { item ->
                val title = articleName(item)
                val plain = qualified.find(title)?.groupValues?.get(1) ?: title
                !plain.equals(name, ignoreCase = true) && shapes.any { it.matches(plain) }
            }
        return overviews.sortedWith(compareBy({ if (namesCause.containsMatchIn(it.text)) 0 else 1 },
            { if (qualified.matches(articleName(it))) 1 else 0 }, { articleName(it).length })).firstOrNull()
    }

    private fun chunkNumber(item: Evidence): Int = item.chunkId.substringAfterLast(':').toIntOrNull() ?: -1

    private fun articleName(item: Evidence) = item.title.substringBefore(" — ")
    private fun aliases(item: Evidence): Set<String> = item.text.lineSequence().filter { it.startsWith("Also known as:") }
        .flatMap { it.removePrefix("Also known as:").split(';').asSequence() }.map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()

    /** Whether two overviews are the same article: one title, or one listed among the other's names. */
    private fun sameArticle(a: Evidence, b: Evidence): Boolean =
        articleName(a).equals(articleName(b), ignoreCase = true) || articleName(a).lowercase() in aliases(b) || articleName(b).lowercase() in aliases(a)

    // An explanation, comparison or history question is opened by what the saved article says of
    // its subject; a personal how-to ("how do I…") is not answered by a definition.
    private val explainsForm = Regex("(?i)^\\s*(?:how (?:does|do|is|are|can|did) (?!i\\b|we\\b|you\\b)|why (?:does|do|is|are|did|was|were|can|could|would|should|will|may|might|must|has|have) |what (?:causes|caused|led to|distinguishes|is|are|was|were) |" +
        "explain |describe |compare |how (?:is|are|does|do) .+ (?:differ|different)|" +
        // "What happened to the Aral Sea", "what's the deal with gas fees": a history or account of the subject.
        "what(?:ever)? happened (?:to|in|with) |what(?:['’]s| is) the (?:story|deal) with )|" +
        "\\bwhat(?:['’]s| is| are) (?:the )?(?:actual |real |main |key |big )?differences? between\\b")

    /** An explanation or comparison by its wording alone; see [leads] for a choice between options. */
    fun explains(question: String): Boolean = explainsForm.containsMatchIn(discoveryQuestion(question))

    // "A or B, which is better": two options and a judgement asked between them. The second option
    // must be a thing, not an auxiliary or pronoun ("pay in euros or in my home currency" is not a choice).
    private val choiceAsk = Regex("(?i)\\b(?:which(?: one)?|better|best|cheaper|safer|faster|stronger|healthier|differences?|differ|prefer|preferable|recommend|" +
        "should (?:i|we|you) (?:take|use|choose|pick|buy|get|go)|what(?:['’]s| is) the difference)\\b")
    private val choiceJoin = Regex("(?i)\\b(?:or|vs\\.?|versus)\\b")
    private val notOptions = framing + setOf("or", "and", "can", "could", "do", "does", "did", "is", "are", "was", "were", "should", "would", "will",
        "shall", "may", "might", "must", "he", "she", "his", "her", "him", "them", "at", "as", "then", "not", "no", "yes", "whether", "here")
    /** An option's noun phrase runs to at most this many words back from the "or". */
    private const val OPTION_WORDS = 4

    /** The "A or B" of a choice question: where the first option's noun phrase starts, that option, and the second's first word. */
    internal class Choice(val start: Int, val first: String, val second: String)

    /**
     * The choice a question puts, when it asks one. The first option is the whole noun phrase before
     * the "or" ("hardware wallet like a Ledger", not "wallet like a Ledger"), reaching back over
     * "like a", which compares the option with an example rather than naming it.
     */
    internal fun choice(question: String): Choice? {
        val asked = discoveryQuestion(question)
        if (!choiceAsk.containsMatchIn(asked)) return null
        val matches = word.findAll(asked).toList()
        fun bare(index: Int) = possessor(matches[index].value.trim('\'', '’', '-')).lowercase()
        fun gap(index: Int) = asked.substring(matches[index].range.last + 1, matches[index + 1].range.first)
        for ((index, m) in matches.withIndex()) {
            if (!choiceJoin.matches(m.value)) continue
            var second = index + 1
            if (second < matches.size && bare(second) in setOf("a", "an", "the")) second++
            if (second >= matches.size || bare(second) in notOptions || (index until second).any { !gap(it).isBlank() }) continue
            val last = index - 1
            if (last < 0 || bare(last) in notOptions || gap(last).any { it !in ", " }) continue
            var start = last
            while (start > 0 && start > last - OPTION_WORDS + 1 && bare(start - 1) !in notOptions && gap(start - 1).isBlank()) start--
            // "hardware wallet like a Ledger": the phrase before the simile is the option.
            if (start >= 2 && bare(start - 1) in setOf("a", "an") && bare(start - 2) in setOf("like", "unlike") && start >= 3 && bare(start - 3) !in notOptions) {
                var head = start - 3
                while (head > 0 && head > start - 3 - OPTION_WORDS + 1 && bare(head - 1) !in notOptions && gap(head - 1).isBlank()) head--
                start = head
            }
            val from = matches[start].range.first
            return Choice(from, asked.substring(from, matches[last].range.last + 1), matches[second].value)
        }
        return null
    }

    /** Whether the question chooses between named options: "DEET vs picaridin, which is better?". */
    fun isChoice(question: String): Boolean = choice(question) != null

    /** Whether the answer opens with what the saved articles say of the question's subjects. */
    fun leads(question: String): Boolean = explains(question) || isChoice(question) || compares(question)

    // "Cheetah vs leopard: how do you tell them apart?", "What's the difference between a frog and a toad?":
    // a comparison reads the article of each subject it names.
    private val tellApart = Regex("(?i)\\btell\\b[^.?!]{1,60}?\\bapart\\b|\\btell the difference\\b")
    fun compares(question: String): Boolean {
        val asked = discoveryQuestion(question)
        return comparisonWords.containsMatchIn(asked) || tellApart.containsMatchIn(asked)
    }

    // Factual lookups: who, when, where, which; yes-or-no questions about facts; how many, big or
    // old; and "tell me about X". The articles they name hold the answer, as an explanation's
    // subject does.
    private val looksUpForms = Regex("(?i)^\\s*(?:who|when|where|which|whose|did|does|do|is|are|was|were|has|have|could|can)\\b|" +
        "^\\s*how (?:many|much|big|tall|high|long|old|far|deep|heavy|large|fast)\\b|" +
        "^\\s*(?:tell me about|what do you know about|give me an overview of|teach me about)\\b")
    // "What is the population of Canada?" asks a fact about Canada, not what a population is.
    private val factOf = Regex("(?i)^\\s*what(?:'s|’s| is| was| are| were)\\b.*\\b(?:population|capital|currency|official languages?|" +
        "height|area|tallest|largest|biggest|highest|longest|deepest|oldest|fastest|heaviest|smallest|most|least)\\b")

    /** A factual lookup, which reads the articles it names; an explanation [leads] with its one subject. */
    fun looksUp(question: String): Boolean {
        val asked = discoveryQuestion(question)
        // A comparison of facts ("how big is Australia compared to Europe") is still a lookup of each side.
        return factOf.containsMatchIn(asked) || !(explains(asked) || isChoice(asked)) && looksUpForms.containsMatchIn(asked)
    }

    private val comparisonWords = Regex("(?i)\\b(?:difference|differences|differ|different|distinguish|distinguishes|distinguishing|compare|compared|comparison|versus|vs\\.?)\\b")
    // Between a comparison's subjects: "a hurricane, a typhoon and a cyclone", "hostels compare with guesthouses".
    private val chainWords = setOf("and", "or", "vs", "versus", "from", "with", "than", "to", "a", "an", "the", "differ", "differs",
        "different", "compared", "compare")
    private val separators = setOf("and", "or", "vs", "versus", "from", "with", "than", "to")

    /**
     * How many subjects the question names: three for a factual [lookup]; for a comparison or a
     * choice, the noun phrases chained from its first by "and", "or", "versus", "from" or a comma,
     * two at least and [MAX_SUBJECTS] at most; otherwise one.
     */
    fun subjectsWanted(question: String, lookup: Boolean = false): Int {
        if (lookup) return MAX_SUBJECTS
        val asked = discoveryQuestion(question)
        val chosen = choice(asked)
        if (chosen == null && !comparisonWords.containsMatchIn(asked) && !tellApart.containsMatchIn(asked)) return 1
        val text = chosen?.let { asked.substring(it.start) } ?: asked
        val matches = word.findAll(text).toList()
        // Each noun phrase as the range of its word indices.
        val runs = ArrayList<IntRange>()
        var start = -1
        for ((index, m) in matches.withIndex()) {
            val w = m.value.trim('\'', '’', '-')
            val bare = possessor(w).lowercase()
            if (bare in framing || bare in emphasis || w.length < 2 || w.all(Char::isDigit)) {
                if (start >= 0) runs += start..index - 1
                start = -1
            } else if (start < 0) start = index
        }
        if (start >= 0) runs += start..matches.lastIndex
        var count = 1
        for ((first, second) in runs.zipWithNext()) {
            val gap = (first.last + 1 until second.first).map { matches[it].value.lowercase().trim('\'', '’', '-') }
            val between = text.substring(matches[first.last].range.last + 1, matches[second.first].range.first)
            val joined = gap.all { it in chainWords } && (gap.any { it in separators } || ',' in between) && between.none { it in "?.!;" }
            if (!joined) break
            count++
        }
        return count.coerceIn(2, MAX_SUBJECTS)
    }

    // A question answered by how or why its subject does something is opened by the section that
    // explains it; "what is X and how…" still opens with the definition.
    private val asksWhyOrHow = Regex("(?i)^\\s*(?:why|how|what (?:causes|caused|led to|makes))\\b")
    private val pronounOpening = Regex("(?i)^(?:it|its|this|these|those|they|their|such|he|she|there|here)\\b")

    /**
     * The definition of each subject found, quoted from its overview, for the answer's opening. For
     * a why or how question about one subject, the opening is instead the first sentences of the
     * section among the [depth] passages that answers it ("Principles" for how GPS works), when that
     * section was packed into [sources]; the definition stays when no section's heading matches.
     */
    fun lead(question: String, subjects: List<Evidence>, sources: List<PromptSource>, depth: List<Evidence> = emptyList()): SourceLead.Lead? {
        if (!leads(question)) return null
        val asked = discoveryQuestion(question)
        val explained = subjects.size == 1 && asksWhyOrHow.containsMatchIn(asked)
        val quotes = subjects.mapNotNull { subject ->
            val section = if (explained) ArticleDepth.answering(asked, subject.title.substringBefore(" — "), depth) else null
            section?.let { quote(it, sources) }?.takeUnless { pronounOpening.containsMatchIn(it.second) }
                ?: quote(subject, sources)?.takeIf { (_, text) -> answersWhy(asked, subject, text) }
        }
        if (quotes.isEmpty()) return null
        // "Why did the Soviet Union collapse" opens with the article that names the causes
        // ("Dissolution of the Soviet Union"), not the one that only says what the Union was.
        val ordered = if (asksCause.containsMatchIn(asked)) quotes.sortedBy { if (givesCause.containsMatchIn(it.second)) 0 else 1 } else quotes
        return SourceLead.Lead(ordered.first().first, ordered.first().second, ordered.drop(1))
    }

    private val asksCause = Regex("(?i)\\b(why|caus\\w*|reasons?)\\b")
    private val givesCause = Regex("(?i)\\b(because|caused?|causes|causing|due to|result(?:s|ed)? (?:from|in)|reasons?|leads? to|makes?|produces?|occurs? when|happens? when)\\b")

    /**
     * "Why are sunsets red and orange?" is not answered by "Sunset is the disappearance of the Sun";
     * a definition opens a why question only when it names a cause or a word the question asks about.
     */
    private fun answersWhy(question: String, subject: Evidence, definition: String): Boolean {
        if (!asksCause.containsMatchIn(question)) return true
        val name = word.findAll(subject.title.substringBefore(" — ").lowercase()).map { it.value }.toSet()
        val asked = word.findAll(question.lowercase()).map { it.value }
            .filter { it.length > 3 && it !in framing && it !in name && it.removeSuffix("s") !in name }.toList()
        // "Why does inflation happen?" asks about nothing but its subject: a definition that names a cause will do.
        if (asked.isEmpty()) return givesCause.containsMatchIn(definition)
        val said = word.findAll(definition.lowercase()).map { it.value }.toSet()
        return asked.any { it in said || it.removeSuffix("s") in said }
    }

    private fun quote(item: Evidence, sources: List<PromptSource>): Pair<String, String>? {
        val source = sources.firstOrNull { it.evidence.documentId == item.documentId && it.evidence.chunkId == item.chunkId } ?: return null
        // A standard's first passage opens with its name and status lines; its summary follows.
        val text = source.excerpt.let { t ->
            if (Regex("^(?:EIP|ERC)-\\d+:").containsMatchIn(t)) t.split(Regex("\\n\\s*\\n")).drop(1).joinToString("\n\n") else t
        }
        return SourceLead.leadText(EvidenceRelevance.passageText(text))?.let { source.citationId to it }
    }
}
