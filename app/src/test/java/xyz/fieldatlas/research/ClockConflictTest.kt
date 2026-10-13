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

    @Test fun theSameTimeInTwelveHourFormIsNotAConflict() {
        val question = "When does the last ferry to Koh Tao leave?"
        val a = source("S1", "a.txt", "The last ferry to Koh Tao leaves at 21:30.")
        assertNull(ClockConflict.note(question, "The last ferry to Koh Tao leaves at 21:30 [S1].",
            listOf(a, source("S2", "b.txt", "The last ferry to Koh Tao departs at 9:30 pm."))))
        assertNotNull(ClockConflict.note(question, "The last ferry to Koh Tao leaves at 21:30 [S1].",
            listOf(a, source("S2", "c.txt", "The last ferry to Koh Tao departs at 8:30 pm."))))
    }

    @Test fun anOpeningAndAClosingTimeAreNotAConflict() {
        val question = "What time does the Vasa Museum open?"
        val a = source("S1", "a.txt", "The Vasa Museum opens at 10:00.")
        assertNull(ClockConflict.note(question, "The Vasa Museum opens at 10:00 [S1].",
            listOf(a, source("S2", "b.txt", "The Vasa Museum closes at 17:00 on weekdays."))))
        assertNotNull(ClockConflict.note(question, "The Vasa Museum opens at 10:00 [S1].",
            listOf(a, source("S2", "c.txt", "The Vasa Museum opens at 09:00 in summer."))))
    }

    @Test fun aTimeThatEndsASentenceIsStillATime() {
        assertNotNull(ClockConflict.note("When does the last ferry depart?", "The last ferry departs at 21:00 [S1].",
            listOf(source("S1", "a.txt", "The last ferry departs at 21:00 daily"), source("S2", "b.txt", "The last ferry departs at 22:00."))))
    }

    @Test fun aFerryFromAnotherPortIsStillADeparture() {
        assertNotNull(ClockConflict.note("When does the last ferry to Koh Tao leave?", "The last ferry to Koh Tao leaves at 21:30 [S1].",
            listOf(source("S1", "a.txt", "The last ferry to Koh Tao leaves at 21:30."),
                source("S2", "b.txt", "The last ferry from Koh Samui to Koh Tao is at 20:00."))))
    }

    @Test fun pricesAndDecimalsAreNotClockTimes() {
        val question = "How much is admission to the city museum?"
        val a = source("S1", "museum-a.txt", "City museum admission is €12.50 for adults and free for children.")
        val b = source("S2", "museum-b.txt", "The city museum admission fee is 15.00 EUR for adults.")
        assertNull(ClockConflict.note(question, "Admission to the city museum is €12.50 for adults [S1].", listOf(a, b)))
        assertNull(ClockConflict.note(question, "Admission is 12.50 [S1].", listOf(a, source("S2", "c.txt", "City museum admission costs 15.00 per adult."))))
        // Ranges and lists of prices are not times either.
        assertNull(ClockConflict.note(question, "Admission is €12.50 [S1].",
            listOf(a, source("S2", "d.txt", "City museum admission is €12.50 and 15.00 for groups."))))
        assertNull(ClockConflict.note(question, "Admission is €12.50 [S1].",
            listOf(a, source("S2", "e.txt", "City museum admission ranges 12.50–15.00 EUR."))))
        // A colon time is a time whatever word comes before it.
        assertNotNull(ClockConflict.note("When does the city museum open?", "The city museum opens at 09:00 [S1].",
            listOf(source("S1", "a.txt", "The city museum opens at 09:00 daily."),
                source("S2", "f.txt", "The city museum is open from 10:00 on weekdays."))))
        // A dotted time beside time words still counts.
        val hours = source("S2", "hours.txt", "The city museum opens at 10.00 and admission is €12.50.")
        assertNotNull(ClockConflict.note("When does the city museum open?", "The city museum opens at 09:00 [S1].",
            listOf(source("S1", "a.txt", "The city museum opens at 09:00 daily."), hours)))
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
