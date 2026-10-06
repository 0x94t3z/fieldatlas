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
}
