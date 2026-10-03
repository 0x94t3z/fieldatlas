package xyz.fieldatlas.research

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import com.arm.aichat.PromptProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import xyz.fieldatlas.inference.InferenceGateway

class ResearchOrchestrator(
    private val retriever: Retriever,
    private val inference: InferenceGateway,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    /** The phone's position for "near me" questions only; null when unavailable or not allowed. */
    private val location: suspend () -> GeoPoint? = { null },
) {
    /** Prefill progress of the loaded engine, surfaced for the activity line. */
    val promptProgress: StateFlow<PromptProgress?> get() = inference.promptProgress

    /**
     * Live retrieval progress (0.0..1.0) for the activity line. Deliberately a StateFlow and
     * not a flow of events: the retriever reports milestones from inside its withContext(IO)
     * coroutine, and emitting flow values across coroutine contexts is illegal (it crashed
     * real searches on-device with "Flow exception transparency is violated") while a
     * StateFlow value may be published from any thread.
     */
    private val _searchProgress = MutableStateFlow(0.0)
    val searchProgress: StateFlow<Double> = _searchProgress

    /** How many evidence matches so far came from vector (concept) search instead of keywords. */
    private val _vectorMatches = MutableStateFlow(0)
    val vectorMatches: StateFlow<Int> = _vectorMatches

    private val activeRun = AtomicBoolean(false)

    fun research(
        question: String,
        resultLimit: Int = 8,
        contextTokenBudget: Int = 2_048,
        maxOutputTokens: Int = 1_536,
        attachments: List<xyz.fieldatlas.attachments.ExtractedAttachment> = emptyList(),
    ): Flow<ResearchEvent> = flow {
        if (question.isBlank()) {
            emit(ResearchEvent.Failed("Question must not be blank"))
            return@flow
        }
        require(resultLimit in 1..50) { "resultLimit must be between 1 and 50" }
        require(maxOutputTokens > 0) { "maxOutputTokens must be positive" }
        if (!activeRun.compareAndSet(false, true)) {
            emit(ResearchEvent.Failed("Another local research run is active"))
            return@flow
        }
        try {
            val startedAt = monotonicMillis()
            _searchProgress.value = 0.0
            _vectorMatches.value = 0
            QuestionRequirements.response(question, attachments.isNotEmpty())?.let { answer ->
                emit(ResearchEvent.Token(answer))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = 0,
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = emptySet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            if (attachments.isEmpty() && VenueLookup.isNearMe(question)) {
                emit(ResearchEvent.Searching(question))
                val point = runCatching { location() }.getOrNull()
                val categories = VenueLookup.nearbyCategories(question)
                // Widen the circle only when the closer one has too few matches: a dense city
                // answers from a small box, a village still finds the station 12 km away.
                var result: VenueLookup.Result? = null
                if (point != null) for (radius in NEARBY_RADII_KM) {
                    result = VenueLookup.nearbyAnswer(
                        question,
                        retriever.nearby(point, radius, NEARBY_CANDIDATES, categories),
                        radius,
                    )
                    if (result.sources.size >= NEARBY_ENOUGH) break
                }
                _searchProgress.value = 1.0
                val retrievedAt = monotonicMillis()
                result?.sources?.takeIf { it.isNotEmpty() }?.let { emit(ResearchEvent.Sources(it)) }
                emit(ResearchEvent.Token(result?.answer ?: VenueLookup.LOCATION_UNAVAILABLE))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, retrievedAt),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = result?.sources.orEmpty().indices.map { "S${it + 1}" }.toSet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            val questionTerms = FtsQuery.from(question)?.terms.orEmpty()
            // Retrieval merges several packs before the relevance gate runs. Request enough
            // candidates that unrelated packs cannot consume every slot ahead of a relevant
            // passage; the prompt still receives at most resultLimit sources.
            val candidateLimit = minOf(50, resultLimit * 3)
            emit(ResearchEvent.Searching(question))
            val includeLibrary = attachments.isEmpty() || AttachmentScope.includesLibrary(question)
            val placesOnly = attachments.isEmpty() && VenueLookup.isPlaceLookup(question)
            var evidence = if (includeLibrary) EvidenceRelevance.keep(
                retriever.searchForQuestion(question, question, candidateLimit, placesOnly) { progress ->
                    _searchProgress.value = progress.fraction
                    _vectorMatches.value = progress.vectorMatches
                },
                questionTerms,
                question = question,
            ).take(resultLimit) else emptyList()
            if (!includeLibrary) _searchProgress.value = 1.0

            // Query planning is a fallback, not a tax on every lookup. Exact/title/vector hits
            // answer immediately; only a weak first pass spends one short model turn finding
            // synonyms. This removes the two 96-token planning turns that dominated phone time.
            var keywords = emptyList<String>()
            if (attachments.isEmpty() && evidence.isEmpty() && retriever.hasEligiblePacks(question) &&
                !EvidenceRelevance.isShortCausalQuestion(question)) {
                emit(ResearchEvent.Planning(question))
                try {
                    val raw = StringBuilder()
                    inference
                        .generate(
                            QueryExpansion.prompt(question),
                            QueryExpansion.GENERATION_BUDGET,
                            QueryExpansion.SYSTEM_PROMPT,
                            seed = KEYWORD_SEED,
                        )
                        .collect { token -> raw.append(token) }
                    keywords = QueryExpansion.parse(raw.toString())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // The model-only answer below is still useful when planning fails.
                }
                if (keywords.isNotEmpty()) {
                    emit(ResearchEvent.Keywords(keywords))
                    emit(ResearchEvent.Searching(question))
                    _searchProgress.value = 0.0
                    _vectorMatches.value = 0
                    val merged = (questionTerms + keywords).distinct()
                    evidence = EvidenceRelevance.keep(
                        retriever.searchForQuestion(merged.joinToString(" "), question, candidateLimit) { progress ->
                            _searchProgress.value = progress.fraction
                            _vectorMatches.value = progress.vectorMatches
                        },
                        questionTerms,
                        keywords,
                        question,
                    ).take(resultLimit)
                }
            }
            // Fully vegan places can be a small minority of a city's vegan-tagged listings; one
            // more precise keyword pass puts them in front of the general matches.
            if (attachments.isEmpty()) VenueLookup.fullyVeganQuery(question, evidence)?.let { query ->
                val focused = EvidenceRelevance.keep(
                    retriever.searchForQuestion(query, question, candidateLimit, placesOnly = true) { progress ->
                        _searchProgress.value = progress.fraction
                    },
                    questionTerms,
                    question = question,
                )
                evidence = (focused + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            val retrievalFinishedAt = monotonicMillis()
            (if (attachments.isEmpty()) VenueLookup.answer(question, evidence) else null)?.let { venue ->
                emit(ResearchEvent.Sources(venue.sources))
                val answerAt = monotonicMillis()
                emit(ResearchEvent.Token(venue.answer))
                emit(ResearchEvent.Complete(
                    ResearchMetrics(
                        retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                        timeToFirstTokenMillis = elapsed(startedAt, answerAt),
                        totalMillis = elapsed(startedAt, monotonicMillis()),
                        generatedTokenCount = 0,
                        citedSourceIds = venue.sources.indices.map { "S${it + 1}" }.toSet(),
                        hasUnmappedCitation = false,
                    ),
                ))
                return@flow
            }
            if (attachments.isEmpty() && evidence.isEmpty() && !PromptBuilder.allowsModelExplanation(question)) {
                // A source-only request cannot be satisfied by unsupported model knowledge.
                // Finish normally so history and answer navigation retain the existing flow.
                val answerAt = monotonicMillis()
                emit(ResearchEvent.Token("I couldn't find supporting saved sources for this question. Add a relevant collection or file and try again."))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, answerAt),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = emptySet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            val outputBudget = if (attachments.isEmpty()) maxOutputTokens else minOf(maxOutputTokens, inference.contextWindowTokens / 4).coerceAtLeast(1)
            val packed = if (attachments.isNotEmpty()) {
                AttachmentEvidence.pack(question, AttachmentEvidence.select(question, attachments, maxOf(resultLimit, attachments.size)), evidence,
                    minOf(8192, inference.contextWindowTokens) - inference.promptOverheadTokens - outputBudget)
            } else if (evidence.isEmpty()) {
                PromptBuilder.buildModelOnly(question)
            } else {
                PromptBuilder.build(question, evidence, contextTokenBudget)
            }
            // A question for one specific item the passages never mention ("the Wi-Fi password at
            // Cedar Lodge") gets a plain gap reply; a small model otherwise answers with whatever
            // other details the files hold. Attachments are checked in full, not just the packed excerpts.
            if (!packed.mixedAnswer && packed.sources.isNotEmpty()) {
                val searched = if (attachments.isNotEmpty()) {
                    attachments.map { file -> Evidence("attachment:${file.id}", file.id, file.displayName, "Attached file", file.pages.joinToString("\n") { it.text }, 0.0) }
                } else packed.sources.map { it.evidence }
                AnswerChecks.missingItem(question, searched)?.let { item ->
                    emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
                    val where = when {
                        attachments.size == 1 -> "The attached file doesn't"
                        attachments.size > 1 -> "The attached files don't"
                        else -> "Your saved sources don't"
                    }
                    emit(ResearchEvent.Token("$where mention $item, so I can't give it."))
                    emit(ResearchEvent.Complete(ResearchMetrics(
                        retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                        timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                        totalMillis = elapsed(startedAt, monotonicMillis()),
                        generatedTokenCount = 0,
                        citedSourceIds = emptySet(),
                        hasUnmappedCitation = false,
                    )))
                    return@flow
                }
            }
            if (packed.sources.isEmpty()) {
                if (evidence.isNotEmpty()) {
                    emit(ResearchEvent.InsufficientEvidence("The context budget could not fit any evidence"))
                    return@flow
                }
            } else {
                emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
            }
            // A verbatim overview opening is only composed with mixed answers, whose stream
            // already replaces the whole answer on every token.
            val lead = if (attachments.isEmpty() && packed.mixedAnswer) SourceLead.select(question, packed.sources) else null
            if (lead != null) {
                emit(ResearchEvent.Lead(lead.render()))
            } else {
                // Field Atlas: an empty token flips the UI into the "writing" phase before the
                // first real token arrives, so prefill progress can show as tokens read/written.
                emit(ResearchEvent.Token(""))
            }
            fun compose(modelText: String): String =
                listOfNotNull(lead?.render(), modelText.takeIf(String::isNotBlank)).joinToString("\n\n")

            val attributionEvidence = packed.sources.map { it.evidence.copy(text = it.excerpt) }
            var firstTokenAt: Long? = null
            var generatedTokenCount = 0
            val output = StringBuilder()
            var generationFailed = false
            try {
                inference.generate(packed.prompt, outputBudget).collect { token ->
                    if (firstTokenAt == null) firstTokenAt = monotonicMillis()
                    generatedTokenCount++
                    output.append(token)
                    emit(if (packed.mixedAnswer) {
                        ResearchEvent.Token(compose(AnswerText.mixed(output.toString(), attributionEvidence)), replace = true)
                    } else ResearchEvent.Token(token))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The verbatim lead is already a useful, cited answer. Keep it rather than
                // discarding it because the slower explanation failed; without a lead the
                // failure still surfaces exactly as before.
                if (lead == null) throw error
                generationFailed = true
            }
            val finishedAt = monotonicMillis()
            // Evidence-only answers: move a citation to the source its details actually come from
            // and correct a 12-hour time that disagrees with the 24-hour time the source states.
            val checked = if (packed.mixedAnswer) output.toString()
                else AnswerChecks.addTwelveHour(
                    AnswerChecks.repairTimes(AnswerChecks.repairCitations(output.toString(), attributionEvidence), attributionEvidence),
                    question,
                )
                .let { text ->
                    if (packed.mixedAnswer || generationFailed) text
                    else AnswerChecks.exceptionNote(question, attributionEvidence)?.let { "$text\n\n_${it}_" } ?: text
                }
            if (checked != output.toString()) emit(ResearchEvent.Token(checked, replace = true))
            val modelText = if (packed.mixedAnswer) AnswerText.mixed(output.toString(), attributionEvidence) else checked
            val attributed = if (generationFailed) {
                compose(modelText) + "\n\n_The model explanation could not be completed._"
            } else compose(modelText)
            if (generationFailed) emit(ResearchEvent.Token(attributed, replace = true))
            val citations = AnswerText.citationAudit(attributed, packed.sources.size)
            val rawCitations = AnswerText.citationAudit(output.toString(), packed.sources.size)
            emit(
                ResearchEvent.Complete(
                    ResearchMetrics(
                        retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                        timeToFirstTokenMillis = firstTokenAt?.let { elapsed(startedAt, it) },
                        totalMillis = elapsed(startedAt, finishedAt),
                        generatedTokenCount = generatedTokenCount,
                        citedSourceIds = citations.citedSourceIds,
                        hasUnmappedCitation = citations.hasUnmappedCitation || rawCitations.hasUnmappedCitation ||
                            // Compare model text only: the lead adds its own valid marker and would mask drops.
                            AnswerText.citationMarkerCount(modelText) < AnswerText.citationMarkerCount(output.toString()),
                    ),
                ),
            )
        } finally {
            activeRun.set(false)
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(ResearchEvent.Failed(error.message ?: "Research failed"))
    }

    private fun elapsed(start: Long, end: Long): Long = (end - start).coerceAtLeast(0)

    private companion object {
        const val KEYWORD_SEED = 17
        /** City scale: "the city I'm in" and "near me" both fit; the answer states distances. */
        const val NEARBY_RADIUS_KM = 15.0
        val NEARBY_RADII_KM = listOf(2.0, 5.0, NEARBY_RADIUS_KM)
        const val NEARBY_ENOUGH = 6
        const val NEARBY_CANDIDATES = 200
    }
}
