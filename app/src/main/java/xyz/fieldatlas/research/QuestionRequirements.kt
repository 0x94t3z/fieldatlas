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
    private const val privatePerson = "(?:(?:an?|the)\\s+)?(?:unnamed|unidentified|anonymous|private)\\s+(?:private\\s+)?(?:person|patient|individual|user|customer)"
    private const val privateValue = "(?:diagnosis|diagnoses|test results?|account balance|bank balance|account number|private messages?)"
    private val personalRecordLookup = Regex(
        "(?i)^\\s*(?:what|which)\\s+(?:$privateValue\\s+(?:did|does|has|had)\\s+" +
            "$privatePerson\\s+(?:receive[ds]?|have|had|get|got|hold|held|been given)|" +
            "(?:is|was|are|were)\\s+(?:$privatePerson['’]s\\s+$privateValue|(?:the\\s+)?$privateValue\\s+of\\s+$privatePerson))" +
            "(?:\\s+(?:yesterday|today|last week|this morning|right now|currently))?\\s*[?!.]*\\s*$",
    )
    private val recentNews = Regex(
        "(?i)\\b(?:published|released|announced|news|happened)\\b.{0,40}\\b(?:today|this week|this morning|yesterday|just now)\\b|" +
            "\\b(?:today|this week|this morning|yesterday)\\b.{0,40}\\b(?:published|released|announced|news|happened)\\b|" +
            "\\blatest (?:news|study|studies|research|paper)\\b",
    )

    const val RECENT_NEWS = "Field Atlas answers from saved collections, which are dated snapshots, so it can't " +
        "know what was published or announced today. Ask about the topic itself and it answers from what is saved."

    fun response(question: String, hasAttachments: Boolean): String? {
        if (hasAttachments) return null // Supplied material can be read, with its original date/context.
        val request = quoted.replace(question, " ")
        if (attachmentReference.containsMatchIn(request)) {
            return "I don't have an attached file for this question. Add the report, document, or image you want me to read, then try again."
        }
        // No identity or supplied record can establish an explicitly unknown person's
        // private result. A deterministic input-gap reply avoids unrelated citations or
        // model guesses. General explanations and supplied documents keep their routes.
        if (personalRecordLookup.containsMatchIn(QuestionSubject.text(request))) {
            return "I don't have that person's private records. Add a document you have permission to use if you want help reading it."
        }
        if (recentNews.containsMatchIn(request)) return RECENT_NEWS
        if (liveTime.containsMatchIn(request) && liveLocation.containsMatchIn(request) &&
            liveStatus.containsMatchIn(request)) {
            return "I can't verify live conditions at your location from saved knowledge. Posted notices, staff or people nearby will know. You can attach a photo of a current notice or report for me to read."
        }
        return null
    }
}
