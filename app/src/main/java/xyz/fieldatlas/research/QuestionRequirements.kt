package xyz.fieldatlas.research

/** Missing inputs are not search terms. These routes never invent a personal/live answer. */
object QuestionRequirements {
    private val quoted = Regex("\"[^\"]*\"|“[^”]*”|`[^`]*`")
    private val attachmentReference = Regex(
        "(?i)\\b(?:according to|based on|summari[sz]e|read|review|explain|analy[sz]e|in|from)\\s+(?:(?:my|the|this|these|our)\\s+)?(?:attached|uploaded)\\s+(?:reports?|files?|documents?|photos?|images?|pdfs?|notes?)\\b",
    )
    private val liveTime = Regex("(?i)\\b(?:right now|currently|today|live)\\b")
    private val liveLocation = Regex("(?i)\\b(?:near me|my location|where I am)\\b")
    private val liveStatus = Regex("(?i)\\b(?:closed|closures|open|available|availability|traffic|weather)\\b")

    fun response(question: String, hasAttachments: Boolean): String? {
        if (hasAttachments) return null // Supplied material can be read, with its original date/context.
        val request = quoted.replace(question, " ")
        if (attachmentReference.containsMatchIn(request)) {
            return "I don't have an attached file for this question. Add the report, document, or image you want me to read, then try again."
        }
        if (liveTime.containsMatchIn(request) && liveLocation.containsMatchIn(request) &&
            liveStatus.containsMatchIn(request)) {
            return "I can't verify live conditions at your location from saved knowledge. Check an up-to-date local source. You can attach a current notice or report for me to explain."
        }
        return null
    }
}
