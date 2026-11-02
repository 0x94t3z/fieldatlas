package xyz.fieldatlas.research

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A meaning pack's vectors (written by tools/packs/build_meaning_pack.py): cluster centres, and per cluster the
 * records rowids of its passages with one byte per subvector each. A question is compared with every centre, the
 * closest [probe] clusters are read, and each row is scored from one lookup table, so a pack of millions of
 * passages costs a few clusters' bytes instead of a full scan.
 */
internal class MeaningIndex(
    /** Unit-length cluster centres, row-major: clusters x dim. */
    private val centres: FloatArray,
    /** Codewords, row-major: subvectors x 256 x (dim / subvectors). */
    private val codebooks: FloatArray,
    private val dim: Int,
    private val subvectors: Int,
    /** Rowids (unsigned 32-bit, little-endian) and codes of one cluster; null when the cluster is absent. */
    private val readCluster: (Int) -> Pair<ByteArray, ByteArray>?,
) {
    private val clusters = centres.size / dim
    private val subDim = dim / subvectors

    init {
        require(dim > 0 && subvectors > 0 && dim % subvectors == 0)
        require(centres.size == clusters * dim && clusters > 0)
        require(codebooks.size == subvectors * CODEWORDS * subDim)
    }

    /** Up to [limit] (records rowid, cosine) pairs, best first. [query] is unit length. */
    fun search(query: FloatArray, probe: Int, limit: Int): List<Pair<Long, Double>> {
        require(query.size == dim && probe > 0 && limit > 0)
        // q . (centre + residual) = q . centre + sum over subvectors of q_sub . codeword: one table serves every cluster.
        val table = FloatArray(subvectors * CODEWORDS)
        for (s in 0 until subvectors) {
            for (k in 0 until CODEWORDS) {
                var dot = 0f
                val base = (s * CODEWORDS + k) * subDim
                for (d in 0 until subDim) dot += query[s * subDim + d] * codebooks[base + d]
                table[s * CODEWORDS + k] = dot
            }
        }
        val centreScores = FloatArray(clusters) { c ->
            var dot = 0f
            for (d in 0 until dim) dot += query[d] * centres[c * dim + d]
            dot
        }
        val nearest = centreScores.indices.sortedByDescending { centreScores[it] }.take(probe)
        val best = TopK(limit)
        for (cluster in nearest) {
            val (refs, codes) = readCluster(cluster) ?: continue
            val rows = refs.size / 4
            if (rows == 0 || codes.size != rows * subvectors) continue
            val ids = ByteBuffer.wrap(refs).order(ByteOrder.LITTLE_ENDIAN)
            for (row in 0 until rows) {
                var score = centreScores[cluster]
                val offset = row * subvectors
                for (s in 0 until subvectors) score += table[s * CODEWORDS + (codes[offset + s].toInt() and 0xFF)]
                best.offer(ids.getInt(row * 4).toLong() and 0xFFFF_FFFFL, score.toDouble())
            }
        }
        return best.sorted()
    }

    private class TopK(private val limit: Int) {
        private val ids = LongArray(limit)
        private val scores = DoubleArray(limit) { Double.NEGATIVE_INFINITY }
        private var filled = 0
        private var worst = 0

        fun offer(id: Long, score: Double) {
            if (filled < limit) {
                ids[filled] = id; scores[filled] = score; filled += 1
                if (filled == limit) worst = scores.indices.minBy { scores[it] }
            } else if (score > scores[worst]) {
                ids[worst] = id; scores[worst] = score
                worst = scores.indices.minBy { scores[it] }
            }
        }

        fun sorted(): List<Pair<Long, Double>> = (0 until filled).map { ids[it] to scores[it] }.sortedByDescending { it.second }
    }

    /** The meaning pack's own database, read-only; centres and codebooks stay in memory while it is open. */
    class Store private constructor(private val database: SQLiteConnection, val index: MeaningIndex) : Closeable {
        override fun close() = synchronized(database) { database.close() }

        companion object {
            fun open(file: File, dim: Int, subvectors: Int): Store {
                val sqlite = BundledSQLiteDriver().open(file.absolutePath, SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX)
                try {
                    val centres = sqlite.prepare("SELECT vector FROM ivf_centroids ORDER BY cluster").use { row ->
                        val parts = ArrayList<FloatArray>()
                        while (row.step()) parts.add(floats(row.getBlob(0)))
                        require(parts.isNotEmpty() && parts.all { it.size == dim }) { "Bad cluster centres" }
                        FloatArray(parts.size * dim).also { out -> parts.forEachIndexed { i, p -> p.copyInto(out, i * dim) } }
                    }
                    val codebooks = sqlite.prepare("SELECT centroids FROM pq_codebooks ORDER BY sub").use { row ->
                        val parts = ArrayList<FloatArray>()
                        while (row.step()) parts.add(floats(row.getBlob(0)))
                        require(parts.size == subvectors) { "Bad codebooks" }
                        FloatArray(parts.sumOf { it.size }).also { out -> var at = 0; parts.forEach { p -> p.copyInto(out, at); at += p.size } }
                    }
                    val index = MeaningIndex(centres, codebooks, dim, subvectors) { cluster ->
                        synchronized(sqlite) {
                            sqlite.prepare("SELECT refs, codes FROM ivf_lists WHERE cluster = ?").use { row ->
                                row.bindLong(1, cluster.toLong())
                                if (row.step()) row.getBlob(0) to row.getBlob(1) else null
                            }
                        }
                    }
                    return Store(sqlite, index)
                } catch (error: Exception) {
                    sqlite.close()
                    throw error
                }
            }

            private fun floats(blob: ByteArray): FloatArray {
                val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
                return FloatArray(blob.size / 4) { buffer.getFloat(it * 4) }
            }
        }
    }

    companion object {
        const val CODEWORDS = 256
    }
}
