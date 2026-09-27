package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceRelevanceTest {
    private fun evidence(text: String, matchedBy: String?) = Evidence(
        documentId = "doc",
        chunkId = text,
        title = "Article",
        source = "local",
        text = text,
        score = -1.0,
        matchedBy = matchedBy,
    )

    @Test fun `multi-term questions reject incidental single-word matches`() {
        val relevant = evidence("Earth's axial tilt gives opposite hemispheres different seasons", "keyword: earth, seasons")
        val incidental = evidence("Earth's geomagnetic field changes over time", "keyword: earth")

        assertEquals(
            listOf(relevant),
            EvidenceRelevance.keep(listOf(incidental, relevant), listOf("earth", "seasons", "hemispheres")),
        )
    }

    @Test fun `seasons question rejects two incidental biology overlaps`() {
        val incidental = evidence(
            "Seasonal changes in the Earth's geomagnetic field affect longevity",
            "keyword: earth, seasons",
        )
        val relevant = evidence(
            "Earth's axial tilt gives opposite hemispheres different seasons",
            "keyword: earth, hemispheres, opposite, seasons",
        )
        assertEquals(
            listOf(relevant),
            EvidenceRelevance.keep(
                listOf(incidental, relevant),
                listOf("earth", "hemispheres", "opposite", "seasons"),
            ),
        )
    }

    @Test fun `missing keyword attribution does not bypass relevance gate`() {
        val incidental = evidence("Earth's geomagnetic field changes over time", null)
        val relevant = evidence("Earth's axial tilt gives opposite hemispheres different seasons", null)
        assertEquals(
            listOf(relevant),
            EvidenceRelevance.keep(
                listOf(incidental, relevant),
                listOf("earth", "hemispheres", "opposite", "seasons"),
            ),
        )
    }

    @Test fun `single topic Indonesian query keeps Indonesian evidence`() {
        val relevant = evidence("Indonesian cooking uses diverse regional ingredients", "keyword: indonesian")
        assertEquals(listOf(relevant), EvidenceRelevance.keep(listOf(relevant), listOf("indonesian")))
    }

    @Test fun `planner synonym can support a direct question term`() {
        val relevant = evidence("Card battery stores electrical energy", "keyword: battery, accumulator")
        assertEquals(
            listOf(relevant),
            EvidenceRelevance.keep(
                listOf(relevant),
                questionTerms = listOf("car", "power"),
                expandedTerms = listOf("battery", "accumulator"),
            ),
        )
    }

    @Test fun `high confidence concepts can bridge vocabulary mismatch`() {
        val semantic = evidence("Myocardial infarction damages heart tissue", "concept match 0.84")
        assertEquals(
            listOf(semantic),
            EvidenceRelevance.keep(listOf(semantic), listOf("cardiac", "event")),
        )
    }

    @Test fun `travel listings require the requested destination rather than a directions mention`() {
        val chiangMai = evidence("Destination: Chiang Mai\nPlace to eat: Vegan Heaven\nVegan food", "keyword: vegan, chiang")
            .copy(documentId = "wv-eat-1-0001")
        val pai = evidence("Destination: Pai\nPlace to eat: Garden Cafe\nVegan food; road to Chiang Mai", "keyword: vegan, chiang")
            .copy(documentId = "wv-eat-2-0001")
        val district = evidence("Destination: Chiang Mai/Old City\nPlace to eat: Vegan House", "keyword: vegan, chiang")
            .copy(documentId = "wv-eat-3-0001")

        assertEquals(
            listOf(chiangMai, district),
            EvidenceRelevance.keep(
                listOf(pai, chiangMai, district),
                listOf("vegan", "restaurants", "chiang", "mai"),
                question = "Best vegan restaurants in Chiang Mai?",
            ),
        )
    }

    @Test fun `vegan request excludes same city restaurants without a vegan claim`() {
        val vegan = evidence("Destination: Chiang Mai\nPlace to eat: Guan Im\nPure vegan restaurant", "keyword: vegan, chiang, mai")
            .copy(documentId = "wv-eat-vegan")
        val unspecified = evidence("Destination: Chiang Mai\nPlace to eat: Old Chiang Mai Cultural Centre\nBest northern Thai food", "keyword: chiang, mai, best")
            .copy(documentId = "wv-eat-unspecified")
        val vegetarian = evidence("Destination: Chiang Mai\nPlace to eat: Veg Table\nVegetarian dishes", "keyword: chiang, mai, vegetarian")
            .copy(documentId = "wv-eat-vegetarian")

        assertEquals(
            listOf(vegan),
            EvidenceRelevance.keep(
                listOf(unspecified, vegetarian, vegan),
                listOf("best", "vegan", "chiang", "mai"),
                question = "Best vegan restaurants in Chiang Mai?",
            ),
        )
    }

    @Test fun `general travel listing from another city is not evidence for Berlin`() {
        val berlin = evidence("Destination: Berlin\nCategory: See\nPlace: City Museum\nHistory museum", "keyword: berlin, museum")
            .copy(documentId = "wv-place-berlin")
        val hamburg = evidence("Destination: Hamburg\nCategory: See\nPlace: Harbor Museum\nRoad to Berlin", "keyword: berlin, museum")
            .copy(documentId = "wv-place-hamburg")

        assertEquals(
            listOf(berlin),
            EvidenceRelevance.keep(
                listOf(hamburg, berlin), listOf("museum", "berlin"),
                question = "Which museums are in Berlin?",
            ),
        )
    }

    @Test fun `museum request excludes hotels and restaurants in the same city`() {
        fun place(id: String, category: String, name: String) =
            evidence("Destination: Berlin\nCategory: $category\nPlace: $name\nNear Berlin Museum", "keyword: berlin, museum")
                .copy(documentId = "wv-place-$id")
        val museum = place("museum", "See", "Berlin Museum")
        val hotel = place("hotel", "Sleep", "Museum Hotel")
        val restaurant = place("restaurant", "Eat", "Museum Cafe")

        assertEquals(
            listOf(museum),
            EvidenceRelevance.keep(
                listOf(hotel, restaurant, museum), listOf("museum", "berlin"),
                question = "Which museums are in Berlin?",
            ),
        )
    }

    @Test fun `lookup wording does not require listed or which to appear in a place passage`() {
        val museum = evidence("Destination: Berlin\nCategory: See\nPlace: City Museum", "keyword: berlin, museum")
            .copy(documentId = "wv-place-berlin-museum")
        assertEquals(
            listOf(museum),
            EvidenceRelevance.keep(
                listOf(museum), listOf("which", "museums", "listed", "berlin"),
                question = "Which museums are listed in Berlin?",
            ),
        )
    }
}
