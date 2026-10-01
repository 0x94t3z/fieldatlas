package xyz.fieldatlas.research

/** A narrow warning for explicit unsourced-answer labels, not a claim to detect all AI text. */
object AttachmentProvenance {
    fun hasUnverifiedAnswerLabel(text: String): Boolean {
        val normalized = text.lowercase().replace(Regex("\\s+"), " ")
        return listOf("no linked local source", "model-generated", "not verified against saved sources",
            "model explanation—not verified", "model explanation - not verified").any { it in normalized }
    }
    fun needsWarning(evidence: Evidence) = evidence.documentId.startsWith("attachment:") && hasUnverifiedAnswerLabel(evidence.text)
}
