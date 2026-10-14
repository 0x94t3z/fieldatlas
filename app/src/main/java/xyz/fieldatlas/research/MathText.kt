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
    private val bareCommand = Regex("\\\\(text|mathrm|ce|frac|times|cdot|rightarrow|to|approx|leq|geq|neq|degree|circ|pm)\\b")

    fun readable(text: String): String {
        if ('$' !in text && '\\' !in text && '_' !in text) return text
        return text.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith(">")) line else readableLine(line)
        }
    }

    private fun readableLine(line: String): String {
        var out = displayDollars.replace(line) { convert(it.groupValues[1]) }
        out = bracketed.replace(out) { convert(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
        out = inlineDollars.replace(out) { match ->
            val body = match.groupValues[1]
            if (looksLikeMath.containsMatchIn(body)) convert(body) else match.value
        }
        // LaTeX written without math marks: only the common commands, so other backslashes stay.
        if (bareCommand.containsMatchIn(out)) out = convert(out, keepSpaces = true)
        return chemicalSubscript.replace(out) { script(it.groupValues[1], SUBSCRIPTS) ?: it.value }
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
                    c == '_' || c == '^' -> {
                        pos++
                        val argument = argument()
                        val table = if (c == '_') SUBSCRIPTS else SUPERSCRIPTS
                        when {
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
                "left", "right", "big", "Big", "bigl", "bigr", "displaystyle", "limits" -> ""
                "quad", "qquad" -> " "
                else -> (SYMBOLS[name] ?: name).also { symbol ->
                    // LaTeX drops the space after a command name: "\\Delta G" is ΔG, a quantity, while an
                    // operator ("3 \\times 10") keeps its spacing on both sides.
                    if (symbol.length == 1 && symbol[0].isLetter()) {
                        var next = pos
                        while (next < source.length && source[next] == ' ') next++
                        if (next > pos && next < source.length && source[next].isLetter()) pos = next
                    }
                }
            }
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
