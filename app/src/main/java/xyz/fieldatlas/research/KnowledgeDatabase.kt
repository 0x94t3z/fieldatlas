package xyz.fieldatlas.research

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.io.Closeable
import java.io.File

class InvalidKnowledgeDatabaseException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class KnowledgeDatabase private constructor(private val database: SQLiteConnection) : Closeable {
    internal fun search(matchExpression: String, limit: Int): List<Evidence> = synchronized(database) {
        database.prepare(SEARCH_SQL).use { statement ->
            statement.bindText(1, matchExpression)
            statement.bindInt(2, limit)
            buildList {
                while (statement.step()) {
                    add(
                        Evidence(
                            documentId = statement.getText(0),
                            chunkId = statement.getText(1),
                            title = statement.getText(2),
                            source = statement.getText(3),
                            text = statement.getText(4),
                            // Raw FTS5 bm25: negative, MORE negative = stronger. The retriever
                            // sorts ascending and the cross-pack merge shifts by the minimum,
                            // both trusting this direction — do not negate here (an earlier
                            // negation inverted the phone's rankings and let the evidence-
                            // packing budget truncate the strongest chunks first).
                            score = statement.getDouble(5),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Title-boost candidates: (title, lede rowid) pairs for articles whose TITLE contains any
     * of the query terms. Cheap (no text column), capped generously — the retriever decides
     * which candidates earn boost slots by how many query terms the title covers, and a global
     * length cut here would drop long-but-perfect titles ("Allies of World War I") before that
     * decision could ever see them.
     */
    internal fun titleCandidateTitles(titleMatch: String): List<TitleCandidate> = synchronized(database) {
        database.prepare(TITLE_TITLES_SQL).use { statement ->
            statement.bindText(1, titleMatch)
            val rows = ArrayList<TitleCandidate>()
            while (statement.step()) rows += TitleCandidate(statement.getText(0), statement.getLong(1))
            rows
        }
    }

    /** The lede chunk (by rowid) of each chosen title-boost candidate, in the given order. */
    internal fun titleLeadChunks(candidates: List<TitleCandidate>): List<Evidence> {
        if (candidates.isEmpty()) return emptyList()
        return synchronized(database) {
            val rowIds = candidates.map { candidate -> candidate.rowid }
            val sql = TITLE_LEAD_SQL + rowIds.joinToString(",") { "?" } + ")"
            database.prepare(sql).use { statement ->
                rowIds.forEachIndexed { index, rowId -> statement.bindLong(index + 1, rowId) }
                val rows = ArrayList<Evidence>()
                while (statement.step()) {
                    rows += Evidence(
                        documentId = statement.getText(0),
                        chunkId = statement.getText(1),
                        title = statement.getText(2),
                        source = statement.getText(3),
                        text = statement.getText(4),
                        score = statement.getDouble(5),
                    )
                }
                val order = candidates.withIndex().associate { (index, candidate) -> candidate.title to index }
                rows.sortedBy { row -> order[row.title] ?: Int.MAX_VALUE }
            }
        }
    }

    /**
     * Cosine top-k over the optional chunk_vectors table (int8-quantized embeddings).
     * Returns evidence whose [Evidence.score] is the cosine similarity (POSITIVE scale,
     * higher = stronger — the opposite direction from the raw bm25 scores search() returns;
     * the retriever re-maps these before merging). Empty when the pack has no vectors.
     */
    internal fun vectorSearch(query: FloatArray, dim: Int, limit: Int): List<Evidence> {
        if (!hasVectorTable()) return emptyList()
        val top = synchronized(database) {
            // Read vectors in one sequential cursor. The previous implementation issued a
            // separate indexed SELECT for every row (700k+ queries for the biology pack), which
            // made one phone lookup spend roughly a minute in retrieval alone.
            database.prepare("SELECT rowid, quant FROM chunk_vectors ORDER BY rowid").use { statement ->
                VectorMath.topK(
                    query = query,
                    dim = dim,
                    limit = limit,
                    rows = sequence {
                        while (statement.step()) yield(statement.getLong(0) to statement.getBlob(1))
                    },
                )
            }
        }
        if (top.isEmpty()) return emptyList()
        val scores = top.toMap()
        return synchronized(database) {
            val sql = VECTOR_EVIDENCE_SQL + top.joinToString(",") { "?" } + ")"
            database.prepare(sql).use { statement ->
                top.forEachIndexed { index, (rowId, _) -> statement.bindLong(index + 1, rowId) }
                val rows = HashMap<Long, Evidence>()
                while (statement.step()) {
                    val rowId = statement.getLong(5)
                    rows[rowId] = Evidence(
                        documentId = statement.getText(0),
                        chunkId = statement.getText(1),
                        title = statement.getText(2),
                        source = statement.getText(3),
                        text = statement.getText(4),
                        score = scores[rowId] ?: 0.0,
                    )
                }
                top.mapNotNull { (rowId, _) -> rows[rowId] }
            }
        }
    }

    /**
     * Places within a box around a point, from the optional rowid-aligned `place_points` table
     * (OpenStreetMap packs). Distances are great-circle kilometres; callers sort and cut.
     * A box crossing the antimeridian is searched as two longitude ranges.
     */
    internal fun nearbyPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        limit: Int,
        categories: Set<String> = emptySet(),
    ): List<NearbyPlace> {
        if (!hasPlacePoints()) return emptyList()
        // Packs that store each place's category filter in SQL; older packs are filtered on the
        // listing text afterwards. Either way a "pharmacy" lookup never keeps a cafe.
        val sqlFilter = categories.isNotEmpty() && hasPlaceCategory()
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        val west = lon - lonDelta
        val east = lon + lonDelta
        val wraps = west < -180.0 || east > 180.0
        val westBound = if (west < -180.0) west + 360.0 else west
        val eastBound = if (east > 180.0) east - 360.0 else east
        val places = synchronized(database) {
            val ordered = categories.sorted()
            val sql = (if (wraps) NEARBY_WRAPPED_SQL else NEARBY_SQL) +
                if (sqlFilter) " AND p.category IN (${ordered.indices.joinToString(",") { "?${it + 5}" }})" else ""
            database.prepare(sql).use { statement ->
                statement.bindDouble(1, lat - latDelta)
                statement.bindDouble(2, lat + latDelta)
                statement.bindDouble(3, westBound)
                statement.bindDouble(4, eastBound)
                if (sqlFilter) ordered.forEachIndexed { index, category -> statement.bindText(index + 5, category) }
                buildList {
                    while (statement.step()) {
                        val placeLat = statement.getDouble(5)
                        val placeLon = statement.getDouble(6)
                        val distance = GeoDistance.km(lat, lon, placeLat, placeLon)
                        if (distance > radiusKm) continue
                        val text = statement.getText(4)
                        if (categories.isNotEmpty() && !sqlFilter &&
                            text.lineSequence().none { line -> line.startsWith("Category: ") && line.removePrefix("Category: ") in categories }) continue
                        add(NearbyPlace(
                            Evidence(
                                documentId = statement.getText(0),
                                chunkId = statement.getText(1),
                                title = statement.getText(2),
                                source = statement.getText(3),
                                text = text,
                                score = distance,
                                matchedBy = "within ${GeoDistance.label(distance)}",
                            ),
                            distance,
                            GeoDistance.bearing(lat, lon, placeLat, placeLon),
                        ))
                    }
                }
            }
        }
        return places.sortedWith(compareBy({ it.distanceKm }, { it.evidence.chunkId })).take(limit)
    }

    fun hasPlacePoints(): Boolean {
        cachedHasPlacePoints?.let { return it }
        val present = runCatching {
            synchronized(database) {
                database.prepare(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name = 'place_points'",
                ).use { statement -> statement.step() }
            }
        }.getOrDefault(false)
        cachedHasPlacePoints = present
        return present
    }

    @Volatile private var cachedHasPlacePoints: Boolean? = null

    /** Whether `place_points` stores each place's category (essentials packs do). */
    private fun hasPlaceCategory(): Boolean {
        cachedHasPlaceCategory?.let { return it }
        val present = runCatching {
            synchronized(database) {
                database.prepare("PRAGMA table_info(place_points)").use { statement ->
                    var found = false
                    while (statement.step()) if (statement.getText(1) == "category") found = true
                    found
                }
            }
        }.getOrDefault(false)
        cachedHasPlaceCategory = present
        return present
    }

    @Volatile private var cachedHasPlaceCategory: Boolean? = null

    /**
     * Whether this pack holds only place listings (Wikivoyage or OpenStreetMap places). Packs are
     * built from one source each, so the first and last rows decide it in two rowid lookups.
     */
    fun isPlaceListing(): Boolean {
        cachedIsPlaceListing?.let { return it }
        val places = runCatching {
            synchronized(database) {
                database.prepare(
                    "SELECT document_id FROM chunks_fts WHERE rowid IN " +
                        "((SELECT min(rowid) FROM chunks_fts), (SELECT max(rowid) FROM chunks_fts))",
                ).use { statement ->
                    var rows = 0
                    var allPlaces = true
                    while (statement.step()) {
                        rows++
                        val id = statement.getText(0)
                        if (!PLACE_PREFIXES.any(id::startsWith)) allPlaces = false
                    }
                    rows > 0 && allPlaces
                }
            }
        }.getOrDefault(false)
        cachedIsPlaceListing = places
        return places
    }

    @Volatile private var cachedIsPlaceListing: Boolean? = null

    /** Cheap one-shot check for the optional vector table; cached for the handle's lifetime. */
    fun hasVectorTable(): Boolean {
        cachedHasVectors?.let { return it }
        val present = runCatching {
            synchronized(database) {
                database.prepare(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name = 'chunk_vectors'",
                ).use { statement -> statement.step() }
            }
        }.getOrDefault(false)
        cachedHasVectors = present
        return present
    }

    @Volatile private var cachedHasVectors: Boolean? = null

    internal fun countCapped(matchExpression: String, cap: Int): Int = synchronized(database) {
        database.prepare(
            "SELECT count(*) FROM (SELECT 1 FROM chunks_fts WHERE chunks_fts MATCH ? LIMIT ?)",
        ).use { statement ->
            statement.bindText(1, matchExpression)
            statement.bindInt(2, cap)
            if (statement.step()) statement.getInt(0) else 0
        }
    }

    override fun close() = database.close()

    private fun validate() {
        val version = database.prepare("PRAGMA user_version").use { statement ->
            requireCursor(statement.step(), "Missing user_version")
            statement.getInt(0)
        }
        requireCursor(version == SCHEMA_VERSION, "Unsupported user_version: $version")

        val columns = database.prepare("PRAGMA table_info(chunks_fts)").use { statement ->
            buildList {
                while (statement.step()) add(statement.getText(1))
            }
        }
        requireCursor(columns == EXPECTED_COLUMNS, "Unexpected chunks_fts columns: $columns")

        val createSql = database.prepare(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='chunks_fts'",
        ).use { statement ->
            requireCursor(statement.step(), "Missing chunks_fts table")
            statement.getText(0)
        }
        requireCursor(
            Regex("(?is)CREATE\\s+VIRTUAL\\s+TABLE.*USING\\s+fts5\\s*\\(").containsMatchIn(createSql),
            "chunks_fts is not an FTS5 virtual table",
        )
        // No PRAGMA quick_check here: import already verifies every artifact's SHA-256, and a
        // quick_check scans the whole database (multi-minute on 40 GB packs), which used to run
        // on every search because handles were reopened per query. The cheap structural checks
        // above still reject wrong-schema files at open time.
    }

    private fun requireCursor(condition: Boolean, message: String) {
        if (!condition) throw InvalidKnowledgeDatabaseException(message)
    }

    companion object {
        const val SCHEMA_VERSION = 1
        /** Document id prefixes written by the place-pack builders. */
        val PLACE_PREFIXES = listOf("wv-place-", "osm-place-")
        val EXPECTED_COLUMNS = listOf("chunk_id", "document_id", "title", "source", "text")

        // Same rows and order as ranking every match by (bm25, chunk_id), but bm25 needs only
        // the FTS index, while chunk_id/text live in the content table. Ranking whole rows read
        // the content row of EVERY match: common words ("local area network") meant ~47k pages
        // from the biology pack and ~19 s of phone retrieval. The cutoff is the limit-th best
        // score, so only rows at or above it (the result plus any exact ties) are read, and
        // ties still break by chunk_id; row order alone differs from chunk_id order in some
        // published packs.
        private const val SEARCH_SQL = """
            WITH ranked AS (
                SELECT rowid AS id, bm25(chunks_fts, 0.0, 0.0, 3.0, 0.0, 1.0) AS rank
                FROM chunks_fts
                WHERE chunks_fts MATCH ?1
            ), cutoff AS (
                SELECT rank FROM ranked ORDER BY rank ASC LIMIT 1 OFFSET ?2 - 1
            )
            SELECT c.document_id, c.chunk_id, c.title, c.source, c.text, r.rank
            FROM ranked AS r
            JOIN chunks_fts AS c ON c.rowid = r.id
            WHERE r.rank <= coalesce((SELECT rank FROM cutoff), 1e308)
            ORDER BY r.rank ASC, c.chunk_id COLLATE BINARY ASC
            LIMIT ?2
        """

        private const val TITLE_TITLES_SQL = """
            SELECT title, MIN(rowid) AS rowid
            FROM chunks_fts
            WHERE chunks_fts MATCH ?
            GROUP BY title
            LIMIT 5000
        """

        private const val TITLE_LEAD_SQL = """
            SELECT document_id, chunk_id, title, source, text, 0.0
            FROM chunks_fts
            WHERE rowid IN ("""

        private const val NEARBY_COLUMNS = """
            SELECT c.document_id, c.chunk_id, c.title, c.source, c.text, p.lat, p.lon
            FROM place_points AS p JOIN chunks_fts AS c ON c.rowid = p.rowid
            WHERE p.lat BETWEEN ?1 AND ?2 AND """
        private const val NEARBY_SQL = NEARBY_COLUMNS + "p.lon BETWEEN ?3 AND ?4"
        private const val NEARBY_WRAPPED_SQL = NEARBY_COLUMNS + "(p.lon >= ?3 OR p.lon <= ?4)"

        private const val VECTOR_EVIDENCE_SQL = """
            SELECT document_id, chunk_id, title, source, text, rowid
            FROM chunks_fts
            WHERE rowid IN ("""

        fun open(file: File): KnowledgeDatabase {
            if (!file.isFile) throw InvalidKnowledgeDatabaseException("Knowledge database does not exist")
            val sqlite = try {
                BundledSQLiteDriver().open(
                    file.absolutePath,
                    SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX,
                )
            } catch (error: Exception) {
                throw InvalidKnowledgeDatabaseException("Unable to open knowledge database", error)
            }
            return try {
                KnowledgeDatabase(sqlite).also { it.validate() }
            } catch (error: InvalidKnowledgeDatabaseException) {
                sqlite.close()
                throw error
            } catch (error: Exception) {
                sqlite.close()
                throw InvalidKnowledgeDatabaseException("Knowledge database validation failed", error)
            }
        }
    }
}

/** A title-boost candidate: article title and the rowid of its lede chunk. */
internal data class TitleCandidate(val title: String, val rowid: Long)
