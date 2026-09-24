package xyz.fieldatlas.research

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FtsRetrieverTest {
    private lateinit var root: File
    private lateinit var databaseFile: File

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.cacheDir, "fts-${UUID.randomUUID()}").apply { mkdirs() }
        databaseFile = File(root, "content.sqlite")
        BundledSQLiteDriver().open(databaseFile.path).use { database ->
            execute(database, "PRAGMA user_version=1")
            execute(
                database,
                """CREATE VIRTUAL TABLE chunks_fts USING fts5(
                    chunk_id UNINDEXED,
                    document_id UNINDEXED,
                    title,
                    source UNINDEXED,
                    text,
                    tokenize='unicode61 remove_diacritics 2'
                )""".trimIndent(),
            )
            insert(database, "doc-a:0001", "doc-a", "Solar basics", "Author A · CC BY 4.0", "solar energy energy storage")
            insert(database, "doc-a:0000", "doc-a", "Solar basics", "Author A · CC BY 4.0", "solar energy storage")
            insert(database, "doc-b:0000", "doc-b", "Wind basics", "Author B · CC0", "energi terbarukan angin")
            insert(database, "doc-c:0000", "doc-c", "Climate title", "Author C · CC0", "climate or title evidence")
            insert(database, "doc-d:0001", "doc-d", "Tie", "Author D · CC0", "identical tie text")
            insert(database, "doc-d:0000", "doc-d", "Tie", "Author D · CC0", "identical tie text")
            insert(database, "doc-e:0000", "doc-e", "Coffee guidance A", "Source E", "coffee pregnancy limit is low")
            insert(database, "doc-f:0000", "doc-f", "Coffee guidance B", "Source F", "coffee pregnancy limit is moderate")
            // Title-boost fixtures: generic stub articles that bm25 loves (they repeat the query
            // words constantly) versus the articles a "who won the world war" question is really
            // about — the article whose TITLE carries the most query terms must win the slots.
            insert(database, "doc-g:0000", "doc-g", "World", "Source G", ("world war history " + "world war world war world war ").repeat(6))
            insert(database, "doc-h:0000", "doc-h", "War", "Source H", ("war world history " + "war war war war war ").repeat(6))
            insert(database, "doc-i:0000", "doc-i", "World War I", "Source I", "the war lasted from 1914 to 1918")
            insert(database, "doc-j:0000", "doc-j", "Allies of World War I", "Source J", "the allies entente france britain defeated the central powers")
        }
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    @Test fun nameLikeTitlesLeadTheirPackNotTermFrequency() = withRetriever { retriever ->
        // "Allies of World War I" is named by its FIRST WORD being a query term, and 'allies'
        // is the rarest covered word - it leads; the article a question is about must never be
        // starved out of the pack's top picks by term-frequency junk.
        val allies = retriever.search("allies history records", 5)
        assertEquals("doc-j:0000", allies.first().chunkId)

        val war = retriever.search("world war allies winners records", 5)
        assertTrue(
            "the named war articles must stay inside the pack's answer set: " + war.map { it.chunkId },
            listOf("doc-i:0000", "doc-j:0000").all { it in war.map { evidence -> evidence.chunkId } },
        )
    }

    @Test fun returnsRankedAttributedEvidence() = withRetriever { retriever ->
        val results = retriever.search("solar energy", 5)
        assertEquals("doc-a:0001", results.first().chunkId)
        assertEquals("Author A · CC BY 4.0", results.first().source)
        assertTrue(results.first().score <= results.last().score)
    }

    @Test fun tiesAreBrokenByChunkId() = withRetriever { retriever ->
        val results = retriever.search("identical", 5)
        assertEquals(listOf("doc-d:0000", "doc-d:0001"), results.map { it.chunkId })
        assertEquals(results[0].score, results[1].score, 0.0)
    }

    @Test fun missingTermReturnsNoEvidence() = withRetriever { retriever ->
        assertTrue(retriever.search("volcanology", 5).isEmpty())
    }

    @Test fun preservesConflictingSourcesForSynthesis() = withRetriever { retriever ->
        val results = retriever.search("coffee pregnancy limit", 5)
        assertEquals(setOf("Source E", "Source F"), results.map { it.source }.toSet())
    }

    @Test fun broadFallbackFindsEvidenceAcrossComparisonTerms() = withRetriever { retriever ->
        val results = retriever.search("Compare solar and wind for a local grid", 5)
        assertEquals(setOf("doc-a", "doc-b"), results.map { it.documentId }.toSet())
    }

    @Test fun operatorsAndUnicodeAreSafeBoundQueries() = withRetriever { retriever ->
        assertEquals("doc-c:0000", retriever.search("climate OR title:\"x\"", 5).single().chunkId)
        assertEquals("doc-b:0000", retriever.search("Energi terbarukan", 5).single().chunkId)
    }

    @Test fun hugeQueryDoesNotCauseSqlFailure() = withRetriever { retriever ->
        val results = retriever.search("solar " + "*' OR title:".repeat(1_000), 5)
        assertTrue(results.size <= 5)
    }

    @Test fun rejectsInvalidLimits() = withRetriever { retriever ->
        assertThrows(IllegalArgumentException::class.java) { runBlocking { retriever.search("solar", 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { retriever.search("solar", 51) } }
    }

    @Test fun rejectsWrongSchemaVersion() {
        BundledSQLiteDriver().open(databaseFile.path).use {
            execute(it, "PRAGMA user_version=2")
        }
        assertThrows(InvalidKnowledgeDatabaseException::class.java) {
            KnowledgeDatabase.open(databaseFile).close()
        }
    }

    private fun withRetriever(block: suspend (FtsRetriever) -> Unit) = runBlocking {
        KnowledgeDatabase.open(databaseFile).use { database -> block(FtsRetriever(database)) }
    }

    private fun insert(
        database: SQLiteConnection,
        chunkId: String,
        documentId: String,
        title: String,
        source: String,
        text: String,
    ) {
        database.prepare(
            "INSERT INTO chunks_fts(chunk_id,document_id,title,source,text) VALUES(?,?,?,?,?)",
        ).use { statement ->
            statement.bindText(1, chunkId)
            statement.bindText(2, documentId)
            statement.bindText(3, title)
            statement.bindText(4, source)
            statement.bindText(5, text)
            statement.step()
        }
    }

    private fun execute(database: SQLiteConnection, sql: String) {
        database.prepare(sql).use { it.step() }
    }
}
