package xyz.fieldatlas.research

/** Conservative post-retrieval gate: irrelevant passages are worse than an honest model-only answer. */
object EvidenceRelevance {
    /** Short causal questions use the original terms as a strict lexical boundary.
     * Avoid a second generative search turn when that boundary found no evidence;
     * an explicitly unverified model-only answer is preferable to query drift. */
    internal fun isShortCausalQuestion(question: String): Boolean =
        comparisonTerms(question).isEmpty() && PromptBuilder.allowsModelExplanation(question) &&
            FtsQuery.from(question)?.terms.orEmpty().size in 2..3 &&
            Regex("(?i)^\\s*(?:explain\\s+)?(?:why|how)\\b").containsMatchIn(question)

    /** Lexical gap disclosure only. Presence is not proof of adequate coverage. */
    fun missingComparisonSubjects(question: String, evidence: List<Evidence>): List<String> =
        comparisonTerms(question).filter { subject ->
            evidence.none { tokens(topicalText(it.text)).matches(subject) }
        }

    internal fun comparisonTerms(question: String?): List<String> {
        if (question == null) return emptyList()
        // Only unambiguous single-word subjects. Multiword names and trailing clauses
        // retain the normal gate rather than turning a modifier into a subject.
        val comparison = Regex("(?i)^\\s*compare\\s+(?:(?:a|an|the)\\s+)?([\\p{L}\\p{N}-]+)\\s+(?:and|with|versus|vs\\.?)\\s+(?:(?:a|an|the)\\s+)?([\\p{L}\\p{N}-]+)\\s*(?=[.!?]|$)")
            .find(question) ?: Regex(
                "(?i)^\\s*how\\s+(?:does|do|is|are)\\s+(?:(?:a|an|the)\\s+)?([\\p{L}\\p{N}-]+)\\s+(?:differ|different)\\s+from\\s+(?:(?:a|an|the)\\s+)?([\\p{L}\\p{N}-]+)\\s*(?=[.!?]|$|,\\s+and\\s+(?:when|how|why)\\b)",
            ).find(question) ?: return emptyList()
        return FtsQuery.from(comparison.groupValues[1] + " " + comparison.groupValues[2])?.terms.orEmpty()
    }
    // Indexing vocabulary helps discovery, but is not a claim made by a passage.
    internal fun passageText(text: String): String = text.lineSequence()
        .filterNot { it.trimStart().startsWith("MeSH:", ignoreCase = true) }
        .joinToString("\n").trim()

    private fun topicalText(text: String): String {
        val passage = passageText(text)
        // Structured scholarly headers describe provenance, not the subject of the
        // abstract. Keep Evidence itself intact for the source viewer and attribution.
        val abstract = Regex("(?im)^Abstract:\\s*").find(passage)
        return if (passage.startsWith("PMID ") && abstract != null) {
            passage.substring(abstract.range.last + 1)
        } else passage
    }

    internal fun rankByPassage(evidence: List<Evidence>, terms: List<String>): List<Evidence> {
        // Keep structured place ranking intact. Scholarly keyword lists otherwise receive
        // title/BM25 boosts even when the actual passage covers none of the requested topics.
        if (evidence.none { it.text.contains(Regex("(?im)^\\s*MeSH:")) }) return evidence
        return evidence.sortedByDescending { item ->
            val body = tokens(topicalText(item.text))
            terms.filterNot(lookupWrappers::contains).count { body.matches(it) }
        }
    }
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val metadataLine = Regex("^\\s*(?:PMID \\d+|(?:Year|Journal|Authors?|DOI|PMCID|Published|Publication types?|Keywords|MeSH):)")
    private val conceptPattern = Regex("^concept match ([0-9.]+)$")
    private val lookupWrappers = setOf("best", "recommend", "suggest", "find", "list", "listed", "which", "where")
    private val genericTerms = setOf(
        "study", "studies", "report", "reports", "result", "results", "outcome", "outcomes",
        "different", "difference", "differences", "example", "practical", "two", "one", "new",
        "information", "data", "know", "fresh", "important", "evidence", "claim", "claims",
    )

    fun keep(
        evidence: List<Evidence>,
        questionTerms: List<String>,
        expandedTerms: List<String> = emptyList(),
        question: String? = null,
    ): List<Evidence> {
        if (questionTerms.isEmpty()) return evidence
        // Page furniture and another language's phrases say nothing, whatever words they share.
        val readable = evidence.filterNot { item ->
            isForeignPhrasebook(item, question) ||
                (question != null && PromptBuilder.allowsModelExplanation(question) && isNavigationBoilerplate(item))
        }
        val kept = gate(readable, questionTerms, expandedTerms, question)
        val overview = overviewSubjects(question)
        if (kept.isNotEmpty() || overview.isEmpty() || comparisonTerms(question).isNotEmpty()) return kept
        // "What is danksharding?" asks for a definition, but when no saved passage defines the
        // subject, the passages whose text names it ("EIP-4844 (Proto-Danksharding) introduced
        // blobs") and shares another of the question's words ("Ethereum") still say more than a
        // model answering from nothing. A title alone ("2-MeV Fast Neutrons") does not count.
        val subjectTerms = overview.flatMap { FtsQuery.from(it)?.terms.orEmpty() }.toSet()
        val otherTerms = questionTerms.filterNot { it in subjectTerms || it in lookupWrappers || it in genericTerms }
        return rankByPassage(readable.filter { item ->
            val passage = topicalText(item.text)
            !isTravelListing(item) && overview.any { containsPhrase(passage, it) } &&
                (otherTerms.isEmpty() || tokens(item.title + " " + passage).let { body -> otherTerms.any { body.matches(it) } })
        }, questionTerms)
    }

    private fun gate(
        evidence: List<Evidence>,
        questionTerms: List<String>,
        expandedTerms: List<String>,
        question: String?,
    ): List<Evidence> {
        val subjects = comparisonTerms(question)
        val overview = overviewSubjects(question)
        // Request boilerplate is not evidence of the requested subject. In source-only
        // mode, planner vocabulary must not substitute for that subject either.
        val sourceOnly = question != null && !PromptBuilder.allowsModelExplanation(question)
        val relevanceTerms = if (sourceOnly) questionTerms.filterNot {
            it in setOf("according", "saved", "local", "provided", "attached", "source", "sources",
                "document", "documents", "file", "files", "notes", "summarize", "summarise",
                "today", "currently", "current", "now")
        } else questionTerms
        val namedArticles = namedArticleSubjects(evidence, question)
        val generalExplanation = question != null && !isAttributeQuestion(question) && PromptBuilder.allowsModelExplanation(question) &&
            Regex("(?i)^\\s*(?:explain|describe|why|how|can|compare|what\\s+(?:is|are|makes)|tell\\s+me\\s+about)\\b").containsMatchIn(question)
        val causalExplanation = subjects.isEmpty() && question != null && PromptBuilder.allowsModelExplanation(question) &&
            Regex("(?i)^\\s*(?:explain\\s+)?(?:why|how)\\b").containsMatchIn(question)
        val overviewAnchors = evidence.filter {
            isRequestedOverview(it, question) && hasOverviewStatement(it, overview)
        }
        return rankByPassage(evidence.filter { item ->
            (if (overview.isNotEmpty()) hasOverviewStatement(item, overview) ||
                overviewAnchors.any { anchor -> isAdjacentOverview(item, anchor) }
             else if (subjects.isEmpty()) (if (isStructuredPlace(item)) isPlaceRelevant(item, relevanceTerms)
                 else isRelevant(item, relevanceTerms, if (sourceOnly) emptyList() else expandedTerms)) ||
                 (!sourceOnly && isNamedReferenceDefinition(item, question))
             else subjects.any { tokens(topicalText(item.text)).matches(it) }) &&
                // A place listing cannot explain a physical mechanism merely because its
                // menu contains the same words. For short causal questions, require every
                // original content term in the body unless an independently computed strong
                // concept match bridges the vocabulary gap. Similarity is not fact checking.
                (!causalExplanation || (!isTravelListing(item) &&
                    (questionTerms.size !in 2..3 || isStrongConceptMatch(item) ||
                        questionTerms.all { tokens(topicalText(item.text)).matches(it) }))) &&
                (!generalExplanation || !isTravelListing(item)) &&
                (!generalExplanation || overview.isNotEmpty() || isStrongConceptMatch(item) ||
                    isSupportedConceptMatch(item, relevanceTerms) || explanationTopicMatches(item, relevanceTerms, question)) &&
                destinationMatches(item, question) && categoryMatches(item, question) && placeAsked(item, question) &&
                dietaryMatches(item, question) &&
                // The definition of the asked subject stays even when the question also names
                // another article: "Explain BLS signatures and why Ethereum validators aggregate them".
                (namedArticles.isEmpty() || isTravelListing(item) ||
                    (overview.isNotEmpty() && hasOverviewStatement(item, overview)) || namedArticles.any { subject ->
                    containsPhrase(item.title.substringBefore(" — ") + " " + topicalText(item.text), subject)
                })
        }, subjects.ifEmpty { relevanceTerms })
    }

    /** A question can name a precise multiword topic while using different words
     * for its consequences. Require the actual definition, not just a title hit. */
    private fun isNamedReferenceDefinition(item: Evidence, question: String?): Boolean {
        if (question == null || !item.title.endsWith(" — Overview") || isTravelListing(item)) return false
        val parent = item.title.removeSuffix(" — Overview")
        val terms = FtsQuery.from(parent)?.terms.orEmpty()
        val body = tokens(topicalText(item.text))
        return terms.size >= 2 && terms.all { body.matches(it) } &&
            containsPhrase(question, parent) && hasOverviewStatement(item, listOf(parent))
    }

    /** An incidental phrase in a long article is not enough for a general explanation.
     * Prefer a named topic in the title, or a compact explanatory statement in the body.
     * Specific source/study questions retain the normal relevance path. */
    private fun explanationTopicMatches(item: Evidence, terms: List<String>, question: String?): Boolean {
        val topicTerms = terms.filterNot { it in setOf("both", "same", "need", "needs", "affect", "different", "differ", "useful") }
        if (topicTerms.isEmpty()) return false
        val title = tokens(item.title)
        val titleHits = topicTerms.count { title.matches(it) }
        // Two broad words in a long question (for example room + temperature)
        // must not outweigh its actual subjects (metal + wood).
        if (titleHits >= maxOf(minOf(2, topicTerms.size), (topicTerms.size * 2 + 2) / 3)) return true
        val explanation = Regex("(?i)\\b(?:because|due to|caused by|results? from|results? in|therefore|through|enables?|allows?|gives?|causes?|requires?|converts?|consists? of|defined as|refers? to)\\b")
        val required = if (topicTerms.size <= 3) topicTerms.size else (topicTerms.size * 2 + 2) / 3
        return topicalText(item.text).split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
            .any { sentence ->
                explanation.containsMatchIn(sentence) &&
                    topicTerms.count { tokens(sentence).matches(it) } >= required
            } || hasOverviewStatement(item, listOf(item.title.substringBefore(" — "))).let { overview ->
                // This fallback requires an explicitly named sectioned reference topic, and a title
                // of one question word must be the question's subject, not the last word of it.
                overview && " — " in item.title && titleHits > 0 &&
                    (titleHits >= 2 || question == null || titleSubjectIsQuestionSubject(item, question, topicTerms))
            }
    }

    /**
     * "Wallet — Overview" (a leather one) defines the last word of "how does a hardware wallet keep
     * my key safe", not its subject: when the question puts another of its content words right
     * before the title's word, the passage must say that word too. "Tide — Overview" says "ocean"
     * for "what causes ocean tides"; the wallet article never says "hardware". English puts the
     * narrowing noun in the singular ("hardware wallet", "ocean tides", "seed phrase"), so a plural
     * before the title's word ends its own phrase instead: "two ways agriculture changed societies"
     * asks about agriculture, not about a "ways agriculture".
     */
    private fun titleSubjectIsQuestionSubject(item: Evidence, question: String, topicTerms: List<String>): Boolean {
        val head = tokenPattern.findAll(item.title.substringBefore(" — ")).map { it.value.lowercase() }.firstOrNull() ?: return false
        val words = tokenPattern.findAll(question).map { it.value.lowercase() }.toList()
        val modifiers = words.indices.filter { index -> index > 0 && setOf(words[index]).matches(head) }
            .map { index -> words[index - 1] }
            .filter { before -> topicTerms.any { term -> term.equals(before, ignoreCase = true) } && !isPluralNoun(before) }
        if (modifiers.isEmpty()) return true
        val body = tokens(topicalText(item.text))
        return modifiers.any { modifier -> body.matches(modifier) }
    }

    /** "ways" and "reasons", but not "glass", "bus" or "basis": a word spelled as a plural. */
    private fun isPluralNoun(word: String): Boolean =
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is")

    /**
     * A blog chunk that is mostly page furniture: three or more lines like "Permalink", "Posted by",
     * "Read 6 Comments" or "Add a Comment", or one such line with under 40% letters in what it
     * shows. A table of constants with no such line stays (a consensus spec's parameters are a
     * quarter letters). Only a question the model may explain drops it; a source-only request
     * reads whatever it attached.
     */
    private fun isNavigationBoilerplate(item: Evidence): Boolean {
        if (isTravelListing(item)) return false
        val markers = item.text.lineSequence().count { navigationLine.matches(it) }
        if (markers >= 3) return true
        val visible = item.text.filterNot(Char::isWhitespace)
        return markers >= 1 && visible.isNotEmpty() && visible.count(Char::isLetter) < visible.length * 0.4
    }

    private val navigationLine = Regex("(?i)^\\s*(?:permalink|posted by\\b.*|add a comment|(?:read\\s+)?\\d*\\s*comments?|comments?\\s*\\(\\d+\\)|" +
        "view comments?|reply|share(?: this)?|tags?:.*|filed under\\b.*)\\s*[|]*\\s*$")

    /**
     * A phrasebook's lines ("Telugu phrasebook — Phrase list > Directions") answer a question that
     * names a language or asks how to say something; for any other question they match English
     * words only ("seed phrase" found "Phrase list", "lose my phone" found "I'm lost / Police!").
     */
    private fun isForeignPhrasebook(item: Evidence, question: String?): Boolean =
        question != null && item.title.contains(" phrasebook — ", ignoreCase = true) && !namesLanguage(question)

    private val languageAsked = Regex("(?i)\\b(?:phrasebooks?|languages?|speak|speaks|speaking|spoken|pronounce|pronounced|pronunciation|" +
        "translate|translated|translation|how (?:do|to|would) (?:i|you|we) say|words? for|phrases? for|vocabulary)\\b")

    private fun namesLanguage(question: String): Boolean {
        if (languageAsked.containsMatchIn(question) || Phrasebook.ask(question) != null) return true
        val words = tokenPattern.findAll(question).map { it.value }.toList()
        return words.indices.any { index ->
            Phrasebook.books(words[index]).isNotEmpty() ||
                (index + 1 < words.size && Phrasebook.books(words[index] + " " + words[index + 1]).isNotEmpty())
        }
    }

    /** "What is the capital of Canada?" asks for one fact about a subject. No article is
     * titled "Capital of Canada", and the answer sits in another article's lead
     * ("Ottawa is the capital city of Canada"), so the overview gate does not apply. */
    internal fun isAttributeQuestion(question: String): Boolean = Regex(
        "(?i)^\\s*(?:what|who|which)\\s+(?:is|are|was|were)\\s+the\\s+(?:capital|population|currency|" +
            "(?:official\\s+|main\\s+)?languages?|president|prime\\s+minister|leader|head\\s+of\\s+state|king|queen|" +
            "area|time\\s+zone|national\\s+anthem|largest\\s+city|highest\\s+(?:point|mountain)|longest\\s+river)" +
            "(?:\\s+city)?\\s+of\\b",
    ).containsMatchIn(question)

    /** Only explicit reference overview sections for the requested subject qualify.
     * A title alone is not enough to admit a passage: keep() also requires an anchor
     * with explanatory body text in the same section and provenance. */
    internal fun isRequestedOverview(item: Evidence, question: String?): Boolean {
        if (!item.title.endsWith(" — Overview") || isTravelListing(item)) return false
        val parent = item.title.removeSuffix(" — Overview").trim()
        val subjects = overviewSubjects(question)
        // "Georgia (country) — Overview" answers "tell me about Georgia, the country".
        return subjects.any { it.equals(parent, ignoreCase = true) } ||
            question != null && subjects.any { SubjectLookup.overviewOf(it, listOf(item), question) != null }
    }

    private fun isAdjacentOverview(item: Evidence, anchor: Evidence): Boolean {
        if (item.documentId != anchor.documentId || item.source != anchor.source ||
            item.title != anchor.title) return false
        fun index(value: Evidence): Long? {
            val prefix = value.documentId + ":"
            if (!value.chunkId.startsWith(prefix)) return null
            return value.chunkId.removePrefix(prefix).takeIf { it.matches(Regex("[0-9]{1,9}")) }?.toLongOrNull()
        }
        val current = index(item) ?: return false
        val anchored = index(anchor) ?: return false
        // Do not recursively extend the window through newly admitted passages.
        return kotlin.math.abs(current - anchored) == 1L
    }

    /** Sectioned reference articles carry an explicit parent subject. If the question
     * names one of those parents, incidental overlaps in an unrelated article must
     * not replace that subject. This lexical guard is not an entailment check and
     * deliberately does not infer subjects from planner-generated vocabulary. */
    private fun namedArticleSubjects(evidence: List<Evidence>, question: String?): List<String> {
        if (question == null) return emptyList()
        return evidence.asSequence().filter { !isTravelListing(it) && " — " in it.title }
            .map { it.title.substringBefore(" — ").trim() }
            .filter { it.length >= 4 && FtsQuery.from(it) != null && containsPhrase(question, it) }
            .distinct().toList()
    }

    private fun containsPhrase(text: String, phrase: String): Boolean {
        val pattern = tokenPattern.findAll(phrase).joinToString("\\s+") { Regex.escape(it.value) }
        return pattern.isNotEmpty() && Regex("(?i)(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])")
            .containsMatchIn(text)
    }

    /** English overview heuristic, not semantic verification. Other question forms keep
     * the ordinary relevance checks; source-specific requests must not lose study findings. */
    /** Article sections a reference names "<section> of <place>" ("History of Japan"). */
    private const val SECTION_NOUNS = "history|geography|culture|economy|politics|government|religion|cuisine|literature|music|architecture"

    internal fun overviewSubjects(raw: String?): List<String> {
        // "whats X" and "im" route like their spelled-out forms.
        val question = raw?.let(QuestionText::normalised)
        if (question == null || !PromptBuilder.allowsModelExplanation(question) || isAttributeQuestion(question)) return emptyList()
        comparisonTerms(question).takeIf { it.size == 2 }?.let { return it }
        val definition = Regex("(?i)^\\s*(?:please\\s+)?(?:what is|what are|what was|what were|explain|describe|tell me about|tell me|teach me about|" +
            "what do you know about|give me an overview of|an overview of|overview of|summari[sz]e)\\s+([\\p{L}\\p{N} '-]+?)(?=\\s+and\\s+(?:how|why|distinguish|compare|contrast|describe|explain|what|whether)\\b|[.!?]|$)")
            .find(question.replace('’', '\''))?.groupValues?.get(1)?.trim() ?: return emptyList()
        val words = definition.split(Regex("\\s+"))
        if (words.size !in 1..4 || words.first().lowercase() in setOf("how", "why", "whether", "my", "our", "this", "that", "these", "those")) return emptyList()
        // "What is a boat?" names the same topic as "Boat — Overview"; any leading article
        // otherwise prevents the section title from matching.
        // "a layer 2 rollup on Ethereum" asks about the rollup; the place or system it is on is context.
        val subject = definition.replace(Regex("(?i)^(?:the|an?)\\s+"), "")
            .replace(Regex("\\s+(?:on|in|of|for|at)\\s+\\p{Lu}[\\p{L}\\p{N}'-]*(?:\\s+\\p{Lu}[\\p{L}\\p{N}'-]*)*$"), "")
        // "Japan's history" and "Japan history" are written "History of Japan" in reference titles.
        val possessive = Regex("^([\\p{L}\\p{N} -]+?)'s?\\s+([\\p{L}\\p{N} -]+)$").find(subject)
            ?: Regex("(?i)^([\\p{L}\\p{N} -]+?)\\s+($SECTION_NOUNS)$").find(subject)
            ?: return listOf(subject)
        return listOf(subject, possessive.groupValues[2] + " of " + possessive.groupValues[1])
    }

    internal fun hasOverviewStatement(item: Evidence, subjects: List<String>): Boolean {
        fun wordPattern(word: String): String {
            val base = word.lowercase()
            val forms = mutableSetOf(base, base + "s", base + "es")
            when {
                base.endsWith("ies") -> forms += base.dropLast(3) + "y"
                // "boxes" is "box", but "signatures" is "signature": keep both singulars.
                base.endsWith("es") -> forms += listOf(base.dropLast(2), base.dropLast(1))
                base.endsWith("s") && !base.endsWith("ss") && !base.endsWith("us") && !base.endsWith("is") -> forms += base.dropLast(1)
            }
            return forms.joinToString("|", "(?:", ")", transform = Regex::escape)
        }
        // One modifier may sit inside a multiword subject: "BLS signatures" is defined as
        // "A BLS digital signature ... is a cryptographic signature scheme".
        val subject = subjects.joinToString("|") { phrase ->
            phrase.split(Regex("\\s+")).joinToString("\\s+(?:[\\p{L}\\p{N}-]+\\s+)?", transform = ::wordPattern)
        }
        // Require the concept to be the subject of an explanatory sentence, rather than
        // appearing as an experimental setting, title, index tag, or trailing keyword.
        val statement = Regex("(?i)^(?:(?:a|an|the)\\s+)?(?:$subject)(?:\\s+(?:and|or)\\s+(?:$subject))?(?:\\s*\\([^)]{1,40}\\),?|,\\s*[^,\\n]{1,80},)?\\s+(?:is|are|refers? to|means?|consists? of|involves?|produces?|generates?|preserves?|reduces?|differs?|supports?|stores?|uses?|converts?|causes?|requires?|enables?|prevents?|spans?|begins?|began|includes?|was|were)\\b")
        val passage = topicalText(item.text)
        if (passage.split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
            .any { line ->
                // A domain qualifier does not make the following definition incidental.
                // Normalize only for matching; retain the complete original evidence.
                val sentence = line.trim().replace('’', '\'')
                    .replace(Regex("(?i)^abstract:\\s*"), "")
                    .replace(Regex("(?i)^in\\s+[^,.!?\\r\\n]{1,60},\\s*"), "")
                statement.containsMatchIn(sentence)
            }) return true
        // Do not apply English sentence grammar to text we cannot confidently classify
        // as English. Keep the ordinary lexical gate for that evidence instead.
        val englishMarkers = setOf("the", "a", "an", "is", "are", "of", "in", "with", "from", "during", "we", "these", "this")
        // A paper's header (PMID, journal, authors) with no abstract says nothing about anything.
        if (passage.lineSequence().filter(String::isNotBlank).all { metadataLine.containsMatchIn(it) }) return false
        if (tokens(passage).count(englishMarkers::contains) < 2) {
            return isRelevant(item, subjects.flatMap { FtsQuery.from(it)?.terms.orEmpty() }, emptyList())
        }
        return false
    }

    private fun categoryMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !isStructuredPlace(item)) return true
        val wanted = buildSet {
            if (Regex("(?i)\\b(restaurants?|places? to eat|dining|food|eat|eating|meals?)\\b").containsMatchIn(question)) add("Eat")
            if (Regex("(?i)\\b(caf[eé]s?)\\b").containsMatchIn(question)) addAll(listOf("Eat", "Drink"))
            if (Regex("(?i)\\b(bars?|nightlife)\\b").containsMatchIn(question)) add("Drink")
            if (Regex("(?i)\\b(hotels?|hostels?|places? to stay)\\b").containsMatchIn(question)) add("Sleep")
            if (Regex("(?i)\\b(museums?|sights?|attractions?|things? to do)\\b").containsMatchIn(question)) {
                addAll(listOf("See", "Do"))
            }
            if (Regex("(?i)\\b(shopping|shops?|stores?)\\b").containsMatchIn(question)) add("Buy")
        }
        if (wanted.isEmpty()) return true
        val category = item.text.lineSequence().firstOrNull { it.startsWith("Category: ") }
            ?.removePrefix("Category: ") ?: return false
        return category in wanted
    }

    private fun dietaryMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !isEatListing(item)) return true
        val veganRequested = Regex("(?i)\\bvegan\\b").containsMatchIn(question)
        val vegetarianRequested = Regex("(?i)\\bvegetarian\\b").containsMatchIn(question)
        if (!veganRequested && !vegetarianRequested) return true
        val text = item.text
        // Vegetarian does not imply vegan. A vegan listing also satisfies a vegetarian query.
        return if (veganRequested) {
            Regex("(?i)\\bvegan\\b").containsMatchIn(text)
        } else {
            Regex("(?i)\\b(?:vegetarian|vegan)\\b").containsMatchIn(text)
        }
    }

    private fun destinationMatches(item: Evidence, question: String?): Boolean {
        if (question == null || !isTravelListing(item)) return true
        if (!Regex("(?i)\\b(in|near|around)\\s+\\p{L}").containsMatchIn(question)) return true
        // An OpenStreetMap place with no resolvable destination cannot answer "in <city>".
        val noDestination = !item.documentId.startsWith("osm-place-")
        val firstLine = item.text.lineSequence().firstOrNull() ?: return noDestination
        if (!firstLine.startsWith("Destination: ")) return noDestination
        val destination = firstLine.removePrefix("Destination: ").trim().takeIf(String::isNotEmpty)
            ?: return true
        // A district guide (e.g. London/Camden) is eligible for a city query; a
        // different city mentioned only in an address or directions is not.
        val names = listOf(destination, destination.substringBefore('/')).distinct()
        return names.any { name ->
            Regex("(?i)\\b(?:in|near|around)\\s+${Regex.escape(name)}\\b").containsMatchIn(question)
        }
    }

    /**
     * A map listing answers a question about places, or one that names its town. A question about
     * EIP-7702 and regular accounts matched a museum whose phone number ends in 7702 and a shop
     * selling "the regular items"; neither question nor town asked for them.
     */
    private fun placeAsked(item: Evidence, question: String?): Boolean {
        if (question == null || !isStructuredPlace(item)) return true
        val destination = item.text.lineSequence().firstOrNull()?.takeIf { it.startsWith("Destination: ") }
            ?.removePrefix("Destination: ")?.trim()?.takeIf(String::isNotEmpty)
        if (destination != null && listOf(destination, destination.substringBefore('/'), destination.substringBefore(" (")).distinct()
            .any { name -> containsPhrase(question, name) }) return true
        // A venue named in the question answers a lookup or a question about its hours ("Is the
        // Louvre open on Mondays?"), not one about who or what it is named after: "who was Ibn
        // Battuta?" packed the Dubai metro station, "Taj Mahal" a restaurant in Graz.
        val place = item.text.lineSequence().firstOrNull { it.startsWith("Place: ") }?.removePrefix("Place: ")?.trim()
        if (place != null && place.length >= 4 && containsPhrase(question, place)) {
            return VenueLookup.isPlaceLookup(question) || hoursAsked.containsMatchIn(question)
        }
        return placeRequest.containsMatchIn(question)
    }

    private val hoursAsked = Regex("(?i)\\b(open|opens|opening|closed|closes|closing|hours|admission|entry|tickets?|fee|fees)\\b")

    private val placeRequest = Regex(
        "(?i)\\b(restaurants?|caf[eé]s?|eat|eating|food|meals?|dining|bars?|pubs?|drinks?|drinking|nightlife|hotels?|hostels?|" +
            "stay|sleep|accommodation|museums?|galler(?:y|ies)|sights?|attractions?|things? to do|visit|see|shops?|shopping|" +
            "buy|markets?|stores?|places?|where|nearby|near me|around here|pharmac(?:y|ies)|hospitals?|clinics?|atms?|banks?|" +
            "open|opening|hours|entry|admission|tickets?)\\b",
    )

    private fun isTravelListing(item: Evidence) = item.documentId.startsWith("wv-eat-") || isStructuredPlace(item)

    /** Wikivoyage and OpenStreetMap places share the Destination/Category/Place layout. */
    private fun isStructuredPlace(item: Evidence) =
        item.documentId.startsWith("wv-place-") || item.documentId.startsWith("osm-place-")

    private fun isEatListing(item: Evidence) = item.documentId.startsWith("wv-eat-") ||
        (isStructuredPlace(item) && item.text.lineSequence().any { it == "Category: Eat" })

    private fun isRelevant(item: Evidence, questionTerms: List<String>, expandedTerms: List<String>): Boolean {
        // FTS prefix matches can lack an exact-term attribution. They still need the same
        // relevance check; otherwise unrelated hits bypass the gate entirely.
        val passage = topicalText(item.text)
        if (passage.isBlank()) return false
        val hasIndexTags = passage != item.text.trim()
        val bodyTokens = tokens(if (hasIndexTags) passage else item.title + " " + passage)
        // Research vocabulary ("study", "outcomes", "different") occurs in almost every paper,
        // so sharing it is no sign that a passage is about the question's subject.
        val contentTerms = questionTerms.filterNot { it in lookupWrappers || it in contractionFragments }
            .let { terms -> terms.filterNot(genericTerms::contains).ifEmpty { terms } }
        val questionHits = contentTerms.count { term -> bodyTokens.matches(term) }
        val expandedHits = expandedTerms
            .filterNot(contentTerms::contains)
            .count { term -> bodyTokens.matches(term) }
        // Two incidental overlaps are common in broad scientific corpora. A question with
        // four or more distinct content terms needs most of them in the same passage before
        // it can displace an honest model-only answer. Single-topic lookups (including
        // "Indonesian") still need only their one requested term.
        val requiredQuestionHits = when (contentTerms.size) {
            0, 1 -> 1
            2, 3 -> 2
            else -> 3
        }
        return questionHits >= requiredQuestionHits ||
            (contentTerms.size <= 3 && questionHits >= 1 && expandedHits >= 1) ||
            // A high semantic score can bridge vocabulary mismatch, but the threshold is
            // intentionally stricter than pack import's broad rejection floor.
            isStrongConceptMatch(item) ||
            isSupportedConceptMatch(item, contentTerms)
    }

    /**
     * A map listing answers one part of "a vegan food stop and a museum", so one of the question's
     * subject words in what the listing SAYS is enough: its name, kind, cuisine, description,
     * address or diet. Never the template's labels ("Hours in source" is not a 24-hour pharmacy),
     * and never its own town, which every listing of the town shares: counting both let a hostel
     * named Sukhumvit and a KFC answer for a pharmacy in Bangkok. Destination, category and diet
     * are still checked separately.
     */
    private fun isPlaceRelevant(item: Evidence, questionTerms: List<String>): Boolean {
        val destination = listingValue(item.text, "Destination")?.let(::tokens).orEmpty()
        val terms = questionTerms.filterNot {
            it in lookupWrappers || it in genericTerms || it in placeInstructionTerms || it in contractionFragments || destination.matches(it)
        }
        if (terms.isEmpty()) return true
        val said = tokens(listingFields.mapNotNull { field -> listingValue(item.text, field) }.joinToString(" "))
        return terms.any { said.matches(it) }
    }

    /** The lines of a listing that describe the place itself; "Vegan" is its diet claim, "Brand" its chain. */
    private val listingFields = listOf("Place", "Type", "Cuisine", "Description", "Address", "Vegan", "Brand")

    private fun listingValue(text: String, field: String): String? =
        text.lineSequence().firstOrNull { it.startsWith("$field: ") }?.removePrefix("$field: ")?.trim()?.takeIf(String::isNotEmpty)

    private val placeInstructionTerms = setOf("listing", "listings", "stop", "stops", "visit", "visits", "trip", "suggest")

    /**
     * Words a place search within one destination ignores: how the lookup is phrased ("best",
     * "suggest") and where ("near", "around") say nothing about the kind of place asked for, yet
     * match place names ("Kaya's Best Döner") once the city no longer separates its listings.
     */
    internal val placeSearchFiller: Set<String> = lookupWrappers + placeInstructionTerms + setOf("near", "nearby", "around", "close")

    /** What is left of "we're", "I'll", "you've" once the apostrophe splits them; not words. */
    private val contractionFragments = setOf("re", "ll", "ve", "s", "t", "d", "m")

    /**
     * A good semantic match that also shares one of the question's own content words. In a
     * technical pack, true matches often score 0.70-0.76 with this encoder (a question about
     * quantum attacks on RSA finds "RSA cryptosystem — Integer factorization" in that range),
     * below the 0.78 a match needs to stand on similarity alone; the shared word keeps a
     * high-scoring but unrelated passage out.
     */
    private fun isSupportedConceptMatch(item: Evidence, terms: List<String>): Boolean {
        val score = item.matchedBy?.let { conceptPattern.matchEntire(it) }?.groupValues?.get(1)?.toDoubleOrNull() ?: return false
        if (!score.isFinite() || score < SUPPORTED_CONCEPT) return false
        val content = terms.filterNot { it in lookupWrappers || it in genericTerms }
        val body = tokens(item.title + " " + topicalText(item.text))
        return content.any { body.matches(it) }
    }

    private fun isStrongConceptMatch(item: Evidence): Boolean = item.matchedBy
        ?.let { conceptPattern.matchEntire(it) }?.groupValues?.get(1)?.toDoubleOrNull()
        ?.let { it.isFinite() && it in HIGH_CONFIDENCE_CONCEPT..1.0 } == true

    private fun tokens(value: String): Set<String> = tokenPattern.findAll(value.lowercase())
        .map { it.value }
        .toSet()

    /** Keep excerpt ranking consistent with the word variants accepted by retrieval. */
    internal fun termHits(text: String, terms: List<String>): Int {
        val words = tokens(text)
        return terms.count { words.matches(it) }
    }

    private fun Set<String>.matches(term: String): Boolean {
        val lower = term.lowercase()
        if (lower in contractionFragments) return false
        // As in FtsQuery: a short word or a number is not a stem. "mev" is not "mevalonate".
        if (lower.codePointCount(0, lower.length) <= 3) return FtsQuery.shortForms(lower).any(::contains)
        if (lower.all(Char::isDigit)) return lower in this
        val stem = stem(lower)
        // "ears" stems to "ear", the start of "earth" and "earthquake": a stem this short matches
        // its own forms only, as FtsQuery searches it.
        if (stem.codePointCount(0, stem.length) <= 3) {
            // "tides" loses its "es" to "tid", which names none of its forms; the word less its "s"
            // ("tide") is its singular unless the letter before the "es" is one that takes "es"
            // itself ("boxes", "buses" and "dishes" are "box", "bus" and "dish").
            val singularBeforeEs = lower.takeIf { it.length > 4 && it.endsWith("es") && !takesEs(it.dropLast(2)) }?.dropLast(1)
            return lower in this || FtsQuery.shortForms(stem).any(::contains) ||
                (singularBeforeEs != null && any { token -> token.startsWith(singularBeforeEs) })
        }
        // The stem of "embassies" ("embassi") is not a prefix of "embassy", so a plural
        // question would otherwise reject every listing that names the place in the singular.
        val singular = if (lower.length > 4 && lower.endsWith("ies")) lower.dropLast(3) + "y" else null
        return any { token -> token.startsWith(stem) || (singular != null && token.startsWith(singular)) }
    }

    /** Whether a word's plural is spelled with "es": "box", "bus", "quiz", "dish", "church". */
    private fun takesEs(base: String): Boolean =
        base.endsWith("s") || base.endsWith("x") || base.endsWith("z") || base.endsWith("ch") || base.endsWith("sh")

    private fun stem(term: String): String = when {
        term.length > 4 && term.endsWith("ies") -> term.dropLast(2)
        term.length > 4 && term.endsWith("es") -> term.dropLast(2)
        term.length > 3 && term.endsWith("s") -> term.dropLast(1)
        term.length > 4 && term.endsWith("ing") -> term.dropLast(3)
        term.length > 4 && term.endsWith("ed") -> term.dropLast(2)
        else -> term
    }

    private const val HIGH_CONFIDENCE_CONCEPT = 0.78
    private const val SUPPORTED_CONCEPT = 0.70
}
