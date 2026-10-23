package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRouterTest {
    private fun ev(id: String, title: String) = Evidence(id, "$id-0000", title, "Saved collection", "$title is a thing.", 1.0)
    private fun src(id: String, title: String) = PromptSource("S1", ev(id, title), "$title is a thing.")
    private val R = SourceRouter.READ_NOTES

    @Test fun aSubjectTheSimpleEncyclopediaHoldsIsAnsweredFromMemory() {
        val both = listOf(ev("swp-5246", "Eiffel Tower — Overview"), ev("enw-9232", "Eiffel Tower — Overview"))
        assertEquals(0, SourceRouter.notes("when was the eiffel tower built", both, listOf(src("enw-9232", "Eiffel Tower — Overview")), mapOf("Eiffel Tower" to 60)))
    }

    @Test fun aFactLookupIsReadByAModelThatAnswersStraightOut() {
        val opera = listOf(ev("swp-77", "Sydney Opera House — Overview"))
        val notes = listOf(src("swp-77", "Sydney Opera House — Overview"))
        val q = "Who designed the Sydney Opera House, and when was it opened?"
        assertEquals(R, SourceRouter.notes(q, opera, notes, mentions = mapOf("Sydney Opera House" to 1000), factLookups = true))
        // A model that reasons first keeps to its memory, as before.
        assertEquals(0, SourceRouter.notes(q, opera, notes, mentions = mapOf("Sydney Opera House" to 1000)))
        assertEquals(R, SourceRouter.notes("What is the deepest lake in the world and how deep is it?", emptyList(),
            listOf(src("swp-9", "Lake Baikal — Overview")), factLookups = true))
        // An explanation is answered from memory; a travel guide leading the passages is not the encyclopedia.
        assertEquals(0, SourceRouter.notes("How do vaccines train the immune system?", listOf(ev("swp-1", "Vaccine — Overview")),
            listOf(src("swp-1", "Vaccine — Overview")), mentions = mapOf("Vaccine" to 1000), factLookups = true))
        assertEquals(0, SourceRouter.notes("How much should I tip in restaurants in Germany?", listOf(ev("swp-4", "Germany — Overview")),
            listOf(src("swp-4", "Germany — Overview")), mentions = mapOf("Germany" to 1000), factLookups = true))
        assertEquals(R, SourceRouter.notes("whats the capital of mongolia and about how many people live there", emptyList(),
            listOf(src("swp-8", "Ulaanbaatar — Overview")), factLookups = true))
        assertEquals(0, SourceRouter.notes("how much rice and water do i need for 4 people in a pot", emptyList(),
            listOf(src("swp-5", "Rice — Overview")), factLookups = true))
        assertEquals(0, SourceRouter.notes("how much does that cost?", emptyList(), listOf(src("swp-6", "Cost — Overview")), factLookups = true))
        assertEquals(0, SourceRouter.notes("Which year did Bolivia open the Desaguadero crossing?", emptyList(),
            listOf(src("wv-3", "Bolivia — Get in > By land")), factLookups = true))
    }

    @Test fun aLittleKnownSubjectOfTheFullEncyclopediaOrASpecialistCollectionIsRead() {
        val battle = listOf(ev("enw-77", "Battle of Ramillies — Overview"))
        assertEquals(R, SourceRouter.notes("who won the battle of ramillies and when", battle, listOf(src("enw-77", "Battle of Ramillies — Overview")), mentions = mapOf("Battle of Ramillies" to 31)))
        // No count known: a short article decides; nothing known at all: memory, as readership unknown is taken as wide.
        assertEquals(R, SourceRouter.notes("who won the battle of ramillies and when", battle, listOf(src("enw-77", "Battle of Ramillies — Overview")), mapOf("Battle of Ramillies" to 9)))
        assertEquals(0, SourceRouter.notes("who won the battle of ramillies and when", battle, listOf(src("enw-77", "Battle of Ramillies — Overview"))))
        assertEquals(R, SourceRouter.notes("what does the klotho gene do", listOf(ev("hagr-12", "Klotho — Overview")), listOf(src("hagr-12", "Klotho — Overview"))))
    }

    @Test fun aRareSubjectIsReadEvenBesideACommonOneTheQuestionAlsoNames() {
        val both = listOf(ev("swp-2", "Surgery — Overview"), ev("enw-1913197", "Hanaoka Seishū — Overview"))
        assertEquals(R, SourceRouter.notes("who was hanaoka seishu, what surgery did he perform in 1804", both,
            listOf(src("enw-1913197", "Hanaoka Seishū — Overview")), mentions = mapOf("Surgery" to 1000, "Hanaoka Seishū" to 5)))
        // A common subject beside a rare name the pack lacks stays with memory.
        assertEquals(0, SourceRouter.notes("who was ferdinand carré, what gas did his machine use", listOf(ev("swp-3283", "Gas — Overview")),
            listOf(src("swp-3283", "Gas — Overview")), mentions = mapOf("Gas" to 1000)))
    }

    @Test fun aFamousSubjectTheSimpleEditionHappensToLackStaysWithMemoryByItsLongArticle() {
        val iron = listOf(ev("enw-40", "Cast iron — Overview"))
        assertEquals(0, SourceRouter.notes("cast iron vs non-stick pan which is better", iron, listOf(src("enw-40", "Cast iron — Overview")), mentions = mapOf("Cast iron" to 1000)))
        assertEquals(0, SourceRouter.notes("cast iron vs non-stick pan which is better", iron, listOf(src("enw-40", "Cast iron — Overview")), mapOf("Cast iron" to 80)))
    }

    @Test fun aSubjectTheQuestionDoesNotNameIsNoReasonToRead() {
        val goods = listOf(ev("enw-5", "Goods — Overview"))
        assertEquals(0, SourceRouter.notes("why are honeybees dying off, is it pesticides or mites", goods, listOf(src("enw-5", "Goods — Overview")), mapOf("Goods" to 8)))
    }

    @Test fun aCommonWordsArticleIsNoRareSubjectHoweverShortButARareOneWordSubjectIs() {
        assertEquals(0, SourceRouter.notes("what is the speed of light in a vacuum", listOf(ev("enw-28748", "Speed — Overview")), listOf(src("enw-28748", "Speed — Overview")), mapOf("Speed" to 6), mapOf("Speed" to 1000)))
        assertEquals(0, SourceRouter.notes("why is the sky blue", listOf(ev("enw-33022", "Sky blue — Overview")), listOf(src("enw-33022", "Sky blue — Overview")), mentions = mapOf("Sky blue" to 640)))
        assertEquals(R, SourceRouter.notes("what causes alkaptonuria and what enzyme is missing", listOf(ev("wp-148249", "Alkaptonuria — Overview")), listOf(src("wp-148249", "Alkaptonuria — Overview")), mentions = mapOf("Alkaptonuria" to 12)))
        assertEquals(0, SourceRouter.notes("what was the aroostook war between whom", listOf(ev("swp-944", "Year — Overview")), listOf(src("swp-944", "Year — Overview"))))
    }

    @Test fun aRareSubjectTheSearchLedWithIsReadWhenNoSubjectWasLookedUp() {
        val found = listOf(src("wp-695860", "Pantelegraph — Overview"), src("wp-695860", "Pantelegraph — History"))
        assertEquals(R, SourceRouter.notes("what was the pantelegraph, who built it", emptyList(), found, mentions = mapOf("Pantelegraph" to 4)))
        // The Simple edition has it too, or the article is long: a famous subject the search happened to lead with.
        val famous = listOf(src("enw-9232", "Eiffel Tower — Overview"), src("swp-5246", "Eiffel Tower — Overview"))
        assertEquals(0, SourceRouter.notes("when was the eiffel tower built", emptyList(), famous))
        assertEquals(0, SourceRouter.notes("what was the pantelegraph", emptyList(), found, mentions = mapOf("Pantelegraph" to 900)))
        // An unnamed lead is another subject.
        assertEquals(0, SourceRouter.notes("what was the pantelegraph", emptyList(), listOf(src("wp-1", "Telegraph — Overview"))))
    }

    @Test fun withNoSubjectTheLeadingPassagesDecideAndOnlyASpecialistLeadReads() {
        val specialist = listOf(src("pmc-1", "Autophagy in ageing"), src("pmc-2", "Autophagy and lifespan"), src("wp-3", "Ageing — Overview"))
        assertEquals(R, SourceRouter.notes("does autophagy slow ageing", emptyList(), specialist))
        val general = listOf(src("wp-3", "Ageing — Overview"), src("pmc-1", "Autophagy in ageing"), src("pmc-2", "Autophagy and lifespan"))
        assertEquals(0, SourceRouter.notes("does autophagy slow ageing", emptyList(), general))
    }

    @Test fun aCollectionTheUserImportedIsReadLikeASpecialistOne() {
        // A handbook someone built with the pack tools: its ids match none of the collections Field Atlas offers.
        val handbook = listOf(src("acme-12", "Leave policy"), src("acme-13", "Parental leave"), src("wp-3", "Leave — Overview"))
        assertEquals(R, SourceRouter.notes("how many days of parental leave do we get", emptyList(), handbook, readImported = true))
        assertEquals(R, SourceRouter.notes("what does the leave policy say", listOf(ev("acme-12", "Leave policy")), handbook, readImported = true))
        // Off by default, as before: the app turns it on where it builds the orchestrator.
        assertEquals(0, SourceRouter.notes("how many days of parental leave do we get", emptyList(), handbook))
    }

    @Test fun theAppsOwnPassagesAndItsCollectionsAreNeverTakenForImportedOnes() {
        val guide = listOf(src("emergency:burns", "Burns"), src("emergency:burns", "Burns"), src("attachment:1", "notes.pdf"))
        assertEquals(0, SourceRouter.notes("how do i treat a burn", emptyList(), guide, readImported = true))
        val travel = listOf(src("wvs-athens", "Athens — Get in"), src("wv-athens", "Athens — Sleep"), src("osm-place-n1", "Athens — Pharmacy"))
        assertEquals(0, SourceRouter.notes("how do i get into athens", emptyList(), travel, readImported = true))
        val simple = listOf(src("swp-1", "Boat — Overview"), src("swp-2", "Boat — Uses"))
        assertEquals(0, SourceRouter.notes("why do boats float", emptyList(), simple, readImported = true))
    }

    @Test fun travelGuidesUnknownCollectionsAndNoPassagesStayWithMemory() {
        assertEquals(0, SourceRouter.notes("how do i get into athens from the airport", listOf(ev("wv-athens", "Athens — Get in")), listOf(src("wv-athens", "Athens — Get in"))))
        assertEquals(0, SourceRouter.notes("why do boats float", listOf(ev("boat", "Boat — Overview")), listOf(src("boat", "Boat — Overview"))))
        assertEquals(0, SourceRouter.notes("who won the battle of ramillies", listOf(ev("enw-77", "Battle of Ramillies — Overview")), emptyList()))
    }
}
