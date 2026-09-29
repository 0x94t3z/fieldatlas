package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.assets.PackEmbedding

class EmbeddingCompatibilityTest {
    @Test fun changingEncoderAfterRetrievalSkipsEncodingInsteadOfUsingWrongVectorSpace() = kotlinx.coroutines.runBlocking {
        var calls = 0
        val nowActive = active.copy(encoderSha256 = "b".repeat(64))
        val result = encodeWithCompatibleEncoder(active, nowActive) { calls++; floatArrayOf(1f) }
        assertNull(result)
        assertEquals(0, calls)
        assertArrayEquals(floatArrayOf(1f), encodeWithCompatibleEncoder(active, active) { floatArrayOf(1f) }, 0f)
    }
    private val active = PackEmbedding("bge-small", 384, "int8-symmetric-per-vector", true, "chunk_vectors", 100,
        0.5, "query: ", "encoder.gguf", "a".repeat(64), "https://example.org/encoder")
    @Test fun differentEncoderWithSameDimensionsCannotShareQueryVector() {
        assertFalse(canShareQueryEncoder(active.copy(encoderSha256 = "b".repeat(64)), active))
        assertFalse(canShareQueryEncoder(active.copy(model = "another"), active))
        assertFalse(canShareQueryEncoder(active.copy(dim = 768), active))
        assertFalse(canShareQueryEncoder(active.copy(quant = "float32"), active))
        assertFalse(canShareQueryEncoder(active.copy(normalized = false), active))
    }
    @Test fun sameEncoderCanServeDifferentIndexesAndPrefixes() {
        assertTrue(canShareQueryEncoder(active, active))
        assertTrue(canShareQueryEncoder(active.copy(table = "other_vectors", count = 999,
            rejectBelow = 0.6, queryPrefix = "search: ", encoderPath = "other.gguf"), active))
    }
    @Test fun unsupportedVectorLayoutsFallBackInsteadOfUsingFixedNativeTable() {
        assertTrue(supportsVectorLayout(active))
        assertFalse(supportsVectorLayout(active.copy(table = "other_vectors")))
        assertFalse(supportsVectorLayout(active.copy(quant = "float32")))
        assertFalse(supportsVectorLayout(active.copy(normalized = false)))
    }
}
