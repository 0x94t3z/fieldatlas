package xyz.fieldatlas.research

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.fieldatlas.attachments.AttachmentPage
import xyz.fieldatlas.attachments.ExtractedAttachment
import xyz.fieldatlas.emergency.EmergencyGuideMatch

/**
 * The file-medicine lines and the lodged-object rule build their patterns from parts, so they are run here, on
 * Android's regex engine (ICU), through every branch that compiles one, rather than listed as literals.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceAuditPatternsTest {
    @Test fun fileMedicineLinesRunOnIcu() {
        val sheet = listOf(ExtractedAttachment("s", "sheet.txt", listOf(AttachmentPage(1, """
            YOUR DOSE
            - START methotrexate 10 mg (four x 2.5 mg tablets) ONCE A WEEK, on FRIDAYS.
            Take it with water. If you forget, take it within 2 days.
            Tuesday, Thursday: 3 mg (one 3 mg blue tablet)
            Keep to no more than 14 units a week of alcohol.
        """.trimIndent()))))
        val note = FileMedicineLines.note("can i take my methotrexate daily if i forget a dose on thursday? what did the doctor say to do",
            "Take methotrexate daily [S1].", sheet)!!
        assertTrue(note, note.startsWith("**Check how often:**"))
        assertTrue(note, "ONCE A WEEK" in note && "methotrexate once a week, not daily" in note)
    }

    /** The later fixes, on ICU: one document per attachment, entries split at a new name, claims tied to their medicine. */
    @Test fun attachmentsEntriesAndClaimsRunOnIcu() {
        fun file(id: String, text: String) = ExtractedAttachment(id, "sheet.txt", listOf(AttachmentPage(1, text)))
        val twoSheets = FileMedicineLines.note("how much methotrexate should I take?", "",
            listOf(file("1", "Apixaban 2.5 mg\nTake twice daily"), file("2", "Methotrexate 10 mg\nTake once weekly")))!!
        assertTrue(twoSheets, "Take once weekly" in twoSheets && "Take twice daily" !in twoSheets)
        val adjacent = listOf(file("1", "Calmavin 5 mg daily\nZorbitrex 20 mg once weekly"))
        val check = FileMedicineLines.note("how much calmavin should I take and is zorbitrex daily?", "", adjacent)!!
        assertTrue(check, check.startsWith("**Check how often:** your file gives Zorbitrex once a week, not daily."))
        val matching = FileMedicineLines.note("do I take calmavin daily and zorbitrex once weekly? how much", "", adjacent)!!
        assertTrue(matching, "Check how often" !in matching)
        val exercise = FileMedicineLines.note("when taking zorbitrex, should i exercise daily? how much do i take", "", adjacent)!!
        assertTrue(exercise, "Check how often" !in exercise)
        val negated = FileMedicineLines.note("I shouldn't take zorbitrex daily, right? how much is it", "", adjacent)!!
        assertTrue(negated, "Check how often" !in negated)
    }

    /** The saved-fact check's patterns (quantities, years, events, comparisons) on ICU, through every branch that compiles one. */
    @Test fun savedFactCheckRunsOnIcu() {
        fun source(id: String, text: String) = PromptSource(id, Evidence("library:$id", id, "Burj Khalifa", "Saved collection", text, 1.0), text)
        val burj = source("S1", "With a total height of 829.8 m (2,722 ft) and a roof height of 828 m (2,717 ft), the Burj Khalifa has been the tallest structure in the world since 2009. " +
            "It opened on 4 January 2010 and has 163 floors above ground.")
        val q = "how tall is the burj khalifa and how many floors does it have"
        val wrong = SavedFactCheck.note(q, "The Burj Khalifa stands 428 m (1,404 ft) tall and has 163 floors. It opened in 2012.", listOf(burj))
        assertTrue(wrong ?: "null", wrong != null && "829.8 m" in wrong && "[S1]" in wrong)
        assertEquals(null, SavedFactCheck.note(q, "The Burj Khalifa is about 830 m tall with 163 floors, and opened in 2010.", listOf(burj)))
        assertEquals(null, SavedFactCheck.note("burj khalifa vs shanghai tower: which is taller", "The Shanghai Tower is 632 m.", listOf(burj)))
        assertEquals(listOf(1986), SavedFactCheck.years("On 26 April 1986 the 1,000 MW reactor exploded"))
        assertEquals(1.0, SavedFactCheck.quantities("over a mile deep")[0].value, 0.0)
    }

    @Test fun lodgedObjectsRunOnIcu() {
        assertEquals(true, EmergencyGuideMatch.embeddedObject("the knife is still in his side, do I pull it out"))
        assertEquals(true, EmergencyGuideMatch.embeddedObject("he got impaled by a metal rod in his calf"))
        assertEquals(false, EmergencyGuideMatch.embeddedObject("the pain went into my left arm"))
        assertEquals(false, EmergencyGuideMatch.embeddedObject("his bone is sticking out of his leg"))
    }
}
