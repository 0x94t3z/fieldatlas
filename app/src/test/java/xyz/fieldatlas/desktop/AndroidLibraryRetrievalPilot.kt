package xyz.fieldatlas.desktop

import android.os.Build
import android.os.Debug
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import xyz.fieldatlas.research.*

/** Opt-in ART shell experiment, never installed or packaged into the application.
 * No generation: compare candidates against the exact same immutable source databases. */
object AndroidLibraryRetrievalPilot {
    @JvmStatic fun main(args: Array<String>) {
        val code = try { runBlocking { run(File(args.single())) }; 0 }
        catch (failure: Throwable) { failure.printStackTrace(); 1 }
        exitProcess(code)
    }

    private suspend fun run(configFile: File) {
        check(System.getProperty("java.vm.name") == "Dalvik") { "Android ART required" }
        val config = Json.parseToJsonElement(configFile.readText()).jsonObject
        val settings = LibraryPilotSettings.parse(config)
        val output = File(config.getValue("output").jsonPrimitive.content).canonicalFile
        val directory = checkNotNull(output.parentFile)
        check(directory == configFile.canonicalFile.parentFile && directory.isDirectory &&
            directory.path.matches(Regex("/data/local/tmp/fieldatlas-library\\.[A-Za-z0-9]+"))) { "Expected owned library pilot scratch directory" }
        check(!output.exists() && !File(output.path + ".writing").exists()) { "Refusing to overwrite an existing report" }
        val pins = settings.packs.flatMap { listOfNotNull(it.source, it.index) } + listOfNotNull(settings.binary)
        pins.forEach(LibraryPinnedFile::verify)
        settings.packs.forEach { KnowledgeDatabase.open(it.source.file).use { } }
        settings.binary?.let {
            check(runLeviathan(listOf(it.file.path, "--version")).trim() == config.getValue("binaryVersion").jsonPrimitive.content)
        }
        val rows = mutableListOf<JsonObject>()
        var finished = false
        var fatalError: String? = null
        val json = Json { prettyPrint = true }
        fun save() {
            val report = buildJsonObject {
                put("schemaVersion", 1); put("scope", "android_art_shell_multi_pack_keyword_retrieval_only")
                put("productionAdopted", false); put("answerQualityMeasured", false)
                put("finished", finished); fatalError?.let { put("fatalError", it) }
                put("configSha256", libraryDigest(configFile)); put("config", config)
                put("device", buildJsonObject { put("model", Build.MODEL); put("api", Build.VERSION.SDK_INT) })
                put("sourceBytes", settings.packs.sumOf { it.source.bytes })
                put("additionalIndexBytes", settings.packs.sumOf { it.index?.bytes ?: 0L })
                put("maxParallelPackWork", MultiKnowledgeRetriever.MAX_PARALLEL_PACKS)
                put("limitations", buildJsonArray {
                    add("Shell UID and extracted app classes, not optimized release UI, app lifecycle, permissions, or installed-library acceptance.")
                    add("Keyword-only: no embedding model, answer generation, Qwen token count, semantic support scoring, or cold filesystem-cache claim.")
                    add("Each measured run creates and closes its database handles; digest checks and preceding runs warm filesystem caches. Per-case backend order alternates.")
                    add("Search timing includes lazy app opens and experimental per-pack preparation/CLI startup; source-origin audit is timed separately.")
                    add("PSS/RSS/FD values are snapshots; VmHWM is this process's lifetime high-water mark. Child CLI memory and full installed-app memory are not included.")
                    add("The 120-second coroutine timeout is cooperative; an outer process deadline is still required for blocked native SQLite work.")
                    add("The production baseline can recover individual query errors; observed open failures are explicit. Completion is not recall or answer correctness.")
                    add("Reported bytes cover configured source/index files only; include the app, models, other saved data and staging separately for the 50 GB limit.")
                    add("Specialized places remain on app FTS and article-only packs remain skipped in general search; traces label those routes.")
                })
                put("runs", JsonArray(rows))
            }
            val encoded = json.encodeToString(JsonObject.serializer(), report)
            check(encoded.toByteArray(Charsets.UTF_8).size <= 64 * 1024 * 1024) { "Pilot report exceeds 64 MiB bound" }
            val pending = File(output.path + ".writing")
            pending.writeText(encoded)
            check(pending.renameTo(output)) { "Unable to save report atomically" }
        }
        save()
        try {
            for ((orderIndex, order) in settings.orders.withIndex()) {
                val packs = if (order == "reversed") settings.packs.reversed() else settings.packs
                for (repetition in 0 until settings.repetitions) for ((caseIndex, case) in settings.cases.withIndex()) {
                    val modes = if ((orderIndex + repetition + caseIndex) % 2 == 0) settings.backends else settings.backends.reversed()
                    for (backend in modes) {
                        println("Library pilot: $order / $repetition / ${case.id} / $backend")
                        rows += measure(packs, settings.binary?.file, case, backend, order, repetition)
                        save()
                    }
                }
            }
            pins.forEach(LibraryPinnedFile::verify)
            finished = true
            save()
            check(rows.none { it.getValue("status").jsonPrimitive.content == "ERROR" }) { "One or more runs failed; inspect the saved report" }
        } catch (failure: Throwable) {
            fatalError = "${failure.javaClass.simpleName}: ${failure.message}"
            save()
            throw failure
        }
    }

    private suspend fun measure(packs: List<LibraryPilotPack>, binary: File?, case: LibraryPilotCase,
                                backend: String, order: String, repetition: Int): JsonObject {
        val opened = Collections.synchronizedMap(linkedMapOf<String, KnowledgeDatabase>())
        val openFailures = Collections.synchronizedList(mutableListOf<String>())
        val baseline = MultiKnowledgeRetriever({ packs.map { it.source.file } }, open = { file ->
            try { KnowledgeDatabase.open(file).also { opened[file.canonicalPath] = it } }
            catch (error: Throwable) { openFailures += "${file.path}: ${error.javaClass.simpleName}"; throw error }
        })
        val experimental = if (backend == "leviathan") LibraryLeviathanRetriever(baseline,
            packs.map { LibraryRetrievalPack(it.id, it.source.file, it.index?.file) }, checkNotNull(binary)) else null
        val retriever: Retriever = experimental ?: baseline
        val before = memorySnapshot()
        var afterSearch: JsonObject? = null
        var candidates = emptyList<Evidence>()
        var finalSources = emptyList<Evidence>()
        var mappings = emptyList<JsonObject>()
        var elapsedMs: Double? = null
        var sourceAuditMs: Double? = null
        var failure: String? = null
        try {
            val start = System.nanoTime()
            try {
                candidates = withTimeout(120_000) {
                    retriever.searchQuestion(case.query, case.question, case.candidateLimit, case.placesOnly) { }
                }
            } finally {
                elapsedMs = (System.nanoTime() - start) / 1_000_000.0
                afterSearch = memorySnapshot()
            }
            check(openFailures.isEmpty()) { "A baseline database failed to open" }
            val auditStarted = System.nanoTime()
            mappings = librarySourceOrigins(packs, candidates)
            sourceAuditMs = (System.nanoTime() - auditStarted) / 1_000_000.0
            finalSources = EvidenceRelevance.keep(candidates, FtsQuery.from(case.question)?.terms.orEmpty(), question = case.question)
                .take(case.finalLimit)
        } catch (error: Throwable) {
            if (error is OutOfMemoryError) throw error
            failure = "${error.javaClass.simpleName}: ${error.message}"
        } finally {
            opened.values.toList().forEach { it.close() }
        }
        return buildJsonObject {
            put("caseId", case.id); put("query", case.query); put("question", case.question)
            put("backend", backend); put("packOrder", order); put("repetition", repetition)
            put("packIds", JsonArray(packs.map { JsonPrimitive(it.id) }))
            put("status", if (failure == null) "COMPLETED_UNSCORED" else "ERROR")
            failure?.let { put("error", it) }
            elapsedMs?.let { put("searchMs", it) }; sourceAuditMs?.let { put("sourceAuditMs", it) }
            put("candidateLimit", case.candidateLimit); put("finalLimit", case.finalLimit); put("placesOnly", case.placesOnly)
            put("candidateIds", JsonArray(candidates.map { buildJsonObject {
                put("documentId", it.documentId); put("chunkId", it.chunkId); put("source", it.source)
            } }))
            put("sourceOrigins", JsonArray(mappings)); put("finalSources", Json.encodeToJsonElement(finalSources))
            put("experimentalPackTraces", JsonArray(experimental?.traces.orEmpty()))
            put("sourcePackTraces", JsonArray(packs.map { pack -> buildJsonObject {
                put("packId", pack.id); put("openedByBaseline", pack.source.file.canonicalPath in opened)
                put("selectedChunkIds", JsonArray(mappings.filter { map ->
                    pack.id in map.getValue("packIds").jsonArray.map { it.jsonPrimitive.content }
                }.map { it.getValue("chunkId") }))
            } }))
            put("openFailures", JsonArray(openFailures.map(::JsonPrimitive)))
            put("memoryBefore", before); afterSearch?.let { put("memoryAfterSearch", it) }
            put("memoryAfterClose", memorySnapshot())
        }
    }

    private fun memorySnapshot(): JsonObject = buildJsonObject {
        val status = File("/proc/self/status").readLines()
        for (key in listOf("VmRSS", "VmHWM", "VmSize", "FDSize", "Threads")) {
            status.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()?.let { put(key, it) }
        }
        File("/proc/self/fd").list()?.size?.let { put("openFileDescriptors", it) }
        runCatching { Debug.getPss() }.onSuccess { put("pssKiB", it) }
            .onFailure { put("pssError", it.javaClass.simpleName) }
    }
}

internal data class LibraryPinnedFile(val file: File, val bytes: Long, val sha256: String) {
    fun verify() { check(file.isFile && file.length() == bytes && libraryDigest(file) == sha256) { "Identity mismatch: $file" } }
    companion object {
        fun parse(value: JsonElement): LibraryPinnedFile {
            val data = value.jsonObject
            val file = File(data.getValue("path").jsonPrimitive.content)
            val bytes = data.getValue("bytes").jsonPrimitive.long
            val hash = data.getValue("sha256").jsonPrimitive.content
            require(file.isAbsolute && bytes in 1..50_000_000_000L && hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid file identity" }
            return LibraryPinnedFile(file.canonicalFile, bytes, hash)
        }
    }
}

internal data class LibraryPilotPack(val id: String, val source: LibraryPinnedFile, val index: LibraryPinnedFile?)
internal data class LibraryPilotCase(val id: String, val query: String, val question: String,
                                    val candidateLimit: Int, val finalLimit: Int, val placesOnly: Boolean)
internal data class LibraryPilotSettings(val packs: List<LibraryPilotPack>, val binary: LibraryPinnedFile?,
                                        val cases: List<LibraryPilotCase>, val orders: List<String>,
                                        val repetitions: Int, val backends: List<String>) {
    companion object {
        fun parse(config: JsonObject): LibraryPilotSettings {
            require(config.getValue("schemaVersion").jsonPrimitive.int == 1)
            val packs = config.getValue("packs").jsonArray.map { item -> item.jsonObject.let {
                LibraryPilotPack(it.getValue("id").jsonPrimitive.content, LibraryPinnedFile.parse(it.getValue("source")),
                    it["index"]?.takeUnless { it == JsonNull }?.let(LibraryPinnedFile::parse))
            } }
            require(packs.size in 1..128 && packs.all { it.id.isNotBlank() } && packs.map { it.id }.distinct().size == packs.size)
            require(packs.map { it.source.file }.distinct().size == packs.size) { "Duplicate source paths" }
            require(packs.sumOf { it.source.bytes + (it.index?.bytes ?: 0) } <= 50_000_000_000L) { "Configured data exceed 50 GB before app/model/staging" }
            val cases = config.getValue("cases").jsonArray.map { item -> item.jsonObject.let {
                val query = it.getValue("query").jsonPrimitive.content
                LibraryPilotCase(it.getValue("id").jsonPrimitive.content, query, it["question"]?.jsonPrimitive?.content ?: query,
                    it["candidateLimit"]?.jsonPrimitive?.int ?: 24, it["finalLimit"]?.jsonPrimitive?.int ?: 8,
                    it["placesOnly"]?.jsonPrimitive?.boolean ?: false)
            } }
            require(cases.size in 1..64 && cases.all { it.id.isNotBlank() && it.query.isNotBlank() && it.question.isNotBlank() &&
                it.query.length <= 2048 && it.question.length <= 2048 && it.candidateLimit in 1..50 && it.finalLimit in 1..it.candidateLimit })
            require(cases.map { it.id }.distinct().size == cases.size)
            val orders = config["orders"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("original")
            require(orders.isNotEmpty() && orders.distinct() == orders && orders.all { it in setOf("original", "reversed") })
            val repetitions = config["repetitions"]?.jsonPrimitive?.int ?: 1
            require(repetitions in 1..5)
            val backends = config["backends"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf("app", "leviathan")
            require(backends.isNotEmpty() && backends.distinct() == backends && backends.all { it in setOf("app", "leviathan") })
            val binary = config["binary"]?.takeUnless { it == JsonNull }?.let(LibraryPinnedFile::parse)
            if ("leviathan" in backends) require(binary != null && config["binaryVersion"]?.jsonPrimitive?.content?.isNotBlank() == true)
            return LibraryPilotSettings(packs, binary, cases, orders, repetitions, backends)
        }
    }
}

/** Read-only identity mapping. A chunk ID is never globally unique across source packs. */
internal fun librarySourceOrigins(packs: List<LibraryPilotPack>, rows: List<Evidence>): List<JsonObject> {
    if (rows.isEmpty()) return emptyList()
    val found = rows.map { mutableListOf<String>() }
    for (pack in packs) BundledSQLiteDriver().open(pack.source.file.path, SQLITE_OPEN_READONLY).use { database ->
        rows.forEachIndexed { index, row ->
            val titleMatch = row.title.any(Char::isLetterOrDigit)
            val sql = "SELECT document_id,chunk_id,title,source,text FROM chunks_fts WHERE chunk_id=?1" +
                if (titleMatch) " AND chunks_fts MATCH ?2" else ""
            database.prepare(sql).use { statement ->
                statement.bindText(1, row.chunkId)
                if (titleMatch) statement.bindText(2, "{title} : \"" + row.title.replace("\"", "\"\"") + "\"")
                while (statement.step()) {
                    if ((0..4).map(statement::getText) == listOf(row.documentId, row.chunkId, row.title, row.source, row.text)) {
                        found[index] += pack.id
                    }
                }
            }
        }
    }
    return rows.mapIndexed { index, row ->
        check(found[index].isNotEmpty()) { "Returned passage differs from all pinned originals: ${row.chunkId}" }
        buildJsonObject {
            put("documentId", row.documentId); put("chunkId", row.chunkId); put("source", row.source)
            put("packIds", JsonArray(found[index].distinct().map(::JsonPrimitive)))
        }
    }
}

internal fun libraryDigest(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
