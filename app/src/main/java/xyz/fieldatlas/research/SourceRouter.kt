package xyz.fieldatlas.research

import java.text.Normalizer

/**
 * How much of the saved sources a model that answers from memory should read for a question: nothing for a subject
 * the whole world knows, a few passages for one it does not. A small model's memory of a famous subject beat reading
 * passages about it on every set measured; its memory of a little-known one is where it invents. The line is drawn
 * by the collections themselves: the Simple English encyclopedia holds about a quarter of a million subjects, the
 * ones most readers meet, and famous subjects have long articles in the full edition; a subject the Simple edition
 * lacks whose full article is short, or one held only by a specialist collection (biology, cryptography) or by a
 * collection the user imported, is read.
 */
object SourceRouter {
    /** Passages read for a little-known subject: its lead and the two best sections, as notes with numbers. */
    const val READ_NOTES = 3
    /** A full-edition article with no more rows than this is about something few readers meet (used when mentions are unknown). */
    const val SHORT_ARTICLE_ROWS = 24
    /** A subject named in no more saved passages than this is one few readers meet; counts are capped at [MENTION_CAP]. */
    const val RARE_MENTIONS = 400
    const val MENTION_CAP = 1000
    /** The edition that holds what most readers know. */
    private val mainstream = listOf("swp-")
    /** The full encyclopedia: seven million subjects, most of them little known. */
    private val fullEdition = listOf("enw-", "wp-")
    private val specialist = listOf("hagr-", "pmc-", "openstax-", "medlineplus-", "erc-", "eip-", "spec-", "nist-")
    /** The rest of the collections Field Atlas offers: travel guides and listings, and map places. */
    private val travelAndPlaces = listOf("wvs-", "wv-", "osm-")
    /** Passages the app makes itself: an attached file's, a first-aid guide's, an emergency number's. */
    private val builtIn = listOf("attachment:", "emergency:")

    private fun from(prefixes: List<String>, id: String) = prefixes.any { id.startsWith(it) }

    /**
     * A passage from a collection the user imported: every collection Field Atlas offers names its passages with one of
     * the prefixes above, so any other id came from a pack someone built for themselves, such as a handbook or their
     * own papers. It is read like a specialist collection: whoever imported it wants the answer to come from it.
     */
    private fun imported(id: String) = !from(mainstream + fullEdition + specialist + travelAndPlaces + builtIn, id)

    private fun specialistOrImported(id: String, readImported: Boolean) = from(specialist, id) || readImported && imported(id)
    private val word = Regex("[\\p{L}\\p{N}]+")
    private fun words(text: String) = word.findAll(Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase())
        .map { it.value }.toSet()

    private fun bareTitle(item: Evidence) = item.title.substringBefore(" — ").replace(Regex("\\s*\\(.*\\)$"), "")

    /** A subject the question names in full: "Goods" found for "why are honeybees dying" is a lookup gone wrong, not a subject. */
    private fun named(subject: Evidence, question: String): Boolean {
        val title = words(bareTitle(subject))
        if (title.isEmpty()) return false
        val asked = words(question)
        return title.all { w -> w in asked || w.length > 3 && asked.any { it.startsWith(w) || w.startsWith(it) && it.length > 3 } }
    }

    /**
     * Few readers meet this subject: a specialist collection holds it, or the full edition does with no Simple English
     * article and few passages naming it ([mentions], counted up to [MENTION_CAP]; a short article when the count is
     * unknown). "Speed" and "Sky blue" are named in thousands of passages; "Pantelegraph" in a handful.
     */
    private fun rare(item: Evidence, hasSimple: Boolean, articleRows: Map<String, Int>, mentions: Map<String, Int>,
        readImported: Boolean): Boolean {
        if (specialistOrImported(item.documentId, readImported)) return true
        if (hasSimple || !from(fullEdition, item.documentId)) return false
        val title = bareTitle(item)
        val count = mentions[title] ?: mentions.entries.firstOrNull { it.key.equals(title, ignoreCase = true) }?.value
        // Unknown readership is taken as wide: only when no count could be had at all does a short article stand in.
        return if (count != null) count <= RARE_MENTIONS else mentions.isEmpty() && (articleRows[title] ?: Int.MAX_VALUE) <= SHORT_ARTICLE_ROWS
    }

    /**
     * The number of passages to read as notes: 0 when the subject is one the model knows, [READ_NOTES] when the
     * question's subjects were found, none in the mainstream edition, and one is held by a specialist collection or
     * its full-edition article is short ([articleRows], rows of that article in the full edition); or when no
     * subject was found and the passages that lead the prompt come from a specialist collection, or, with
     * [readImported], one the user imported. Travel guides and an empty prompt keep the model to its memory.
     */
    fun notes(question: String, subjects: List<Evidence>, sources: List<PromptSource>, articleRows: Map<String, Int> = emptyMap(),
        mentions: Map<String, Int> = emptyMap(), readImported: Boolean = false): Int {
        if (sources.isEmpty()) return 0
        // A rare subject the question names is read even beside a common one it also names: "who was Hanaoka Seishū,
        // what surgery did he perform" resolves "Surgery" too, and the sources are needed for the surgeon, not the surgery.
        val own = subjects.filter { named(it, question) }
        if (own.isNotEmpty()) return if (own.any { rare(it, hasSimple = false, articleRows, mentions, readImported) }) READ_NOTES else 0
        val lead = sources.take(3)
        if (specialistOrImported(lead.first().evidence.documentId, readImported) &&
            lead.count { specialistOrImported(it.evidence.documentId, readImported) } >= 2) return READ_NOTES
        // No subject was looked up, but the search itself led with a full-edition article the question names ("Pantelegraph —
        // Overview"): the same rare subject, found the other way.
        val overview = lead.firstOrNull { it.evidence.title.endsWith(" — Overview") && from(fullEdition, it.evidence.documentId) && named(it.evidence, question) }
            ?: return 0
        val simpleHasIt = sources.any { from(mainstream, it.evidence.documentId) && bareTitle(it.evidence).equals(bareTitle(overview.evidence), ignoreCase = true) }
        return if (rare(overview.evidence, simpleHasIt, articleRows, mentions, readImported)) READ_NOTES else 0
    }

    /** The titles whose readership decides the route: the subjects' and the leading overview's. */
    fun titlesToCount(subjects: List<Evidence>, sources: List<PromptSource>): List<String> =
        (subjects.map(::bareTitle) + sources.take(3).filter { it.evidence.title.endsWith(" — Overview") }.map { bareTitle(it.evidence) })
            .filter { it.isNotBlank() }.distinct()
}
