package xyz.fieldatlas.attachments

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Decoded text plus whether it was cut at [AttachmentPolicy.MAX_CHARACTERS]. */
data class DecodedText(val text: String, val truncated: Boolean, val charset: String)

object TextAttachmentDecoder {
    fun decode(bytes: ByteArray): String = decodeText(bytes).text

    /**
     * [inputTruncated] means [bytes] is only the start of the file: a character cut in half at
     * the end is dropped and the result is marked truncated. [allowLegacy] permits the
     * Windows-1252 fallback, used only for files whose extension says they are text.
     */
    fun decodeText(bytes: ByteArray, inputTruncated: Boolean = false, allowLegacy: Boolean = true): DecodedText {
        val (detected, offset) = detect(bytes)
        var used = detected
        fun attempt(charset: Charset, start: Int): String? {
            strict(charset, bytes, start, bytes.size)?.let { return it }
            if (!inputTruncated) return null
            // The sample may end inside a multi-byte character; try without the last 1-3 bytes.
            for (drop in 1..3) strict(charset, bytes, start, bytes.size - drop)?.let { return it }
            return null
        }
        val decoded = attempt(detected, offset)
            // Files saved by older Windows editors are often Windows-1252, not UTF-8. Control
            // characters are still rejected below, so binary data does not slip through.
            ?: (if (allowLegacy && detected == Charsets.UTF_8) attempt(WINDOWS_1252, 0)?.also { used = WINDOWS_1252 } else null)
            ?: throw AttachmentException("Save this text file as UTF-8 or UTF-16, then try again.")
        val text = decoded.removePrefix("\uFEFF")
        if (text.any { it.isISOControl() && it !in "\n\r\t\u000C" }) throw AttachmentException("This file does not contain readable plain text.")
        if (text.isBlank()) throw AttachmentException("No readable text was found in this file.")
        val cut = truncate(text, AttachmentPolicy.MAX_CHARACTERS)
        return DecodedText(cut, inputTruncated || cut.length < text.length, used.name())
    }

    /** Cuts at a line or word boundary near [limit], never inside a surrogate pair. */
    fun truncate(text: String, limit: Int): String {
        if (text.length <= limit) return text
        var end = limit
        if (text[end - 1].isHighSurrogate()) end--
        val boundary = text.lastIndexOf('\n', end - 1).takeIf { it > limit * 9 / 10 }
            ?: text.lastIndexOf(' ', end - 1).takeIf { it > limit * 9 / 10 }
        return text.substring(0, boundary ?: end)
    }

    private fun detect(bytes: ByteArray): Pair<Charset, Int> {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xff)
        if (at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF) return Charsets.UTF_8 to 3
        if (at(0) == 0xFF && at(1) == 0xFE) return Charsets.UTF_16LE to 2
        if (at(0) == 0xFE && at(1) == 0xFF) return Charsets.UTF_16BE to 2
        // UTF-16 without a byte-order mark: mostly-Latin text has a zero in every other byte.
        val sample = minOf(bytes.size, 1024) and 1.inv()
        if (sample >= 4) {
            var evenZero = 0
            var oddZero = 0
            for (i in 0 until sample) if (bytes[i].toInt() == 0) { if (i % 2 == 0) evenZero++ else oddZero++ }
            val pairs = sample / 2
            if (oddZero > pairs * 0.4 && evenZero < pairs * 0.05) return Charsets.UTF_16LE to 0
            if (evenZero > pairs * 0.4 && oddZero < pairs * 0.05) return Charsets.UTF_16BE to 0
        }
        return Charsets.UTF_8 to 0
    }

    private fun strict(charset: Charset, bytes: ByteArray, start: Int, end: Int): String? = if (end < start) null else try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, end - start)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { null }

    private val WINDOWS_1252: Charset = runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)
}
