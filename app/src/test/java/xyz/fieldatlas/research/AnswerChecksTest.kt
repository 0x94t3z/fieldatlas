package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Failures reported from an offline emulator test of 1.2.0, reduced to the checks that catch them. */
class AnswerChecksTest {
    private fun file(name: String, text: String) = Evidence("attachment:$name", "$name:p1:0", name, "Attached file", text, 0.0)

    private val revisionA = file("Helios_Pump_Revision_A.txt",
        "Helios pump model HX-44 revision A. Applies only to serial numbers 1000 through 1999. Use filter K-7 and connector M2. " +
            "Service interval: 400 operating hours. This revision does not apply to serial numbers 2000 or greater.")
    private val revisionB = file("Helios_Pump_Revision_B.txt",
        "Helios pump model HX-44 revision B. Applies only to serial numbers 2000 through 2999. Use filter K-9 and connector M5. " +
            "Service interval: 600 operating hours.")

    @Test fun aCitationMovesToTheRevisionItsDetailsComeFrom() {
        val answer = "Your Helios HX-44 serial number (2345) falls within the range covered by Revision B [1], which applies to 2000–2999.\n" +
            "Since your serial is 2345, Revision A does not apply [2]."
        assertEquals(
            "Your Helios HX-44 serial number (2345) falls within the range covered by Revision B [S2], which applies to 2000–2999.\n" +
                "Since your serial is 2345, Revision A does not apply [S1].",
            AnswerChecks.repairCitations(answer, listOf(revisionA, revisionB)),
        )
    }

    @Test fun correctOrMixedCitationsStayPut() {
        val correct = "Revision B uses filter K-9 [S2]. Revision A uses K-7 [S1]."
        assertEquals(correct, AnswerChecks.repairCitations(correct, listOf(revisionA, revisionB)))
        val both = "Unlike Revision A with K-7, Revision B uses K-9 [S1]."
        assertEquals(both, AnswerChecks.repairCitations(both, listOf(revisionA, revisionB)))
        val shared = "Both revisions cover the HX-44 pump [S1]."
        assertEquals(shared, AnswerChecks.repairCitations(shared, listOf(revisionA, revisionB)))
        val plain = "The plan to service it is sound [S1]."
        assertEquals(plain, AnswerChecks.repairCitations(plain, listOf(revisionA, revisionB)))
    }

    @Test fun aCitationBeforeItsClaimIsCheckedAgainstTheRestOfTheSentence() {
        val answer = "Your unit is Revision B. According to [2], this revision requires filter K-7 and connector M2."
        assertEquals("Your unit is Revision B. According to [S1], this revision requires filter K-7 and connector M2.",
            AnswerChecks.repairCitations(answer, listOf(revisionA, revisionB)))
        val right = "According to [S2], use filter K-9 and connector M5."
        assertEquals(right, AnswerChecks.repairCitations(right, listOf(revisionA, revisionB)))
    }

    @Test fun aSourceIdWrittenAsProseBecomesACitation() {
        assertEquals("Do not apply the parameters from [S1] as they are invalid.",
            AnswerText.finalized("Do not apply the parameters from S1 as they are invalid.", 2))
        assertEquals("The Tesla Model S1 is not a source.", AnswerText.finalized("The Tesla Model S1 is not a source.", 2))
    }

    @Test fun aWrongValueStaysOnTheSourceItCitedInsteadOfLookingSupported() {
        // Revision B's interval is 600; moving this to Revision A would make the false 400 look sourced.
        val wrong = "Revision B service interval: 400 hours [S2]."
        assertEquals(wrong, AnswerChecks.repairCitations(wrong, listOf(revisionA, revisionB)))
        for (list in listOf(
            "Revision B:\n- Service interval: 400 hours [S2].",
            "**Revision B**\n\n- Filter: K-9 [S2]\n- Service interval: 400 hours [S2].",
            "### Revision B\n1. Service interval: 400 hours [S2]",
        )) assertEquals(list, AnswerChecks.repairCitations(list, listOf(revisionA, revisionB)))
        // A list whose lead names no revision still gets its citation checked.
        assertEquals("Details:\n- Filter K-9 and connector M5 [S2].",
            AnswerChecks.repairCitations("Details:\n- Filter K-9 and connector M5 [S1].", listOf(revisionA, revisionB)))
        val label = "Revision B: filter K-7 [S2]."
        assertEquals(label, AnswerChecks.repairCitations(label, listOf(revisionA, revisionB)))
    }

    @Test fun onlyPasswordsPhoneNumbersAndEmailsAreReportedMissing() {
        val address = file("notes.txt", "Cedar Lodge is at 12 Oak Lane. Reception closes at 22:30.")
        assertNull(AnswerChecks.missingItem("What is the address of Cedar Lodge?", listOf(address)))
        val wifiOnly = file("notes.txt", "Cedar Lodge has free Wi-Fi in the lobby.")
        assertEquals("the Wi-Fi password at Cedar Lodge",
            AnswerChecks.missingItem("What is the Wi-Fi password at Cedar Lodge?", listOf(wifiOnly)))
        val phone = file("notes.txt", "Cedar Lodge front desk: +49 30 1234 567.")
        assertNull(AnswerChecks.missingItem("What is the phone number for Cedar Lodge?", listOf(phone)))
        assertEquals("the email address", AnswerChecks.missingItem("What is the email address?", listOf(address)))
    }

    private val travel = file("Orion_Travel_Plan.txt",
        "Late shuttle from Pine Station to Cedar Lodge: last departure 21:30. Cedar Lodge reception closes at 22:30. " +
            "There is no after-hours entry or key box. Station Hotel has a front desk open 24 hours. It does not arrange transport.")

    @Test fun aTwelveHourTimeIsCorrectedToTheStatedTwentyFourHourTime() {
        assertEquals("Late shuttle service ends at 9:30 PM (21:30) [S1].",
            AnswerChecks.repairTimes("Late shuttle service ends at 10:30 PM (21:30) [S1].", listOf(travel)))
        assertEquals("Reception closes at 22:30 (10:30 PM).",
            AnswerChecks.repairTimes("Reception closes at 22:30 (11:30 PM).", listOf(travel)))
        val right = "Reception closes at 10:30 PM (22:30)."
        assertEquals(right, AnswerChecks.repairTimes(right, listOf(travel)))
        val unstated = "Breakfast starts at 8:00 AM (07:00)."
        assertEquals(unstated, AnswerChecks.repairTimes(unstated, listOf(travel)))
    }

    @Test fun aRequestedTwelveHourTimeIsAddedByTheApp() {
        val ask = "When does the last shuttle leave? Give the time in 12-hour format too."
        assertEquals("The last shuttle leaves at 21:30 (9:30 PM) [S1]; reception closes at 22:30 (10:30 PM).",
            AnswerChecks.addTwelveHour("The last shuttle leaves at 21:30 [S1]; reception closes at 22:30.", ask))
        val done = "It leaves at 21:30 (9:30 PM), or 9:30 PM (21:30)."
        assertEquals(done, AnswerChecks.addTwelveHour(done, ask))
        assertEquals("It leaves at 21:30.", AnswerChecks.addTwelveHour("It leaves at 21:30.", "When does the last shuttle leave?"))
        val quote = "The plan says \"last departure 21:30\"."
        assertEquals(quote, AnswerChecks.addTwelveHour(quote, ask))
        assertEquals("Open 09:00 (9:00 AM) to 00:15 (12:15 AM).",
            AnswerChecks.addTwelveHour("Open 09:00 to 00:15.", "What are the hours in AM/PM?"))
    }

    @Test fun aDecisionAboutFilesWithExceptionsGetsANote() {
        val garden = file("Garden_Irrigation_Policy.txt", "Rule 1. Water a plot when its soil moisture is below 30%. " +
            "Rule 2. Exception: never water a plot that has an active maintenance hold.")
        assertEquals("Check each decision against the file's exceptions (\"exception\" and \"hold\"): this model can miss one.",
            AnswerChecks.exceptionNote("Should East and West be watered today? Give a decision and reason for each.", listOf(garden)))
        assertNull(AnswerChecks.exceptionNote("What does Rule 1 say?", listOf(garden)))
        assertNull(AnswerChecks.exceptionNote("Which filter should I use for serial 2345?", listOf(revisionA, revisionB)))
    }

    @Test fun anItemTheFilesNeverMentionIsReportedMissing() {
        assertEquals("the Wi-Fi password at Cedar Lodge",
            AnswerChecks.missingItem("What is the Wi-Fi password at Cedar Lodge? Use my saved travel notes.", listOf(travel)))
        assertEquals("the phone number for Cedar Lodge",
            AnswerChecks.missingItem("What is the phone number for Cedar Lodge?", listOf(travel)))
    }

    @Test fun anItemPresentUnderAnotherWordIsNotReportedMissing() {
        val withCode = file("notes.txt", "Cedar Lodge network: CedarGuest, passcode pine-2026.")
        assertNull(AnswerChecks.missingItem("What is the Wi-Fi password at Cedar Lodge?", listOf(withCode)))
        val withPrice = file("tour.txt", "Harbour tour: €40 per adult, departs 10:00.")
        assertNull(AnswerChecks.missingItem("What is the cost of the harbour tour?", listOf(withPrice)))
        assertNull(AnswerChecks.missingItem("What is the last departure time for the shuttle?", listOf(travel)))
        assertNull(AnswerChecks.missingItem("Should East and West be watered today?", listOf(travel)))
    }

    @Test fun adviceThatNeedsAConnectionIsRemoved() {
        val answer = "Vedis is a fully vegan Indian restaurant in Mitte [S1]. Check TripAdvisor or Yelp for the latest reviews. " +
            "Kopps serves vegan brunch [S2].\n\n- Visit their website to confirm opening hours.\n- Ask staff about the daily menu."
        val out = AnswerChecks.removeOnlineAdvice(answer, "Tell me the best vegan restaurants in Berlin")
        assertEquals("Vedis is a fully vegan Indian restaurant in Mitte [S1]. Kopps serves vegan brunch [S2].\n\n- Ask staff about the daily menu.", out)
    }

    @Test fun mentionsOfOnlineThingsAndOfflineAdviceStay() {
        val answer = "Google Maps cannot plan transit routes without a connection [S1]. Use an offline maps app such as OsmAnd to navigate. " +
            "You can search the saved guide for the station name."
        assertEquals(answer, AnswerChecks.removeOnlineAdvice(answer, "How do I get around Tokyo without data?"))
    }

    @Test fun questionsAboutOnlineServicesAreLeftAlone() {
        val answer = "You can check the official website for visa fees [S1]."
        assertEquals(answer, AnswerChecks.removeOnlineAdvice(answer, "Which website lists the visa fees?"))
    }

    @Test fun anAnswerWithoutOnlineAdviceIsUnchanged() {
        val answer = "1. Keep calm [S1].\n2. Remove rings and watches before swelling starts [S1]."
        assertEquals(answer, AnswerChecks.removeOnlineAdvice(answer, "A snake bit my friend. What do we do?"))
    }

    @Test fun deferringToLaterWhenOnlineStays() {
        val answer = "I can't know today's news offline. Ask about the topic itself, or check a current source when you're online."
        assertEquals(answer, AnswerChecks.removeOnlineAdvice(answer, "What study was published today?"))
    }

    @Test fun theAnswerDoesNotNarrateThatThePersonIsOffline() {
        val q = "How do I get from Porto airport to the old town?"
        assertEquals("To get from Porto Airport to the old town, follow these steps:\n* Take the metro purple line [S1].",
            AnswerChecks.removeOfflineNarration("To get from Porto Airport to the old town without internet access, follow these steps:\n* Take the metro purple line [S1].", q))
        assertEquals("**How to Verify Locally:**\nCompare the blob count in the block header [S2].",
            AnswerChecks.removeOfflineNarration("**How to Verify Locally Without Internet:**\nSince you have no internet access, you cannot check official EIP-4844 status online. Compare the blob count in the block header [S2].", "What is EIP-4844?"))
        assertEquals("Validators build blocks [S1]. Participants rely on local mempools.",
            AnswerChecks.removeOfflineNarration("Validators build blocks [S1]. Without internet access, participants rely on local mempools.", "How are blocks built?"))
        assertEquals("Check the wattage printed on the bulb's packaging.",
            AnswerChecks.removeOfflineNarration("Since you have no internet access, check the wattage printed on the bulb's packaging.", "How do LED bulbs differ?"))
        // A condition is information, and advice after "so" is kept.
        val condition = "Without data, the eSIM won't activate."
        assertEquals(condition, AnswerChecks.removeOfflineNarration(condition, "How do I set up my phone in Japan?"))
        assertEquals("Ask at the hotel reception.",
            AnswerChecks.removeOfflineNarration("Without internet you can't check bus times, so ask at the hotel reception.", "When is the next bus?"))
        // Statements about things, not the person, keep their meaning.
        val about = "Google Maps cannot plan transit routes without a connection [S1]. **Maps.me works without internet** once a region is saved."
        assertEquals(about, AnswerChecks.removeOfflineNarration(about, "How do I get around Tokyo?"))
        val asked = "To use the app without internet access, download the region first."
        assertEquals(asked, AnswerChecks.removeOfflineNarration(asked, "Does the app work without internet?"))
    }

    @Test fun anOfflineOptionInTheSameSentenceStays() {
        assertEquals("You can buy tickets at the station.",
            AnswerChecks.removeOnlineAdvice("You can buy tickets at the station or online.", "How do I get a train ticket in Porto?"))
        val data = "Buy a prepaid SIM at the airport kiosk; check the operator's website for data plans."
        assertEquals(data, AnswerChecks.removeOnlineAdvice(data, "How do I get mobile data in Japan?"))
    }

    @Test fun theModelsOwnExplanationCarriesNoAmountOfMedicine() {
        val answer = """Both relieve pain and fever. Ibuprofen also reduces swelling, while paracetamol is gentler on the stomach.
- The usual amount is 10 g (150 mg/kg) per day.
- Take it with food if your stomach is sensitive.
1. Take 2 tablets.

From saved sources
- "An adult usually takes 500 mg to 1 g at a time." [S1]"""
        val out = AnswerChecks.removeDoses(answer, "What is the difference between ibuprofen and paracetamol?")
        assertFalse(out, "150 mg/kg" in out || "2 tablets" in out)
        assertTrue(out, "Ibuprofen also reduces swelling" in out && "Take it with food" in out)
        assertTrue("an exact quotation of a source keeps its words", "500 mg to 1 g" in out)
        assertTrue(out, out.endsWith(AnswerChecks.DOSE_NOTE))
        // Grams of rice are not a dose, and an answer without an amount is left as it was.
        val recipe = "Use 300 g of rice for four people."
        assertEquals(recipe, AnswerChecks.removeDoses(recipe, "How much rice for four people?"))
        assertEquals("Rest and drink water.", AnswerChecks.removeDoses("Rest and drink water.", "What helps a cold?"))
        assertFalse("a 5G network is no dose", AnswerChecks.removeDoses("A 5G network is faster.", "How fast is 5G?").contains("_Amounts"))
    }

    @Test fun aHomeRemedyAmountTheSourcesDoNotGiveIsRemoved() {
        // The 2B model gave a rehydration drink one sixth of the sugar it needs and credited the WHO.
        val ort = file("ort.txt", "Oral rehydration therapy is a treatment for dehydration: the sick person drinks water mixed with sugar and salt. They don't stop eating food.")
        val answer = "You can make a rehydration drink at home. Add one level teaspoon of sugar per litre of water and half a level teaspoon of salt. Keep eating your usual food."
        assertEquals("You can make a rehydration drink at home. Keep eating your usual food.\n\n" + AnswerChecks.RECIPE_NOTE,
            AnswerChecks.removeDoses(answer, "How do I make a rehydration drink for diarrhoea?", listOf(ort)))
        // The amount the source states stays, in either spelling.
        val who = file("who.txt", "Mix 6 level teaspoons of sugar and half a level teaspoon of salt in one litre of clean water.")
        val right = "Mix six teaspoons of sugar and ½ teaspoon of salt in 1 litre of water."
        assertEquals(right, AnswerChecks.removeDoses(right, "How do I make a rehydration drink?", listOf(who)))
        // Outside a health context a kitchen measure is a recipe, not a dose.
        val cake = "Add 2 cups of sugar and a pinch of salt."
        assertEquals(cake, AnswerChecks.removeDoses(cake, "How do I bake a sponge cake?", listOf(ort)))
    }

    private val heatStroke = Evidence("emergency:heat-illness", "emergency:heat-illness:3", "Heat exhaustion and heat stroke: Heat Stroke", "https://www.weather.gov/safety/heat-illness",
        "Heat Stroke\n- Symptoms: Throbbing headache; confusion; slurred speech; nausea; dizziness; body temperature above 103°F; hot, red, dry or damp skin; rapid and strong pulse; fainting; loss of consciousness.\n" +
            "- First Aid: Call 911 or get the victim to a hospital immediately. Heat stroke is a severe medical emergency. Delay can be fatal. Move the victim to a cooler, preferably air-conditioned, environment. " +
            "Reduce body temperature with cool cloths or bath. Use fan if heat index temperatures are below the high 90s. A fan can make you hotter at higher temperatures. Do NOT give fluids.", 1.0)
    private val heatExhaustion = Evidence("emergency:heat-illness", "emergency:heat-illness:2", "Heat exhaustion and heat stroke: Heat Exhaustion", "https://www.weather.gov/safety/heat-illness",
        "Heat Exhaustion\n- Symptoms: Heavy sweating; weakness or tiredness; cool, pale, clammy skin; fast, weak pulse; muscle cramps; dizziness, nausea or vomiting; headache; fainting.\n" +
            "- First Aid: Move person to a cooler environment, preferably a well air-conditioned room. Loosen clothing. Apply cool, wet cloths or have person sit in a cool bath. Offer sips of water. " +
            "Seek immediate medical attention if the person vomits, symptoms worsen or last longer than 1 hour", 1.0)

    @Test fun aWarningTheGuideNeverGivesIsRemovedAndStepsRenumbered() {
        // Under the heat guide the model wrote "do not give food or drink unless they can swallow", cited to a section that says no such thing.
        val answer = "1. Call 911 or get them to a hospital immediately [S1].\n2. Move them to a cooler place [S1].\n" +
            "3. Do not give food or drink unless they are able to swallow safely [S2].\n4. Do not give fluids [S1].\n5. Cool the skin with wet cloths [S1]."
        assertEquals("1. Call 911 or get them to a hospital immediately [S1].\n2. Move them to a cooler place [S1].\n3. Do not give fluids [S1].\n4. Cool the skin with wet cloths [S1].",
            AnswerChecks.removeUnsupportedWarnings(answer, listOf(heatStroke, heatExhaustion)))
    }

    @Test fun aWarningInsideAParagraphGoesAloneAndTheGuidesOwnStay() {
        val answer = "Move them somewhere cooler and cool the skin [S1]. Never give them anything to eat until a doctor has seen them [S1]. Do not give fluids [S1]."
        assertEquals("Move them somewhere cooler and cool the skin [S1]. Do not give fluids [S1].", AnswerChecks.removeUnsupportedWarnings(answer, listOf(heatStroke)))
        // Uncited, a warning is checked against every source, and one the guide gives in other words stays.
        assertEquals("Don't give the person fluids.", AnswerChecks.removeUnsupportedWarnings("Don't give the person fluids.", listOf(heatStroke, heatExhaustion)))
        val seizure = file("seizures.txt", "Don't put anything in their mouth. This can hurt their teeth or jaw.")
        assertEquals("Do not put anything in their mouth [S1].", AnswerChecks.removeUnsupportedWarnings("Do not put anything in their mouth [S1].", listOf(seizure)))
        val none = "1. Move them to a cooler place [S1].\n2. Cool the skin [S1]."
        assertEquals(none, AnswerChecks.removeUnsupportedWarnings(none, listOf(heatStroke)))
        assertEquals(answer, AnswerChecks.removeUnsupportedWarnings(answer, emptyList()))
    }

    @Test fun aWrongStepResultIsRecomputedAndMarked() {
        assertEquals("3 h + 20 min = 3 h 20 min (corrected on this phone)", AnswerChecks.checkArithmetic("3 h + 20 min = 3 h 30 min"))
        assertEquals("612 × 1.12 = 685.44 (corrected on this phone)", AnswerChecks.checkArithmetic("612 × 1.12 = 672.20"))
        assertEquals("€720 × 15% = €108 (corrected on this phone).", AnswerChecks.checkArithmetic("€720 × 15% = €100."))
        assertEquals("- Fare: $1,200 x 3 = $3,600 (corrected on this phone) for the group", AnswerChecks.checkArithmetic("- Fare: $1,200 x 3 = $3,500 for the group"))
        assertEquals("18 ÷ 30 = 0.6 bags (corrected on this phone)", AnswerChecks.checkArithmetic("18 ÷ 30 = 1 bags"))
        assertEquals("09:40 + 25 min = 10:05 (corrected on this phone)", AnswerChecks.checkArithmetic("09:40 + 25 min = 10:15"))
        assertEquals("7:30 + 5 h 51 min = 13:21 (corrected on this phone)", AnswerChecks.checkArithmetic("7:30 + 5 h 51 min = 12:21"))
        assertEquals("250 euros * 0.84 = 210.00 pounds (corrected on this phone)", AnswerChecks.checkArithmetic("250 euros * 0.84 = 215.00 pounds"))
        assertEquals("210.00 - 3.00 = 207.00 (corrected on this phone)", AnswerChecks.checkArithmetic("210.00 - 3.00 = 205.00"))
        assertEquals("18 km ÷ 5 km/h = 3 h 36 min (corrected on this phone)", AnswerChecks.checkArithmetic("18 km ÷ 5 km/h = 2 h"))
    }

    @Test fun aCorrectChainAndRoundedFiguresAreLeftAlone() {
        val train = "Distance: 420 km\nSpeed: 140 km/h\n420 km ÷ 140 km/h = 3 h\n2 × 10 min = 20 min\n3 h + 20 min = 3 h 20 min\n" +
            "08:15 + 3 h 20 min = 11:35\nAnswer: 11:35"
        assertEquals(train, AnswerChecks.checkArithmetic(train))
        val hotel = "€180 × 4 = €720 (room)\n€720 × 0.85 = €612\n€612 × 1.12 = €685.44\n2 × €3.50 = €7.00\n4 × €7.00 = €28.00\n" +
            "€685.44 + €28.00 = €713.44\nAnswer: €713.44"
        assertEquals(hotel, AnswerChecks.checkArithmetic(hotel))
        // A figure rounded to the precision it is written at is right; a quotient is not rounded up.
        assertEquals("€612 × 1.12 = €685", AnswerChecks.checkArithmetic("€612 × 1.12 = €685"))
        assertEquals("860 ÷ 38 = 22.63 euros", AnswerChecks.checkArithmetic("860 ÷ 38 = 22.63 euros"))
        assertEquals("84 ÷ 12 = 7 boxes", AnswerChecks.checkArithmetic("84 ÷ 12 = 7 boxes"))
        // Prose with numbers, and a sum the app cannot check, stay as written.
        val prose = "The 4 nights at €180 come to €720 and the 12% tax applies after the discount."
        assertEquals(prose, AnswerChecks.checkArithmetic(prose))
        assertEquals("08:15 + 420 = 11:35", AnswerChecks.checkArithmetic("08:15 + 420 = 11:35"))
        // A longer chain is not checked from its second operator.
        assertEquals("2 × 4 nights × €3.50 = €28.00", AnswerChecks.checkArithmetic("2 × 4 nights × €3.50 = €28.00"))
        assertEquals("3 h 36 min + 1 h 30 min + 45 min = 5 h 51 min", AnswerChecks.checkArithmetic("3 h 36 min + 1 h 30 min + 45 min = 5 h 51 min"))
    }

    @Test fun aLinearChainCarriesTheCorrectionIntoTheAnswer() {
        val answer = "Room: €180 × 4 = €720\nDiscount: €720 × 0.85 = €612\nTax: €612 × 1.12 = €672.20\n**Answer:** €672.20 in total"
        assertEquals("Room: €180 × 4 = €720\nDiscount: €720 × 0.85 = €612\nTax: €612 × 1.12 = €685.44 (corrected on this phone)\n" +
            "**Answer:** €685.44 (corrected on this phone) in total", AnswerChecks.checkArithmetic(answer))
        // A later step that starts from the wrong figure is recomputed from the corrected one.
        val chain = "3 h + 20 min = 3 h 30 min\n08:15 + 3 h 30 min = 11:45\nAnswer: 11:45"
        assertEquals("3 h + 20 min = 3 h 20 min (corrected on this phone)\n08:15 + 3 h 20 min = 11:35 (corrected on this phone)\nAnswer: 11:35 (corrected on this phone)",
            AnswerChecks.checkArithmetic(chain))
    }

    @Test fun aBranchingWorkingOnlyNotesThatAStepWasCorrected() {
        val branched = "€180 × 4 = €720\n€720 × 0.85 = €612\n€612 × 1.12 = €672.20\n2 × €3.50 = €7.00\n€672.20 + €7.00 = €679.20\nAnswer: €679.20"
        assertEquals("€180 × 4 = €720\n€720 × 0.85 = €612\n€612 × 1.12 = €685.44 (corrected on this phone)\n2 × €3.50 = €7.00\n" +
            "€685.44 + €7.00 = €692.44 (corrected on this phone)\nAnswer: €679.20\n\n" + AnswerChecks.STEP_CORRECTED_NOTE,
            AnswerChecks.checkArithmetic(branched))
        // An answer figure that no written step produced cannot be re-derived either.
        val mental = "14 × 2 = 28\n28 × 3 = 84\n84 ÷ 12 = 7\n18 ÷ 30 = 1\nAnswer: 7 boxes and 2 bags"
        assertEquals("14 × 2 = 28\n28 × 3 = 84\n84 ÷ 12 = 7\n18 ÷ 30 = 0.6 (corrected on this phone)\nAnswer: 7 boxes and 2 bags\n\n" +
            AnswerChecks.STEP_CORRECTED_NOTE, AnswerChecks.checkArithmetic(mental))
    }

    private val book = xyz.fieldatlas.emergency.EmergencyData.guides(java.io.File("src/main/assets/emergency/guides.json").readText())
    private fun guideSections(id: String) = xyz.fieldatlas.emergency.EmergencyGuideMatch.sections(book.guides.single { it.id == id })

    @Test fun aStepNamingABodySiteOrMedicineTheGuideNeverMentionsIsRemoved() {
        // Scored 1/10 and 2/10 in the field: under the anaphylaxis guide the model wrote "give the
        // epipen in the arm" (auto-injectors go in the thigh) and cited MedlinePlus, which says no such thing.
        val anaphylaxis = guideSections("anaphylaxis")
        assertEquals("Call emergency services immediately [S1].",
            AnswerChecks.removeUnsupportedSteps("Call emergency services immediately [S1]. Give the epipen in the arm of the child if available [S1].", anaphylaxis))
        assertEquals("1. Call 911 [S1].\n2. Give the auto-injector injection right away [S1].",
            AnswerChecks.removeUnsupportedSteps("1. Call 911 [S1].\n2. Give the auto-injector injection right away [S1].\n3. Apply the epipen on the lip [S1].\n4. Give a second dose after 5 minutes [S1].", anaphylaxis))
        // A body site the guide itself names stays.
        val snakebite = guideSections("snakebite")
        // sections() are in reading order: [S2] is "First Aid: If a snake bites you".
        val bite = "1. Remove rings and watches before swelling starts [S2].\n2. Wash the bite with soap and water [S2].\n3. Mark the leading edge of swelling on the skin and write the time alongside it [S2]."
        assertEquals(bite, AnswerChecks.removeUnsupportedSteps(bite, snakebite))
    }

    @Test fun aStepWithNoWordsFromTheGuideIsRemovedAndTheRestRenumbered() {
        val hypothermia = guideSections("hypothermia")
        // [S3] is "If you are not able to get medical help right away, try to warm the person up."
        val answer = "1. Move your friend into a warm room or shelter to stop heat loss [S3].\n2. Rub the arms and legs briskly to restore circulation [S3].\n" +
            "3. Remove wet clothing and wrap them in dry blankets for warmth [S3].\n4. Give them a cup of hot coffee every 20 minutes [S3]."
        assertEquals("1. Move your friend into a warm room or shelter to stop heat loss [S3].\n2. Remove wet clothing and wrap them in dry blankets for warmth [S3].",
            AnswerChecks.removeUnsupportedSteps(answer, hypothermia))
        // The heat example from the warning check: the invented "unless they can swallow" goes, the guide's own "do not give fluids" stays.
        val heat = "1. Call 911 or get them to a hospital immediately [S1].\n2. Move them to a cooler place [S1].\n" +
            "3. Do not give food or drink unless they are able to swallow safely [S2].\n4. Do not give fluids [S1].\n5. Cool the skin with wet cloths [S1]."
        assertEquals("1. Call 911 or get them to a hospital immediately [S1].\n2. Move them to a cooler place [S1].\n3. Do not give fluids [S1].\n4. Cool the skin with wet cloths [S1].",
            AnswerChecks.removeUnsupportedSteps(heat, listOf(heatStroke, heatExhaustion)))
    }

    @Test fun inlineNumberingAndGroundedStepsAreLeftAlone() {
        // The burns answer scored 7/10 and every step is the guide's; "2." is a step marker, not an invented number.
        val burns = guideSections("burns")
        // [S2] is "How to Treat Minor Burns".
        val answer = "1. Remove all clothing/diapers/jewelry/metal from the area immediately [S2]. 2. Hold the burned skin under cool running water for 10–15 minutes if possible [S2]; " +
            "otherwise apply a cool wet cloth or soak in a cool bath for five minutes [S2]. 3. Clean gently with soap and water [S2]. 4. Do not pop the blister as an opened blister can get infected [S2]. " +
            "5. Cover lightly with sterile non-stick gauze taped over it [S2]."
        assertEquals(answer, AnswerChecks.removeUnsupportedSteps(answer, burns))
        assertEquals(answer, AnswerChecks.removeUnsupportedSteps(answer, emptyList()))
    }

    @Test fun unsupportedModelStepsCanBeEmptiedAndGettingHelpStays() {
        val bleeding = guideSections("severe-bleeding")
        // Nothing here is sufficiently grounded: do not retain guesses just to fill the answer.
        val loose = "Stay calm and reassure them [S1]. Elevate the limb above the heart [S1]. Keep the person warm with a jacket [S1]."
        val kept = AnswerChecks.removeUnsupportedSteps(loose, bleeding)
        assertEquals("", kept)
        // "Seek emergency medical assistance as soon as possible" shares no words with "until medical help arrives" and stays.
        assertEquals("Remove any clothing covering the wound [S1]. Seek emergency medical assistance as soon as possible [S1].",
            AnswerChecks.removeUnsupportedSteps("Remove any clothing covering the wound [S1]. Seek emergency medical assistance as soon as possible [S1].", bleeding))
        // A heading and a blank line are formatting, not steps.
        assertEquals("**Steps**\n\n- Cover with a clean cloth and apply pressure [S1].",
            AnswerChecks.removeUnsupportedSteps("**Steps**\n\n- Cover with a clean cloth and apply pressure [S1].\n- Apply a tourniquet above the knee and note the time [S1].", bleeding))
    }

    @Test fun aStepWrittenResultFirstIsCheckedToo() {
        val q = "Lunch for 14 people, 2 sandwiches each, 6 sandwiches per platter at 9.50 a platter. How many platters and what total?"
        val out = AnswerChecks.checkArithmetic("Sandwiches: 28 (14 × 2)\nPlatters: 5 (28 ÷ 6 = 4.67, round up)\nTotal: 38 (5 platters × 9.50)\nFinal answer: 5 platters, 38", q)
        assertTrue(out, "Total: 47.5 (corrected on this phone) (5 platters × 9.50)" in out)
        assertTrue(out, "Sandwiches: 28 (14 × 2)" in out)
        // The final answer repeats a figure no correct step gave: it is flagged.
        assertTrue(out, AnswerChecks.STEP_CORRECTED_NOTE in out )
    }

    @Test fun onlyTheOnlineClauseOfASentenceGoes() {
        val q = "How do I pay for the tram in Oslo?"
        fun check(text: String) = AnswerChecks.removeOnlineAdvice(text, q)
        assertEquals("A single ticket costs 42 NOK; buy it from the machine at the stop [S1].",
            check("A single ticket costs 42 NOK; buy it from the machine at the stop or with the Ruter app [S1]."))
        assertEquals("Pay the driver in cash [S2].", check("Pay the driver in cash, or through the Bolt app [S2]."))
        assertEquals("Get a travel card at any station kiosk [S1].",
            check("Get a travel card at any station kiosk; it also works in the Ruter app [S1]."))
        assertEquals("- Ask the front desk to call a taxi [S3].",
            check("- Ask the front desk to call a taxi; or book through the Uber app [S3]."))
        // Naming an app without telling them to use it is not advice to go online.
        assertEquals("Ask the front desk to call a taxi; the Uber app is popular here [S3].",
            check("Ask the front desk to call a taxi; the Uber app is popular here [S3]."))
        // A quoted source line is never cut down.
        assertFalse(check("> Buy a day ticket at the kiosk, or with the Ruter app [S1].").contains("kiosk [S1]"))
        // Nothing offline is left: the sentence goes.
        assertEquals("Trams run every 10 minutes [S1].", check("Trams run every 10 minutes [S1]. Download the Ruter app and pay in it [S1]."))
    }

    @Test fun onlineAdviceIsRemovedWhateverTheQuestionMentionsInPassing() {
        val checked = AnswerChecks.removeOnlineAdvice("The train runs every 20 minutes. Check the official website for the latest timetable.",
            "Is there a train link from Lisbon airport to the city centre?")
        assertFalse(checked, "website" in checked)
        assertFalse(AnswerChecks.removeOnlineAdvice("Book a ride with the Grab app if you have luggage.", "How do I get to my hotel with luggage?")
            .contains("Grab app"))
        assertFalse(AnswerChecks.removeOnlineAdvice("Download a translation app to hear the pronunciation.", "How do I say thank you in Thai?")
            .contains("translation app"))
        // A question about the service itself keeps it.
        assertTrue(AnswerChecks.removeOnlineAdvice("Buy a local SIM card at the airport.", "Where can I get a SIM card in Bangkok?").contains("SIM card"))
    }

    @Test fun transitLinesInAQuotedGuideAreNotCitations() {
        val quoted = "## From the saved reference\n\n> Take the S8 to the city and change to S8 at Ostbahnhof. Night buses replace the airport train (S1) after midnight. [S1]"
        val shown = AnswerText.finalized(quoted, 1)
        assertTrue(shown, "change to S8 at Ostbahnhof" in shown && "airport train (S1) after" in shown)
        // The model's own text is still normalised.
        assertTrue(AnswerText.finalized("The train is fast (S1).", 1).contains("[S1]"))
    }
}
