package xyz.fieldatlas.attachments

/** Bound labels without losing the file extension or splitting a UTF-16 surrogate pair. */
fun attachmentDisplayName(name: String): String {
    val clean = name.replace('\\', '/').substringAfterLast('/').filterNot { it.isISOControl() }.ifBlank { "Attachment" }
    if (clean.length <= 160) return clean
    return clean.take(106).dropLastWhile { it.isHighSurrogate() } + "…" +
        clean.takeLast(53).dropWhile { it.isLowSurrogate() }
}
