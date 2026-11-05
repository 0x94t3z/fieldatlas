package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Routing set 41 (3 November) — six safety reports from the 1.4.6 device pass. Each case sits beside a neighbour that
 * must keep its answer, because every one of these fixes could be made by widening a rule until it swallowed the next
 * guide over.
 *
 * #14 a hanging person was a crime scene for the model: no guide, no call line
 * #15 high blood sugar opened the hypo guide, whose first step is sugar
 * #16 "can't speak" and "can't breathe" opened choking for a stroke and an anaphylaxis
 * #17 104 F with confusion opened the hypothermia guide
 * #18 a person shaking on the floor opened the earthquake guide
 * #19 "face down" on land opened the drowning guide
 */
class RoutingSet41Test {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun aLigatureIsALifeThreatThatOpensTheCprGuide() {
        assertEquals("cpr", match("found my brother hanging"))
        assertEquals("cpr", match("found my brother hanging from a rope in the garage"))
        assertEquals("cpr", match("my neighbour has strangled himself with a cord in the garage, hes on the floor"))
        // A rope that is not round a neck is not this.
        assertNull(match("how much rope do i need to hang a hammock between two trees"))
        // The bare word "hanging" is not a neck: a picture, a chat and a game on the monkey bars are not ligatures.
        assertNotEquals("cpr", match("i was hanging a picture and fell off the ladder, my wrist really hurts"))
        assertNotEquals("cpr", match("my friend and i were hanging out when he started having chest pain"))
        assertNotEquals("cpr", match("my son is hanging upside down on the monkey bars and his nose is bleeding"))
    }

    @Test fun highBloodSugarDoesNotOpenTheHypoGuide() {
        assertNull(match("my daughter has high blood sugar and shes vomiting"))
        assertNull(match("my blood sugar is 500 and im throwing up"))
        // The reading on the other side of the range keeps the hypo guide.
        assertEquals("low-blood-sugar", match("my son is type 1 and his meter says 3.1 and hes shaking and confused"))
    }

    @Test fun aStrokeToldAsCantSpeakOpensTheStrokeGuide() {
        assertEquals("stroke", match("my husband suddenly cant speak and his right arm is weak"))
        assertEquals("stroke", match("he suddenly cant talk and his face looks funny"))
        // A lost voice with no other sign is still an airway question.
        assertEquals("choking", match("my daughter is choking, she was eating cashew nuts and can't breathe!"))
        assertEquals("choking", match("man at the next table is grabbing his throat and can't speak"))
    }

    @Test fun anaphylaxisToldAsCantBreatheOpensTheAdrenalineGuide() {
        assertEquals("anaphylaxis", match("her face is swelling up and she cant breathe"))
        assertEquals("anaphylaxis", match("he ate a peanut and now his lips are swelling and his throat feels tight"))
        // A sting that is only a sting stays with the sting guide.
        assertEquals("insect-stings", match("got stung by a wasp on the arm, it stings and there is a red lump"))
    }

    @Test fun aDangerouslyHotPersonDoesNotGetTheWarmingGuide() {
        assertEquals("heat-illness", match("his body temperature is 104 and hes not making sense"))
        assertEquals("heat-illness", match("my mom has a fever of 40 and is shivering uncontrollably"))
        // A feverish child is still the fever guide, even at 40 where an adult is heat illness.
        assertEquals("fever-children", match("my baby has a fever of 39, what do i do"))
        assertEquals("fever-children", match("my 3 year old has a fever of 40 and is shivering"))
        assertEquals("fever-children", match("my 3-year-old has a fever of 40 and is shivering"))
        assertEquals("hypothermia", match("he fell through the ice into freezing water and now hes shivering and confused"))
    }

    @Test fun aPersonShakingOnTheFloorIsNotAnEarthquake() {
        assertEquals("seizures", match("shes on the floor shaking and cant respond"))
        assertEquals("seizures", match("my husband is on the ground shaking and foaming"))
        // The ground itself still opens the earthquake guide.
        assertEquals("earthquake", match("everything shaking, shelves falling, 6th floor, what do i do"))
        // A shiver with the cold is not a fit.
        assertEquals("hypothermia", match("we found my grandad in the garden this morning, he had been out all night and is shaking and confused"))
    }

    @Test fun faceDownOnLandOpensCprAndNotTheDrowningGuide() {
        assertEquals("cpr", match("i found my baby face down in the crib and hes not moving"))
        assertEquals("cpr", match("i found my dad face down on the kitchen floor"))
        // Face down needs a person: an object lying face down is not a casualty.
        assertNull(match("found my phone face down in the grass"))
        // In the water the rescue steps are still the water ones.
        assertEquals("drowning", match("pulled him out of the pool face down in the water and hes not breathing"))
    }
}
