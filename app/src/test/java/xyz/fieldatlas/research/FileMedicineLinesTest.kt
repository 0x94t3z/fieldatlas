package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.attachments.AttachmentPage
import xyz.fieldatlas.attachments.ExtractedAttachment

/** A file answer about a medicine quotes the file's own lines on it, and says so when the question has the frequency wrong. */
class FileMedicineLinesTest {
    private fun file(text: String, name: String = "sheet.txt") = listOf(ExtractedAttachment(name, name, listOf(AttachmentPage(1, text.trimIndent()))))
    private fun plain(note: String?) = note?.replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }

    private val weeklySheet = file("""
        YOUR DOSE
        METHOTREXATE 10 mg (four x 2.5 mg tablets) ONCE A WEEK, on FRIDAYS.
        Methotrexate is a weekly medicine. Taking it every day by mistake can be fatal.
        IF YOU MISS A DOSE
        If you remember within 2 days, take it then. If you remember later than that, miss that week's dose.
        Keep to no more than 14 units a week of alcohol, because methotrexate and alcohol can both affect the liver.
    """)

    @Test fun dailyForAWeeklyMedicineIsFlaggedAndTheWeeklyLineQuoted() {
        val note = plain(FileMedicineLines.note("can i take my methotrexate daily if i forget a dose?", "", weeklySheet))
        assertNotNull(note)
        assertTrue(note!!, note.startsWith("**Check how often:** your file gives METHOTREXATE once a week, not daily."))
        assertTrue(note, "“METHOTREXATE 10 mg (four x 2.5 mg tablets) ONCE A WEEK, on FRIDAYS.”" in note)
        assertTrue(note, "If you remember within 2 days" in note)
        // Alcohol units are not a dose.
        assertFalse(note, "14 units" in note)
    }

    @Test fun theDailyClaimInTheAnswerCountsToo() {
        val note = plain(FileMedicineLines.note("what dose of methotrexate do i take?", "Take methotrexate 10 mg daily [S1].", weeklySheet))
        assertTrue(note!!, note.startsWith("**Check how often:**"))
    }

    @Test fun matchingFrequenciesQuoteWithoutAWarning() {
        val note = plain(FileMedicineLines.note("which day do i take my methotrexate dose?", "On Fridays, once a week [S1].", weeklySheet))
        assertNotNull(note)
        assertFalse(note!!, "Check how often" in note)
    }

    @Test fun aDailyMedicineCalledWeeklyIsFlagged() {
        val sheet = file("- Apixaban 2.5 mg orally TWICE daily. Continue until 09/11/2026 inclusive (35 days in total), then STOP.")
        val note = plain(FileMedicineLines.note("is mum's apixaban dose once a week?", "", sheet))
        assertTrue(note!!, note.startsWith("**Check how often:** your file gives Apixaban daily, not once a week."))
        // The dose keeps its end date and its stop, as the line has them.
        assertTrue(note, "Continue until 09/11/2026 inclusive (35 days in total), then STOP." in note)
    }

    @Test fun aDosingTableIsReadForTheDayAsked() {
        val sheet = file("""
            Warfarin: your dosing plan
            Monday, Wednesday, Friday: 4 mg (one 3 mg blue tablet + one 1 mg brown tablet)
            Tuesday, Thursday, Saturday, Sunday: 3 mg (one 3 mg blue tablet)
            Take your warfarin at the same time each day, ideally around 6 pm.
        """)
        val note = plain(FileMedicineLines.note("what dose am i meant to take on a thursday", "", sheet))
        assertTrue(note!!, "“Tuesday, Thursday, Saturday, Sunday: 3 mg (one 3 mg blue tablet)”" in note)
        assertTrue(note, note.startsWith("_From sheet.txt, word for word:_"))
    }

    @Test fun aDoctorsPlanIsAMedicineQuestion() {
        val letter = file("Densitometric osteoporosis. Recommend oral alendronate 70 mg weekly, with calcium and vitamin D. Review in 3 years.", "dexa.txt")
        val note = plain(FileMedicineLines.note("what was my t-score and what did the doctor say to do about it", "Your T-score is -2.7 [S1].", letter))
        assertTrue(note!!, "alendronate 70 mg weekly" in note)
    }

    @Test fun ordinaryDocumentQuestionsGetNothing() {
        // Not about taking a medicine, even in a file with doses.
        assertNull(FileMedicineLines.note("summarise this sheet for my sister", "", weeklySheet))
        assertNull(FileMedicineLines.note("what does methotrexate actually do in the body", "", weeklySheet))
        // A file with no medicine in it.
        val lease = file("The tenant may end the tenancy by giving two months' notice in writing. Rent is due on the 1st.")
        assertNull(FileMedicineLines.note("how much notice do i need to give and when is rent due, can i take a break", "", lease))
        assertNull(FileMedicineLines.note("what dose do i take", "", emptyList()))
    }

    @Test fun theLeafletNoteGivesWayToTheFile() {
        assertEquals(true, FileMedicineLines.DOSE_NOTE.contains("your file's own lines"))
    }

    // ---- Two attachments with the same name: each quoted line belongs to its own document and medicine. ----

    private fun attachment(id: String, name: String, text: String) = ExtractedAttachment(id, name, listOf(AttachmentPage(1, text)))
    private val apixabanSheet = attachment("1", "sheet.txt", "Apixaban 2.5 mg\nTake twice daily")
    private val methotrexateSheet = attachment("2", "sheet.txt", "Methotrexate 10 mg\nTake once weekly")

    /** The lines quoted under each "From …" heading, by heading. */
    private fun quotedByFile(note: String): Map<String, List<String>> {
        val groups = linkedMapOf<String, MutableList<String>>()
        var current = ""
        note.lines().forEach { line ->
            Regex("^_From (.+), word for word:_$").find(line)?.let { current = it.groupValues[1]; groups[current] = mutableListOf() }
            Regex("^- “(.*)”$").find(line)?.let { groups.getValue(current) += it.groupValues[1] }
        }
        return groups
    }

    @Test fun sameNamedAttachmentsDoNotMixTheirInstructions() {
        val note = plain(FileMedicineLines.note("how much methotrexate should I take?", "", listOf(apixabanSheet, methotrexateSheet)))!!
        val quoted = quotedByFile(note).values.flatten()
        assertTrue(note, "Methotrexate 10 mg" in quoted && "Take once weekly" in quoted)
        assertFalse(note, "Take twice daily" in quoted)
        assertFalse(note, "Apixaban 2.5 mg" in quoted)
    }

    @Test fun reversedAttachmentOrderQuotesTheSameDocument() {
        val note = plain(FileMedicineLines.note("how much methotrexate should I take?", "", listOf(methotrexateSheet, apixabanSheet)))!!
        assertEquals(listOf("Methotrexate 10 mg", "Take once weekly"), quotedByFile(note).values.flatten())
    }

    @Test fun overlappingLineNumbersStayWithTheirOwnDocument() {
        // Both files have their medicine on line 0 and its schedule on line 1.
        val note = plain(FileMedicineLines.note("how much apixaban and methotrexate do I take?", "", listOf(apixabanSheet, methotrexateSheet)))!!
        val groups = quotedByFile(note)
        // Two documents with one name are told apart, and each keeps its own lines.
        assertEquals(note, 2, groups.size)
        val (first, second) = groups.entries.toList()
        assertTrue(note, first.key != second.key && first.key.startsWith("sheet.txt") && second.key.startsWith("sheet.txt"))
        assertEquals(listOf("Apixaban 2.5 mg", "Take twice daily"), first.value)
        assertEquals(listOf("Methotrexate 10 mg", "Take once weekly"), second.value)
    }

    // ---- Frequency claims belong to the medicine they are said about. ----

    private val twoMedicines = file("Methotrexate 10 mg once weekly\n\nFolic acid 5 mg daily")
    private val madeUp = file("Zorbitrex 20 mg once weekly\n\nCalmavin 5 mg daily")
    private fun warning(question: String, answer: String = "", sheet: List<ExtractedAttachment> = twoMedicines) =
        plain(FileMedicineLines.note(question, answer, sheet))?.lines()?.firstOrNull { it.startsWith("**Check how often:**") }

    @Test fun aQuestionThatMatchesTheFileGetsNoWarning() {
        assertNull(warning("do I take methotrexate once weekly and folic acid daily?"))
        assertNull(warning("do I take zorbitrex once weekly and calmavin daily?", sheet = madeUp))
    }

    @Test fun onlyTheMedicineWithTheWrongFrequencyIsCorrected() {
        val one = warning("do I take methotrexate daily and folic acid daily?")!!
        assertTrue(one, "Methotrexate once a week, not daily" in one)
        assertFalse(one, "Folic" in one)
        val both = warning("do I take zorbitrex daily and calmavin once weekly?", sheet = madeUp)!!
        assertTrue(both, "Zorbitrex once a week, not daily" in both && "Calmavin daily, not once a week" in both)
    }

    @Test fun aNegatedFrequencyIsNotAClaim() {
        assertNull(warning("I shouldn't take methotrexate daily, right? how much is it"))
        assertNull(warning("calmavin is not weekly is it? how much do i take", sheet = madeUp))
    }

    @Test fun aFrequencyThatCannotBeTiedToOneMedicineIsLeftAlone() {
        // No medicine in the clause that says how often, or two medicines in it.
        assertNull(warning("I have methotrexate and folic acid. do I take them daily?"))
        assertNull(warning("is the methotrexate or the folic acid the daily one? how much do i take", sheet = twoMedicines))
    }

    @Test fun oneMedicineInTheQuestionTakesItsFrequencyClaims() {
        val single = file("METHOTREXATE 10 mg ONCE A WEEK, on FRIDAYS.\nIf you miss a dose, take it within 2 days.")
        assertTrue(warning("my methotrexate sheet says if i forget a dose i can take it the next day and then carry on daily", sheet = single)!!
            .contains("once a week, not daily"))
    }

    // ---- Adjacent medicines and frequencies of other things. ----

    @Test fun adjacentMedicinesWithoutABlankLineKeepTheirOwnSchedules() {
        for (sheet in listOf("Calmavin 5 mg daily\nZorbitrex 20 mg once weekly", "Zorbitrex 20 mg once weekly\nCalmavin 5 mg daily",
                "Calmavin 5 mg daily\n\nZorbitrex 20 mg once weekly")) {
            val note = plain(FileMedicineLines.note("how much calmavin should I take and is zorbitrex daily?", "", file(sheet)))!!
            val check = note.lines().firstOrNull { it.startsWith("**Check how often:**") }
            assertEquals(sheet, "**Check how often:** your file gives Zorbitrex once a week, not daily.", check)
            // Each line is quoted once, as itself.
            assertEquals(sheet, 1, note.lines().count { it == "- “Calmavin 5 mg daily”" })
            assertEquals(sheet, 1, note.lines().count { it == "- “Zorbitrex 20 mg once weekly”" })
        }
    }

    @Test fun aMedicineNextToAnotherDoesNotTakeItsSchedule() {
        // Asked about the daily one only: the weekly line under it is another medicine, not its schedule.
        assertNull(warning("is calmavin daily? how much do i take", sheet = file("Calmavin 5 mg daily\nZorbitrex 20 mg once weekly")))
        assertNull(warning("how much calmavin do i take daily?", sheet = file("Calmavin 5 mg daily\nZorbitrex 20 mg once weekly")))
    }

    @Test fun howOftenSomethingElseIsDoneIsNotAClaimAboutTheMedicine() {
        val weeklyOnly = file("Zorbitrex 20 mg once weekly")
        assertNull(warning("when taking zorbitrex, should i exercise daily?", sheet = weeklyOnly))
        assertNull(warning("i walk daily and check my sugar every morning, how much zorbitrex do i take?", sheet = weeklyOnly))
        assertNull(warning("when taking methotrexate, should i exercise daily?", sheet = file("Methotrexate 10 mg once weekly")))
        // Said of the medicine, it still counts.
        assertTrue(warning("can i take zorbitrex daily?", sheet = weeklyOnly)!!.contains("Zorbitrex once a week, not daily"))
        assertTrue(warning("is zorbitrex daily?", sheet = weeklyOnly)!!.contains("Zorbitrex once a week, not daily"))
        assertTrue(warning("how much zorbitrex do i take, can i take it daily?", sheet = weeklyOnly)!!.contains("not daily"))
        assertTrue(warning("do i take 20 mg of zorbitrex every morning?", sheet = weeklyOnly)!!.contains("not daily"))
    }

    @Test fun eachOfSeveralAdjacentMedicinesKeepsItsOwnScheduleAndInstructions() {
        val list = file("- Calmavin 5 mg daily\n- Zorbitrex 20 mg once weekly\n- Folbax 1 mg daily")
        val both = warning("is zorbitrex daily and folbax weekly? how much do i take", sheet = list)!!
        assertTrue(both, "Zorbitrex once a week, not daily" in both && "Folbax daily, not once a week" in both)
        assertFalse(both, "Calmavin" in both)
        // Instructions under each name belong to that name, up to the next one.
        val blocks = file("Calmavin 5 mg\nTake twice daily\nZorbitrex 20 mg\nTake once weekly")
        assertTrue(warning("is calmavin weekly? how much do i take", sheet = blocks)!!.contains("Calmavin daily, not once a week"))
        assertTrue(warning("is zorbitrex daily? how much do i take", sheet = blocks)!!.contains("Zorbitrex once a week, not daily"))
        assertNull(warning("is zorbitrex weekly? how much do i take", sheet = blocks))
    }

    @Test fun otherThingsDoneDailyAreNotTheMedicinesSchedule() {
        val weeklyOnly = file("Zorbitrex 20 mg once weekly")
        assertNull(warning("should i drink water daily with zorbitrex? how much do i take", sheet = weeklyOnly))
        assertNull(warning("i take my vitamins daily, how much zorbitrex do i take?", sheet = weeklyOnly))
        assertNull(warning("can i check my blood pressure every morning while on zorbitrex? how much do i take", sheet = weeklyOnly))
    }

    /** Through the whole answer path: a file answer that lets "carry on daily" stand still shows the weekly line. */
    @Test fun aFileAnswerCarriesTheNoteUnderTheModelsWords() = kotlinx.coroutines.runBlocking {
        val gateway = object : xyz.fieldatlas.inference.InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<xyz.fieldatlas.inference.InferenceState>(xyz.fieldatlas.inference.InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override suspend fun unload() = Unit
            override fun reviewEvidence(prompt: String, savedPrefixChars: Int) = kotlinx.coroutines.flow.flow { emit("SUPPORTED") }
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int) =
                kotlinx.coroutines.flow.flow { emit("Yes, take it the next day and then carry on as the sheet says [S1].") }
        }
        val answers = mutableListOf<String>()
        ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, gateway, prefillSpeed = InMemoryPrefillSpeed(100.0))
            .research("my methotrexate sheet says if i forget a dose i can take it the next day and then carry on daily - is that right?",
                attachments = weeklySheet).collect { if (it is ResearchEvent.Token && it.replace) answers += it.text }
        val answer = plain(answers.last())!!
        assertTrue(answer, "Yes, take it the next day" in answer)
        assertTrue(answer, "**Check how often:** your file gives METHOTREXATE once a week, not daily." in answer)
        assertTrue(answer, "ONCE A WEEK, on FRIDAYS." in answer)
    }
}
