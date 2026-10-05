package xyz.fieldatlas.research

/**
 * A question that names one Ethereum proposal ("How does EIP-7702 change my account?") is answered
 * best by that proposal's own summary. Keyword and concept search can return its Specification
 * or another proposal that mentions it, so the summary is searched for directly and quoted.
 */
object ProposalLookup {
    private val proposal = Regex("(?i)\\b(EIP|ERC)[- ]?(\\d{1,5})\\b")
    private val summary = Regex(" — (?:Abstract|Simple Summary)$")

    /** "EIP-7702", when the question names exactly one proposal; two are a comparison. */
    fun named(question: String): String? = proposal.findAll(question)
        .map { "${it.groupValues[1].uppercase()}-${it.groupValues[2].toInt()}" }
        .toSet().singleOrNull()

    /** The sections a proposal summarises itself in: "Abstract", or "Simple Summary" in older ones. */
    val SUMMARY_SECTIONS = listOf("abstract", "simple summary")

    /** The named proposal's own summary section, as the pack titles it ("EIP-7702: … — Abstract"). */
    fun isSummaryOf(item: Evidence, label: String): Boolean =
        labels(label).any { item.title.startsWith("$it:") } && summary.containsMatchIn(item.title)

    /** People write "EIP-20" for what the ERC repository now titles "ERC-20", and the reverse. */
    fun labels(label: String): List<String> = listOf(label, label.replaceFirst("EIP-", "TMP-").replaceFirst("ERC-", "EIP-").replaceFirst("TMP-", "ERC-"))

    /** Its opening paragraph, verbatim, after the name and status lines the pack puts first. */
    fun lead(question: String, sources: List<PromptSource>): SourceLead.Lead? {
        val label = named(question) ?: return null
        // A Simple Summary is the one-line plain version; an Abstract can open with a byte format.
        val source = sources.filter { isSummaryOf(it.evidence, label) }
            .sortedBy { if (it.evidence.title.endsWith(" — Simple Summary")) 0 else 1 }
            .firstOrNull() ?: return null
        val paragraphs = source.evidence.text.split(Regex("\\n\\s*\\n"))
        // The first section starts with "EIP-n: title / Status … / description"; the summary follows it.
        val body = if (paragraphs.first().startsWith("$label:")) paragraphs.drop(1) else paragraphs
        val text = SourceLead.leadText(body.joinToString("\n\n")) ?: return null
        return SourceLead.Lead(source.citationId, text)
    }
}
