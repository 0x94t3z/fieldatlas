package xyz.fieldatlas.research

/** A generic parts page does not establish specifications for a particular model. */
internal object DeviceEvidenceGap {
    private val partRequest = Regex("(?i)\\b(?:replacement|compatible|compatibility|filter|cartridge|connector|charger|spare|specifications?)\\b")
    private val code = Regex("(?i)(?<![\\p{L}\\p{N}])([a-z]{1,8})[-_](\\d{1,8}[a-z0-9-]*)(?![\\p{L}\\p{N}])")
    private val standards = setOf("eip", "erc", "rfc", "iso", "iec", "fips", "nist")
    fun response(question: String, sources: List<Evidence>): String? {
        if (!partRequest.containsMatchIn(question)) return null
        val models = code.findAll(question).filter { it.groupValues[1].lowercase() !in standards }.toList()
        if (models.isEmpty()) return null
        val corpus = sources.joinToString("\n") { it.title + "\n" + it.text }
        val missing = models.firstOrNull { model ->
            !Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(model.groupValues[1]) +
                "[-_ ]?" + Regex.escape(model.groupValues[2]) + "(?![\\p{L}\\p{N}])").containsMatchIn(corpus)
        } ?: return null
        return "I couldn't find a saved source identifying ${missing.value}, so I can't verify its parts or specifications. Add its manual or a photo of the model label."
    }
}
