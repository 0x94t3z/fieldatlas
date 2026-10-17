package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class SavedPrefixTest {
    @Test fun theAnswerTurnTellsTheModelWhereTheFixedInstructionsEnd() = runBlocking {
        // With no saved evidence this remains a real generation turn. The ordinary
        // saved-knowledge route now finishes from originals without calling the model.
        val question = "What is a tide?"
        val model = FakeInferenceGateway(listOf("The Moon pulls the water."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, model).research(question).toList()
        val (prompt, fixed) = model.prompts.last()
        val expected = PromptBuilder.buildModelOnly(question)
        assertTrue(fixed > 0)
        assertEquals(expected.savedPrefixChars, fixed)
        assertEquals(expected.prompt.take(fixed), prompt.take(fixed))
        assertTrue(prompt.substring(fixed).contains(question))
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun rejectedKnowledgeDraftRetainsItsPackedPrefixForOneRepair() = runBlocking {
        val article = Evidence("wp-tide", "wp-tide:0000", "Tide — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1",
            "A tide is the regular rise and fall of the sea, caused mainly by the Moon.", 0.0)
        val model = FakeInferenceGateway(listOf("Unsupported extra explanation."))
        var observed: PackedPrompt? = null
        val events = ResearchOrchestrator(Retriever { _, _, _ -> listOf(article) }, model,
            onEvidencePacked = { observed = it }).research("What is a tide?").toList()
        val selected = observed!!
        val prompt = selected.prompt
        val fixed = selected.savedPrefixChars
        val opening = prompt.take(fixed)
        // The opening runs from the instructions through the evidence; the repair turn restores it.
        assertTrue(opening, fixed > 0 && opening.contains("own words") && opening.contains(article.text))
        assertEquals(selected.savedPrefixChars, model.prompts.last().second)
        assertTrue(model.prompts.all { it.first.startsWith(opening) && it.second == fixed })
        assertEquals(2, model.generateCalls)
        assertFalse(events.filterIsInstance<ResearchEvent.Token>().any { it.text.contains("Unsupported extra") })
    }
}
