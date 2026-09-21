package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EvidenceTest {
    @Test fun keepsStableCitationFields() {
        val evidence = Evidence("doc-1", "doc-1:0000", "Title", "Source", "Text", 1.25)
        assertEquals("doc-1:0000", evidence.chunkId)
        assertEquals("Source", evidence.source)
    }

    @Test fun rejectsBlankCitationFields() {
        listOf(0, 1, 2, 3, 4).forEach { blankIndex ->
            val fields = mutableListOf("doc", "chunk", "title", "source", "text")
            fields[blankIndex] = " "
            assertThrows(IllegalArgumentException::class.java) {
                Evidence(fields[0], fields[1], fields[2], fields[3], fields[4], 0.0)
            }
        }
    }

    @Test fun rejectsNonFiniteScores() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { score ->
            assertThrows(IllegalArgumentException::class.java) {
                Evidence("doc", "chunk", "title", "source", "text", score)
            }
        }
    }
}
