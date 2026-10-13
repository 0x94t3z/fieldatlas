package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Runs against the guides actually bundled in the APK. */
class EmergencyGuideMatchTest {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun explicitReferenceResearchDoesNotBecomeImmediateEmergencyAdvice() {
        listOf(
            "What do the saved Japan travel guides say about earthquake safety?",
            "What does the saved reference say about tsunami safety?",
            "Compare the travel guides on earthquake safety.",
        ).forEach { assertNull(it, match(it)) }
    }

    @Test fun immediateHelpStillOverridesReferenceResearch() {
        listOf(
            "What do the saved guides say about earthquakes? I am trapped right now.",
            "What do the saved guides say about earthquakes? Help me.",
            "Earthquake safety",
        ).forEach { assertEquals(it, "earthquake", match(it)) }
    }

    @Test fun emergencyQuestionsOpenTheRightGuide() {
        val cases = mapOf(
            "someone collapsed and is not breathing" to "cpr",
            "how do I do CPR" to "cpr",
            "my child is choking" to "choking",
            "how to stop bleeding from a deep cut" to "severe-bleeding",
            "I got bitten by a snake what should I do" to "snakebite",
            "signs of hypothermia" to "hypothermia",
            "what are the symptoms of a stroke" to "stroke",
            "heart attack symptoms" to "heart-attack",
            "my friend burned his hand on the stove what to do" to "burns",
            "my child burned her hand on a hot pan, what should I do?" to "burns",
            "scalded by boiling water" to "burns",
            "is it safe to drink stream water" to "safe-drinking-water",
            "how to purify water" to "safe-drinking-water",
            "I'm lost on a hike" to "lost",
            "what to do during an earthquake" to "earthquake",
            "tsunami" to "tsunami",
            "frostbite on my toes" to "frostbite",
            "bee sting swelling" to "insect-stings",
            "how do I remove a tick" to "tick-bites",
            "dog bite rabies" to "animal-bites",
            "heat stroke" to "heat-illness",
            "altitude sickness symptoms" to "altitude-sickness",
            "how to signal for help" to "signaling",
            "allergic reaction throat swelling" to "anaphylaxis",
            "someone is having a seizure" to "seizures",
        )
        val wrong = cases.mapNotNull { (q, id) ->
            val actual = match(q)
            if (actual != id) "$q -> $actual (want $id)" else null
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun aStingThatBurnsIsNotABurn() {
        // The burns guide says to cool the burn under running water; fresh water can fire a
        // jellyfish's remaining stingers. No bundled guide covers marine stings, so research answers.
        assertNotEquals("burns", match("Got stung by a jellyfish while swimming. Long red welts on my leg and it burns like crazy. Vinegar or fresh water?"))
        assertNotEquals("burns", match("stepped on a sea urchin and it burns, what do I do"))
        assertEquals("burns", match("my friend burned his hand on the stove what to do"))
        assertEquals("burns", match("I spilled boiling water and it burns, what should I do?"))
    }

    @Test fun everydayDescriptionsOfTicksAndFrostbiteFindTheirGuides() {
        assertEquals("tick-bites", match("found a tick stuck in my kid's leg after a hike. how do i remove it properly and what signs should i watch for?"))
        assertEquals("tick-bites", match("there's a tick embedded in my arm, what should I do"))
        assertEquals("frostbite", match("After a long day skiing, two of my fingers are white, hard and numb. How should I warm them up?"))
        assertEquals("frostbite", match("my toes are numb and white after the snow hike, what do I do"))
        // Numb fingers away from the cold are not frostbite.
        assertNotEquals("frostbite", match("my fingers are numb when I type, what should I do"))
    }

    @Test fun howAThingWorksIsAnExplanationEvenWhenItMentionsSafety() {
        assertNull(match("How does a squeeze water filter like a Sawyer make river water safe to drink? Does it stop viruses?"))
        assertEquals("safe-drinking-water", match("how do I make stream water safe to drink on a hike?"))
    }

    @Test fun ordinaryQuestionsWithTopicWordsAreLeftToResearch() {
        for (question in listOf(
            "history of the 2004 Indian Ocean tsunami",
            "what happened during the 1906 San Francisco earthquake",
            "how to cut onions without crying",
            "how to burn calories fast",
            "how to make a snake game in Python",
            "how to paint a snake",
            "I lost my phone how do I find it",
            "explain photosynthesis",
            "best vegan restaurants in Berlin",
            "how does water boil at altitude",
            "how to improve my swimming stroke",
            "what is the boiling point of water",
            "compare the revisions in my files",
        )) assertNull(question, match(question))
    }

    @Test fun detailedEmergencyRequestsStillMatch() {
        val question = "I was bitten by a snake while hiking with my friend this morning and we are still on the trail a long way from our car with no internet connection. What should I do?"
        assertTrue(question.split(' ').size > 30)
        assertEquals("snakebite", match(question))
        assertEquals("snakebite", match("We are a long way from our car. ".repeat(20) + question))
    }

    @Test fun scientificComparisonsDoNotBecomeEmergencyInstructions() {
        for (question in listOf(
            "How do earthquake P waves differ from S waves?",
            "How does a tsunami differ from a tidal wave?",
            "How do earthquakes differ from aftershocks?",
        )) assertNull(question, match(question))
        // A comparison must not suppress an accompanying real request for help.
        assertEquals("earthquake", match("How do earthquake P waves differ from S waves? What should I do right now?"))
        assertEquals("earthquake", match("How do earthquake P waves differ from S waves when I am trapped and need help?"))
    }

    @Test fun lengthyHistoricalQuestionsStayInResearch() {
        assertNull(match("Describe the history of the 2004 Indian Ocean tsunami, including the geological causes, the countries affected, the economic consequences and the changes in coastal infrastructure that followed over the next two decades."))
        // Long research questions that name a hazard and a safety or symptom word are not first aid.
        for (question in listOf(
            "Compare the symptoms of heatstroke and dehydration in marathon runners and explain the physiological mechanisms that cause each, citing what research says about electrolyte balance, sweat rates, core temperature and recovery after long races in hot weather.",
            "Explain how earthquake early warning systems work in Japan and Mexico, how many seconds of warning they typically give, what limits their accuracy, and how public safety outcomes have changed since they were introduced in each country.",
            "Why did the Chernobyl disaster lead to long term changes in nuclear reactor safety design, and how do those design changes compare with the lessons drawn after the Fukushima tsunami in 2011 for coastal plants?",
        )) assertNull(question, match(question))
    }

    @Test fun aBystanderDescribingAStrangerGetsItsGuide() {
        // Found by an outside review: 31 words, third person, no "my" or "I was".
        assertEquals("cpr", match("A man has collapsed and is not breathing. How do I do CPR? We are outside the railway station and an ambulance has already been called but has not arrived yet."))
        assertEquals("cpr", match("A man has collapsed and is not breathing. How do I do CPR?"))
    }

    @Test fun aSwollenAnkleThatCannotTakeWeightIsASprainOrFracture() {
        assertEquals("fractures-sprains", match("My friend fell while we were climbing and now his ankle is swollen and he cannot put weight on it, we are three hours from the trailhead and it is getting dark, what should we do right now?"))
        assertEquals("fractures-sprains", match("I twisted my ankle on the trail, what should I do?"))
    }

    @Test fun anInjuryThatCannotTakeWeightLeadsWithWhenToGetCare() {
        val guide = book.guides.single { it.id == "fractures-sprains" }
        val flagged = EmergencyGuideMatch.select("My friend fell and now his ankle is swollen and he cannot put weight on it, what should we do?", guide, 2_000)
        assertTrue(flagged.first().title, flagged.first().title.endsWith("Treatment for Serious Injuries"))
        // A minor sprain without a red flag still gets the home-care steps first.
        val minor = EmergencyGuideMatch.select("I twisted my ankle a little on a walk, how do I treat a mild sprain at home?", guide, 2_000)
        assertTrue(minor.first().title, minor.first().title.endsWith("Treatment of Minor Injuries"))
    }

    @Test fun aLongAccountOfAnInjuryStillGetsItsGuide() {
        assertEquals("burns", match("We are camping and my son touched the hot stove and burned his hand, the skin is red and blistering and he is crying a lot, there is no signal here and the nearest town is far, how do I treat it?"))
    }

    @Test fun excerptsIdentifyThePublisherWithoutClaimingOfficialAuthority() {
        for (id in listOf("choking", "drowning", "snakebite")) {
            val guide = book.guides.single { it.id == id }
            val excerpt = EmergencyGuideMatch.officialSteps(EmergencyGuideMatch.sections(guide).first(), 2, guide.source.publisher)
            assertTrue(excerpt, excerpt.startsWith("**Source excerpt from ${guide.source.publisher}, as published** [S2]"))
            assertFalse(excerpt, excerpt.contains("Official steps"))
        }
    }

    @Test fun answersKeepThePublishedWordingAndCiteTheSource() {
        val guide = book.guides.single { it.id == "snakebite" }
        val answer = EmergencyGuideMatch.answer(guide)
        assertTrue(answer.contains("[S1]"))
        assertTrue(answer.contains("CDC/NIOSH"))
        assertTrue(answer.contains("written for the United States"))
        for (step in guide.sections.flatMap { it.steps + it.paragraphs }) assertTrue(step, answer.contains(step))
        val evidence = EmergencyGuideMatch.evidence(guide)
        assertEquals(guide.source.url, evidence.source)
        assertTrue(evidence.text.contains("Do not try to suck out the venom."))
    }

    @Test fun everyGuideHasASourceWithALicence() {
        assertTrue(book.guides.size >= 30)
        for (guide in book.guides) {
            assertTrue(guide.id, guide.source.license.isNotBlank() && guide.source.url.startsWith("https://"))
            assertTrue(guide.id, guide.plainText().length > 100)
            assertTrue(guide.id, guide.category in EmergencyData.categoryLabels)
        }
    }

    @Test fun everydayAccountsOfAnEmergencyFindTheirGuide() {
        // Found while preparing the demo: "a snake bit my friend, what do we do?" got no guide and
        // the model, answering alone, advised ice and raising the limb above the heart.
        val cases = listOf(
            "A snake bit my friend. What do we do?" to "snakebite",
            // Dictation arrives lowercase and unpunctuated.
            "a snake bit my friend what do we do" to "snakebite",
            "My son was bit by a snake while camping" to "snakebite",
            "A dog bit my daughter, what do we do?" to "animal-bites",
            "My friend got stung by a bee and his throat is swelling" to "anaphylaxis",
            "My friend is shivering and confused after falling in a cold river" to "hypothermia",
            "It's really hot and my friend stopped sweating and is confused" to "heat-illness",
            "My kid swallowed bleach, what do we do?" to "poisoning",
            "My friend fell and hit his head, he's dizzy" to "head-injury",
            // On a ridge the backcountry lightning guide is the one that helps (see thunderOnARidgeOpensTheBackcountryGuide).
            "We got caught in a lightning storm on the ridge" to "lightning-backcountry",
            "My friend twisted his ankle and can't walk" to "fractures-sprains",
            "A tick bit me on the hike" to "tick-bites",
        )
        // No guide at all is a failure too.
        val wrong = cases.mapNotNull { (q, id) -> match(q).let { got -> if (got != id) "$q -> $got (want $id)" else null } }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun everydayWordsInOrdinaryQuestionsStayInResearch() {
        for (question in listOf(
            "How many bits are in a byte?", "What bit rate should I use for an MP3?", "Explain why some animals are confused by mirrors",
            "Explain how the stomach digests what we swallowed", "Why do cats bite when you pet them?",
            "How do I get my code to stop when the loop is caught in an exception?", "What should we do to reduce plastic waste in our city?",
            "My son asks why the sun is hot", "What is the history of antivenom research?",
        )) assertNull(question, match(question))
    }

    @Test fun theSafetyNoteSaysWhatToDoWithNoSignal() {
        val note = EmergencyGuideMatch.safetyNote(book.guides.single { it.id == "snakebite" })
        assertTrue(note, note.startsWith("If this is an emergency, call your local emergency number first"))
        assertTrue(note, "No signal? Try the emergency call anyway" in note)
        assertTrue(note, "send someone for help and keep following these steps" in note)
    }

    @Test fun headachesNearAHeaterAreCarbonMonoxideNotAltitude() {
        assertEquals("carbon-monoxide", match("Several people in our cabin have headaches and feel dizzy near the gas heater. What should we do?"))
        assertEquals("carbon-monoxide", match("The CO alarm is going off and my kids feel sick, what do I do?"))
        assertEquals("altitude-sickness", match("I have a headache at 3,800 m in the Andes, what should I do?"))
        assertEquals("altitude-sickness", match("How do I avoid altitude sickness when I arrive in Cusco?"))
        assertNotEquals("altitude-sickness", match("I have a bad headache and feel dizzy, what should I do?"))
        assertNotEquals("carbon-monoxide", match("How do I clean a gas stove?"))
        assertNotEquals("carbon-monoxide", match("The heater in my car is broken, what do I do?"))
        assertEquals("power-outage", match("Is it safe to use a generator during a power outage?"))
    }

    @Test fun oneEverydayWordDoesNotPickAGuide() {
        assertNull(match("My baby has a fever of 39°C, what do I do?"))
        assertNull(match("My throat hurts, what should I do?"))
        assertEquals("cpr", match("Someone collapsed and isn't breathing, what do I do?"))
        assertEquals("cpr", match("My dad stopped breathing"))
        assertNull(match("I lost my passport while travelling in Spain. What should I do?"))
        assertEquals("fractures-sprains", match("My friend fell off a bike and his wrist is swollen and bent. What should we do?"))
        assertEquals("animal-bites", match("A monkey scratched my arm at a temple in Bali and drew blood. What should I do?"))
        assertNull(match("I scratched my car in a car park, what should I do?"))
        assertEquals("lost", match("We are lost in the forest and it is getting dark. What should we do?"))
        assertEquals("hypothermia", match("My friend's body temperature is 34°C and he is shivering, what do I do?"))
        assertEquals("frostbite", match("It's -20°C and my friend's fingers are white and numb, what should I do?"))
    }

    @Test fun aBentLimbLeadsWithWhenToGetCare() {
        val guide = book.guides.first { it.id == "fractures-sprains" }
        val first = EmergencyGuideMatch.select("My friend fell off a bike and his wrist is swollen and bent. What should we do?", guide, 400).first()
        assertTrue(first.title, "Serious Injuries" in first.title)
    }


    @Test fun heatIllnessInEverydayWordsAndExplanationsLeftToResearch() {
        assertEquals("heat-illness", match("Someone fainted in the heat at a festival. What should I do?"))
        assertEquals("heat-illness", match("my friend collapsed in the sun and seems confused"))
        // A stove in a hot tent is carbon monoxide first.
        assertNotEquals("heat-illness", match("It's hot in our tent with the gas stove on and my friend is dizzy and confused, what do we do?"))
        // Explanations are research, not first-aid steps.
        assertNull(match("What causes tsunamis?"))
        assertNull(match("How do avalanches form?"))
        assertNull(match("Why do people get altitude sickness?"))
        // Danger words or a person's own situation still open the guide.
        assertEquals("snakebite", match("What is the treatment for a snake bite?"))
        assertEquals("cpr", match("how do I do CPR"))
        assertEquals("choking", match("Why is the baby choking?"))
        assertEquals("choking", match("What is happening to him? He can't breathe!"))
        assertEquals("cpr", match("Why is he unresponsive?"))
        assertEquals("hypothermia", match("How does hypothermia start, and how do I warm him up?"))
    }

    @Test fun aSpecificSignOutranksHeatAsTheCause() {
        assertEquals("cpr", match("My friend collapsed in the sun and isn't breathing, what do I do?"))
        assertEquals("cpr", match("My dad collapsed in the heat and isn't breathing"))
        assertEquals("anaphylaxis", match("A bee stung my son in the hot sun and now he is dizzy and his throat is swelling"))
        assertEquals("head-injury", match("She collapsed in the sun and hit her head"))
        assertNotEquals("heat-illness", match("The CO alarm is going off, it's really hot in the room and my son is dizzy and confused. What do I do?"))
        assertNotEquals("heat-illness", match("Our CO detector is beeping, it's hot in here and my wife feels dizzy, what should we do?"))
        assertNotEquals("heat-illness", match("It's hot in the cabin with the wood burner going and my wife is dizzy and has a headache, what should I do?"))
        assertEquals("heat-illness", match("Someone fainted in the heat at a festival. What should I do?"))
        // Two everyday words of another guide outrank the heat too: hives after peanuts, a spider bite.
        assertEquals("anaphylaxis", match("My daughter fainted in the sun after eating peanuts and has hives"))
        assertEquals("anaphylaxis", match("Dad collapsed in the heat, his face is swelling and he has hives"))
        assertEquals("spider-bites", match("My friend fainted in the sun after a spider bite, what should we do?"))
        assertEquals("poisoning", match("My kid swallowed pills and fainted in the sun"))
        assertEquals("stroke", match("My wife fainted in the heat and her face is drooping"))
        assertEquals("cpr", match("My friend collapsed in the heat and isn't responding"))
    }

    @Test fun noBreathingNoPulseOrNoResponseOpensCprWhateverTheCause() {
        // The snakebite and flood guides have no CPR step.
        assertEquals("cpr", match("My friend isn't breathing after a snake bite"))
        assertEquals("cpr", match("My friend isn't breathing after a snake bite, what do I do?"))
        assertEquals("cpr", match("My friend has no pulse after a snake bite"))
        assertEquals("cpr", match("My son is not responding after a snake bite"))
        assertEquals("cpr", match("My friend stopped breathing after a snake bite"))
        assertEquals("cpr", match("My son isn't responding after a snake bite"))
        assertEquals("cpr", match("My friend was hit by lightning and is unresponsive, what do we do?"))
        // The sign is the emergency even when nothing is asked.
        assertEquals("cpr", match("He fell off the ladder and isn't responding"))
        assertEquals("cpr", match("someone collapsed and is not breathing"))
        assertEquals("cpr", match("Why is he unresponsive?"))
        // In the water, the drowning guide's rescue breaths come first.
        assertEquals("drowning", match("The baby is unresponsive in the flood water"))
        assertEquals("drowning", match("My friend isn't breathing in the cold water"))
        assertEquals("drowning", match("We pulled him out of the pool and he isn't breathing"))
        // A blocked or swelling airway keeps its own steps.
        assertEquals("anaphylaxis", match("My friend got stung by a bee and his throat is swelling and he isn't breathing"))
        assertEquals("choking", match("He's choking, I tried the Heimlich and he's still not breathing"))
        // Not responding to treatment or to texts is not the sign.
        assertEquals("tick-bites", match("My friend has been unresponsive to antibiotics since a tick bite, what should we do?"))
        assertNull(match("My mom isn't responding to my texts, what should I do?"))
        assertNull(match("My app isn't responding, what do I do?"))
        assertNull(match("My son's phone isn't responding, what do I do?"))
        // Without the sign, the cause's own guide still opens.
        assertEquals("snakebite", match("My friend is unconscious after a snake bite"))
    }

    @Test fun theBestWayToStopOrTreatSomethingIsFirstAid() {
        val cases = mapOf(
            "What is the fastest way to stop a seizure?" to "seizures",
            "What is the fastest way to stop an allergic reaction?" to "anaphylaxis",
            "What is the fastest way to stop a heart attack?" to "heart-attack",
            "What is the best way to stop a snake bite from spreading venom?" to "snakebite",
            "What is the best way to cool a burned hand?" to "burns",
            "What is the best way to purify stream water?" to "safe-drinking-water",
            "What is the best way to prevent frostbite?" to "frostbite",
            "What is the most effective way to prevent frostbite?" to "frostbite",
            "What are the quickest ways to stop a seizure?" to "seizures",
        )
        val wrong = cases.mapNotNull { (q, id) -> match(q).let { got -> if (got != id) "$q -> $got (want $id)" else null } }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
        assertNull(match("What causes tsunamis?"))
        assertNull(match("What is the best way to learn about tsunamis?"))
    }

    @Test fun stungByABeeIsTheStingGuide() {
        assertEquals("insect-stings", match("My friend was stung by a bee, what do I do?"))
        assertEquals("insect-stings", match("I got stung by a wasp on my hand"))
        // A swelling throat after a sting is still the airway emergency first.
        assertEquals("anaphylaxis", match("He was stung by a bee and his throat is swelling"))
    }

    @Test fun numbFingersWithAnAcuteEmergencyOpenThatEmergency() {
        // A cold sweat is a heart attack sign, not cold weather; "skin" is not skiing; ice on a sprain is not the cold.
        assertEquals("heart-attack", match("My dad has chest pain and a cold sweat and his fingers are numb, what should I do?"))
        assertEquals("stroke", match("My mum's face is drooping, her skin is pale and her fingers are numb, what do I do?"))
        assertEquals("fractures-sprains", match("I sprained my wrist and put ice on it, now my fingers are numb, what should I do?"))
        // In real cold, the acute injury still leads; frostbite can follow it.
        assertEquals("head-injury", match("I slipped on the ice and hit my head and now my fingers are numb, what should I do?"))
        assertEquals("fractures-sprains", match("I fell skiing and my arm looks broken and my fingers are numb, what do I do?"))
        // Frostbite named outright still leads.
        assertEquals("frostbite", match("I think I have frostbite after skiing, my fingers are numb and waxy, what do I do?"))
    }

    @Test fun fallingThroughIceIsAWaterRescueFirst() {
        val q = "Someone just fell through the ice on a frozen lake and is clinging to the edge. How do we get them out safely, and what do we do once they're out?"
        assertEquals("drowning", match(q))
        // The warming steps always come with the rescue.
        for (it in listOf(q, "my son fell through the ice on the pond, we pulled him out, he's shivering and confused, what do I do",
            "he fell through the ice but he's out now and shivering, help")) {
            assertTrue(it, EmergencyGuideMatch.pack(it, book.guides).map { g -> g.id }.containsAll(listOf("drowning", "hypothermia")))
        }
    }

    @Test fun shiveringAndConfusionWithAFeverIsNotHypothermia() {
        listOf("my mom has a high fever and she's shivering and confused, what should I do",
            "my grandma has a fever of 39, she is shivering and confused",
            "she has the flu, shivering and confused, what should I do",
            "he took too many pills and is shivering and confused",
            "he has malaria symptoms, shivering and confused, help",
        ).forEach { assertNotEquals(it, "hypothermia", match(it)) }
    }

    @Test fun venomousMarineAndOtherStingsAreNotBurns() {
        listOf("portuguese man of war sting burns what do I do", "man-o-war sting burns help",
            "I got a lionfish sting and it burns, what do I do?", "stonefish sting burns, help",
            "sea anemone sting burns what do I do", "weever fish sting burns", "catfish sting burns",
            "caterpillar sting burns", "horsefly bite burns", "leech bite burns", "centipede bite burns what do I do",
            "fire coral burned my leg while snorkeling, what do I do",
        ).forEach { assertNotEquals(it, "burns", match(it)) }
        // A real heat burn while handling fish is still a burn.
        assertEquals("burns", match("I burned my hand on the stove while cooking catfish, what should I do?"))
    }

    @Test fun clearFrostbiteSignsKeepFrostbiteWithAnotherInjury() {
        assertEquals("frostbite", match("my toes are white, hard and numb after snowshoeing and I twisted my ankle"))
        assertEquals("frostbite", match("my fingers are white hard and numb and I sprained my wrist skiing"))
        assertTrue(EmergencyGuideMatch.pack("my fingers are white and numb in the cold and I cut my finger, it's bleeding", book.guides)
            .any { it.id == "frostbite" } || match("my fingers are white and numb in the cold and I cut my finger, it's bleeding") == "frostbite")
    }

    @Test fun moreColdInjuriesFindFrostbite() {
        assertEquals("frostbite", match("we lost power in the ice storm and my toes are white and numb, what do I do"))
        assertEquals("frostbite", match("I left an ice pack on my knee too long and now the skin is white, hard and numb"))
    }

    @Test fun anAllergicPersonWhoseThroatClosesIsAnaphylaxis() {
        assertEquals("anaphylaxis", match("she's allergic to bees, got stung and now she's choking and can't breathe"))
        assertEquals("anaphylaxis", match("my kid is allergic to peanuts, his face is swollen and he's choking"))
    }

    @Test fun aFreezingTemperatureIsTheColdFrostbiteNeeds() {
        assertEquals("frostbite", match("My toes are numb, white and waxy after a night camping out at 5°F, what should I do?"))
        assertEquals("frostbite", match("My fingers are numb and white after hours outside at −15 °C, what should I do?"))
        assertEquals("frostbite", match("My fingers are numb and white after sleeping in a tent at 10 degrees F, what do I do?"))
        assertTrue(EmergencyGuideMatch.freezingTemperature("it was -5 C last night"))
        assertFalse(EmergencyGuideMatch.freezingTemperature("it was 40°F last night"))
        assertFalse(EmergencyGuideMatch.freezingTemperature("it is 25°C and sunny"))
    }

    @Test fun burnsThatStingOrAreSmallAreStillBurns() {
        assertEquals("burns", match("I burned my finger on a candle and it really stings, what should I do?"))
        assertEquals("burns", match("I burned my hand a bit on the barbecue grill, what should I do?"))
        assertEquals("burns", match("My toddler grabbed my hair straightener and burned her palm, it's blistering and stinging"))
        assertEquals("burns", match("My son touched the motorbike exhaust and burned his leg, it stings badly, what do I do?"))
        // A sting that burns is still a sting.
        assertNotEquals("burns", match("Got stung by a jellyfish and it burns like crazy, what do I do?"))
    }

    @Test fun aStingWithBreathingOrSwellingSignsIsAnaphylaxis() {
        assertEquals("anaphylaxis", match("My son got stung by a wasp and now he is wheezing and his lips are swollen, what do I do?"))
        assertEquals("anaphylaxis", match("My friend was stung by a bee and is having trouble breathing, what should I do?"))
        assertEquals("anaphylaxis", match("Got stung by a bee, my face and tongue are swollen and I feel dizzy"))
        assertEquals("anaphylaxis", match("I got stung by a bee and now I can't breathe"))
        // A plain sting stays the sting guide.
        assertEquals("insect-stings", match("I got stung by a wasp on my hand and it's red, what do I do?"))
    }

    @Test fun aMarineOrPlantStingThatBurnedTheSkinIsNotABurn() {
        listOf(
            "I got stung by a jellyfish and it burned my leg, what do I do?",
            "Stung by a jellyfish, it burned my skin badly, help",
            "Jellyfish sting burnt my arm, what should I do?",
            "Brushed against nettles and it burned my legs, what do I do?",
            "I touched a stinging nettle and it burned my hand, what do I do?",
            "I got a sting on my foot at the beach and it burns, what do I do?",
            "The bite on my leg burns and is swollen, what do I do?",
        ).forEach { assertNotEquals(it, "burns", match(it)) }
    }

    @Test fun chokingOnFoodStaysChokingWhateverTheAllergy() {
        assertEquals("choking", match("My son has a peanut allergy and is choking on a grape, he can't breathe, what do I do?"))
        assertEquals("choking", match("My son with a bee sting allergy is choking on food and can't breathe"))
    }

    @Test fun skatingAndIceFishingAreTheColdForFrostbite() {
        assertEquals("frostbite", match("My fingers are white and numb after ice skating, what do I do?"))
        assertEquals("frostbite", match("My fingers are white and numb after ice skating on the outdoor rink, what do I do?"))
        assertEquals("frostbite", match("My fingers are white and numb after playing ice hockey on the pond, what do I do?"))
        assertEquals("frostbite", match("My fingers are white and numb after ice fishing on the frozen lake all day, what do I do?"))
        assertEquals("frostbite", match("My fingers are white and numb after ice fishing on the frozen pond, what do I do?"))
        assertEquals("frostbite", match("My fingers are numb and white after my hands were in cold water while ice fishing"))
    }

    @Test fun aPlacesTapWaterIsTravelAdviceNotWaterTreatment() {
        assertNull(match("Is it safe to drink tap water in Mexico City?"))
        assertNull(match("Can I drink the tap water in Lisbon?"))
        // Water someone must treat, or a supply an event has spoiled, still opens the guide.
        assertEquals("safe-drinking-water", match("Is the tap water safe to drink when it looks brown?"))
        assertEquals("safe-drinking-water", match("is it safe to drink stream water"))
    }

    @Test fun coolingAndFuelWordsDoNotHideAHeatCollapse() {
        assertEquals("heat-illness", match("She fainted in the heat and her body temperature is very high"))
        assertEquals("heat-illness", match("My friend collapsed in the heat, we poured cold water on him, what else should we do?"))
        assertEquals("heat-illness", match("Someone fainted in the heat, should I give them water to drink?"))
        assertEquals("heat-illness", match("My dad passed out from the heat while pumping gas, what should I do?"))
        assertEquals("heat-illness", match("She passed out in the sun next to the barbecue, what do we do?"))
        assertEquals("heat-illness", match("My friend collapsed in the heat, we poured cool water on her, what next?"))
        assertEquals("heat-illness", match("He got dizzy in the sun while drinking water, what should I do?"))
        // Without the heat these are still hypothermia, and a stove or heater that scores is still carbon monoxide.
        assertEquals("hypothermia", match("My friend's body temperature is 34°C and he is shivering, what do I do?"))
        assertEquals("hypothermia", match("My friend fell into cold water and is shivering, what do I do?"))
        assertEquals("carbon-monoxide", match("We have a charcoal grill in the tent and my friend collapsed from the heat"))
        assertEquals("carbon-monoxide", match("My friend fainted from the heat next to the gas heater in our cabin, what do we do?"))
    }

    @Test fun plantsAndCakesThatCollapseInTheHeatAreNotFirstAid() {
        assertNull(match("How do I stop my tomato plants collapsing in the heat?"))
        assertNull(match("How do I keep my cake from collapsing in the heat?"))
        assertNull(match("My tomato plants collapsed in the heat, what should I do?"))
        assertNull(match("The tent collapsed in the heat of the afternoon, what should we do?"))
        assertEquals("heat-illness", match("My dad was walking in the sun and collapsed, what should I do?"))
        assertEquals("heat-illness", match("my friend collapsed in the sun and seems confused"))
    }

    @Test fun aHeatCollapseWithSevereSignsLeadsWithHeatStroke() {
        // Scored 2/10 in the field: the introduction, cramps and exhaustion filled the budget, heat
        // stroke ("call 911", "do NOT give fluids") was dropped, and the model advised sips of water.
        val guide = book.guides.single { it.id == "heat-illness" }
        val question = "Someone in our group collapsed in the heat on a hike. Their skin is hot, they're confused and they've almost stopped sweating. What do I do?"
        // 350 tokens is what a slow phone gets; heat stroke must be the one section that always fits.
        for (budget in listOf(350, 700, 2_000)) {
            val kept = EmergencyGuideMatch.select(question, guide, budget)
            assertTrue("$budget: ${kept.first().title}", kept.first().title.endsWith("Heat Stroke"))
            assertTrue(kept.first().text, "Do NOT give fluids." in kept.first().text)
        }
        // The introduction says nothing to do and is read last when it is read at all.
        val all = EmergencyGuideMatch.select(question, guide, 4_000)
        assertEquals(4, all.size)
        assertEquals(guide.title, all.last().title)
        assertTrue(EmergencyGuideMatch.select("Someone fainted in the heat at a festival. What should I do?", guide, 700).first().title.endsWith("Heat Stroke"))
        // Without a severe sign the mild form keeps its place.
        assertTrue(EmergencyGuideMatch.select("I have painful cramps in my legs after running in the heat, what do I do?", guide, 700).first().title.endsWith("Heat Cramps"))
    }

    @Test fun laySignsRankTheSectionThatNamesThemClinically() {
        val fractures = book.guides.single { it.id == "fractures-sprains" }
        val question = "I think my friend broke her ankle on the trail. We're about 3 hours' walk from the car and there's no signal. How do we splint it, and should we try to move her?"
        assertEquals("fractures-sprains", match(question))
        assertTrue(EmergencyGuideMatch.select(question, fractures, 700).first().title.endsWith("Treatment for Serious Injuries"))
        // "Swollen but I can still walk" is the minor injury, not the opening advice to stop the activity.
        val minor = EmergencyGuideMatch.select("I twisted my ankle hiking. It is swollen but I can still walk on it. What should I do?", fractures, 700)
        assertTrue(minor.first().title, minor.first().title.endsWith("Treatment of Minor Injuries"))
        // The general guide lacks battery-specific treatment. Show its poison-contact
        // reference with that limitation, instead of allowing the model to invent steps.
        assertEquals("poisoning", match("My child swallowed a button battery. What do I do?"))
    }

    @Test fun aSeizureLeadsWithItsStepsAndAWorryQuestionBringsTheCallList() {
        val guide = book.guides.single { it.id == "seizures" }
        val steps = EmergencyGuideMatch.select("Someone is having a seizure on the bus. What should I do?", guide, 350)
        assertTrue(steps.first().title, "Steps to keeping a person safe" in steps.first().title)
        val worried = EmergencyGuideMatch.select("My friend had a seizure, when should I worry and call for help?", guide, 700)
        assertTrue(worried.joinToString { it.title }, worried.any { "call 911 if one or more" in it.title })
        val dehydration = book.guides.single { it.id == "dehydration" }
        val worry = EmergencyGuideMatch.select("I have diarrhoea and feel dizzy, when should I worry about dehydration?", dehydration, 350).first()
        assertTrue(worry.text, "Get medical help right away if" in worry.text)
    }

    @Test fun aDefinitionHeadingLeadsOnlyWhenTheTermIsAsked() {
        val guide = book.guides.single { it.id == "cpr" }
        assertTrue(EmergencyGuideMatch.select("My dad collapsed and isn't breathing, what do I do?", guide, 700).first().title.endsWith("How is CPR done?"))
        assertTrue(EmergencyGuideMatch.select("What is CPR?", guide, 700).first().title.endsWith("What is CPR?"))
    }

    @Test fun theVerbatimLeadQuotesTheParagraphForTheCaseAndNoWebPointers() {
        val altitude = book.guides.single { it.id == "altitude-sickness" }
        val question = "we're at about 4200 m on a trek and my friend has a pounding headache, threw up twice and is stumbling like he's drunk. what do we do"
        val lead = EmergencyGuideMatch.officialSteps(EmergencyGuideMatch.select(question, altitude, 700).first(), 1, altitude.source.publisher, question)
        assertTrue(lead, "Go down immediately and seek medical attention." in lead)
        assertFalse(lead, "Every year, rangers" in lead)
        assertTrue(lead, "…" in lead)
        assertTrue(lead, lead.startsWith("**Source excerpt from ${altitude.source.publisher}, as published** [S1]"))
        // A short section is shown whole under its heading, minus the sentence that points to a web site.
        val heat = book.guides.single { it.id == "heat-illness" }
        val stroke = EmergencyGuideMatch.sections(heat).single { it.title.endsWith("Heat Stroke") }
        val excerpt = EmergencyGuideMatch.officialSteps(stroke, 1, heat.source.publisher, "collapsed in the heat and confused")
        assertTrue(excerpt, "_Heat Stroke_" in excerpt)
        assertTrue(excerpt, "Do NOT give fluids." in excerpt)
        assertTrue(excerpt, "Using a fan to blow air" in excerpt)
        assertFalse(excerpt, "see the Centers for Disease Control" in excerpt)
        // The sting site is a place on the body, not a web site.
        val stings = book.guides.single { it.id == "insect-stings" }
        val sting = EmergencyGuideMatch.officialSteps(EmergencyGuideMatch.select("A bee stung me, what do I do?", stings, 700).first(), 1, stings.source.publisher)
        assertTrue(sting, "Wash the site with soap and water." in sting)
    }

    @Test fun thunderOnARidgeOpensTheBackcountryGuide() {
        // Scored 3/10 and 4/10 in the field: no guide matched and the model, answering alone, sent the
        // asker to "a low-lying area where you cannot be struck".
        assertEquals("lightning-backcountry", match("Caught in a thunderstorm on an exposed ridge, what's the safest thing to do?"))
        assertEquals("lightning-backcountry", match("I'm on an exposed ridge and a thunderstorm is rolling in, I can already hear thunder. What's the safest thing to do?"))
        assertEquals("lightning-backcountry", match("A thunderstorm is coming while we are on an exposed mountain ridge. What do we do?"))
        // Indoors, or with no outdoor context, the general lightning guide keeps its place.
        assertEquals("lightning", match("There's thunder outside and the lights flickered, is it safe to shower?"))
        assertEquals("cpr", match("My friend was hit by lightning and is unresponsive, what do we do?"))
        // An explanation is research.
        assertNull(match("what causes thunderstorms?"))
        assertNull(match("How do avalanches form?"))
    }

    @Test fun aColdCaseOnTheTrailLeadsWithTheWarmingSteps() {
        // Scored 4/10 in the field: the symptom list led, and the model's first step was "take the
        // temperature first" on a dark trail in the rain.
        val guide = book.guides.single { it.id == "hypothermia" }
        val question = "We're stuck on a trail in cold rain. My friend is shivering really hard, slurring his words and stumbling. It's getting dark. What should I do?"
        for (budget in listOf(350, 700, 2_000)) {
            val first = EmergencyGuideMatch.select(question, guide, budget).first()
            assertTrue("$budget: ${first.title}", first.title.endsWith("try to warm the person up."))
            assertTrue(first.text, "Remove any wet clothing" in first.text)
        }
        // Asked for the signs, the signs still lead.
        assertTrue(EmergencyGuideMatch.select("What are the signs of hypothermia?", guide, 700).first().title.endsWith("What to look out for"))
        // The heat guide's "Call 911 or get the victim to a hospital immediately" is an instruction and still leads.
        val heat = book.guides.single { it.id == "heat-illness" }
        assertTrue(EmergencyGuideMatch.select("my friend collapsed in the sun and seems confused", heat, 350).first().title.endsWith("Heat Stroke"))
    }

    @Test fun aShortGuideOrAnInjuryFarFromHelpPacksACompanion() {
        fun ids(question: String) = EmergencyGuideMatch.pack(question, book.guides).map { it.id }
        // The four-sentence bleeding guide gets the bleeding-control guide; no signal adds the outdoors guide.
        assertEquals(listOf("severe-bleeding", "bleeding-control", "injured-outdoors"),
            ids("Slipped on rocks and got a deep cut on my shin. Blood is soaking through the t-shirt I wrapped around it and there's no phone signal. How do I stop the bleeding?"))
        assertEquals(listOf("severe-bleeding", "bleeding-control"), ids("My friend cut his leg badly and it is bleeding heavily. What should I do?"))
        assertEquals(listOf("hypothermia", "emergency-shelter"),
            ids("We're stuck on a trail in cold rain. My friend is shivering really hard, slurring his words and stumbling. It's getting dark. What should I do?"))
        assertEquals(listOf("snakebite", "injured-outdoors"),
            ids("my friend just got bitten by a snake on the ankle while hiking. no signal and we're about 3 hours from the car. what do we do right now?"))
        assertEquals(listOf("fractures-sprains", "injured-outdoors"),
            ids("I think my friend broke her ankle on the trail. We're about 3 hours' walk from the car and there's no signal. How do we splint it, and should we try to move her?"))
        // A full guide in town stands alone; a non-emergency packs nothing.
        assertEquals(listOf("heat-illness"), ids("Someone fainted in the heat at a festival. What should I do?"))
        assertEquals(listOf("burns"), ids("burned the inside of my leg on a scooter exhaust pipe, it's blistered and really painful. how do i treat it and should i pop the blister?"))
        assertEquals(emptyList<String>(), ids("what causes thunderstorms?"))
    }

    @Test fun packedGuidesLeadWithThePrimaryAndKeepItsExcerpt() {
        val question = "Slipped on rocks and got a deep cut on my shin. Blood is soaking through the t-shirt I wrapped around it and there's no phone signal. How do I stop the bleeding?"
        val guides = EmergencyGuideMatch.pack(question, book.guides)
        val primary = guides.first()
        for (budget in listOf(350, 700, 1_800)) {
            val kept = EmergencyGuideMatch.select(question, guides, budget)
            assertEquals("$budget", "emergency:severe-bleeding", kept.first().documentId)
            assertTrue("$budget: ${kept.map { it.title }}", kept.any { it.documentId != primary.let { g -> "emergency:${g.id}" } })
        }
        val wide = EmergencyGuideMatch.select(question, guides, 1_800)
        assertTrue(wide.joinToString { it.title }, wide.any { it.documentId == "emergency:injured-outdoors" })
        assertTrue(wide.joinToString { it.title }, wide.any { it.title.endsWith("Direct pressure") })
        // The quoted excerpt is the primary's own section, whatever else is packed.
        val lead = EmergencyGuideMatch.officialSteps(wide.first(), 1, primary.source.publisher, question)
        assertTrue(lead, "Cover with a clean cloth and apply pressure by pushing down with hands." in lead)
        // One guide packs exactly as before.
        val heat = book.guides.single { it.id == "heat-illness" }
        val hot = "Someone fainted in the heat at a festival. What should I do?"
        assertEquals(EmergencyGuideMatch.select(hot, heat, 700), EmergencyGuideMatch.select(hot, listOf(heat), 700))
    }
}
