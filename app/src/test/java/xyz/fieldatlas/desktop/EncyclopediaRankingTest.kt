package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.FtsRetriever
import xyz.fieldatlas.research.KnowledgeDatabase
import xyz.fieldatlas.research.search

/** Uses desktop SQLite only; checks which article an encyclopedia pack leads with. */
class EncyclopediaRankingTest {
    @get:Rule val temp = TemporaryFolder()

    private fun pack(rows: List<Pair<String, String>>): KnowledgeDatabase {
        val file = File(temp.root, "encyclopedia.sqlite")
        BundledSQLiteDriver().open(file.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            rows.forEachIndexed { index, (title, text) ->
                sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use {
                    it.bindText(1, "doc-$index:0000"); it.bindText(2, "doc-$index"); it.bindText(3, title)
                    it.bindText(4, "https://example.org/$index"); it.bindText(5, text); it.step()
                }
            }
        }
        return KnowledgeDatabase.open(file)
    }

    @Test fun articlesNamedByTheQuestionLeadAndOverviewsLeadTheirSections() = runBlocking {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val database = pack(listOf(
            "Ève Curie — Overview" to "Ève Curie was a writer, the daughter of Marie Curie and Pierre Curie. Curie Curie.",
            "Marie Curie — Overview" to "Maria Skłodowska-Curie (Marie Curie) was a Polish physicist and chemist.",
            "Pierre and Marie Curie University — Overview" to "Pierre and Marie Curie University was a university in Paris.",
            "Photosynthesis — Details" to "Photosynthesis needs light. Photosynthesis happens in chloroplasts. Photosynthesis.",
            "Photosynthesis — Overview" to "Photosynthesis is a process in which green plants make their own food.",
            "Japan — Details" to "Japan has a long history. The history of Japan is studied by historians in Japan.",
            "History of Japan — Overview" to "The history of Japan begins in prehistoric times.",
        ))
        try {
            val retriever = FtsRetriever(database)
            assertEquals("Marie Curie — Overview", retriever.search("Who was Marie Curie?", 5).first().title)
            assertEquals("Photosynthesis — Overview", retriever.search("What is photosynthesis?", 5).first().title)
            assertEquals("History of Japan — Overview", retriever.search("Tell me about Japan's history", 5).first().title)
        } finally {
            database.close()
        }
    }
}
