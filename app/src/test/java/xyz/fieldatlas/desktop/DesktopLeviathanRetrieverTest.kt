package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.*

class DesktopLeviathanRetrieverTest {
    @get:Rule val temp = TemporaryFolder()
    private val passage = "Do not apply the general rule when the exception holds. " + "Full original passage. ".repeat(200)

    private fun fixture(docChange: (JsonObject) -> JsonObject = { it }): Pair<File, File> {
        assumeTrue("Requires desktop SQLite", !System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val source = File(temp.root, "source.sqlite")
        val index = File(temp.root, "index.sqlite")
        BundledSQLiteDriver().open(source.path).use { sql ->
            sql.prepare("PRAGMA user_version=1").use { it.step() }
            sql.prepare("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)").use { it.step() }
            sql.prepare("INSERT INTO chunks_fts VALUES('chunk','doc','Complete title','https://example.org',?)").use { it.bindText(1, passage); it.step() }
        }
        val doc = docChange(buildJsonObject {
            put("id", "chunk"); put("document_id", "doc"); put("title", "Complete title")
            put("source", "https://example.org"); put("text", passage); put("source_rowid", 1)
        })
        BundledSQLiteDriver().open(index.path).use { sql ->
            sql.prepare("CREATE TABLE records(id TEXT UNIQUE,doc TEXT)").use { it.step() }
            sql.prepare("INSERT INTO records VALUES('chunk',?)").use { it.bindText(1, doc.toString()); it.step() }
        }
        return source to index
    }

    @Test fun expandsAndVerifiesFullPassageNotClippedCard() = runBlocking {
        val (source, index) = fixture()
        val progress = mutableListOf<Double>()
        DesktopLeviathanRetriever(Retriever { _, _, _ -> error("Unexpected fallback") }, source, index, File("/binary with spaces")) { command ->
            assertEquals(listOf("--", "injected query"), command.takeLast(2))
            """{"status":"ok","results":[{"id":"chunk","title":"Clipped","snippet":"Apply the general rule","relevance":0.99}]}"""
        }.use { retriever ->
            val row = retriever.search("--injected query", 5) { progress += it.fraction }.single()
            assertEquals(passage, row.text); assertEquals("Complete title", row.title)
            assertEquals(0.0, row.score, 0.0); assertEquals(listOf(0.5, 1.0), progress)
            assertTrue(retriever.traces.single().getValue("fullPassagesVerified").jsonPrimitive.boolean)
        }
    }

    @Test fun rejectsUnknownDuplicateAndTooManyIdsWithoutFallback() = runBlocking {
        val (source, index) = fixture()
        for ((json, limit) in listOf(
            """{"status":"ok","results":[{"id":"unknown"}]}""" to 5,
            """{"status":"ok","results":[{"id":"chunk"},{"id":"chunk"}]}""" to 5,
            """{"status":"ok","results":[{"id":"chunk"},{"id":"other"}]}""" to 1,
            """{"status":"error","results":[]}""" to 5,
        )) {
            DesktopLeviathanRetriever(Retriever { _, _, _ -> error("Unexpected fallback") }, source, index, File("unused")) { json }.use {
                try { it.search("query", limit) {}; fail("Invalid IDs accepted") } catch (_: IllegalStateException) { }
            }
        }
    }

    @Test fun rejectsChangedOriginalTextAndMetadataAndRowid() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val (source, index) = fixture()
        val originalDoc = BundledSQLiteDriver().open(index.path).use { sql ->
            sql.prepare("SELECT doc FROM records").use { row -> row.step(); Json.parseToJsonElement(row.getText(0)).jsonObject }
        }
        for ((key, value) in listOf("text" to JsonPrimitive("truncated"), "source" to JsonPrimitive("https://forged.org"),
            "title" to JsonPrimitive("changed"), "document_id" to JsonPrimitive("other"), "id" to JsonPrimitive("other"), "source_rowid" to JsonPrimitive(999))) {
            BundledSQLiteDriver().open(index.path).use { sql ->
                sql.prepare("UPDATE records SET doc=?").use { it.bindText(1, JsonObject(originalDoc + (key to value)).toString()); it.step() }
            }
            DesktopLeviathanRetriever(Retriever { _, _, _ -> emptyList() }, source, index, File("unused")) {
                """{"status":"ok","results":[{"id":"chunk"}]}"""
            }.use { retriever ->
                try { retriever.search("query", 5) {}; fail("Changed $key accepted") } catch (_: IllegalStateException) { }
            }
        }
    }

    @Test fun preservesSpecializedRoutesAndEmptyProgress() = runBlocking {
        val (source, index) = fixture()
        var places = false
        val baseline = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean, onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                places = placesOnly; return emptyList()
            }
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) = listOf(Evidence("doc", "chunk", "Title", "source", passage, 0.0))
        }
        var calls = 0
        DesktopLeviathanRetriever(baseline, source, index, File("unused")) { calls++; """{"status":"ok","results":[]}""" }.use {
            assertEquals(passage, it.titled("title", emptyList(), 1).single().text)
            it.searchQuestion("query", "question", 5, true) {}; assertTrue(places); assertEquals(0, calls)
            val progress = mutableListOf<Double>()
            assertTrue(it.search("missing", 5) { p -> progress += p.fraction }.isEmpty())
            assertEquals(1, calls); assertEquals(listOf(0.5, 1.0), progress)
        }
    }

    @Test fun subprocessFailureTimeoutAndOutputBoundAreExplicit() {
        assertTrue(runLeviathan(listOf("/usr/bin/printf", "%s", "small")).contains("small"))
        for (command in listOf(listOf("/usr/bin/false"), listOf("/bin/sleep", "1"), listOf("/usr/bin/printf", "%s", "exceeds"))) {
            try { runLeviathan(command, timeoutMs = 50, maxBytes = 5); fail("Failure accepted") } catch (_: IllegalStateException) { }
        }
    }

    @Test fun punctuationAndStopWordsNeverListNewestRecords() = runBlocking {
        val (source, index) = fixture()
        DesktopLeviathanRetriever(Retriever { _, _, _ -> error("Unexpected fallback") }, source, index, File("unused")) {
            error("An empty normalized query must not invoke the engine")
        }.use { retriever ->
            for (query in listOf("?!", "the and please")) {
                val progress = mutableListOf<Double>()
                assertTrue(retriever.search(query, 5) { progress += it.fraction }.isEmpty())
                assertEquals(listOf(1.0), progress)
            }
        }
    }

    @Test fun subjectIntroductionSurvivesHolidayCandidatesAndIsLabeledAsAppAssisted() = runBlocking {
        val (source, index) = fixture()
        val introduction = "A day is the time the Earth takes to spin once. Its rotation gives us day and night."
        BundledSQLiteDriver().open(source.path).use { sql ->
            sql.prepare("INSERT INTO chunks_fts VALUES('day:0000','day','Day — Overview','https://example.org/day',?)").use {
                it.bindText(1, introduction); it.step()
            }
        }
        DesktopLeviathanRetriever(Retriever { _, _, _ -> error("General search fallback is not allowed") }, source, index, File("unused")) {
            """{"status":"ok","results":[{"id":"chunk","snippet":"Christmas traditions","relevance":999}]}"""
        }.use { retriever ->
            val rows = retriever.search("According to the saved sources, why does day and night occur?", 5) { }
            assertEquals("day:0000", rows.first().chunkId)
            assertEquals(introduction, rows.first().text)
            assertTrue(rows.first().matchedBy!!.startsWith("app title anchor"))
            assertTrue(rows.all { it.score == 0.0 })
            assertEquals(listOf("day:0000"), retriever.traces.single().getValue("appTitleAnchorIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("chunk"), retriever.traces.single().getValue("chunkIds").jsonArray.map { it.jsonPrimitive.content })
        }
    }
}
