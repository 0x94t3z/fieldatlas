package xyz.fieldatlas.compliance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun everyStatusIsOneTheAuditScopeDefines() {
        ids().forEach { id ->
            assertTrue("$id has status ${requirement(id).status}", requirement(id).status in STATUSES)
        }
    }

    @Test
    fun everyRequirementNamesEvidenceThatExists() {
        ids().forEach { id ->
            val evidence = requirement(id).evidence
            assertTrue("$id must name evidence", evidence.isNotEmpty())
            // A link is checked by a reader; a path in this repository must be there, or the ledger points at nothing.
            evidence.filterNot { it.startsWith("https://") }.forEach { path ->
                assertTrue("$id names $path, which is missing", File("../$path").exists())
                assertFalse("$id names $path in the ignored build directory", path.startsWith("build/") || path.contains("/build/"))
            }
        }
    }

    @Test
    fun aRequirementNotFullyMetSaysWhy() {
        ids().filter { requirement(it).status != "pass" }.forEach { id ->
            assertTrue("$id is ${requirement(id).status} without a note", requirement(id).note.isNotBlank())
        }
    }

    @Test
    fun evidenceGatedRequirementsCannotPassFromCodeAlone() {
        assertTrue(
            "fresh-shell toolchain evidence must be named",
            requirement("B31-10").evidence.contains("scripts/tests/test_android_sdk.py"),
        )
        // Running on a phone, staying within its memory, working with the radios off, speed and real
        // hardware at submission are shown on a phone, not by code.
        listOf("B31-01", "B31-02", "B31-04", "B31-08", "B31-12").forEach { id ->
            assertTrue(
                "$id must name a physical-device record",
                requirement(id).evidence.any { it.startsWith("docs/evidence/physical/") },
            )
        }
        // The quality bar is measured on questions written after the last change, with every grade published.
        val bar = requirement("B31-20").evidence
        assertTrue(bar.contains("docs/evaluation.md"))
        assertTrue(bar.any { it.startsWith("docs/fieldtest/heldout") })
        assertTrue(bar.any { it.startsWith("docs/fieldtest/results-") })
    }

    private fun ids(): List<String> = (1..20).map { "B31-%02d".format(it) }

    private fun requirement(id: String): Requirement {
        val objectText = Regex("\\{[^{}]*\\\"id\\\"\\s*:\\s*\\\"$id\\\"[^{}]*}")
            .find(ledger.readText())?.value ?: error("Missing requirement $id")
        val status = Regex("\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(objectText)?.groupValues?.get(1) ?: error("Missing status for $id")
        val evidenceBody = Regex("\\\"evidence\\\"\\s*:\\s*\\[([^]]*)]")
            .find(objectText)?.groupValues?.get(1) ?: error("Missing evidence for $id")
        val evidence = Regex("\\\"([^\\\"]+)\\\"").findAll(evidenceBody).map { it.groupValues[1] }.toList()
        val note = Regex("\\\"note\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
            .find(objectText)?.groupValues?.get(1) ?: ""
        return Requirement(status, evidence, note)
    }

    private data class Requirement(val status: String, val evidence: List<String>, val note: String)

    private companion object {
        /** As defined in the ledger's auditScope. */
        val STATUSES = setOf("pass", "partial", "fail")
    }
}
