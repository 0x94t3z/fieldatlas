package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class UnaskedProposalTest {
    private val proposal = Evidence("erc-07802-00", "erc-07802-00:0000", "ERC-7802: Token With Mint/Burn Access Across Chains — Rationale",
        "https://github.com/ethereum/ERCs", "Tokens can be burned on one chain and minted on another.", -2.0)
    private val guide = Evidence("wvs-1-0001", "wvs-1-0001:0000", "Household chemicals — Overview", "Local", "Rinse the eye with water.", -1.0)

    @Test fun anEthereumStandardIsNotEvidenceForAQuestionAboutSomethingElse() {
        assertEquals(listOf(guide),
            EvidenceRelevance.withoutUnaskedProposals(listOf(proposal, guide), "splashed drain cleaner in my eye, it burns"))
    }

    @Test fun aCryptoQuestionOrANamedProposalKeepsIt() {
        assertEquals(listOf(proposal, guide),
            EvidenceRelevance.withoutUnaskedProposals(listOf(proposal, guide), "how do tokens move between chains without being burned twice?"))
        assertEquals(listOf(proposal, guide), EvidenceRelevance.withoutUnaskedProposals(listOf(proposal, guide), "what does ERC-7802 say about burning?"))
        assertEquals(listOf(proposal, guide), EvidenceRelevance.withoutUnaskedProposals(listOf(proposal, guide), null))
    }
}
