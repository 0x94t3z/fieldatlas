package xyz.fieldatlas.ui.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.Evidence

/** While an answer is researched, the best passage found so far is there to read, from its first whole sentence. */
class BestMatchSoFarTest {
    private fun passage(text: String) = Evidence("doc", "doc:0", "Eiffel Tower — Overview", "https://simple.wikipedia.org/w/x", text, 1.0)

    @Test fun aPassageCutMidSentenceStartsAtItsFirstWholeSentence() {
        // Saved passages on 1 Nov: "hem ascending and being disdainful…", " changes lasting only a few years…".
        assertEquals("As such, there was significant social tension.",
            passageExcerpt("hem ascending and being disdainful when they did ascend. As such, there was significant social tension."))
        assertEquals("Climate is a measure of the long-term trends in the weather.",
            passageExcerpt(" changes lasting only a few years can have a stress effect.\nClimate is a measure of the long-term trends in the weather."))
        val whole = "The Eiffel Tower is a wrought-iron landmark in Paris. It was built between 1887 and 1889."
        assertEquals(whole, passageExcerpt(whole))
        assertEquals("\"Eiffel\" is the name.", passageExcerpt("\"Eiffel\" is the name."))
    }

    @Test fun aLongPassageEndsAtASentenceOrAWord() {
        val sentences = "The Eiffel Tower is a wrought-iron landmark in Paris. " + "It was built for the World Fair of 1889 and was meant to stand for twenty years. ".repeat(6)
        val excerpt = passageExcerpt(sentences)
        assertTrue(excerpt, excerpt.length <= 280 && excerpt.endsWith("."))
        val oneLongSentence = "The tower " + "is very tall and made of iron ".repeat(20)
        val cut = passageExcerpt(oneLongSentence)
        assertTrue(cut, cut.length <= 281 && cut.endsWith("…") && !cut.endsWith(" …"))
    }

    @Test fun theCardShowsUntilTheAnswersOwnWordsAppear() {
        val match = passage("The Eiffel Tower is a wrought-iron landmark in Paris.")
        val searching = ResearchUiState(phase = ResearchPhase.Searching, preview = match)
        assertEquals(match, bestMatchSoFar(searching))
        assertEquals(match, bestMatchSoFar(searching.copy(phase = ResearchPhase.Generating, sources = listOf(match))))
        assertNull(bestMatchSoFar(searching.copy(answer = "The tower was built in 1889.")))
        // The app's call line above an answer still to come does not take the card's place; the answer's first words do.
        val call = xyz.fieldatlas.emergency.GuideAnswerCheck.callLine("my car broke down on the highway at night", guideFound = false)
        assertEquals(match, bestMatchSoFar(searching.copy(answer = call)))
        assertEquals(match, bestMatchSoFar(searching.copy(answer = call.trim())))
        assertNull(bestMatchSoFar(searching.copy(answer = call + "Stay in the car")))
        assertNull(bestMatchSoFar(searching.copy(waitingFor = "an earlier question")))
        assertNull(bestMatchSoFar(ResearchUiState(phase = ResearchPhase.Searching)))
    }

    @Test fun aCardPickedForTheQuestionStaysWhenTheSourcesArrive() {
        val drink = Evidence("wv-hanoi", "wv-hanoi:12", "Hanoi — Drink", "https://en.wikivoyage.org/wiki/Hanoi", "Bia hơi is abundant.", 0.73)
        val general = Evidence("swp-drink", "swp-drink:0000", "Drink — Overview", "https://simple.wikipedia.org/wiki/Drink", "A drink is a liquid.", 1.0)
        val searching = ResearchUiState(phase = ResearchPhase.Searching, preview = drink)
        assertEquals(drink, previewAfterSources(searching.copy(previewChosen = true), listOf(general, drink)))
        // A first search hit is replaced by the passage the run settled on.
        assertEquals(general, previewAfterSources(searching, listOf(general, drink)))
        assertEquals(drink, previewAfterSources(searching, emptyList()))
        assertNull(previewAfterSources(ResearchUiState(phase = ResearchPhase.Searching), listOf(general)))
    }
}
