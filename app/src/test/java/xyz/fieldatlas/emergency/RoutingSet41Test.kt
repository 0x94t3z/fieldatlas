package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // A person has to be what was found or seen hanging.
        assertNull(match("found my keys hanging on the hook"))
        assertNotEquals("cpr", match("i saw the christmas lights hanging off the roof and the ladder fell on my dad, his leg is bleeding"))
        assertNotEquals("cpr", match("i saw my son hanging upside down on the monkey bars and he fell and hit his head"))
        assertNotEquals("cpr", match("found my brother hanging out with friends and hes drunk and vomiting"))
        assertEquals("cpr", match("my husband found someone hanging in the garage"))
    }

    @Test fun highBloodSugarDoesNotOpenTheHypoGuide() {
        assertNull(match("my daughter has high blood sugar and shes vomiting"))
        assertNull(match("my blood sugar is 500 and im throwing up"))
        // The reading on the other side of the range keeps the hypo guide.
        assertEquals("low-blood-sugar", match("my son is type 1 and his meter says 3.1 and hes shaking and confused"))
        // Too much insulin is a hypo whatever the earlier reading.
        assertEquals("low-blood-sugar", match("his sugar reading is 350 after too much insulin hes shaking and sweaty"))
    }

    @Test fun aStrokeToldAsCantSpeakOpensTheStrokeGuide() {
        assertEquals("stroke", match("my husband suddenly cant speak and his right arm is weak"))
        assertEquals("stroke", match("he suddenly cant talk and his face looks funny"))
        // A lost voice with no other sign is still an airway question.
        assertEquals("choking", match("my daughter is choking, she was eating cashew nuts and can't breathe!"))
        assertEquals("choking", match("man at the next table is grabbing his throat and can't speak"))
        assertEquals("choking", match("my dad is choking at dinner and his face looks funny"))
    }

    @Test fun anaphylaxisToldAsCantBreatheOpensTheAdrenalineGuide() {
        assertEquals("anaphylaxis", match("her face is swelling up and she cant breathe"))
        assertEquals("anaphylaxis", match("he ate a peanut and now his lips are swelling and his throat feels tight"))
        // A sting that is only a sting stays with the sting guide.
        assertEquals("insect-stings", match("got stung by a wasp on the arm, it stings and there is a red lump"))
        // An illness behind the breathing is not an object to dislodge, and the classifier may not reopen choking either.
        assertNotEquals("choking", match("my baby has rsv and cant breathe"))
        assertTrue(EmergencyGuideMatch.rulesOut("choking", "my baby has rsv and cant breathe"))
        assertEquals("choking", match("my son swallowed a coin and cant breathe or talk"))
    }

    @Test fun aDangerouslyHotPersonDoesNotGetTheWarmingGuide() {
        assertEquals("heat-illness", match("his body temperature is 104 and hes not making sense"))
        assertEquals("heat-illness", match("my mom has a fever of 40 and is shivering uncontrollably"))
        // A feverish child is still the fever guide, even at 40 where an adult is heat illness.
        assertEquals("fever-children", match("my baby has a fever of 39, what do i do"))
        assertEquals("fever-children", match("my 3 year old has a fever of 40 and is shivering"))
        assertEquals("fever-children", match("my 3-year-old has a fever of 40 and is shivering"))
        assertEquals("hypothermia", match("he fell through the ice into freezing water and now hes shivering and confused"))
        // A Fahrenheit air temperature is the weather, not a fever: these people need warming.
        assertEquals("hypothermia", match("it was 39 degrees and raining, he is soaked and shivering and confused"))
        assertEquals("hypothermia", match("temperature dropped to 40 degrees overnight and my dad is shivering and slurring"))
        assertEquals("hypothermia", match("we were out all night at 38 degrees in wet clothes and she wont stop shivering"))
    }

    @Test fun aPersonShakingOnTheFloorIsNotAnEarthquake() {
        assertEquals("seizures", match("shes on the floor shaking and cant respond"))
        assertEquals("seizures", match("my husband is on the ground shaking and foaming"))
        // The ground itself still opens the earthquake guide.
        assertEquals("earthquake", match("everything shaking, shelves falling, 6th floor, what do i do"))
        // A fit after the quake is a fit; while the ground is still moving the quake guide stays first.
        assertEquals("seizures", match("hes shaking and foaming after the earthquake"))
        assertEquals("seizures", match("after the earthquake my wife is on the floor convulsing"))
        assertEquals("earthquake", match("we are in an earthquake right now the building is swaying"))
        // A shiver with the cold is not a fit.
        assertEquals("hypothermia", match("we found my grandad in the garden this morning, he had been out all night and is shaking and confused"))
    }

    @Test fun faceDownOnLandOpensCprAndNotTheDrowningGuide() {
        assertEquals("cpr", match("i found my baby face down in the crib and hes not moving"))
        assertEquals("cpr", match("i found my dad face down on the kitchen floor"))
        // Face down needs a person: an object lying face down is not a casualty.
        assertNull(match("found my phone face down in the grass"))
        assertNotEquals("cpr", match("my phone is face down on the table and the screen went cold and gone"))
        // In the water the rescue steps are still the water ones.
        assertEquals("drowning", match("pulled him out of the pool face down in the water and hes not breathing"))
    }
}
