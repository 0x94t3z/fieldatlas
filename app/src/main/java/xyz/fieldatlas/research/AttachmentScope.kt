package xyz.fieldatlas.research

/** Whether a question with files attached also uses the library: only when the question asks for it ("use my
 * library"). Read from the user's question only, never from document contents. */
object AttachmentScope {
    private val quoted = Regex("\"[^\"]*\"|“[^”]*”|‘[^’]*’|`[^`]*`|(?<![\\p{L}\\p{N}])'[^'\\n]*'(?![\\p{L}\\p{N}])")
    private const val LIBRARY = "(?:my\\s+|the\\s+|our\\s+)?(?:library|knowledge\\s+collections|saved\\s+sources)\\b"
    private const val REQUEST_START = "(?:^|[.!?;\\n])\\s*(?:(?:can|could|would|will)\\s+you\\s+)?(?:please\\s+)?(?:also\\s+)?"
    private val directRequest = Regex("(?i)$REQUEST_START(?:use|include|consult|search|check)\\s+(?:also\\s+)?$LIBRARY")
    private val comparison = Regex("(?i)$REQUEST_START(?:compare|combine|cross-check)\\b[^.!?;\\n]{0,160}\\b(?:with|against)\\s+$LIBRARY")
    private val libraryFirstComparison = Regex("(?i)$REQUEST_START(?:compare|combine|cross-check)\\s+$LIBRARY\\s+(?:with|against)\\b")
    private val exclusion = Regex("(?i)\\b(?:do\\s+not|don['’]t|never|not|without)\\s+(?:(?:use|using|include|including|search|searching|consult|consulting)\\s+)?$LIBRARY|\\bexclude\\s+$LIBRARY|\\bonly\\s+(?:(?:use|read)\\s+)?(?:(?:my|the|these|attached)\\s+)*(?:files?|attachments?|notes?|documents?|pdfs?)\\b")

    /** [byDefault] includes the library unless the question says files only (the desktop harness uses it; the app
     * leaves it off); "use my library" in the question includes it either way. */
    fun includesLibrary(question: String, byDefault: Boolean = false): Boolean {
        val instruction = quoted.replace(question, " ")
        // Ambiguous/contradictory instructions stay files-only. Do not guess permission.
        if (exclusion.containsMatchIn(instruction)) return false
        return directRequest.containsMatchIn(instruction) || comparison.containsMatchIn(instruction) ||
            libraryFirstComparison.containsMatchIn(instruction) || byDefault
    }
}
