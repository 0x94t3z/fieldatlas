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
        "source-serif-4-OFL.txt" -> "Source Serif 4 font"
        else -> name.removeSuffix(".txt").removeSuffix("-LICENSE").removeSuffix("-README").removeSuffix("-OFL")
    }
}
