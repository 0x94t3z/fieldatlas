package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipRankingTest {
    private fun passage(id: String, title: String, text: String, score: Double = -1.0) =
        Evidence(id, "$id:0000", title, "local", text, score)

    @Test fun namedAttributeNeedsOwnerAndAttributeInTheOriginalPassage() {
        val generic = passage("definition", "Hash function — Overview", "A hash function maps inputs to fixed size outputs.", -100.0)
        val owner = passage("owner", "Meridian — Overview", "Meridian is a software platform.", -50.0)
        val relationship = passage("relation", "Specification — Hashes", "Meridian uses the Quartz hash function.")
        val query = "According to the saved sources, which hash function does Meridian use?"
        assertEquals(listOf("hash", "function", "meridian"), QuestionSubject.relationTerms(query))
        assertEquals(listOf(relationship), FtsRetriever.relationshipCandidates(query, listOf(generic, owner, relationship)))
    }

    @Test fun titleAndIndexKeywordsCannotSupplyTheMissingRelationship() {
        val titleOnly = passage("title", "Meridian hash function", "A general definition.")
        val keywordsOnly = passage("keywords", "Definition", "A hash function maps inputs.\nMeSH: Meridian")
        val proposal = passage("proposal", "Specification", "EIP: Meridian hash function\n\nDefine a selected hash function; this proposes a future mechanism.")
        assertTrue(FtsRetriever.relationshipCandidates("Which hash function does Meridian use?", listOf(titleOnly, keywordsOnly, proposal)).isEmpty())
    }

    @Test fun unrelatedQuestionFormsRetainTheirExistingRanking() {
        for (query in listOf("What is a hash function?", "Compare hashing and encryption",
            "Which hash function does meridian use?", "Which language does Go use?", "How does Meridian work?")) {
            assertTrue(query, QuestionSubject.relationTerms(query).isEmpty())
        }
    }

    @Test fun relationshipSlotsAreBoundedAndOriginalWordingIsNotChanged() {
        val rows = (1..4).map { passage("r$it", "Specification", "Meridian uses the Quartz hash function. Version $it.", -it.toDouble()) }
        assertEquals(listOf(rows[3], rows[2]), FtsRetriever.relationshipCandidates("Which hash function does Meridian use?", rows))
        val oversized = passage("long", "Specification", "Meridian uses a hash function. " + "Unrelated text. ".repeat(60))
        assertTrue(FtsRetriever.relationshipCandidates("Which hash function does Meridian use?", listOf(oversized)).isEmpty())
    }
}
