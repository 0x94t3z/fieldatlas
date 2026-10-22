package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A first-aid answer the model wrote from a guide, checked against that guide on the phone. */
class GuideAnswerCheckTest {
    private val guides = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText()).guides.associateBy { it.id }

    @Test fun aDangerSignTheGuideListsPutsCallNowFirst() {
        // Set 44: "call 9-1-1 if he loses consciousness" for a man already confused after hitting his head.
        val result = GuideAnswerCheck.check("my diabetic uncle fell off a ladder, hit his head and now he's confused and sweaty",
            guides.getValue("head-injury"), "Give him something sugary if he can swallow.\nCall 9-1-1 if he loses consciousness [S1].")
        assertTrue(result.callNow, result.callNow!!.startsWith("**Call your local emergency number now.**"))
        assertTrue(result.callNow, result.callNow!!.contains("get confused"))
    }

    @Test fun noCallNowWhenTheSignIsDeniedOrTheAnswerAlreadySaysSo() {
        val guide = guides.getValue("head-injury")
        assertNull(GuideAnswerCheck.check("he bumped his head, he is not confused and talking normally", guide, "Watch him for a day [S1].").callNow)
        assertNull(GuideAnswerCheck.check("hit his head and now he's confused", guide, "Call 911 now [S1].\nKeep him still.").callNow)
        assertNull(GuideAnswerCheck.dangerSign("my son bumped his head on a table, he cried but is playing again", guide))
    }

    @Test fun aStepTheGuideForbidsIsQuotedBack() {
        val guide = guides.getValue("snakebite")
        assertEquals(listOf("Do not apply ice or immerse the wound in water."),
            GuideAnswerCheck.conflicts("Apply ice to the bite to bring the swelling down.", guide))
        assertTrue("an answer that agrees raises nothing", GuideAnswerCheck.conflicts("Do not put ice on it. Keep the bite still.", guide).isEmpty())
        assertTrue(GuideAnswerCheck.conflicts("Try to suck out the venom.", guide).single().startsWith("Do not try to suck out the venom"))
        assertTrue("words from different sentences do not join", GuideAnswerCheck.conflicts("Apply a clean dressing. Ice cream helps nobody.", guide).isEmpty())
    }

    @Test fun aSentenceGoingAgainstTheGuideIsReplacedByTheGuidesOwn() {
        val guide = guides.getValue("snakebite")
        val result = GuideAnswerCheck.check("snake bit my ankle", guide, "1. Keep the leg still [S1].\n2. Apply ice to the bite to bring the swelling down.")
        assertFalse("the forbidden step is gone", result.answer.contains("Apply ice"))
        assertTrue(result.answer, result.answer.contains("2. Do not apply ice or immerse the wound in water. [S1]"))
        assertEquals(1, result.conflicts.size)
    }

    @Test fun whileStreamingOnlyCheckedCompletedLinesShow() {
        val guide = guides.getValue("snakebite")
        val partial = "1. Keep the leg still [General Knowledge].\n2. Try to suck out the venom.\n3. Apply ice to the bi"
        val shown = GuideAnswerCheck.streamed(partial, guide)
        assertFalse("the line being written is hidden", shown.contains("Apply ice"))
        assertFalse("a forbidden step never shows", shown.contains("Try to suck"))
        assertTrue(shown, shown.contains("Do not try to suck out the venom"))
        assertFalse(shown, shown.contains("General Knowledge"))
        assertEquals("", GuideAnswerCheck.streamed("Keep the leg st", guide))
    }

    @Test fun unsupportedMarksAndInventedLabelsAreRemoved() {
        val result = GuideAnswerCheck.check("my friend got stung by a bee and his face is swelling", guides.getValue("anaphylaxis"),
            "Use his auto-injector now [S1].\nKeep him sitting upright [General Knowledge].\nThe moon is full tonight [S1].")
        assertFalse(result.answer, result.answer.contains("General Knowledge"))
        assertTrue(result.answer, result.answer.contains("The moon is full tonight\n") || result.answer.endsWith("The moon is full tonight."))
        assertEquals(1, result.unsupportedMarks)
        assertTrue("a short supported step keeps its mark", GuideAnswerCheck.check("bee sting swelling", guides.getValue("anaphylaxis"),
            "Call 911 [S1].").answer.contains("[S1]"))
    }

    @Test fun firstAidQuestionsNoGuideMatchedAreRecognised() {
        listOf(
            "my wife suddenly cant lift her left arm and her words are coming out wrong, it started about 10 min ago",
            "my uncles face got burned by a flash from the gas grill, his voice is hoarse and he's coughing up black stuff",
            "my diabetic dad is really drowsy and confused and cant swallow properly",
            "can my friend use my salbutamol inhaler, hers is empty and she is wheezing",
        ).forEach { assertTrue(it, GuideAnswerCheck.firstAidQuestion(it)) }
        listOf("what is the capital of bhutan", "how does an epipen work", "why do onions make your eyes water")
            .forEach { assertFalse(it, GuideAnswerCheck.firstAidQuestion(it)) }
    }

    @Test fun publishersReadAsPossessives() {
        assertEquals("CDC's", GuideAnswerCheck.possessive("CDC"))
        assertEquals("Wikipedia contributors'", GuideAnswerCheck.possessive("Wikipedia contributors"))
    }

    @Test fun aDescribedLifeThreatIsToldApartFromAQuestionAboutTheTopic() {
        listOf(
            "my 7 month old baby is gagging and went silent after putting a grape in her mouth, face going blue",
            "my wifes waters have gone and shes pushing i can see the top of the babys head",
            "my mate just got buried by an avalanche i can see one glove sticking out of the snow",
            "my son is wheezing after eating shrimp, should i use his epipen first or his inhaler",
        ).forEach { assertTrue(it, GuideAnswerCheck.describesLifeThreat(it)) }
        listOf(
            "what is the difference between a heart attack and cardiac arrest",
            "for my first aid exam, what are the signs of choking",
            "why do lips turn blue in the cold",
            "how does an epipen work",
        ).forEach { assertFalse(it, GuideAnswerCheck.describesLifeThreat(it)) }
    }

    @Test fun nhsCallNowSignsWithNoGuideOpenWithCallNow() {
        // Set 50 opened these with "if this is an emergency"; the NHS lists each as a reason to call 999.
        listOf(
            "my uncle fell off a ladder onto his back and says he cant feel his legs, he wants to stand up",
            "my mum has a kidney infection and now her skin is mottled and she hasnt peed all day",
            "my type 1 son has been vomiting and his breath smells like pear drops",
            "my friend with asthma is too breathless to talk and is using her neck muscles",
            "im 36 weeks pregnant and seeing flashing lights with a bad headache",
            "my dad says its indigestion but he is sweaty and his arm aches",
        ).forEach { assertTrue(it, GuideAnswerCheck.describesLifeThreat(it)) }
        listOf(
            "my legs ache after the marathon, what helps",
            "what causes blurred vision in pregnancy",
        ).forEach { assertFalse(it, GuideAnswerCheck.describesLifeThreat(it)) }
    }

    @Test fun aDoubleDoseOrAGarageLiquidCountsAsSwallowed() {
        assertTrue(GuideAnswerCheck.swallowedSomething("my dad took his evening heart tablets twice by mistake"))
        assertTrue(GuideAnswerCheck.swallowedSomething("my son drank some windscreen washer fluid"))
        assertFalse(GuideAnswerCheck.swallowedSomething("it took twice as long as i thought"))
        assertFalse(GuideAnswerCheck.swallowedSomething("my son drank lots of fluids today"))
    }

    @Test fun aPaintingIsNotPain() {
        assertFalse(GuideAnswerCheck.firstAidQuestion("who painted girl with a pearl earring"))
        assertTrue(GuideAnswerCheck.firstAidQuestion("my son has a painful swollen ankle after football"))
    }

    @Test fun aFirstAidSituationNoGuideFitsOpensWithCallNowUnlessItIsMinorOrGeneral() {
        // Set 52: an ectopic pregnancy, a meningitis rash and ketoacidosis were each told "if this is an emergency".
        listOf(
            "my sister is 6 weeks pregnant with bad pain on one side and pain in the tip of her shoulder",
            "my daughter has a rash that doesnt fade when i press a glass on it and a stiff neck",
            "my dad stopped his insulin because he was sick and now he is drowsy and breathing deeply",
            "my friend is really unwell, shivering and confused after a kidney infection",
        ).forEach { assertTrue(it, GuideAnswerCheck.callLine(it, guideFound = false).startsWith("**Call your local emergency number now**")) }
        listOf(
            "my son has a small graze on his elbow, otherwise totally fine, how do i clean it",
            "how many chest compressions a minute for cpr, just want to know before my course",
        ).forEach { assertTrue(it, GuideAnswerCheck.callLine(it, guideFound = false).startsWith("**If this is an emergency")) }
    }

    @Test fun aMatchedLifeThreatGuideOpensWithCallNowForSomeoneInTheSituation() {
        assertTrue(GuideAnswerCheck.callLine("my wife was stung and her face is swelling, she is hoarse", guideId = "anaphylaxis")
            .startsWith("**Call your local emergency number now**"))
        // Learning about it is not an emergency.
        assertTrue(GuideAnswerCheck.callLine("our hall just got a defibrillator, do you need training to use one", guideId = "cpr")
            .startsWith("**If this is an emergency"))
        // A fall with neck pain and a numb arm is a possible spinal injury.
        assertTrue(GuideAnswerCheck.describesLifeThreat("my daughter fell off her horse, her neck hurts and one arm feels numb"))
    }

    @Test fun aSmallModelOpensEveryFirstAidSituationWithCallNowUnlessMinorOrGeneral() {
        // Set 54: a possible heart attack in a diabetic woman ("a bit breathless") was played down as minor.
        val heart = "my wife is diabetic and has an upper stomach ache, she is sick, sweaty and a bit breathless"
        assertFalse(GuideAnswerCheck.minor(heart))
        assertTrue(GuideAnswerCheck.callLine(heart, guideFound = false, cautious = true).startsWith("**Call your local emergency number now**"))
        // A guide matched, the small model shows it as published: still "now" for someone in trouble.
        assertTrue(GuideAnswerCheck.callLine("my daughter has asthma and has gone quiet and sleepy, i cant hear the wheeze anymore",
            guideId = "asthma-attack", cautious = true).startsWith("**Call your local emergency number now**"))
        // Minor, and learning, stay "if this is an emergency".
        assertTrue(GuideAnswerCheck.callLine("got a splinter in my thumb, it doesnt hurt much", guideFound = false, cautious = true)
            .startsWith("**If this is an emergency"))
        assertTrue(GuideAnswerCheck.callLine("how do you do back blows on a baby who is choking? my son starts on solid food next month",
            guideId = "choking", cautious = true).startsWith("**If this is an emergency"))
        assertTrue(GuideAnswerCheck.describesLifeThreat("im 37 weeks and havent felt the baby move since this morning"))
    }

    @Test fun aCordProlapseAndASpinalRedFlagAreLifeThreatsAndLearningIsNotFirstAid() {
        assertTrue(GuideAnswerCheck.describesLifeThreat("my wife is 34 weeks, her waters just went and she can feel something soft hanging down"))
        assertTrue(GuideAnswerCheck.describesLifeThreat("my son got tackled at rugby and now gets electric shocks down both arms when he moves his neck"))
        // Learning about CPR before a course is not first aid for someone now, so a small model answers it.
        assertFalse(GuideAnswerCheck.firstAidQuestion("how hard do you push for chest compressions? just want to know before my course"))
        assertTrue(GuideAnswerCheck.firstAidQuestion("my dad collapsed and i am doing chest compressions, how hard do i push"))
    }

    @Test fun aLearningQuestionIsNotSomeoneInTroubleNow() {
        assertTrue(GuideAnswerCheck.learning("how many chest compressions a minute is it for CPR and how deep do you push? just want to know before my course next week"))
        assertTrue(GuideAnswerCheck.learning("how do you do the heimlich on a child"))
        assertFalse(GuideAnswerCheck.learning("my uncle dropped while carrying boxes, just a snorty gasp every 8 seconds, do i start cpr"))
        assertFalse(GuideAnswerCheck.learning("who painted girl with a pearl earring"))
    }
}
