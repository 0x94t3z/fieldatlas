package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class MultipartRelevanceTest {
    private val question = "How does the Luma projector produce a beam, and what limits wet storage?"
    private fun row(id: String, title: String, text: String, source: String = "fixture://$id") =
        Evidence(id, "$id:0", title, source, text, 0.0)
    private val beam = row("luma-beam", "Luma projector — Beam",
        "The Luma projector produces a beam through its emitter. The emitter has a fixed position.")
    private val wet = row("luma-storage", "Luma projector — Storage",
        "Wet storage limits remain unknown. Only dry storage was evaluated; the test does not establish wet storage safety.")
    private val unrelated = row("aquarium", "Aquarium — Storage",
        "Wet storage limits are determined by tank capacity. The aquarium stores water.")
    private fun keep(q: String, rows: List<Evidence>) = EvidenceRelevance.keep(rows, FtsQuery.from(q)!!.terms, question = q)

    @Test fun eachPassageMaySupportOneClauseWithoutClaimingWholeQuestionCoverage() {
        for (q in listOf(question, "According to the saved sources, $question")) {
            val found = keep(q, listOf(beam, unrelated, wet))
            assertEquals(q, listOf(beam, wet), found)
            assertEquals(listOf(beam, wet), MultipartEvidence.anchors(EvidenceRelevance.clauseGroups(found, q)))
            assertEquals(listOf(beam.text, wet.text), found.map { it.text })
            assertEquals(PromptBuilder.allowsModelExplanation(q), PromptBuilder.build(q, found, 8192).mixedAnswer)
        }
    }

    @Test fun dependentClauseCannotSwitchToANamePrefixOrAnUnrelatedTopic() {
        val wrong = listOf(unrelated,
            wet.copy(documentId = "lumatic", chunkId = "lumatic:0", title = "Lumatic projector — Storage"),
            wet.copy(documentId = "other", chunkId = "other:0", title = "Projector — Storage"),
            wet.copy(documentId = "modifier-prefix", chunkId = "modifier-prefix:0", title = "Projector — Storage",
                text = "Lumatic projector wet storage limits remain unknown."))
        for (q in listOf(question, "According to the saved sources, $question")) {
            assertEquals(listOf(beam), keep(q, listOf(beam) + wrong))
        }
    }

    @Test fun pronounClauseKeepsItsOriginalEntityAndProvenance() {
        val q = "How does the Luma projector produce a beam, and how does humidity affect its surface?"
        val humidity = wet.copy(text = "Humidity affects the surface because moisture changes the outer layer.")
        val wrong = humidity.copy(documentId = beam.documentId, chunkId = "other:1",
            title = "Aquarium — Surface", source = "fixture://different-provenance")
        assertEquals(listOf(beam, humidity), keep(q, listOf(beam, wrong, humidity)))
        val continuation = humidity.copy(documentId = beam.documentId, source = beam.source,
            title = "Observed surface", chunkId = "same:1")
        assertEquals(listOf(beam, continuation), keep(q, listOf(beam, continuation)))
    }

    @Test fun independentExplicitTopicCanHaveItsOwnNamedReference() {
        val q = "How does the Luma projector produce a beam, and how do mirrors reflect light?"
        val mirror = row("mirror", "Mirror — Reflection", "Mirrors reflect light because the surface redirects incoming light.")
        assertEquals(listOf(beam, mirror), keep(q, listOf(beam, mirror, unrelated)))
    }

    @Test fun boundedMergeReservesBothPartsBeforeEarlierPackDuplicates() {
        val duplicates = (0 until 20).map { listOf(beam.copy(documentId = "luma$it", chunkId = "luma$it:0")) }
        val found = MultiKnowledgeRetriever.mergeRelevantEvidence(duplicates + listOf(listOf(wet, unrelated)), question, question, 2)
        assertEquals(2, found.size)
        assertEquals(wet, found.last())
        assertFalse(found.contains(unrelated))
    }

    @Test fun aPassageEligibleForBothClausesKeepsPriorityUnderASingleSlot() {
        val whole = beam.copy(chunkId = "luma-beam:1", text = beam.text + " " + wet.text)
        val found = MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(listOf(beam), listOf(wet), listOf(whole)),
            question, question, 1)
        assertEquals(listOf(whole), found)
    }

    @Test fun confirmedMultiwordTopicCanAnchorAnotherFilesBodyButNeverMetadataOrPrefix() {
        val named = wet.copy(title = "Test note", text = "Luma projector wet storage limits remain unknown.")
        val metadata = named.copy(documentId = "metadata", chunkId = "metadata:0", text =
            "PMID 1\nAuthors: Luma projector\nMeSH: Luma projector\nAbstract: Wet storage limits remain unknown.")
        val prefix = named.copy(documentId = "prefix", chunkId = "prefix:0", text =
            "Luma projectorson wet storage limits remain unknown.")
        for (q in listOf(question, "According to the saved sources, $question")) {
            val found = MultiKnowledgeRetriever.mergeRelevantEvidence(listOf(listOf(beam), listOf(named, metadata, prefix)), q, q, 2)
            assertEquals(q, listOf(beam, named), found)
        }
    }

    @Test fun recoveryIsBoundedKeepsFullScopeAndNeverChangesPromptQuestion() = runBlocking {
        val queries = mutableListOf<String>()
        val scopes = mutableListOf<String>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = emptyList<Evidence>()
            override suspend fun searchQuestion(query: String, question: String, limit: Int, placesOnly: Boolean,
                onProgress: suspend (SearchProgress) -> Unit): List<Evidence> {
                queries += query; scopes += question
                assertTrue(limit <= 24 || query == question)
                return if (query.contains("what limits")) listOf(wet, unrelated) else listOf(beam)
            }
        }
        var packed: PackedPrompt? = null
        val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("Fixture answer [S1].")),
            onEvidencePacked = { packed = it }).research(question).toList()
        assertTrue(events.last() is ResearchEvent.Complete)
        assertTrue(queries.size <= 3)
        assertTrue(scopes.all { it == question })
        assertEquals(listOf(beam, wet), packed!!.sources.map { it.evidence })
        assertTrue(packed!!.prompt.contains(question))
    }

    @Test fun unchangedSingleQuestionAndUnknownGrammarDoNotAddSearches() = runBlocking {
        var searches = 0
        val retriever = Retriever { _, _, _ -> searches++; emptyList() }
        for (q in listOf("Compare battery capacity and power.", "Explain how it works and why?",
            "How does a hardware wallet protect keys?")) {
            assertEquals(listOf(beam), MultipartEvidence.recover(q, listOf(beam), retriever, 24, 8))
        }
        assertEquals(0, searches)
    }

    @Test fun sourceOnlyNamedIdentifierCannotBeReplacedByAnotherModel() {
        val q = "According to the saved sources, how does projector L-7 produce a beam, and what limits wet storage?"
        val generic = wet.copy(title = "Projector — Storage")
        assertTrue(keep(q, listOf(beam, wet, generic, unrelated)).isEmpty())
        val before = "According to the saved sources, how does L-7 projector produce a beam, and what limits wet storage?"
        assertTrue(keep(before, listOf(beam, wet, generic, unrelated)).isEmpty())
    }
}
