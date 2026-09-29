package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceRelevanceTest {
    @Test fun `an author name cannot establish the topic of a scientific passage`() {
        val question = "According to my saved sources, what is the population of Atlantis today?"
        val study = evidence("PMID 123\nYear: 2018\nAuthors: Atlantis Evan\nAbstract: The study assessed diabetes in the general population of Australia.", null)
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(study),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `source request boilerplate and planner terms cannot substitute for the requested subject`() {
        val question = "According to my saved sources, what is the population of Atlantis today?"
        val unrelated = evidence("Local sources discuss population retirement income today.", null)
        val direct = evidence("The population of Atlantis is not recorded in this fictional atlas.", null)
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(unrelated, direct),
            FtsQuery.from(question)!!.terms, listOf("retirement", "income"), question))
    }

    @Test fun `possessive overview rejects place listings and incidental expanded study matches`() {
        listOf("Japan's", "Japan’s", "France's").forEach { subject ->
            val country = subject.substringBefore("'").substringBefore("’")
            val question = "Tell me about $subject history"
            val museum = evidence("Description: A museum of history and folk culture of $country.", null)
            val study = evidence("Patients in $country were assessed for vascular disease.\nMeSH: History", null)
            val direct = evidence("$subject history spans several periods. The early period is poorly documented.", null)
            assertEquals(question, listOf(direct), EvidenceRelevance.keep(
                listOf(museum, study, direct), FtsQuery.from(question)!!.terms,
                listOf("patients", "vascular", "culture"), question))
        }
    }

    @Test fun `possessive overview preserves definitions beyond history`() {
        val question = "Explain Earth's atmosphere"
        val direct = evidence("Earth's atmosphere is a mixture of gases surrounding the planet.", null)
        val incidental = evidence("We studied the atmosphere of a laboratory on Earth.", null)
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(incidental, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `auxiliary have cannot make Haven Farm a source for hemispheric seasons`() {
        val farm = evidence("Destination: Tweed\nCategory: Buy\nPlace: Earth Haven Farm\nSeasonal produce.", null)
            .copy(documentId = "wv-place-farm")
        val question = "Why do Earth's hemispheres have opposite seasons?"
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(farm), FtsQuery.from(question)!!.terms, question = question))
    }
    @Test fun `overview preserves singular definitions appositives and acronyms`() {
        listOf(
            "What are viruses?" to "A virus is an infectious agent that reproduces inside host cells.",
            "Compare batteries and capacitors." to "A battery stores chemical energy.",
            "Explain mitosis" to "Mitosis, a type of cell division, produces daughter cells.",
            "Explain cellular senescence" to "Cellular senescence (CS) is a stable arrest of cell division.",
        ).forEach { (question, passage) ->
            val item = evidence(passage, null)
            assertEquals(question, listOf(item), EvidenceRelevance.keep(listOf(item), FtsQuery.from(question)!!.terms, question = question))
        }
    }

    @Test fun `English overview grammar does not exclude foreign language evidence`() {
        val question = "Explain mitosis"
        val item = evidence("La mitosis es un proceso de división celular.", null)
        assertEquals(listOf(item), EvidenceRelevance.keep(listOf(item), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `source specific explanation does not require definition grammar`() {
        val question = "Explain this study."
        val item = evidence("This study examined mitosis in mice.", null)
        assertEquals(listOf(item), EvidenceRelevance.keep(listOf(item), FtsQuery.from(question)!!.terms, question = question))
    }
    @Test fun `planner expansion cannot rescue incidental overlap in a detailed question`() {
        val incidental = evidence("Seasonal reproduction in rainforest flies changes with sunlight.", "keyword: seasons, sunlight")
        val question = "Why do Earth's hemispheres have opposite seasons?"
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(incidental),
            FtsQuery.from(question)!!.terms, listOf("sunlight", "summer", "winter"), question))
    }
    @Test fun `overview comparison rejects studies that only mention a transition between its subjects`() {
        val incidental = evidence("These findings explain a regulatory mechanism during the transition from meiosis to the first mitosis in mammals.\nMeSH: Cells", null)
        val direct = evidence("Mitosis preserves chromosome number whereas meiosis reduces it.", null)
        val question = "Compare mitosis and meiosis. Explain their outcomes."
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(incidental, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `overview gate is topic independent and retains separate explanations of each subject`() {
        val incidental = evidence("We measured batteries and capacitors in an experimental sensor.", null)
        val battery = evidence("Batteries store energy chemically.", null)
        val capacitor = evidence("Capacitors store energy in an electric field.", null)
        val question = "Compare batteries and capacitors."
        assertEquals(listOf(battery, capacitor), EvidenceRelevance.keep(listOf(incidental, battery, capacitor),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `definition request keeps explanatory prose with adjacent limitations rather than incidental mentions`() {
        val incidental = evidence("We investigated cellular senescence in mice exposed to the compound.", null)
        val direct = evidence("Abstract: Cellular senescence is a stable arrest of cell division. These observations alone do not establish cell death.", null)
        val question = "Explain cellular senescence and how it differs from a cell dying."
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(incidental, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `specific study questions retain relevant research observations`() {
        val study = evidence("We investigated cellular senescence in mice exposed to the compound.", null)
        val question = "What did the compound study investigate about cellular senescence in mice?"
        assertEquals(listOf(study), EvidenceRelevance.keep(listOf(study), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `ambiguous multiword comparisons do not weaken relevance to one generic word`() {
        val incidental = evidence("Energy levels change.\nMeSH: Cells", null)
        val question = "Compare solar energy and wind energy costs"
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(incidental),
            FtsQuery.from(question)!!.terms, question = question))
        assertEquals(emptyList<String>(), EvidenceRelevance.comparisonTerms(
            "Compare mitosis and meiosis in growth and sexual reproduction"))
    }

    @Test fun `comparison subjects outrank incidental growth and reproduction overlap`() {
        val broad = evidence("Growth and sexual reproduction support different outcomes.\nMeSH: Cells", null)
        val direct = evidence("Mitosis and meiosis are two kinds of cell division.", null)
        val question = "Compare mitosis and meiosis. Explain how their different outcomes support growth and sexual reproduction."
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(broad, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `broader body coverage outranks strong title scores in scholarly results`() {
        val narrow = evidence("Mitosis supports growth and repair.\nMeSH: Meiosis; Reproduction", null)
            .copy(score = -100.0)
        val broad = evidence("Mitosis supports growth and repair; meiosis supports reproduction.\nMeSH: Cells", null)
        assertEquals(listOf(broad, narrow), EvidenceRelevance.keep(listOf(narrow, broad),
            listOf("mitosis", "meiosis", "growth", "repair", "reproduction")))
    }

    @Test fun `index tags and title alone cannot establish passage relevance`() {
        val tagged = evidence("A treatment improved fertility.\nMeSH: Mitosis; Meiosis; Growth; Sexual Reproduction", null)
            .copy(title = "Mitosis and meiosis")
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(tagged), listOf("mitosis", "meiosis")))
    }

    @Test fun `body covering both topics leads a title boosted partial match`() {
        val partial = evidence("Mitosis supports growth.\nMeSH: Meiosis", null).copy(title = "Mitosis and meiosis", score = -100.0)
        val comparison = evidence("Mitosis supports growth whereas meiosis produces gametes.", null)
        assertEquals(listOf(comparison), EvidenceRelevance.keep(listOf(partial, comparison), listOf("mitosis", "meiosis")))
    }

    @Test fun `removing index tags preserves ordinary prose and source metadata`() {
        val relevant = evidence("Mitosis and meiosis differ.\nMeSH: Cells; Humans", null)
        assertEquals(listOf(relevant), EvidenceRelevance.keep(listOf(relevant), listOf("mitosis", "meiosis")))
    }

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
