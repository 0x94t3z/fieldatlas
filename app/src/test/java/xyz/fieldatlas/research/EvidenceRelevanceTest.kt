package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class EvidenceRelevanceTest {
    @Test fun sourceOnlyComparisonKeepsBothDefinitionsNotIncidentalTreatmentMentions() {
        val q = "Using only saved sources, what is the difference between a vaccine and an antibiotic?"
        val a = Evidence("a", "a:1", "Vaccines", "fixture://a", "Vaccines are preparations that train an immune response.", 0.0)
        val b = a.copy(documentId = "b", chunkId = "b:1", title = "Antibiotics", text = "Antibiotics are medicines that fight bacteria.")
        val c = a.copy(documentId = "c", title = "Other topic", text = "The experiment mentions a vaccine and an antibiotic.")
        assertEquals(listOf(a, b), EvidenceRelevance.keep(listOf(a, b, c), FtsQuery.from(q)!!.terms, question = q))
    }

    @Test fun multiwordConceptComparisonDoesNotAcceptGeneNameCollisions() {
        val q = "Using only saved sources, explain how programmed cell death differs from cellular senescence."
        assertEquals(listOf("programmed cell death", "cellular senescence"), EvidenceRelevance.conceptualComparisonSubjects(q))
        val record = Evidence("gene", "gene:0", "CellAge: PDCD10", "fixture://gene",
            "Dataset: CellAge\nGene name: programmed cell death 10\nType of senescence: Replicative\nEffect: Induces", 0.0)
        assertTrue(EvidenceRelevance.keep(listOf(record), FtsQuery.from(q)!!.terms, question = q).isEmpty())
        for (qualified in listOf("Compare cell death and senescence in this experiment.",
            "Compare my vaccine and this antibiotic.", "How does a gene differ from a protein in yeast?"))
            assertTrue(EvidenceRelevance.conceptualComparisonSubjects(qualified).isEmpty())
    }

    @Test fun explicitNamedRecordScopeRejectsAnUnrelatedPaper() {
        val question = "Using only saved sources, does an association in AtlasMap prove a treatment increases lifespan?"
        val paper = Evidence("paper", "p:0", "Treatment results", "Local fixture",
            "A treatment increases lifespan in the animal sample. The association does not prove a mechanism.", -1.0)
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(paper),
            FtsQuery.from(question)!!.terms, question = question))
        val relevant = paper.copy(documentId = "record", text =
            "An association in AtlasMap does not prove that a treatment increases lifespan.")
        assertEquals(listOf(relevant), EvidenceRelevance.keep(listOf(paper, relevant),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun strongSemanticMatchIsNotUndoneByTheLaterKeywordExplanationGate() {
        val question = "Why do boats float?"
        val source = Evidence("reference", "c", "Boat — Buoyancy", "Local",
            "A boat displaces its weight in water. Overloading makes it sink.", -100_001.0,
            matchedBy = "concept match 0.79")
        val terms = FtsQuery.from(question)!!.terms
        assertEquals(listOf(source), EvidenceRelevance.keep(listOf(source), terms, question = question))
        for (attribution in listOf(null, "keyword: boat", "concept match 0.77", "concept match 1.50")) {
            assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(source.copy(matchedBy = attribution)), terms, question = question))
        }
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(
            listOf(source.copy(documentId = "wv-place-boat")), terms, question = question))
    }

    @Test fun namedMultiwordReferenceKeepsItsDefinitionDespiteDifferentConsequenceWords() {
        val question = "How can sampling bias make a large survey misleading?"
        val definition = Evidence("reference", "reference:0", "Sampling bias — Overview", "Pinned reference",
            "In statistics, sampling bias is a bias in which some members of a population are more likely to be selected than others.", -1.0)
        val titleOnly = definition.copy(documentId = "noise", text =
            "The experiment recorded two measurements. The records contain no definition or explanation.")
        val incidental = definition.copy(documentId = "unrelated", title = "Experimental apparatus — Overview")
        assertEquals(listOf(definition), EvidenceRelevance.keep(listOf(titleOnly, incidental, definition),
            FtsQuery.from(question)!!.terms, question = question))
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(definition),
            FtsQuery.from("According to my saved report, how was this survey sampled?")!!.terms,
            question = "According to my saved report, how was this survey sampled?"))
    }

    @Test fun generalExplanationRejectsMetaphorsButKeepsRealMechanisms() {
        val question = "Why do boats float?"
        val metaphor = Evidence("aging", "aging:0", "Funding aging research", "Saved article",
            "A rising tide floats all boats. Research funding helps our project grow.", -1.0)
        val direct = metaphor.copy(documentId = "physics", title = "Buoyancy", text =
            "Boats float because they displace water whose weight equals their own weight.")
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(metaphor, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun generalNetworkQuestionCannotUseAnAccommodationListing() {
        val question = "Can two computers share files over a local network without internet access?"
        val listing = Evidence("wv-place-hotel", "hotel:0", "Hotel computers", "Travel",
            "Computers share files over the local network because internet access is provided to hotel guests.", -1.0)
        val direct = listing.copy(documentId = "guide", title = "Local network file sharing", source = "Reference")
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(listing, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }
    @Test fun `short causal questions require the subject not just an action and medium`() {
        listOf("Explain why ice floats on water.", "Why boats float on water?").forEach { question ->
            val subject = if ("ice" in question) "Ice" else "Boats"
            val relevant = Evidence("physics", "physics:0", "Buoyancy", "Reference",
                "$subject floats on water because its average density is lower than that of water.", -1.0)
            val unrelated = relevant.copy(documentId = "study", text =
                "The floating duration of tablets in water increased. The drug release mechanism was diffusion controlled.")
            val listing = relevant.copy(documentId = "wv-place-shop", text =
                "Destination: Ocean City\nCategory: Eat\nDescription: $subject, rootbeer floats and water ice are on the menu.")
            assertEquals(listOf(relevant), EvidenceRelevance.keep(listOf(unrelated, listing, relevant),
                FtsQuery.from(question)!!.terms, listOf("drug", "diffusion"), question))
        }
    }

    @Test fun `overview retains adjacent qualifications but not unrelated sections or provenance`() {
        val question = "How does a battery differ from a capacitor?"
        val anchor = Evidence("b", "b:0000", "Battery — Overview", "Reference A",
            "A battery stores chemical energy.", -1.0)
        val qualification = anchor.copy(chunkId = "b:0001", text = "Some designs cannot be recharged. The intended use depends on their chemistry.")
        val remote = qualification.copy(chunkId = "b:0003")
        val differentSource = qualification.copy(source = "Reference B")
        val differentSection = qualification.copy(title = "Battery — History")
        val differentDocument = qualification.copy(documentId = "other", chunkId = "other:0001")
        val candidates = listOf(qualification, remote, differentSource, differentSection, differentDocument, anchor)
        assertEquals(listOf(qualification, anchor), EvidenceRelevance.keep(candidates,
            FtsQuery.from(question)!!.terms, question = question))
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(qualification),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `overview adjacency does not chain or apply to arbitrary chunk identifiers`() {
        val question = "Explain battery"
        val anchor = Evidence("b", "b:0000", "Battery — Overview", "Reference",
            "A battery stores chemical energy.", -1.0)
        val next = anchor.copy(chunkId = "b:0001", text = "Some designs cannot be recharged. The choice of chemistry is important for longevity.")
        val later = next.copy(chunkId = "b:0002")
        val malformed = next.copy(chunkId = "unrelated:0001")
        assertEquals(listOf(anchor, next), EvidenceRelevance.keep(listOf(anchor, next, later, malformed),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `domain qualifier preserves the full definition and its limitations`() {
        val question = "How does a battery differ from a capacitor?"
        val direct = evidence("In electrical engineering, a capacitor stores energy in an electric field. This does not specify how long it can retain energy.", null)
        val incidental = evidence("In electrical engineering, we tested a battery and a capacitor in the laboratory.", null)
        val unrelated = evidence("In a capacitor experiment, a sensor is used to measure a battery.", null)
        assertEquals(listOf(direct), EvidenceRelevance.keep(listOf(incidental, unrelated, direct),
            FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `difference question preserves definitions of either subject without incidental subtypes`() {
        val battery = evidence("A battery stores chemical energy. Some batteries cannot be recharged.", null)
        val capacitor = evidence("A capacitor stores energy in an electric field.", null)
        val incidental = evidence("A battery-powered instrument measured a capacitor in a useful experiment.", null)
        listOf(
            "How does a battery differ from a capacitor, and when would each be useful?",
            "How is a battery different from a capacitor?",
            "How do batteries differ from capacitors?",
        ).forEach { question ->
            assertEquals(question, listOf(battery, capacitor), EvidenceRelevance.keep(
                listOf(incidental, battery, capacitor), FtsQuery.from(question)!!.terms, question = question))
        }
    }

    @Test fun `difference extraction does not weaken qualified or multiword comparisons`() {
        listOf(
            "How does solar energy differ from wind energy?",
            "How does a battery differ from a capacitor in this experiment?",
            "How does a battery differ from a capacitor, according to this study?",
        ).forEach { question -> assertEquals(question, emptyList<String>(), EvidenceRelevance.comparisonTerms(question)) }
    }

    @Test fun `compare questions may put an article before each subject`() {
        assertEquals(listOf("fungus", "bacterium"), EvidenceRelevance.comparisonTerms("Compare a fungus and a bacterium"))
        assertEquals(listOf("crocodile", "alligator"), EvidenceRelevance.comparisonTerms("Compare the crocodile with an alligator."))
        assertEquals(listOf("mitosis", "meiosis"), EvidenceRelevance.comparisonTerms("Compare mitosis and meiosis"))
        assertEquals(emptyList<String>(), EvidenceRelevance.comparisonTerms("Compare a solar panel and a wind turbine"))
    }

    @Test fun `named reference topic rejects another subject with generic overlap`() {
        val question = "Explain two ways agriculture changed human societies, and one disadvantage."
        val wrong = evidence("Writing changed human societies and the way people communicate.", null)
            .copy(title = "Writing — Influence on society")
        val right = evidence("Agriculture changed human societies through food production and settlement.", null)
            .copy(title = "Agriculture — History")
        assertEquals(listOf(right), EvidenceRelevance.keep(listOf(wrong, right), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `reference focus preserves multiple named topics and cross article evidence`() {
        val question = "How did agriculture and writing change human societies?"
        val farm = evidence("Agriculture changed human societies through food production.", null).copy(title = "Agriculture — History")
        val writing = evidence("Writing changed human societies through record keeping.", null).copy(title = "Writing — History")
        val cross = evidence("Agriculture and writing changed human societies through taxation records.", null).copy(title = "History — Records")
        assertEquals(listOf(farm, writing, cross), EvidenceRelevance.keep(listOf(farm, writing, cross), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `source only boilerplate cannot admit unrelated live information sources`() {
        val question = "Using only these saved sources, what earthquake happened today and which roads are currently closed?"
        val wrong = evidence("Only a few objects happened to become visible in the Solar System.", null)
        assertEquals(emptyList<Evidence>(), EvidenceRelevance.keep(listOf(wrong), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun `unnamed reference subject does not suppress descriptive questions`() {
        val question = "What changed human societies through food production?"
        val farm = evidence("Agriculture changed human societies through food production.", null).copy(title = "Agriculture — History")
        assertEquals(listOf(farm), EvidenceRelevance.keep(listOf(farm), FtsQuery.from(question)!!.terms, question = question))
    }
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
    @Test fun `attribute questions keep the article that states the fact`() {
        val question = "What is the capital of Canada?"
        assertTrue(EvidenceRelevance.isAttributeQuestion(question))
        assertEquals(emptyList<String>(), EvidenceRelevance.overviewSubjects(question))
        val ottawa = evidence("Ottawa is the capital city of Canada.", null)
        val unrelated = evidence("A capital letter begins each sentence.", null)
        assertEquals(listOf(ottawa), EvidenceRelevance.keep(listOf(unrelated, ottawa),
            FtsQuery.from(question)!!.terms, question = question))
        assertFalse(EvidenceRelevance.isAttributeQuestion("What is the history of Japan?"))
    }

    @Test fun `possessive subjects are also read in reference title order`() {
        assertEquals(listOf("Japan's history", "history of Japan"),
            EvidenceRelevance.overviewSubjects("Tell me about Japan's history"))
        val lead = evidence("The history of Japan begins in prehistoric times.", null)
            .copy(title = "History of Japan — Overview")
        assertEquals(listOf(lead), EvidenceRelevance.keep(listOf(lead),
            FtsQuery.from("Tell me about Japan's history")!!.terms, question = "Tell me about Japan's history"))
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

    @Test fun `two generic title matches cannot substitute for the subjects of an explanation`() {
        val clinic = evidence("Indoor room temperatures were higher than outdoor temperatures.", "keyword: room, temperature")
            .copy(title = "Indoor temperatures in patient waiting rooms")
        val mechanism = evidence("Metal feels colder than wood at the same room temperature because metal conducts heat away from skin faster.", "keyword: metal, wood, temperature")
            .copy(title = "Heat transfer")
        val question = "Why does metal feel colder than wood at the same room temperature?"
        assertEquals(listOf(mechanism), EvidenceRelevance.keep(
            listOf(clinic, mechanism), FtsQuery.from(question)!!.terms, question = question,
        ))
    }

    @Test fun `planner synonym can support a direct question term`() {
        val relevant = evidence("Car battery stores electrical energy", "keyword: battery, accumulator")
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

    @Test fun aGoodSemanticMatchThatSharesAQuestionWordIsKept() {
        fun concept(title: String, text: String, score: Double) = Evidence("d-$title", "d-$title:0", title, "local", text, -100000.0, matchedBy = "concept match $score")
        val question = "How does Shor's algorithm threaten RSA keys?"
        val terms = FtsQuery.from(question)!!.terms
        val rsa = concept("RSA cryptosystem — Integer factorization and the RSA problem",
            "The security of RSA relies on the practical difficulty of factoring the product of two large prime numbers.", 0.759)
        val shor = concept("Shor's algorithm — Feasibility and implications",
            "Shor's algorithm could factor large integers in polynomial time on a quantum computer.", 0.745)
        val unrelated = concept("Somatic gene signatures", "Circulating cell-free DNA was sequenced in the cohort.", 0.74)
        val weak = concept("RSA Conference", "RSA is mentioned here only in passing.", 0.66)
        val kept = EvidenceRelevance.keep(listOf(rsa, shor, unrelated, weak), terms, question = question)
        assertTrue(kept.toString(), rsa in kept && shor in kept)
        assertFalse("high similarity without any shared word stays out", unrelated in kept)
        assertFalse("below the semantic floor stays out", weak in kept)
    }

    @Test fun aDefinitionWithAModifierInsideTheSubjectCounts() {
        val question = "Explain BLS signatures and why Ethereum validators aggregate them"
        val bls = Evidence("w-bls", "w-bls:0", "BLS digital signature — Overview", "local",
            "A BLS digital signature, also known as Boneh–Lynn–Shacham (BLS), is a cryptographic signature scheme which allows a user to verify that a signer is authentic.", -19.8)
        val tls = Evidence("w-tls", "w-tls:0", "Transport Layer Security — Overview", "local",
            "Transport Layer Security is a cryptographic protocol that uses signatures in certificates.", -24.8)
        // Naming Ethereum too must not make its definition depend on mentioning Ethereum.
        val ethereum = Evidence("w-eth", "w-eth:0", "Ethereum — Overview", "local",
            "Ethereum is a decentralized blockchain with smart contract functionality.", -20.8)
        val kept = EvidenceRelevance.keep(listOf(tls, ethereum, bls), FtsQuery.from(question)!!.terms, question = question)
        assertTrue(kept.toString(), bls in kept)
        assertFalse(kept.toString(), tls in kept)
    }

    @Test fun anAcronymIsNotThePrefixOfAnotherWord() {
        val question = "Explain MEV and how searchers capture it"
        // A paper's header chunk: no abstract text, only "Mevalonate" in its title.
        val header = Evidence("pmid-39467704", "pmid-39467704:0", "Reconstitution of the Mevalonate Pathway for Improvement of Isoprenoid Production", "local",
            "PMID 39467704\nYear: 2024\nJournal: Journal of microbiology and biotechnology\nAuthors: Kang Min-Kyoung, Nguyen Minh Phuong", -18.3)
        assertTrue(EvidenceRelevance.keep(listOf(header), FtsQuery.from(question)!!.terms, question = question).isEmpty())
    }

    @Test fun aShortWordMatchesItsInflectionsButNotLongerWords() {
        fun passage(text: String) = Evidence("d-$text", "d-$text:0", "Article", "local", text, -1.0)
        val getting = passage("Getting the ferry to Belém takes ten minutes.")
        assertEquals(listOf(getting), EvidenceRelevance.keep(listOf(getting), listOf("get", "ferry")))
        val mevalonate = passage("The mevalonate pathway feeds the ferry of isoprenoids.")
        assertTrue(EvidenceRelevance.keep(listOf(mevalonate), listOf("mev", "pathway", "builder")).isEmpty())
    }

    @Test fun aPapersHeaderAloneIsNotADefinition() {
        val question = "Explain MEV and how searchers capture it"
        val header = Evidence("pmid-1", "pmid-1:0", "Age Modifies the Effect of 2-MeV Fast Neutrons on Rat Mammary Carcinogenesis.", "local",
            "PMID 1234567\nYear: 1999\nJournal: Radiation research\nAuthors: Smith J, Doe A", -15.0)
        assertTrue(EvidenceRelevance.keep(listOf(header), FtsQuery.from(question)!!.terms, question = question).isEmpty())
    }

    @Test fun aMapListingNeedsAPlaceQuestionOrItsTown() {
        fun place(town: String, name: String, text: String) = Evidence("wv-place-$name", "wv-place-$name:0", "$town — $name", "local",
            "Destination: $town\nCategory: See\nPlace: $name\nDescription: $text", -14.0)
        val museum = place("Vaals", "Museum de Kopermolen", "Regularly hosts concerts. Phone: +31 43 306 7702.")
        val question = "How does EIP-7702 change a regular account?"
        assertTrue(EvidenceRelevance.keep(listOf(museum), FtsQuery.from(question)!!.terms, question = question).isEmpty())
        // Naming the town is enough: "Kopermolen Vaals concerts" uses no place word.
        val town = "Kopermolen Vaals concerts"
        assertEquals(listOf(museum), EvidenceRelevance.keep(listOf(museum), FtsQuery.from(town)!!.terms, question = town))
    }

    private fun osm(id: String, destination: String, category: String, place: String, vararg lines: String) = Evidence(
        "osm-place-$id", "osm-place-$id:0000", "$destination — $place", "https://www.openstreetmap.org/node/$id",
        (listOf("Destination: $destination", "Category: $category", "Place: $place") + lines +
            "Map data snapshot: 2026-10-02 (© OpenStreetMap contributors)").joinToString("\n"), -20.0,
    )

    @Test fun aListingAnswersWithWhatItSaysNotItsLabelsOrItsTown() {
        val question = "need a 24 hour pharmacy in Bangkok around Sukhumvit"
        // "Hours in source" is not an hour and "24/7" not a 24-hour pharmacy; Bangkok is every listing's town.
        val kfc = osm("n1", "Bangkok", "Eat", "KFC", "Type: fast_food", "Hours in source: 24/7")
        val hostel = osm("n2", "Bangkok", "Sleep", "Home16 Hostel", "Type: hostel", "Hours in source: 24/7")
        val pharmacy = osm("n3", "Bangkok", "Health", "eXta Plus Pharmacy", "Type: pharmacy", "Address: Sukhumvit 4 Alley")
        assertEquals(listOf(pharmacy), EvidenceRelevance.keep(listOf(kfc, hostel, pharmacy), FtsQuery.from(question)!!.terms, question = question))
    }

    @Test fun aListingNamedAfterAPersonOrMonumentAnswersOnlyAPlaceLookupOrItsTown() {
        val station = osm("n4", "Dubai", "Transport", "Ibn Battuta", "Type: train station")
        val person = "Who was Ibn Battuta?"
        assertTrue(EvidenceRelevance.keep(listOf(station), FtsQuery.from(person)!!.terms, question = person).isEmpty())
        val restaurant = osm("n5", "Graz", "Eat", "Taj Mahal", "Type: restaurant", "Cuisine: indian", "Vegan: vegan options")
        val monument = "Who built the Taj Mahal and when?"
        assertTrue(EvidenceRelevance.keep(listOf(restaurant), FtsQuery.from(monument)!!.terms, question = monument).isEmpty())
        // Naming the town, or asking about the venue's hours, still reaches the listing.
        val metro = "Where is the Ibn Battuta metro station in Dubai?"
        assertEquals(listOf(station), EvidenceRelevance.keep(listOf(station), FtsQuery.from(metro)!!.terms, question = metro))
        val louvre = Evidence("wv-place-9", "wv-place-9:0000", "Paris — Louvre", "https://en.wikivoyage.org",
            "Destination: Paris\nCategory: See\nPlace: Louvre\nDescription: The world's largest art museum.\nHours in source: W-M 09:00-18:00", -20.0)
        val hours = "Is the Louvre open on Mondays?"
        assertEquals(listOf(louvre), EvidenceRelevance.keep(listOf(louvre), FtsQuery.from(hours)!!.terms, question = hours))
    }

    @Test fun anotherLanguagesPhrasebookIsNotEvidenceWithoutALanguageWord() {
        val phrasebook = Evidence("wvs-35557-0018", "wvs-35557-0018:0001", "Telugu phrasebook — Phrase list > Transportation > Directions",
            "https://en.wikivoyage.org/w/index.php?oldid=5320538", "- Turn right. : kudi vypu ki tirugu\n- left : left (yedama)\n- Watch for the _____. : -- kosam chudandi", -5.0)
        val wallet = Evidence("wp-55164087-0009", "wp-55164087-0009:0000", "Cryptocurrency wallet — Technology > Seed phrases", "https://en.wikipedia.org",
            "A seed phrase is a list of words which store all the information needed to recover a cryptocurrency wallet. Keep it offline, written on paper.", -20.0)
        val question = "What is a seed phrase and why should I keep it offline?"
        assertEquals(listOf(wallet), EvidenceRelevance.keep(listOf(phrasebook, wallet), FtsQuery.from(question)!!.terms, question = question))
        val thai = Evidence("wvs-1-0003", "wvs-1-0003:0000", "Thai phrasebook — Phrase list > Basics", "https://en.wikivoyage.org",
            "- Thank you. : khop khun (ขอบคุณ)\n- Please. : karuna", -5.0)
        val words = "Thai words for thank you"
        assertEquals(listOf(thai), EvidenceRelevance.keep(listOf(thai), FtsQuery.from(words)!!.terms, question = words))
    }

    @Test fun aBlogChunkOfPageFurnitureIsNotEvidenceForAnExplanation() {
        val prose = "Vitalik Buterin is the originator of Ethereum, but also a strong supporter of research aimed at bringing aging " +
            "under medical control. He made a 2.4 million dollar donation to the SENS Research Foundation."
        val furniture = Evidence("fightaging-interview", "fightaging-interview:0000", "An interview with Vitalik Buterin", "https://www.fightaging.org",
            "      Posted by Reason\n\n        Read 6 Comments      \n\n      Add a Comment\n\n$prose", -28.0)
        val article = furniture.copy(chunkId = "fightaging-interview:0001", text = prose)
        val question = "What did Vitalik Buterin donate to aging research?"
        assertEquals(listOf(article), EvidenceRelevance.keep(listOf(furniture, article), FtsQuery.from(question)!!.terms, question = question))
        // A source-only request reads what it was given.
        val summary = "Summarize what the attached notes say about Vitalik Buterin"
        assertEquals(listOf(furniture, article), EvidenceRelevance.keep(listOf(furniture, article), FtsQuery.from(summary)!!.terms, question = summary))
    }

    @Test fun aOneWordTitleMustBeTheQuestionsSubjectNotTheEndOfItsCompound() {
        val leather = Evidence("swp-8608-0000", "swp-8608-0000:0000", "Wallet — Overview", "https://simple.wikipedia.org",
            "A wallet is a small flat container, mostly of leather or fabric, that a person uses to hold cash, credit cards, identification cards, etc. " +
                "Most men usually keep their wallets in their pockets. A cryptocurrency wallet is a device, program, or service that stores public and/or private keys.", -14.0)
        val hardware = Evidence("wp-55164087-0005", "wp-55164087-0005:0000", "Cryptocurrency wallet — Types of Wallets > Cold storage > Hardware wallets", "https://en.wikipedia.org",
            "A hardware wallet is a small device that stores the user's private keys offline; it keeps the key safe because signing happens inside the device.", -18.0)
        val question = "How does a hardware wallet keep my private key safe?"
        assertEquals(listOf(hardware), EvidenceRelevance.keep(listOf(leather, hardware), FtsQuery.from(question)!!.terms, question = question))
        // The modifier in the passage makes the one-word title the subject: tides are ocean tides.
        val tide = Evidence("swp-tide", "swp-tide:0000", "Tide — Overview", "https://simple.wikipedia.org",
            "A tide is the periodic rising and falling of Earth's ocean surface caused mainly by the gravitational pull of the Moon acting on the oceans. Tides cause changes in the depth of marine waters.", -14.0)
        val tides = "How do ocean tides rise and fall each day?"
        assertEquals(listOf(tide), EvidenceRelevance.keep(listOf(tide), FtsQuery.from(tides)!!.terms, question = tides))
    }

    @Test fun aShortStemMatchesItsOwnFormsAndContractionFragmentsDoNotCount() {
        fun passage(text: String) = Evidence("d-$text", "d-$text:0", "Article", "local", text, -1.0)
        assertTrue(EvidenceRelevance.keep(listOf(passage("The earthquake hurt the town.")), listOf("ears", "hurt")).isEmpty())
        val ear = passage("Ear infections hurt.")
        assertEquals(listOf(ear), EvidenceRelevance.keep(listOf(ear), listOf("ears", "hurt")))
        // "we're" leaves "re" behind; it is not a second term the passage must hold.
        val lost = passage("Lost hikers should stay put.")
        assertEquals(listOf(lost), EvidenceRelevance.keep(listOf(lost), listOf("re", "lost")))
    }
}
