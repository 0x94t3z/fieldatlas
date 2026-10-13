package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class AllPackRelevanceRegressionTest {
    @Test fun earthSeasonsQuestionDoesNotAdmitAnotherPlanet() {
        val question = "Why do Earth's northern and southern hemispheres have opposite seasons?"
        val earth = Evidence("enw-earth", "earth:0", "Earth — Axial tilt and seasons", "Saved encyclopedia",
            "Earth's axial tilt causes its northern and southern hemispheres to receive different sunlight through the seasons.", -1.0)
        val pluto = earth.copy(documentId = "enw-pluto", chunkId = "pluto:0", title = "Atmosphere of Pluto — Seasonal changes",
            text = "Pluto's northern and southern hemispheres receive different sunlight as its seasons change.", score = -10.0)
        assertEquals(listOf(earth), EvidenceRelevance.keep(listOf(pluto, earth), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun museumLookupDoesNotAdmitAHotelMentioningAMuseum() {
        val question = "Which museums can I visit in Amsterdam?"
        val museum = Evidence("wv-place-museum", "museum:0", "Amsterdam — Amsterdam Museum", "Saved travel",
            "Destination: Amsterdam\nCategory: See\nPlace: Amsterdam Museum\nDescription: A museum in Amsterdam.", -1.0)
        val hotel = museum.copy(documentId = "wv-place-hotel", chunkId = "hotel:0", title = "Amsterdam — Hotel De Looier",
            text = "Destination: Amsterdam\nCategory: Sleep\nPlace: Hotel De Looier\nDescription: A hotel near a museum in Amsterdam.", score = -10.0)
        assertEquals(listOf(museum), EvidenceRelevance.keep(listOf(hotel, museum), FtsQuery.from(question)!!.terms, question = question))
    }
}
