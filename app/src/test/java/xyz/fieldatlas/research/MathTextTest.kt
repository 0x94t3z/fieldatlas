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

    @Test fun repeatingDecimalsKeepTheirBar() {
        // As the Prealgebra collection writes them; the command name used to show ("0.58overline3").
        assertEquals("0.583\u0305 is a repeating decimal", MathText.readable("\$0.58\\overline{3}\$ is a repeating decimal"))
        assertEquals("written 1.3\u0305.", MathText.readable("written \$1.\\overline{3}.\$"))
        assertEquals("0.4\u03055\u0305", MathText.readable("\$0.\\overline{45}\$"))
        assertEquals("0.7\u03051\u03054\u03052\u03058\u03055\u0305", MathText.readable("\\(0.\\overline{714285}\\)"))
        // Copied without math marks.
        assertEquals("1/3 = 0.3\u0305", MathText.readable("1/3 = 0.\\overline{3}"))
    }

    @Test fun stackedLayoutsReadAsOneLineOfSteps() {
        // As the Prealgebra collection writes worked steps and column sums; the layout's name used to show.
        assertEquals("m = rise/run; m = 3/3; m = 1", MathText.readable(
            "\$\\begin{matrix} \\\\ m = \\frac{\\text{rise}}{\\text{run}} \\\\ m = \\frac{3}{3} \\\\ m = 1 \\end{matrix}\$"))
        assertEquals("3(9 + 0.25); 27 + 0.75; 27.75", MathText.readable(
            "\$\$\\begin{matrix} 3(9 & + & 0.25) \\\\ 27 & + & 0.75 \\\\  & 27.75 &  \\end{matrix}\$\$"))
        // The rule under a sum has no text form; the carried digit stays.
        assertEquals("8³7; 93; +89; 269", MathText.readable(
            "\$\\begin{matrix} {8}^{3}7 \\\\ 93 \\\\ {+89}_{\\text{____}} \\\\ 269 \\end{matrix}\$"))
        assertEquals("47; ×100; 00; 000; 4700; 4,700", MathText.readable(
            "\$\\begin{matrix} 47 \\\\ {\\times100}_{\\text{_____}} \\\\ 00 \\\\ " +
                "{\\begin{matrix} 000 \\\\ 4700 \\end{matrix}}_{\\text{_____}} \\\\ 4,700 \\end{matrix}\$"))
        assertEquals("x = 1; y = 2", MathText.readable("\\[\\begin{array}{rcl} x & = & 1 \\\\ y & = & 2 \\end{array}\\]"))
        // Copied without math marks, and one that never closes.
        assertEquals("So 15; × 4; 60 is the product.", MathText.readable("So \\begin{matrix} 15 \\\\ \\times 4 \\\\ 60 \\end{matrix} is the product."))
        assertEquals("a + b", MathText.readable("\$\\begin{matrix} a + b\$"))
    }

    @Test fun moneyInsideAFormulaStaysMoney() {
        assertEquals("Divide: \$3.99÷24.", MathText.readable("Divide: \$\\text{\$3.99}\\div24.\$"))
        assertEquals("17·\$0.10=\$1.70; number·value=total value", MathText.readable(
            "\$\$\\begin{matrix} 17\\cdot\\text{\$0.10}=\\text{\$1.70} \\\\ \\text{number}\\cdot\\text{value}=\\text{total value} \\end{matrix}\$\$"))
    }

    @Test fun blanksAndTightOperators() {
        assertEquals("7 __ 9", MathText.readable("\$7 \\text{__} 9\$"))
        assertEquals("a·0=0 and 0·a=0", MathText.readable("\$a\\cdot0=0\$ and \$0\\cdot a=0\$"))
        assertEquals("3 × a", MathText.readable("\$3 \\times a\$"))
        assertEquals("sin x", MathText.readable("\$\\sin x\$"))
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

    @Test fun numbersBetweenMathMarksAreNotPrices() {
        assertEquals("Sum is 17.0 (or 17.00).", MathText.readable("Sum is \$17.0\$ (or \$17.00\$)."))
        assertEquals("Add: 3.7+12.4.", MathText.readable("Add: \$3.7+12.4.\$"))
        assertEquals("the sum of 3 and x", MathText.readable("the sum of \$3\$ and \$x\$"))
        for (text in listOf("from \$5-\$10 or \$5/\$10", "It is \$5 (\$4.50 after tax) or \$6.", "echo \$HOME and \$PATH; \$a.\$b; \$1\$2",
                "\$BTC/\$ETH", "The total cost was \$1,875."))
            assertEquals(text, MathText.readable(text))
    }

    @Test fun aSavedPassageShowsItsMathAsReadableText() {
        // Lines of the Prealgebra collection, as saved.
        assertEquals("Write the numbers vertically so the decimal points line up. | 3.7; +12.4",
            MathText.display("Write the numbers vertically so the decimal points line up. | \$\\begin{matrix} 3.7 \\\\ {+12.4}_{\\text{_____}} \\end{matrix}\$"))
        assertEquals("Simplify 70/100.", MathText.display("Simplify \$\\frac{70}{100}.\$"))
        assertEquals("Add the ones: 8+1=9Add the tens: 2+6=8", MathText.display("Add the ones: \$8+1=9\$Add the tens: \$2+6=8\$"))
        assertEquals("412 12/4 | Twelve divided by four", MathText.display("\$412\$\$12/4\$ | \$\\text{Twelve divided by four}\$"))
        assertEquals("a balance of −\$23 and a \$20 bill", MathText.display("a balance of \$\\text{−\$23}\$ and a \$\\text{\$20}\$ bill"))
        assertEquals("> Simplify 70/100.", MathText.display("> Simplify \$\\frac{70}{100}.\$"))
        // Nothing but marked math changes: code, paths and prices read as published.
        for (text in listOf("Run C:\\to\\file with snake_case_name and CO_2.", "It costs \$5 to \$10 a night.", "Three \$100 bills, 3 times \$100 equals \$300."))
            assertEquals(text, MathText.display(text))
    }
}
