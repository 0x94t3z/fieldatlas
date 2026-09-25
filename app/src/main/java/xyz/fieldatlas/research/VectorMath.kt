package xyz.fieldatlas.research

/**
 * Cosine search over int8-quantized embedding vectors, in pure Kotlin so the arithmetic is
 * JVM-testable without SQLite.
 *
 * Vector storage format (written by tools/build_vector_pack.py): a little-endian float32
 * symmetric scale followed by `dim` two's-complement bytes; value[i] = scale * byte[i].
 * Both stored and query vectors are L2-normalized before quantization, so the dot product of
 * the two dequantized vectors is the cosine similarity directly (no norm division needed).
 */
object VectorMath {

    /** Dequantize a stored vector; null when the blob does not match the expected size. */
    fun dequantize(blob: ByteArray, dim: Int): FloatArray? {
        if (blob.size != 4 + dim) return null
        val scalar = readScale(blob)
        return FloatArray(dim) { index -> scalar * blob[4 + index].toInt() }
    }

    /** Dot product of a query vector with a stored vector; null on a malformed blob. */
    fun cosine(query: FloatArray, blob: ByteArray, dim: Int): Double? {
        if (blob.size != 4 + dim) return null
        val scale = readScale(blob).toDouble()
        var dot = 0.0
        for (index in 0 until dim) {
            dot += query[index] * scale * blob[4 + index].toInt()
        }
        return dot
    }

    private fun readScale(blob: ByteArray): Float = Float.fromBits(
        (blob[0].toInt() and 0xFF) or
            ((blob[1].toInt() and 0xFF) shl 8) or
            ((blob[2].toInt() and 0xFF) shl 16) or
            ((blob[3].toInt() and 0xFF) shl 24),
    )

    /**
     * Full scan over every stored vector, returning up to [limit] (rowId, cosine) pairs sorted
     * best-first. [readBlob] fetches the quant blob for one rowid (null = row absent/corrupt,
     * skipped). Selection keeps a fixed-size unsorted top-k buffer — no per-row allocation, the
     * cosine computation dominates: a 700k-chunk pack costs one sequential blob scan plus
     * dim multiplies per row.
     */
    fun topK(
        query: FloatArray,
        dim: Int,
        limit: Int,
        rowIds: Sequence<Long>,
        readBlob: (Long) -> ByteArray?,
    ): List<Pair<Long, Double>> {
        require(limit > 0)
        val bestIds = LongArray(limit)
        val bestScores = DoubleArray(limit) { Double.NEGATIVE_INFINITY }
        var filled = 0
        for (rowId in rowIds) {
            val blob = readBlob(rowId) ?: continue
            val score = cosine(query, blob, dim) ?: continue
            if (filled < limit) {
                bestIds[filled] = rowId
                bestScores[filled] = score
                filled += 1
            } else {
                var worst = 0
                for (slot in 1 until limit) {
                    if (bestScores[slot] < bestScores[worst]) worst = slot
                }
                if (score > bestScores[worst]) {
                    bestIds[worst] = rowId
                    bestScores[worst] = score
                }
            }
        }
        return buildList {
            for (slot in 0 until filled) {
                if (bestScores[slot] > Double.NEGATIVE_INFINITY) add(bestIds[slot] to bestScores[slot])
            }
        }.sortedByDescending { (_, score) -> score }
    }
}
