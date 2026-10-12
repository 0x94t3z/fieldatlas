package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CappedMatchCountCacheTest {
    @Test fun repeatedExactProbeLoadsOnceIncludingAbsentTerms() {
        val cache = CappedMatchCountCache()
        var loads = 0
        repeat(4) {
            assertEquals(7, cache.getOrLoad("\"solar\"*", 100_000) { loads++; 7 })
            assertEquals(0, cache.getOrLoad("\"absent\"*", 100_000) { loads++; 0 })
        }
        assertEquals(2, loads)
    }

    @Test fun capsAndExactMatchExpressionsRemainIndependent() {
        val cache = CappedMatchCountCache()
        val probes = listOf(
            "\"rule\"" to 1, "\"rule\"" to 100_000, "\"rule\"*" to 100_000,
            "title : \"rule\"*" to 100_000, "text : \"rule\"*" to 100_000,
            "\"rule\"* AND \"owner\"" to 100_000,
        )
        var loads = 0
        repeat(2) {
            probes.forEachIndexed { index, (expression, cap) ->
                assertEquals(index, cache.getOrLoad(expression, cap) { loads++; index })
            }
        }
        assertEquals(probes.size, loads)
    }

    @Test fun unicodeAndIdentifierSpellingsAreNotNormalizedByTheCache() {
        val cache = CappedMatchCountCache()
        val expressions = listOf("\"café\"", "\"cafe\"", "\"0xffffffff\"", "\"0xfffffffe\"")
        var loads = 0
        repeat(2) {
            expressions.forEachIndexed { index, expression ->
                assertEquals(index, cache.getOrLoad(expression, 100_000) { loads++; index })
            }
        }
        assertEquals(4, loads)
    }

    @Test fun leastRecentlyUsedEntryIsEvictedAtTheBound() {
        val cache = CappedMatchCountCache(maxEntries = 2)
        var loads = 0
        fun get(term: String) = cache.getOrLoad(term, 100_000) { ++loads }
        assertEquals(1, get("a"))
        assertEquals(2, get("b"))
        assertEquals(1, get("a"))
        assertEquals(3, get("c"))
        assertEquals(1, get("a"))
        assertEquals(4, get("b"))
    }

    @Test fun oversizedExpressionsAreNotRetainedAndStillLoadNormally() {
        val cache = CappedMatchCountCache(maxExpressionChars = 4)
        var loads = 0
        repeat(3) {
            assertEquals(3, cache.getOrLoad("abcde", 100_000) { loads++; 3 })
            assertEquals(4, cache.getOrLoad("abcd", 100_000) { loads++; 4 })
        }
        assertEquals(4, loads)
    }

    @Test fun loaderFailureIsRetriedInsteadOfBecomingAnAbsentTerm() {
        val cache = CappedMatchCountCache()
        assertThrows(IllegalStateException::class.java) {
            cache.getOrLoad("a", 100_000) { error("fixture read failure") }
        }
        assertEquals(5, cache.getOrLoad("a", 100_000) { 5 })
    }

    @Test fun clearingAndNewHandlesNeverReuseOldCounts() {
        val old = CappedMatchCountCache()
        assertEquals(4, old.getOrLoad("a", 100_000) { 4 })
        old.clear()
        assertEquals(8, old.getOrLoad("a", 100_000) { 8 })
        val replacement = CappedMatchCountCache()
        assertEquals(12, replacement.getOrLoad("a", 100_000) { 12 })
    }

    @Test fun packCountsAreNeverSharedBetweenHandles() {
        val first = CappedMatchCountCache()
        val second = CappedMatchCountCache()
        repeat(2) {
            assertEquals(1, first.getOrLoad("a", 100_000) { 1 })
            assertEquals(10, second.getOrLoad("a", 100_000) { 10 })
        }
    }

    @Test fun fiftyPackOriginalAndExpandedProbeFixtureRemovesOnlyDuplicateLoads() {
        // A deterministic call-count fixture, not a phone timing or actual full-library trace.
        val original = listOf("owner", "rule", "missing", "return")
        val expanded = listOf("owner", "rule", "missing", "condition")
        var uncachedCalls = 0
        var cachedCalls = 0
        val caches = List(50) { CappedMatchCountCache() }
        caches.forEachIndexed { pack, cache ->
            for (terms in listOf(original, expanded, original)) {
                for (term in terms) {
                    val expression = "\"$term\"*"
                    val expected = if (term == "missing") 0 else pack + term.length
                    uncachedCalls++
                    val actual = cache.getOrLoad(expression, 100_000) { cachedCalls++; expected }
                    assertEquals(expected, actual)
                }
            }
        }
        assertEquals(600, uncachedCalls)
        assertEquals(250, cachedCalls)
        assertEquals(350, uncachedCalls - cachedCalls)
    }

    @Test fun rejectsUnboundedOrEmptyCacheConfiguration() {
        assertThrows(IllegalArgumentException::class.java) { CappedMatchCountCache(maxEntries = 0) }
        assertThrows(IllegalArgumentException::class.java) { CappedMatchCountCache(maxExpressionChars = 0) }
    }
}
