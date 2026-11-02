package xyz.fieldatlas.research

import xyz.fieldatlas.assets.PackEmbedding

/** Layout implemented by KnowledgeDatabase/VectorMath; other layouts remain keyword-only. */
fun supportsVectorLayout(metadata: PackEmbedding): Boolean =
    metadata.table == "chunk_vectors" && metadata.quant == "int8-symmetric-per-vector" && metadata.normalized

/** A meaning pack: clustered, compressed vectors in ivf_lists (see [MeaningIndex]). */
fun isMeaningLayout(metadata: PackEmbedding): Boolean =
    metadata.table == "ivf_lists" && metadata.quant == "ivf-pq" && metadata.normalized &&
        metadata.clusters != null && metadata.subvectors != null && metadata.probe != null

/** Equal dimensions alone do not imply that two encoders share a vector space. */
fun canShareQueryEncoder(candidate: PackEmbedding, active: PackEmbedding): Boolean =
    candidate.model == active.model && candidate.encoderSha256 == active.encoderSha256 &&
        candidate.dim == active.dim && candidate.normalized == active.normalized &&
        // How the passages' vectors are stored does not change how a question is encoded.
        candidate.quant in STORED_QUANTS && active.quant in STORED_QUANTS

private val STORED_QUANTS = setOf("int8-symmetric-per-vector", "ivf-pq")

/** Called under the encoder lock, with the actual encoder identity at encoding time. */
internal suspend fun encodeWithCompatibleEncoder(
    expected: PackEmbedding, active: PackEmbedding?, encode: suspend () -> FloatArray?,
): FloatArray? = if (active != null && canShareQueryEncoder(expected, active)) encode() else null
