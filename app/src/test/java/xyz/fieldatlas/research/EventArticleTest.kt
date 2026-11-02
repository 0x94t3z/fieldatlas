package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventArticleTest {
    private fun lead(id: String, title: String, text: String) = Evidence(id, "$id:0000", "$title — Overview", "Saved collection", text, 1.0)

    @Test fun aYearAndAKindOfEventAskForThatEventsArticle() {
        assertEquals(EventArticle.Ask("2016", "election", listOf("trump")), EventArticle.of("how many electoral votes did Trump have in 2016?"))
        assertEquals("Olympics", EventArticle.of("how many gold medals did team gb win in 2012")?.kind)
        assertEquals("World Cup", EventArticle.of("who scored in the 1966 world cup final")?.kind)
        assertEquals(listOf("conservatives"), EventArticle.of("how many seats did the conservatives win in 1983")?.terms)
    }

    @Test fun noYearTwoYearsNoEventOrNothingElseAskedIsNoLookup() {
        assertNull(EventArticle.of("how many electoral votes are there"))
        assertNull(EventArticle.of("did more people vote in 2016 or 2020"))
        assertNull(EventArticle.of("when was the eiffel tower built in 1889"))
        assertNull(EventArticle.of("who won the 2016 election"))
        // Only the event's own name besides the year: any league's final that year would do, so none is looked up.
        assertNull(EventArticle.of("who won the 1999 champions league final"))
        assertEquals(listOf("rugby"), EventArticle.of("who won the 2003 rugby world cup")?.terms)
    }

    @Test fun theShortestLeadOfThatYearsEventNamingTheAskedWordsIsRead() {
        val ask = EventArticle.of("how many electoral votes did Trump have in 2016")!!
        val national = lead("swp-1", "2016 United States presidential election", "Trump and Pence defeated Clinton. Trump received 304 electoral votes.")
        val state = lead("swp-2", "2016 United States presidential election in Utah", "Trump won Utah. Trump got 6 votes. Trump, Trump.")
        val other = lead("swp-3", "2016 Philippine presidential election", "Duterte won.")
        val wrongYear = lead("swp-4", "2020 United States presidential election", "Trump lost.")
        val list = lead("swp-5", "2016 Alabama elections", "Trump carried Alabama.")
        val jamaica = lead("swp-6", "2016 Jamaican general election", "Held the year Trump was elected.")
        assertEquals(national, EventArticle.pick(listOf(list, jamaica, state, other, wrongYear, national), ask))
        assertEquals(state, EventArticle.pick(listOf(state, other), ask))
        assertNull(EventArticle.pick(listOf(other, wrongYear, list), ask))
        assertEquals(true, EventArticle.covers(national, ask))
    }
}
