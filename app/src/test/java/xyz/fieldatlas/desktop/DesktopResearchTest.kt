package xyz.fieldatlas.desktop

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.fieldatlas.research.*
import xyz.fieldatlas.emergency.EmergencyNumbers
import xyz.fieldatlas.ui.AppContainer
import xyz.fieldatlas.assets.PackEmbedding
import xyz.fieldatlas.assets.PackDiscovery
import xyz.fieldatlas.attachments.AttachmentKind
import xyz.fieldatlas.attachments.AttachmentPage
import xyz.fieldatlas.attachments.ExtractedAttachment

/** Opt-in real-model run, NOT an accuracy assertion or part of the normal unit suite. */
class DesktopResearchTest {
    @Test fun recordRealModelAnswers() = runBlocking {
        val configPath = System.getenv("FIELDATLAS_DESKTOP_CONFIG")
        assumeTrue("Desktop evaluation is opt-in", !configPath.isNullOrBlank())
        val config = Json.parseToJsonElement(File(configPath!!).readText()).jsonObject
        val manualEvidence = config["manualEvidence"]?.jsonObject
        // Text files attached to a question go through the same attachment path as the phone (no OCR).
        val attachedFiles = config["attachments"]?.jsonObject
        // A follow-up reads the sources of the answer it follows, as on the phone.
        val followUps = config["followUps"]?.jsonObject
        val answeredSources = mutableMapOf<Pair<Int, String>, List<Evidence>>()
        val output = File(config.getValue("output").jsonPrimitive.content)
        val databases = config.getValue("databases").jsonArray.map { File(it.jsonPrimitive.content).canonicalFile }
        // Fail explicitly rather than allowing the production retriever's recovery path to
        // silently turn a broken database into a supposedly successful model-only test.
        databases.forEach { KnowledgeDatabase.open(it).use { } }
        val model = File(config.getValue("model").jsonPrimitive.content).canonicalFile
        check(model.isFile) { "Model file missing" }
        val vectorFixture = config["vectorFixture"]?.jsonObject
        val experiment = config["experimentalRetrieval"]?.jsonObject
        if (experiment != null) {
            check(manualEvidence == null && vectorFixture == null && databases.size == 1)
            check(config.getValue("retrievalBackend").jsonPrimitive.content == "leviathan")
            check(!experiment.getValue("productionAdopted").jsonPrimitive.boolean)
            check(experiment.getValue("schemaVersion").jsonPrimitive.int == 1)
            check(experiment.getValue("scope").jsonPrimitive.content == "desktop_full_pack_experiment")
            for (key in listOf("source", "index", "binary")) {
                val identity = experiment.getValue(key).jsonObject
                val file = File(identity.getValue("path").jsonPrimitive.content).canonicalFile
                check(file.length() == identity.getValue("bytes").jsonPrimitive.long && sha256(file) == identity.getValue("sha256").jsonPrimitive.content)
                if (key == "source") check(file == databases.single())
            }
        }
        check((config["retrievalBackend"]?.jsonPrimitive?.content == "leviathan") == (experiment != null))
        val vectorDatabase = vectorFixture?.getValue("database")?.jsonPrimitive?.content?.let { File(it).canonicalFile }
        val embedding = vectorFixture?.getValue("embedding")?.let { Json.decodeFromJsonElement<PackEmbedding>(it) }
        val discovery = vectorFixture?.getValue("discovery")?.let { Json.decodeFromJsonElement<PackDiscovery>(it) }
        val queryVectors = vectorFixture?.getValue("queries")?.jsonObject?.mapValues { (_, values) ->
            values.jsonArray.map { it.jsonPrimitive.float }.toFloatArray().also { vector ->
                check(vector.size == embedding!!.dim && vector.all { it.isFinite() })
                check(vector.sumOf { (it * it).toDouble() } in 0.98..1.02)
            }
        }.orEmpty()
        if (vectorFixture != null) {
            check(vectorDatabase in databases)
            check(sha256(vectorDatabase!!) == vectorFixture.getValue("databaseSha256").jsonPrimitive.content)
            check(embedding!!.encoderSha256 == vectorFixture.getValue("encoderSha256").jsonPrimitive.content)
        }
        val identities = buildJsonArray {
            (listOf(model) + databases).forEach { file ->
                add(buildJsonObject { put("path", file.path); put("bytes", file.length()); put("sha256", sha256(file)) })
            }
        }
        val rows = mutableListOf<JsonObject>()
        val json = Json { prettyPrint = true }
        fun save() {
            output.parentFile.mkdirs()
            output.writeText(json.encodeToString(buildJsonObject {
                put("schemaVersion", 1)
                put("provenance", config["provenance"] ?: JsonNull)
                put("qualityVerdict", "UNSCORED: completion is not correctness")
                put("evidenceMode", if (manualEvidence == null) "automatic retrieval" else "manual selection diagnostic")
                put("answerPolicy", config["answerPolicy"] ?: JsonPrimitive("app"))
                put("retrievalBackend", config["retrievalBackend"] ?: JsonPrimitive("app"))
                put("experimentalRetrieval", experiment ?: JsonNull)
                put("settings", buildJsonObject {
                    put("seeds", config["seeds"] ?: buildJsonArray { add(17) })
                    put("temperature", config["temperature"] ?: JsonPrimitive(0.3))
                    put("attachments", attachedFiles ?: JsonNull)
                    put("location", config["location"] ?: JsonNull)
                    put("followUps", followUps ?: JsonNull)
                })
                put("identities", identities)
                put("limitations", buildJsonArray {
                    add("Uses current app orchestration, relevance gate, prompt packing, and answer attribution directly.")
                    if (experiment != null) add("Experimental single-pack Leviathan general search; specialized routes delegate to app. Full passages verified before normal prompt packing. Not Android or production adoption; rank is not comparable BM25 confidence.")
                    add(if (manualEvidence == null) "Uses automatic keyword retrieval." else "Retrieval bypassed with manually selected passages; not a retrieval score or guaranteed sufficient evidence.")
                    add(if (vectorFixture == null) "Keyword-only: vector encoder not enabled. Not full Android inference parity."
                        else "Real vector search/fusion with frozen encoder queries; no live desktop encoding. Missing query fixtures use keyword fallback. Not Android inference parity.")
                    add("Desktop backend, fixed answer seed 17, non-streaming; app first-token/count metrics are not comparable.")
                    add(if (attachedFiles == null) "Native repetition-loop stopping, UI, attachments/OCR and device resources are not tested here."
                        else "Text attachments use the app's attachment packing; OCR, native repetition-loop stopping, UI and device resources are not tested here.")
                    add("Policy overrides affect inference instructions only; unchanged app attribution may strip paraphrase citations. Inspect rawAnswer as well as visibleAnswer.")
                })
                put("runs", JsonArray(rows))
            }))
        }
        var failures = 0
        val seeds = config["seeds"]?.jsonArray?.map { it.jsonPrimitive.int } ?: listOf(17)
        val temperature = config["temperature"]?.jsonPrimitive?.double ?: 0.3
        // A chained follow-up ("@c01.1") is read when its turn comes, from the reading and the answer it follows, as on the phone.
        val readings = mutableMapOf<Pair<Int, String>, String>()
        val shown = mutableMapOf<Pair<Int, String>, String>()
        val turns = mutableMapOf<Pair<Int, String>, List<FollowUp.Turn>>()
        for (seed in seeds) for (item in config.getValue("questions").jsonArray) {
            val listed = item.jsonPrimitive.content
            val chained = followUps?.get(listed)?.jsonObject?.takeIf { listed.startsWith("@") }
            val question = chained?.let { link ->
                val after = link.getValue("after").jsonPrimitive.content
                val previous = readings[seed to after] ?: after
                FollowUp.readAs(previous, link.getValue("asked").jsonPrimitive.content,
                    root = link["root"]?.jsonPrimitive?.content ?: previous, answer = shown[seed to previous].orEmpty())
            } ?: listed
            readings[seed to listed] = question
            // The conversation so far, as the phone carries it: each earlier question as typed, its reading and its answer.
            val earlierTurns = chained?.let { link ->
                val after = link.getValue("after").jsonPrimitive.content
                val previous = readings[seed to after] ?: after
                // The turn before: a chained key's own typed follow-up, or the first question as typed.
                val askedBefore = followUps[after]?.jsonObject?.get("asked")?.jsonPrimitive?.content ?: after
                turns[seed to after].orEmpty() + FollowUp.Turn(askedBefore, previous, shown[seed to previous].orEmpty())
            } ?: followUps?.get(listed)?.jsonObject?.get("earlier")?.jsonArray?.map { turn ->
                // A follow-up given with its conversation written out, for tracing one reading on its own.
                val t = turn.jsonObject
                FollowUp.Turn(t.getValue("asked").jsonPrimitive.content, t.getValue("readAs").jsonPrimitive.content, t.getValue("answer").jsonPrimitive.content)
            }.orEmpty()
            turns[seed to listed] = earlierTurns
            // Read the existing private constant instead of keeping another prompt copy.
            val system = AppContainer::class.java.getDeclaredField("SYSTEM_PROMPT").apply { isAccessible = true }.get(null) as String
            val gateway = DesktopGateway(config.getValue("endpoint").jsonPrimitive.content, system,
                config["answerPolicy"]?.jsonPrimitive?.content ?: "app", seed, temperature,
                retrievalOnly = System.getenv("FIELDATLAS_DESKTOP_RETRIEVAL_ONLY") == "1")
            val multi = MultiKnowledgeRetriever(
                { databases },
                packEmbeddings = { databases.map { if (it == vectorDatabase) embedding else null } },
                packDiscoveries = { databases.map { if (it == vectorDatabase) discovery else null } },
                embedForPack = { expected, text ->
                    check(expected == embedding)
                    queryVectors[text]
                },
            )
            val searches = mutableListOf<JsonObject>()
            val experimental = experiment?.let { manifest ->
                DesktopLeviathanRetriever(multi, databases.single(),
                    File(manifest.getValue("index").jsonObject.getValue("path").jsonPrimitive.content),
                    File(manifest.getValue("binary").jsonObject.getValue("path").jsonPrimitive.content))
            }
            try {
            val backend: Retriever = experimental ?: multi
            val selected = manualEvidence?.let { Json.decodeFromJsonElement<List<Evidence>>(it.getValue(question)) }
            val recordingRetriever = object : Retriever {
                override fun hasEligiblePacks(query: String) = multi.hasEligiblePacks(query)
                override suspend fun destinations(question: String) = multi.destinations(question)
                override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit): List<Evidence> =
                    searchQuestion(query, question, limit, false, onProgress)
                // A place lookup searches only the place collections, as on the phone.
                override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                                                    onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                    val started = System.nanoTime()
                    val evidence = selected?.take(limit) ?: backend.searchQuestion(query, question, limit, placesOnly, onProgress)
                    searches += buildJsonObject {
                        put("query", query); put("limit", limit); put("placesOnly", placesOnly)
                        put("elapsedMs", (System.nanoTime() - started) / 1_000_000.0)
                        put("backend", if (experimental != null && !placesOnly) "leviathan" else "app")
                        put("candidates", Json.encodeToJsonElement(evidence))
                    }
                    return evidence
                }
                override suspend fun article(article: String, limit: Int): List<Evidence> = multi.article(article, limit).also { evidence ->
                    searches += buildJsonObject {
                        put("query", "article: $article"); put("limit", limit)
                        put("candidates", Json.encodeToJsonElement(evidence))
                    }
                }
                // Near-me lookups read the same place packs by coordinates, as on the phone.
                override suspend fun nearby(point: GeoPoint, radiusKm: Double, limit: Int, categories: Set<String>) =
                    multi.nearby(point, radiusKm, limit, categories)
                override suspend fun aliased(name: String, limit: Int): List<Evidence> = multi.aliased(name, limit).also { evidence ->
                    searches += buildJsonObject {
                        put("query", "also known as: $name"); put("limit", limit)
                        put("candidates", Json.encodeToJsonElement(evidence))
                    }
                }
                override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> {
                    val evidence = multi.titled(phrase, sections, limit)
                    searches += buildJsonObject {
                        put("query", "title: $phrase (${sections.joinToString(" | ")})"); put("limit", limit)
                        put("candidates", Json.encodeToJsonElement(evidence))
                    }
                    return evidence
                }
                override suspend fun titled(phrase: String, sections: List<String>, limit: Int, anchored: Boolean): List<Evidence> =
                    multi.titled(phrase, sections, limit, anchored)
                override suspend fun airportOverviews(place: String, limit: Int) = multi.airportOverviews(place, limit)
                override suspend fun headed(phrase: String, words: List<String>, limit: Int, sections: List<String>): List<Evidence> =
                    multi.headed(phrase, words, limit, sections)
                override suspend fun sections(article: String, limit: Int): List<Evidence> = multi.sections(article, limit).also { evidence ->
                    searches += buildJsonObject {
                        put("query", "sections of: $article"); put("limit", limit)
                        put("candidates", Json.encodeToJsonElement(evidence))
                    }
                }
            }
            var sources = emptyList<Evidence>()
            val answer = StringBuilder()
            var metrics: ResearchMetrics? = null
            var error: String? = null
            var packedPrompt: String? = null
            val attachments = attachedFiles?.get(question)?.jsonArray.orEmpty().mapIndexed { index, file ->
                val entry = file.jsonObject
                ExtractedAttachment("desktop-$index", entry.getValue("name").jsonPrimitive.content,
                    listOf(AttachmentPage(1, entry.getValue("text").jsonPrimitive.content)), kind = AttachmentKind.TEXT)
            }
            // Same bundled guides as the app, so emergency questions take the app's path.
            val guides = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText()).guides
            val numbers = EmergencyNumbers.book(File("src/main/assets/emergency/numbers.json").readText())
            // A fixed test position stands in for the phone's GPS fix.
            val position = config["location"]?.jsonArray?.let { GeoPoint(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) }
            ResearchOrchestrator(recordingRetriever, gateway, emergencyGuides = { guides }, emergencyNumbers = { numbers }, location = { position },
                // A phone's learned reading speed (estimated tokens per second), so the desktop run
                // packs the evidence that phone would; unset keeps the unknown-phone default.
                prefillSpeed = InMemoryPrefillSpeed(System.getenv("FIELDATLAS_DESKTOP_PREFILL_TPS")?.toDoubleOrNull()),
                // On by default, as in the app; FIELDATLAS_DESKTOP_ARTICLE_DEPTH=0 measures without it.
                articleDepth = System.getenv("FIELDATLAS_DESKTOP_ARTICLE_DEPTH") != "0",
                // Model-first answering with this many notes (0 for none); unset keeps the cited route.
                modelFirstNotes = System.getenv("FIELDATLAS_DESKTOP_MODEL_FIRST")?.toIntOrNull(),
                onEvidencePacked = { packedPrompt = it.prompt }).research(question, attachments = attachments,
                // The app's "Also use my collections" switch; FIELDATLAS_DESKTOP_LIBRARY_WITH_FILES=1 turns it on.
                libraryWithFiles = System.getenv("FIELDATLAS_DESKTOP_LIBRARY_WITH_FILES") == "1",
                topic = (followUps?.get(listed) ?: followUps?.get(question))?.jsonObject?.let { link ->
                    TopicEvidence.Carried(link.getValue("asked").jsonPrimitive.content,
                        answeredSources[seed to link.getValue("after").jsonPrimitive.content.let { readings[seed to it] ?: it }].orEmpty(),
                        earlierTurns)
                }).collect { event ->
                when (event) {
                    is ResearchEvent.Sources -> sources = event.evidence
                    is ResearchEvent.Lead -> { answer.clear(); answer.append(event.text) }
                    is ResearchEvent.Token -> { if (event.replace) answer.clear(); answer.append(event.text) }
                    is ResearchEvent.Complete -> metrics = event.metrics
                    is ResearchEvent.Failed -> error = event.message
                    is ResearchEvent.InsufficientEvidence -> error = event.reason
                    else -> Unit
                }
            }
            answeredSources[seed to question] = sources
            shown[seed to question] = AnswerText.finalized(answer.toString(), sources.size)
            if (metrics == null || error != null) failures++
            rows += buildJsonObject {
                put("question", question)
                if (listed != question) put("listed", listed)
                put("seed", seed)
                put("attachments", JsonArray(attachments.map { JsonPrimitive(it.displayName) }))
                put("status", if (metrics != null && error == null) "COMPLETED_UNSCORED" else "ERROR")
                error?.let { put("error", it) }
                put("searches", JsonArray(searches))
                put("experimentalSearches", JsonArray(experimental?.traces.orEmpty()))
                put("sources", Json.encodeToJsonElement(sources))
                put("calls", JsonArray(gateway.calls))
                packedPrompt?.let { put("packedPrompt", it) }
                put("visibleAnswer", AnswerText.finalized(answer.toString(), sources.size))
                put("metrics", Json.encodeToJsonElement(metrics))
            }
            save()
            println("Desktop question recorded: $question -> ${rows.last()["status"]}")
            } finally { experimental?.close() }
        }
        check(failures == 0) { "$failures desktop runs failed; partial report: $output" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
