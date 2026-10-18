package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** However it is described, a swallowed button battery opens the poisoning guide and its poison-contact steps. */
class SwallowedBatteryMatchTest {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun swallowedBatteriesOpenThePoisoningGuide() {
        listOf(
            "my 3 year old swallowed one of those small round watch batteries maybe 10 min ago. she seems totally fine, is it ok to just wait and see?",
            "my son swallowed a button battery",
            "toddler just swallowed a coin battery what do i do",
            "I think my daughter ate a battery from the remote",
            "baby swallowed a watch battery",
        ).forEach { assertEquals(it, "poisoning", match(it)) }
    }

    @Test fun batteriesThatNobodySwallowedDoNot() {
        listOf(
            "how do I replace my watch battery",
            "which button battery does a car key fob use",
            "how long does a CR2032 coin cell last",
            "I ate lunch and my phone battery died",
        ).forEach { assertNotEquals(it, "poisoning", match(it)) }
    }
}
