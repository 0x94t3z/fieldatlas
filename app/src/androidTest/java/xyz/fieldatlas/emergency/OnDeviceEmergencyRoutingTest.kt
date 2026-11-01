package xyz.fieldatlas.emergency

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The guide matcher on Android's own regex engine (ICU), against the guides bundled in the APK: the JVM unit tests cannot
 * show that the sign rules compile here or match the same way. Cases from EmergencyGuideMatchTest, sets 57 to 59.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceEmergencyRoutingTest {
    private val guides = EmergencyData.guides(
        InstrumentationRegistry.getInstrumentation().targetContext.assets.open("emergency/guides.json").bufferedReader().readText(),
    ).guides
    private fun match(question: String) = EmergencyGuideMatch.match(question, guides)?.id

    @Test fun bleedingAndElectricShockRouteAsOnTheJvm() {
        assertEquals("severe-bleeding", match("HELP my husband cut his hand on broken glass like 5 min ago its spurting and wont stop, the towel is soaked what do i do"))
        assertEquals("severe-bleeding", match("mate put the angle grinder thru his thigh in the garage blood is pumping out in spurts ive got 2 tea towels on it and theyre soaked thru already"))
        assertEquals("electric-shock", match("my brother touched a frayed cord and got shocked hes on the floor still touching the lamp what do i do"))
        assertEquals("electric-shock", match("my dad got zapped by the toaster and now hes lying on the kitchen floor, what do i do"))
        assertEquals("electric-shock", match("hair dryer fell in the bath with my daughter in it"))
        assertEquals("electric-shock", match("husband touched a bare wire in the attic and now hes not moving"))
        assertTrue(EmergencyGuideMatch.describesFirstAidSituation("my brother touched a frayed cord and got shocked hes on the floor still touching the lamp what do i do"))
    }

    @Test fun lookAlikesStayAwayFromTheseGuides() {
        assertNotEquals("severe-bleeding", match("nose has been bleeding for 25 minutes and wont stop, im 70 and on blood thinners"))
        assertNotEquals("severe-bleeding", match("im 34 weeks and suddenly a gush of blood, no pain at all"))
        assertNotEquals("severe-bleeding", match("my baby drank from the soap pump bottle, it was spurting all over her face"))
        assertEquals("poisoning", match("my 2 yr old drank hand sanitiser gel from the pump bottle by the door, maybe a few squirts"))
        assertNotEquals("severe-bleeding", match("why are the leaves on my bleeding heart plant turning yellow"))
        assertNotEquals("electric-shock", match("my son 14 got tackled hard at rugby and got up fine, but an hour later he says he gets electric shocks down both arms when he bends his neck"))
        assertNotEquals("electric-shock", match("my mum was shocked by the price of the new kettle, what should i do"))
        assertNotEquals("electric-shock", match("is electric shock therapy still used for depression and is it safe"))
        assertNotEquals("electric-shock", match("goat keeps touching the electric fence and jumping back, will it hurt her"))
        assertNotEquals("electric-shock", match("which wire is live and which is neutral when replacing a light switch, power is off"))
        assertFalse(EmergencyGuideMatch.describesFirstAidSituation("i was shocked by the electricity bill this month, what should i do"))
    }

    @Test fun theNoGuideCuesReadTheSameHere() {
        assertTrue(EmergencyGuideMatch.mayInvolveCurrent("my brother was rewiring the garage and now hes lying on the floor twitching"))
        assertTrue(EmergencyGuideMatch.mayBeBleeding("wife got her finger in the meat slicer at the deli the whole counter is covered and shes going grey"))
        assertFalse(EmergencyGuideMatch.mayBeBleeding("nose has been bleeding for 25 minutes and wont stop, im 70 and on blood thinners"))
    }
}
