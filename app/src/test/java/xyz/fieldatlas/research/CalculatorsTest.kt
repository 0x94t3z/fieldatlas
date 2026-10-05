package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.inference.FakeInferenceGateway

class CalculatorsTest {
    private fun solve(q: String) = Calculators.solve(q)
    private fun text(q: String) = solve(q)!!.text

    @Test fun fuelEconomyConvertsBothWays() {
        // 235.215 ÷ 9 = 26.14; 282.481 ÷ 9 = 31.39.
        val van = text("Our van uses 9 litres per 100 km. How many miles per US gallon is that?")
        assertTrue(van, "**26.1 miles per US gallon** (31.4 mpg in UK imperial gallons)" in van)
        assertTrue("**5.9 L/100 km**" in text("My car does 40 mpg. How many litres per 100 km is that?"))
        assertTrue("**7.1 L/100 km**" in text("40 miles per imperial gallon in L/100 km?"))
    }

    @Test fun naismithAddsAscentToWalkingTime() {
        val t = text("A 14 km walk with 900 m of climbing. How long will it take by Naismith's rule?")
        assertTrue(t, "About **4 h 18 min**" in t)
        assertTrue(t, "14 km ÷ 5 km/h = 2 h 48 min, plus 900 m ÷ 600 m/h = 1 h 30 min" in t)
        // Miles and feet are converted first: 16.09 km ÷ 5 = 3.219 h, 914.4 m ÷ 600 = 1.524 h, 4.743 h in all.
        assertTrue(text("How long will a 10 mile hike with 3,000 ft of climbing take?").contains("About **4 h 45 min**"))
        // Height above the sea is not climb: the lake is at 2,400 m, the walk is 8 km.
        assertTrue(text("How long to hike 8 km to a lake at 2,400 m elevation?").contains("About **1 h 36 min**"))
    }

    @Test fun aBillAtAGivenRateWithATip() {
        // 860 ÷ 38 = 22.63; × 1.15 = 26.03; 860 × 1.15 = 989.
        val t = text("Our taxi fare is 860 Turkish lira and 1 euro is 38 lira. How much is that in euros, with a 15% tip?")
        assertTrue(t, "860 lira ÷ 38 = **22.63 euros**" in t && "**26.03 euros**" in t && "(989 lira)" in t)
        assertTrue(solve("Our taxi fare is 860 Turkish lira and 1 euro is 38 lira. How much is that in euros, with a 15% tip?")!!.complete)
        assertTrue(text("The taxi was 18,000 yen and one euro is 162 yen. How much in euros?").contains("**111.11 euros**"))
        // Both currencies are dollars: the meal is 30 Australian dollars, not the rate's "1 US dollar".
        assertTrue(text("1 US dollar is 1.5 Australian dollars. How much is a 30 Australian dollar meal in US dollars?")
            .contains("30 Australian dollars ÷ 1.5 = **20.00 US dollars**"))
    }

    @Test fun waterForAGroupAndItsWeight() {
        val t = text("Five of us are camping for three days and each person drinks 4 litres of water a day. How much water is that, and what does it weigh?")
        assertTrue(t, "**60 litres**" in t && "**60 kg**" in t && "12 L (12 kg) each" in t)
        // The "a day" of the rate is not the number of days.
        assertTrue(text("We need 3 litres per person a day for 5 days, 4 people").contains("**60 litres**"))
        assertTrue(text("A family of four needs 4 litres per person per day for 3 days. How much water?").contains("**48 litres**"))
    }

    @Test fun powerBankWattHoursAndTheFlightRule() {
        val r = solve("Is my 26,800 mAh power bank okay in my carry-on bag?")!!
        assertTrue(r.text, "**99.16 Wh**" in r.text && "carry-on" in r.text && "never go in checked baggage" in r.text)
        assertTrue(r.complete)
        val big = text("Is a 27,000 mAh power bank allowed on a flight?")
        assertTrue(big, "**99.9 Wh**" in big && "3.7 V" in big)
        assertTrue(text("Can I fly with a 30,000 mAh 3.7 V battery?").let { "**111 Wh**" in it && "between 100 and 160 Wh" in it })
        assertTrue(text("Can I fly with a 45,000 mAh 3.7 V battery?").let { "**166.5 Wh**" in it && "do **not allow**" in it })
        // A charging voltage is not the cell's: 20,000 mAh is 74 Wh, not 400.
        val usbC = text("Can I take a 20,000 mAh power bank with 20 V USB-C output on a plane?")
        assertTrue(usbC, "**74 Wh**" in usbC && "charging voltage" in usbC)
    }

    @Test fun temperatureWithAJudgementLeadsAndResearchAnswersTheRest() {
        val r = solve("It's 95°F outside. What is that in Celsius, and is it safe for a long bike ride?")!!
        assertTrue(r.text, "95°F is **35°C**" in r.text)
        assertFalse(r.complete)
        assertEquals("95°F = 35°C.", r.facts)
        assertEquals("It's 95°F (35°C, very hot) outside. Is it safe for a long bike ride?", r.followUp)
        assertEquals("heat-illness", r.guideId)
        assertNull(solve("It's 25°C. Is that ok for a run?")!!.guideId)
        assertEquals("extreme-cold", solve("It is -15 C outside, is it safe to hike?")!!.guideId)
        assertNull("a plain conversion needs no guide", solve("What is 104°F in Celsius?")!!.guideId)
        assertTrue(solve("What is 25 degrees C in Fahrenheit?")!!.let { it.complete && "**77°F**" in it.text })
        assertTrue(solve("-40 °C in fahrenheit")!!.text.contains("**-40°F**"))
        assertTrue(solve("It is -15 C outside, is it safe to hike?")!!.let { !it.complete && "-15°C is **5°F**" in it.text && "extreme cold" in it.followUp })
    }

    @Test fun aTemperatureThatIsNotTheWeatherGetsNoHeatOrColdGuide() {
        listOf("Is a 104°F fever dangerous?", "Should my freezer be at 0°F?", "Is 75°C safe for cooking chicken?").forEach { q ->
            val r = solve(q)!!
            assertNull(q, r.guideId); assertNull(q, r.riskNote); assertFalse(q, r.complete)
        }
    }

    @Test fun unitsAndPercentages() {
        assertTrue("**6.21 miles**" in text("How far is 10 km in miles?"))
        assertTrue("**22.05 lb**" in text("Convert 10 kg to pounds"))
        assertTrue("**96.56 km/h**" in text("What is 60 mph in km/h?"))
        assertTrue("**12**" in text("What is 15% of 80?"))
        assertTrue(text("20% tip on 45").let { "**9**" in it && "**54**" in it })
        assertNull(solve("What did 70% of 2023 visitors to Bali say?"))
    }

    @Test fun ordinaryQuestionsAreNotCalculations() {
        listOf(
            "Tell me the best vegan restaurants in Berlin",
            "Explain how Naismith's rule works",
            "One day is 24 hours; how do time zones work?",
            "What is the difference between Kyber and Dilithium?",
            "A snake bit my friend. What do we do?",
            "Is tap water safe to drink in Lima?",
            "How many people live in Lagos?",
            "What happened in 1989 in Berlin?",
            "Compare 5G and 4G",
        ).forEach { assertNull(it, solve(it)) }
    }

    @Test fun numbersInsideALargerQuestionOnlyLeadTheAnswer() {
        // Each asks for more than the arithmetic; the numbers lead and research answers the rest.
        listOf(
            "My baby has a fever of 39°C, what do I do?",
            "When is the best time to hike the 43 km Inca Trail?",
            "How many times can a 10000 mAh power bank charge my phone?",
            "What to pack for 30°C weather in Bangkok?",
            "How far is 10 km in miles and where is the nearest pharmacy?",
            "My son is 4 feet 2 inches, can he ride the roller coasters?",
        ).forEach { q -> solve(q)?.let { assertFalse(q, it.complete) } }
    }

    @Test fun aPureCalculationNeverCallsTheModel() = runBlocking {
        val inference = FakeInferenceGateway(listOf("must not be generated"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference)
            .research("Our van uses 9 litres per 100 km. How many miles per US gallon is that?").toList()
        assertEquals(0, inference.generateCalls)
        val answer = events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
        assertTrue(answer, answer.startsWith("9 L/100 km is about **26.1 miles per US gallon**") && answer.endsWith("_Calculated on this phone._"))
        assertTrue(events.last() is ResearchEvent.Complete)
    }

    @Test fun extremeHeatIsAnsweredByTheAppAndTheOfficialGuideWithoutTheModel() = runBlocking {
        val guides = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText()).guides
        val inference = FakeInferenceGateway(listOf("must not be generated"))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference, emergencyGuides = { guides })
            .research("The forecast says 106°F this afternoon. Is it dangerous to go cycling?").toList()
        assertEquals(0, inference.generateCalls)
        val answer = events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text }
        assertTrue(answer, answer.startsWith("106°F is **41.1°C**."))
        assertTrue(answer, "risks heat exhaustion and heat stroke" in answer && "above 103°F (39°C) as \"danger\"" in answer)
        assertTrue(answer, "shown as published by NOAA/National Weather Service [S1]" in answer && "Heat Stroke" in answer)
        assertEquals(setOf("S1"), (events.last() as ResearchEvent.Complete).metrics.citedSourceIds)
        assertTrue(solve("It's 35°C, is it safe to hike?")!!.riskNote!!.contains("\"extreme caution\""))
        assertTrue(solve("It is -15 C outside, is it safe to hike?")!!.riskNote!!.contains("frostbite"))
        assertNull(solve("It's 25°C. Is that ok for a run?")!!.riskNote)
    }

    @Test fun aCalculationWithAFurtherQuestionLeadsTheResearchedAnswer() = runBlocking {
        val inference = FakeInferenceGateway(listOf("25°C is comfortable for a run; drink water."))
        val events = ResearchOrchestrator(Retriever { _, _, _ -> emptyList() }, inference)
            .research("It's 77°F outside. What is that in Celsius, and is it ok for a run?").toList()
        // Rebuild the answer exactly as the screen does: a lead or a replacing token resets it.
        val shown = StringBuilder()
        for (e in events) when (e) {
            is ResearchEvent.Lead -> { shown.clear(); shown.append(e.text) }
            is ResearchEvent.Token -> { if (e.replace) shown.clear(); shown.append(e.text) }
            else -> Unit
        }
        assertTrue(shown.toString(), shown.startsWith("77°F is **25°C**."))
        assertTrue(shown.toString(), "25°C is comfortable" in shown)
        assertTrue(inference.generateCalls >= 1)
    }

    @Test fun moneyConvertsForwardAndAcceptsACountryBeforeTheCurrency() {
        val t = text("I have 150 euros and 1 euro is 4.3 Polish zloty. How many zloty is that?")
        assertTrue(t, "150 euros × 4.3 = **645.00 Polish zloty**" in t)
    }

    @Test fun aFullTanksRangeAndWhetherATripFits() {
        val t = text("My car does 6 litres per 100 km and the tank holds 45 litres. Can I drive 800 km on one tank?")
        // 45 ÷ 6 × 100 = 750 km; 800 km needs 48 L.
        assertTrue(t, "**750 km** on a full tank" in t && "So **no**: 800 km needs about 48 L, 3 L more" in t)
        assertTrue(text("The tank holds 50 L and my car uses 5 L/100 km. Can I do 600 km?").contains("So **yes**, 600 km fits, using about 30 L"))
    }

    @Test fun walkingTimeAgainstTheDaylightLeft() {
        val r = solve("I need to walk 26 km at about 4 km/h and there are 7 hours of daylight left. Can I make it before dark?")!!
        // 26 ÷ 4 = 6.5 h moving; with 10 minutes' rest an hour 7 h 35 min, over the 7 h left.
        assertTrue(r.text, "**6 h 30 min** of moving time" in r.text && "about **7 h 35 min**" in r.text && "**only just**" in r.text)
        assertTrue(r.complete)
        assertTrue(text("Walking 10 km at 5 km/h with 4 hours of daylight left, will I make it?").contains("**yes**: even with breaks you have 1 h 40 min to spare"))
    }

    @Test fun theTimeInAnotherCityUsesThatDaysOffsets() {
        val october = java.time.LocalDate.of(2026, 10, 5)
        assertTrue(Calculators.solve("It is 8:30 am in Nairobi. What time is it in Lima?", october)!!.text
            .startsWith("8:30 am in Nairobi is **12:30 am in Lima**.\n\nToday Nairobi is on UTC+3 and Lima on UTC−5."))
        // Daylight saving on both sides: Sydney is UTC+11 and Berlin UTC+1 in December, UTC+10 and UTC+2 in June.
        assertTrue(Calculators.solve("It is 3 pm in Sydney. What time is it in Berlin?", java.time.LocalDate.of(2026, 12, 1))!!.text
            .startsWith("3:00 pm in Sydney is **5:00 am in Berlin**."))
        assertTrue(Calculators.solve("It is 3 pm in Sydney. What time is it in Berlin?", java.time.LocalDate.of(2026, 6, 15))!!.text
            .startsWith("3:00 pm in Sydney is **7:00 am in Berlin**."))
        assertTrue(Calculators.solve("What time is it in New York when it is 9:00 in Paris?", october)!!.text
            .startsWith("9:00 am in Paris is **3:00 am in New York**."))
        assertTrue(Calculators.solve("What time is it in Kolkata when it's 10 am in New Delhi?", october) == null)
        assertTrue(Calculators.solve("My flight leaves London at 10 am. What time will I arrive in New York?", october) == null)
    }

    @Test fun aRecipeForMorePeople() {
        assertTrue(text("A recipe for 4 people needs 300 g of rice. How much do I need for 10 people?").contains("300 g × 10 ÷ 4 = **750 g of rice** for 10 people."))
        // The amount asked for can come first.
        assertTrue(text("How much rice for 10 people if the recipe for 4 people uses 300 g of rice?").contains("300 g × 10 ÷ 4 = **750 g of rice** for 10 people."))
        assertTrue(text("A cake for 8 people needs 200 g of brown sugar. How much for 6 people?").contains("**150 g of brown sugar** for 6 people."))
    }

    @Test fun whereWaterBoilsAtAltitude() {
        val t = text("What is the boiling point of water at 2,800 m altitude?")
        assertTrue(t, "about **90.7°C**" in t && "pasta, rice and beans need noticeably longer" in t)
        assertTrue(text("What is the boiling point of water at 10,000 feet elevation?").contains("about **89.9°C**"))
    }

    @Test fun aStatedSpeedIsNotADistanceAndHeightsKeepTheirFeet() {
        // 12 km at 5 km/h is 2 h 24 min; Naismith once read "5 km" out of "5 km/h" and said 1 h.
        assertTrue(text("I walk at 5 km/h, how long will 12 km take?").contains("12 km ÷ 5 km/h = **2 h 24 min**"))
        assertTrue(text("Convert 5 feet 10 inches to centimetres").contains("5 ft 10 in is **177.8 cm**"))
        val train = solve("Can I bring a 20,000 mAh power bank on a train?")!!
        assertFalse("whether it may go on a train is left to research", train.complete)
        assertTrue(solve("How many watt-hours is a 10,000 mAh 3.6 V power bank, and can I take it on a plane?")!!.complete)
    }

    @Test fun theTimeBetweenTwoClockTimes() {
        assertTrue(text("Sunrise is at 6:10 and sunset is at 18:40. How many hours of daylight is that?").contains("From 06:10 to 18:40 is **12 h 30 min**."))
        assertTrue(text("The bar opens at 9 pm and closes at 2 am. How long is it open?").contains("From 21:00 to 02:00 the next day is **5 h**."))
        assertNull(solve("What time is it in Lima when it is 9:00 in Paris and 10:00 in Rome?")?.takeIf { "From" in it.text })
    }
}
