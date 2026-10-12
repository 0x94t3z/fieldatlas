package xyz.fieldatlas.research

import xyz.fieldatlas.text.MarkdownLiteral

/** Narrow rule lookups show original wording instead of inventing a return value.
 * Lexical identity is a discovery constraint, not entailment or source-truth proof.
 * Conflicting/qualified passages are displayed as written, never resolved by polarity. */
internal object PreciseReferenceAnswer {
    fun answer(question: String, packed: PackedPrompt): String? {
        val focus = TechnicalRuleFocus.from(question) ?: return null
        val rules = packed.sources.filter { source ->
            focus.matches(source.evidence.title, source.excerpt) &&
                source.excerpt == EvidenceRelevance.passageText(source.evidence.text)
        }
        if (rules.isEmpty()) return "The retrieved passages do not establish the return value for that condition. " +
            "This does not mean the full specification has no rule."
        return "## Matching passages from your saved sources\n\n" + rules.joinToString("\n\n", transform = ::quote) +
            "\n\n_Original wording, not a generated conclusion. Check the conditions and source versions; selected passages may not cover every exception._"
    }

    fun conditionalAnswer(question: String, packed: PackedPrompt, subjects: List<Evidence>): String? {
        val focus = ConditionalReferenceFocus.from(question, subjects) ?: return null
        val details = packed.sources.filter { focus.matches(it.evidence, it.excerpt) }
        if (details.isEmpty()) return "The retrieved passages do not establish why both conditions can hold. " +
            "I can't give a source-backed explanation from these excerpts."
        return "## Selected passages from your saved sources\n\n" + details.joinToString("\n\n", transform = ::quote) +
            "\n\n_Original excerpts, not a generated causal explanation. Their qualifications still apply; these selections may not fully resolve the question._"
    }

    internal fun quote(source: PromptSource): String {
        val citation = source.citationId.takeIf { Regex("S[1-9][0-9]*").matches(it) }
        val body = copiedExcerpt(source)
        if (citation == null || body == null) {
            // Keep a source's place visible, including a potentially conflicting
            // record, without calling altered text an original quotation.
            return "_This selected excerpt couldn't be displayed unchanged; open its source to read the original._" +
                (citation?.let { " [$it]" } ?: "")
        }
        val laterChunk = source.evidence.chunkId.substringAfterLast(':').toIntOrNull()?.let { it > 0 } == true
        val prefix = if (laterChunk && !body.startsWith("…")) "… " else ""
        val suffix = if (!Regex("[.!?。！？…][\\\"”’)]*$").containsMatchIn(body)) " …" else ""
        // Keep the literal as one physical Markdown line. Ordinary prose quotes join
        // their lines with spaces; doing that to source code can turn the next statement
        // into part of a line comment. References decode line breaks as plain text only.
        return "> ${MarkdownLiteral.encode(prefix + body + suffix)} [$citation]"
    }

    /** Only a contiguous original, with optional outer packing ellipses, is a quote.
     * Do not normalize internal whitespace: flattening a line comment can change code. */
    internal fun copiedExcerpt(source: PromptSource): String? {
        val originals = listOf(source.evidence.text, EvidenceRelevance.passageText(source.evidence.text))
        val excerpt = source.excerpt.trim()
        if (excerpt.isEmpty()) return null
        if (originals.any { it.contains(excerpt) }) return excerpt
        val selected = excerpt.removePrefix("… ").removeSuffix(" …").trim()
        return excerpt.takeIf { selected.isNotEmpty() && originals.any { it.contains(selected) } }
    }
}
