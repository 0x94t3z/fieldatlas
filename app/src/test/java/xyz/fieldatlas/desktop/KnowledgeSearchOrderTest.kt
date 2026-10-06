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
                // A pack with no listing of the kind asked is not read at all.
                assertEquals(emptyList<String>(), database.nearbyPlaces(52.5200, 13.4050, 2.0, 10, setOf("Sleep")).map { it.evidence.documentId })
                // The nearest are kept before their text is read; the limit applies to them.
                assertEquals(listOf("osm-place-cafe"), database.nearbyPlaces(52.5200, 13.4050, 2.0, 1).map { it.evidence.documentId })
            } finally {
                database.close()
            }
        }
    }

    /** A place pack as the OpenStreetMap builders write it; each listing lies the given kilometres due north of Berlin Mitte. */
    private fun placePack(name: String, withColumn: Boolean, listings: List<Triple<String, String, Double>>): File {
        val db = File(temp.root, "$name.sqlite")
        BundledSQLiteDriver().open(db.path).use { sql ->
            fun exec(text: String) { sql.prepare(text).use { it.step() } }
            exec("PRAGMA user_version=1")
            exec("CREATE VIRTUAL TABLE chunks_fts USING fts5(chunk_id UNINDEXED,document_id UNINDEXED,title,source UNINDEXED,text)")
            exec("CREATE TABLE place_points (rowid INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL" + (if (withColumn) ", category TEXT)" else ")"))
            exec("BEGIN")
            listings.forEachIndexed { index, (id, category, kmNorth) ->
                sql.prepare("INSERT INTO chunks_fts(rowid, chunk_id, document_id, title, source, text) VALUES(?,?,?,?,?,?)").use {
                    it.bindLong(1, index + 1L); it.bindText(2, "$id:0000"); it.bindText(3, "osm-place-$id"); it.bindText(4, "Berlin — $id")
                    it.bindText(5, "https://example.org/$id"); it.bindText(6, "Destination: Berlin\nCategory: $category\nPlace: $id"); it.step()
                }
                sql.prepare(if (withColumn) "INSERT INTO place_points VALUES(?,?,?,?)" else "INSERT INTO place_points VALUES(?,?,?)").use {
                    // GeoDistance's earth radius, so a listing's distance is the kilometres given.
                    it.bindLong(1, index + 1L); it.bindDouble(2, 52.52 + kmNorth * 180 / (6371.0 * Math.PI)); it.bindDouble(3, 13.405)
                    if (withColumn) it.bindText(4, category); it.step()
                }
            }
            exec("COMMIT")
        }
        return db
    }

    @Test fun nearbyPlacesReadListingsUntilEnoughOfTheKindMatch() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        // "Vegan bar near me" in Berlin: one bar 1 km away, a thousand restaurants nearer than the
        // other bars, which lie 4.4 to 4.7 km out. Reading only the nearest 800 listings found the
        // one bar at every radius.
        val bars = listOf("belushis" to 1.03, "alaska" to 4.43, "barkett" to 4.51, "klunkerkranich" to 4.58, "silent-jazz-bar" to 4.71)
            .map { (id, km) -> Triple(id, "Drink", km) }
        val restaurants = (0 until 1000).map { Triple("restaurant-%04d".format(it), "Eat", 1.5 + it * 0.002) }
        val barIds = bars.map { "osm-place-${it.first}" }
        for (withColumn in listOf(false, true)) {
            val database = KnowledgeDatabase.open(placePack("bars-$withColumn", withColumn, restaurants + bars))
            try {
                fun ids(radiusKm: Double, limit: Int, categories: Set<String> = emptySet()) =
                    database.nearbyPlaces(52.52, 13.405, radiusKm, limit, categories).map { it.evidence.documentId }
                assertEquals("column=$withColumn", barIds, ids(5.0, 200, setOf("Drink")))
                assertEquals("column=$withColumn", barIds, ids(15.0, 200, setOf("Drink")))
                assertEquals("column=$withColumn", barIds.take(2), ids(5.0, 2, setOf("Drink")))
                // The radius still bounds the read, and the common kind and plain lookups are unchanged.
                assertEquals("column=$withColumn", barIds.take(1), ids(2.0, 200, setOf("Drink")))
                assertEquals("column=$withColumn", (0 until 200).map { "osm-place-restaurant-%04d".format(it) }, ids(5.0, 200, setOf("Eat")))
                val nearest = listOf(barIds.first(), "osm-place-restaurant-0000", "osm-place-restaurant-0001")
                assertEquals("column=$withColumn", nearest, ids(5.0, 3))
                assertEquals("column=$withColumn", nearest, ids(5.0, 3, setOf("Drink", "Eat")))
            } finally {
                database.close()
            }
        }
    }

    @Test fun nearbyPlacesBreakDistanceTiesAtTheLimitByChunkId() {
        assumeTrue(!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank())
        // Two listings on one spot, written in the reverse of chunk id order: the limit keeps the
        // one that sorting every place in the circle keeps, whether or not the text decides.
        val listings = listOf(Triple("twin-b", "Eat", 1.0), Triple("twin-a", "Eat", 1.0), Triple("far", "Eat", 1.5))
        for (withColumn in listOf(false, true)) {
            val database = KnowledgeDatabase.open(placePack("twins-$withColumn", withColumn, listings))
            try {
                for (categories in listOf(emptySet<String>(), setOf("Eat"))) {
                    fun ids(limit: Int) = database.nearbyPlaces(52.52, 13.405, 2.0, limit, categories).map { it.evidence.documentId }
                    assertEquals("column=$withColumn $categories", listOf("osm-place-twin-a"), ids(1))
                    assertEquals("column=$withColumn $categories", listOf("osm-place-twin-a", "osm-place-twin-b"), ids(2))
                    assertEquals("column=$withColumn $categories", listOf("osm-place-twin-a", "osm-place-twin-b", "osm-place-far"), ids(3))
                }
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
