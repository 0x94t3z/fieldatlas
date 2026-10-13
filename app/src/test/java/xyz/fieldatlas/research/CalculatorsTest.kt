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

    @Test fun powerBankChargesUseTheStatedEfficiency() {
        // 10,000 × 0.65 = 6,500 usable; ÷ 4,500 = 1.44. The model once multiplied the two capacities.
        val r = solve("My power bank is 10,000 mAh and my phone battery is 4,500 mAh. If charging is only about 65% efficient, how many full phone charges do I get from it?")!!
        assertTrue(r.text, "**1.44**" in r.text && "**one full charge**" in r.text)
        assertTrue(r.complete)
        assertTrue(text("How many charges will a 20000 mAh bank give a 4000 mAh phone at 70% efficiency?").contains("**3 full charges**"))
        // Without an efficiency, the energy alone leads and research continues.
        assertFalse(solve("How many charges will a 20000 mAh bank give a 4000 mAh phone?")?.text.orEmpty().contains("full charges"))
    }

    @Test fun powerBankPhrasingsPeopleUse() {
        assertTrue(text("How many times can a 20,000 mAh power bank charge my iPhone 15 (3,349 mAh) at 80% efficiency?").contains("**4.78**"))
        assertTrue(text("How many times can a 10,000 mAh power bank charge my smartphone with a 4,000 mAh battery at 80% efficiency?").contains("**2.00**") ||
            text("How many times can a 10,000 mAh power bank charge my smartphone with a 4,000 mAh battery at 80% efficiency?").contains("**2**"))
        assertTrue(text("How many full charges from a 26,800 mAh battery for my 4,000 mAh phone at 85% efficiency?").contains("**5.70**") ||
            text("How many full charges from a 26,800 mAh battery for my 4,000 mAh phone at 85% efficiency?").contains("**5.7**"))
        assertTrue(text("How many times can I charge a 3,000 mAh Galaxy from a 10,000 mAh Anker at 80% efficiency?").contains("**2.67**"))
        // The efficiency is the percentage bound to "efficient"; the phone's charge and its drain are not.
        assertTrue(text("I have a 10,000 mAh power bank and a 4,000 mAh phone. The phone is at 40% and the bank is 80% efficient. How many full charges?").contains("**2 full charges**"))
        assertTrue(text("My phone lost 40% in an hour. How many full charges will a 10,000 mAh power bank give a 4,000 mAh phone at 80% efficiency?").contains("**2 full charges**"))
        assertTrue(text("How many full charges will a 10,000 mAh power bank give a 4,000 mAh phone with a 35% efficiency loss?").contains("**1.63**"))
        assertTrue(text("My phone is at 20%. How many full charges will a 10,000 mAh power bank give a 4,000 mAh phone at 80% efficiency?").contains("**2 full charges**"))
    }

    @Test fun aDeviceWordBesideTheBankIsNotThePhone() {
        assertTrue(text("How many times can my 10000 mAh phone charger charge my 3000 mAh phone at 80% efficiency?").contains("**2.67**"))
        assertTrue(text("How many times can my 20000 mAh laptop power bank charge my 4000 mAh phone at 85% efficiency?").contains("**4.25**"))
        assertTrue(text("How many times can my 10000 mAh mobile battery charge my 3000 mAh iPhone at 80% efficiency?").contains("**2.67**"))
        assertTrue(text("How many times can a 20000 mAh phone power bank give a 5000 mAh Galaxy at 80% efficiency?").contains("**3.20**") ||
            text("How many times can a 20000 mAh phone power bank give a 5000 mAh Galaxy at 80% efficiency?").contains("**3.2**"))
        // Other ways of writing the efficiency.
        assertTrue(text("How many times can my 10000 mAh power bank charge my 3000 mAh phone? Efficiency: 80%").contains("**2.67**"))
        assertTrue(text("How many times can my 10000 mAh power bank charge my 3000 mAh phone with an efficiency rating of 85%?").contains("**2.83**"))
        assertTrue(text("How many times can my 10000 mAh power bank charge my 3000 mAh phone assuming 85%-efficient charging?").contains("**2.83**"))
        // A phone's own drain is not a charging loss, and a top-up from 20% is not a full charge.
        assertFalse(solve("How many times can my 20000 mAh power bank charge my 4000 mAh phone? My phone loses 30% overnight.")?.complete == true)
        assertFalse(solve("How many times can my 20000 mAh power bank charge my 4000 mAh phone from 20% to 100% at 80% efficiency?")
            ?.text.orEmpty().contains("**2 full charges**"))
    }

    @Test fun aChargeAnswerIsNeverConfidentlyWrong() {
        // One battery named by a nearby word is not enough to swap them: the usual case is stated instead.
        val q = "How many times can a 10,000 mAh battery charge my phone at 80% efficiency if the battery in it is only 4,000 mAh?"
        assertTrue(text(q), text(q).contains("**2 full charges**") && text(q).contains("is the power bank"))
        // A loss that is not a charging loss is not the efficiency.
        for (it in listOf("How many full charges does a 10,000 mAh power bank give a 4,000 mAh phone? It's been in storage, about 10% loss from self-discharge.",
            "How many full charges does a 10,000 mAh power bank give a 4,000 mAh phone? My phone battery has a 20% loss in capacity.",
            "How many full charges will a 10,000 mAh power bank give a 4,000 mAh phone with a 75% efficiency loss?",
            // Conditions this shortcut does not model: research continues.
            "My 20,000 mAh power bank is only 50% charged. How many times can it charge my 4,000 mAh phone at 80% efficiency?",
            "How many full charges from two 10,000 mAh power banks for a 4,000 mAh phone at 80% efficiency?",
            "How many times can I charge my 4000 mAh phone from 20 to 80% with a 10000 mAh bank at 85% efficiency?",
            "How many times can I top up my 4000 mAh phone with a 10000 mAh bank at 85% efficiency?",
            "How many times can a 10000 mAh bank charge my 4000 mAh phone at 85% efficiency using wireless charging, which loses another 30%?",
            "How many times can a 10000 mAh bank charge my 4000 mAh phone? The power bank is 90% efficient and the cable loses 10% to heat.",
            "How many times can a 10000 mAh bank charge my 4000 mAh phone? Efficiency is 60% to 70%.",
            // Thousands written with a dot or a space.
            "How many full charges does a 10000 mAh power bank give a 4.500 mAh phone at 85% efficiency?",
            "How many times can a 20.000 mAh power bank charge my 4500 mAh phone at 80% efficiency?",
            "How many times can a 20000 mAh power bank charge my 4 500 mAh phone at 80% efficiency?",
        )) assertFalse(it, solve(it)?.text.orEmpty().contains("full charge"))
    }

    @Test fun powerBankRolesComeFromTheWordsNotTheSize() {
        // A small bank and a big phone: 2,000 × 0.8 ÷ 5,000 = 0.32, once mis-read as two charges.
        val small = solve("My power bank is 2,000 mAh and my phone battery is 5,000 mAh at 80% efficiency. How many full charges do I get?")!!
        assertTrue(small.text, "**0.32**" in small.text && "less than one full charge" in small.text && "same voltage" in small.text)
        // Phone named first, bank second.
        assertTrue(text("My phone has a 4,000 mAh battery. How many times can I charge it from a 10,000 mAh power bank at 80% efficiency?").contains("**2 full charges**"))
        // No efficiency stated, or the voltages differ: no shortcut, research continues.
        // A loss is the complement of the efficiency: 35% lost is 65% efficient, 10,000 × 0.65 ÷ 4,500 = 1.44.
        assertTrue(text("My power bank is 10,000 mAh and my phone battery is 4,500 mAh. About 35% is lost to heat and conversion. How many full charges will I get?")
            .contains("**1.44**"))
        assertTrue(text("How many charges does a 26,800 mAh battery pack give a 3,200 mAh phone if it has 30% conversion loss?").contains("**5.86**"))
        // No word says which is which: the usual case, said out loud.
        assertTrue(text("How many charges do I get from 10,000 mAh into 4,000 mAh at 80% efficiency?").contains("the larger one, 10,000 mAh, is the power bank"))
        for (q in listOf("My phone is at 50% now. I have a 10,000 mAh power bank and a 4,500 mAh phone. How many full charges can I get?",
            "How many charges will a 20,000 mAh 3.7 V power bank give a 4,000 mAh phone at 80% efficiency?")) {
            val r = solve(q)
            assertFalse(q, r?.complete == true || r?.text.orEmpty().contains("full charge"))
        }
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


    @Test fun aClockTimePlusOrMinusADuration() {
        val gate = solve("My flight departs at 14:20 and boarding closes 30 minutes before. When must I be at the gate?")!!
        assertTrue(gate.text, "14:20 minus 30 min is **13:50**" in gate.text)
        assertTrue(gate.complete)
        assertTrue(text("My train leaves at 9:40 and the trip takes 2 h 35. When do I arrive?").contains("**12:15**"))
        assertTrue(text("The night bus leaves at 22:30 and takes 9 hours. What time does it arrive?").contains("**07:30 the next day**"))
        // Another place's clock needs its time zone: not a plain sum.
        assertNull(solve("My flight leaves at 23:50 local time and takes 13 h 45 min. What time is it at home (UTC+1) when I land?"))
        // No direction given: not guessed.
        assertNull(solve("I have a meeting at 15:00 and 45 minutes. When?"))
        // A known end counts back.
        assertTrue(text("I need to arrive by 9:00 and the walk takes 25 minutes. When must I leave?").contains("**08:35**"))
        assertTrue(text("My train arrives at 18:00 and the journey takes 3 hours. When does it leave?").contains("**15:00**"))
        assertTrue(text("If I start hiking at 7:30 and it takes 5 hours, when will I finish?").contains("**12:30**"))
        // A second leg the sum would leave out is not answered with half of it.
        assertNull(solve("My train leaves at 9:40 and takes 2 hours, then a 30-minute bus ride. When do I arrive?"))
        assertNull(solve("My train leaves at 9:40 and takes 2 hours, then the bus takes an hour. When do I arrive?"))
    }

    @Test fun aPricePerNightForSeveralNightsWithADiscount() {
        val hostel = solve("A hostel costs 18 euros a night. How much for 9 nights with a 10% discount?")!!
        assertTrue(hostel.text, "18.00 euros × 9 nights = **162.00 euros**" in hostel.text)
        assertTrue(hostel.text, "**145.80 euros**" in hostel.text)
        assertTrue(hostel.complete)
        assertTrue(text("A room is €45 per night. What do 3 nights cost plus 7% tax?").contains("**€144.45**"))
        // One price and no count: nothing to multiply.
        assertNull(solve("A hostel costs 18 euros a night. Is that cheap?"))
        // A price per person per night multiplies by both counts, and a flat charge per night is added after.
        assertTrue(text("A dorm bed costs 18 euros per person per night. How much for 3 people for 4 nights?").contains("18.00 euros × 3 people × 4 nights = **216.00 euros**"))
        val taxed = solve("A hostel costs 18 euros a night plus tax of 2 euros a night. How much for 4 nights?")!!
        assertTrue(taxed.text, "Tax: 2.00 euros × 4 nights = **8.00 euros**" in taxed.text && "**80.00 euros**" in taxed.text)
        assertTrue(taxed.complete)
        // What this sum does not cover is left to research rather than half answered.
        assertNull(solve("A room is €45 per night. What do 3 nights cost with a 10% booking fee?"))
        assertNull(solve("A hostel costs 18 euros a night. How much for 9 nights in dollars?"))
        assertNull(solve("A bike costs 5 euros an hour. How much for 2.5 hours?"))
        assertNull(solve("A room is €100 per night including 10% VAT. How much for 3 nights?"))
        assertNull(solve("The room is 50 euros a night after a 20% discount. How much for 3 nights?"))
    }

    @Test fun theClockTimesRoleComesFromTheNearestRoleWordAndTheAskFromTheFirstVerb() {
        // "Leave to arrive by 9:00": the time is the arrival, and leaving is what is asked.
        assertTrue(text("When do I leave to arrive by 9:00 if the walk takes 25 minutes?").contains("09:00 minus 25 min is **08:35**"))
        assertTrue(text("What time do I need to leave to arrive at 9:00 if the walk takes 25 minutes?").contains("**08:35**"))
        assertTrue(text("The walk takes 25 minutes. When must I leave to arrive by 9:00?").contains("**08:35**"))
        assertTrue(text("Boarding closes at 13:50 and the walk to the gate takes 15 minutes. When must I leave to get to the gate?").contains("**13:35**"))
        assertTrue(text("When do we set off to be back by 18:00 if the hike takes 4 hours?").contains("18:00 minus 4 h is **14:00**"))
        assertTrue(solve("When do I leave to arrive by 9:00 if the walk takes 25 minutes?")!!.complete)
        // The time is another trip's start ("the ferry leaves at 7:15") and a start is asked: not one sum.
        assertNull(solve("The ferry leaves at 7:15 and the drive takes 45 minutes. When do we leave to get there in time?"))
        assertNull(solve("My train leaves at 9:40 and the walk takes 20 minutes. When must I leave to be there?"))
        // Both ends asked: one sum cannot give both.
        assertNull(solve("My train leaves at 9:40 and takes 2 h 35. When do I leave and when do I arrive?"))
        assertNull(solve("My train leaves at 9:40 and takes 2 h 35. When do I leave and arrive?"))
        // The plain forms keep their answers.
        assertTrue(text("The ferry leaves at 7:15 and the crossing takes 45 minutes. When do we arrive?").contains("07:15 plus 45 min is **08:00**"))
        assertTrue(text("When will I arrive if I leave at 9:40 and the trip takes 2 hours?").contains("**11:40**"))
        assertTrue(text("The hike takes 6 hours and we must be back by 17:00. When do we start?").contains("**11:00**"))
    }

    @Test fun aDurationBeforeOrAfterAnotherEventIsNotCountedFromTheTime() {
        assertNull(solve("We set off at 6:00 and it is 4 hours before we get there. When do we arrive?"))
        assertNull(solve("We start hiking at 12:00 and have 6 hours before sunset. When is sunset?"))
        assertNull(solve("It's 15:00 now and we have 3 hours before dark. When does it get dark?"))
        assertNull(solve("The museum opens at 9:00 and I want to spend 3 hours there before lunch. When is lunch?"))
        assertNull(solve("The bus departs at 8:00 and it's 6 hours before we arrive. When do we get there?"))
        assertNull(solve("I land at 14:00 and have 3 hours before my train. When does the train leave?"))
        assertNull(solve("I land at 14:00 and there are 3 hours until my train. When does the train leave?"))
        assertNull(solve("The tour starts at 9:00 and we meet 2 hours after lunch. When do we meet?"))
        // "Before" the time's own event still counts back.
        assertTrue(text("My flight departs at 14:20 and boarding closes 30 minutes before departure. When must I be at the gate?").contains("**13:50**"))
        assertTrue(text("My flight departs at 14:20 and boarding closes 30 minutes before the flight. When must I be at the gate?").contains("**13:50**"))
        assertTrue(text("I need to be at the station 20 minutes before my train at 9:40. When must I be there?").contains("**09:20**"))
        assertTrue(text("The tour starts at 9:00 and we should arrive 15 minutes early. When should we get there?").contains("**08:45**"))
        // "After" and "later" run forward from the event they name.
        assertTrue(text("The bus leaves 15 minutes after the train arrives at 10:00. When does the bus leave?").contains("10:00 plus 15 min is **10:15**"))
        assertTrue(text("I need to leave 30 minutes after the meeting ends at 15:00. When do I leave?").contains("**15:30**"))
        assertTrue(text("I arrive at 10:00 and leave 3 hours later. When do I leave?").contains("10:00 plus 3 h is **13:00**"))
        assertTrue(text("We leave at 9:00 and arrive 3 hours later. When do we arrive?").contains("**12:00**"))
    }

    @Test fun minutesAfterTheHoursWithoutTheirUnit() {
        val train = solve("My train leaves at 9:40 and takes 2 hrs 45. When do I arrive?")!!
        assertTrue(train.text, "09:40 plus 2 h 45 min is **12:25**" in train.text)
        assertTrue(train.complete)
        assertTrue(text("The bus leaves at 22:30 and takes 9 hours 30. When does it arrive?").contains("**08:00 the next day**"))
        assertTrue(text("My train leaves at 9:40 and takes 2 hours and 45. When do I arrive?").contains("**12:25**"))
        assertTrue(text("My train leaves at 9:40 and takes 2h35. When do I arrive?").contains("**12:15**"))
        assertTrue(text("My train leaves at 9:40 and takes 2 hours 30 minutes. When do I arrive?").contains("**12:10**"))
        // A single digit after the hours is not read as minutes, and is not dropped either.
        assertNull(solve("My train leaves at 9:40 and takes 2 hours 5. When do I arrive?"))
    }

    @Test fun aTripBetweenNamedPlacesIsLeftToTheirTimeZones() {
        assertNull(solve("My flight leaves Paris at 10:00 and takes 8 hours. What time do I land in New York?"))
        assertNull(solve("my flight leaves paris at 10:00 and takes 8 hours. what time do i land in new york?"))
        assertNull(solve("My train leaves Paris at 9:40 and takes 3 h 35. When do I get to London?"))
        assertNull(solve("The night bus to Chiang Mai leaves at 22:30 and takes 9 hours. What time does it arrive?"))
        // No place named: the plain sum stands.
        assertTrue(text("My flight leaves at 10:00 and takes 8 hours. What time do I land?").contains("10:00 plus 8 h is **18:00**"))
        assertTrue(text("The museum in Paris opens at 9:00 and the visit takes 3 hours. When do we finish?").contains("**12:00**"))
    }

    @Test fun peopleCountedWithoutAUnitWordAreASecondMultiplier() {
        assertNull(solve("There are 3 of us. A dorm bed costs 15 euros a night. How much for 4 nights?"))
        // A price per person takes the head count as its multiplier.
        assertTrue(text("€18 a night per person. How much for 4 nights for the two of us?").contains("€18.00 × 4 nights × 2 people = **€144.00**"))
        assertNull(solve("We are 3. A dorm bed costs 15 euros a night. How much for 4 nights?"))
        assertNull(solve("A dorm bed is €20 per night. How much for both of us for 3 nights?"))
        assertNull(solve("A dorm bed costs 15 euros a night. How much for 4 nights for two?"))
        assertNull(solve("Ski rental is 30 euros a day. How much for 6 days for my son and me?"))
        assertNull(solve("A hostel is 20 euros per night per person and we stay 3 nights. Total?"))
        assertNull(solve("Bikes are €15 a day each. How much for 3 days?"))
        val hostel = solve("A hostel costs 18 euros a night. How much for 9 nights?")!!
        assertTrue(hostel.text, "18.00 euros × 9 nights = **162.00 euros**" in hostel.text)
        assertTrue(hostel.complete)
        assertTrue(text("A tour costs €40 per person. How much for 3 people?").contains("**€120.00**"))
        assertTrue(text("A bed in a 6-bed dorm costs €18 a night. How much for 4 nights?").contains("**€72.00**"))
    }

    @Test fun aUnitPriceWithoutItsCurrencyIsNotMultiplied() {
        assertNull(solve("A room costs 60 euros plus 10 per person. How much for 3 people?"))
        assertNull(solve("We are 2 per room and each room costs 60 euros. How much for 3 rooms?"))
        assertNull(solve("A tour costs €50 plus 5 per person. How much for 4 people?"))
        assertNull(solve("Tickets are 12 per person. How much for 4 people?"))
        // A number the sum would leave out.
        assertNull(solve("A room costs 60 euros a night plus 10 a day. How much for 3 nights?"))
        assertTrue(text("A room is €45 per night. What do 3 nights cost?").contains("**€135.00**"))
    }

    @Test fun aChargeWithoutItsPercentageIsNotLeftOut() {
        assertNull(solve("A room is €45 per night plus tax. How much for 3 nights?"))
        assertNull(solve("A room is €45 per night plus VAT. How much for 3 nights?"))
        assertNull(solve("A room is €45 per night minus a discount. How much for 3 nights?"))
        assertNull(solve("A room is €45 per night. What do 3 nights cost plus 7% tax and a cleaning fee?"))
        // "Percent" is a percentage and is applied.
        val hostel = solve("A hostel costs 18 euros a night. How much for 9 nights with a 10 percent discount?")!!
        assertTrue(hostel.text, "**145.80 euros**" in hostel.text)
        assertTrue(hostel.complete)
        assertTrue(text("A room is €45 per night. How much for 4 nights with 15 percent off?").contains("**€153.00**"))
    }

    @Test fun aTripsFuelCostSplitAmongThePeople() {
        // 840 × 6.5 ÷ 100 = 54.6 L; × 1.89 = 103.19; ÷ 3 = 34.40.
        val trip = solve("Road trip is 840 km. The rental uses 6.5 litres per 100 km and petrol is 1.89 euros a litre. If 3 of us split the fuel, how much does each person pay?")!!
        assertTrue(trip.text, "**54.6 litres**" in trip.text)
        assertTrue(trip.text, "**103.19 euros**" in trip.text)
        assertTrue(trip.text, "**34.40 euros each**" in trip.text)
        assertTrue(trip.complete)
        // Miles and US gallons: 300 ÷ 35 = 8.57 gal; × 3.60 = 30.86.
        assertTrue(text("The drive is 300 miles and the car does 35 mpg. Gas is $3.60 a gallon. Total fuel cost?").contains("**$30.86**"))
        // British money means imperial gallons: 260 ÷ 42 = 6.19 gal = 28.14 L; × 1.45 = 40.81; ÷ 3 = 13.60.
        assertTrue(text("The hire car does 42 mpg and we have 260 miles to drive; petrol is £1.45 a litre. What's the fuel bill between 3 of us?").contains("**£13.60 each**"))
        // Kilometres per litre, and a price with no currency named where fuel is spoken of: 980 ÷ 14 = 70 L; × 1.45 = 101.50.
        assertTrue(text("My car gets 14 km per litre. If I drive 980 km and fuel is 1.45 a litre, how much fuel money do I need?").contains("**101.50**"))
        assertTrue(text("Our camper burns 12 litres per 100 km. The route is 1,450 km and diesel is 1.75 euros a litre. Split between the two of us, what do we each pay?").contains("**152.25 euros each**"))
        // Half the driving is not doubled on a guess.
        assertNull(solve("Road trip of 1,200 km, each way. The van uses 9 L/100 km, diesel 1.60 euros a litre. How much for fuel?"))
        // A split asked for without a head count leads; a toll the sum leaves out keeps it a lead.
        assertFalse(solve("Drive 500 km at 7 L/100 km with diesel at 1.80 euros a litre. How much do we each pay?")!!.complete)
        assertFalse(solve("The car uses 7 L/100 km and the trip is 500 km; fuel is 1.80 euros a litre and the toll is 25 euros. Total cost?")!!.complete)
    }

    @Test fun changingMoneyAtAQuotedRateLessTheCommission() {
        // 2,000,000 ÷ 25,400 = 78.74; × 0.97 = 76.38.
        val dong = solve("I've got 2,000,000 Vietnamese dong left. The exchange office gives 25,400 dong per US dollar and takes a 3% commission. How many dollars do I get back?")!!
        assertTrue(dong.text, "**78.74 US dollars** before the commission" in dong.text)
        assertTrue(dong.text, "**76.38 US dollars**" in dong.text)
        assertTrue(dong.complete)
        // The other way round, and "one euro buys" as the rate: 300 × 4.28 = 1,284; × 0.96 = 1,232.64.
        assertTrue(text("One euro buys 4.28 zloty at the kiosk, which keeps a 4% fee. If I change 300 euros, how many zloty will I walk away with?").contains("**1,232.64 zloty**"))
        // A flat fee in the money handed over comes off first: (400 − 5) × 0.86 = 339.70.
        assertTrue(text("The bank gives 0.86 pounds per euro and charges a flat 5 euro fee. How many pounds for 400 euros?").contains("**339.70 pounds**"))
        assertTrue(text("1 USD = 17.2 Mexican pesos. How many pesos is $250?").contains("**4,300.00 Mexican pesos**"))
        // Dots between the thousands, for money written that way: 1,500,000 ÷ 17,100 = 87.72; 3,000,000 ÷ 25,400 = 118.11.
        assertTrue(text("I've got 1.500.000 rupiah left. 1 euro is 17.100 rupiah. How many euros is that?").contains("**87.72 euros**"))
        assertTrue(text("The exchange office gives 25.400 dong per dollar. How many dollars do I get for 3.000.000 dong?").contains("**118.11 dollars**"))
        assertTrue(text("Rate 36,5 baht per euro. How many baht for 100 euros?").contains("**3,650.00 baht**"))
        // "1.250 dollars" could be 1.25 or 1,250: not guessed.
        assertNull(solve("The rate is 1.250 dollars to the euro. How many euros do I get for 500 dollars?"))
        // A fee named without its size, a fee that is not the changer's, two amounts, or two kinds of fee.
        assertNull(solve("1 euro is 38 lira and the office takes a commission. How many lira for 200 euros?"))
        assertNull(solve("I have 500 dollars. 1 dollar is 36 baht. The hotel charges a 10% fee for late checkout. How many baht do I get?"))
        assertNull(solve("The exchange rate is 25,400 dong per dollar. I have 2,000,000 dong and 50 dollars. How many dollars in all?"))
        assertNull(solve("I have 200 euros. 1 euro is 4.3 zloty. The exchange office takes 2% commission and a 5 zloty fee. How many zloty do I get?"))
        // A bill with a service charge keeps its sum: 40 ÷ 1.27 = 31.50; × 1.1 = 34.65.
        assertTrue(text("One pound is 1.27 dollars. There is a service charge of 10% on my 40 dollar dinner. How much is the dinner in pounds with the charge?").contains("**34.65 pounds**"))
    }

    @Test fun waterReadsTheDaysAsTheQuestionGivesThem() {
        // 3 × 2 × 3.5 = 21 L, 7 L (7 kg) each; "day 2" once read as one day.
        val hike = solve("3 of us are hiking and we won't reach any water until the end of day 2. If each person drinks 3.5 litres a day, how much water do we need to carry from the start, and how much will it weigh per person?")!!
        assertTrue(hike.text, "3 people × 2 days × 3.5 L = **21 litres**" in hike.text)
        assertTrue(hike.text, "7 L (7 kg) each" in hike.text)
        assertTrue(hike.complete)
        assertTrue(text("Two of us won't find water until the third day. If we each drink 4 litres a day, how much do we need to start with?").contains("**24 litres**"))
        assertTrue(text("We're a group of 5 on a 2-day hike and each person needs 2.5 litres of water a day. How much water do we bring?").contains("**25 litres**"))
        assertTrue(text("There's no water for 48 hours on this trail and the three of us drink 3 litres a day each. How much water is that?").contains("2 days (48 hours) × 3 L = **18 litres**"))
        assertTrue(text("Each hiker drinks 3 litres a day. How much water for a 5-day trek for 6 hikers?").contains("**90 litres**"))
        assertTrue(text("I drink about 2.5 litres a day. How much water do I need for a week?").contains("7 days (1 week) × 2.5 L = **17.5 litres**"))
        assertTrue(solve("How much water do I need for a 3-day hike at 3 litres a day?")!!.complete)
        // Nights, no head count, a weekend or a child drinking half: the sum leads and says what it assumed.
        val nights = solve("We need 3 litres of water per person per day on the trek. How much for 4 nights?")!!
        assertTrue(nights.text, "**12 litres**" in nights.text)
        assertTrue(nights.text, "This counts one person" in nights.text)
        assertTrue(nights.text, "4 nights as 4 days" in nights.text)
        assertFalse(nights.complete)
        assertFalse(solve("We're camping for the weekend, 4 litres a day each, 3 of us. How much water?")!!.complete)
        assertFalse(solve("3 of us, 3 litres per person per day, 2 days, plus 1 litre each for cooking. Total?")!!.complete)
        assertNull(solve("We are 4 and drink 3 litres a day each for the 2 days; one of us is a child who drinks half. How much water?"))
    }

    @Test fun landingTimeAcrossTimeZonesFromTheOffsetsGiven() {
        // 23:50 + 13 h 45 min = 13:35 Singapore time the next day; London is 7 h behind: 06:35.
        val london = solve("My flight leaves Singapore at 23:50 local time and takes 13 h 45 min to London. Singapore is UTC+8 and London is UTC+1 right now. What time is it in London when I land?")!!
        assertTrue(london.text, "London is 7 h behind Singapore (UTC+1 against UTC+8), so you land at **06:35 the next day**, London time." in london.text)
        assertTrue(london.complete)
        assertTrue(text("My flight departs New York at 18:30 and the flight time is 7 h 10 min. New York is UTC-4 and Paris is UTC+2. What time do I land in Paris?").contains("**07:40 the next day**"))
        assertTrue(text("I leave Tokyo at 11:00 on a 9 hour 30 minute flight to Los Angeles. Tokyo is GMT+9, Los Angeles is GMT-7. What time is it in Los Angeles when I arrive?").contains("**04:30**, Los Angeles time"))
        assertTrue(text("I take off from Lima at 23:10 (UTC−5) and fly 11 h 30 min to Madrid (UTC+2). What time do I land?").contains("**17:40 the next day**"))
        assertTrue(text("The flight is 13 h 45 min and leaves at 23:50 Singapore time (UTC+8). London is UTC+1. When do I land, London time?").contains("**06:35 the next day**"))
        // The gap given in words, either way round.
        assertTrue(text("Our train leaves Madrid at 07:00 and takes 11 hours to Lisbon. Lisbon is one hour behind Madrid. When do we arrive, Lisbon time?").contains("**17:00**"))
        assertTrue(text("Leaving Sydney at 21:45, the flight to Auckland takes 3 hours 5 minutes. Auckland is 2 hours ahead of Sydney. When do we touch down, Auckland time?").contains("**02:50 the next day**"))
        assertTrue(text("Tokyo is 8 hours ahead of London. My flight leaves London at 13:00 and takes 11 hours 50 minutes. When do I land?").contains("**08:50 the next day**"))
        // A landing time and the departure asked counts back: 18:30 − 13 h = 05:30 Singapore, 22:30 London the day before.
        assertTrue(text("London is UTC+1 and Singapore is UTC+8. I land in Singapore at 18:30 after a 13 h flight. What time did I leave London?").contains("**22:30 the day before**"))
        // One offset, city names alone, or a second leg: not guessed.
        assertNull(solve("My flight leaves at 23:50 local time and takes 13 h 45 min. What time is it at home (UTC+1) when I land?"))
        assertNull(solve("My flight leaves Paris at 10:00 and takes 8 hours. What time do I land in New York?"))
        assertNull(solve("I'm flying from London to Bangkok, leaving at 21:00 London time; the flight takes 11 hours 20 minutes and there's a 2-hour layover in Doha. Bangkok is 6 hours ahead. When do I land?"))
        assertNull(solve("My flight leaves Singapore at 23:50 and takes 13 h 45 min to London. Singapore is UTC+8 and London is UTC+1. I then take a 2 hour train. What time do I reach Oxford?"))
    }

    @Test fun everyNumberMustBeUsedAndTheAskingClauseIsJudged() {
        // A number the sum left out keeps the answer a lead.
        assertFalse(solve("1 euro is 38 lira. I paid 860 lira for the taxi and 200 lira for lunch. How much in euros?")!!.complete)
        assertFalse(solve("I've got 250 euros left before flying home. The airport kiosk gives 0.84 pounds per euro and charges a £3 fee; the bus into town is number 22. How much do I get?")!!.complete)
        // Scene-setting words in a declarative sentence do not make a fully answered question a lead.
        assertTrue(solve("A cake for 8 people needs 200 g of brown sugar. How much for 6 people?")!!.complete)
        assertTrue(solve("The night bus leaves at 22:30 and takes 9 hours. What time does it arrive?")!!.complete)
        assertTrue(solve("It's 95°F. What is that?")!!.complete)
        // The asking clause must ask for what was worked out.
        assertFalse(solve("My baby has a fever of 39°C. What do I do?")!!.complete)
        assertFalse(solve("How much is 2,000,000 dong in dollars at 25,400 dong per dollar, and is the exchange office at the airport any good?")!!.complete)
    }

    @Test fun aWorkedOutTripLengthIsCarriedToTheClockTime() {
        // 420 ÷ 140 = 3 h, plus two 10-minute stops: 8:15 + 3 h 20 min = 11:35. The stated stops replace the usual allowance.
        val train = solve("train leaves at 8:15, the trip is 420 km and it averages 140 km/h while moving, plus two 10-minute stops. what time do we get in?")!!
        assertTrue(train.text, "**3 h** of moving time. With 20 min of stops it takes **3 h 20 min**." in train.text)
        assertTrue(train.text, "08:15 plus 3 h 20 min is **11:35**" in train.text)
        assertTrue(train.complete)
        assertFalse(train.text.contains("10-minute breaks each hour"))
        assertTrue(text("Our bus leaves at 6:45 and the road is 300 km; it averages 75 km/h and makes one 30-minute stop. When do we arrive?").contains("06:45 plus 4 h 30 min is **11:15**"))
        assertTrue(text("We set off at 9:00 to cycle 60 km at 15 km/h and plan a 45 minute lunch break. What time will we arrive?").contains("**13:45**"))
        assertTrue(text("Driving 240 miles at 60 mph, leaving at 7:30 am with two 15-minute breaks. When do we get there?").contains("**12:00**"))
        assertTrue(text("The train leaves at 22:10, covers 630 km at 90 km/h and stops twice for 10 minutes. When does it arrive?").contains("**05:30 the next day**"))
        // The day named with the departure is kept.
        assertTrue(text("The ferry leaves at 14:00 on Friday, the crossing is 180 km at 30 km/h, with a 20-minute stop at an island. What time do we get in?").contains("**20:20 on Friday**"))
        // A known end counts back: 17:00 − 3 h 30 min.
        val back = solve("We must be back at the car by 17:00. The walk is 12 km at 4 km/h with a 30-minute rest. When do we need to start?")!!
        assertTrue(back.text, "start by **13:30**" in back.text)
        assertTrue(back.complete)
        // A span that is not a stop is another leg the sum would leave out, and a time with no role is not a start.
        assertFalse(solve("train leaves at 8:15, the trip is 420 km and it averages 140 km/h, then a 30-minute bus to the hotel. what time do we get in?")!!.complete)
        assertFalse(solve("We leave at 8:00, drive 200 km at 80 km/h and have a 1 hour meeting before heading back. When are we back?")!!.complete)
        assertFalse(solve("I need to walk 26 km at 4 km/h; it's 10:00 and there are 7 hours of daylight left. When will I arrive?")!!.complete)
        // Without stated stops the moving time is carried and says so; the plain time-plus-span sum does not answer beside it.
        val plain = solve("The hike is 12 km at 4 km/h. We leave at 9:00. When do we arrive?")!!
        assertTrue(plain.text, plain.text.endsWith("09:00 plus 3 h is **12:00** without stops."))
        assertTrue(plain.complete)
    }

    @Test fun naismithReadsTheRulesOwnConstantsAndTheStops() {
        // 18 ÷ 5 = 3 h 36 min, 900 ÷ 600 = 1 h 30 min, plus 45 min for lunch: 7:30 + 5 h 51 min = 13:21.
        val hike = solve("We're planning an 18 km hike with 900 m of total climbing. Using Naismith's rule (1 hour per 5 km plus 1 hour per 600 m of ascent), how long should it take? If we start at 7:30 and stop 45 minutes for lunch, when do we finish?")!!
        assertTrue(hike.text, "About **5 h 6 min**" in hike.text && "With 45 min of stops: **5 h 51 min**" in hike.text && "07:30 plus 5 h 51 min is **13:21**" in hike.text)
        assertTrue(hike.complete)
        // Other constants: 16 ÷ 4 = 4 h, 900 ÷ 450 = 2 h; and 30 minutes per 300 m is 600 m an hour.
        val langmuir = solve("Naismith's rule with 1 hour per 4 km plus 1 hour per 450 m of climb: a 16 km walk with 900 m of ascent, how long?")!!
        assertTrue(langmuir.text, "About **6 h**" in langmuir.text && "(1 hour per 4 km, plus 1 hour per 450 m of ascent)" in langmuir.text)
        assertTrue(langmuir.complete)
        assertTrue(solve("How long is a 20 km hike with 600 m of climbing by Naismith's rule (1 hour for every 5 km and 1 hour for every 600 m of ascent)?")!!.let { it.complete && "About **5 h**" in it.text })
        assertTrue(solve("Using Naismith's rule (1 hour per 5 km plus 30 minutes per 300 m of ascent), how long is 15 km with 600 m of climb?")!!.let { it.complete && "About **4 h**" in it.text })
        // The rule stated before the walk: its 5 km and 600 m are not the walk's.
        assertTrue(text("Naismith's rule (1 hour per 5 km plus 1 hour per 600 m of ascent): how long for 10 km with 300 m of climbing?").contains("About **2 h 30 min**"))
        // The time asked for the finish counts as asking how long; the stop is added: 2 h 30 min + 30 min from 8:00.
        val finish = solve("We start the 10 km hike with 300 m of climbing at 8:00 and take a 30 minute break. When do we finish, by Naismith's rule?")!!
        assertTrue(finish.text, "08:00 plus 3 h is **11:00**" in finish.text)
        assertTrue(finish.complete)
        assertTrue(text("A 24 km trail with 1,200 m of ascent. By Naismith's rule, how long will it take?").contains("About **6 h 48 min**"))
        // A safety question beside the sum keeps it a lead, and a time with no role is not a start.
        assertFalse(solve("Hike 12 km with 400 m of climbing. We start at 9:00. Is it safe to go alone and when will we finish?")!!.complete)
        assertFalse(solve("The 10 km hike has 300 m of climbing; lunch is at 12:00. When do we finish?")!!.complete)
    }

    @Test fun twoExchangeOffersAreComparedNetOfTheirFees() {
        // Kiosk: 250 × 0.84 − 3 = 207; bank: 250 × 0.86 = 215; 8 more at the bank.
        val bank = solve("I've got 250 euros left before flying home. The airport kiosk gives 0.84 pounds per euro and charges a £3 fee; my bank in town gives 0.86 with no fee. How much more would I get at the bank?")!!
        assertTrue(bank.text, "**207.00 pounds**" in bank.text && "At the bank's 0.86 with no fee: 250 euros × 0.86 = **215.00 pounds**" in bank.text)
        assertTrue(bank.text, "The bank gives **8.00 pounds more** than the airport kiosk (215.00 against 207.00)." in bank.text)
        assertTrue(bank.complete)
        assertTrue(solve("250 euros, kiosk 0.84 pounds per euro and a £3 fee, bank 0.86 no fee, how much more at the bank?")!!.let { it.complete && "**8.00 pounds more**" in it.text })
        // A fee in the money received, a percentage, a second rate named with its money, and a card instead of a place.
        assertTrue(text("I have 500 dollars. The airport counter gives 36 baht per dollar with a 150 baht fee; the bank in town gives 36.8 with no fee. What's the difference?").contains("**550.00 baht more** than the airport counter"))
        val zloty = solve("Changing 300 euros: the kiosk offers 4.2 zloty per euro and takes 3% commission, while the bank offers 4.1 zloty with no fee. Which gives me more?")!!
        assertTrue(zloty.text, "**1,222.20 zloty**" in zloty.text && "**1,230.00 zloty**" in zloty.text && "The bank gives **7.80 zloty more** than the kiosk" in zloty.text)
        assertTrue(zloty.complete)
        assertTrue(text("The hotel desk changes money at 1,450 won per dollar with a 5,000 won fee; the bank gives 1,480 with no fee. How much more do I get at the bank for 200 dollars?").contains("**11,000.00 won more** than the hotel desk"))
        assertTrue(text("1 pound is 1.25 dollars at the office, which charges a £5 fee. My card gives 1.27 with no fee. How much more do I get changing 400 pounds with the card?").contains("The card gives **14.25 dollars more** than the office (508.00 against 493.75)."))
        // "Opens at 9" and "2 of us" are not rates; a third offer or two fees of one kind are not guessed at.
        assertFalse(solve("I have 300 euros. The bank gives 4.2 zloty per euro and opens at 9. How many zloty do I get?")!!.let { it.complete || "At the" in it.text })
        assertFalse(solve("I have 300 euros. The bank gives 4.2 zloty per euro and 2 of us are going. How many zloty?")!!.complete)
        assertNull(solve("The kiosk gives 0.84 pounds per euro; the bank gives 0.86 and the hotel gives 0.80. How many pounds for 250 euros at the bank?"))
        assertNull(solve("1 euro is 38 lira. The office takes 3% and the bank takes 5%. How many lira for 200 euros?"))
    }

    @Test fun aBillWithPercentagesThenFlatChargesPerUnit() {
        // 180 × 4 = 720; × 0.85 = 612; × 1.12 = 685.44; plus 3.50 × 2 × 4 = 28: 713.44.
        val hotel = solve("Our hotel is €180 a night for 4 nights. We get 15% off the room rate, then 12% tax is added to the discounted price, plus a city tax of €3.50 per person per night for the two of us. What's the total?")!!
        assertTrue(hotel.text, "€180.00 × 4 nights = **€720.00**" in hotel.text && "**€612.00**" in hotel.text && "**€685.44**" in hotel.text)
        assertTrue(hotel.text, "City tax: €3.50 × 2 people × 4 nights = **€28.00**" in hotel.text && "Total: €685.44 + €28.00 = **€713.44**" in hotel.text)
        assertTrue(hotel.complete)
        assertTrue(text("Our room is €120 a night for 3 nights with a 10% discount, then 8% tax on the discounted price. Total?").contains("**€349.92**"))
        assertTrue(text("A hostel bed is 25 euros per person per night. How much for 3 people for 2 nights?").contains("25.00 euros × 3 people × 2 nights = **150.00 euros**"))
        // A charge per stay is added once; rooms multiply a nightly price but not a per-person charge.
        assertTrue(text("The apartment is $95 a night for 5 nights plus a cleaning fee of $40 per stay. Total?").contains("Total: $475.00 + $40.00 = **$515.00**"))
        val rooms = solve("€60 a night for 2 rooms for 3 nights, plus a city tax of €2 per person per night for the four of us. How much is it?")!!
        assertTrue(rooms.text, "€60.00 × 3 nights × 2 rooms = **€360.00**" in rooms.text && "City tax: €2.00 × 4 people × 3 nights = **€24.00**" in rooms.text && "**€384.00**" in rooms.text)
        assertTrue(rooms.complete)
        assertTrue(text("A campsite charges 12 euros per person per night plus 5 euros per tent per night. How much for 3 people in 2 tents for 4 nights?").contains("**184.00 euros**"))
        val resort = solve("A room is £80 a night. How much for 4 nights with 20% off and a £10 per night resort fee?")!!
        assertTrue(resort.text, "**£256.00**" in resort.text && "Resort fee: £10.00 × 4 nights = **£40.00**" in resort.text && "**£296.00**" in resort.text)
        assertTrue(resort.complete)
        // A second price with no word adding it, or a percentage on everything, is left to research.
        assertNull(solve("The hotel is €100 a night for 3 nights. Breakfast is €12 per person per day for 2 of us. Total?"))
        assertNull(solve("Room €100 a night for 3 nights, 10% tax on everything plus €5 city tax per night. Total?"))
    }

    @Test fun provisionsForAGroupOverTheDaysInPacks() {
        // 14 × 2 × 3 = 84 eggs = 7 boxes of 12; 84 cups ÷ 30 a bag = 2.8, so 3 bags.
        val breakfast = solve("cooking breakfast for 14 people for 3 mornings. everyone has 2 eggs and 2 cups of coffee each morning. eggs come in boxes of 12 and a 250 g bag of coffee makes 30 cups. how many boxes of eggs and bags of coffee do i need?")!!
        assertTrue(breakfast.text, "Eggs: 14 people × 2 × 3 mornings = **84 eggs**; 84 ÷ 12 = 7, so **7 boxes**." in breakfast.text)
        assertTrue(breakfast.text, "Coffee: 14 people × 2 cups × 3 mornings = **84 cups of coffee**; 84 ÷ 30 = 2.8, which rounds up to **3 bags**." in breakfast.text)
        assertTrue(breakfast.complete)
        assertTrue(text("8 of us for 2 mornings, each person has 3 pancakes and 1 cup of juice. A box of mix makes 20 pancakes and a carton of juice gives 8 cups. How many boxes and cartons?").let { "**3 boxes**" in it && "**2 cartons**" in it })
        // Grams against grams: 6 × 125 × 4 = 3,000 g in 500 g packets.
        assertTrue(text("Cooking dinner for 6 people for 4 nights: everyone eats 125 g of pasta each night and pasta comes in 500 g packets. How many packets?").contains("**3,000 g of pasta**; 3,000 ÷ 500 = 6, so **6 packets**"))
        assertTrue(text("4 hikers for 5 days, 2 energy bars each per day, bars come in boxes of 12. How many boxes do we need?").contains("**40 bars**; 40 ÷ 12 = 3.33, which rounds up to **4 boxes**"))
        assertTrue(text("10 campers, 3 breakfasts, each person has 2 slices of bread; a loaf has 20 slices. How many loaves?").contains("**60 slices of bread**; 60 ÷ 20 = 3, so **3 loaves**"))
        assertTrue(text("12 people for 2 evenings, 2 beers each per evening, and beer is sold in packs of 6. How many packs?").contains("**48 beers**; 48 ÷ 6 = 8, so **8 packs**"))
        assertTrue(solve("14 people, 3 mornings, 2 eggs each, how many eggs?")!!.let { it.complete && "**84 eggs**" in it.text })
        // Money per person is a bill, and a number the sum left out keeps it a lead.
        assertFalse(solve("Each of us has 2 bags; how many bags for 5 people on 3 flights?")!!.complete)
        assertNull(solve("How many eggs do 14 people eat in 3 mornings at 2 each, and how many people live in Lagos?"))
        assertFalse(solve("A tour costs €40 per person. How much for 3 people?")!!.text.contains("Buy a little extra"))
    }

    @Test fun theLandingDayIsNamedWhenTheDepartureDayIs() {
        // 10:00 + 12 h 30 min = 22:30 Sydney time; Santiago is 14 h behind: 08:30, still Tuesday.
        val santiago = solve("My flight leaves Sydney at 10:00 on Tuesday and takes 12 h 30 min to get to Santiago. Sydney is on UTC+11 and Santiago on UTC-3 right now. What day and local time do I land?")!!
        assertTrue(santiago.text, "Santiago is 14 h behind Sydney (UTC−3 against UTC+11), so you land at **08:30 on Tuesday**, Santiago time." in santiago.text)
        assertTrue(santiago.complete)
        assertTrue(text("My flight leaves Tokyo at 17:30 on Sunday and takes 11 hours to London. Tokyo is UTC+9 and London is UTC+1. What day and local time do I land?").contains("**20:30 on Sunday**"))
        assertTrue(text("Leaving New York at 21:00 on Friday, the flight to Paris takes 7 h 15 min. New York is UTC-4 and Paris UTC+2. Which day and what local time do I arrive?").contains("**10:15 on Saturday**"))
        assertTrue(text("I fly from Dubai (UTC+4) at 02:10 on Wednesday for 14 hours to Los Angeles (UTC-7). What local time do I land?").contains("**05:10 on Wednesday**"))
        // A plain sum keeps the day too.
        assertTrue(solve("The night bus leaves at 23:30 on Saturday and takes 9 hours. What day and time do we arrive?")!!.let { it.complete && "23:30 plus 9 h is **08:30 on Sunday**" in it.text })
        // Another question beside the landing keeps it a lead.
        assertFalse(solve("My flight leaves Sydney at 10:00 on Tuesday and takes 12 h 30 min to get to Santiago. Sydney is on UTC+11 and Santiago on UTC-3 right now. What day do I land and is the airport far from town?")!!.complete)
    }
}
