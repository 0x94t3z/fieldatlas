package xyz.fieldatlas.research

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideScopeTest {
    private fun guide(title: String, text: String = "Text.") =
        Evidence("wvs-${title.substringBefore(" — ")}", "wvs-$title:0", title, "https://en.wikivoyage.org/w/index.php?oldid=1", text, 0.0)

    @Test fun aCapitalizedNameIsAPlaceButAQuestionWordIsNot() {
        assertFalse(GuideScope.namesPlace("How does a hardware wallet keep my private key safe?"))
        assertFalse(GuideScope.namesPlace("How do hostels compare with guesthouses for a solo traveller on a budget?"))
        assertTrue(GuideScope.namesPlace("I lost my passport in Avelia. What should I do?"))
        assertTrue("a place may open the question", GuideScope.namesPlace("Avelton: how do I cross the street?"))
        assertFalse("an acronym names a thing, not a place", GuideScope.namesPlace("What is CRISPR?"))
        assertFalse(GuideScope.namesPlace("How does GPS work when I have no signal?"))
        assertTrue("the place beside the acronym still counts", GuideScope.namesPlace("How do I get a SIM card in Vietnam?"))
    }

    @Test fun aLowercaseCityThePlacePacksConfirmNamesAPlace() = runBlocking {
        val question = "good food markets in lyon? is anything open on sunday morning?"
        assertFalse(GuideScope.namesPlace(question))
        assertTrue(GuideScope.namesPlace(question, listOf("lyon")))
        assertTrue(GuideScope.namesPlace("How do I get a SIM card in Vietnam?", emptyList()))
        val listing = Evidence("wv-place-20606-0091", "wv-place-20606-0091:0", "Lyon — Halles de Lyon Paul Bocuse", "https://en.wikivoyage.org/w/index.php?oldid=2",
            "Destination: Lyon\nCategory: Buy\nPlace: Halles de Lyon Paul Bocuse", 0.0)
        assertEquals(listOf(listing), GuideScope.withoutOtherPlaces(question, listOf(listing), listOf("lyon")) { emptyList() })
        assertEquals(emptyList<Evidence>(), GuideScope.withoutOtherPlaces(question, listOf(listing)) { emptyList() })
    }

    @Test fun aQuestionThatNamesNoPlaceReadsNoDestinationsGuideOrListings() = runBlocking {
        val destination = guide("Avelton — Buy > Debit cards", "In Avelton foreign cards are refused; bring cash.")
        val topic = guide("Money — Cards", "A credit card gives more protection against fraud than a debit card.")
        val listing = Evidence("wv-place-1-0001", "wv-place-1-0001:0", "Avelton — Card shop", "https://en.wikivoyage.org/w/index.php?oldid=2",
            "Destination: Avelton\nCategory: Buy\nPlace: Card shop", 0.0)
        val article = Evidence("wp-debit", "wp-debit:0", "Debit card — Overview", "https://simple.wikipedia.org/w/index.php?oldid=3",
            "A debit card takes money straight from a bank account.", 0.0)
        val read = ArrayList<String>()
        // What a title search for the destination headings returns: "Avelton Hills" is another guide.
        val sections = mapOf("Avelton" to listOf(guide("Avelton — Get in")), "Money" to listOf(guide("Money Island — Get in")))
        val kept = GuideScope.withoutOtherPlaces("Should I use a debit card or a credit card when travelling abroad?",
            listOf(destination, topic, listing, article)) { name -> read += name; sections[name].orEmpty() }
        assertEquals(listOf("Money — Cards", "Debit card — Overview"), kept.map { it.title })
        assertEquals(listOf("Avelton", "Money"), read)
        val named = listOf(destination, listing)
        assertEquals("a question naming the place keeps its guide and listings", named,
            GuideScope.withoutOtherPlaces("Can I pay by card in Avelton?", named) { sections[it].orEmpty() })
    }
}
