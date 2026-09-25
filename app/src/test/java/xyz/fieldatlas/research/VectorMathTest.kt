package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vector search arithmetic for int8-quantized knowledge-pack embeddings. The golden values
 * mirror tools/build_vector_pack.py: blob = little-endian float32 scale + int8 components,
 * vectors L2-normalized before quantization.
 */
class VectorMathTest {
    private fun blob(scale: Float, vararg values: Int): ByteArray {
        val bytes = ByteArray(4 + values.size)
        val bits = scale.toRawBits()
        bytes[0] = (bits and 0xFF).toByte()
        bytes[1] = ((bits ushr 8) and 0xFF).toByte()
        bytes[2] = ((bits ushr 16) and 0xFF).toByte()
        bytes[3] = ((bits ushr 24) and 0xFF).toByte()
        values.forEachIndexed { index, value -> bytes[4 + index] = (value and 0xFF).toByte() }
        return bytes
    }

    @Test
    fun `dequantize applies little-endian scale and signed bytes`() {
        val vector = VectorMath.dequantize(blob(0.5f, 2, -3, 127, -128), dim = 4)!!
        assertEquals(listOf(1.0f, -1.5f, 63.5f, -64.0f), vector.toList())
    }

    @Test
    fun `dequantize rejects wrong-sized blobs`() {
        assertNull(VectorMath.dequantize(blob(1.0f, 1, 2, 3), dim = 4))
        assertNull(VectorMath.dequantize(blob(1.0f), dim = 4))
    }

    @Test
    fun `cosine of matched vectors is their dot product`() {
        // unit vectors with int8 steps of 1/127: scale = 1/127 so stored ints reach 127.
        val aligned = blob(1.0f / 127.0f, 127, 0, 0)
        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        assertEquals(1.0, VectorMath.cosine(query, aligned, dim = 3)!!, 1e-3)

        val orthogonal = blob(1.0f / 127.0f, 0, 127, 0)
        assertEquals(0.0, VectorMath.cosine(query, orthogonal, dim = 3)!!, 1e-9)
    }

    @Test
    fun `cosine rejects malformed blobs`() {
        assertNull(VectorMath.cosine(floatArrayOf(1.0f, 0.0f, 0.0f), blob(1.0f, 1, 2), dim = 3))
    }

    @Test
    fun `topK returns best rows first and respects the limit`() {
        val scale = 1.0f / 127.0f
        val store = mapOf(
            10L to blob(scale, 0, 127, 0),   // middle
            20L to blob(scale, 90, 90, 0),   // second
            30L to blob(scale, 127, 0, 0),   // best for [1,0,0]
            40L to blob(scale, 0, 0, 127),   // worst
        )
        val top = VectorMath.topK(
            query = floatArrayOf(1.0f, 0.0f, 0.0f),
            dim = 3,
            limit = 2,
            rowIds = store.keys.asSequence(),
            readBlob = store::get,
        )
        assertEquals(listOf(30L, 20L), top.map { (rowId, _) -> rowId })
        assertTrue(top[0].second > top[1].second)
    }

    @Test
    fun `topK returns fewer rows than the limit when the store is small`() {
        val store = mapOf(5L to blob(1.0f, 1, 2, 3))
        val top = VectorMath.topK(
            query = floatArrayOf(0.0f, 0.0f, 1.0f),
            dim = 3,
            limit = 8,
            rowIds = (1L..5L).asSequence(),
            readBlob = store::get,
        )
        assertEquals(listOf(5L), top.map { (rowId, _) -> rowId })
    }

    @Test
    fun `topK survives null blobs`() {
        val store = mapOf(1L to blob(1.0f / 127.0f, 127, 0, 0))
        val top = VectorMath.topK(
            query = floatArrayOf(1.0f, 0.0f, 0.0f),
            dim = 3,
            limit = 4,
            rowIds = sequenceOf(1L, 2L, 3L),
            readBlob = store::get,
        )
        assertEquals(1, top.size)
        assertEquals(1L, top.single().first)
    }

    @Test
    fun `quantization keeps vector norms so cosine stays in range`() {
        // [0.7071, 0.7071] is unit; stored at scale = 0.7071/127 the dot product is ~1.
        val component = 0.70710678f
        val scale = component / 127.0f
        val vector = floatArrayOf(component, component)
        val match = VectorMath.cosine(vector, blob(scale, 127, 127), dim = 2)!!
        assertEquals(1.0, match, 1e-3)
    }
}
