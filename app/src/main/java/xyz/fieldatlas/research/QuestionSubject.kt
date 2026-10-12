package xyz.fieldatlas.research

/** Source-scope instructions are not an encyclopedia subject. Preserve the original
 * question for answer policy, consent and display; normalize only discovery input. */
internal object QuestionSubject {
    private val sourceScope = Regex(
        "(?i)^\\s*(?:according\\s+to|based\\s+on|using(?:\\s+only)?)\\s+" +
            "(?:(?:the|my|our|these)\\s+)?(?:(?:saved|local|provided|attached)\\s+)?" +
            "(?:sources?|documents?|files?|notes?|travel\\s+guides?|guides?|papers?|reports?)" +
            "(?:\\s*[,;:]\\s*|\\s+(?=(?:what|which|who|where|why|how|when|explain|describe|" +
            "compare|contrast|define|list|find|tell|summari[sz]e)\\b))",
    )

    // Only a complete reporting directive is removed. In particular, "sources of
    // information" and "source control" are subjects, not answer instructions.
    private val reportedTopic = Regex(
        "(?i)^\\s*(?:summari[sz]e\\s+what|what\\s+(?:do|does))\\s+" +
            "(?:(?:the|my|our|these)\\s+)?(?:(?:saved|local|provided|attached)\\s+)?" +
            "(?:sources?|documents?|files?|notes?|guides?|papers?|reports?)\\s+" +
            "(?:say|says)\\s+about\\s+(?=[\\p{L}\\p{N}])",
    )

    fun text(question: String): String = question.replaceFirst(sourceScope, "").replaceFirst(reportedTopic, "")

    /** A recognized discovery directive also binds the answer to the supplied sources. */
    fun hasSourceDirective(question: String): Boolean =
        sourceScope.containsMatchIn(question) || reportedTopic.containsMatchIn(question)

    /** A full multiword title, not a bag of words or words on opposite sides of a sentence.
     * Spaces and hyphens are interchangeable in discovery ("sealing-membrane"). */
    fun containsMultiwordTitle(question: String, title: String): Boolean {
        val words = Regex("[\\p{L}\\p{N}]+").findAll(title).map { it.value }.toList()
        if (words.size !in 2..8) return false
        val name = words.joinToString("[\\s\\p{Pd}]+") { Regex.escape(it) }
        return Regex("(?i)(?<![\\p{L}\\p{N}])$name(?![\\p{L}\\p{N}])").containsMatchIn(text(question))
    }

    private val namedUse = Regex("(?i)^\\s*which\\s+([\\p{L}-]+(?:\\s+[\\p{L}-]+){0,2})\\s+does\\s+(?:the\\s+)?" +
        "([\\p{L}\\p{N}_-]+(?:\\s+[\\p{L}\\p{N}_-]+){0,2})\\s+use\\b")

    /** A narrow attribute lookup, not a definition of its generic attribute.
     * A named owner is required; ordinary comparisons and explanations stay unchanged. */
    fun relationTerms(question: String): List<String> {
        val match = namedUse.find(text(question)) ?: return emptyList()
        if (relationOwner(question) == null) return emptyList()
        return FtsQuery.from(match.groupValues[1] + " " + match.groupValues[2])?.terms.orEmpty()
            .takeIf { it.size in 2..6 }.orEmpty()
    }

    fun relationOwner(question: String): String? = namedUse.find(text(question))?.groupValues?.get(2)
        ?.takeIf { name -> name.split(' ').all { it.length >= 3 && it.first().isUpperCase() } }
}
