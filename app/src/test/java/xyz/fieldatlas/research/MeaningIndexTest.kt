package xyz.fieldatlas.research

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningIndexTest {
    private val dim = 8
    private val subvectors = 4
    private val subDim = dim / subvectors
    private val random = Random(7)

    private fun unit(v: FloatArray): FloatArray {
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    private val centres = listOf(unit(FloatArray(dim) { if (it < 4) 1f else 0f }), unit(FloatArray(dim) { if (it >= 4) 1f else 0f }))
    private val codebooks = FloatArray(subvectors * MeaningIndex.CODEWORDS * subDim) { random.nextFloat() * 0.4f - 0.2f }

    /** Rows per cluster: records rowid and one code per subvector. */
    private val rows = mapOf(
        0 to List(40) { (1000L + it) to ByteArray(subvectors) { random.nextInt(256).toByte() } },
        1 to List(40) { (5000L + it) to ByteArray(subvectors) { random.nextInt(256).toByte() } },
    )

    private fun reconstruct(cluster: Int, codes: ByteArray) = FloatArray(dim) { d ->
        val s = d / subDim
        centres[cluster][d] + codebooks[(s * MeaningIndex.CODEWORDS + (codes[s].toInt() and 0xFF)) * subDim + d % subDim]
    }

    private val index = MeaningIndex(FloatArray(2 * dim) { centres[it / dim][it % dim] }, codebooks, dim, subvectors) { cluster ->
        val list = rows[cluster] ?: return@MeaningIndex null
        val refs = ByteBuffer.allocate(list.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> list.forEach { b.putInt(it.first.toInt()) } }.array()
        refs to list.flatMap { it.second.toList() }.toByteArray()
    }

    @Test fun scoresEqualTheCosineWithTheRebuiltVectors() {
        val query = unit(FloatArray(dim) { random.nextFloat() - 0.3f })
        val expected = rows.flatMap { (cluster, list) ->
            list.map { (id, codes) -> id to reconstruct(cluster, codes).indices.sumOf { (query[it] * reconstruct(cluster, codes)[it]).toDouble() } }
        }.sortedByDescending { it.second }.take(10)
        val found = index.search(query, probe = 2, limit = 10)
        assertEquals(expected.map { it.first }, found.map { it.first })
        expected.zip(found).forEach { (e, f) -> assertEquals(e.second, f.second, 1e-4) }
    }

    @Test fun readsOnlyTheClosestClusters() {
        val query = unit(FloatArray(dim) { if (it < 4) 1f else 0.05f })
        val found = index.search(query, probe = 1, limit = 50)
        assertEquals(40, found.size)
        assertTrue(found.all { it.first in 1000L until 1040L })
    }
}
