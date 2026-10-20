package xyz.fieldatlas.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A distance to the nearest hospital, or whether to see a doctor, is not a request to find a place (set 38, 20 October). */
class VenueRoutingGuardTest {
    @Test fun howFarHelpIsIsNotARequestToFindIt() {
        val labour = "my wifes contractions are 3 minutes apart and she says the baby is coming now. were 90 minutes from the nearest hospital on a mountain road. what do i do"
        assertFalse(VenueLookup.isNearMe(labour))
        assertFalse(VenueLookup.isPlaceLookup(labour))
        assertFalse(VenueLookup.asksForBusinesses(labour))
        assertFalse(VenueLookup.isNearMe("we're 2 hours from the nearest hospital, is a 39 C fever in a 2 year old something to drive in for"))
    }

    @Test fun whetherToSeeADoctorIsNotALookup() {
        val fever = "my 2 year old has a temperature of 39.2. shes drinking and a bit grumpy but ok otherwise. at what point do i actually need to take her to a doctor"
        assertFalse(VenueLookup.asksForBusinesses(fever))
        assertFalse(VenueLookup.isPlaceLookup(fever))
        assertFalse(VenueLookup.asksForBusinesses("should i see a doctor about a mole that has changed colour"))
        assertFalse(VenueLookup.asksForBusinesses("do i need to go to a hospital for a sprained ankle or can i treat it at home"))
    }

    @Test fun askingWhereThePlaceIsStillIs() {
        assertTrue(VenueLookup.isNearMe("where is the nearest pharmacy"))
        assertTrue(VenueLookup.isNearMe("i need a hospital near me right now"))
        assertTrue(VenueLookup.isNearMe("nearest hospital to me, my friend is hurt"))
        assertTrue(VenueLookup.isPlaceLookup("which hospitals in Lisbon have an emergency department"))
        assertTrue(VenueLookup.asksForBusinesses("is there a pharmacy open now near me"))
    }
}
