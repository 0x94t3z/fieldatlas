package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class PrefillSpeedTest {
    @Test fun slowPhonesGetTheTopPassagesAndFastPhonesGetMore() {
        assertEquals(PrefillSpeed.UNKNOWN_EVIDENCE, PrefillSpeed.evidenceTokens(null))
        // Redmi 13C measured about 17 estimated tokens a second: only the minimum fits 40 s.
        assertEquals(PrefillSpeed.MIN_EVIDENCE, PrefillSpeed.evidenceTokens(17.0))
        assertEquals(1150, PrefillSpeed.evidenceTokens(40.0))
        assertEquals(AttachmentEvidence.EVIDENCE_TOKENS, PrefillSpeed.evidenceTokens(150.0))
    }
    @Test fun measurementsAreBlendedAndTinyPromptsIgnored() {
        assertEquals(20.0, PrefillSpeed.update(null, 2000, 100_000)!!, 1e-9)
        assertEquals(20.0 * 0.6 + 40.0 * 0.4, PrefillSpeed.update(20.0, 2000, 50_000)!!, 1e-9)
        assertEquals(20.0, PrefillSpeed.update(20.0, 50, 10)!!, 1e-9)
    }
    @Test fun aLearnedSlowSpeedShrinksTheNextPrompt() = kotlinx.coroutines.runBlocking {
        var captured = ""
        val gateway = object : xyz.fieldatlas.inference.InferenceGateway {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<xyz.fieldatlas.inference.InferenceState>(xyz.fieldatlas.inference.InferenceState.Ready)
            override suspend fun load(modelPath: String, systemPrompt: String) = Unit
            override suspend fun unload() = Unit
            override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int) = kotlinx.coroutines.flow.flow {
                captured = prompt; emit("Answer [S1]")
            }
        }
        val file = xyz.fieldatlas.attachments.ExtractedAttachment("h", "handbook.txt",
            listOf(xyz.fieldatlas.attachments.AttachmentPage(1, (1..60).joinToString("\n\n") { "Section $it: water, trails and permits are described here in some detail for visitors." })))
        fun run(speed: PrefillSpeed): Int {
            kotlinx.coroutines.runBlocking {
                ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, gateway, prefillSpeed = speed)
                    .research("Where is water on the trail?", attachments = listOf(file)).collect {}
            }
            return TokenEstimate.of(captured)
        }
        val slow = run(InMemoryPrefillSpeed(15.0))
        val fast = run(InMemoryPrefillSpeed(200.0))
        assertTrue("slow $slow vs fast $fast", slow < fast)
    }
    @Test fun rulesThatCannotApplyAreLeftOut() {
        val note = xyz.fieldatlas.attachments.ExtractedAttachment("n", "notes.txt", listOf(xyz.fieldatlas.attachments.AttachmentPage(1, "The spring is at Cedar Saddle.")))
        val plain = AttachmentEvidence.pack("Where is the spring?", AttachmentEvidence.select("Where is the spring?", listOf(note), 8), emptyList(), 4000).prompt
        assertFalse(plain.contains("screenshot"))
        assertFalse(plain.contains("item by item"))
        assertFalse(plain.contains("21:30 (9:30 PM)"))
        assertFalse(plain.contains("OCR may contain errors"))
        val timed = note.copy(pages = listOf(xyz.fieldatlas.attachments.AttachmentPage(1, "Gate closes at 16:00.")))
        val decision = AttachmentEvidence.pack("Should we leave before the gate closes?",
            AttachmentEvidence.select("Should we leave before the gate closes?", listOf(timed), 8), emptyList(), 4000).prompt
        assertTrue(decision.contains("item by item"))
        assertTrue(decision.contains("21:30 (9:30 PM)"))
    }
}
