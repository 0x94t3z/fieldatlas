package xyz.fieldatlas.emergency

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Blind routing set 40 (20 October) left 11 covered emergencies without a guide and opened an unsafe one five times.
 * Each case sits beside a neighbour that must keep its answer.
 */
class RoutingSet40Test {
    private val book = EmergencyData.guides(File("src/main/assets/emergency/guides.json").readText())
    private fun match(question: String) = EmergencyGuideMatch.match(question, book.guides)?.id

    @Test fun longMessagesThatDescribeTheSignsStillOpenTheGuide() {
        assertEquals("cpr", match("my husband just slumped over at the dinner table hes not answering me i put my ear by his mouth and theres nothing his lips are going blue im alone with him please tell me what to do"))
        assertEquals("seizures", match("guy on the train just dropped, whole body jerking, eyes rolled back, foam at his mouth, two blokes are pinning him down and someone wants to shove a wallet in his mouth"))
        assertEquals("head-injury", match("mate came off his skateboard with no helmet and the back of his head hit the kerb hes awake but keeps asking me the same thing over and over and has thrown up twice"))
        assertEquals("poisoning", match("my 18 month old was playing with the tv remote and the back is off and the little coin battery is gone i think she swallowed it she seems fine right now"))
        // A long message that only names hazards is still research, and so is a piece of work about them.
        assertNull(match("the documentary covered how earthquakes, tsunamis, tornadoes and wildfires are forecast, which early warning systems have saved the most lives over the last fifty years, and what the next decade of satellite monitoring might add"))
        assertNull(match("i am writing a long essay comparing how earthquakes, tsunamis, tornadoes and wildfires are forecast and which early warning systems have saved the most lives in the last fifty years"))
    }

    @Test fun hazardsToldAsWhatIsHappeningOpenTheirGuide() {
        assertEquals("carbon-monoxide", match("whole family woke up with pounding headaches and feeling sick, even the dog is wobbly, weve had the gas heater running all night with the windows shut"))
        assertEquals("earthquake", match("everything shaking, shelves falling, 6th floor, what do i do"))
        assertEquals("lost", match("went off trail for a shortcut and now nothing looks familiar, phone on 9 percent with no signal, sun getting low, keep walking or stay put"))
        assertEquals("lightning-backcountry", match("were above the treeline on an exposed ridge, sky went black, hair on my arms is standing up and my trekking poles are buzzing"))
        assertEquals("fractures-sprains", match("ankle bent sideways after trampoline, foot going purple, cant stand"))
        assertEquals("wildfire", match("sky is orange and ash is landing on the car, flames on the ridge behind the houses and smoke is getting into the house, do we leave now"))
        assertEquals("snakebite", match("hiking through long grass, something struck my calf, two small puncture holes, swelling fast and i feel sick and dizzy"))
        // A hike alone is not being lost.
        assertNotEquals("lost", match("hiking tomorrow, how much water should i carry for 20 km in the heat"))
    }

    @Test fun workTalkPlanningAndThePastOpenNoGuide() {
        assertNull(match("inbox is flooded, deadlines hitting like lightning, pressure from every direction and a storm of slack messages, how do i prioritise"))
        assertNull(match("best way to store two weeks of drinking water for hurricane season and how often should i rotate it"))
        assertNull(match("broke my wrist skiing last winter and it still aches when its cold out, is that normal a year on"))
        // The same words with someone hurt now keep their guide.
        assertEquals("fractures-sprains", match("broke my wrist skiing just now, its swollen and bent and i cant move my fingers"))
        assertEquals("safe-drinking-water", match("the tap water has gone brown after the storm, how do i make it safe to drink"))
    }

    @Test fun theNewSignsDoNotTakeTheirNeighbours() {
        // Found by the 1,207-question harness after the first draft of these signs.
        assertEquals("poisoning", match("toddler bit into one of those dishwasher pods, foam around his mouth and hes coughing"))
        assertNotEquals("cpr", match("my kid was left in the hot car for a while, hes flushed, floppy and wont answer me properly"))
        assertEquals("snakebite", match("kid picked up a little snake at the campsite and it bit his finger, finger going purple, should we cut it"))
        assertNotEquals("fractures-sprains", match("back went out lifting a box, cant stand up straight and pain is shooting down my leg"))
        assertNotEquals("head-injury", match("my sister had a fit in the shopping centre, it stoped after a minute, now shes groggy and doesnt know where she is, how do i lie her down"))
    }

    @Test fun aNosebleedAndAPanicAttackGetNoGuideWhateverTheWords() {
        assertNull(match("nose has been pouring blood for 25 minutes even with pinching, tissue after tissue, im on blood thinners"))
        assertNull(match("girlfriend breathing really fast, says her hands are tingling and her chest is tight and shes going to die, this has happened before when shes stressed"))
        // Chest pain with no panic history stays the heart.
        assertEquals("heart-attack", match("girlfriend has crushing chest pain spreading to her left arm and shes sweating and sick, never had this before"))
        assertEquals("severe-bleeding", match("blood pouring from a deep cut on his forearm, soaked two towels already"))
    }
}
