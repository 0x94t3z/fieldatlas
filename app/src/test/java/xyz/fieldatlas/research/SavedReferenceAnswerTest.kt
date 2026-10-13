package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.text.MarkdownLiteral

class SavedReferenceAnswerTest {
    private val subject = Evidence("enw-1-0000", "enw-1-0000:0000", "Heat pump — Overview", "Local revision 1",
        "A heat pump moves thermal energy.\n\nHistory not selected for the prompt.", 0.0)

    @Test fun existingChunkFragmentsGetVisibleBoundaryEllipsesWithoutChangingTheirWords() {
        val item = subject.copy(chunkId = "enw-1-0000:0001", text = "remaining words from an earlier sentence. A later incomplete clause")
        val packed = PackedPrompt("", listOf(PromptSource("S1", item)))
        val answer = SavedReferenceAnswer.answer("According to saved sources, how does a heat pump work?", packed, listOf(subject))!!
        assertTrue(MarkdownLiteral.decode(answer).contains("> … " + item.text + " … [S1]"))
    }

    @Test fun copiesOnlyActualSelectedUnitsWithQualificationsAndStableNumbers() {
        val excerpt = "A heat pump moves thermal energy.\n\nHowever, performance depends on conditions."
        val source = PromptSource("S2", subject.copy(text = excerpt + "\n\nOther history."), excerpt)
        val packed = PackedPrompt("", listOf(source), mixedAnswer = false)
        val answer = SavedReferenceAnswer.answer("According to the saved sources, how does a heat pump work?", packed, listOf(subject))!!
        assertTrue(MarkdownLiteral.decode(answer).contains("> $excerpt [S2]"))
        assertFalse(answer.contains("Other history"))
        assertTrue(answer.contains("may not fully answer"))
        assertFalse(answer.contains(AnswerText.MODEL_LABEL))
    }

    @Test fun ordinaryQuestionsFilesAndUnconfirmedSubjectsKeepTheirExistingPaths() {
        val packed = PackedPrompt("", listOf(PromptSource("S1", subject)))
        assertNull(SavedReferenceAnswer.answer("How does a heat pump work?", packed, listOf(subject)))
        assertNull(SavedReferenceAnswer.answer("According to saved sources, how does a heat pump work?", packed, emptyList()))
        assertNull(SavedReferenceAnswer.answer("According to saved sources, how does a heat pump work?", packed,
            listOf(subject.copy(documentId = "attachment:1"))))
    }

    @Test fun strictNamedReferenceExplanationNeverCallsTheModel() = runBlocking {
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(subject)
            override suspend fun titled(title: String, sections: List<String>, limit: Int) =
                if (title.equals("heat pump", true)) listOf(subject) else emptyList()
        }
        val inference = FakeInferenceGateway(listOf("Unsupported model addition."))
        val events = ResearchOrchestrator(retriever, inference)
            .research("According to the saved sources, how does a heat pump work? Quote the source.").toList()
        val answer = events.filterIsInstance<ResearchEvent.Token>().last().text
        assertTrue(answer.contains("Selected passages"))
        assertFalse(answer.contains("Unsupported model addition"))
        assertEquals(0, (events.last() as ResearchEvent.Complete).metrics.generatedTokenCount)
    }

    @Test fun subjectLeadQuotesTheSelectedMechanismNotTheOmittedChunkOpening() {
        val mechanism = subject.copy(documentId = "enw-1-0001", chunkId = "enw-1-0001:0000", title = "Heat pump — Principles",
            text = "An omitted historical introduction.\n\nThe heat pump transfers heat through a refrigerant. This requires power.")
        val excerpt = "The heat pump transfers heat through a refrigerant. This requires power."
        val lead = SubjectLookup.lead("How does a heat pump work?", listOf(subject),
            listOf(PromptSource("S1", subject), PromptSource("S2", mechanism, excerpt)), listOf(mechanism))!!
        assertEquals("S2", lead.citationId)
        assertEquals(excerpt, lead.text)
        assertFalse(lead.text.contains("historical"))
    }
}
