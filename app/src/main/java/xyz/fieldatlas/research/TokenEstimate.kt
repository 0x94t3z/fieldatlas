package xyz.fieldatlas.research

/**
 * Upper-bound token count for the Qwen tokenizer without loading it. Calibrated against the
 * real tokenizer on prose, code, CSV, escaped JSON and eleven scripts
 * (app/src/test/resources/token-estimate-samples.json): it never undercounts there, and
 * overcounts English by about a third. Counting UTF-8 bytes instead overcounts English about
 * fourfold, which wastes most of the context on a phone.
 *
 * Digits are counted one token each because this tokenizer splits every digit, so number-heavy
 * text (tables, logs) stays safe.
 */
object TokenEstimate {
    fun of(text: String): Int {
        var total = 0.0
        var index = 0
        while (index < text.length) {
            val kind = kind(text.codePointAt(index))
            var end = index
            var run = 0
            while (end < text.length) {
                val point = text.codePointAt(end)
                if (kind(point) != kind) break
                end += Character.charCount(point)
                run++
            }
            total += when (kind) {
                ASCII_LETTER -> 1.0 + (run - 1) / 5
                DIGIT -> run.toDouble()
                PUNCTUATION -> maxOf(1L, Math.round(run * 0.7)).toDouble()
                NEWLINE -> 1.0
                SPACE -> if (run == 1) 0.0 else 1.0
                DENSE_SCRIPT -> run * 0.65 + 0.5
                OTHER_LETTER -> 1.0 + run * 0.3
                else -> run * 2.4
            }
            index = end
        }
        return (total * 1.05).toInt() + 2
    }

    private const val ASCII_LETTER = 0
    private const val DIGIT = 1
    private const val PUNCTUATION = 2
    private const val NEWLINE = 3
    private const val SPACE = 4
    private const val DENSE_SCRIPT = 5
    private const val OTHER_LETTER = 6
    private const val SYMBOL = 7

    private fun kind(point: Int): Int = when {
        point in '0'.code..'9'.code -> DIGIT
        point in 'a'.code..'z'.code || point in 'A'.code..'Z'.code -> ASCII_LETTER
        point == ' '.code || point == '\t'.code -> SPACE
        point == '\n'.code || point == '\r'.code -> NEWLINE
        point < 128 -> PUNCTUATION
        // Kana, CJK ideographs and Hangul: the tokenizer packs these densely.
        point in 0x3040..0x30FF || point in 0x3400..0x4DBF || point in 0x4E00..0x9FFF ||
            point in 0xAC00..0xD7AF || point in 0xF900..0xFAFF -> DENSE_SCRIPT
        Character.isLetter(point) || Character.getType(point).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
        } -> OTHER_LETTER
        Character.isSpaceChar(point) -> SPACE
        else -> SYMBOL
    }
}
