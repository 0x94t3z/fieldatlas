package xyz.fieldatlas.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.fieldatlas.research.KnowledgeDatabase

/** Uses desktop SQLite only; checks the index-first search against full-row ranking. */
class KnowledgeSearchOrderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun nearbyPlacesUseTheCoordinateTableAcrossTheAntimeridian() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val db = File(temp.root, "points.sqlite")
        val places = listOf(
            Triple("berlin-near", 52.5200, 13.4050), Triple("berlin-far", 52.6500, 13.4050),
            Triple("potsdam", 52.3906, 13.0645), Triple("fiji-east", -16.50, 179.95), Triple("fiji-west", -16.50, -179.95),
        )
        BundledSQLiteDriver().open(db.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            exec("CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL)")
            places.forEachIndexed { index, (id, lat, lon) ->
                sql.prepare("INSERT INTO chunks_fts(rowid, chunk_id, document_id, title, source, text) VALUES(?,?,?,?,?,?)").use {
                    it.bindLong(1, index + 1L); it.bindText(2, "$id:0000"); it.bindText(3, id); it.bindText(4, id)
                    it.bindText(5, "https://example.org/$id"); it.bindText(6, "Place: $id"); it.step()
                }
                sql.prepare("INSERT INTO place_points(rowid, lat, lon) VALUES(?,?,?)").use {
                    it.bindLong(1, index + 1L); it.bindDouble(2, lat); it.bindDouble(3, lon); it.step()
                }
            }
        }
        val database = KnowledgeDatabase.open(db)
        try {
            val berlin = database.nearbyPlaces(52.5200, 13.4050, 15.0, 10)
            // berlin-far is 14.5 km north; Potsdam is ~25 km away and outside the radius.
            assertEquals(listOf("berlin-near", "berlin-far"), berlin.map { it.evidence.documentId })
            assertEquals(0.0, berlin.first().distanceKm, 0.001)
            assertEquals(listOf("berlin-near"), database.nearbyPlaces(52.5200, 13.4050, 15.0, 1).map { it.evidence.documentId })
            val fiji = database.nearbyPlaces(-16.50, 179.99, 15.0, 10).map { it.evidence.documentId }
            assertEquals(listOf("fiji-east", "fiji-west"), fiji)
        } finally {
            database.close()
        }
    }

    @Test fun nearbyPlacesFilterByCategoryInSqlOrFromTheListing() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val rows = listOf(
            listOf("pharmacy", "Health", 52.5220, 13.4080), listOf("cafe", "Eat", 52.5201, 13.4051),
            listOf("atm", "Money", 52.5210, 13.4050),
        )
        for (withColumn in listOf(true, false)) {
            val db = File(temp.root, "cat-$withColumn.sqlite")
            BundledSQLiteDriver().open(db.path).use { sql ->
                fun exec(text: String) { sql.prepare(text).use { it.step() } }
                exec("PRAGMA user_version=1")
                exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
                exec("CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL" + (if (withColumn) ", category TEXT)" else ")"))
                rows.forEachIndexed { index, row ->
                    val (id, category) = row; val lat = row[2] as Double; val lon = row[3] as Double
                    sql.prepare("INSERT INTO chunks_fts(rowid, chunk_id, document_id, title, source, text) VALUES(?,?,?,?,?,?)").use {
                        it.bindLong(1, index + 1L); it.bindText(2, "$id:0000"); it.bindText(3, "osm-place-$id"); it.bindText(4, id as String)
                        it.bindText(5, "https://example.org/$id"); it.bindText(6, "Destination: Berlin\nCategory: $category\nPlace: $id"); it.step()
                    }
                    sql.prepare(if (withColumn) "INSERT INTO place_points VALUES(?,?,?,?)" else "INSERT INTO place_points VALUES(?,?,?)").use {
                        it.bindLong(1, index + 1L); it.bindDouble(2, lat); it.bindDouble(3, lon); if (withColumn) it.bindText(4, category as String); it.step()
                    }
                }
            }
            val database = KnowledgeDatabase.open(db)
            try {
                val health = database.nearbyPlaces(52.5200, 13.4050, 2.0, 10, setOf("Health"))
                assertEquals("column=$withColumn", listOf("osm-place-pharmacy"), health.map { it.evidence.documentId })
                // The pharmacy lies a little north-east of the point.
                assertEquals("north-east", xyz.fieldatlas.research.GeoDistance.compass(health.single().bearingDegrees!!))
                assertEquals(3, database.nearbyPlaces(52.5200, 13.4050, 2.0, 10).size)
            } finally {
                database.close()
            }
        }
    }

    @Test fun indexFirstSearchMatchesFullRowRankingIncludingTies() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        val db = File(temp.root, "ties.sqlite")
        val rows = buildList {
            // Identical passages score identically. Insert them in REVERSE chunk_id order so
            // rowid order and chunk_id order disagree, as in a published biology pack.
            for (index in 9 downTo 0) add("tie:%02d".format(index) to "Local area network cables link computers in one building.")
            add("strong:00" to "Local area network. A local area network links computers locally in a local area.")
            add("weak:00" to "A network of rivers drains the wider area.")
            add("none:00" to "Buoyancy is the upward force exerted by a fluid.")
        }
        BundledSQLiteDriver().open(db.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            rows.forEach { (chunkId, text) ->
                sql.prepare("INSERT INTO chunks_fts VALUES(?,?,?,?,?)").use { statement ->
                    statement.bindText(1, chunkId)
                    statement.bindText(2, chunkId.substringBefore(':'))
                    statement.bindText(3, "Passage")
                    statement.bindText(4, "https://example.org/$chunkId")
                    statement.bindText(5, text)
                    statement.step()
                }
            }
        }
        val reference = """
            SELECT chunk_id FROM chunks_fts WHERE chunks_fts MATCH ?
            ORDER BY bm25(chunks_fts, 0.0, 0.0, 3.0, 0.0, 1.0) ASC, chunk_id COLLATE BINARY ASC LIMIT ?
        """
        val database = KnowledgeDatabase.open(db)
        try {
            for (match in listOf("\"local\"* OR \"area\"* OR \"network\"*", "\"network\"*", "\"buoyancy\"*", "\"absent\"*")) {
                for (limit in listOf(1, 2, 3, 5, 11, 13, 50)) {
                    val expected = BundledSQLiteDriver().open(db.path).use { sql ->
                        sql.prepare(reference).use { statement ->
                            statement.bindText(1, match)
                            statement.bindInt(2, limit)
                            buildList { while (statement.step()) add(statement.getText(0)) }
                        }
                    }
                    assertEquals("$match / $limit", expected, database.search(match, limit).map { it.chunkId })
                }
            }
            // The cutoff falls inside the tie group: chunk_id, not insertion order, decides.
            assertEquals(listOf("tie:00", "tie:01", "tie:02"),
                database.search("\"cables\"*", 3).map { it.chunkId })
        } finally {
            database.close()
        }
    }
}
