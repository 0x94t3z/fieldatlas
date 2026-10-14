package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class LeviathanSessionTest {
    private val record = """{"id":"c1","document_id":"d1","title":"Water","source":"Saved reference","text":"The complete original passage."}"""
    private val row = """{"record":$record,"relevance":3.8}"""
    @Test fun fullPassagesRetainIdentityAndLeviathanRelevance() {
        val evidence = LeviathanSession.decode("[$row]", 5).single()
        assertEquals("c1", evidence.chunkId)
        assertEquals("The complete original passage.", evidence.text)
        // bm25 direction: more negative is stronger.
        assertEquals(-3.8, evidence.score, 0.0)
    }
    @Test fun malformedAndUnboundedResponsesFailClosed() {
        for (raw in listOf("[$row,$row]", "[{}]", "{}", "[" + row.replace("\"c1\"", "null") + "]", "[$record]",
                "[" + row.replace("3.8", "\"high\"") + "]")) {
            assertTrue(runCatching { LeviathanSession.decode(raw, 5) }.isFailure)
        }
        assertTrue(runCatching { LeviathanSession.decode("[$row]", 0) }.isFailure)
        assertTrue(runCatching { LeviathanSession.decode("[$row,$row]", 1) }.isFailure)
    }
}
