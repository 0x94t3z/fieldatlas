package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteChoiceTest {
    private fun src(n: Int, title: String, text: String = "$title.", excerpt: String = text) =
        PromptSource("S$n", Evidence("enw-$n", "enw-$n:0000", title, "Saved collection", text, 1.0), excerpt)

    @Test fun aSectionWhoseHeadingTheQuestionNamesIsReadFirst() {
        val q = "when did The Strokes release Is This It"
        assertTrue(NoteChoice.headingAsked(q, "The Strokes — History > 2001–2002: Is This It"))
        assertFalse(NoteChoice.headingAsked(q, "The Strokes — History > 2003–2004: Room on Fire"))
        assertFalse(NoteChoice.headingAsked(q, "The Strokes — Overview"))
        val sources = listOf(src(1, "The Strokes — History > 2003–2004: Room on Fire"), src(2, "The Strokes — Overview"),
            src(3, "Someday (The Strokes song) — Overview"), src(4, "The Strokes — History > 2001–2002: Is This It"))
        assertEquals(listOf(3, 0, 1, 2), NoteChoice.order(q, sources))
        // With no heading named, the packing order stands.
        assertEquals(listOf(0, 1, 2, 3), NoteChoice.order("how does a heat pump work", sources))
    }

    @Test fun aNoteShowsThePartWithTheAskedWordsWhenItsStartLacksThem() {
        val lead = "The 2016 United States presidential election was held on November 8, 2016. " +
            "Many candidates ran in the primaries that year and the campaign was long. ".repeat(8) +
            "Trump received 304 electoral votes, while Clinton received 227. Trump took office in 2017."
        val trump = src(1, "2016 United States presidential election — Overview", lead, excerpt = lead.take(700))
        assertTrue("304 electoral votes" in NoteChoice.excerpt("how many electoral votes did Trump have in 2016", trump, 700).take(700))
        val cowboys = "The Dallas Cowboys are a professional American Football team from the Dallas area. " +
            "Their stadium is in Arlington. ".repeat(20) + "The Cowboys are often known as \"America's Team\"."
        assertTrue("often known as \"America's Team\"" in NoteChoice.excerpt("why are the Dallas Cowboys considered Americas team",
            src(2, "Dallas Cowboys — Overview", cowboys, excerpt = cowboys.take(700)), 700).take(700))
        // A note whose start already has the asked words, or a short one, is read as packed.
        val start = "Trump received 304 electoral votes. " + "Filler sentence here. ".repeat(40)
        assertEquals(start.take(700), NoteChoice.excerpt("how many electoral votes did Trump have in 2016",
            src(3, "2016 United States presidential election — Overview", start, excerpt = start.take(700)), 700))
        assertEquals("Short.", NoteChoice.excerpt("how many electoral votes", src(4, "X — Overview", "Short."), 700))
    }

    @Test fun citationsOfTheNotesBecomeThePassagesOwnNumbers() {
        assertEquals("Released in October 2001 [S4]. Recorded in New York [S1].",
            NoteChoice.renumber("Released in October 2001 [S1]. Recorded in New York [S2].", listOf(3, 0, 1)))
        assertEquals("A claim .", NoteChoice.renumber("A claim [S5].", listOf(3, 0, 1)))
    }

    @Test fun aNoteCutMidWordReadsOnIntoItsNextChunk() {
        assertTrue(NoteChoice.cutMidWay("The Cowboys are often known as \"Ame"))
        assertFalse(NoteChoice.cutMidWay("They won five Super Bowls."))
        assertEquals("known as \"America's Team\".", NoteChoice.joined("known as \"Ame", "rica's Team\"."))
        // Chunks that overlap keep the shared text once.
        assertEquals("Trump received 304. Clinton received 227. Even though Trump lost the popular vote.",
            NoteChoice.joined("Trump received 304. Clinton received 227. Even though Tru",
                "Clinton received 227. Even though Trump lost the popular vote."))
        val first = Evidence("swp-31", "swp-31-0000:0000", "Dallas Cowboys — Overview", "Saved collection", "The Cowboys are often known as \"Ame", 1.0)
        val second = first.copy(chunkId = "swp-31-0000:0001", text = "rica's Team\". They play in Arlington.")
        val packed = PackedPrompt("p", listOf(PromptSource("S1", first, first.text)))
        val read = kotlinx.coroutines.runBlocking { NoteChoice.continued("why are the cowboys called americas team", packed, 3) { listOf(first, second) } }
        assertEquals("The Cowboys are often known as \"America's Team\". They play in Arlington.", read.sources[0].evidence.text)
        // Nothing to join, or no next chunk: the notes are as packed.
        val done = PackedPrompt("p", listOf(PromptSource("S1", second, second.text)))
        assertEquals(done, kotlinx.coroutines.runBlocking { NoteChoice.continued("q", done, 3) { listOf(first, second) } })
        assertEquals(packed, kotlinx.coroutines.runBlocking { NoteChoice.continued("q", packed, 3) { listOf(first) } })
    }
}
