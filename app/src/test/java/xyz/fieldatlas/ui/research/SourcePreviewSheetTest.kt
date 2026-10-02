package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.fieldatlas.research.Evidence

class SourcePreviewSheetTest {
    @Test fun mapListingsBecomePlaceDetailsWithCoordinatesLast() {
        val place = placeSheetContent(
            "Destination: Berlin\nCategory: Eat\nPlace: Daizu\nType: restaurant\nVegan: fully vegan\n" +
                "Address: Falckensteinstraße 36, 10997 Berlin\nPhone: +49 30 123\nLatitude: 52.498627\nLongitude: 13.442626\n" +
                "Listing last checked: 2025-09-01\nMap data snapshot: 2026-10-02 (© OpenStreetMap contributors)",
        )!!
        assertEquals("Daizu", place.name)
        assertEquals("Falckensteinstraße 36, 10997 Berlin", place.address)
        assertEquals("+49 30 123", place.phone)
        assertEquals(52.498627, place.lat, 1e-9)
        assertEquals(listOf("Destination", "Type", "Vegan", "Address", "Last checked", "Coordinates"),
            place.facts.map { it.first })
        assertEquals("52.49863, 13.44263", place.facts.last().second)
    }

    @Test fun ordinaryPassagesAreNotPlaces() {
        assertNull(placeSheetContent("Photosynthesis is a process in which green plants make their own food."))
        assertNull(placeSheetContent("Place: Somewhere\nAddress: Main Street"))
    }

    @Test fun copiedAnswersListEverySourceWithItsLink() {
        val sources = listOf(
            Evidence("d1", "d1:0", "History of Japan — Overview", "https://simple.wikipedia.org/w/index.php?oldid=10658093", "t", 0.0),
        )
        assertEquals(
            "Tell me about Japan's history\n\nJapan's history begins in prehistoric times. [S1]\n\nSources\n" +
                "[S1] History of Japan — Overview — https://simple.wikipedia.org/w/index.php?oldid=10658093",
            answerWithSources("Tell me about Japan's history", "Japan's history begins in prehistoric times. [S1]", sources),
        )
    }
}
