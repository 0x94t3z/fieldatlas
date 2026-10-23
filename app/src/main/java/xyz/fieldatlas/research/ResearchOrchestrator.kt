package xyz.fieldatlas.research

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import com.arm.aichat.PromptProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.fieldatlas.inference.InferenceGateway

class ResearchOrchestrator(
    private val retriever: Retriever,
    private val inference: InferenceGateway,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    /** The phone's position for "near me" questions only; null when unavailable or not allowed. */
    private val location: suspend () -> GeoPoint? = { null },
    /** Why [location] gave nothing, when it is known, so the reply says what to do. */
    private val locationProblem: () -> LocationProblem? = { null },
    /** Bundled first-aid and safety guides; an emergency question is answered from these, never by the model. */
    private val emergencyGuides: () -> List<xyz.fieldatlas.emergency.EmergencyGuide> = { emptyList() },
    /** Bundled emergency telephone numbers by country; "what number do I call in Japan?" is answered from these. */
    private val emergencyNumbers: () -> xyz.fieldatlas.emergency.NumberBook? = { null },
    /** Learned prompt-reading speed; sizes file evidence to the phone. */
    private val prefillSpeed: PrefillSpeed = InMemoryPrefillSpeed(),
    /**
     * Whether an explanation also reads more of its subject's article (see [ArticleDepth]), sized
     * to the phone's reading speed and added after the passages chosen as before.
     */
    private val articleDepth: Boolean = false,
    /** Optional in-process diagnostic observer. Nothing is logged by the app by default. */
    private val onEvidencePacked: (PackedPrompt) -> Unit = {},
    /**
     * Model-first answering: an ordinary question is answered from the model's own knowledge with up to this many of
     * the selected passages as notes (0: none), the passages listed under the answer. Null keeps the cited route.
     */
    private val modelFirstNotes: Int? = null,
    /** Whether a little-known subject is read from the sources even by a model that answers from memory (see [SourceRouter]). */
    private val sourceRouter: Boolean = true,
    /** Whether the router reads collections the user imported, as it reads specialist ones (see [SourceRouter]). */
    private val readImportedCollections: Boolean = false,
    /**
     * Measurement only, off in the app: the model writes a first-aid answer for the situation with the official guide
     * as its note, and the guide follows it. The app shows the guide as published (see the guide route).
     */
    private val modelWritesGuides: Boolean = false,
    /**
     * Measurement only, with [modelWritesGuides]: the written answer is checked against the guide (see
     * [xyz.fieldatlas.emergency.GuideAnswerCheck]), the prompt asks for "call now" on a danger sign already described,
     * and an answer from memory to a question describing a life threat opens with the call line too.
     */
    private val guideAnswerChecks: Boolean = false,
    /**
     * For a model not trusted with first aid (ModelOffer.writesFirstAid false) and a first-aid question no guide matched.
     * "fallback" (the app): no treatment steps from memory, only what the saved guides support. On 40 fresh first-aid
     * questions (set 47, two blind grades each) the 4B answering from memory had 6 answers both graders called unsafe, the
     * fallback 1. Measurement only: "memory", "careful" (a safety instruction added) and "cited" (the saved-passage route).
     */
    private val smallModelFirstAid: String = "fallback",
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

    /**
     * The phrase [question] asks for, when that comes first. "How do I say 'call the police' in Thai?"
     * asks for words, not for help or a number; "my friend is choking, how do I say help in Thai?" is
     * an emergency first, and so is a question that also asks for an emergency number.
     */
    fun phraseRequest(question: String, hasAttachments: Boolean = false): Phrasebook.Ask? =
        if (hasAttachments) null else Phrasebook.ask(question)?.takeIf {
            xyz.fieldatlas.emergency.EmergencyGuideMatch.match(Phrasebook.withoutRequest(question), emergencyGuides()) == null &&
                emergencyNumbers()?.let { book -> xyz.fieldatlas.emergency.EmergencyNumbers.answer(question, book) } == null
        }

    /**
     * Questions the app answers itself, so they can run before (or without) the model. Decided as
     * [research] decides: a phrase request goes to the phrasebook, never to the guide, number,
     * calculation or place its words mention, and whether a phrasebook line is the whole answer is
     * known only once the phrasebook is read, so it waits for the model.
     */
    fun answersWithoutModel(question: String, hasAttachments: Boolean): Boolean {
        if (QuestionRequirements.response(question, hasAttachments) != null) return true
        if (hasAttachments || phraseRequest(question) != null) return false
        return Calculators.solve(question)?.complete == true ||
            VenueLookup.isNearMe(question) || VenueLookup.isWhereAmI(question) ||
            xyz.fieldatlas.emergency.EmergencyGuideMatch.match(question, emergencyGuides()) != null ||
            emergencyNumbers()?.let { book -> xyz.fieldatlas.emergency.EmergencyNumbers.answer(question, book) } != null
    }

    fun research(
        question: String,
        resultLimit: Int = 8,
        contextTokenBudget: Int = 2_048,
        maxOutputTokens: Int = 1_536,
        attachments: List<xyz.fieldatlas.attachments.ExtractedAttachment> = emptyList(),
        /** For a follow-up: the answer it builds on, whose passages can be read again. */
        topic: TopicEvidence.Carried? = null,
        /** With files attached, also search the collections unless the question says files only; the files' passages
         * still lead. The app leaves it off: on set 31 it made answers worse. The desktop harness can turn it on. */
        libraryWithFiles: Boolean = false,
    ): Flow<ResearchEvent> {
        val phrase = phraseRequest(question, attachments.isNotEmpty())
        val guide = if (attachments.isEmpty() && phrase == null) guideFor(question, topic) else null
        // An emergency number is read from the saved table, never guessed by the model; a first-aid
        // question that also asks for the number keeps its guide, with the number leading it.
        if (attachments.isEmpty() && guide == null && phrase == null) {
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
        // told the numbers so the model doesn't redo them. A phrase request asks for words: "it is
        // 40°C outside today" in Spanish is a sentence to say, not a sum or a heat warning.
        val calculation = (if (attachments.isEmpty() && phrase == null && question.isNotBlank()) Calculators.solve(question) else null)
            // A first-aid question is answered from its guide ("my friend's body temperature is 34°C
            // and he is shivering"); the arithmetic stays only when it concerns that same danger.
            ?.takeIf { guide == null || it.guideId == guide.id }
        // A word problem that gives every number it needs is answered from those numbers alone:
        // retrieval only brought a parador's discount or a film about a train, which the model then
        // worked into the sum. Its every written step is recomputed on the phone.
        if (attachments.isEmpty() && phrase == null && guide == null && (calculation == null || !calculation.complete && calculation.guideId == null) &&
            WordProblem.isSelfContained(question)) {
            return arithmetic(question, calculation?.text, maxOutputTokens)
        }
        if (calculation == null) {
            val core = researchCore(question, resultLimit, contextTokenBudget, maxOutputTokens, attachments, topic = topic,
                libraryWithFiles = libraryWithFiles)
            val numbers = guide?.let { emergencyNumbers()?.let { book -> xyz.fieldatlas.emergency.EmergencyNumbers.line(question, book) } }
            // First aid no guide matched opens with the app's call line on every route (an answer, a refusal, the fallback):
            // on set 47 refusals and off-topic answers to "heavy bleeding at 32 weeks" never said to call.
            if (numbers == null && guide == null && attachments.isEmpty() && phrase == null &&
                xyz.fieldatlas.emergency.GuideAnswerCheck.firstAidQuestion(question))
                return leading(xyz.fieldatlas.emergency.GuideAnswerCheck.callLine(question, guideFound = false, cautious = !inference.writesFirstAid), core)
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
            researchCore(calculation.followUp, resultLimit, contextTokenBudget, maxOutputTokens, attachments, calculation.guideId,
                libraryWithFiles = libraryWithFiles))
    }

    /** The model works a self-contained sum from the question's own numbers, each step checked. */
    private fun arithmetic(question: String, lead: String?, maxOutputTokens: Int): Flow<ResearchEvent> = flow {
        if (!activeRun.compareAndSet(false, true)) {
            emit(ResearchEvent.Failed("Another local research run is active"))
            return@flow
        }
        try {
            val startedAt = monotonicMillis()
            val packed = PromptBuilder.buildArithmetic(question, lead)
            val prefix = lead?.let { "$it\n\n" } ?: ""
            if (lead != null) emit(ResearchEvent.Lead(prefix))
            val output = StringBuilder()
            var firstTokenAt: Long? = null
            var promptTokensRead: Int? = null
            var generatedTokenCount = 0
            inference.generate(packed.prompt, maxOutputTokens, savedPrefixChars = packed.savedPrefixChars).collect { token ->
                if (firstTokenAt == null) {
                    firstTokenAt = monotonicMillis()
                    promptTokensRead = inference.promptProgress.value?.total?.takeIf { it > 0 }
                }
                generatedTokenCount++
                output.append(token)
                emit(ResearchEvent.Token(prefix + AnswerText.mixed(output.toString(), emptyList()), replace = true))
            }
            firstTokenAt?.let { first ->
                val read = if (inference.reusedPromptTokens > 0) packed.prompt.substring(packed.savedPrefixChars) else packed.prompt
                prefillSpeed.tokensPerSecond = PrefillSpeed.update(prefillSpeed.tokensPerSecond, TokenEstimate.of(read), first - startedAt)
            }
            // The same offline rule as every other model answer: no "check online" a phone without signal cannot follow.
            val checked = AnswerChecks.removeOnlineAdvice(AnswerChecks.checkArithmetic(AnswerText.visible(output.toString()), question), question)
            emit(ResearchEvent.Token(prefix + AnswerText.mixed(checked, emptyList()), replace = true))
            emit(ResearchEvent.Complete(ResearchMetrics(
                workedFromQuestion = true,
                retrievalMillis = 0,
                timeToFirstTokenMillis = elapsed(startedAt, firstTokenAt ?: monotonicMillis()),
                totalMillis = elapsed(startedAt, monotonicMillis()),
                generatedTokenCount = generatedTokenCount,
                citedSourceIds = emptySet(),
                hasUnmappedCitation = false,
                promptTokens = promptTokensRead,
                promptMillis = firstTokenAt?.let { it - startedAt },
            )))
        } finally {
            activeRun.set(false)
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(ResearchEvent.Failed(error.message ?: "Research failed"))
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

    /**
     * [leading], taken back when the run found nothing: a note promising the city's listings above
     * "I couldn't produce a properly cited explanation" contradicts itself.
     */
    private fun leadingIfFound(lead: String, core: Flow<ResearchEvent>): Flow<ResearchEvent> = flow {
        var shown = ""
        var found = false
        leading(lead, core).collect { event ->
            when (event) {
                is ResearchEvent.Sources -> if (event.evidence.isNotEmpty()) found = true
                is ResearchEvent.Lead -> shown = event.text.removePrefix(lead)
                is ResearchEvent.Token -> shown = if (event.replace) event.text.removePrefix(lead) else shown + event.text
                is ResearchEvent.Complete -> if (!found || shown.trim() in setOf(CitedExplanation.GAP, CitedExplanation.UNAVAILABLE))
                    emit(ResearchEvent.Token(shown, replace = true))
                // A run that ends without an answer does not keep a note promising one.
                is ResearchEvent.Failed, is ResearchEvent.InsufficientEvidence -> emit(ResearchEvent.Token(shown, replace = true))
                else -> Unit
            }
            emit(event)
        }
    }

    private fun researchCore(
        question: String,
        resultLimit: Int,
        contextTokenBudget: Int,
        maxOutputTokens: Int,
        attachments: List<xyz.fieldatlas.attachments.ExtractedAttachment>,
        preferredGuideId: String? = null,
        topic: TopicEvidence.Carried? = null,
        libraryWithFiles: Boolean = false,
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
            // Emergency instructions must not be rewritten by the model. Real-model regression
            // checks reversed official instructions despite lexical support; show the guide itself.
            val phraseAsk = phraseRequest(question, attachments.isNotEmpty())
            val guide = if (attachments.isEmpty()) (if (phraseAsk == null) guideFor(question, topic) else null)
                ?: preferredGuideId?.let { id -> emergencyGuides().firstOrNull { it.id == id } } else null
            // A model trusted with it writes the answer for the situation, the guide as its source (ModelOffer.writesFirstAid);
            // every other model shows the guide as published.
            val writesGuide = modelWritesGuides || inference.writesFirstAid
            val checksGuide = guideAnswerChecks || inference.writesFirstAid
            if (guide != null && writesGuide && inference.state.value == xyz.fieldatlas.inference.InferenceState.Ready) {
                val evidence = xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(guide)
                emit(ResearchEvent.Sources(listOf(evidence)))
                val thinking = inference.thinkingBudget.coerceAtLeast(0)
                val first = PromptBuilder.buildGuideAnswer(question, evidence, thinking > 0, dangerSignsFirst = checksGuide)
                onEvidencePacked(first)
                emit(ResearchEvent.Token(""))
                val written = StringBuilder()
                var tokens = 0
                var firstAt: Long? = null
                val generationStart = monotonicMillis()
                // "Now" when the question describes a life threat or a swallowed poison, as on the no-guide route (set 50 opened
                // a swallowed poison's guide with "if this is an emergency").
                val lead = xyz.fieldatlas.emergency.GuideAnswerCheck.callLine(question, guideId = guide.id)
                // The official guide is on screen within a second; the answer written for the situation grows above it and
                // replaces it when checked (the guide stays in Sources). Writing takes minutes on a phone, and until 22 Oct
                // the screen showed only the call line meanwhile.
                val meanwhile = GuideStandIn.NOTE + "\n\n" + xyz.fieldatlas.emergency.EmergencyGuideMatch.published(guide)
                emit(ResearchEvent.Token(lead + meanwhile, replace = true))
                suspend fun write(answer: Flow<String>) = answer.collect { token ->
                    if (firstAt == null) firstAt = monotonicMillis()
                    tokens++
                    written.append(token)
                    val shownSoFar = AnswerText.mixed(AnswerChecks.removeDoses(
                        AnswerChecks.removeOnlineAdvice(written.toString(), question), question, emptyList()), emptyList())
                    // Checked as it streams: a step the guide forbids is never on screen, not even before the answer ends.
                    val partial = if (checksGuide) xyz.fieldatlas.emergency.GuideAnswerCheck.streamed(shownSoFar, guide) else shownSoFar
                    emit(ResearchEvent.Token(lead + (if (partial.isBlank()) "" else partial.trimEnd() + "\n\n---\n\n") + meanwhile, replace = true))
                }
                write(if (thinking > 0) inference.think(first.prompt, maxOutputTokens + thinking, savedPrefixChars = first.savedPrefixChars)
                    else inference.generate(first.prompt, maxOutputTokens, savedPrefixChars = first.savedPrefixChars))
                if (thinking > 0 && (AnswerText.visible(written.toString()).isBlank() || inference.lastHitLimit)) {
                    written.clear()
                    val direct = PromptBuilder.buildGuideAnswer(question, evidence, thinking = false, dangerSignsFirst = checksGuide)
                    write(inference.generate(direct.prompt, maxOutputTokens, savedPrefixChars = direct.savedPrefixChars))
                }
                val raw = AnswerChecks.removeDoses(AnswerChecks.removeOfflineNarration(
                    AnswerChecks.removeOnlineAdvice(written.toString(), question), question), question, emptyList())
                val draft = AnswerText.finalized(raw, 1).trim()
                val checked = if (checksGuide) xyz.fieldatlas.emergency.GuideAnswerCheck.check(question, guide, draft) else null
                val body = checked?.answer ?: draft
                val figures = if (checksGuide) SavedFactCheck.note(question, body, listOf(PromptSource("S1", evidence, excerpt = evidence.text))) else null
                // The guide's own call-now line names the sign; it is not repeated under a lead that already says "now".
                val shown = lead + listOfNotNull(checked?.callNow?.takeIf { lead == CALL_FIRST }, body, figures,
                    if (guide.source.license.startsWith("Open Government Licence"))
                        // An answer written from an NHS page is an adaptation: credited to the licence, never presented as the NHS's.
                        "_Written for your situation from a guide on the NHS website [S1] (contains public sector information licensed under " +
                            "the Open Government Licence v3.0; not reviewed or endorsed by the NHS). The guide as published is in Sources._"
                    else "_Written for your situation from " + xyz.fieldatlas.emergency.GuideAnswerCheck.possessive(guide.source.publisher) +
                        " guide [S1]; the guide as published is in Sources._")
                    .joinToString("\n\n")
                emit(ResearchEvent.Token(shown, replace = true))
                val audit = AnswerText.citationAudit(shown, 1)
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = 0,
                    timeToFirstTokenMillis = firstAt?.let { elapsed(startedAt, it) },
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = tokens,
                    citedSourceIds = audit.citedSourceIds,
                    hasUnmappedCitation = audit.hasUnmappedCitation,
                    promptMillis = firstAt?.let { it - generationStart },
                )))
                return@flow
            }
            if (guide != null) {
                emit(ResearchEvent.Sources(listOf(xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(guide))))
                emit(ResearchEvent.Token(xyz.fieldatlas.emergency.EmergencyGuideMatch.answer(guide,
                    modelUnavailable = inference.state.value != xyz.fieldatlas.inference.InferenceState.Ready, question = question,
                    cautious = !inference.writesFirstAid)))
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
            // The small model's no-guide reply uses none of the library: decided before the search, it appears at once. On the
            // Redmi the search first cost 50-70 s before an emergency answer that never used it (22 Oct offline check).
            if (attachments.isEmpty() && phraseAsk == null && !inference.writesFirstAid && smallModelFirstAid == "fallback" &&
                xyz.fieldatlas.emergency.GuideAnswerCheck.firstAidQuestion(question)) {
                val swallowed = xyz.fieldatlas.emergency.GuideAnswerCheck.swallowedSomething(question)
                val basics = (listOf("cpr", "recovery-position", "anaphylaxis") + (if (swallowed) listOf("poisoning-steps") else emptyList()))
                    .mapNotNull { id -> emergencyGuides().firstOrNull { it.id == id } }
                emit(ResearchEvent.Sources(basics.map { xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(it) }))
                emit(ResearchEvent.Token(FIRST_AID_GAP + (if (swallowed && basics.size == 4) POISON_LINE else "")))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = 0,
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = basics.indices.map { "S${it + 1}" }.toSet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            if (attachments.isEmpty() && phraseAsk == null) {
                StructuredFieldLookup.ask(question)?.let { ask ->
                    emit(ResearchEvent.Searching(question))
                    val result = StructuredFieldLookup.answer(ask,
                        retriever.titled(ask.dataset, listOf(ask.record), limit = 20))
                    if (result != null) {
                        _searchProgress.value = 1.0
                        val retrievedAt = monotonicMillis()
                        emit(ResearchEvent.Sources(result.evidence))
                        emit(ResearchEvent.Token(result.answer))
                        emit(ResearchEvent.Complete(ResearchMetrics(
                            retrievalMillis = elapsed(startedAt, retrievedAt),
                            timeToFirstTokenMillis = elapsed(startedAt, retrievedAt),
                            totalMillis = elapsed(startedAt, monotonicMillis()),
                            generatedTokenCount = 0,
                            citedSourceIds = result.evidence.indices.map { "S${it + 1}" }.toSet(),
                            hasUnmappedCitation = false,
                        )))
                        return@flow
                    }
                }
            }
            // "Where am I?" is answered from the phone's position and the map collections around it.
            if (attachments.isEmpty() && phraseAsk == null && VenueLookup.isWhereAmI(question)) {
                emit(ResearchEvent.Searching(question))
                val point = runCatching { location() }.getOrNull()
                var places = emptyList<NearbyPlace>()
                if (point != null) for (radius in WHERE_RADII_KM) {
                    places = retriever.nearby(point, radius, NEARBY_CANDIDATES)
                    if (places.size >= NEARBY_ENOUGH) break
                }
                _searchProgress.value = 1.0
                val retrievedAt = monotonicMillis()
                val result = point?.let { VenueLookup.whereAnswer(it, places) }
                result?.sources?.takeIf { it.isNotEmpty() }?.let { emit(ResearchEvent.Sources(it)) }
                emit(ResearchEvent.Token(result?.answer ?: VenueLookup.locationUnavailable(locationProblem())))
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
            // "A hospital near the Colosseum in Rome": the places around the landmark, nearest first, measured from where
            // the saved guide puts it. A city-wide list put a hospital 7 km away first (23 Oct). No position, no change.
            val nearLandmark = if (attachments.isEmpty() && phraseAsk == null) landmarkAnswer(question) else null
            if (nearLandmark != null) {
                val answered: VenueLookup.Result = nearLandmark
                _searchProgress.value = 1.0
                val retrievedAt = monotonicMillis()
                emit(ResearchEvent.Sources(answered.sources))
                emit(ResearchEvent.Token(answered.answer))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, retrievedAt),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = answered.sources.indices.map { "S${it + 1}" }.toSet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            if (attachments.isEmpty() && phraseAsk == null && VenueLookup.isNearMe(question)) {
                emit(ResearchEvent.Searching(question))
                val point = runCatching { location() }.getOrNull()
                val categories = VenueLookup.nearbyCategories(question)
                // Widen the circle only when the closer one has too few matches: a dense city
                // answers from a small box, a village still finds the station 12 km away.
                var result: VenueLookup.Result? = null
                // Help in the countryside or on a trail can be far away: health, safety and water
                // keep widening past city scale; a restaurant 80 km off is not an answer.
                val radii = when {
                    categories.any { it in FAR_REACH } -> NEARBY_RADII_KM + FAR_RADII_KM
                    // "The best vegan restaurants in the city I'm in" ranks the city, not the street.
                    VenueLookup.asksBest(question) -> BEST_RADII_KM
                    else -> NEARBY_RADII_KM
                }
                if (point != null) for (radius in radii) {
                    result = VenueLookup.nearbyAnswer(
                        question,
                        retriever.nearby(point, radius, NEARBY_CANDIDATES, categories),
                        radius,
                        fixAgeMinutes = point.ageMinutes,
                    )
                    // "Pharmacy open now near me" at night: six closed pharmacies close by are not
                    // an answer while the wider circle may hold one that is open.
                    if (result.sources.size >= NEARBY_ENOUGH && result.openNow != 0) break
                }
                // Museums and sights are travel-guide listings without map positions: answer them from
                // the city the mapped places around the phone are filed under.
                // The city's answer can need the model, and near-me questions may start before it has
                // loaded, so without a ready model the phone's own "nothing nearby" answer stands.
                if (point != null && result?.sources.isNullOrEmpty() && VenueLookup.asksGuideKind(question) &&
                    inference.state.value == xyz.fieldatlas.inference.InferenceState.Ready) {
                    VenueLookup.cityOf(retriever.nearby(point, WHERE_RADII_KM.last(), NEARBY_CANDIDATES))?.let { city ->
                        // This run hands over to the city question, which takes the run itself. The
                        // reader asked about their surroundings, so the answer says it is city-wide.
                        activeRun.set(false)
                        emitAll(leadingIfFound(VenueLookup.cityWideNote(question, city),
                            research(VenueLookup.inCity(question, city), resultLimit, contextTokenBudget, maxOutputTokens)))
                        return@flow
                    }
                }
                _searchProgress.value = 1.0
                val retrievedAt = monotonicMillis()
                result?.sources?.takeIf { it.isNotEmpty() }?.let { emit(ResearchEvent.Sources(it)) }
                emit(ResearchEvent.Token(result?.answer ?: VenueLookup.locationUnavailable(locationProblem())))
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
            val includeLibrary = (attachments.isEmpty() || AttachmentScope.includesLibrary(question, byDefault = libraryWithFiles))
            // A follow-up read as a name the earlier answer gave is about that name; the earlier answer's own
            // passages then follow the new search instead of leading it.
            val carriedAhead = topic == null || !FollowUp.namesSubject(question, topic.earlier)
            val placesOnly = attachments.isEmpty() && VenueLookup.isPlaceLookup(question)
            val technicalFocus = if (attachments.isEmpty()) TechnicalRuleFocus.from(question) else null
            val destinations = if (includeLibrary && attachments.isEmpty()) retriever.destinations(question) else emptyList()
            var evidence = if (includeLibrary) EvidenceRelevance.keep(
                retriever.searchForQuestion(question, question, candidateLimit, placesOnly) { progress ->
                    _searchProgress.value = progress.fraction
                    _vectorMatches.value = progress.vectorMatches
                },
                questionTerms,
                question = question,
            ).let { technicalFocus?.prioritize(it) ?: it }.take(resultLimit) else emptyList()
            if (!includeLibrary) _searchProgress.value = 1.0

            if (includeLibrary && attachments.isEmpty() && !placesOnly && technicalFocus == null &&
                retriever.hasEligiblePacks(question)) {
                evidence = StudyApplicability.recover(question, evidence, retriever, candidateLimit).take(resultLimit)
            }

            // One bounded discovery pass for each explicit missing clause. Each search
            // retains the full question's scope; only already eligible passages enter.
            if (includeLibrary && attachments.isEmpty() && technicalFocus == null && !placesOnly &&
                retriever.hasEligiblePacks(question)) {
                evidence = MultipartEvidence.recover(question, evidence, retriever, candidateLimit, resultLimit)
            }
            // A follow-up also reads the passages behind the answer it follows (library questions only; files and
            // place lookups keep their own sources).
            if (includeLibrary && !placesOnly) evidence = TopicEvidence.merge(evidence, topic, resultLimit, carriedAhead)

            // Query planning is a fallback, not a tax on every lookup. Exact/title/vector hits
            // answer immediately; only a weak first pass spends one short model turn finding
            // synonyms. This removes the two 96-token planning turns that dominated phone time.
            var keywords = emptyList<String>()
            if (attachments.isEmpty() && evidence.isEmpty() && phraseAsk == null && technicalFocus == null && retriever.hasEligiblePacks(question) &&
                !EvidenceRelevance.isShortCausalQuestion(question) &&
                !EvidenceRelevance.missingRequestedIdentifier(question, evidence) &&
                !EvidenceRelevance.missingNamedMechanism(question, evidence)) {
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
                    ).let { technicalFocus?.prioritize(it) ?: it }.take(resultLimit)
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
            // Search the requested kind within a confirmed destination, not the city's overview.
            if (attachments.isEmpty()) VenueLookup.kindQuery(question, evidence, destinations)?.let { query ->
                val places = VenueLookup.kindListings(question,
                    retriever.searchForQuestion(query.query, query.scope, candidateLimit, placesOnly = true) { progress ->
                        _searchProgress.value = progress.fraction
                    }, evidence, destinations)
                if (places.isNotEmpty()) evidence = (places.take(resultLimit) + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            // A question naming one Ethereum proposal gets that proposal's own summary first.
            if (attachments.isEmpty() && technicalFocus == null) ProposalLookup.named(question)?.let { label ->
                val summary = ProposalLookup.labels(label).flatMap { retriever.titled(it, ProposalLookup.SUMMARY_SECTIONS, PROPOSAL_CANDIDATES) }
                    .filter { ProposalLookup.isSummaryOf(it, label) }
                if (summary.isNotEmpty()) evidence = (summary.take(PROPOSAL_SLOTS) + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            // "How do I say thank you in Thai?" reads the Thai phrasebook's phrase lists, whole.
            var phrasePicked = emptyList<Evidence>()
            if (phraseAsk != null) for (book in Phrasebook.books(phraseAsk.language)) {
                val rows = retriever.article(book, Phrasebook.ROWS).filter { Phrasebook.isPhraseList(it, book) }
                if (rows.isEmpty()) continue
                phrasePicked = Phrasebook.pick(phraseAsk, rows, PHRASEBOOK_SLOTS)
                evidence = (phrasePicked + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                break
            }
            // "Cross the street in Hanoi" reads Hanoi's own "Stay safe" section, found by its title.
            var travelPlace: String? = null
            var travelPicked = emptyList<Evidence>()
            var airportGuide: String? = null
            var travelNames = destinations
            var travelThings = emptyList<String>()
            var travelJourney: TravelSections.Journey? = null
            if (attachments.isEmpty() && phrasePicked.isEmpty() && !placesOnly) TravelSections.lookup(question)?.let { requested ->
                val names = (requested.places + requested.candidates).distinct().take(12)
                val titles = names.flatMap { name ->
                    retriever.titled(name, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES, anchored = true)
                }.filter { it.documentId.startsWith("wvs-") || it.source.contains("wikivoyage", true) }.map { it.title }
                val resolved = TravelSections.resolved(requested.copy(candidates = names), titles)
                val travel = resolved ?: requested.takeIf { it.places.isNotEmpty() } ?: return@let
                travelNames = (destinations + travel.places).distinct()
                travelThings = requested.places.filter { name -> travel.places.none { it.equals(name, true) } }
                travelJourney = travel.journey
                class Read(val place: String, val picked: List<Evidence>)
                // Each section on its own, anchored to the place's own guide: a big guide's short
                // "Buy > Shopping" titles must not crowd out "Get in > Entry requirements".
                suspend fun read(place: String, sections: List<String>, needHeaded: Boolean = false): Read? {
                    val picked = TravelSections.pick(question, place,
                        sections.flatMap { retriever.titled(place, listOf(it), TRAVEL_CANDIDATES, anchored = true) }, TRAVEL_SLOTS, travelNames)
                    if (picked.isEmpty() || (needHeaded && !TravelSections.coversPart(question, place, picked, travel.journey, travelNames, travelThings))) return null
                    return Read(place, picked)
                }
                // Bounded city -> region -> country traversal, with cycle protection.
                suspend fun parents(place: String): List<String> {
                    val seen = linkedSetOf(place)
                    val pending = ArrayDeque<String>().apply { add(place) }
                    while (pending.isNotEmpty() && seen.size <= 8) {
                        val current = pending.removeFirst()
                        val overview = SubjectLookup.overviewOf(current,
                            retriever.titled(current, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES, anchored = true)) ?: continue
                        for (parent in TravelSections.parentsOf(current, overview.text)) {
                            if (seen.size >= 9) break
                            if (seen.none { it.equals(parent, true) }) { seen += parent; pending += parent }
                        }
                    }
                    return seen.drop(1)
                }
                suspend fun parentRead(place: String, sections: List<String>, needHeaded: Boolean = true): Read? {
                    for (parent in parents(place)) read(parent, sections, needHeaded)?.let { return it }
                    return null
                }
                var read: Read? = null
                val journey = travel.journey
                if (journey != null) {
                    // "Lisbon to Porto by train": the country's rail advice when both lie in one country,
                    // else how to get into the destination.
                    val originParents = parents(journey.origin)
                    val destinationParents = parents(journey.destination)
                    for (country in originParents.filter { it in destinationParents }) {
                        read = read(country, listOf("Get around"), needHeaded = true)
                        if (read != null) break
                    }
                    if (read == null) read = read(journey.destination, (travel.sections + "Get in").distinct())
                } else {
                    val named = travel.places.take(TRAVEL_PLACES)
                    var uncovered: Read? = null
                    for (place in named) {
                        val found = read(place, travel.sections) ?: continue
                        if (TravelSections.coversPart(question, place, found.picked, travel.journey, travelNames, travelThings)) { read = found; break }
                        if (uncovered == null) uncovered = found
                    }
                    if (read == null && uncovered == null) {
                        for (place in named) { read = parentRead(place, travel.sections) ?: continue; break }
                    } else if (read == null) {
                        // "Flying into Bali, visa on arrival?": Bali's guide has no entry section; Indonesia's has.
                        read = parentRead(uncovered!!.place, travel.sections) ?: uncovered
                    }
                }
                read?.let { found ->
                    var picked = found.picked
                    // "SIM card at Delhi airport": the airport's own guide adds what the city's lacks.
                    TravelSections.airportOf(question)?.let { airport ->
                        TravelSections.airportGuide(airport, retriever.airportOverviews(airport, AIRPORT_CANDIDATES))?.let { guideTitle ->
                            picked = picked + TravelSections.pick(question, guideTitle,
                                TravelSections.airportSections(question).flatMap { retriever.titled(guideTitle, listOf(it), TRAVEL_CANDIDATES, anchored = true) }, 1)
                            airportGuide = guideTitle
                        }
                    }
                    evidence = (picked + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    travelPlace = found.place
                    travelPicked = picked
                }
            }
            // "I lost my passport while travelling" reads the travel guide "Passport", found by its
            // title, when no place guide answered; an explanation reads its subject's article below.
            // Cultural practices can have a fuller topic guide than the country's short introduction.
            // Only sections explicitly naming the resolved destination can supplement its guide.
            if (attachments.isEmpty() && travelPlace != null && "Respect" in TravelSections.topicsOf(question)) {
                for (name in TopicGuides.names(question).filterNot(VenueLookup::isVenueKind).sortedBy { it.count(Char::isWhitespace) }.take(TOPIC_NAMES)) {
                    if (travelPlace.equals(name, true)) continue
                    val related = TopicGuides.pickForDestination(question, name, retriever.sections(name, TOPIC_CANDIDATES), travelNames, 2)
                    if (related.isEmpty()) continue
                    evidence = (related + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    travelPicked = (related + travelPicked).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    break
                }
            }
            if (attachments.isEmpty() && technicalFocus == null && travelPlace == null && phrasePicked.isEmpty() && !SubjectLookup.explains(question) &&
                !VenueLookup.isPlaceLookup(question) && ProposalLookup.named(question) == null) {
                for (name in TopicGuides.names(question).filterNot(VenueLookup::isVenueKind)) {
                    val sections = TopicGuides.pick(question, name, retriever.sections(name, TOPIC_CANDIDATES), TRAVEL_SLOTS)
                    if (sections.isEmpty()) continue
                    evidence = (sections + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    travelPlace = sections.first().title.substringBefore(" — ")
                    travelPicked = sections
                    break
                }
            }
            // The search's best passage, taken before any subject is read: it replaces a broader subject (below) and keeps a
            // place after the first subject in the prompt (PromptBuilder.buildKnowledge). A map listing ("Stockholm — AM Cash &
            // Carry") is never the passage a question is about, and a follow-up keeps its earlier passages' places.
            val searchLead = evidence.firstOrNull { !it.documentId.startsWith("wv-place-") && !it.documentId.startsWith("wv-eat-") &&
                !it.documentId.startsWith("osm-place-") }.takeIf { topic == null }
            // "How does a heat pump heat a house?" reads the article "Heat pump", found by its title;
            // a comparison reads both of its subjects.
            val subjects = ArrayList<Evidence>()
            var depth = emptyList<Evidence>()
            // Rows of each subject's article in the full encyclopedia: a short one is about something few readers meet.
            var articleRows = emptyMap<String, Int>()
            // A factual lookup ("who won the First World War", "tell me about Japan") also reads the
            // articles it names, up to three ("are lions or tigers heavier" names two). The Simple
            // English article answers alone; the English one adds depth when that collection is read.
            val lookup = SubjectLookup.looksUp(question) && !VenueLookup.isPlaceLookup(question)
            if (attachments.isEmpty() && technicalFocus == null && travelPlace == null && phrasePicked.isEmpty() && ProposalLookup.named(question) == null &&
                (SubjectLookup.leads(question) || lookup)) {
                val wanted = SubjectLookup.subjectsWanted(question, lookup)
                // "heat pump" found; "pump" alone, from the same noun phrase, is the same subject, not a second one.
                val foundRuns = HashSet<Int>()
                for (candidate in SubjectLookup.candidates(question, wanted, lookup)) {
                    if (subjects.size >= wanted) break
                    if (candidate.run in foundRuns) continue
                    val phrase = candidate.text
                    // "Georgia, the country": the qualified title "Georgia (country)" is read by its qualifier.
                    val byTitle = SubjectLookup.overviewOf(phrase, retriever.titled(phrase, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES), question)
                        ?: if (lookup) SubjectLookup.qualifiers(question).firstNotNullOfOrNull { q ->
                            SubjectLookup.overviewOf("$phrase ($q)", retriever.titled("$phrase $q", listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES), question)
                        } else null
                    // "GPS" is the article "Global Positioning System", which lists GPS among its other names.
                    val byAlias = if (byTitle == null && candidate.rest.isEmpty())
                        SubjectLookup.aliasOf(phrase, retriever.aliased(phrase, ALIAS_CANDIDATES))?.let { title ->
                            SubjectLookup.overviewOf(title, retriever.titled(title, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES))
                        } else null
                    // A standard named by what it is: "stealth address" -> ERC-5564 "Stealth Addresses".
                    val byStandard = if (byTitle == null && byAlias == null && candidate.rest.isEmpty() && ' ' in phrase)
                        SubjectLookup.standardOf(phrase, retriever.titled(phrase, ProposalLookup.SUMMARY_SECTIONS, SUBJECT_CANDIDATES)) else null
                    // A subject with no article of its own may head a section of a broader one.
                    val bySection = if (byTitle == null && byAlias == null && byStandard == null && candidate.rest.isEmpty() && SubjectLookup.headsSection(phrase))
                        listOf(phrase, phrase + "s").firstNotNullOfOrNull { name ->
                            SubjectLookup.sectionOf(phrase, retriever.headed(name, listOf(name), SUBJECT_CANDIDATES) +
                                retriever.titled(name, listOf(name), SUBJECT_CANDIDATES))
                        } else null
                    // An article only English Wikipedia holds ("Debit card") is the subject when nothing else is.
                    val byArticle = if (articleDepth && byTitle == null && byAlias == null && byStandard == null && bySection == null)
                        SubjectLookup.overviewOf(phrase, retriever.article(phrase, 2).filter { it.documentId.startsWith("enw-") }, question)
                    else null
                    val found = byTitle ?: byAlias ?: byStandard ?: bySection ?: byArticle
                    // A one-line stub ("A hurricane is a tropical cyclone") or a travel topic's overview
                    // names the fuller article an explanation needs.
                    val fuller = found?.takeIf { SubjectLookup.leads(question) && (SubjectLookup.isStub(it) || it.source.contains("wikivoyage", true)) }?.let { stub ->
                        SubjectLookup.fuller(candidate, stub, listOf(
                            SubjectLookup.overviewOf(phrase, retriever.article(phrase, 2).filter { it.documentId.startsWith("enw-") }, question),
                            SubjectLookup.aliasOf(phrase, retriever.aliased(phrase, ALIAS_CANDIDATES))?.takeUnless { it.equals(phrase, true) }
                                ?.let { title -> SubjectLookup.overviewOf(title, retriever.titled(title, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES)) },
                        ))
                    } ?: found
                    val headings = if (fuller != null && articleDepth && !SubjectLookup.accepts(candidate, fuller))
                        retriever.article(fuller.title.substringBefore(" — "), ArticleDepth.ARTICLE_ROWS).map { it.title } else emptyList()
                    if (fuller != null && SubjectLookup.accepts(candidate, fuller, headings) && subjects.none { it.documentId == fuller.documentId }) {
                        subjects += fuller
                        foundRuns += candidate.run
                    }
                }
                // "could there be life on titan": the search's best passage opens a more specific article ("Life on Titan")
                // than a subject the question's words named ("Life"), and is read in its place.
                searchLead?.let { lead -> subjects.removeAll { SubjectLookup.supersededBy(it, lead, question) } }
                if (articleDepth) subjects += ArticleDepth.supportingSubjects(question, evidence, subjects)
                if (articleDepth) SubjectLookup.relatedWords(question)?.let { words ->
                    val related = subjects.toList().take(2).flatMap { subject ->
                        SubjectLookup.relatedNames(subject.title.substringBefore(" — ")).mapNotNull { name ->
                            SubjectLookup.relatedTitle(name, retriever.headed(name, words, SUBJECT_CANDIDATES, listOf(SubjectLookup.SECTION)))
                        }
                    }.distinctBy { it.documentId }.take(2)
                    subjects += related.filter { found -> subjects.none { it.documentId == found.documentId } }
                }
                val encyclopedic = subjects.filter(ArticleDepth::isEncyclopedia)
                if (articleDepth && encyclopedic.isNotEmpty()) {
                    val articles = encyclopedic.map { it.title.substringBefore(" — ") }.distinct()
                        .associateWith { retriever.article(it, ArticleDepth.ARTICLE_ROWS) }
                    articleRows = articles.mapValues { (_, rows) -> rows.count { it.documentId.startsWith("enw-") || it.documentId.startsWith("wp-") } }
                    depth = ArticleDepth.pickForPrompt(question, encyclopedic, articles,
                        ArticleDepth.budget(prefillSpeed.tokensPerSecond))
                }
                if (subjects.isNotEmpty()) evidence = (subjects + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
            }
            // A follow-up read as a name the earlier answer gave ("Tokyo: how many people live there") reads that
            // name's own article whatever else the question looks like: a city's travel guide has no population,
            // and "how many people live there" names no article of its own.
            if (attachments.isEmpty() && !carriedAhead) topic?.let { FollowUp.namedSubject(question, it.earlier) }?.let { named ->
                if (subjects.none { it.title.substringBefore(" — ").equals(named, ignoreCase = true) }) {
                    // The short Simple English overview and the fuller English one: the first says what the name is,
                    // the second holds its figures (a city's population, a mountain's height).
                    val titled = retriever.titled(named, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES)
                    val overview = SubjectLookup.overviewOf(named, titled, question)
                    val fuller = if (articleDepth) SubjectLookup.overviewOf(named, titled.filter { it.documentId.startsWith("enw-") }, question) else null
                    val read = listOfNotNull(overview, fuller).distinctBy { it.documentId }
                    if (read.isNotEmpty()) {
                        subjects.addAll(0, read)
                        evidence = (read + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    }
                }
            }
            // A traveller's practical question that names no place ("pay in euros or my home currency?")
            // and has no article of its own is answered by the travel topic guides for its topic
            // ("Money"), which no title word names.
            var topicGuideRead = false
            if (attachments.isEmpty() && technicalFocus == null && travelPlace == null && phrasePicked.isEmpty() && subjects.isEmpty() &&
                !VenueLookup.isPlaceLookup(question) && ProposalLookup.named(question) == null && !GuideScope.namesPlace(question, travelNames) &&
                TravelSections.inTravelContext(question)) {
                val guides = TopicGuides.guidesFor(TravelSections.topicsOf(question), question)
                // article(), not sections(): the answering section can lie past a 50-row cap
                // ("Money — Card costs > Dynamic currency conversion" is row 60 of 109).
                val sections = if (guides.isEmpty()) emptyList()
                    else TopicGuides.pickAcross(question, guides, guides.associateWith { retriever.article(it, ArticleDepth.ARTICLE_ROWS) }, TRAVEL_SLOTS)
                if (sections.isNotEmpty()) {
                    evidence = (sections + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    travelPlace = sections.first().title.substringBefore(" — ")
                    travelPicked = sections
                    topicGuideRead = true
                }
            }
            // A question that names no place reads no one destination's guide or listings (see GuideScope).
            if (attachments.isEmpty() && !VenueLookup.isPlaceLookup(question)) {
                evidence = GuideScope.withoutOtherPlaces(question, evidence, travelNames,
                    chosen = travelPicked.map { it.title.substringBefore(" — ") } + listOfNotNull(travelPlace)) { article ->
                    retriever.titled(article, GuideScope.HEADINGS, GuideScope.SECTION_CANDIDATES)
                }
            }
            if (attachments.isEmpty()) {
                val chosen = travelNames + travelPicked.map { it.title.substringBefore(" — ") } + listOfNotNull(travelPlace)
                evidence = GuideScope.withoutUnaskedPlaces(question, evidence, chosen) { article ->
                    retriever.titled(article, GuideScope.HEADINGS, GuideScope.SECTION_CANDIDATES)
                }
            }
            if (includeLibrary && attachments.isEmpty() && phraseAsk == null && travelPlace == null && !placesOnly) {
                EvidenceRelevance.definitionSubject(question)?.let { topic ->
                    if ((evidence + subjects + depth).none {
                            it.title.substringBefore(" — ").equals(topic, ignoreCase = true) && EvidenceRelevance.directlyDefines(it, topic)
                        }) {
                        // Ordinary document-diversity caps can omit the definition
                        // after an abstract and a methods passage from the same paper.
                        val definitions = retriever.titled(topic, listOf(topic), limit = 24)
                            .filter { EvidenceRelevance.directlyDefines(it, topic) }
                        evidence = (definitions + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    }
                }
                for (topic in EvidenceRelevance.conceptualComparisonSubjects(question)) {
                    if ((evidence + subjects + depth).none { EvidenceRelevance.hasOverviewStatement(it, listOf(topic)) }) {
                        val definitions = retriever.titled(topic, listOf(topic), limit = 12)
                            .filter { EvidenceRelevance.hasOverviewStatement(it, listOf(topic)) }
                        evidence = (definitions + evidence).distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                    }
                }
            }
            // The follow-up's earlier passages keep the front slots, ahead of subject overviews added above.
            if (includeLibrary && !placesOnly) evidence = TopicEvidence.lead(evidence, topic, carriedAhead)
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
            // Nothing passed the relevance gate and no subject was found ("why is measles coming back"): an article named
            // by the question's own noun ("Measles") still says what it asks about, which beats an answer from no passage.
            if (includeLibrary && attachments.isEmpty() && evidence.isEmpty() && subjects.isEmpty() && technicalFocus == null &&
                !placesOnly && phraseAsk == null) {
                val found = ArrayList<Evidence>()
                for (name in SubjectLookup.fallbackNames(question)) {
                    if (found.size >= FALLBACK_OVERVIEWS) break
                    SubjectLookup.overviewOf(name, retriever.titled(name, listOf(SubjectLookup.SECTION), SUBJECT_CANDIDATES), question)
                        ?.takeIf { overview -> found.none { it.documentId == overview.documentId } }
                        ?.let { found += it }
                }
                evidence = found
            }
            if (attachments.isEmpty() && technicalFocus == null && (EvidenceRelevance.missingRequestedIdentifier(question, evidence) ||
                travelPlace == null && phrasePicked.isEmpty() && EvidenceRelevance.missingNamedMechanism(question, evidence + depth))) {
                evidence = emptyList()
            }
            if (attachments.isEmpty() && evidence.isEmpty() && technicalFocus == null &&
                (!PromptBuilder.allowsModelExplanation(question) || StudyApplicability.asks(question))) {
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
            // A follow-up is asked as typed, under the conversation it continues; the joined reading only searched.
            val framing = topic?.takeIf { it.earlier.isNotEmpty() && it.asked.isNotBlank() && attachments.isEmpty() }
                ?.let { PromptBuilder.Framing(it.asked, FollowUp.conversation(it.earlier)) }
            // Ranked candidates beyond what fits are cheap; packing keeps only what the budget allows.
            val selection = if (attachments.isNotEmpty()) AttachmentEvidence.select(question, attachments, maxOf(ATTACHMENT_CANDIDATES, attachments.size)) else null
            val packed = if (selection != null) {
                AttachmentEvidence.pack(question, selection, evidence,
                    minOf(8192, inference.contextWindowTokens) - inference.promptOverheadTokens - outputBudget,
                    evidenceTokens = PrefillSpeed.fileEvidenceTokens(prefillSpeed.tokensPerSecond), includeLibrary = includeLibrary)
            } else if (evidence.isEmpty()) {
                PromptBuilder.buildModelOnly(question, framing)
            } else {
                // Subject overviews are packed first; a follow-up's earlier passages go ahead of them, so a phone's
                // short prompt keeps the passage the earlier answer read.
                val leadingSubjects = if (subjects.isEmpty()) subjects
                    else (if (carriedAhead) TopicEvidence.leading(topic) + subjects else subjects + TopicEvidence.leading(topic))
                        .distinctBy { Triple(it.documentId, it.chunkId, it.source) }
                PromptBuilder.buildKnowledge(question, evidence, leadingSubjects, depth, contextTokenBudget,
                    prefillSpeed.tokensPerSecond, framing,
                    searchLead = searchLead?.takeIf { lead -> evidence.any { it.documentId == lead.documentId && it.chunkId == lead.chunkId } })
            }
            onEvidencePacked(packed)
            DeviceEvidenceGap.response(question, packed.sources.map { it.evidence.copy(text = it.excerpt) })?.let { gap ->
                emit(ResearchEvent.Token(gap))
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
            // Exact return-rule lookups must never turn a generic summary or a
            // clipped rule into a model-invented negative finding. This also handles
            // an empty packed result as a normal completion, preserving History UX.
            if (attachments.isEmpty()) (PreciseReferenceAnswer.answer(question, packed)
                ?: PreciseReferenceAnswer.conditionalAnswer(question, packed, subjects))?.let { reference ->
                if (packed.sources.isNotEmpty()) emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
                emit(ResearchEvent.Token(reference))
                val audit = AnswerText.citationAudit(reference, packed.sources.size)
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = audit.citedSourceIds,
                    hasUnmappedCitation = audit.hasUnmappedCitation,
                )))
                return@flow
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
            // Model-first: the model answers what it knows, labelled unverified, with the selected passages listed below
            // it. Emergencies, places, files, phrases, calculations and source-only requests keep their own routes.
            // The loaded model may answer from its own knowledge (ModelOffer.knowledgeFirst); a fixed note count overrides it.
            // A subject few readers meet is read from the sources as cited notes: a small model's memory of famous
            // subjects beats reading about them, its memory of obscure ones is where it invents (see SourceRouter).
            val routed = if (!sourceRouter) 0 else {
                val mentions = SourceRouter.titlesToCount(subjects, packed.sources).associateWith { retriever.mentions(it, SourceRouter.MENTION_CAP) }
                SourceRouter.notes(question, subjects, packed.sources, articleRows, mentions, readImportedCollections,
                    factLookups = inference.thinkingBudget <= 0)
            }
            val firstAidQuestion = attachments.isEmpty() && xyz.fieldatlas.emergency.GuideAnswerCheck.firstAidQuestion(question)
            val firstAidHere = firstAidQuestion && !inference.writesFirstAid && smallModelFirstAid != "memory"
            if (firstAidHere && smallModelFirstAid == "fallback") {
                // No saved guide covers it and the model is not trusted to write first aid from memory: the call line, a
                // plain statement of the gap, and only the basics the saved CPR, recovery-position and anaphylaxis guides support.
                val swallowed = xyz.fieldatlas.emergency.GuideAnswerCheck.swallowedSomething(question)
                val basics = (listOf("cpr", "recovery-position", "anaphylaxis") + (if (swallowed) listOf("poisoning-steps") else emptyList()))
                    .mapNotNull { id -> emergencyGuides().firstOrNull { it.id == id } }
                emit(ResearchEvent.Sources(basics.map { xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(it) }))
                emit(ResearchEvent.Token(FIRST_AID_GAP + (if (swallowed && basics.size == 4) POISON_LINE else "")))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = basics.indices.map { "S${it + 1}" }.toSet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            val knowledgeNotes: Int? = when {
                firstAidHere && smallModelFirstAid == "cited" -> null
                // A fixed count of zero (the desktop's knowledge-first switch) still lets the router read a rare subject.
                modelFirstNotes != null -> if (modelFirstNotes == 0) routed else modelFirstNotes
                inference.knowledgeFirst -> routed
                else -> null
            }
            if (knowledgeNotes != null && attachments.isEmpty() && phraseAsk == null && !VenueLookup.asksForBusinesses(question) &&
                !CitedExplanation.explicitlyRequestsQuotes(question) && !PromptBuilder.requiresSavedSources(question)) {
                // A model chosen for it reasons first: Gemma 4 26B got a time-zone sum wrong straight out and right
                // after reasoning, and scored 82.5% against 77.5% of web-and-AI answers on set 33 (two blind rounds).
                val thinking = inference.thinkingBudget.coerceAtLeast(0)
                val first = PromptBuilder.buildKnowledgeFirst(question, packed, knowledgeNotes, framing, thinking = thinking > 0, cite = knowledgeNotes > 0,
                    firstAidCare = firstAidQuestion && (inference.writesFirstAid || firstAidHere && smallModelFirstAid == "careful"))
                onEvidencePacked(first)
                if (packed.sources.isNotEmpty()) emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
                emit(ResearchEvent.Token(""))
                val written = StringBuilder()
                var tokens = 0
                var firstAt: Long? = null
                val generationStart = monotonicMillis()
                suspend fun write(answer: Flow<String>) = answer.collect { token ->
                    if (firstAt == null) firstAt = monotonicMillis()
                    tokens++
                    written.append(token)
                    emit(ResearchEvent.Token(AnswerText.mixed(AnswerChecks.removeDoses(
                        AnswerChecks.removeOnlineAdvice(written.toString(), question), question, emptyList()), emptyList()), replace = true))
                }
                write(if (thinking > 0) inference.think(first.prompt, outputBudget + thinking, savedPrefixChars = first.savedPrefixChars)
                    else inference.generate(first.prompt, outputBudget, savedPrefixChars = first.savedPrefixChars))
                if (thinking > 0 && (AnswerText.visible(written.toString()).isBlank() || inference.lastHitLimit)) {
                    // The reasoning used the whole budget, so no answer followed or it stopped mid-way: Gemma 4 26B left 5
                    // of the 16 set 42 questions it read notes for empty, and cut a set 33 answer after "Key facts:".
                    // Asked again with the thinking turn closed, it answers at once and in full.
                    written.clear()
                    val direct = PromptBuilder.buildKnowledgeFirst(question, packed, knowledgeNotes, framing, thinking = false,
                        cite = knowledgeNotes > 0, shaped = false, firstAidCare = firstAidQuestion && (inference.writesFirstAid || firstAidHere && smallModelFirstAid == "careful"))
                    write(inference.generate(direct.prompt, outputBudget, savedPrefixChars = direct.savedPrefixChars))
                }
                firstAt?.let { at -> prefillSpeed.tokensPerSecond = PrefillSpeed.update(prefillSpeed.tokensPerSecond, TokenEstimate.of(first.prompt), at - generationStart) }
                val raw = AnswerChecks.removeDoses(AnswerChecks.removeOfflineNarration(
                    AnswerChecks.removeOnlineAdvice(written.toString(), question), question), question, emptyList())
                // With no notes the passages never reached the model: the screen lists them under the answer as the sources
                // to check it against. A sentence whose figure one of them states too is marked with that passage; where one
                // gives a different figure for what was asked, its sentence is quoted under the answer.
                // Read as notes, the passages' facts come back cited by the model; those citations are kept and audited.
                val citation = Regex("\\[S\\d+]")
                val explained = if (knowledgeNotes == 0) AnswerText.mixed(raw, emptyList())
                    else "## " + AnswerText.MODEL_LABEL + "\n\n" + AnswerText.finalized(raw, first.sources.size).trim()
                val cited = citation.findAll(explained).count()
                val marked = SavedFactCheck.marks(question, explained, packed.sources)
                val markCount = citation.findAll(marked).count() - cited
                // The call line for first aid no guide matched is added above this route, in research().
                val shown = listOfNotNull(marked.trimEnd(), SavedFactCheck.note(question, raw, packed.sources),
                    SavedFactCheck.knowledgeNote(markCount, packed.sources.size, cited)).joinToString("\n\n")
                emit(ResearchEvent.Token(shown, replace = true))
                val audit = AnswerText.citationAudit(shown, packed.sources.size)
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = firstAt?.let { elapsed(startedAt, it) },
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = tokens,
                    citedSourceIds = audit.citedSourceIds,
                    hasUnmappedCitation = audit.hasUnmappedCitation,
                    promptMillis = firstAt?.let { it - generationStart },
                )))
                return@flow
            }
            if (packed.sources.isEmpty()) {
                if (evidence.isNotEmpty()) {
                    emit(ResearchEvent.InsufficientEvidence("The context budget could not fit any evidence"))
                    return@flow
                }
            } else {
                emit(ResearchEvent.Sources(packed.sources.map { it.evidence }))
            }
            // Explanations are generated from selected evidence. Original passages
            // remain in Sources, not automatically prepended/appended to the answer.
            // Explicit quotations and the earlier exact-rule/calculation routes keep
            // their literal behavior. Never expose an unvalidated streaming draft.
            // The reply a rejected cited draft would have had, kept in case no route below answers.
            var unconfirmed: String? = null
            if (packed.sources.isNotEmpty() && phraseAsk == null && !CitedExplanation.explicitlyRequestsQuotes(question)) {
                val explanation = PromptBuilder.explain(question, packed)
                onEvidencePacked(explanation)
                emit(ResearchEvent.Token(""))
                val draft = StringBuilder()
                var tokens = 0
                var first: Long? = null
                val generationStart = monotonicMillis()
                var finished = CitedExplanation.UNAVAILABLE
                var partial = false
                // A model that fails here (out of memory, a native error) leaves the draft unconfirmed: the
                // question still gets the saved-text routes below, as it did before cited explanations.
                try {
                inference.explainEvidence(explanation.prompt, CitedExplanation.outputBudget(question, outputBudget),
                    savedPrefixChars = explanation.savedPrefixChars).collect { token ->
                    if (first == null) first = monotonicMillis()
                    tokens++
                    draft.append(token)
                    emit(ResearchEvent.Drafting(tokens))
                }
                // Only the part of the prompt the engine read counts: a restored evidence block costs no reading time.
                first?.let { at ->
                    val read = if (inference.reusedPromptTokens > 0) explanation.prompt.substring(explanation.savedPrefixChars) else explanation.prompt
                    prefillSpeed.tokensPerSecond = PrefillSpeed.update(prefillSpeed.tokensPerSecond, TokenEstimate.of(read), at - generationStart)
                }
                val selectedEvidence = packed.sources.map { it.evidence.copy(text = it.excerpt) }
                val dosed = AnswerChecks.removeDoses(AnswerChecks.removeOfflineNarration(
                    AnswerChecks.removeOnlineAdvice(draft.toString(), question), question), question, selectedEvidence)
                // The app's own notes ("amounts of medicine are left out…") carry no citation; they are set aside
                // while the draft is checked and reviewed, and added back to an answer that passes.
                val (draftBody, draftNotes) = AnswerChecks.withoutAppNotes(dosed)
                var notes = draftNotes
                val (cleaned, draftGap) = CitedExplanation.withoutGapStatements(draftBody)
                // The same repairs as every other answer from saved sources: a citation moved to the passage
                // its details come from, and a 12-hour time that disagrees with the 24-hour time stated.
                fun repairedDraft(text: String) = if (text == CitedExplanation.UNAVAILABLE || text == CitedExplanation.GAP) text
                    else AnswerChecks.addTwelveHour(AnswerChecks.repairTimes(AnswerChecks.repairCitations(text, selectedEvidence), selectedEvidence), question)
                finished = repairedDraft(CitedExplanation.finish(cleaned, packed.sources.size))
                partial = draftGap
                if (CitedExplanation.hasScopeRisk(finished, packed) ||
                    CitedExplanation.hasWrongComparisonCitation(finished, question, packed)) finished = CitedExplanation.UNAVAILABLE
                // An answer from the person's own files is not sent to the model's review: on 24 sealed file questions
                // (set 31) the review rejected most correct drafts ("your T-score is -2.7 [S1]"), and the file is in
                // front of the person to check. The citation, scope and dose checks above still apply.
                val reviewed = attachments.isEmpty() || includeLibrary
                if (reviewed && finished != CitedExplanation.UNAVAILABLE && finished != CitedExplanation.GAP) {
                    val review = StringBuilder()
                    emit(ResearchEvent.Drafting(tokens, checking = true))
                    inference.reviewEvidence(CitedExplanation.reviewPrompt(finished, packed)).collect { review.append(it) }
                    // Model review reduces risk but is not independent fact checking.
                    // Fail closed on an ambiguous, truncated or negative verdict.
                    if (!CitedExplanation.reviewPassed(review.toString())) finished = CitedExplanation.UNAVAILABLE
                }
                if (finished == CitedExplanation.UNAVAILABLE) {
                    // One bounded repair attempt; never reveal the rejected draft.
                    val retry = StringBuilder()
                    val repairShape = if (packed.sources.size > 1 && EvidenceRelevance.conceptualComparisonSubjects(question).size == 2)
                        "Give one short explanatory sentence for each supported subject. Cite each sentence using the source that actually supports that subject."
                        else "Give just one short explanatory sentence."
                    val retryPrompt = explanation.prompt.substringBeforeLast("\n\nANSWER:") +
                        CitedExplanation.repairInstruction(cleaned, question, packed, repairShape)
                    inference.explainEvidence(retryPrompt, minOf(outputBudget, 256), explanation.savedPrefixChars).collect {
                        tokens++; retry.append(it); emit(ResearchEvent.Drafting(tokens))
                    }
                    val (retryBody, retryNotes) = AnswerChecks.withoutAppNotes(AnswerChecks.removeDoses(AnswerChecks.removeOfflineNarration(
                        AnswerChecks.removeOnlineAdvice(retry.toString(), question), question), question, selectedEvidence))
                    val (retried, repairGap) = CitedExplanation.withoutGapStatements(retryBody)
                    val repaired = repairedDraft(CitedExplanation.finish(retried, packed.sources.size))
                    if (repaired == CitedExplanation.GAP) finished = repaired
                    else if (repaired != CitedExplanation.UNAVAILABLE && !CitedExplanation.hasScopeRisk(repaired, packed) &&
                        !CitedExplanation.hasWrongComparisonCitation(repaired, question, packed)) {
                        val review = StringBuilder()
                        if (reviewed) {
                            emit(ResearchEvent.Drafting(tokens, checking = true))
                            inference.reviewEvidence(CitedExplanation.reviewPrompt(repaired, packed)).collect { review.append(it) }
                        }
                        if (!reviewed || CitedExplanation.reviewPassed(review.toString())) { finished = repaired; partial = repairGap; notes = retryNotes }
                    }
                }
                if (finished != CitedExplanation.UNAVAILABLE && finished != CitedExplanation.GAP && notes.isNotEmpty())
                    finished = finished.trimEnd() + notes.joinToString("") { "\n\n$it" }
                // Only an amount of medicine was taken out and the run ends here (a file, or saved sources only): say
                // why and where to look. Otherwise the routes below answer, each with its own dose check.
                val endsHere = attachments.isNotEmpty() || CitedExplanation.explicitlyRequestsQuotes(question) || PromptBuilder.requiresSavedSources(question)
                if (finished == CitedExplanation.UNAVAILABLE && endsHere && draftNotes.isNotEmpty()) finished = draftNotes.joinToString("\n\n")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    finished = CitedExplanation.UNAVAILABLE
                }
                val missing = EvidenceRelevance.missingComparisonSubjects(question, packed.sources.map { it.evidence.copy(text = it.excerpt) })
                val answered = finished != CitedExplanation.UNAVAILABLE && finished != CitedExplanation.GAP
                // A draft the review could not confirm is never shown. The question still gets the
                // routes below: a travel guide's own section, a phrasebook line, a source lead, or a
                // model explanation labelled as unverified. Withholding every unconfirmed draft left
                // 25 of 60 fresh questions with no answer at all (13 October 2026).
                // A source-only request ("using only saved sources") still stops here: the routes
                // below can add model knowledge, which that request rules out.
                if (answered || attachments.isNotEmpty() || CitedExplanation.explicitlyRequestsQuotes(question) ||
                    PromptBuilder.requiresSavedSources(question)) {
                val explanationWithGap = when {
                    !answered -> finished
                    missing.isNotEmpty() -> finished + "\n\n_The selected excerpts do not cover ${missing.joinToString(", ")}; this is not a complete comparison._"
                    partial -> finished + "\n\n" + CitedExplanation.PARTIAL
                    else -> finished
                }.let { text ->
                    val conflict = if (answered) ClockConflict.note(question, finished, packed.sources) else null
                    if (conflict == null) text else text + "\n\n" + conflict
                }.let { text ->
                    // A medicine in the person's own file: its lines quoted under the answer, whether or not one was
                    // written, and the leaflet note pointed at them instead.
                    val medicine = if (attachments.isNotEmpty()) FileMedicineLines.note(question, if (answered) finished else "", attachments) else null
                    if (medicine == null) text
                    else text.replace(AnswerChecks.DOSE_NOTE, FileMedicineLines.DOSE_NOTE).trimEnd() + "\n\n" + medicine
                }
                val coverage = selection?.let { AttachmentEvidence.coverageSummary(it, packed.sources.map { s -> s.evidence }, attachments) }
                val answer = if (coverage == null) explanationWithGap else explanationWithGap + "\n\n_" + coverage + "_"
                val audit = AnswerText.citationAudit(answer, packed.sources.size)
                emit(ResearchEvent.Token(answer, replace = true))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = first?.let { elapsed(startedAt, it) },
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = tokens,
                    citedSourceIds = audit.citedSourceIds,
                    hasUnmappedCitation = AnswerText.citationAudit(draft.toString(), packed.sources.size).hasUnmappedCitation,
                    promptMillis = first?.let { it - generationStart },
                )))
                return@flow
                }
                unconfirmed = finished
                emit(ResearchEvent.Drafting(0))
                emit(ResearchEvent.Token("", replace = true))
            }
            if (attachments.isEmpty()) PartialComparison.answer(question, packed)?.let { partial ->
                emit(ResearchEvent.Token(partial))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = AnswerText.citationAudit(partial, packed.sources.size).citedSourceIds,
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            if (attachments.isEmpty()) SavedReferenceAnswer.answer(question, packed, subjects)?.let { reference ->
                emit(ResearchEvent.Token(reference))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = AnswerText.citationAudit(reference, packed.sources.size).citedSourceIds,
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            // A verbatim overview opening is only composed with mixed answers, whose stream
            // already replaces the whole answer on every token.
            val phraseSources = packed.sources.filter { s -> phrasePicked.any { it.chunkId == s.evidence.chunkId && it.documentId == s.evidence.documentId } }
            val phraseQuote = if (phraseAsk != null && phraseSources.isNotEmpty()) Phrasebook.lead(phraseAsk, phraseSources) else null
            // "Vegetarian" for "vegan" is the phrasebook's closest line, not the phrase asked for.
            val phraseNear = if (phraseAsk != null && phraseQuote != null) Phrasebook.nearNote(phraseAsk, phraseSources) else null
            val referenceLead = if (attachments.isEmpty() && packed.mixedAnswer && phraseQuote == null)
                SourceLead.select(question, packed.sources) ?: ProposalLookup.lead(question, packed.sources)
                    ?: SubjectLookup.lead(question, subjects, packed.sources, depth)
            else null
            val guideQuote = if (attachments.isEmpty() && packed.mixedAnswer && referenceLead == null && phraseQuote == null)
                TravelSections.lead(question, travelPlace, packed.sources.filter { s -> travelPicked.any { it.chunkId == s.evidence.chunkId && it.documentId == s.evidence.documentId } }, airportGuide, travelNames, travelThings)
            else null
            val lead = phraseQuote ?: referenceLead ?: guideQuote
            val leadText = lead?.render()?.let { text -> phraseNear?.let { "$text\n\n_${it}_" } ?: text }
            // A phrasebook line that says exactly what was asked is the answer, in the script and
            // pronunciation the phrasebook prints; the model would only add guesses.
            if (phraseQuote != null && phraseNear == null && phraseAsk != null && Phrasebook.answersAlone(phraseAsk, phraseSources)) {
                emit(ResearchEvent.Token(phraseQuote.render() + "\n\n_" + PHRASEBOOK_QUOTE_NOTE + "_"))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = setOf(phraseQuote.citationId) + phraseQuote.more.map { it.first },
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            // A travel guide's own section is advice written for this need, so quoted at length it
            // is the answer. The 2B model's additions below a guide quote carried most travel
            // errors ("use designated crossings like Beer Street" after Hanoi's traffic advice).
            // An encyclopedia definition is not a complete answer to how or why; those continue
            // to the conservative source-excerpt finish below. Questions with numbers may still
            // need a calculated answer worked out from them
            // ("how much water for a 6-hour hike?") that no quoted passage gives, and a comparison,
            // which one guide's section about one side does not make ("hostels compared with guesthouses").
            // A topic guide read for a choice or an explanation ("why do my ears pop on planes") leads
            // the model rather than replacing it: it was found by topic, not by the question's own words.
            // A quote stands alone only when the section was read for the part the question asks
            // ("Entry requirements" for a visa), never for a topic guide found by its topic alone: a
            // parking paragraph quoted for a visa question answers nothing, while the model under a
            // lead still can.
            if (guideQuote != null && !asksCalculation(question) && !comparisonWords.containsMatchIn(question) &&
                !SubjectLookup.isChoice(question) && !topicGuideRead && travelPlace != null &&
                TravelSections.coversPart(question, travelPlace!!, travelPicked, travelJourney, travelNames, travelThings) &&
                TravelSections.coversEveryClause(question, guideQuote, travelNames, travelThings)) {
                val quoted = SourceLead.extend(guideQuote, packed.sources)
                emit(ResearchEvent.Token(quoted.render() + "\n\n_" + GUIDE_QUOTE_NOTE + "_"))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = setOf(quoted.citationId) + quoted.more.map { it.first },
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            // Ordinary source-backed explanations keep the selected originals. A
            // model paraphrase can strengthen an observed condition into an operating
            // rule even when its attribution is labelled unverified. Specialized routes
            // above and model-only/attachment/phrase/guide paths keep their behavior.
            if (attachments.isEmpty() && phraseAsk == null && travelPlace == null && !topicGuideRead) {
                GroundedKnowledgeAnswer.answer(question, packed)?.let { grounded ->
                    emit(ResearchEvent.Token(grounded))
                    val audit = AnswerText.citationAudit(grounded, packed.sources.size)
                    emit(ResearchEvent.Complete(ResearchMetrics(
                        retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                        timeToFirstTokenMillis = elapsed(startedAt, monotonicMillis()),
                        totalMillis = elapsed(startedAt, monotonicMillis()),
                        generatedTokenCount = 0,
                        citedSourceIds = audit.citedSourceIds,
                        hasUnmappedCitation = audit.hasUnmappedCitation,
                    )))
                    return@flow
                }
            }
            // After a rejected draft, only a mixed answer may be generated again: its model part is
            // labelled unverified. A strictly cited answer (places, for one) would repeat the
            // rejected claim with citations that look checked, so it stops with the gap instead.
            unconfirmed?.takeIf { !packed.mixedAnswer }?.let { reply ->
                emit(ResearchEvent.Token(reply, replace = true))
                emit(ResearchEvent.Complete(ResearchMetrics(
                    retrievalMillis = elapsed(startedAt, retrievalFinishedAt),
                    timeToFirstTokenMillis = null,
                    totalMillis = elapsed(startedAt, monotonicMillis()),
                    generatedTokenCount = 0,
                    citedSourceIds = emptySet(),
                    hasUnmappedCitation = false,
                )))
                return@flow
            }
            if (leadText != null) {
                emit(ResearchEvent.Lead(leadText))
            } else {
                // Field Atlas: an empty token flips the UI into the "writing" phase before the
                // first real token arrives, so prefill progress can show as tokens read/written.
                emit(ResearchEvent.Token(""))
            }
            fun compose(modelText: String): String =
                listOfNotNull(leadText, modelText.takeIf(String::isNotBlank)).joinToString("\n\n")

            val attributionEvidence = packed.sources.map { it.evidence.copy(text = it.excerpt) }
            var firstTokenAt: Long? = null
            var promptTokensRead: Int? = null
            var generatedTokenCount = 0
            val output = StringBuilder()
            var generationFailed = false
            val generateStartedAt = monotonicMillis()
            try {
                inference.generate(packed.prompt, outputBudget, savedPrefixChars = packed.savedPrefixChars).collect { token ->
                    if (firstTokenAt == null) {
                        firstTokenAt = monotonicMillis()
                        // The engine's own count; read before it clears its progress.
                        promptTokensRead = inference.promptProgress.value?.total?.takeIf { it > 0 }
                    }
                    generatedTokenCount++
                    output.append(token)
                    // What streams passes the same removals as the final text below (online advice always, a dose
                    // in the model's own words of a mixed answer), so neither is on screen even before the final
                    // check, and a cancelled run keeps the checked text. The whole
                    // text is sent each time, under the lead, so model words never run into a quoted line.
                    val streamed = AnswerChecks.removeOnlineAdvice(output.toString(), question)
                    emit(ResearchEvent.Token(compose(if (packed.mixedAnswer)
                        AnswerText.mixed(AnswerChecks.removeDoses(streamed, question, attributionEvidence), attributionEvidence)
                    else streamed), replace = true))
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
            // The labelled explanation keeps to the excerpts. When none of them is about the question, the model says so
            // and stops: a swallowed button battery got "No excerpt supports …" above a stock-car race report. Asked again
            // without them, it answers from its own knowledge, still labelled unverified, and the unrelated excerpts are
            // no longer listed as the answer's sources.
            var answerPacked = packed
            var answerEvidence = attributionEvidence
            if (packed.mixedAnswer && packed.sources.isNotEmpty() && lead == null && !generationFailed && attachments.isEmpty() &&
                ExcerptRefusal.opensWith(output.toString())) {
                val knowledge = PromptBuilder.buildModelOnly(question, framing)
                val again = StringBuilder()
                try {
                    emit(ResearchEvent.Token("", replace = true))
                    inference.generate(knowledge.prompt, outputBudget, savedPrefixChars = knowledge.savedPrefixChars).collect { token ->
                        generatedTokenCount++
                        again.append(token)
                        emit(ResearchEvent.Token(AnswerText.mixed(AnswerChecks.removeDoses(
                            AnswerChecks.removeOnlineAdvice(again.toString(), question), question, emptyList()), emptyList()), replace = true))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // The refusal is still shown: a failed second attempt must not leave the screen empty.
                    again.setLength(0)
                }
                if (again.isNotBlank() && !ExcerptRefusal.opensWith(again.toString())) {
                    output.setLength(0)
                    output.append(again)
                    answerPacked = knowledge
                    answerEvidence = emptyList()
                    emit(ResearchEvent.Sources(emptyList()))
                }
            }
            val finishedAt = monotonicMillis()
            // Learn this phone's reading speed from the wait before the first token, counting only the
            // part of the prompt the engine read: restored instructions cost no reading time.
            firstTokenAt?.let { first ->
                val read = if (inference.reusedPromptTokens > 0) packed.prompt.substring(packed.savedPrefixChars) else packed.prompt
                prefillSpeed.tokensPerSecond = PrefillSpeed.update(prefillSpeed.tokensPerSecond,
                    TokenEstimate.of(read), first - generateStartedAt)
            }
            // Evidence-only answers: move a citation to the source its details actually come from
            // and correct a 12-hour time that disagrees with the 24-hour time the source states.
            // The person is offline: advice to search the web, visit a site or use an app is removed.
            val raw = AnswerChecks.removeOfflineNarration(AnswerChecks.removeOnlineAdvice(output.toString(), question), question)
                // The model's own explanation never carries an amount of medicine (see removeDoses).
                .let { if (packed.mixedAnswer) AnswerChecks.removeDoses(it, question, answerEvidence) else it }
            val checked = if (packed.mixedAnswer) raw
                else AnswerChecks.addTwelveHour(
                    AnswerChecks.repairTimes(AnswerChecks.repairCitations(raw, answerEvidence), answerEvidence),
                    question,
                )
                .let { text ->
                    if (packed.mixedAnswer || generationFailed) text
                    else AnswerChecks.exceptionNote(question, answerEvidence)?.let { "$text\n\n_${it}_" } ?: text
                }
                .let { text ->
                    // Written by the app, not the model, so a partial read is always disclosed.
                    val coverage = selection?.let { AttachmentEvidence.coverageSummary(it, packed.sources.map { s -> s.evidence }, attachments) }
                    if (coverage == null || generationFailed) text else "$text\n\n_${coverage}_"
                }
            // The final checked text, under its lead.
            emit(ResearchEvent.Token(compose(if (packed.mixedAnswer) AnswerText.mixed(raw, answerEvidence) else checked), replace = true))
            val modelText = if (packed.mixedAnswer) AnswerText.mixed(raw, answerEvidence) else checked
            val composed = when {
                generationFailed -> compose(modelText) + "\n\n_The model explanation could not be completed._"
                else -> compose(modelText)
            }
            val attributed = SavedEvidenceAppendix.append(composed, answerPacked)
            if (generationFailed || attributed != composed) emit(ResearchEvent.Token(attributed, replace = true))
            val citations = AnswerText.citationAudit(attributed, answerPacked.sources.size)
            val rawCitations = AnswerText.citationAudit(output.toString(), answerPacked.sources.size)
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

    /**
     * The saved guide for [question], read as the follow-up it may be. A follow-up is first matched as typed: "tornado on
     * the ground and we're in a trailer" after a long question about Oklahoma City is its own emergency, though the
     * joined reading is too long to count as one. Otherwise the joined reading still matches the earlier emergency, and
     * that guide is shown again only if no answer in the topic already showed it: "is it ok to use coconut oil on it?"
     * after the sunburn guide asks something the guide does not say, and so does "does aloe help?" after that answer,
     * though the joined reading of every later follow-up still starts with the sunburn.
     */
    private fun guideFor(question: String, topic: TopicEvidence.Carried?): xyz.fieldatlas.emergency.EmergencyGuide? {
        val guides = emergencyGuides()
        if (topic != null) xyz.fieldatlas.emergency.EmergencyGuideMatch.match(topic.asked, guides)?.let {
            return xyz.fieldatlas.emergency.EmergencyGuideMatch.shown(it, guides)
        }
        // A model with no knowledge route (the 2B) shows the guide a learning question is about rather than paraphrase a
        // passage: the 4 GB phone answered "how many compressions a minute" from a research paper (23 Oct). A first-aid
        // question no situation rule matches gets its topic's guide on any model that does not write first aid, instead of
        // the no-guide reply: "how do I treat a minor burn at home" got CPR and anaphylaxis basics while the burns guide
        // sat unused (23 Oct).
        val guide = (xyz.fieldatlas.emergency.EmergencyGuideMatch.match(question, guides)
            ?: if (!inference.knowledgeFirst && xyz.fieldatlas.emergency.GuideAnswerCheck.learning(question) ||
                    !inference.writesFirstAid && xyz.fieldatlas.emergency.GuideAnswerCheck.firstAidQuestion(question))
                xyz.fieldatlas.emergency.EmergencyGuideMatch.learningGuide(question, guides) else null)
            ?.let { xyz.fieldatlas.emergency.EmergencyGuideMatch.shown(it, guides) } ?: return null
        val shown = xyz.fieldatlas.emergency.EmergencyGuideMatch.evidence(guide).documentId
        return guide.takeIf { topic == null || (topic.sources + topic.earlier.flatMap { it.sources }).none { it.documentId == shown } }
    }

    /**
     * The places a question asks for around a landmark it names, nearest first, or null when it names none, no saved
     * listing gives the landmark a position, or nothing is mapped around it: the city lookup then answers as before.
     */
    private suspend fun landmarkAnswer(question: String): VenueLookup.Result? {
        val categories = VenueLookup.nearbyCategories(question)
        if (categories.isEmpty()) return null
        val landmark = VenueLookup.landmark(question) ?: return null
        // A long listing splits into passages and its position is in the last one ("Rome/Colosseo — Colosseum");
        // search keeps one passage a listing, the title lookup returns them all.
        val found = (retriever.titled(landmark.city ?: landmark.name, listOf(landmark.name), LANDMARK_CANDIDATES) +
            retriever.search(listOfNotNull(landmark.name, landmark.city).joinToString(" "), LANDMARK_CANDIDATES) {})
            .distinctBy { it.chunkId }
        val point = VenueLookup.landmarkPoint(landmark, found) ?: return null
        val asked = VenueLookup.withoutLandmark(question, landmark)
        val radii = if (categories.any { it in FAR_REACH }) NEARBY_RADII_KM + FAR_RADII_KM else NEARBY_RADII_KM
        var best: VenueLookup.Result? = null
        for (radius in radii) {
            val result = VenueLookup.nearbyAnswer(asked, retriever.nearby(point, radius, NEARBY_CANDIDATES, categories), radius, anchor = landmark)
            best = result
            if (result.sources.size >= NEARBY_ENOUGH && result.openNow != 0) break
        }
        return best?.takeIf { it.sources.isNotEmpty() }
    }

    private fun elapsed(start: Long, end: Long): Long = (end - start).coerceAtLeast(0)

    private companion object {
        /** Opens every first-aid answer a model writes: the call is the app's, never left to the model. */
        /** The fallback for first aid no saved guide covers (measurement): no treatment steps the sources do not support. */
        const val FIRST_AID_GAP = "No first-aid guide saved on this phone covers this situation, so this answer gives no treatment steps for it. " +
            "While you get help:\n\n" +
            "- If the person is not breathing normally, start CPR: chest compressions with the heel of one hand on the centre of the chest [S1].\n" +
            "- If they are unconscious but breathing, turn them onto their side in the recovery position [S2].\n" +
            "- If they have an adrenaline auto-injector for a severe allergic reaction, give the injection right away [S3].\n" +
            "- Stay with them and watch their breathing until help arrives."
        // The poisoning guide (NHS): "Call 999 or go to A&E if … someone else may have swallowed … something harmful" and
        // "Symptoms may take a few hours or days to start".
        const val POISON_LINE = "\n- If they may have swallowed something harmful, call your local emergency number or go to hospital now, even if they " +
            "seem fine: symptoms may take a few hours or days to start [S4]."
        const val CALL_FIRST = "**If this is an emergency, call your local emergency number first** (112 works on mobile phones in many countries).\n\n"
        const val KEYWORD_SEED = 17
        const val ATTACHMENT_CANDIDATES = 16
        const val MUSEUM_SLOTS = 3
        // A proposal's summary can run over two chunks, or come as a Simple Summary plus an Abstract.
        const val PROPOSAL_CANDIDATES = 8
        const val PROPOSAL_SLOTS = 2
        // The guide sections of the place a travel question names: the closest two of up to 30.
        const val TRAVEL_PLACES = 3
        const val TRAVEL_CANDIDATES = 50
        const val TRAVEL_SLOTS = 2
        const val SUBJECT_CANDIDATES = 10
        const val FALLBACK_OVERVIEWS = 2
        const val TOPIC_CANDIDATES = 50
        const val TOPIC_NAMES = 12
        const val AIRPORT_CANDIDATES = 20
        // A number other than a year ("in 2026").
        private val quantity = Regex("(?<![\\p{L}\\p{N}])(?!(?:19|20)\\d\\d(?![\\p{N}]))\\d+(?:[.,]\\d+)?")
        internal fun asksCalculation(question: String) = quantity.containsMatchIn(question)
        const val GUIDE_QUOTE_NOTE = "Quoted from the saved travel guide, without a model summary; tap the citation to read the whole section."
        const val PHRASEBOOK_QUOTE_NOTE = "Quoted from the saved Wikivoyage phrasebook, without a model summary; tap the citation to see the whole list."
        // A phrasebook's phrase lists: the passages holding the asked line, then its basics.
        const val PHRASEBOOK_SLOTS = 3
        // Short-titled articles that merely mention an acronym come first; the alias line decides.
        const val ALIAS_CANDIDATES = 50
        val comparisonWords = Regex("(?i)\\b(difference|differ|different|compare|compared|comparison|versus|vs\\.?)\\b")
        /** City scale: "the city I'm in" and "near me" both fit; the answer states distances. */
        const val NEARBY_RADIUS_KM = 15.0
        val NEARBY_RADII_KM = listOf(2.0, 5.0, NEARBY_RADIUS_KM)
        val FAR_RADII_KM = listOf(50.0, 100.0)
        val BEST_RADII_KM = listOf(10.0, NEARBY_RADIUS_KM)
        // Wider than 5 km a dense map pack is thousands of reads for a town name the 5 km circle gives.
        val WHERE_RADII_KM = listOf(1.0, 5.0)
        val FAR_REACH = setOf("Health", "Safety", "Water")
        const val NEARBY_ENOUGH = 6
        const val NEARBY_CANDIDATES = 200
        /** Passages searched for a landmark's own listing; its position can sit in a later passage of it. */
        const val LANDMARK_CANDIDATES = 30
    }
}

/** The note above the official guide shown while a trusted model writes its answer; the preview shows its head, not its tail. */
object GuideStandIn {
    const val NOTE = "_The official guide, while an answer for your situation is written:_"
}
