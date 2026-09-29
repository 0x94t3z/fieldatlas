package xyz.fieldatlas.research

import xyz.fieldatlas.assets.PackEmbedding

/** Layout implemented by KnowledgeDatabase/VectorMath; other layouts remain keyword-only. */
fun supportsVectorLayout(metadata: PackEmbedding): Boolean =
    metadata.table == "chunk_vectors" && metadata.quant == "int8-symmetric-per-vector" && metadata.normalized

/** Equal dimensions alone do not imply that two encoders share a vector space. */
fun canShareQueryEncoder(candidate: PackEmbedding, active: PackEmbedding): Boolean =
    candidate.model == active.model && candidate.encoderSha256 == active.encoderSha256 &&
        candidate.dim == active.dim && candidate.quant == active.quant &&
        candidate.normalized == active.normalized

/** Called under the encoder lock, with the actual encoder identity at encoding time. */
internal suspend fun encodeWithCompatibleEncoder(
    expected: PackEmbedding, active: PackEmbedding?, encode: suspend () -> FloatArray?,
): FloatArray? = if (active != null && canShareQueryEncoder(expected, active)) encode() else null
