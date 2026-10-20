package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An object lodged in the body opens the bleeding guide that pads around it and leaves it in. Held-out case h37-31 ("a
 * broken branch went into his thigh, it's still sticking out") once opened the drowning guide, whose text shares
 * "branch" and "pull … out" with it.
 */
class EmbeddedObjectMatchTest {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun anObjectLeftInTheBodyOpensTheGuideThatSaysToLeaveItIn() {
        listOf(
            "my friend crashed his mountain bike and a broken branch went into his thigh, its still sticking out. do we pull it out??",
            "a stick went into my leg when I fell off my bike, it's still in there. should I take it out?",
            "my son fell on a fence spike and it's stuck in his arm",
            "there's a piece of glass stuck in my foot and it's bleeding",
            "he got impaled by a metal rod in his calf",
            "the knife is still in his side, do I pull it out",
            "my brother stepped on a rusty nail, it went right through his foot and it's still in there",
            "kid fell onto the garden fork and one of the prongs is in her calf",
            "there's a shard of glass in his hand and blood everywhere, should I pull it out",
            "she was stabbed and the knife is still in her",
        ).forEach { assertEquals(it, "bleeding-control", match(it)) }
    }

    @Test fun lookAlikesKeepTheirOwnGuideOrNone() {
        val cases = mapOf(
            // Pain that spreads names no object.
            "I have chest pain that went into my left arm" to "heart-attack",
            // A blade that went in and came out, with heavy bleeding: the tourniquet guide.
            "fillet knife slipped and went into my upper thigh, blood pouring out and my waders are filling up" to "severe-bleeding",
            "he fell off his bike and his bone is sticking out of his leg" to "fractures-sprains",
            "bike crash, his leg is bent the wrong way" to "fractures-sprains",
            "my friend crashed his bike and hit his head" to "head-injury",
            "found a tick stuck in my kid's leg" to "tick-bites",
            "fish bone stuck in my throat" to "choking",
            "my friend fell into the river and isn't breathing" to "drowning",
        )
        cases.forEach { (question, guide) -> assertEquals(question, guide, match(question)) }
    }

    @Test fun nothingInABodyIsNotAnEmergency() {
        listOf(
            "my car is stuck in the mud, how do I pull it out?",
            "the branch is still sticking out of the tree trunk, should I pull it out",
            "the pipe burst and water went into the back room what do I do",
            "a branch fell on the car what do I do",
            "how do I remove a splinter",
        ).forEach { assertNull(it, match(it)) }
    }

    @Test fun ambiguousOrOtherObjectsDoNotOpenTheBleedingGuideOrAnUnrelatedOne() {
        // Whether the nail is still in is not said; a hook, a splinter and an eye have their own steps.
        listOf(
            "a nail went through my hand",
            "a fish hook is stuck in my thumb",
            "how do I get a splinter out of my finger",
            "a nail went into my eye",
        ).forEach { question ->
            assertNotEquals(question, "bleeding-control", match(question))
            assertNotEquals(question, "drowning", match(question))
        }
    }

    @Test fun theTextRankingNeverPicksASettingThatIsNotThere() {
        // No water, no drowning guide; no eye, no eye guide; no fuel, no carbon monoxide guide.
        listOf(
            "my friend crashed his mountain bike and a broken branch went into his thigh, its still sticking out. do we pull it out??",
            "we pulled him out from under the branch and he is groaning",
            "neighbour is 70 and has a heavy ache in the middle of his chest, left arm feels dead, hes white and sweaty",
            "im 58 woke up at 4am with heavy pressure in the middle of my chest and feel like throwing up cant get comfy",
        ).forEach { question ->
            assertNotEquals(question, "drowning", match(question))
            assertNotEquals(question, "eye-injury", match(question))
            assertNotEquals(question, "carbon-monoxide", match(question))
        }
        // Chest pressure told without the words "chest pain" now clearly leads to the heart attack guide.
        assertEquals("heart-attack", match("neighbour is 70 and has a heavy ache in the middle of his chest, left arm feels dead, hes white and sweaty"))
        assertEquals("heart-attack", match("im 58 woke up at 4am with heavy pressure in the middle of my chest and feel like throwing up cant get comfy"))
    }

    @Test fun embeddedObjectNeedsAnObjectAndABody() {
        assertEquals(true, EmergencyGuideMatch.embeddedObject("the knife is still in his side"))
        assertEquals(false, EmergencyGuideMatch.embeddedObject("the pain went into my left arm"))
        assertEquals(false, EmergencyGuideMatch.embeddedObject("the knife went into my thigh and came out"))
        assertEquals(false, EmergencyGuideMatch.embeddedObject("the stick is still in the mud"))
    }
}
