package xyz.fieldatlas.ui.history

import android.content.Context
import xyz.fieldatlas.research.AnswerRecord
import xyz.fieldatlas.text.MarkdownLiteral

/** A question and its follow-ups, oldest first: one entry in History. */
internal class HistoryTopic(val turns: List<AnswerRecord>) {
    val first: AnswerRecord get() = turns.first()
    val last: AnswerRecord get() = turns.last()
    val key: Long get() = first.topicKey
}

/** Every conversation with a turn matching [query], the one with the latest turn first. */
internal fun historyTopics(records: List<AnswerRecord>, query: String): List<HistoryTopic> =
    records.groupBy { it.topicKey }.values
        .map { turns -> HistoryTopic(turns.sortedBy(AnswerRecord::createdAtEpochMs)) }
        .filter { topic -> topic.turns.any { historyMatches(it, query) } }
        .sortedByDescending { it.last.createdAtEpochMs }

/** What a topic is about, read from where its answers came from; a filter in History. */
internal enum class TopicKind(val label: String) { FirstAid("First aid"), Travel("Travel"), Knowledge("Knowledge"), Crypto("Crypto") }

private val cryptoWords = Regex("(?i)\\b(?:crypto\\w*|bitcoin|btc|ethereum|eth|blockchains?|nfts?|daos?|defi|tokens?|wallets?|stablecoins?|" +
    "smart contracts?|rollups?|staking|validators?|eips?|ercs?|airdrops?|mining|satoshi|vitalik|web3|gas fees?)\\b")

internal fun topicKind(topic: HistoryTopic): TopicKind {
    val ids = topic.turns.flatMap { turn -> turn.evidence.map { it.documentId } }
    return when {
        ids.any { it.startsWith("emergency:") } -> TopicKind.FirstAid
        ids.any { it.startsWith("wv") || it.startsWith("osm-") } -> TopicKind.Travel
        ids.any { it.startsWith("eip-") || it.startsWith("erc-") || it.startsWith("spec-") } ||
            topic.turns.any { cryptoWords.containsMatchIn(it.question) } -> TopicKind.Crypto
        else -> TopicKind.Knowledge
    }
}

/** The line under a topic's question: how many questions, sources (a guide, for first aid), and when. */
internal fun historyMeta(time: String, topic: HistoryTopic, kind: TopicKind = topicKind(topic)): String {
    val sources = topic.turns.flatMap { it.sources }.distinct().size
    val parts = buildList {
        if (topic.turns.size > 1) add("${topic.turns.size} questions")
        add(when {
            kind == TopicKind.FirstAid && topic.turns.size == 1 && sources == 1 -> "1 guide"
            sources == 0 -> "No sources"
            sources == 1 -> "1 source"
            else -> "$sources sources"
        })
        add(time)
    }
    return parts.joinToString(" · ")
}

/** Where a search matched in a topic: which question or source, and the words around the match. */
internal data class HistoryMatch(val where: String, val lead: String?, val before: String, val hit: String, val after: String)

private const val SNIPPET_BEFORE = 36
private const val SNIPPET_AFTER = 70

internal fun historyMatch(topic: HistoryTopic, query: String): HistoryMatch? {
    val words = query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return null
    topic.turns.forEachIndexed { index, turn ->
        if (!historyMatches(turn, query)) return@forEachIndexed
        val place = if (topic.turns.size > 1) "In question ${index + 1} of ${topic.turns.size}" else "In the answer"
        val lead = turn.question.takeIf { topic.turns.size > 1 }
        val answer = plainAnswer(turn.answer)
        val at = words.map { answer.lowercase().indexOf(it) }.filter { it >= 0 }.minOrNull()
        if (at != null) {
            val word = words.first { answer.lowercase().indexOf(it) == at }
            val start = (at - SNIPPET_BEFORE).coerceAtLeast(0).let { s -> if (s == 0) 0 else answer.indexOf(' ', s).takeIf { it in s until at }?.plus(1) ?: s }
            val end = (at + word.length + SNIPPET_AFTER).coerceAtMost(answer.length).let { e -> if (e == answer.length) e else answer.lastIndexOf(' ', e).takeIf { it > at + word.length } ?: e }
            return HistoryMatch(place, lead, (if (start > 0) "…" else "") + answer.substring(start, at), answer.substring(at, at + word.length),
                answer.substring(at + word.length, end) + if (end < answer.length) "…" else "")
        }
        val title = turn.sources.firstOrNull { source -> words.any { it in source.lowercase() } }
        if (title != null) return HistoryMatch("Source · $title", lead, "", "", "")
        return HistoryMatch(place, lead ?: turn.question, "", "", "")
    }
    return null
}

private fun plainAnswer(answer: String): String =
    MarkdownLiteral.decode(answer.replace(Regex("\\[S\\d+]"), "")).lineSequence().map(String::trim)
        .filterNot { it.isEmpty() || it.startsWith("#") || it.startsWith("_") }
        .joinToString(" ") { it.removePrefix(">").removePrefix("- ").removePrefix("* ").trim() }
        .replace(Regex("\\*\\*|__|`"), "").replace(Regex("\\s+"), " ")
        // A citation removed before a full stop would otherwise leave "general ." in the snippet.
        .replace(Regex(" ([.,;:!?])"), "$1").trim()

/** The topics kept on top of History, by topic key, on this phone. */
internal class HistoryPins(context: Context) {
    private val preferences = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    fun load(): Set<Long> = preferences.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    fun save(keys: Set<Long>) { preferences.edit().putStringSet(KEY, keys.map { it.toString() }.toSet()).apply() }
    private companion object { const val KEY = "pinned" }
}
