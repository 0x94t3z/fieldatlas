package xyz.fieldatlas.attachments

import java.util.Locale

/** Plain-text formats only. Uploaded code is displayed, never executed. */
object TextFileTypes {
    private val languages = mapOf(
        "kt" to "Kotlin", "kts" to "Kotlin", "java" to "Java", "py" to "Python",
        "js" to "JavaScript", "jsx" to "JSX", "mjs" to "JavaScript", "cjs" to "JavaScript",
        "ts" to "TypeScript", "tsx" to "TSX", "go" to "Go", "rs" to "Rust",
        "c" to "C", "h" to "C header", "cpp" to "C++", "cc" to "C++", "hpp" to "C++ header",
        "cs" to "C#", "swift" to "Swift", "m" to "Objective-C", "mm" to "Objective-C++",
        "rb" to "Ruby", "php" to "PHP", "dart" to "Dart", "scala" to "Scala",
        "sh" to "Shell", "bash" to "Bash", "zsh" to "Zsh", "ps1" to "PowerShell",
        "sql" to "SQL", "r" to "R", "lua" to "Lua", "pl" to "Perl", "ex" to "Elixir",
        "exs" to "Elixir", "erl" to "Erlang", "hs" to "Haskell", "clj" to "Clojure",
        "sol" to "Solidity", "vue" to "Vue", "svelte" to "Svelte", "html" to "HTML",
        "htm" to "HTML", "css" to "CSS", "scss" to "SCSS", "sass" to "Sass",
        "json" to "JSON", "jsonl" to "JSON Lines", "ndjson" to "JSON Lines",
        "yaml" to "YAML", "yml" to "YAML", "toml" to "TOML", "xml" to "XML",
        "svg" to "SVG", "ini" to "INI", "cfg" to "Config", "conf" to "Config",
        "properties" to "Properties", "gradle" to "Gradle", "cmake" to "CMake",
        "csv" to "CSV", "tsv" to "TSV", "diff" to "Diff", "patch" to "Patch",
        "graphql" to "GraphQL", "proto" to "Protobuf", "ipynb" to "Notebook JSON",
    )
    private val documentExtensions = setOf("txt", "md", "markdown", "rst", "log", "tex")
    private val codeNames = setOf("dockerfile", "makefile", "cmakelists.txt", ".gitignore", ".editorconfig")
    fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun language(name: String): String? = languages[extension(name)]
        ?: name.lowercase(Locale.ROOT).takeIf { it in codeNames }?.let { "Config" }
    fun supports(name: String) = language(name) != null || extension(name) in documentExtensions
    fun label(name: String) = language(name) ?: extension(name).uppercase(Locale.ROOT).ifBlank { "Text" }
}
