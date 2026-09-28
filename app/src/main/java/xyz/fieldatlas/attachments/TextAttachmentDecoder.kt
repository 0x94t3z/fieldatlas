package xyz.fieldatlas.attachments

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object TextAttachmentDecoder {
    fun decode(bytes: ByteArray): String {
        val charset = when {
            bytes.take(2) == listOf((-1).toByte(), (-2).toByte()) -> Charsets.UTF_16LE
            bytes.take(2) == listOf((-2).toByte(), (-1).toByte()) -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val text = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: Exception) { throw AttachmentException("Save this text file as UTF-8 or UTF-16, then try again.") }
        if (text.any { it.isISOControl() && it !in "\n\r\t" }) throw AttachmentException("This file does not contain readable plain text.")
        if (text.length > AttachmentPolicy.MAX_CHARACTERS) throw AttachmentException("This document is too long. Choose a shorter section (up to 100,000 characters).")
        if (text.isBlank()) throw AttachmentException("No readable text was found in this file.")
        return text
    }
}
