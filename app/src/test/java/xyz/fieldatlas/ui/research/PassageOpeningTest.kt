package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageOpeningTest {
    @Test fun aPlaceSourceShowsItsOwnWordsNotItsDataFields() {
        fun place(id: String, vararg lines: String) = xyz.fieldatlas.research.Evidence("$id", "$id:0000", "Rome — Place", "https://example.org",
            (lines.toList() + listOf("Latitude: 41.88", "Longitude: 12.49")).joinToString("\n"), 0.0)
        val hospital = place("osm-place-1", "Destination: Rome", "Category: Health", "Place: Ospedale Britannico", "Type: hospital",
            "Address: Via di Santo Stefano Rotondo 6, 00184 Roma")
        assertEquals("Hospital · Via di Santo Stefano Rotondo\u00A06", sourceOpening(hospital))
        val cafe = place("wv-place-2", "Destination: Tokyo/Shibuya", "Category: Drink", "Place: Excelsior Cafe",
            "Description: Great coffee and delicious sandwiches, with a warm smile.")
        assertEquals("“Great coffee and delicious sandwiches, with a warm smile.”", sourceOpening(cafe))
        // Any other passage keeps its opening words.
        assertEquals("“The tower is 330 metres tall.”", sourceOpening(xyz.fieldatlas.research.Evidence("d", "d:0", "Eiffel Tower", "x",
            "The tower is 330 metres tall.", 0.0)))
    }

    @Test fun aShortPassageIsQuotedWhole() {
        assertEquals("“The tower is 330 metres tall.”", passageOpening("The tower is 330 metres tall."))
    }

    @Test fun aLongPassageIsCutWithAnEllipsisAndItsLineBreaksJoined() {
        val opening = passageOpening("It was built for the 1889 World's Fair\nto mark the centenary of the French Revolution, " +
            "and was at first criticised by some of France's leading artists.")!!
        assertTrue(opening, opening.endsWith("…”"))
        assertTrue(opening, "\n" !in opening)
        assertTrue(opening, opening.length <= 92)
    }

    @Test fun aPassageCutMidWordOpensAtItsFirstWholeWord() {
        assertEquals("\u201C\u2026parts on site.\u201D", passageOpening("e parts on site."))
    }

    @Test fun anEmptyPassageHasNoOpening() {
        assertNull(passageOpening("   "))
    }
}
