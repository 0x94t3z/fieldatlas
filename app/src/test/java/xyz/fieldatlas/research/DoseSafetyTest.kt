package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class DoseSafetyTest {
    @Test fun aDoseTheModelWritesNeverReachesTheAnswer() = runBlocking {
        val article = Evidence("wp-para", "wp-para:0000", "Paracetamol — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1",
            "Paracetamol is a common medicine for pain and fever. Taking far more than the label allows can badly damage the liver.", 0.0)
        val model = FakeInferenceGateway(listOf("Paracetamol eases pain and fever. The usual amount is 4000 mg every 4 hours. Never mix it with alcohol."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(article) }, model).research("How does paracetamol work?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer, "4000 mg" in answer)
        assertTrue(answer, "Never mix it with alcohol." in answer)
        assertTrue(answer, "follow the package leaflet" in answer)
    }

    @Test fun aSourcesHeadingDoesNotProtectTheModelsOwnLines() {
        val answer = "From saved sources:\n\"Paracetamol, also called acetaminophen, is a medicine used to treat pain and fever.\" [S1]\n\n" +
            "Model explanation:\nAdults can usually take 1000 mg every 4 to 6 hours, up to 4000 mg a day."
        val out = AnswerChecks.removeDoses(answer, "How much paracetamol can an adult take for a headache?")
        assertFalse(out, "1000 mg" in out || "4000 mg" in out)
        assertTrue(out, "used to treat pain and fever" in out)
    }

    @Test fun aDoseIsNeverOnScreenWhileTheAnswerStreams() = runBlocking {
        val article = Evidence("wp-para", "wp-para:0000", "Paracetamol — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1",
            "Paracetamol is a common medicine for pain and fever. Taking far more than the label allows can badly damage the liver.", 0.0)
        val model = FakeInferenceGateway("Paracetamol eases pain and fever. Adults can usually take 1000 mg every 4 to 6 hours. Never mix it with alcohol."
            .split(Regex("(?<= )")))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(article) }, model).research("How does paracetamol work?").toList()
        val shown = events.filterIsInstance<ResearchEvent.Token>().map { it.text } + events.filterIsInstance<ResearchEvent.Lead>().map { it.text }
        assertTrue(shown.none { "1000 mg" in it })
    }

    @Test fun aDoseFromTheUsersOwnLeafletPointsToTheLeaflet() = runBlocking {
        val leaflet = Evidence("attachment:leaflet", "leaflet:0", "Children's paracetamol leaflet", "Attached file",
            "Children aged 6 to 8 years: 5 ml up to 4 times in 24 hours, at least 4 hours apart.", 0.0)
        val model = FakeInferenceGateway(listOf("A 7 year old can have 5 ml up to 4 times in 24 hours, at least 4 hours apart [S1]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(leaflet) }, model)
            .research("According to my saved leaflet, how much paracetamol suspension can a 7 year old have?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer, answer.trim() == CitedExplanation.UNAVAILABLE)
        assertTrue(answer, "leaflet" in answer || "pharmacist" in answer)
    }

    @Test fun aCitedAnswerKeepsItsFactsWhenOnlyTheDoseGoes() = runBlocking {
        val article = Evidence("mp-ibu", "mp-ibu:0000", "Ibuprofen — MedlinePlus", "https://medlineplus.gov/druginfo/meds/a682159.html",
            "Ibuprofen is used to relieve pain from various conditions such as headache, dental pain and menstrual cramps. " +
                "It is also used to reduce fever.", 0.0)
        val model = FakeInferenceGateway(listOf("Ibuprofen relieves headache pain and lowers fever [S1]. Adults usually take 200 to 400 mg [S1]."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(article) }, model).research("Can I take ibuprofen for a headache?").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertFalse(answer, "400 mg" in answer)
        assertTrue(answer, "relieves headache pain" in answer)
        assertTrue(answer, "follow the package leaflet" in answer)
    }
}
