package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.text.MarkdownLiteral

class ClockConflictTest {
    private fun source(id: String, name: String, text: String) =
        PromptSource(id, Evidence("attachment:$name", name, name, "Attached file", text, 0.0))

    private val first = source("S1", "notice-a.txt",
        "The autumn community meeting will be held in the parish hall on Saturday 18 October. " +
            "Doors open at 17:30 and the meeting starts at 18:00. Please bring your plot number.")
    private val second = source("S2", "notice-b.txt",
        "Reminder: autumn community meeting, Saturday 18 October, parish hall.\n" +
            "The meeting starts at 19:00 because the hall is booked for a children's class until 18:30.")

    @Test fun anUncitedSourceWithAnotherTimeIsQuotedUnchanged() {
        val question = "When does the allotment community meeting start?"
        val note = ClockConflict.note(question, "The meeting starts at 18:00 [S1].", listOf(first, second))!!
        assertTrue(note.endsWith("[S2] _Check which one is current._"))
        assertTrue(MarkdownLiteral.decode(note).contains("“The meeting starts at 19:00 because the hall is booked for a children's class until 18:30.”"))
        // Citing both, or agreeing on the time, is not a conflict to disclose.
        assertNull(ClockConflict.note(question, "It starts at 18:00 [S1] or 19:00 [S2].", listOf(first, second)))
        assertNull(ClockConflict.note(question, "The meeting starts at 19:00 [S2].",
            listOf(second, source("S3", "flyer.txt", "The community meeting starts at 19.00 in the hall."))))
    }

    @Test fun quantitiesAndComparisonsAreNotTreatedAsDisagreements() {
        val ipv4 = source("S1", "IPv4", "IPv4 uses a 32-bit address space.")
        val ipv6 = source("S2", "IPv6", "IPv6 addresses are 128 bits long.")
        assertNull(ClockConflict.note("How long is an IPv4 address?", "An IPv4 address has 32 bits [S1].", listOf(ipv4, ipv6)))
        val opening = source("S2", "hours.txt", "The library opens at 09:00 on weekdays.")
        assertNull(ClockConflict.note("How does the morning shift differ from the evening shift?",
            "The morning shift starts at 08:00 [S1].", listOf(first, opening)))
        // A time in an unrelated sentence does not count.
        assertNull(ClockConflict.note("When does the allotment community meeting start?", "The meeting starts at 18:00 [S1].",
            listOf(first, source("S2", "bus.txt", "The last bus leaves at 23:10."))))
    }
}
