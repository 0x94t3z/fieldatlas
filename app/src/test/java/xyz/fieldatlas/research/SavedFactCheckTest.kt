package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A passage about the asked subject that gives a different figure is quoted under a from-memory answer; nothing else is. */
class SavedFactCheckTest {
    private fun source(id: String, title: String, text: String) =
        PromptSource(id, Evidence("library:$title", title, title, "Saved collection", text, 1.0), text)
    private fun plain(s: String?) = s?.replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }

    private val burj = source("S1", "Burj Khalifa", "The Burj Khalifa is a skyscraper in Dubai, United Arab Emirates. " +
        "With a total height of 829.8 m (2,722 ft) and a roof height of 828 m (2,717 ft), the Burj Khalifa has been the tallest structure in the world since 2009. " +
        "The building has 163 floors above ground. It opened on 4 January 2010.")
    private val question = "how tall is the burj khalifa and how many floors does it have"

    @Test fun aWrongHeightIsAnsweredWithThePassagesSentence() {
        val note = plain(SavedFactCheck.note(question, "The Burj Khalifa stands 428 m (1,404 ft) tall and has 163 floors.", listOf(burj)))
        assertNotNull(note)
        assertTrue(note!!, note.startsWith(SavedFactCheck.HEADING))
        assertTrue(note, "829.8 m (2,722 ft)" in note && "[S1]" in note && note.endsWith("_Check which is right._"))
    }

    @Test fun theRightFigureRoundedOrInAnotherUnitIsNoConflict() {
        // 828 against 829.8 is within rounding; feet against metres are never compared; 163 floors agrees.
        assertNull(SavedFactCheck.note(question, "It is 828 metres tall, about 2,717 feet, with 163 floors.", listOf(burj)))
        assertNull(SavedFactCheck.note(question, "It is 830 m tall with 162 storeys.", listOf(burj)))
        // "About" widens the allowance; a sentence starting "It" takes its subject from the one before.
        assertNull(SavedFactCheck.note(question, "The Burj Khalifa is in Dubai. It is about 800 m tall.", listOf(burj)))
        assertNotNull(SavedFactCheck.note(question, "The Burj Khalifa is in Dubai. It is about 600 m tall.", listOf(burj)))
        // Alone, "it" is tied to nothing asked.
        assertNull(SavedFactCheck.note(question, "It is about 600 m tall.", listOf(burj)))
    }

    @Test fun aRangeOnEitherSideCountsAsAgreement() {
        val falcon = source("S2", "Peregrine falcon", "The peregrine falcon reaches over 320 km/h (200 mph) during its hunting stoop, with speeds of 320–390 km/h recorded.")
        val q = "how fast is a peregrine falcon when it dives"
        assertNull(SavedFactCheck.note(q, "The peregrine falcon dives at close to 390 km/h.", listOf(falcon)))
        assertNull(SavedFactCheck.note(q, "The falcon reaches 320 to 400 km/h in a dive.", listOf(falcon)))
        assertNotNull(SavedFactCheck.note(q, "The peregrine falcon dives at about 500 km/h.", listOf(falcon)))
    }

    @Test fun aPassageAboutSomethingElseIsNotCompared() {
        // The same unit, a different mountain: the passage sentence lacks the question's words.
        val everest = source("S1", "Mountain", "Mount Everest is 8,849 m (29,032 ft) tall, the highest mountain on Earth. Kilimanjaro is a dormant volcano in Tanzania.")
        assertNull(SavedFactCheck.note("how tall is kilimanjaro", "Kilimanjaro is 5,895 m tall.", listOf(everest)))
        // One answer sentence off the subject is not checked either.
        val kili = source("S1", "Mount Kilimanjaro", "Mount Kilimanjaro is a dormant volcano in Tanzania; its summit, Uhuru Peak, is 5,895 m (19,341 ft) above sea level.")
        assertNull(SavedFactCheck.note("how tall is kilimanjaro", "Kilimanjaro is 5,895 m tall. Everest, by comparison, is 8,000 m.", listOf(kili)))
    }

    @Test fun yearsAreComparedOnlyForTheSameEvent() {
        val chernobyl = source("S1", "Chernobyl disaster", "The Chernobyl disaster began on 26 April 1986 with the explosion of reactor No. 4 of the Chernobyl Nuclear Power Plant near Pripyat. " +
            "The plant's construction started in 1972. It lies in what is today Ukraine.")
        val q = "when did the chernobyl disaster happen and what country is it in today"
        val wrong = plain(SavedFactCheck.note(q, "The Chernobyl disaster happened in 1984 in what is today Ukraine.", listOf(chernobyl)))
        assertNotNull(wrong); assertTrue(wrong!!, "26 April 1986" in wrong)
        assertNull(SavedFactCheck.note(q, "The Chernobyl disaster happened in April 1986, in today's Ukraine.", listOf(chernobyl)))
        // A different event's year is not the disaster's: construction in 1972 is no conflict with 1986.
        assertNull(SavedFactCheck.note(q, "The disaster happened in 1986; the plant had been built in the 1970s.", listOf(chernobyl)))
        // Two years in one answer sentence: too ambiguous to check.
        assertNull(SavedFactCheck.note(q, "Between 1984 and 1986 the plant had several incidents.", listOf(chernobyl)))
    }

    @Test fun comparisonsFewTermsAndEmptySourcesGetNothing() {
        assertNull(SavedFactCheck.note("how do sweet potatoes differ from yams", "Yams grow to 1.5 m long.", listOf(burj)))
        assertNull(SavedFactCheck.note("height?", "It is 428 m tall.", listOf(burj)))
        assertNull(SavedFactCheck.note(question, "It is 428 m tall.", emptyList()))
        assertNull(SavedFactCheck.note(question, "", listOf(burj)))
    }

    @Test fun atMostTwoNotesEachQuotedOnce() {
        val two = listOf(burj, source("S2", "List of tallest buildings", "The Burj Khalifa, at 829.8 m, has been the tallest building since 2010. " +
            "The Burj Khalifa has 163 floors. The Burj Khalifa was topped out in 2009."))
        val note = plain(SavedFactCheck.note(question, "The Burj Khalifa is 428 m tall with 110 floors.", two))!!
        assertEquals(note, 2, Regex("\\[S\\d\\]").findAll(note).count())
    }

    @Test fun theNoteSitsAboveTheSavedExcerpts() {
        val shown = "The answer.\n\n## Saved excerpts\n\n> quote [S1]\n\n_These citations…_"
        val placed = SavedFactCheck.insert(shown, "NOTE")
        assertEquals("The answer.\n\nNOTE\n\n## Saved excerpts\n\n> quote [S1]\n\n_These citations…_", placed)
        assertEquals("The answer.\n\nNOTE", SavedFactCheck.insert("The answer.\n", "NOTE"))
    }

    // ---- False alarms seen on set 37's recorded answers, each kept out. ----

    @Test fun aYearBelongsToTheEventNearestIt() {
        val louverture = source("S3", "Toussaint Louverture", "In 1802, Toussaint Louverture was arrested in Saint-Domingue and imprisoned in France, where he died the following year.")
        val q = "who was toussaint louverture and how did he die"
        // "Died ... 1803" against a sentence whose 1802 is the arrest: no conflict.
        assertNull(SavedFactCheck.note(q, "He died on April 8, 1803, in a prison cell in France.", listOf(louverture)))
        val plain = source("S3", "Toussaint Louverture", "Toussaint Louverture died in the Fort de Joux in France in 1803.")
        assertNotNull(SavedFactCheck.note(q, "He died in 1805 in France.", listOf(plain)))
        assertEquals(SavedFactCheck.eventOf("In 1802 he was arrested; he died the following year.", 1802), SavedFactCheck.eventOf("They arrested him in 1802.", 1802))
    }

    @Test fun aListOfFiguresStatesNone() {
        val cicada = source("S1", "Periodical cicadas", "A periodical cicada is any of the seven species of the genus Magicicada of eastern North America, the 13- and 17-year cicadas.")
        val q = "how do periodical cicadas know when 17 years are up? they're underground the whole time"
        assertNull(SavedFactCheck.note(q, "By staying underground for a prime number of years, periodical cicadas avoid predators that follow 2, 3, 5, or 7-year population booms.", listOf(cicada)))
    }

    @Test fun aWorkedSumIsNotChecked() {
        val outage = source("S1", "Northeast blackout", "The outage lasted a total of about 17 hours in most areas.")
        val q = "The vet gave us tablets for our dog: one every 8 hours, 21 in the pack. We gave the first one at 7:30 pm on Tuesday. When is the last one?"
        assertNull(SavedFactCheck.note(q, "Total hours: 20 intervals × 8 hours = 160 hours. 160 hours divided by 24 hours per day equals 6 days and 16 hours. So Tuesday 11:30 am.", listOf(outage)))
    }

    @Test fun unitsAndEverydayWordsDoNotMakeAPassageAboutTheSubject() {
        val marathon = source("S2", "Marathon", "The Marathon is a classic long-distance foot race; the standard distance is 42.195 km (26 miles, 385 yards) though there is some variation between courses.")
        val q = "ran a 10k in 52:30 today. what's my pace per km and per mile, and if i held the same pace what would my half marathon time be"
        assertNull(SavedFactCheck.note(q, "Your 5:15/km pace over the half marathon distance of 21.0975 km gives 1 hour 50 minutes.", listOf(marathon)))
    }

    @Test fun sharesAndCountsAreComparedOnlyWhenAsked() {
        val containers = source("S2", "Intermodal container", "Containers have largely supplanted the traditional break bulk cargo; in 2010, containers accounted for 60% of the world's seaborne trade.")
        assertNull(SavedFactCheck.note("how did the shipping container change world trade and shipping costs",
            "Shipping containers cut costs by filling vessels to over 90% capacity.", listOf(containers)))
        assertNotNull(SavedFactCheck.note("what share of world seaborne trade do shipping containers carry",
            "Shipping containers carry about 20% of the world's seaborne trade.", listOf(containers)))
        // The passage must share two of the question's subject words ("live", "tokyo"), as every passage must.
        val tokyo = source("S1", "Tokyo", "About 14 million people live in Tokyo, the capital of Japan, within the city proper.")
        assertNull(SavedFactCheck.note("is tokyo a good place to live", "Tokyo is busy; 2 million people commute into the centre daily.", listOf(tokyo)))
        assertNotNull(SavedFactCheck.note("how many people live in tokyo", "About 9 million people live in Tokyo.", listOf(tokyo)))
    }

    // ---- False alarms seen on set 38's recorded answers, each kept out. ----

    @Test fun aMeasureOfSomethingElseIsNotTheSubjectsFigure() {
        val eiffel = source("S1", "Eiffel Tower", "It was supposed to be a temporary installation for the 1889 World Fair, held at Paris to show respect to 100 years of the French Revolution.")
        val q = "When was the Eiffel Tower built and how tall is it? Wasn't it supposed to be temporary?"
        assertNull(SavedFactCheck.note(q, "The Eiffel Tower was scheduled to remain only for 20 years.", listOf(eiffel)))
    }

    @Test fun aQuestionSettingTwoThingsAgainstEachOtherIsNotChecked() {
        val a380 = source("S1", "Airbus A380", "The Airbus A380 can carry up to 850 passengers (but it usually carries about 525), and weighs over 550 tonnes.")
        assertNull(SavedFactCheck.note("Airbus A380 vs Boeing 747: which is bigger and how many passengers does each carry?",
            "The Boeing 747-8 carries 416–524 passengers.", listOf(a380)))
        assertNull(SavedFactCheck.note("is the a380 or the 747 bigger? how many passengers", "The Boeing 747-8 carries 416 passengers.", listOf(a380)))
    }

    @Test fun aFigureWrittenAsAWordIsRead() {
        val canyon = source("S1", "Grand Canyon", "The Grand Canyon is 277 miles (446 km) long, up to 18 miles (29 km) wide and is over a mile (1.83 km) (6000 feet) deep in places.")
        val q = "how deep is the grand canyon and how long is it"
        assertNull(SavedFactCheck.note(q, "The Grand Canyon's depth averages about 1 mile (6,000 feet). Its length is roughly 277 miles.", listOf(canyon)))
        assertNotNull(SavedFactCheck.note(q, "The Grand Canyon is 150 miles long.", listOf(canyon)))
        assertEquals(1.0, SavedFactCheck.quantities("over a mile deep")[0].value, 0.0)
    }

    @Test fun anAgeIsNotALengthOfTimeAndADurationNeedsItsEvent() {
        val tut = source("S1", "Tutankhamun", "Tutankhamun ruled for 9 years and died very young, at 19, so he is known as The Boy King.")
        val q = "who found tutankhamuns tomb and in what year? how old was he when he died"
        assertNull(SavedFactCheck.note(q, "Tutankhamun was approximately 18 or 19 years old when he died.", listOf(tut)))
        // Said of the same thing, the reign, a different length still counts.
        assertNotNull(SavedFactCheck.note("how long did tutankhamun rule egypt", "Tutankhamun ruled for about 20 years.", listOf(tut)))
        val portugal = source("S1", "Portugal", "South Africans, however, must produce a passport which has at least 3 months' validity beyond their period of stay in Portugal.")
        assertNull(SavedFactCheck.note("I have a South African passport and want to go to Portugal. how far ahead should I apply for the visa",
            "Aim to apply for your Portugal visa 2 months in advance.", listOf(portugal)))
    }

    @Test fun quantitiesAreReadWithSeparatorsMultipliersAndRanges() {
        val q = SavedFactCheck.quantities("Tokyo has about 13.9 million people across 2,194 km², 320–390 km/h trains, 5,000-year history, 21 °C")
        assertEquals(listOf("people", "km²", "km/h", "years", "°C"), q.map { it.unit })
        assertEquals(13_900_000.0, q[0].value, 0.0); assertTrue(q[0].about)
        assertEquals(2194.0, q[1].value, 0.0); assertFalse(q[1].about)
        assertEquals(320.0 to 390.0, q[2].range)
        // Years alone, not years-as-units or parts of other numbers.
        assertEquals(listOf(1986), SavedFactCheck.years("On 26 April 1986 the 1,000 MW reactor exploded; 2,717 ft away"))
        assertEquals(emptyList<Int>(), SavedFactCheck.years("It is 1,990 m tall"))
    }
}
