package xyz.fieldatlas.research

/**
 * Turns the LaTeX a model writes ("carbon dioxide ($\text{CO}_2$)") into text a phone shows as
 * written: "carbon dioxide (CO₂)". The answer is rendered as Markdown, not math, so LaTeX left
 * in place shows its commands, and its underscores turn the words between them into italics.
 *
 * Only math the model marks as math is rewritten: `$…$`, `$$…$$`, `\(…\)` and `\[…\]`. A dollar
 * span must look like math (a command, a sub/superscript or braces), so "$5 to $10" is a price.
 * A chemical formula written without math marks ("CO_2", "H_2O") gets its subscripts too.
 * Quoted source lines ("> …") are left exactly as published.
 */
object MathText {
    private val displayDollars = Regex("\\$\\$([^$]+?)\\$\\$")
    private val bracketed = Regex("\\\\\\[(.+?)\\\\\\]|\\\\\\((.+?)\\\\\\)", RegexOption.DOT_MATCHES_ALL)
    // Content may not start or end with a space, as a price range ("$5 and $10") would. A unit may
    // follow the closing mark directly ("$20\\,^\\circ$C").
    private val inlineDollars = Regex("(?<![\\\\$\\w])\\$(?=\\S)([^$\\n]{1,300}?)(?<=\\S)\\$(?!\\$)")
    private val looksLikeMath = Regex("\\\\[A-Za-z]|[_^{}]")
    private val chemicalSubscript = Regex("(?<=[A-Z]|[A-Z][a-z]|\\))_\\{?(\\d{1,2})\\}?")
    private val moneyInText = Regex("(\\\\text\\{)\\$(?=\\d)")
    private val bareCommand = Regex("\\\\(text|mathrm|ce|frac|times|cdot|rightarrow|to|approx|leq|geq|neq|degree|circ|pm|overline|begin)\\b")

    fun readable(text: String): String {
        if ('$' !in text && '\\' !in text && '_' !in text) return text
        return text.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith(">")) line else readableLine(line)
        }
    }

    private fun readableLine(source: String): String {
        // An amount of money inside a formula ("$\text{$3.99}\div24$") is not the end of the formula.
        val line = moneyInText.replace(source, "$1$MONEY")
        var out = displayDollars.replace(line) { convert(it.groupValues[1]) }
        out = bracketed.replace(out) { convert(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
        out = inlineDollars.replace(out) { match ->
            val body = match.groupValues[1]
            if (looksLikeMath.containsMatchIn(body)) convert(body) else match.value
        }
        // LaTeX written without math marks: only the common commands, so other backslashes stay.
        if (bareCommand.containsMatchIn(out)) out = convert(out, keepSpaces = true)
        return chemicalSubscript.replace(out) { script(it.groupValues[1], SUBSCRIPTS) ?: it.value }.replace(MONEY, '$')
    }

    /** One math expression as plain text. */
    internal fun convert(math: String, keepSpaces: Boolean = false): String {
        val text = Parser(math).expression()
        return if (keepSpaces) text else text.replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    private class Parser(val source: String) {
        var pos = 0

        fun expression(stopAtBrace: Boolean = false): String {
            val out = StringBuilder()
            while (pos < source.length) {
                val c = source[pos]
                when {
                    c == '}' && stopAtBrace -> return out.toString()
                    c == '{' -> { pos++; out.append(expression(stopAtBrace = true)); if (pos < source.length) pos++ }
                    c == '}' -> pos++
                    c == '\\' -> out.append(command())
                    c == '_' && pos + 1 < source.length && source[pos + 1] == '_' -> {
                        // A blank to fill in ("7 __ 9"), kept as written.
                        while (pos < source.length && source[pos] == '_') { out.append('_'); pos++ }
                    }
                    c == '_' || c == '^' -> {
                        pos++
                        val argument = argument()
                        val table = if (c == '_') SUBSCRIPTS else SUPERSCRIPTS
                        when {
                            // The rule under a column sum ("{+ 8,596}_{\text{_____}}") has no text form.
                            c == '_' && argument.isNotBlank() && argument.all { it == '_' || it == ' ' } -> Unit
                            c == '^' && argument.trim() in setOf("°", "\\circ", "circ") -> out.append('°')
                            else -> out.append(script(argument, table) ?: (if (c == '_') "_" else "^") +
                                if (argument.length == 1) argument else "($argument)")
                        }
                    }
                    c == '~' -> { out.append(' '); pos++ }
                    else -> { out.append(c); pos++ }
                }
            }
            return out.toString()
        }

        /** A braced group or a single character or command, as text. */
        fun argument(): String {
            while (pos < source.length && source[pos] == ' ') pos++
            if (pos >= source.length) return ""
            return when (source[pos]) {
                '{' -> { pos++; expression(stopAtBrace = true).also { if (pos < source.length) pos++ } }
                '\\' -> command()
                else -> source[pos++].toString()
            }
        }

        fun command(): String {
            pos++ // the backslash
            if (pos >= source.length) return ""
            val start = pos
            if (!source[pos].isLetter()) {
                pos++
                return when (val symbol = source[start]) {
                    ',', ';', ':', ' ' -> " "
                    '!' -> ""
                    '\\' -> " "
                    else -> symbol.toString() // \% \$ \{ \} \_ \&
                }
            }
            while (pos < source.length && source[pos].isLetter()) pos++
            val name = source.substring(start, pos)
            return when (name) {
                "text", "mathrm", "mathbf", "mathit", "textbf", "textit", "operatorname", "mathsf", "boldsymbol", "mbox" -> argument()
                "ce" -> chemistry(argument())
                "frac", "dfrac", "tfrac" -> {
                    val top = argument().trim(); val bottom = argument().trim()
                    "${grouped(top)}/${grouped(bottom)}"
                }
                "sqrt" -> {
                    val index = if (pos < source.length && source[pos] == '[') {
                        val end = source.indexOf(']', pos)
                        if (end < 0) "" else source.substring(pos + 1, end).also { pos = end + 1 }
                    } else ""
                    val radicand = argument().trim()
                    (script(index, SUPERSCRIPTS) ?: index) + "√" + grouped(radicand)
                }
                // A repeating decimal ("0.58\\overline{3}"): the bar goes over each character it covers.
                // The reading font has no overline, so the phone's own font draws these characters.
                "overline", "bar" -> argument().trim().map { if (it == ' ') " " else "$it$OVERLINE" }.joinToString("")
                "begin" -> environment(argument().trim())
                "end" -> { argument(); "" }
                "left", "right", "big", "Big", "bigl", "bigr", "displaystyle", "limits" -> ""
                "quad", "qquad" -> " "
                else -> (SYMBOLS[name] ?: name).also { symbol ->
                    // LaTeX drops the space after a command name: "\\Delta G" is ΔG, a quantity, while an
                    // operator ("3 \\times 10") keeps its spacing on both sides.
                    // An operator written tight on its left ("0\\cdot a") is tight on its right too.
                    val tightOperator = symbol.length == 1 && name in SYMBOLS && start >= 2 && source[start - 2] != ' '
                    if (symbol.length == 1 && (symbol[0].isLetter() || tightOperator)) {
                        var next = pos
                        while (next < source.length && source[next] == ' ') next++
                        if (next > pos && next < source.length && source[next].isLetter()) pos = next
                    }
                }
            }
        }

        /**
         * A stacked layout (worked steps or a column sum between `\begin{matrix}` and `\end{matrix}`)
         * as one line of text, its rows set apart by "; ". Text cannot keep the columns, so cells are
         * spaced.
         */
        fun environment(name: String): String {
            val bodyStart = pos
            var depth = 0
            var i = pos
            var bodyEnd = -1
            while (i < source.length) {
                if (source[i] != '\\') { i++; continue }
                when {
                    source.startsWith("\\begin{", i) -> { depth++; i += 7 }
                    source.startsWith("\\end{", i) -> {
                        if (depth == 0) { bodyEnd = i; break }
                        depth--; i += 5
                    }
                    else -> i += 2 // an escaped character is never a brace or a row break
                }
            }
            if (bodyEnd < 0) return "" // never closed: the rest reads as ordinary math
            val close = source.indexOf('}', bodyEnd)
            pos = if (close < 0) source.length else close + 1
            var body = source.substring(bodyStart, bodyEnd)
            // The column letters of an array ("{rcl}") are not content.
            if (name.startsWith("array")) body = body.trimStart().let { if (it.startsWith("{")) it.substringAfter('}') else it }
            return rows(body).map { Parser(it).expression().replace(Regex("[ \\t]{2,}"), " ").trim() }
                .filter(String::isNotEmpty).joinToString("; ")
        }

        /** [body] cut at its own row breaks, its own column marks as spaces; a nested layout stays whole. */
        private fun rows(body: String): List<String> {
            val rows = mutableListOf<String>()
            val row = StringBuilder()
            var braces = 0
            var nested = 0
            var i = 0
            while (i < body.length) {
                val c = body[i]
                val top = braces == 0 && nested == 0
                when {
                    c == '\\' && body.startsWith("\\begin{", i) -> { nested++; row.append("\\begin"); i += 6 }
                    c == '\\' && body.startsWith("\\end{", i) -> { nested--; row.append("\\end"); i += 4 }
                    c == '\\' && top && body.startsWith("\\\\", i) -> { rows += row.toString(); row.clear(); i += 2 }
                    c == '\\' -> { row.append(body, i, minOf(i + 2, body.length)); i += 2 }
                    c == '{' -> { braces++; row.append(c); i++ }
                    c == '}' -> { braces--; row.append(c); i++ }
                    c == '&' && top -> { row.append(' '); i++ }
                    else -> { row.append(c); i++ }
                }
            }
            rows += row.toString()
            return rows
        }
    }

    /** "H2O" in \ce{} is H₂O: a digit straight after an element or a bracket is a subscript. */
    private fun chemistry(formula: String): String =
        Regex("(?<=[A-Za-z)\\]])(\\d+)").replace(formula) { script(it.value, SUBSCRIPTS) ?: it.value }
            .replace("->", "→").replace("<=>", "⇌")

    private fun grouped(part: String): String =
        if (part.length <= 1 || part.all { it.isLetterOrDigit() || it == '.' || it in SUBSCRIPTS.values || it in SUPERSCRIPTS.values }) part
        else "($part)"

    /** [text] in Unicode sub- or superscript characters, or null when one of them has none. */
    private fun script(text: String, table: Map<Char, Char>): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it !in table }) return null
        return trimmed.map(table::getValue).joinToString("")
    }

    private const val OVERLINE = '\u0305'
    private const val MONEY = '\uE000' // stands in for a "$" that is money, while the math marks are read

    private val SUBSCRIPTS = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅',
        '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'i' to 'ᵢ', 'n' to 'ₙ', 'm' to 'ₘ', 'k' to 'ₖ', 't' to 'ₜ')
    private val SUPERSCRIPTS = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵',
        '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '−' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'T' to 'ᵀ')

    private val SYMBOLS = mapOf(
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
        "approx" to "≈", "sim" to "~", "simeq" to "≃", "equiv" to "≡", "propto" to "∝", "infty" to "∞",
        "to" to "→", "rightarrow" to "→", "longrightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
        "Leftarrow" to "⇐", "leftrightarrow" to "↔", "Leftrightarrow" to "⇔", "rightleftharpoons" to "⇌",
        "uparrow" to "↑", "downarrow" to "↓", "degree" to "°", "circ" to "°",
        "ldots" to "…", "dots" to "…", "cdots" to "⋯", "partial" to "∂", "nabla" to "∇",
        "sum" to "Σ", "prod" to "Π", "int" to "∫", "in" to "∈", "notin" to "∉", "subset" to "⊂",
        "cup" to "∪", "cap" to "∩", "forall" to "∀", "exists" to "∃", "neg" to "¬", "land" to "∧", "lor" to "∨",
        "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "prime" to "′",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν",
        "xi" to "ξ", "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "varphi" to "φ",
        "chi" to "χ", "psi" to "ψ", "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ",
        "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "log" to "log", "ln" to "ln", "sin" to "sin", "cos" to "cos", "tan" to "tan", "exp" to "exp",
        "min" to "min", "max" to "max", "lim" to "lim",
    )
}
