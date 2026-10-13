package xyz.fieldatlas.ui.more

/**
 * THIRD_PARTY_NOTICES.md as the app shows it: each table row becomes one line ("**llama.cpp** —
 * commit … — MIT — …"), so nothing depends on columns lining up on a narrow screen, and every link
 * keeps its address in plain text, since links cannot be opened offline.
 */
internal fun readableNotices(markdown: String): String {
    val link = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    fun plain(text: String) = link.replace(text) { m -> "${m.groupValues[1]} (${m.groupValues[2]})" }
    val out = StringBuilder()
    var header = false
    for (line in markdown.lines()) {
        val row = line.trim()
        if (!row.startsWith("|")) {
            header = false
            out.append(plain(line)).append('\n')
            continue
        }
        val cells = row.trim('|').split('|').map { it.trim() }
        if (cells.all { it.matches(Regex(":?-{3,}:?")) }) continue
        if (!header) { header = true; continue }
        val rest = cells.drop(1).filter(String::isNotBlank).joinToString(" — ") { plain(it) }
        out.append("- **").append(plain(cells.first())).append("**").append(if (rest.isEmpty()) "" else " — $rest").append('\n')
    }
    return out.toString()
}

/** A licence file's name as a button label: "llama.cpp-LICENSE.txt" is "llama.cpp". */
internal fun licenceLabel(path: String): String {
    val name = path.substringAfterLast('/')
    return when (name) {
        "LICENSE.txt" -> "Apache License 2.0 (Field Atlas)"
        "NATIVE_SEARCH_NOTICES.md" -> "Leviathan and native search"
        "source-serif-4-OFL.txt" -> "Source Serif 4 font"
        "source-sans-3-OFL.txt" -> "Source Sans 3 font"
        "manrope-OFL.txt" -> "Manrope font"
        "bouncycastle-LICENSE.txt" -> "Bouncy Castle"
        "llvm-LICENSE.txt" -> "LLVM"
        "rust-runtime-LICENSE.txt" -> "Rust runtime"
        "tessdata-LICENSE" -> "Tesseract language data"
        else -> name.removeSuffix(".txt").removeSuffix("-LICENSE").removeSuffix("-README").removeSuffix("-OFL")
    }
}

/** Reflow prose for phone widths without removing legal words or flattening lists/rules. */
internal fun readableLicenceParagraphs(text: String): List<String> = text.replace("\r\n", "\n")
    .trim().split(Regex("\n[\\t ]*\n+"))
    .filter(String::isNotBlank).map { paragraph ->
        val lines = paragraph.lines().map(String::trim)
        val structured = lines.any { line ->
            line.matches(Regex("[-_=]{3,}")) || line.matches(Regex("(?:[-*•]|\\d+[.)])\\s+.*"))
        }
        lines.joinToString(if (structured) "\n" else " ")
    }

/** Presentation only: legal text is literal, never parsed as Markdown emphasis/links. */
internal sealed interface LicenceReadingBlock {
    val text: String
    data class Heading(override val text: String) : LicenceReadingBlock
    data class Paragraph(override val text: String) : LicenceReadingBlock
    data class Item(val marker: String, val body: String) : LicenceReadingBlock {
        override val text: String get() = "$marker $body"
    }
    data class Rule(override val text: String) : LicenceReadingBlock
}

internal fun licenceReadingBlocks(text: String): List<LicenceReadingBlock> = buildList {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines().map(String::trim)
    val rule = Regex("[-_=]{3,}")
    val item = Regex("^(\\d+[.)]|\\([a-z0-9]+\\)|[-*•])\\s+(.+)$")
    // Apache sections may put their first sentence on the same line as the title.
    val apacheSection = Regex("^(\\d+\\. (?:Definitions|Grant of Copyright License|Grant of Patent License|Redistribution|Submission of Contributions|Trademarks|Disclaimer of Warranty|Limitation of Liability|Accepting Warranty or Additional Liability)\\.)(?:\\s+(.*))?$")
    val sectionNames = setOf("PREAMBLE", "DEFINITIONS", "PERMISSION & CONDITIONS", "TERMINATION", "DISCLAIMER")
    val pending = mutableListOf<String>()
    var marker: String? = null
    fun flush() {
        if (pending.isNotEmpty()) {
            val body = pending.joinToString(" ")
            add(marker?.let { LicenceReadingBlock.Item(it, body) } ?: LicenceReadingBlock.Paragraph(body))
        }
        pending.clear()
        marker = null
    }
    lines.forEachIndexed { index, line ->
        val next = lines.getOrNull(index + 1).orEmpty()
        val previous = lines.getOrNull(index - 1).orEmpty()
        val section = apacheSection.matchEntire(line)
        val upperHeading = line.length <= 100 && line.any(Char::isLetter) && line.none(Char::isLowerCase) &&
            (next.isBlank() || line in sectionNames) && !line.endsWith(',') && !line.endsWith('.')
        val heading = upperHeading || line == "Apache License" || line.startsWith("SIL OPEN FONT LICENSE Version") ||
            (line.isNotBlank() && !rule.matches(line) && rule.matches(next))
        when {
            line.isBlank() -> flush()
            rule.matches(line) -> { flush(); add(LicenceReadingBlock.Rule(line)) }
            section != null -> {
                flush()
                add(LicenceReadingBlock.Heading(section.groupValues[1]))
                section.groupValues[2].takeIf(String::isNotBlank)?.let(pending::add)
            }
            heading && (pending.isEmpty() || previous.isBlank() || line in sectionNames) -> {
                flush(); add(LicenceReadingBlock.Heading(line))
            }
            item.matches(line) -> {
                flush()
                val match = item.matchEntire(line)!!
                marker = match.groupValues[1]
                pending += match.groupValues[2]
            }
            else -> pending += line
        }
    }
    flush()
}
