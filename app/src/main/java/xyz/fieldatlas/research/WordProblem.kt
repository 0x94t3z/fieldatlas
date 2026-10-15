package xyz.fieldatlas.research

/**
 * A word problem that carries every number its answer needs ("€180 a night for 4 nights, 15% off,
 * then 12% tax"). When no calculator answers it whole, research on its words finds unrelated
 * passages (a parador's 20% discount, a film about a train) and the model imports their numbers.
 * Such a question is answered from its own figures instead, with no evidence at all.
 */
object WordProblem {
    /** The numbers a question states, in the order written; a clock time ("8:15") is one number. */
    data class Problem(val numbers: List<String>, val selfContained: Boolean)

    private const val MIN_NUMBERS = 3
    /**
     * With this many of its own figures and an arithmetic ask, a question that names places is still
     * worked from its figures: "leaves Tokyo at 17:05 … lands in San Francisco at 10:20 … UTC+9 …
     * UTC-7" gives everything, and the travel guide's passages only mislead the sum.
     */
    private const val FIGURES_OVER_PLACES = 4
    // A clock time, then a plain number with thousands separators and decimals ("two 10-minute stops" has one).
    private val number = Regex("(?<![\\d.,:])(?:\\d{1,2}:\\d{2}|\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)(?![\\d:]|[.,]\\d)")
    private val numberWord = Regex("(?i)\\b(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|half|quarter)\\b")
    // Anything beyond the arithmetic ("is it dangerous", "which is better") is left to research.
    private val judgement = Regex(
        "(?i)\\b(dangerous|danger|safe|safely|risky|risk|ok(?:ay)?|should|recommend\\w*|better|worse|best|worst|why|explain|compare|" +
            "healthy|enough|too (?:hot|cold|much|far|long)|worth|legal|allowed|advise|advice|suggest|prefer)\\b",
    )
    // "how long should it take", "how long should the walk take": asks for the sum, not a judgement.
    private val shouldTake = Regex("(?i)\\bhow long should\\b(?:\\s+[\\p{L}']+){0,3}\\s+take\\b")
    // Three numbers alone do not make a calculation: model numbers, dates and versions
    // can occur in a question that needs knowledge not supplied by the person.
    private val arithmeticAsk = Regex("(?i)\\b(?:how (?:many|much|long|far|heavy|fast)|what time|when (?:do|does|will|would|can)|" +
        "(?:what(?:'s| is)|find|calculate|work out) (?:the )?(?:total|sum|product|difference|cost|price|amount)|calculate|work out|" +
        // "Which option is cheaper and by how much?", "what's my daily budget?", "the gradient as a percentage".
        "which (?:\\w+ )?(?:is|are|works? out|comes? out) (?:cheaper|faster|quicker|shorter|longer|more expensive)|by how much|" +
        "what(?:'s| is| are) (?:my|our|the|each) (?:\\w+ ){0,2}(?:budget|gradient|slope|speed|average|percentage|rate|share|split|cost|price|total)|" +
        "as a percentage|in degrees)\\b|[×÷=]")
    // A sentence opens with a capital whatever it is about ("Our hotel", "Using the rule"); GuideScope
    // reads such a word as a name unless it is a question word, so it is lowered first. A name a
    // colon marks ("Hanoi: how do I...") keeps its capital.
    private val sentenceOpener = Regex("(?:^|[.!?]\\s+)(\\p{Lu})(?![\\p{L}'’-]*:)")
    // "Naismith's rule (1 hour per 5 km ...)": a named method whose figures the question gives is not
    // a place or a subject to look up.
    private val namedRule = Regex("\\b\\p{Lu}[\\p{L}'’-]*(?:['’]s)?\\s+(?:rule|formula|law|method)\\b")

    fun of(question: String): Problem {
        val numbers = number.findAll(question).map { it.value }.toList()
        val counted = numbers.size + numberWord.findAll(question).count()
        val plain = namedRule.replace(question, "rule")
        val lowered = sentenceOpener.replace(plain) { m -> m.value.dropLast(1) + m.groupValues[1].lowercase() }
        val selfContained = counted >= MIN_NUMBERS &&
            arithmeticAsk.containsMatchIn(question) &&
            !PromptBuilder.requiresSavedSources(question) &&
            (counted >= FIGURES_OVER_PLACES ||
                !GuideScope.namesPlace(lowered) && TravelSections.lookup(plain)?.places.isNullOrEmpty() && !SubjectLookup.leads(plain)) &&
            !judgement.containsMatchIn(shouldTake.replace(question, "take"))
        return Problem(numbers, selfContained)
    }

    fun isSelfContained(question: String): Boolean = of(question).selfContained
}
