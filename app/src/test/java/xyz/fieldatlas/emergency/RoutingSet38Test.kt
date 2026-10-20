package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Set 38 (20 October) opened the wrong guide for four questions and both models scored 0 on them: a question about
 * reheated rice opened the poisoning guide, a blood pressure reading opened CPR through the text ranking, a diver's
 * dizziness opened the stroke guide, and a frying pan on fire opened the burns guide. Each now opens nothing, so the
 * model answers, and each guide still opens for its own case.
 */
class RoutingSet38Test {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun foodPoisoningIsNotTheSwallowedPoisonGuide() {
        assertNull(match("how long is cooked rice safe in the fridge? i heard reheated rice can give you food poisoning, is that real or just something people say"))
        assertNull(match("my friend ate bad chicken and now has food poisoning, throwing up all night, what do i do"))
        assertNull(match("think we got a stomach bug from the buffet, both of us vomiting"))
        assertEquals("poisoning", match("my toddler swallowed some bleach what do i do"))
        assertEquals("poisoning", match("my friend took too many pills and is drowsy"))
    }

    @Test fun aReadingAndAQuestionAboutNormalIsNotCpr() {
        assertNull(match("What's a normal blood pressure for an adult? Mine was 138 over 88 at the pharmacy machine. Is that high and should I be worried?"))
        assertNull(match("my resting heart rate is 58, is that too low"))
        assertEquals("cpr", match("my dad just collapsed and isn't breathing and i can't find a pulse"))
        assertEquals("cpr", match("someone collapsed and is not breathing"))
        // Shared words ("sudden", "swollen") never make the text ranking open the stroke guide without a stroke sign.
        assertNull(match("my 13 yr old son has sudden really bad pain in one testicle and its swollen, started an hour ago"))
        // A study question about the difference is not first aid.
        assertNull(match("whats the difference between a heart attack and cardiac arrest, studying for my nursing exam"))
    }

    @Test fun aDiversSignsAreNotAStroke() {
        assertNull(match("My buddy came up way too fast from a dive about 40 minutes ago. Now his shoulder is really aching, he feels dizzy and his arm is tingling. We're on a boat an hour from shore, what do we do"))
        assertNull(match("after scuba diving this morning i feel dizzy and my arm is numb and tingly"))
        assertEquals("stroke", match("what are the symptoms of a stroke"))
        assertEquals("stroke", match("my mum's face is drooping on one side and her arm keeps drifting down"))
    }

    @Test fun aPanOnFireIsNotABurn() {
        assertNull(match("the oil in the frying pan just caught fire in our airbnb kitchen, flames going up to the hood. water? what do i do"))
        assertNull(match("the toaster is on fire what do i do"))
        assertNull(match("grease fire on the stove, flames, do i throw water on it"))
        assertEquals("burns", match("i burned my hand on the frying pan what should i do"))
        assertEquals("burns", match("my child's arm got burned by hot oil from the pan, the skin is blistering"))
        assertEquals("burns", match("scalded by boiling water"))
    }

    @Test fun emergenciesWithNoGuideOpenNone() {
        // The model answers these; no bundled guide covers them and no other guide may stand in.
        assertNull(match("fire alarm going off in our hotel, opened the door and the corridor is full of smoke. were on the 7th floor. do we run for the stairs or stay in the room"))
        assertNull(match("skiing off piste and a slab just slid, my friend got carried down and is buried, i can see one of his gloves above the snow. what do i do right now"))
        assertNull(match("my wifes contractions are 3 minutes apart and she says the baby is coming now. were 90 minutes from the nearest hospital on a mountain road. what do i do"))
    }
}
