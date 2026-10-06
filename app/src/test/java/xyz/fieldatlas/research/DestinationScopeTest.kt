package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationScopeTest {
    private fun phrases(question: String) = DestinationScope.candidates(question).map { it.phrase }

    @Test fun capitalisedPhrasesComeLongestFirstWithoutQuestionOpeners() {
        val mexico = phrases("first time in Mexico City, where should I go for really good street food?")
        assertEquals("Mexico City", mexico.first())
        assertTrue(mexico.containsAll(listOf("Mexico", "City")))
        assertFalse(mexico.any { it.equals("first", ignoreCase = true) || it.equals("where", ignoreCase = true) })

        val capeTown = phrases("Best beaches in Cape Town where you can actually swim?")
        assertEquals("Cape Town", capeTown.first())
        assertFalse("Best" in capeTown)
        // A one-word opener is not a place, but the same word after "in" is tried.
        assertFalse("Nice" in phrases("Nice vegan places in Berlin"))
        assertTrue("Nice" in phrases("vegetarian restaurants in Nice"))
    }

    @Test fun wordsAfterAPrepositionAreTriedSoLowercaseNamesWork() {
        assertEquals(listOf("budapest"), phrases("where can a vegan get dinner in budapest?"))
        val bangkok = phrases("need a 24 hour pharmacy in Bangkok around Sukhumvit")
        assertEquals(listOf("Bangkok", "Sukhumvit"), bangkok)
        // Names may hold a joining word; the phrase stops at a clause break or a filler word.
        assertEquals("Rio de Janeiro", phrases("beaches in Rio de Janeiro, please").first())
        assertTrue(phrases("museums in the morning").isEmpty())
    }

    @Test fun resolvingKeepsTheLongestHeldPhraseAndNotItsWords() {
        val held = setOf("Mexico City", "Nice", "Berlin")
        val question = "Nice vegan places in Mexico City"
        assertEquals(listOf("Mexico City"), DestinationScope.resolve(DestinationScope.candidates(question)) { it in held })
        assertEquals(listOf("Nice"), DestinationScope.resolve(DestinationScope.candidates("vegan places in Nice")) { it in held })
        // A common word that the pack does not hold as a destination never scopes.
        assertTrue(DestinationScope.resolve(DestinationScope.candidates("Where should I go in Split?")) { it in held }.isEmpty())
    }

    @Test fun aScopedTitleIsTheDestinationOrOneOfItsDistricts() {
        assertTrue(DestinationScope.scopes("Mexico City/Centro — Street food alley", listOf("Mexico City")))
        assertTrue(DestinationScope.scopes("Cape Town — Swimming", listOf("Oslo", "cape town")))
        assertTrue(DestinationScope.scopes("Zürich — Kunsthaus", listOf("Zurich")))
        assertFalse(DestinationScope.scopes("Toronto Pearson International Airport — Heirloom Bakery", listOf("Toronto")))
        assertFalse(DestinationScope.scopes("Cape Cod — Golf", listOf("Cape Town")))
    }
}
