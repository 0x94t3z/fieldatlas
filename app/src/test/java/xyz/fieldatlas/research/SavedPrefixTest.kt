package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class SavedPrefixTest {
    @Test fun theAnswerTurnTellsTheModelWhereTheFixedInstructionsEnd() = runBlocking {
        val article = Evidence("wp-tide", "wp-tide:0000", "Tide — Overview", "https://simple.wikipedia.org/w/index.php?oldid=1",
            "A tide is the regular rise and fall of the sea, caused mainly by the Moon.", 0.0)
        val model = FakeInferenceGateway(listOf("The Moon pulls the water."))
        ResearchOrchestrator(Retriever { _, _, _ -> listOf(article) }, model).research("What is a tide?").toList()
        val (prompt, fixed) = model.prompts.last()
        val opening = prompt.take(fixed)
        assertTrue(opening, fixed > 0 && opening.endsWith("absent from the full source."))
        assertEquals(PromptBuilder.build("What is a tide?", listOf(article), 2048).prompt.take(fixed), opening)
    }
}
