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
    /** Bundled first-aid and safety guides; an emergency question is answered from these, never by the model. */
    private val emergencyGuides: () -> List<xyz.fieldatlas.emergency.EmergencyGuide> = { emptyList() },
    /** Bundled emergency telephone numbers by country; "what number do I call in Japan?" is answered from these. */
    private val emergencyNumbers: () -> xyz.fieldatlas.emergency.NumberBook? = { null },
    /** Learned prompt-reading speed; sizes file evidence to the phone. */
    private val prefillSpeed: PrefillSpeed = InMemoryPrefillSpeed(),
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
    ): Flow<ResearchEvent> {
        val guide = if (attachments.isEmpty()) xyz.fieldatlas.emergency.EmergencyGuideMatch.match(question, emergencyGuides()) else null
        // An emergency number is read from the saved table, never guessed by the model; a first-aid
        // question that also asks for the number keeps its guide, with the number leading it.
        if (attachments.isEmpty() && guide == null) {
            emergencyNumbers()?.let { book -> xyz.fieldatlas.emergency.EmergencyNumbers.answer(question, book) }?.let { answer ->
                return flow {
                    val startedAt = monotonicMillis()
                    emit(ResearchEvent.Sources(listOf(answer.evidence)))
                    emit(ResearchEvent.Token(answer.text))
                    emit(ResearchEvent.Complete(ResearchMetrics(
                        retrievalMillis = 0,
                        timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                        totalMillis = elapsed(startedAt, monotonicMillis()),
                        generatedTokenCount = 0,
                        citedSourceIds = setOf("S1"),
                        hasUnmappedCitation = false,
                    )))
                }
            }
        }
        // Arithmetic is worked out exactly by the app. A pure calculation needs no model; with a
        // further question ("is it dangerous?") the numbers lead and research answers the rest,
        // told the numbers so the model doesn't redo them.
        val calculation = (if (attachments.isEmpty() && question.isNotBlank()) Calculators.solve(question) else null)
            // A first-aid question is answered from its guide ("my friend's body temperature is 34°C
            // and he is shivering"); the arithmetic stays only when it concerns that same danger.
            ?.takeIf { guide == null || it.guideId == guide.id }
        if (calculation == null) {
            val core = researchCore(question, resultLimit, contextTokenBudget, maxOutputTokens, attachments)
            val numbers = guide?.let { emergencyNumbers()?.let { book -> xyz.fieldatlas.emergency.EmergencyNumbers.line(question, book) } }
            return if (numbers == null) core else leading(numbers + "\n\n", core)
        }
        if (calculation.complete) return flow {
            val startedAt = monotonicMillis()
            emit(ResearchEvent.Token(calculation.text + "\n\n_Calculated on this phone._"))
            emit(ResearchEvent.Complete(ResearchMetrics(
                retrievalMillis = 0,
                timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                totalMillis = elapsed(startedAt, monotonicMillis()),
                generatedTokenCount = 0,
                citedSourceIds = emptySet(),
                hasUnmappedCitation = false,
            )))
        }
        // Extreme heat or cold with "is it dangerous / safe?": a safety answer the app writes itself,
        // then the official guide as published. A model paraphrase got it wrong ("heat loss").
        calculation.riskNote?.let { note ->
            emergencyGuides().firstOrNull { it.id == calculation.guideId }?.let { guide ->
                return flow {
                    val startedAt = monotonicMillis()
                    emit(ResearchEvent.Sources(listOf(xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(guide))))
                    emit(ResearchEvent.Token(calculation.text + "\n\n" + note + "\n\n" +
                        xyz.fieldatlas.emergency.EmergencyGuideMatch.published(guide) + "\n\n_" +
                        xyz.fieldatlas.emergency.EmergencyGuideMatch.safetyNote(guide) + "_"))
                    emit(ResearchEvent.Complete(ResearchMetrics(
                        retrievalMillis = 0,
                        timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                        totalMillis = elapsed(startedAt, monotonicMillis()),
                        generatedTokenCount = 0,
                        citedSourceIds = setOf("S1"),
                        hasUnmappedCitation = false,
                    )))
                }
            }
        }
        return leading(calculation.text + "\n\n",
            researchCore(calculation.followUp, resultLimit, contextTokenBudget, maxOutputTokens, attachments, calculation.guideId))
    }

    /** Text the app writes itself, kept above everything the research run shows. */
    private fun leading(lead: String, core: Flow<ResearchEvent>): Flow<ResearchEvent> = flow {
        emit(ResearchEvent.Lead(lead))
        core.collect { event ->
            emit(when (event) {
                is ResearchEvent.Lead -> ResearchEvent.Lead(lead + event.text)
                is ResearchEvent.Token -> if (event.replace) ResearchEvent.Token(lead + event.text, replace = true) else event
                else -> event
            })
        }
    }

    private fun researchCore(
        question: String,
        resultLimit: Int,
        contextTokenBudget: Int,
        maxOutputTokens: Int,
        attachments: List<xyz.fieldatlas.attachments.ExtractedAttachment>,
        preferredGuideId: String? = null,
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
            // An emergency question is answered by the model from the matching official guide only
            // (strict, cited). If the model cannot run, the guide itself is shown as published.
            val guide = if (attachments.isEmpty()) xyz.fieldatlas.emergency.EmergencyGuideMatch.match(question, emergencyGuides())
                ?: preferredGuideId?.let { id -> emergencyGuides().firstOrNull { it.id == id } } else null
            if (guide != null && inference.state.value != xyz.fieldatlas.inference.InferenceState.Ready) {
                emit(ResearchEvent.Sources(listOf(xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(guide))))
                emit(ResearchEvent.Token(xyz.fieldatlas.emergency.EmergencyGuideMatch.answer(guide)))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = 0,
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = setOf("S1"),
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
                // Help in the countryside or on a trail can be far away: health, safety and water
                // keep widening past city scale; a restaurant 80 km off is not an answer.
                val radii = if (categories.any { it in FAR_REACH }) NEARBY_RADII_KM + FAR_RADII_KM else NEARBY_RADII_KM
                if (point != null) for (radius in radii) {
                    result = VenueLookup.nearbyAnswer(
                        question,
                        retriever.nearby(point, radius, NEARBY_CANDIDATES, categories),
                        radius,
                    )
                    // "Pharmacy open now near me" at night: six closed pharmacies close by are not
                    // an answer while the wider circle may hold one that is open.
                    if (result.sources.size >= NEARBY_ENOUGH && result.openNow != 0) break
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
            val includeLibrary = guide == null && (attachments.isEmpty() || AttachmentScope.includesLibrary(question))
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
            if (guide == null && attachments.isEmpty() && evidence.isEmpty() && retriever.hasEligiblePacks(question) &&
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
            if (guide == null && attachments.isEmpty()) VenueLookup.fullyVeganQuery(question, evidence)?.let { query ->
                val focused = EvidenceRelevance.keep(
                    retriever.searchForQuestion(query, question, candidateLimit, placesOnly = true) { progress ->
                        _searchProgress.value = progress.fraction
                    },
                    questionTerms,
                    question = question,
                )
                evidence = (focused + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            // A trip question that also asks for a museum gets that city's museums, not the zoo
            // its overview happens to mention.
            if (guide == null && attachments.isEmpty()) VenueLookup.museumQuery(question, evidence)?.let { query ->
                val museums = VenueLookup.museumListings(question,
                    retriever.searchForQuestion(query, question, candidateLimit, placesOnly = true) { progress ->
                        _searchProgress.value = progress.fraction
                    }, evidence)
                if (museums.isNotEmpty()) evidence = (museums.take(MUSEUM_SLOTS) + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            // A question naming one Ethereum proposal gets that proposal's own summary first.
            if (guide == null && attachments.isEmpty()) ProposalLookup.named(question)?.let { label ->
                val summary = ProposalLookup.labels(label).flatMap { retriever.titled(it, ProposalLookup.SUMMARY_SECTIONS, PROPOSAL_CANDIDATES) }
                    .filter { ProposalLookup.isSummaryOf(it, label) }
                if (summary.isNotEmpty()) evidence = (summary.take(PROPOSAL_SLOTS) + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            // "Cross the street in Hanoi" reads Hanoi's own "Stay safe" section, found by its title.
            if (guide == null && attachments.isEmpty()) TravelSections.lookup(question)?.let { travel ->
                for (place in travel.places.take(TRAVEL_PLACES)) {
                    val sections = TravelSections.pick(question, place,
                        retriever.titled(place, travel.sections, TRAVEL_CANDIDATES), TRAVEL_SLOTS)
                    if (sections.isEmpty()) continue
                    evidence = (sections + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    break
                }
            }
            val retrievalFinishedAt = monotonicMillis()
            (if (guide == null && attachments.isEmpty()) VenueLookup.answer(question, evidence) else null)?.let { venue ->
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
            if (guide == null && attachments.isEmpty() && evidence.isEmpty() && !PromptBuilder.allowsModelExplanation(question)) {
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
            // Ranked candidates beyond what fits are cheap; packing keeps only what the budget allows.
            val selection = if (attachments.isNotEmpty()) AttachmentEvidence.select(question, attachments, maxOf(ATTACHMENT_CANDIDATES, attachments.size)) else null
            val packed = if (selection != null) {
                AttachmentEvidence.pack(question, selection, evidence,
                    minOf(8192, inference.contextWindowTokens) - inference.promptOverheadTokens - outputBudget,
                    evidenceTokens = PrefillSpeed.evidenceTokens(prefillSpeed.tokensPerSecond))
            } else if (guide != null) {
                PromptBuilder.buildGuide(question, xyz.fieldatlas.emergency.EmergencyGuideMatch.select(question, guide,
                    PrefillSpeed.evidenceTokens(prefillSpeed.tokensPerSecond)))
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
            val lead = if (attachments.isEmpty() && packed.mixedAnswer)
                SourceLead.select(question, packed.sources) ?: ProposalLookup.lead(question, packed.sources) else null
            // For an emergency the official steps appear at once, word for word; the model's answer
            // for the person's situation streams in below them (it can take a minute on a slow phone).
            val guideLead = guide?.let { g ->
                packed.sources.firstOrNull()?.let { key ->
                    xyz.fieldatlas.emergency.EmergencyGuideMatch.officialSteps(key.evidence, 1, g.source.publisher) + "\n\n**For this situation**\n\n"
                }
            }
            if (lead != null) {
                emit(ResearchEvent.Lead(lead.render()))
            } else if (guideLead != null) {
                // A lead, not a token: the progress line keeps showing how much the model has read.
                emit(ResearchEvent.Lead(guideLead))
            } else {
                // Field Atlas: an empty token flips the UI into the "writing" phase before the
                // first real token arrives, so prefill progress can show as tokens read/written.
                emit(ResearchEvent.Token(""))
            }
            fun compose(modelText: String): String =
                listOfNotNull(lead?.render(), modelText.takeIf(String::isNotBlank)).joinToString("\n\n")

            val attributionEvidence = packed.sources.map { it.evidence.copy(text = it.excerpt) }
            var firstTokenAt: Long? = null
            var promptTokensRead: Int? = null
            var generatedTokenCount = 0
            val output = StringBuilder()
            var generationFailed = false
            val generateStartedAt = monotonicMillis()
            try {
                inference.generate(packed.prompt, outputBudget).collect { token ->
                    if (firstTokenAt == null) {
                        firstTokenAt = monotonicMillis()
                        // The engine's own count; read before it clears its progress.
                        promptTokensRead = inference.promptProgress.value?.total?.takeIf { it > 0 }
                    }
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
                // An emergency question still gets its guide, as published, if the model fails.
                if (lead == null && guide == null) throw error
                generationFailed = true
            }
            val finishedAt = monotonicMillis()
            // Learn this phone's reading speed from the wait before the first token.
            firstTokenAt?.let { first ->
                prefillSpeed.tokensPerSecond = PrefillSpeed.update(prefillSpeed.tokensPerSecond,
                    TokenEstimate.of(packed.prompt), first - generateStartedAt)
            }
            // Evidence-only answers: move a citation to the source its details actually come from
            // and correct a 12-hour time that disagrees with the 24-hour time the source states.
            // The person is offline: advice to search the web, visit a site or use an app is removed.
            val raw = AnswerChecks.removeOfflineNarration(AnswerChecks.removeOnlineAdvice(output.toString(), question), question)
            val checked = if (packed.mixedAnswer) raw
                else AnswerChecks.addTwelveHour(
                    AnswerChecks.repairTimes(AnswerChecks.repairCitations(raw, attributionEvidence), attributionEvidence),
                    question,
                )
                .let { text ->
                    if (packed.mixedAnswer || generationFailed) text
                    else if (guide != null) "${guideLead.orEmpty()}$text\n\n_${xyz.fieldatlas.emergency.EmergencyGuideMatch.safetyNote(guide)}_"
                    else AnswerChecks.exceptionNote(question, attributionEvidence)?.let { "$text\n\n_${it}_" } ?: text
                }
                .let { text ->
                    // Written by the app, not the model, so a partial read is always disclosed.
                    val coverage = selection?.let { AttachmentEvidence.coverageSummary(it, packed.sources.map { s -> s.evidence }, attachments) }
                    if (coverage == null || generationFailed) text else "$text\n\n_${coverage}_"
                }
            if (packed.mixedAnswer && checked == raw && raw != output.toString()) {
                // The streamed mixed answer still holds the removed advice; show it composed again without.
                emit(ResearchEvent.Token(compose(AnswerText.mixed(raw, attributionEvidence)), replace = true))
            } else if (checked != output.toString()) emit(ResearchEvent.Token(checked, replace = true))
            val modelText = if (packed.mixedAnswer) AnswerText.mixed(raw, attributionEvidence) else checked
            val attributed = when {
                generationFailed && guide != null -> xyz.fieldatlas.emergency.EmergencyGuideMatch.answer(guide)
                generationFailed -> compose(modelText) + "\n\n_The model explanation could not be completed._"
                else -> compose(modelText)
            }
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
                            AnswerText.citationMarkerCount(modelText) < AnswerText.citationMarkerCount(raw),
                        promptTokens = promptTokensRead,
                        promptMillis = firstTokenAt?.let { it - generateStartedAt },
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
        const val ATTACHMENT_CANDIDATES = 16
        const val MUSEUM_SLOTS = 3
        // A proposal's summary can run over two chunks, or come as a Simple Summary plus an Abstract.
        const val PROPOSAL_CANDIDATES = 8
        const val PROPOSAL_SLOTS = 2
        // The guide sections of the place a travel question names: the closest two of up to 30.
        const val TRAVEL_PLACES = 3
        const val TRAVEL_CANDIDATES = 30
        const val TRAVEL_SLOTS = 2
        /** City scale: "the city I'm in" and "near me" both fit; the answer states distances. */
        const val NEARBY_RADIUS_KM = 15.0
        val NEARBY_RADII_KM = listOf(2.0, 5.0, NEARBY_RADIUS_KM)
        val FAR_RADII_KM = listOf(50.0, 100.0)
        val FAR_REACH = setOf("Health", "Safety", "Water")
        const val NEARBY_ENOUGH = 6
        const val NEARBY_CANDIDATES = 200
    }
}
