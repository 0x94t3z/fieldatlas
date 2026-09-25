package xyz.fieldatlas.compliance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequirementLedgerTest {
    private val ledger = File("../docs/compliance/bounty-31.json")

    @Test
    fun everyBountyRequirementAppearsExactlyOnceInOrder() {
        assertTrue("requirement ledger must exist", ledger.isFile)
        val ids = Regex("\"id\"\\s*:\\s*\"(B31-\\d{2})\"")
            .findAll(ledger.readText())
            .map { it.groupValues[1] }
            .toList()

        assertEquals((1..20).map { "B31-%02d".format(it) }, ids)
    }

    @Test
    fun ledgerPinsTheReviewedBountyDescription() {
        assertTrue("requirement ledger must exist", ledger.isFile)
        assertTrue(
            ledger.readText().contains(
                "81507393d46e58800373024441b2222a271fa8bc4e2a0fc77f0dd4556de7a4bb",
            ),
        )
    }

    @Test
    fun evidenceGatedRequirementsCannotPassFromCodeAlone() {
        assertEquals("pass", requirement("B31-07").status)
        listOf("B31-02", "B31-04", "B31-08", "B31-12", "B31-13").forEach { id ->
            assertEquals("$id must be backed by the physical release record", "pass", requirement(id).status)
        }
        assertTrue(
            "fresh-shell toolchain evidence must be named",
            requirement("B31-10").evidence.contains("scripts/tests/test_android_sdk.py"),
        )
        assertTrue(
            "physical-device evidence directory must be named",
            requirement("B31-12").evidence.any { it.startsWith("docs/evidence/physical/") },
        )
        assertTrue(File("../docs/evidence/physical/infinix-x6840-android16/radios-off-answer.png").isFile)
        assertTrue(File("../docs/evidence/physical/infinix-x6840-android16/current-app-meminfo.txt").isFile)
    }

    private fun requirement(id: String): Requirement {
        val objectText = Regex("\\{[^{}]*\\\"id\\\"\\s*:\\s*\\\"$id\\\"[^{}]*}")
            .find(ledger.readText())?.value ?: error("Missing requirement $id")
        val status = Regex("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(objectText)?.groupValues?.get(1) ?: error("Missing status for $id")
        val evidenceBody = Regex("\\\"evidence\\\"\\s*:\\s*\\[([^]]*)]")
            .find(objectText)?.groupValues?.get(1) ?: error("Missing evidence for $id")
        val evidence = Regex("\\\"([^\\\"]+)\\\"").findAll(evidenceBody).map { it.groupValues[1] }.toList()
        return Requirement(status, evidence)
    }

    private data class Requirement(val status: String, val evidence: List<String>)
}
