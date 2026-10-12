package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

/** Discovery instructions must not erase a noun that the user actually asks about. */
class SourceSubjectIntentTest {
    @Test fun recognizedSourceInstructionsCannotEnableAnUnverifiedModelExplanation() {
        for (question in listOf("Using only sources, explain source control.",
            "Based on reports, explain source reliability.",
            "What do sources say about source control?", "What does the report say about file signatures?")) {
            assertNotEquals(question, QuestionSubject.text(question))
            assertTrue(question, PromptBuilder.requiresSavedSources(question))
            assertFalse(question, PromptBuilder.allowsModelExplanation(question))
            val packed = PromptBuilder.build(question, emptyList(), 2048)
            assertFalse(packed.mixedAnswer)
            assertTrue(packed.prompt.contains(question))
        }
        for (question in listOf("Using a telescope, how does focusing work?", "How does source control work?",
            "What are sources of information?")) {
            assertFalse(question, PromptBuilder.requiresSavedSources(question))
            assertTrue(question, PromptBuilder.allowsModelExplanation(question))
        }
        val unicode = "Ｕｓｉｎｇ ｏｎｌｙ ｓｏｕｒｃｅｓ, explain source control."
        assertTrue(PromptBuilder.requiresSavedSources(unicode))
        assertEquals(listOf("source", "control"), FtsQuery.from(unicode)!!.terms)
        assertTrue(PromptBuilder.build(unicode, emptyList(), 2048).prompt.contains(unicode))
    }

    @Test fun commaLessDirectivesRetainComparisonAndDefinitionSubjects() {
        for (prefix in listOf("According to saved sources ", "Using only attached files ", "Based on my notes ")) {
            for (topic in listOf("compare conflicting sources.", "tell me about source control.",
                "define source provenance.", "contrast source reliability.")) {
                val question = prefix + topic
                assertEquals(question, topic, QuestionSubject.text(question))
                assertEquals(FtsQuery.from(topic)!!.terms, FtsQuery.from(question)!!.terms)
                assertFalse(PromptBuilder.allowsModelExplanation(question))
            }
        }
    }

    @Test fun substantiveSourceNounsSurvivePlainAndScopedQuestions() {
        val questions = mapOf(
            "Compare conflicting sources before choosing." to listOf("conflicting", "sources", "before", "choosing"),
            "How does source control work?" to listOf("source", "control", "work"),
            "What are sources of information?" to listOf("sources", "information"),
            "Explain open source code." to listOf("open", "source", "code"),
            "Where are river sources?" to listOf("river", "sources"),
            "Sources" to listOf("sources"),
        )
        for ((plain, terms) in questions) {
            assertEquals(plain, terms, FtsQuery.from(plain)!!.terms)
            val scoped = "According to the saved sources, $plain"
            assertEquals(scoped, terms, FtsQuery.from(scoped)!!.terms)
            assertEquals(scoped, FtsQuery.from(scoped)!!.normalizedInput)
            assertFalse(PromptBuilder.allowsModelExplanation(scoped))
        }
    }

    @Test fun explicitReportingWrapperLeavesItsTopicIncludingRepeatedSourceNouns() {
        for (prefix in listOf("Summarize what my saved sources say about ",
            "What do the local documents say about ", "SUMMARISE WHAT PROVIDED SOURCES SAY ABOUT ")) {
            for (topic in listOf("rapamycin", "source control", "sources of information")) {
                val question = prefix + topic + "?"
                assertEquals(topic + "?", QuestionSubject.text(question))
                assertEquals(FtsQuery.from(topic)!!.terms, FtsQuery.from(question)!!.terms)
                assertFalse(PromptBuilder.allowsModelExplanation(question))
            }
        }
    }

    @Test fun unknownGrammarAndNonInstructionTextAreNotSilentlyRewritten() {
        for (question in listOf("According sources of information", "According to the sources of the Nile",
            "What do sources of information say about trust?", "Summarize what sources say about",
            "Read what my saved sources say about trust", "Source control", "open source")) {
            assertEquals(question, QuestionSubject.text(question))
            assertTrue(question, FtsQuery.from(question)!!.terms.any { it == "source" || it == "sources" })
        }
        assertEquals(listOf("sources", "information"), FtsQuery.from("Ｓｏｕｒｃｅｓ of information")!!.terms)
        assertTrue(FtsQuery.from("sources OR title:information")!!.matchExpression.contains("\"source\"*"))
    }

    @Test fun strictRelevanceRetainsSubstantiveSourceTermsAndIgnoresPlannerDrift() {
        val question = "According to the saved sources, compare source reliability."
        val direct = Evidence("direct", "direct:0", "Evaluation", "fixture://direct",
            "Source reliability depends on provenance.", 0.0)
        val distractor = direct.copy(documentId = "other", chunkId = "other:0", source = "fixture://other",
            text = "Motor reliability depends on cooling and servicing.")
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(distractor, direct),
            FtsQuery.from(question)!!.terms, listOf("motor", "cooling", "servicing"), question))
        assertTrue(EvidenceRelevance.coversQuestionForMerge(direct, FtsQuery.from(question)!!.terms))
        assertFalse(EvidenceRelevance.coversQuestionForMerge(distractor, FtsQuery.from(question)!!.terms))
    }

    @Test fun sourceSubjectSurvivesBoundedMergeAndPackingWithItsOriginalIdentity() {
        val question = "According to the saved sources, compare source reliability."
        val direct = Evidence("direct", "direct:0", "Evaluation", "fixture://direct",
            "Source reliability depends on provenance; independent confirmation is still required.", 0.0)
        val noise = (0 until 30).map { index -> listOf(direct.copy(documentId = "noise-$index",
            chunkId = "noise-$index:0", source = "fixture://noise-$index",
            text = "Motor reliability depends on cooling.", score = -100.0)) }
        for (runs in listOf(noise + listOf(listOf(direct)), listOf(listOf(direct)) + noise)) {
            val selected = MultiKnowledgeRetriever.mergeRelevantEvidence(runs, question, question, 1)
            assertEquals(listOf(direct), selected)
            val packed = PromptBuilder.build(question, selected, 2048)
            assertEquals(listOf(direct), packed.sources.map { it.evidence })
            assertFalse(packed.mixedAnswer)
            assertTrue(packed.prompt.contains(question))
            assertTrue(packed.prompt.contains(direct.text))
        }
    }
}
