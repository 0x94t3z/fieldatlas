package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Failures reported from an offline emulator test of 1.2.0, reduced to the checks that catch them. */
class AnswerChecksTest {
    private fun file(name: String, text: String) = Evidence("attachment:$name", "$name:p1:0", name, "Attached file", text, 0.0)

    private val revisionA = file("Helios_Pump_Revision_A.txt",
        "Helios pump model HX-44 revision A. Applies only to serial numbers 1000 through 1999. Use filter K-7 and connector M2. " +
            "Service interval: 400 operating hours. This revision does not apply to serial numbers 2000 or greater.")
    private val revisionB = file("Helios_Pump_Revision_B.txt",
        "Helios pump model HX-44 revision B. Applies only to serial numbers 2000 through 2999. Use filter K-9 and connector M5. " +
            "Service interval: 600 operating hours.")

    @Test fun aCitationMovesToTheRevisionItsDetailsComeFrom() {
        val answer = "Your Helios HX-44 serial number (2345) falls within the range covered by Revision B [1], which applies to 2000–2999.\n" +
            "Since your serial is 2345, Revision A does not apply [2]."
        assertEquals(
            "Your Helios HX-44 serial number (2345) falls within the range covered by Revision B [S2], which applies to 2000–2999.\n" +
                "Since your serial is 2345, Revision A does not apply [S1].",
            AnswerChecks.repairCitations(answer, listOf(revisionA, revisionB)),
        )
    }

    @Test fun correctOrMixedCitationsStayPut() {
        val correct = "Revision B uses filter K-9 [S2]. Revision A uses K-7 [S1]."
        assertEquals(correct, AnswerChecks.repairCitations(correct, listOf(revisionA, revisionB)))
        val both = "Unlike Revision A with K-7, Revision B uses K-9 [S1]."
        assertEquals(both, AnswerChecks.repairCitations(both, listOf(revisionA, revisionB)))
        val shared = "Both revisions cover the HX-44 pump [S1]."
        assertEquals(shared, AnswerChecks.repairCitations(shared, listOf(revisionA, revisionB)))
        val plain = "The plan to service it is sound [S1]."
        assertEquals(plain, AnswerChecks.repairCitations(plain, listOf(revisionA, revisionB)))
    }

    private val travel = file("Orion_Travel_Plan.txt",
        "Late shuttle from Pine Station to Cedar Lodge: last departure 21:30. Cedar Lodge reception closes at 22:30. " +
            "There is no after-hours entry or key box. Station Hotel has a front desk open 24 hours. It does not arrange transport.")

    @Test fun aTwelveHourTimeIsCorrectedToTheStatedTwentyFourHourTime() {
        assertEquals("Late shuttle service ends at 9:30 PM (21:30) [S1].",
            AnswerChecks.repairTimes("Late shuttle service ends at 10:30 PM (21:30) [S1].", listOf(travel)))
        assertEquals("Reception closes at 22:30 (10:30 PM).",
            AnswerChecks.repairTimes("Reception closes at 22:30 (11:30 PM).", listOf(travel)))
        val right = "Reception closes at 10:30 PM (22:30)."
        assertEquals(right, AnswerChecks.repairTimes(right, listOf(travel)))
        val unstated = "Breakfast starts at 8:00 AM (07:00)."
        assertEquals(unstated, AnswerChecks.repairTimes(unstated, listOf(travel)))
    }

    @Test fun anItemTheFilesNeverMentionIsReportedMissing() {
        assertEquals("the Wi-Fi password at Cedar Lodge",
            AnswerChecks.missingItem("What is the Wi-Fi password at Cedar Lodge? Use my saved travel notes.", listOf(travel)))
        assertEquals("the phone number for Cedar Lodge",
            AnswerChecks.missingItem("What is the phone number for Cedar Lodge?", listOf(travel)))
    }

    @Test fun anItemPresentUnderAnotherWordIsNotReportedMissing() {
        val withCode = file("notes.txt", "Cedar Lodge network: CedarGuest, passcode pine-2026.")
        assertNull(AnswerChecks.missingItem("What is the Wi-Fi password at Cedar Lodge?", listOf(withCode)))
        val withPrice = file("tour.txt", "Harbour tour: €40 per adult, departs 10:00.")
        assertNull(AnswerChecks.missingItem("What is the cost of the harbour tour?", listOf(withPrice)))
        assertNull(AnswerChecks.missingItem("What is the last departure time for the shuttle?", listOf(travel)))
        assertNull(AnswerChecks.missingItem("Should East and West be watered today?", listOf(travel)))
    }
}
