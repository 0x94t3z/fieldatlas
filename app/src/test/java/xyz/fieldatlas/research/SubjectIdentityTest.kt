package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class SubjectIdentityTest {
    private fun overview(name: String, text: String) = Evidence("wp-$name", "wp-$name:0",
        "$name — Overview", "fixture://$name", text, 0.0)

    @Test fun roleQuestionsFindTheEntityAndLetterQualifiedSubjectsKeepTheirIdentity() {
        val role = "What role do chloroplasts play in photosynthesis?"
        assertTrue(SubjectLookup.leads(role))
        assertEquals("chloroplasts", SubjectLookup.candidates(role).first().text)
        val wave = "How do earthquake P waves differ from S waves?"
        val candidates = SubjectLookup.candidates(wave, SubjectLookup.subjectsWanted(wave))
        assertTrue(candidates.toString(), candidates.any { it.text == "P wave" })
        assertTrue(candidates.toString(), candidates.any { it.text == "S wave" })
        val p = overview("P-wave", "A P-wave is a compressional seismic wave.")
        assertEquals(p, SubjectLookup.overviewOf("P wave", listOf(p), wave))
        assertNull(SubjectLookup.overviewOf("S wave", listOf(p), wave))
    }

    @Test fun roleAndWaveQuestionsPackTheirActualSubjectsDespiteTangentialSearchHits() = runBlocking {
        val chloroplast = overview("Chloroplast", "A chloroplast conducts photosynthesis in plant cells. Chlorophyll captures sunlight to make chemical energy and sugars.")
        val p = overview("P wave", "P waves are seismic body waves. P waves travel faster and are the first signal from an earthquake. They travel through solids, liquids and gases.")
        val s = overview("S wave", "S waves are transverse seismic waves. They travel through solids but not liquids.")
        val unrelated = overview("Tyrosine", "A tyrosine residue is an electron donor in photosynthesis in chloroplasts.")
        val rows = listOf(chloroplast, p, s, unrelated)
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(unrelated)
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int) = rows.filter {
                it.title.substringBefore(" — ").equals(phrase, true)
            }
            override suspend fun article(article: String, limit: Int) = rows.filter {
                it.title.substringBefore(" — ").equals(article, true)
            }
        }
        for ((question, expected) in listOf(
            "What role do chloroplasts play in photosynthesis?" to listOf(chloroplast),
            "How do earthquake P waves differ from S waves?" to listOf(p, s),
        )) {
            var packed: PackedPrompt? = null
            val events = ResearchOrchestrator(retriever, FakeInferenceGateway(listOf("Unverified explanation.")),
                articleDepth = true, onEvidencePacked = { packed = it }).research(question).toList()
            assertTrue(events.last() is ResearchEvent.Complete)
            val actual = checkNotNull(packed).sources.map { it.evidence.chunkId }
            expected.forEach { assertTrue("$question: $actual", it.chunkId in actual) }
        }
    }

    @Test fun omittedProperNameCannotBecomeADifferentNamedEntity() {
        val q = "Explain how Formula One championships work."
        val candidate = SubjectLookup.candidates(q).first { it.text == "One championship" }
        assertEquals("Formula", candidate.modifier)
        val unrelated = overview("ONE Championship", "ONE Championship is a mixed martial arts promotion.")
        assertFalse(SubjectLookup.accepts(candidate, unrelated))
        val formula = overview("Formula One", "Formula One is a motorsport championship. Teams compete in races.")
        val full = SubjectLookup.candidates(q).first { it.text == "Formula One" }
        assertTrue(SubjectLookup.accepts(full, formula))
        assertTrue(SubjectLookup.accepts(candidate, formula))
    }

    @Test fun exactNamingWordsMustStayConnectedAndPeriodFramingStillWorks() {
        val candidate = SubjectLookup.candidates("Explain how Silver River engines work.")
            .first { it.text == "River engines" }
        assertEquals("Silver", candidate.modifier)
        for (body in listOf("River engines are machines with silver coatings.",
            "Silverton River engines are machines.", "A silver mineral is common. River engines are machines.")) {
            assertFalse(body, SubjectLookup.accepts(candidate, overview("River engines", body)))
        }
        assertTrue(SubjectLookup.accepts(candidate, overview("River engines", "Silver River engines are machines.")))
        val classic = SubjectLookup.candidates("Why did the Classic Maya civilization collapse?")
            .first { it.text == "Maya civilization" }
        assertTrue(SubjectLookup.accepts(classic, overview("Maya civilization", "The Maya civilization was a Mesoamerican civilization.")))
        val york = SubjectLookup.candidates("Explain New York.").first { it.text == "York" }
        assertFalse(SubjectLookup.accepts(york, overview("York", "York is a city in England.")))
    }

    @Test fun wrongTitleLookupCannotPrependAnotherSubjectAfterCorrectFtsRetrieval() = runBlocking {
        val correct = overview("Formula One", "Formula One is a motorsport championship. Teams compete in races.")
        val wrong = overview("ONE Championship", "ONE Championship is a mixed martial arts promotion.")
        val plain = "Explain how Formula One championships work."
        for (q in listOf(plain, "According to the saved sources, $plain")) {
            val retriever = object : Retriever {
                override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(correct)
                override suspend fun titled(phrase: String, sections: List<String>, limit: Int) = when {
                    phrase.equals("One championship", true) -> listOf(wrong)
                    phrase.equals("Formula One", true) -> listOf(correct)
                    else -> emptyList()
                }
                override suspend fun article(article: String, limit: Int) = when {
                    article.equals("ONE Championship", true) -> listOf(wrong)
                    article.equals("Formula One", true) -> listOf(correct)
                    else -> emptyList()
                }
            }
            var packed: PackedPrompt? = null
            val inference = FakeInferenceGateway(listOf("Wrong blended sports story."))
            val events = ResearchOrchestrator(retriever, inference, articleDepth = true,
                onEvidencePacked = { packed = it }).research(q).toList()
            assertTrue(events.last() is ResearchEvent.Complete)
            assertEquals(listOf(correct), checkNotNull(packed).sources.map { it.evidence })
            assertEquals(2, inference.generateCalls)
            assertEquals(CitedExplanation.UNAVAILABLE, events.filterIsInstance<ResearchEvent.Token>().last().text)
            assertFalse(events.filterIsInstance<ResearchEvent.Token>().joinToString { it.text }.contains("martial arts"))
        }
    }
}
