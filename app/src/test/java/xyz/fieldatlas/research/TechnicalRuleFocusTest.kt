package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class TechnicalRuleFocusTest {
    @Test fun `extracts synthetic owner function and literal without selecting an answer`() {
        for ((owner, symbol, literal) in listOf(
            Triple("QRS-42", "readFlag", "0xface"),
            Triple("ABC-7619", "isReady", "0x1234"),
            Triple("XYZ-17", "read_status", "0x0000"),
        )) {
            val focus = TechnicalRuleFocus.from("Under $owner, what should $symbol return for $literal?")!!
            assertEquals(owner, focus.owner)
            assertEquals(symbol.lowercase(), focus.symbol)
            assertEquals(literal, focus.literal)
            for (result in listOf("true", "false", "7", "ERROR")) {
                assertTrue(focus.matches("$owner: Specification", "$symbol returns $result for $literal."))
            }
        }
    }

    @Test fun `supports explicit call and backtick symbols`() {
        assertEquals("status", TechnicalRuleFocus.from("Under ABC-9, what does status(0x01) return?")!!.symbol)
        assertEquals("ready", TechnicalRuleFocus.from("Under ABC-9, what does `ready` return for 0x01?")!!.symbol)
    }

    @Test fun `hex and symbol are case normalized without changing leading zeros`() {
        val focus = TechnicalRuleFocus.from("Under qrs 42, what does ReadFlag return for 0X00FAcE?")!!
        assertEquals("QRS-42", focus.owner)
        assertEquals("0x00face", focus.literal)
        assertTrue(focus.matches("QRS-42: Rule", "READFLAG returns true for 0X00FACE."))
        assertFalse(focus.matches("QRS-42: Rule", "readFlag returns true for 0xface."))
    }

    @Test fun `normalizes compatibility characters before parsing`() {
        assertNotNull(TechnicalRuleFocus.from("Under ＱＲＳ-４２, what does readFlag return for ０ｘＦＡＣＥ?"))
    }

    @Test fun `existing proposal aliases are scoped to the title owner`() {
        val focus = TechnicalRuleFocus.from("Under ERC165, what should supportsInterface return for 0xffffffff?")!!
        assertTrue(focus.matches("EIP-165: Standard Interface Detection — Specification", "supportsInterface returns false for 0xffffffff."))
        assertFalse(focus.matches("ERC-6604: ERC-165 extension", "ERC-165 supportsInterface returns true for 0xffffffff."))
        assertFalse(focus.matches("ERC-1650: Specification", "supportsInterface returns false for 0xffffffff."))
    }

    @Test fun `declines missing or ambiguous lookup components`() {
        for (question in listOf(
            "Explain QRS-42 and readFlag with 0xface.",
            "Under QRS-42, what is the result for 0xface?",
            "What does readFlag return for 0xface?",
            "Under QRS-42, what does readFlag return?",
            "Under QRS-42 and ABC-7, what does readFlag return for 0xface?",
            "Under QRS-42, what do readFlag and isReady return for 0xface?",
            "Under QRS-42, what does readFlag return for 0xface and 0xcafe?",
            "Under QRS-42, what does readFlag return for x0xface?",
            "Under QRS-42, what does readFlag return for 0xfaceZ?",
        )) assertNull(question, TechnicalRuleFocus.from(question))
    }

    @Test fun `long input cannot activate from a truncated or unseen identifier`() {
        assertNull(TechnicalRuleFocus.from(" ".repeat(512) + "Under QRS-42, what does readFlag return for 0xface?"))
        val prefix = "Under QRS-42, what does readFlag return for "
        assertNull(TechnicalRuleFocus.from(prefix + " ".repeat(508 - prefix.length) + "0xface"))
        assertNull(TechnicalRuleFocus.from("Under QRS-42, what does readFlag return for 0xface? " + " ".repeat(512) + "ABC-17"))
    }

    @Test fun `FTS punctuation and operators cannot escape validated fields`() {
        val focus = TechnicalRuleFocus.from("Under QRS-42, what does readFlag return for `0xface`? \" OR (NEAR *)")!!
        assertEquals("{title} : (^\"QRS 42\") AND {text} : \"readflag\" AND {text} : \"0xface\"", focus.matchExpression)
    }

    @Test fun `body exact boundaries reject prefixed suffixed and underscored identifiers`() {
        val focus = TechnicalRuleFocus.from("Under QRS-42, what does readFlag return for 0xface?")!!
        for (body in listOf(
            "readFlag returns false for 0xface0", "readFlag returns false for x0xface",
            "readFlag returns false for 0xface_suffix", "readFlagExtra returns false for 0xface",
            "xreadFlag returns false for 0xface", "readFlag_suffix returns false for 0xface",
            "\$readFlag returns false for 0xface",
        )) assertFalse(body, focus.matches("QRS-42: Specification", body))
        assertTrue(focus.matches("QRS-42: Specification", "`readFlag(0xface)` returns false."))
    }

    @Test fun `title keywords and indexing metadata are not a matching body`() {
        val focus = TechnicalRuleFocus.from("Under QRS-42, what does readFlag return for 0xface?")!!
        assertFalse(focus.matches("QRS-42: readFlag 0xface", "A general summary."))
        assertFalse(focus.matches("QRS-42: Specification", "MeSH: readFlag 0xface\nA general summary."))
        assertFalse(focus.matches("ABC-42: QRS-42", "readFlag returns false for 0xface."))
    }

    @Test fun `priority is stable and does not rewrite scores source identities or polarity`() {
        val focus = TechnicalRuleFocus.from("Under QRS-42, what does readFlag return for 0xface?")!!
        fun row(id: String, body: String) = Evidence(id, "$id:0", "QRS-42: Rule", "fixture://$id", body, -2.0)
        val summary = row("summary", "General readFlag information.")
        val yes = row("yes", "readFlag returns true for 0xface.")
        val no = row("no", "readFlag returns false for 0xface.")
        assertEquals(listOf(yes, no, summary), focus.prioritize(listOf(summary, yes, no)))
        assertEquals(listOf(no, yes, summary), focus.prioritize(listOf(summary, no, yes)))
    }
}
