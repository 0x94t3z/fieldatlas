package xyz.fieldatlas.research

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway
import xyz.fieldatlas.inference.InferenceGateway
import xyz.fieldatlas.inference.InferenceState

/**
 * A passage reader is handed the passages closest to the question in meaning first, then the ones the rules chose. On
 * 10 Nov 2026 "how many bones are in the adult human body" reached the reader without the passage that says 206.
 */
class ClosestFirstTest {
    private val boats = Evidence("boat", "boat:0", "Boat — Buoyancy", "Local",
        "A boat floats because it displaces a weight of water equal to its own weight.", -1.0)
    private val archimedes = Evidence("archimedes", "archimedes:0", "Archimedes' principle — Overview", "Local",
        "Any object in a fluid is pushed up by a force equal to the weight of the fluid it displaces.", 0.84)
    private val bone = Evidence("bone", "bone:0", "Bone — Overview", "Local", "A bone is a rigid organ that forms part of the skeleton.", -4.0)
    private val spine = Evidence("spine", "spine:0", "Vertebral column — Overview", "Local", "The vertebral column has 26 bones in an adult.", -3.0)
    private val skeleton = Evidence("skeleton", "skeleton:0", "Human skeleton — Overview", "Local",
        "The human skeleton has around 206 bones in adulthood.", 0.84)

    private fun reader(prompts: MutableList<String>, reply: String) = object : InferenceGateway {
        override val state = MutableStateFlow<InferenceState>(InferenceState.Ready)
        override val readsPassages = true
        override suspend fun load(modelPath: String, systemPrompt: String) = Unit
        override suspend fun unload() = Unit
        override fun generate(prompt: String, maxTokens: Int, systemPrompt: String?, seed: Int, savedPrefixChars: Int): Flow<String> = flow {
            prompts += prompt
            emit(reply)
        }
    }

    /** Search returns the rules' passages; [closest] are the nearest in meaning, asked for by count. */
    private fun library(found: List<Evidence>, closest: List<Evidence>, asked: MutableList<Int> = mutableListOf()) = object : Retriever {
        override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = found
        override suspend fun meaningLeads(question: String, limit: Int): List<Evidence> { asked += limit; return closest.take(limit) }
    }

    @Test fun theClosestPassagesLeadAndTheRulesChoiceFollows() = runBlocking {
        val prompts = mutableListOf<String>(); val asked = mutableListOf<Int>()
        val events = ResearchOrchestrator(library(listOf(boats), listOf(archimedes), asked), reader(prompts, "A boat floats because it displaces its own weight of water [S2]."))
            .research("Why do boats float?").toList()
        assertEquals(prompts.joinToString("\n=====\n"), 1, prompts.size)
        val prompt = prompts.single()
        assertTrue(prompt, prompt.contains("PASSAGES:\n[S1] Archimedes' principle — Overview\n" + archimedes.text +
            "\n\n[S2] Boat — Buoyancy\n" + boats.text + "\n\nQUESTION: Why do boats float?"))
        // The sources on screen are the passages the reader was handed, in the same order.
        val sources = events.filterIsInstance<ResearchEvent.Sources>().last().evidence
        assertEquals(listOf("Archimedes' principle — Overview", "Boat — Buoyancy"), sources.map { it.title })
        assertEquals("concept match 0.84", sources.first().matchedBy)
        assertEquals("A boat floats because it displaces its own weight of water [S2].", events.filterIsInstance<ResearchEvent.Token>().last().text)
        assertTrue(PromptBuilder.CLOSEST_FIRST in asked)
    }

    @Test fun aPassageFoundBothWaysIsReadOnce() = runBlocking {
        val prompts = mutableListOf<String>()
        ResearchOrchestrator(library(listOf(boats), listOf(archimedes, boats.copy(score = 0.8))), reader(prompts, "The saved sources don't say."))
            .research("Why do boats float?").toList()
        assertEquals(prompts.single(), 1, Regex("Boat — Buoyancy").findAll(prompts.single()).count())
    }

    @Test fun whatTheRulesDroppedStillReachesTheReaderWithoutAPlanningTurn() = runBlocking {
        // The rules keep neither passage for this question; before, the model was first asked for other words to search.
        val prompts = mutableListOf<String>()
        ResearchOrchestrator(library(listOf(bone, spine), listOf(skeleton)), reader(prompts, "An adult has around 206 bones [S1]."))
            .research("how many bones are in the adult human body").toList()
        assertEquals(prompts.joinToString("\n=====\n"), 1, prompts.size)
        assertTrue(prompts.single(), prompts.single().contains("PASSAGES:\n[S1] Human skeleton — Overview\n" + skeleton.text))
    }

    @Test fun switchedOffTheReaderIsHandedOnlyTheRulesChoice() = runBlocking {
        val prompts = mutableListOf<String>()
        ResearchOrchestrator(library(listOf(boats), listOf(archimedes)), reader(prompts, "The saved sources don't say."), closestFirst = 0)
            .research("Why do boats float?").toList()
        assertFalse(prompts.single(), prompts.single().contains("Archimedes"))
    }

    @Test fun aModelThatIsNotAPassageReaderKeepsItsPassages() = runBlocking {
        val model = FakeInferenceGateway(listOf("A boat floats because it displaces its own weight of water [S1]."))
        ResearchOrchestrator(library(listOf(boats), listOf(archimedes)), model).research("Why do boats float?").toList()
        assertTrue(model.prompts.isNotEmpty())
        assertTrue(model.prompts.none { (prompt, _) -> prompt.contains("Archimedes") })
    }

    @Test fun firstAidKeepsItsOwnSources() = runBlocking {
        val prompts = mutableListOf<String>()
        val events = ResearchOrchestrator(library(listOf(bone), listOf(skeleton)), reader(prompts, "The saved sources don't say."))
            .research("my friend fell off his bike and the bone is sticking out of his leg, what do i do").toList()
        assertTrue(prompts.none { it.contains("Human skeleton") })
        assertTrue(events.filterIsInstance<ResearchEvent.Sources>().none { sources -> sources.evidence.any { it.title.startsWith("Human skeleton") } })
    }

    @Test fun nothingCloseLeavesThePromptAsItWas() {
        val packed = PromptBuilder.build("how many bones are in the adult human body", listOf(bone, spine), 2_048)
        assertSame(packed, PromptBuilder.closestFirst("how many bones are in the adult human body", packed, emptyList(), 2_048))
    }

    @Test fun aLongPassageEndsAtASentenceAndTheRestMustFit() {
        val sentence = "The skeleton protects the organs and anchors the muscles of the body. "
        val long = skeleton.copy(text = "The human skeleton has around 206 bones in adulthood. " + sentence.repeat(30))
        val second = skeleton.copy(documentId = "b", chunkId = "b:0", title = "Skeleton — Humans", text = sentence.repeat(17).trim())
        val third = skeleton.copy(documentId = "c", chunkId = "c:0", title = "Skull — Bones", text = sentence.repeat(17).trim())
        val packed = PromptBuilder.build("how many bones are in the adult human body", listOf(bone, spine), 2_048)
        val led = PromptBuilder.closestFirst("how many bones are in the adult human body", packed, listOf(long, second, third), 2_048)
        val first = led.sources.first()
        assertTrue(first.excerpt.length <= 1_200 && first.excerpt.endsWith("."))
        assertTrue(first.excerpt.startsWith("The human skeleton has around 206 bones"))
        // The whole passage stays behind the source for its viewer.
        assertEquals(long.text, first.evidence.text)
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), led.sources.map { it.citationId })
        // Three passages of about 1,200 characters leave room for the two short ones the rules chose, and no more than that.
        assertTrue(led.sources.sumOf { it.excerpt.length } <= 4_200)
        val crowded = PromptBuilder.closestFirst("how many bones are in the adult human body",
            PromptBuilder.build("how many bones are in the adult human body", listOf(bone.copy(text = sentence.repeat(12).trim()), spine), 2_048),
            listOf(long, second, third), 2_048)
        assertEquals(listOf("Human skeleton — Overview", "Skeleton — Humans", "Skull — Bones", "Vertebral column — Overview"), crowded.sources.map { it.evidence.title })
    }
}
