package xyz.fieldatlas.emergency

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A second reading of a first-aid message the keyword matcher found no guide for: a linear model over the message's
 * character 3-5-grams and words, trained offline on messages written for each guide and on look-alikes that need none
 * (tools/emergency-classifier). On 2 Nov 2026 the keyword matcher left 33 of 120 emergencies in a fresh, sealed set
 * without a guide; with this model after it, 16, with no unsafe guide.
 *
 * The features and the FNV-1a hash are the training script's exactly, so the shipped weights mean the same here; a
 * test replays the script's own predictions.
 */
class EmergencyClassifier private constructor(
    private val dimBits: Int,
    val labels: List<String>,
    private val bias: FloatArray,
    private val scale: FloatArray,
    private val weights: ByteArray,
) {
    private val dim = 1 shl dimBits

    /** The most likely label for [text] ("none" when no guide fits) and its probability. */
    fun classify(text: String): Pair<String, Double> {
        val p = probabilities(text)
        val best = p.indices.maxBy { p[it] }
        return labels[best] to p[best]
    }

    /** How likely [label] is for [text], or null when this model has no such label or gives no usable number. */
    fun probability(text: String, label: String): Double? {
        val index = labels.indexOf(label)
        return if (index < 0) null else probabilities(text)[index].takeIf { it.isFinite() }
    }

    private fun probabilities(text: String): DoubleArray {
        val x = features(text, dim)
        val scores = DoubleArray(labels.size) { y ->
            var sum = 0.0
            val row = y * dim
            for ((k, v) in x) sum += weights[row + k] * v
            bias[y] + scale[y] * sum
        }
        val max = scores.max()
        var total = 0.0
        for (y in scores.indices) {
            scores[y] = exp(scores[y] - max)
            total += scores[y]
        }
        for (y in scores.indices) scores[y] /= total
        return scores
    }

    companion object {
        /** Reads the model the training script writes (see tools/emergency-classifier/export.py for the layout). */
        fun read(bytes: ByteArray): EmergencyClassifier {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { b.get(it) }
            require(String(magic, Charsets.US_ASCII) == "FAEC") { "Not an emergency classifier" }
            require(b.get().toInt() == 1) { "Unknown emergency classifier version" }
            val dimBits = b.get().toInt()
            val count = b.short.toInt() and 0xffff
            val labels = List(count) { ByteArray(b.get().toInt() and 0xff).also { b.get(it) }.toString(Charsets.UTF_8) }
            val bias = FloatArray(count)
            val scale = FloatArray(count)
            for (y in 0 until count) { bias[y] = b.float; scale[y] = b.float }
            val weights = ByteArray(count shl dimBits).also { b.get(it) }
            return EmergencyClassifier(dimBits, labels, bias, scale, weights)
        }

        /** 32-bit FNV-1a over UTF-8, the training script's hash. */
        internal fun fnv1a(s: String): Int {
            var h = 0x811c9dc5.toInt()
            for (byte in s.toByteArray(Charsets.UTF_8)) h = (h xor (byte.toInt() and 0xff)) * 0x01000193
            return h
        }

        private val space = Regex("\\s+")
        private val word = Regex("[a-z0-9']+")

        /** Hashed, L2-normalised counts of character 3-5-grams (by code point) and of words and word pairs. */
        internal fun features(text: String, dim: Int): Map<Int, Double> {
            val t = " " + space.replace(text.lowercase(), " ").trim() + " "
            val counts = HashMap<Int, Double>()
            fun add(key: String) { val k = fnv1a(key) and (dim - 1); counts[k] = (counts[k] ?: 0.0) + 1.0 }
            val points = t.codePoints().toArray()
            for (n in 3..5) for (i in 0..points.size - n) add("c" + String(points, i, n))
            val words = word.findAll(t).map { it.value }.toList()
            for (w in words) add("w$w")
            for (i in 0 until words.size - 1) add("b" + words[i] + " " + words[i + 1])
            val norm = sqrt(counts.values.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
            return counts.mapValues { it.value / norm }
        }
    }
}
