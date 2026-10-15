package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Word problems from a fresh-question run that research answered with other passages' numbers. */
class WordProblemTest {
    private val hotel = "Our hotel is €180 a night for 4 nights. We get 15% off the room rate, then 12% tax is added to the discounted price, " +
        "plus a city tax of €3.50 per person per night for the two of us. What's the total?"
    private val train = "train leaves at 8:15, the trip is 420 km and it averages 140 km/h while moving, plus two 10-minute stops. what time do we get in?"
    private val naismith = "We're planning an 18 km hike with 900 m of total climbing. Using Naismith's rule (1 hour per 5 km plus 1 hour per 600 m of ascent), " +
        "how long should it take? If we start at 7:30 and stop 45 minutes for lunch, when do we finish?"

    @Test fun aQuestionThatCarriesAllItsNumbersIsSelfContained() {
        assertTrue(WordProblem.isSelfContained(hotel))
        assertTrue(WordProblem.isSelfContained(train))
        assertTrue("a named rule whose figures are given is not a subject to look up", WordProblem.isSelfContained(naismith))
        assertTrue(WordProblem.isSelfContained("cooking breakfast for 14 people for 3 mornings. everyone has 2 eggs each morning and eggs come in boxes of 12. how many boxes?"))
    }

    @Test fun theNumbersAreListedAsWrittenWithClockTimesCounted() {
        assertEquals(listOf("180", "4", "15", "12", "3.50"), WordProblem.of(hotel).numbers)
        assertEquals(listOf("8:15", "420", "140", "10"), WordProblem.of(train).numbers)
        assertEquals(listOf("1,200", "3"), WordProblem.of("A $1,200 bill split 3 ways").numbers)
    }

    @Test fun aPlaceOrASubjectSendsTheQuestionToResearch() {
        assertFalse(WordProblem.isSelfContained("How much water do I need for 3 days on the 43 km Inca Trail if I drink 3 litres a day?"))
        assertFalse(WordProblem.isSelfContained("My flight leaves Sydney at 10:00 on Tuesday and takes 12 h 30 min to get to Santiago. " +
            "Sydney is on UTC+11 and Santiago on UTC-3 right now. What day and local time do I land?"))
        assertFalse(WordProblem.isSelfContained("Which vaccinations do I need for 3 weeks in Kenya with 2 kids under 5?"))
        assertFalse("an explanation reads its article", WordProblem.isSelfContained("How does a 12 V 100 Ah battery store 1,200 Wh?"))
    }

    @Test fun aJudgementOrTooFewNumbersIsNotArithmetic() {
        assertFalse(WordProblem.isSelfContained("Is 40°C dangerous for a 12 km walk with 2 litres of water?"))
        assertFalse(WordProblem.isSelfContained("Should I take the 8:15 train or the 9:40 bus for a 420 km trip?"))
        assertFalse("which is better is a judgement", WordProblem.isSelfContained("3 nights at €180 or 4 nights at €150, which is better value?"))
        assertFalse("two numbers may be a conversion the model should not attempt", WordProblem.isSelfContained("Convert 10 kg to pounds"))
        assertFalse(WordProblem.isSelfContained("What time is it?"))
        assertFalse(WordProblem.of("How long should a 2 km walk take?").selfContained)
    }

    @Test fun figuresGivenOutweighAPlaceName() {
        listOf(
            "A tour costs 2,400 baht in Chiang Mai or 75 dollars. 1 dollar is 34 baht and my card charges 2% on baht. Which option is cheaper and by how much?",
            "I have 1,500 for 10 days. Hostels take 40% and transport 25%. What's my daily budget?",
            "The bus leaves Lima at 22:30 on day 1 and arrives in Cusco at 18:10 on day 2, with a 45 minute stop. How long is the ride?",
        ).forEach { assertTrue(it, WordProblem.isSelfContained(it)) }
        listOf(
            "How many people live in Tokyo, and how much did it grow from 1950 to 2020?",
            "Is it safe to hike 20 km at 35 degrees with 2 litres of water each in Arizona?",
        ).forEach { assertFalse(it, WordProblem.isSelfContained(it)) }
    }
}
