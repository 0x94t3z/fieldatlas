package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class MathTextTest {
    @Test fun chemicalFormulasFromTheModelReadAsWritten() {
        // As a 2B model wrote it on the phone (14 October 2026); Markdown turned the underscores into italics.
        val answer = "a chloroplast uses carbon dioxide (\$\\text{CO}_2\$) and water (\$\\text{H}_2\\text{O}\$) " +
            "to form sugar and gives off oxygen (\$\\text{O}_2\$). [S1]"
        assertEquals("a chloroplast uses carbon dioxide (CO₂) and water (H₂O) to form sugar and gives off oxygen (O₂). [S1]",
            MathText.readable(answer))
    }

    @Test fun formulasAndSymbols() {
        assertEquals("6CO₂ + 6H₂O → C₆H₁₂O₆ + 6O₂", MathText.readable("\$\\ce{6CO2 + 6H2O -> C6H12O6 + 6O2}\$"))
        assertEquals("E = mc²", MathText.readable("\$E = mc^2\$"))
        assertEquals("v = d/t", MathText.readable("\\(v = \\frac{d}{t}\\)"))
        assertEquals("speed = (100 km)/(2 h) = 50 km/h", MathText.readable("\$\$\\text{speed} = \\frac{100\\ \\text{km}}{2\\ \\text{h}} = 50\\text{ km/h}\$\$"))
        assertEquals("3 × 10⁸ m/s", MathText.readable("\$3 \\times 10^{8}\$ m/s"))
        assertEquals("20 °C", MathText.readable("\$20\\,^\\circ\$C"))
        assertEquals("ΔG ≤ 0", MathText.readable("\\(\\Delta G \\leq 0\\)"))
        assertEquals("√(a² + b²)", MathText.readable("\$\\sqrt{a^2 + b^2}\$"))
        assertEquals("xₘₐₓ", MathText.readable("\$x_{max}\$"))
        // No Unicode subscript for every letter: the script stays readable in brackets.
        assertEquals("v_(best)", MathText.readable("\$v_{best}\$"))
    }

    @Test fun formulasWithoutMathMarks() {
        assertEquals("CO₂ and H₂O", MathText.readable("CO_2 and H_2O"))
        assertEquals("Ca₃(PO₄)₂", MathText.readable("Ca_3(PO_4)_2"))
    }

    @Test fun pricesCodeAndQuotesStayAsWritten() {
        for (text in listOf("It costs \$5 to \$10 a night.", "Tickets are US\$12 or \$15.", "Call block_hash() or snake_case_name.",
                "> Source line with \$\\text{CO}_2\$ as published", "Use the S8 train [S1].")) {
            assertEquals(text, MathText.readable(text))
        }
    }
}
