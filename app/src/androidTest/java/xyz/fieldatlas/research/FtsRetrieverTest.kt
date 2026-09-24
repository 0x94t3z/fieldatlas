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
        }
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    @Test fun returnsRankedAttributedEvidence() = withRetriever { retriever ->
        val results = retriever.search("solar energy", 5)
        assertEquals("doc-a:0001", results.first().chunkId)
        assertEquals("Author A · CC BY 4.0", results.first().source)
        assertTrue(results.first().score >= results.last().score)
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

    @Test fun strictRetrievalDoesNotCitePartialComparisonMatches() = withRetriever { retriever ->
        val results = retriever.search("Compare solar and wind for a local grid", 5)
        assertTrue(results.isEmpty())
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
