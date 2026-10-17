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

    @Test fun aQuestionNamingAPlaceReadsNoOtherDestinationsGuide() = runBlocking {
        val question = "Is it safe to drink tap water in Avelton?"
        val own = guide("Avelton — Stay healthy", "Tap water in Avelton is not safe to drink; buy bottled water.")
        val district = guide("Avelton/Centro — Stay safe", "Pickpockets work the old market.")
        val other = guide("Corvin — Stay safe > Tap water", "The Corvin water district says tap water is safe to drink.")
        val parent = guide("Velland — Stay healthy", "Bring water purification tablets for rural Velland.")
        val topic = guide("Water — Tap water", "Ask locals whether tap water is drinkable.")
        val destinations = setOf("Avelton", "Avelton/Centro", "Corvin", "Velland")
        val sectionsOf: suspend (String) -> List<Evidence> = { article ->
            if (article in destinations) listOf(guide("$article — Get in")) else emptyList()
        }
        val evidence = listOf(own, district, other, parent, topic)
        // Another destination goes; the asked place, its district, a topic guide and the app's own choice stay.
        assertEquals(listOf(own, district, topic), GuideScope.withoutUnaskedPlaces(question, evidence, emptyList(), sectionsOf))
        assertEquals(listOf(own, district, parent, topic), GuideScope.withoutUnaskedPlaces(question, evidence, listOf("Velland"), sectionsOf))
        // A region that speaks about the place, or a part named after it, stays.
        val region = guide("Northern Isles — Respect", "In Avelton and its neighbours, cover your shoulders in temples.")
        val part = guide("Avelton Proper — Do", "Lakes and saunas.")
        assertEquals(listOf(own, region, part), GuideScope.withoutUnaskedPlaces(question, listOf(own, region, part, other), emptyList()) { article ->
            listOf(guide("$article — Get in"))
        })
        // No guide of the named place among the evidence: nothing to prefer, nothing removed.
        assertEquals(listOf(other, topic), GuideScope.withoutUnaskedPlaces(question, listOf(other, topic), emptyList(), sectionsOf))
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

    @Test fun aPlaceTypedInLowercaseOrChosenByTheAppKeepsItsGuide() = runBlocking {
        val sections = { article: String -> listOf(guide("$article — Get in")) }
        // "hoi an what food should i try": two words in lowercase still name the place.
        val own = guide("Hoi Vel — Eat > Where > Budget", "Try the local noodles at the central market.")
        val other = guide("Corvin — Eat", "Corvin is known for its fish soup.")
        assertEquals(listOf(own), GuideScope.withoutOtherPlaces("hoi vel what food should i try", listOf(own, other)) { sections(it) })
        // The guide the app itself read for the question is the one it asked about.
        val cruise = guide("Avel Bay — See and do > Cruise", "Day boats leave the harbour at 8:00; overnight cruises sleep on board.")
        assertEquals(listOf(cruise), GuideScope.withoutOtherPlaces("best way to see it, day boat or overnight cruise?", listOf(cruise),
            chosen = listOf("Avel Bay")) { sections(it) })
        // One word counts only in a question typed all in lowercase: "mobile" is not the city of Mobile.
        assertTrue(GuideScope.namedLowercase("is avelton old town safe at night", "Avelton"))
        assertFalse(GuideScope.namedLowercase("How do I charge my mobile in Japan?", "Mobile"))
        assertFalse(GuideScope.namedLowercase("is avelton safe", "Ave"))
    }
}
