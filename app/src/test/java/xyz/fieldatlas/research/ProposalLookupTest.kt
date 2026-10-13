package xyz.fieldatlas.research

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalLookupTest {
    private fun source(id: String, title: String, text: String) =
        PromptSource(id, Evidence("d-$title", "d-$title:0", title, "https://eips.ethereum.org/EIPS/eip-7702", text, -1.0), excerpt = text)

    @Test fun aQuestionNamingOneProposalIsAnsweredFromItsSummary() {
        assertEquals("EIP-7702", ProposalLookup.named("How does EIP-7702 change an externally owned account?"))
        assertEquals("ERC-4337", ProposalLookup.named("Explain ERC 4337 for smart wallets"))
        assertNull("two proposals are a comparison", ProposalLookup.named("Compare ERC-20 with ERC-1155"))
        assertNull(ProposalLookup.named("How do Ethereum validators finalise blocks?"))

        val spec = source("S1", "EIP-7702: Set Code for EOAs — Specification", "A new transaction type is introduced.")
        val abstract = source("S2", "EIP-7702: Set Code for EOAs — Abstract",
            "EIP-7702: Set Code for EOAs\nStatus: Final; Type: Standards Track\nAdd a new tx type that permanently sets the code for an EOA\n\n" +
                "Add a new transaction type that adds a list of [chain_id, address, nonce, y_parity, r, s] authorization tuples. For each tuple, write a delegation indicator to the signing account's code.")
        val lead = ProposalLookup.lead("How does EIP-7702 change an externally owned account?", listOf(spec, abstract))!!
        assertEquals("S2", lead.citationId)
        assertTrue(lead.text, lead.text.startsWith("Add a new transaction type that adds a list of"))
        assertNull("another proposal's summary is not this one's", ProposalLookup.lead("What is EIP-1559?", listOf(abstract)))
    }

    @Test fun theSimpleSummaryLeadsWhenAProposalHasBoth() {
        val summary = source("S3", "EIP-1559: Fee market change for ETH 1.0 chain — Simple Summary",
            "EIP-1559: Fee market change for ETH 1.0 chain\nStatus: Final\n\nA transaction pricing mechanism that includes fixed-per-block network fee that is burned.")
        val abstract = source("S1", "EIP-1559: Fee market change for ETH 1.0 chain — Abstract",
            "EIP-1559: Fee market change for ETH 1.0 chain (Abstract)\n\nWe introduce a new EIP-2718 transaction type, with the format `0x02 || rlp([...])`.")
        val lead = ProposalLookup.lead("What does EIP-1559 change about fees?", listOf(abstract, summary))!!
        assertEquals("S3", lead.citationId)
        assertTrue(lead.text, lead.text.startsWith("A transaction pricing mechanism"))
    }

    @Test fun theNamedProposalsOwnSummaryIsFetchedByTitleAndQuoted() = runBlocking {
        val abstract = Evidence("eip-7702", "eip-7702:0", "EIP-7702: Set Code for EOAs — Abstract", "https://eips.ethereum.org/EIPS/eip-7702",
            "EIP-7702: Set Code for EOAs\nStatus: Final\n\nAdd a new transaction type that permanently sets the code for an EOA.", 0.0)
        val other = Evidence("erc-4337", "erc-4337:9", "ERC-4337: Account Abstraction — Support for EIP-7702 authorizations", "https://ercs.ethereum.org/ERCS/erc-4337",
            "Bundlers must accept EIP-7702 authorizations.", -5.0)
        val asked = mutableListOf<Pair<String, List<String>>>()
        val retriever = object : Retriever {
            override suspend fun search(query: String, limit: Int, onProgress: suspend (SearchProgress) -> Unit) = listOf(other)
            override suspend fun titled(phrase: String, sections: List<String>, limit: Int): List<Evidence> {
                asked += phrase to sections; return if (phrase == "EIP-7702") listOf(other, abstract) else emptyList()
            }
        }
        val inference = xyz.fieldatlas.inference.FakeInferenceGateway(listOf("It lets an EOA run contract code [S1]."))
        val events = ResearchOrchestrator(retriever, inference).research("Explain EIP-7702 for an EOA owner")
            .toList()
        // ERC-7702 is looked up too: the repositories title some proposals EIP and others ERC.
        assertEquals(listOf("EIP-7702", "ERC-7702").map { it to ProposalLookup.SUMMARY_SECTIONS }, asked)
        val sources = events.filterIsInstance<ResearchEvent.Sources>().last().evidence
        assertEquals("EIP-7702: Set Code for EOAs — Abstract", sources.first().title)
        val shown = events.filterIsInstance<ResearchEvent.Token>().joinToString("") { it.text } +
            events.filterIsInstance<ResearchEvent.Lead>().joinToString("") { it.text }
        assertTrue(shown, "It lets an EOA run contract code [S1]." in shown)
        assertTrue(inference.prompts.last().first.contains(abstract.text))
        assertTrue(events.none { it is ResearchEvent.Lead })
    }
}
