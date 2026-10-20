package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The blind routing set 39 (20 October) found the matcher opening nothing for nine plainly told emergencies and an
 * unsafe guide for four look-alikes. Each is kept here beside a neighbour that must keep its old answer.
 */
class RoutingSet39Test {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun signsToldInEverydayWordsOpenTheirGuide() {
        assertEquals("choking", match("my toddler is grabbing at his throat cant make a sound going blue he was eating grapes HELP"))
        assertEquals("stroke", match("mum just dropped her tea and one side of her face has slid down and shes talking total gibberish what do i do"))
        assertEquals("shock", match("after the crash my brother is awake but grey and clammy breathing really fast and keeps saying hes gonna pass out"))
        // The same grey, clammy skin with the chest, and no injury, is the heart; a diabetic talking nonsense is low sugar.
        assertEquals("heart-attack", match("dad says it feels like an elephant is sat on his chest, hes gone grey and clammy and the ache is spreading to his jaw"))
        assertNotEquals("stroke", match("my husband is diabetic and hes gone sweaty and shaky and is talking nonsense and pushing me away when i try to give him juice"))
        assertEquals("drowning", match("just dragged my son out the pool he was under for a bit hes coughing and spluttering and breathing but really pale and floppy"))
        assertEquals("altitude-sickness", match("day 3 trekking at 4500m my mate has a pounding headache keeps vomiting and is stumbling about like hes drunk"))
        assertEquals("poisoning", match("my 2 yr old just ate a cigarette butt out the ashtray shes gagging a bit is that bad what do i do"))
    }

    @Test fun aDisasterToldAsHappeningOpensItsGuideWithNobodyInTheSentence() {
        assertEquals("tornado", match("sirens going sky is green and i can see a funnel out the window were in a mobile home what do we do"))
        assertEquals("tsunami", match("big earthquake just stopped and now the sea has sucked right back off the beach and theres a weird roaring noise"))
        assertEquals("lightning-backcountry", match("were on an exposed ridge above the treeline storm rolling in and my hair is literally standing up where do we go"))
        // The same hazards as research stay research.
        assertNull(match("how do tornadoes form and why is the sky green before one"))
        assertNull(match("what causes a tsunami after an earthquake"))
    }

    @Test fun aPastEventIsNotAnEmergencyNow() {
        assertNull(match("how long after a heart attack is it ok to fly, my dad had one in march and wants to come to the wedding in june"))
        assertNull(match("my mum had a stroke 2 years ago, what are the chances of another one"))
        // Still now, with the past in it.
        assertEquals("heart-attack", match("my dad had a heart attack last year and right now he's clutching his chest and sweating, what do i do"))
    }

    @Test fun aNosebleedAPanicAttackAndACrushAreNotWoundsAirwaysOrBreaks() {
        assertNull(match("my nose has been bleeding heavily for 25 minutes and wont stop ive been tilting my head back and holding tissues what else"))
        assertNull(match("my friend is hyperventilating says she cant breathe and her hands are tingling and shes convinced shes dying, this has happened before when shes stressed"))
        assertNull(match("a big metal shelving unit fell on my dad hes pinned under it says he cant breathe properly and i cant lift it on my own"))
        // Their neighbours keep their guides.
        assertEquals("severe-bleeding", match("deep cut on his thigh and blood is pouring out, soaking through the towel"))
        assertEquals("choking", match("my friend is choking on a piece of steak and cant breathe or talk"))
        assertNotEquals("choking", match("she's wheezing and can't get her breath, asthma attack and no inhaler"))
        assertEquals("fractures-sprains", match("he fell off the ladder and his leg is bent the wrong way, bone sticking out"))
    }
}
