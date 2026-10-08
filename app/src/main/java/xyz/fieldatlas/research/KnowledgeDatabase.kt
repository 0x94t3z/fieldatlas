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

    /** Rows matching [titleMatch], shortest title first (the plain "Hurricane" before "Hurricane Lee"), then in pack order. */
    internal fun titled(titleMatch: String, limit: Int): List<Evidence> = synchronized(database) {
        database.prepare(TITLED_SQL).use { statement ->
            statement.bindText(1, titleMatch)
            statement.bindInt(2, limit)
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
            rows
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
     * A box crossing the antimeridian is searched as two longitude ranges. A place pack without
     * that table (the Wikivoyage listings) answers only when [city] names the destination the
     * mapped packs file the nearest places under (see [nearbyInCity]).
     */
    internal fun nearbyPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
        limit: Int,
        categories: Set<String> = emptySet(),
        city: String? = null,
    ): List<NearbyPlace> {
        if (!hasPlacePoints()) return if (city == null) emptyList() else nearbyInCity(lat, lon, radiusKm, limit, categories, city)
        // Essentials holds no restaurants: its 10 km box in a dense city is thousands of rows read for nothing.
        if (!mayHold(categories)) return emptyList()
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
        // Coordinates first, then the listing text nearest-first until enough places match, so a wide
        // box in a dense city reads only as far out as the answer needs.
        data class Point(val rowid: Long, val lat: Double, val lon: Double, val distance: Double)
        val points = synchronized(database) {
            val ordered = categories.sorted()
            val sql = (if (wraps) NEARBY_POINTS_WRAPPED_SQL else NEARBY_POINTS_SQL) +
                if (sqlFilter) " AND p.category IN (${ordered.indices.joinToString(",") { "?${it + 5}" }})" else ""
            database.prepare(sql).use { statement ->
                statement.bindDouble(1, lat - latDelta)
                statement.bindDouble(2, lat + latDelta)
                statement.bindDouble(3, westBound)
                statement.bindDouble(4, eastBound)
                if (sqlFilter) ordered.forEachIndexed { index, category -> statement.bindText(index + 5, category) }
                buildList {
                    while (statement.step()) {
                        val point = Point(statement.getLong(0), statement.getDouble(1), statement.getDouble(2), 0.0)
                        val distance = GeoDistance.km(lat, lon, point.lat, point.lon)
                        if (distance <= radiusKm) add(point.copy(distance = distance))
                    }
                }
            }
        }
        val nearest = points.sortedWith(compareBy({ it.distance }, { it.rowid }))
        // Without a stored category the text decides, and a rare kind (a bar among a city's vegan
        // restaurants) can lie past thousands of nearer listings, so no fixed count of the nearest
        // is enough: batches go on until `limit` match. Otherwise every listing read is kept, and
        // only the nearest `limit` are read.
        val textFilter = categories.isNotEmpty() && !sqlFilter
        val places = ArrayList<NearbyPlace>()
        synchronized(database) {
            var start = 0
            while (start < nearest.size && places.size < limit) {
                val wanted = if (textFilter) ROWID_BATCH else minOf(limit - places.size, ROWID_BATCH)
                var end = minOf(start + wanted, nearest.size)
                // A batch never splits equally distant places, so the cut below breaks their ties by
                // chunk id exactly as sorting every match in the circle would.
                while (end < nearest.size && nearest[end].distance == nearest[end - 1].distance) end++
                val batch = nearest.subList(start, end)
                val byRow = batch.associateBy { it.rowid }
                database.prepare(VECTOR_EVIDENCE_SQL + batch.joinToString(",") { "?" } + ")").use { statement ->
                    batch.forEachIndexed { index, point -> statement.bindLong(index + 1, point.rowid) }
                    while (statement.step()) {
                        val point = byRow[statement.getLong(5)] ?: continue
                        val text = statement.getText(4)
                        if (textFilter &&
                            text.lineSequence().none { line -> line.startsWith("Category: ") && line.removePrefix("Category: ") in categories }) continue
                        places += NearbyPlace(
                            Evidence(
                                documentId = statement.getText(0),
                                chunkId = statement.getText(1),
                                title = statement.getText(2),
                                source = statement.getText(3),
                                text = text,
                                score = point.distance,
                                matchedBy = "within ${GeoDistance.label(point.distance)}",
                            ),
                            point.distance,
                            GeoDistance.bearing(lat, lon, point.lat, point.lon),
                        )
                    }
                }
                start = end
            }
        }
        return places.sortedWith(compareBy({ it.distanceKm }, { it.evidence.chunkId })).take(limit)
    }

    /**
     * A guide's listings near a point, for a place pack with no coordinate table: the listings
     * titled with [city] ("Athens — Kostas", "Athens/Plaka — …") in one of [categories] that carry
     * Latitude and Longitude lines, filtered to [radiusKm]. The title index narrows the read to the
     * city's own listings, a few hundred rows, which is why no coordinate table is needed; a
     * namesake elsewhere ("Athens (Georgia) — …") shares the first title word and is dropped by
     * [DestinationScope.scopes].
     */
    private fun nearbyInCity(lat: Double, lon: Double, radiusKm: Double, limit: Int, categories: Set<String>, city: String): List<NearbyPlace> {
        if (!isPlaceListing()) return emptyList()
        val match = buildString {
            append("{title} : ^\"").append(city.replace("\"", " ")).append("\" AND {text} : (\"latitude\"")
            if (categories.isNotEmpty()) {
                append(" AND (").append(categories.joinToString(" OR ") { "\"category " + it.replace("\"", " ") + "\"" }).append(')')
            }
            append(')')
        }
        val rows = synchronized(database) {
            database.prepare(CITY_ROWS_SQL).use { statement ->
                statement.bindText(1, match)
                statement.bindInt(2, CITY_ROWS)
                buildList {
                    while (statement.step()) {
                        add(Evidence(statement.getText(0), statement.getText(1), statement.getText(2), statement.getText(3), statement.getText(4), 0.0))
                    }
                }
            }
        }
        fun line(text: String, key: String) = text.lineSequence().firstOrNull { it.startsWith("$key: ") }?.removePrefix("$key: ")?.trim()
        return rows.mapNotNull { row ->
            if (!DestinationScope.scopes(row.title, listOf(city))) return@mapNotNull null
            val placeLat = line(row.text, "Latitude")?.toDoubleOrNull() ?: return@mapNotNull null
            val placeLon = line(row.text, "Longitude")?.toDoubleOrNull() ?: return@mapNotNull null
            if (placeLat !in -90.0..90.0 || placeLon !in -180.0..180.0) return@mapNotNull null
            val distance = GeoDistance.km(lat, lon, placeLat, placeLon)
            if (distance > radiusKm) return@mapNotNull null
            NearbyPlace(
                row.copy(score = distance, matchedBy = "within ${GeoDistance.label(distance)}"),
                distance,
                GeoDistance.bearing(lat, lon, placeLat, placeLon),
            )
        }.sortedWith(compareBy({ it.distanceKm }, { it.evidence.chunkId })).take(limit)
    }

    private val categoryPresence = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Whether any listing here is in one of [categories], read from the full-text index (a listing
     * says "Category: Eat") and remembered for this handle. Unknown counts as possible.
     */
    private fun mayHold(categories: Set<String>): Boolean {
        if (categories.isEmpty()) return true
        val key = categories.sorted().joinToString(",")
        return categoryPresence.getOrPut(key) {
            runCatching {
                synchronized(database) {
                    database.prepare("SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ?1 LIMIT 1").use { statement ->
                        statement.bindText(1, "{text} : (" + categories.joinToString(" OR ") {
                            "\"category " + it.replace("\"", " ") + "\""
                        } + ")")
                        statement.step()
                    }
                }
            }.getOrDefault(true)
        }
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

    /**
     * True for a pack of whole articles by section (English Wikipedia's vital articles), identified
     * like a place pack from its first and last rows. Such a pack is read only article by article.
     */
    fun isArticleSections(): Boolean {
        cachedIsArticleSections?.let { return it }
        val sections = runCatching {
            synchronized(database) {
                database.prepare(
                    "SELECT document_id FROM chunks_fts WHERE rowid IN " +
                        "((SELECT min(rowid) FROM chunks_fts), (SELECT max(rowid) FROM chunks_fts))",
                ).use { statement ->
                    var rows = 0
                    var all = true
                    while (statement.step()) {
                        rows++
                        if (!statement.getText(0).startsWith(ARTICLE_PREFIX)) all = false
                    }
                    rows > 0 && all
                }
            }
        }.getOrDefault(false)
        cachedIsArticleSections = sections
        return sections
    }

    @Volatile private var cachedIsArticleSections: Boolean? = null

    /**
     * The article whose lead is titled [leadTitle], found with [leadMatch], and the rows after its
     * first lead row that belong to the same page, at most [limit]. Packs write a page's documents
     * together ("enw-68316-0000", "enw-68316-0001"…), so the article is one run of rows.
     */
    internal fun article(leadMatch: String, leadTitle: String, limit: Int): List<Evidence> = synchronized(database) {
        var lead = -1L
        var page = ""
        database.prepare(ARTICLE_LEAD_SQL).use { statement ->
            statement.bindText(1, leadMatch)
            while (statement.step()) {
                if (statement.getText(2).equals(leadTitle, ignoreCase = true)) {
                    lead = statement.getLong(0)
                    page = statement.getText(1).substringBeforeLast('-') + "-"
                    break
                }
            }
        }
        if (lead < 0) return@synchronized emptyList()
        database.prepare(ARTICLE_ROWS_SQL).use { statement ->
            statement.bindLong(1, lead)
            statement.bindLong(2, lead + limit)
            val rows = ArrayList<Evidence>()
            while (statement.step()) {
                val id = statement.getText(0)
                if (!id.startsWith(page)) break
                rows += Evidence(id, statement.getText(1), statement.getText(2), statement.getText(3), statement.getText(4), 0.0)
            }
            rows
        }
    }

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

    private val destinationPresence = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * Whether this place pack holds listings of [name]: a title "<name> — <place>" or a district's
     * "<name>/<district> — <place>". The title index is asked for titles beginning with the name's
     * words, and the first rows read decide, so "dinner" (only "Dinner Plain — …") and "museum"
     * ("Museums — The Louvre") are not destinations while "Nice" and "Bath" are. Remembered per handle.
     */
    internal fun holdsDestination(name: String): Boolean = destinationPresence.getOrPut(name.lowercase()) {
        runCatching {
            synchronized(database) {
                database.prepare("SELECT title FROM chunks_fts WHERE chunks_fts MATCH ?1 LIMIT ?2").use { statement ->
                    statement.bindText(1, "{title} : ^\"" + name.replace("\"", " ") + "\"")
                    statement.bindInt(2, DESTINATION_PROBE_ROWS)
                    var found = false
                    while (!found && statement.step()) found = DestinationScope.scopes(statement.getText(0), listOf(name))
                    found
                }
            }
        }.getOrDefault(false)
    }

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
        const val ARTICLE_PREFIX = "enw-"
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

        private const val ARTICLE_LEAD_SQL = """
            SELECT rowid, document_id, title
            FROM chunks_fts
            WHERE chunks_fts MATCH ?1
            ORDER BY length(title), rowid
            LIMIT 20
        """

        private const val ARTICLE_ROWS_SQL = """
            SELECT document_id, chunk_id, title, source, text
            FROM chunks_fts
            WHERE rowid >= ?1 AND rowid < ?2
            ORDER BY rowid
        """

        private const val TITLED_SQL = """
            SELECT document_id, chunk_id, title, source, text, 0.0
            FROM chunks_fts
            WHERE chunks_fts MATCH ?1
            ORDER BY length(title), rowid
            LIMIT ?2
        """

        private const val TITLE_LEAD_SQL = """
            SELECT document_id, chunk_id, title, source, text, 0.0
            FROM chunks_fts
            WHERE rowid IN ("""

        private const val NEARBY_POINTS = """
            SELECT p.rowid, p.lat, p.lon
            FROM place_points AS p
            WHERE p.lat BETWEEN ?1 AND ?2 AND """
        private const val NEARBY_POINTS_SQL = NEARBY_POINTS + "p.lon BETWEEN ?3 AND ?4"
        private const val NEARBY_POINTS_WRAPPED_SQL = NEARBY_POINTS + "(p.lon >= ?3 OR p.lon <= ?4)"
        private const val ROWID_BATCH = 400
        /** Listings read for one city from a pack without coordinates; a capital with districts has under two thousand. */
        private const val CITY_ROWS = 3000

        private const val CITY_ROWS_SQL = """
            SELECT document_id, chunk_id, title, source, text
            FROM chunks_fts
            WHERE chunks_fts MATCH ?1
            LIMIT ?2
        """
        /** Titles read to confirm a destination; a city's own listings come well within them. */
        private const val DESTINATION_PROBE_ROWS = 40

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
