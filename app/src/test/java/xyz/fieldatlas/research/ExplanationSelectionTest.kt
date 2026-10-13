package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class ExplanationSelectionTest {
    private fun row(id: String, title: String, text: String) =
        Evidence(id, "$id:0", title, "fixture://$id", text, 0.0)

    @Test fun comparisonPrefersAnExplanatoryJointPassageNotTableFurniture() {
        val first = row("first", "Neral", "Neral is a solid material used in the fixture.")
        val second = row("second", "Velin", "Velin is a liquid material used in the fixture.")
        val empty = row("empty", "Neral and Velin", "The table below compares Neral and Velin.")
        val joint = row("joint", "Materials: Neral and Velin",
            "Neral and Velin are test materials with different storage needs. Neral is a solid kept in a dry container. " +
                "Velin is a liquid kept in a sealed container. The dry container prevents moisture exposure while the sealed container prevents leakage. " +
                "These requirements apply only to the laboratory fixtures in this report.")
        val packed = PromptBuilder.buildKnowledge("How does Neral differ from Velin?", listOf(first, second, empty, joint),
            listOf(first, second), emptyList(), 8192, null)
        assertEquals(listOf(joint), packed.sources.map { it.evidence })
        assertTrue(packed.sources.single().excerpt.contains("only to the laboratory"))
    }

    @Test fun aFunctionExplainedAcrossSentencesIsNotDiscarded() {
        val question = "How do chloroplasts help plants make food?"
        val direct = row("cells", "Eukaryotic Cells: Plant Cells: Chloroplasts",
            "Chloroplasts are plant cell organelles that carry out photosynthesis. " +
                "Photosynthesis uses carbon dioxide, water, and light energy to make glucose and oxygen. " +
                "Plants make their own food, like sugars used in cellular respiration.")
        val unrelated = row("food", "Food safety", "Plants can be food. Cooking food helps prevent illness.")
        val kept = EvidenceRelevance.keep(listOf(direct, unrelated), FtsQuery.from(question)!!.terms, question = question)
        assertTrue(kept.contains(direct))
        assertFalse(kept.contains(unrelated))
    }

    @Test fun definitionDoesNotMixInSubtypeOrRegulationPassages() {
        val direct = row("basic", "Saved processes", "Recycling is a process that recovers materials for reuse.")
        val subtype = row("subtype", "Special recycling", "Targeted recycling is a specialised recovery process.")
        val regulation = row("regulation", "Rules", "Recycling is regulated by regional rules.")
        val packed = PromptBuilder.buildKnowledge("What is recycling?", listOf(subtype, regulation, direct),
            emptyList(), emptyList(), 8192, null)
        assertEquals(listOf(direct), packed.sources.map { it.evidence })
        assertNull(EvidenceRelevance.definitionSubject("What is recycling and how does it work?"))
        assertFalse(EvidenceRelevance.directlyDefines(subtype, "recycling"))
        assertFalse(EvidenceRelevance.directlyDefines(regulation, "recycling"))
    }

    @Test fun conflictingDefinitionsAndOriginalIdentitiesArePreserved() {
        val first = row("first", "Process A", "Valen is a process requiring dry fixtures.")
        val second = row("second", "Process B", "Valen is a process requiring wet fixtures.")
        val packed = PromptBuilder.buildKnowledge("What is Valen?", listOf(first, second), emptyList(), emptyList(), 8192, null)
        assertEquals(listOf(first, second), packed.sources.map { it.evidence })
    }

    @Test fun encyclopediaRedirectsAreNotScientificSynonyms() {
        val original = row("process", "Neral", "Neral is a process with several phases.\n\nAlso known as: Neral phase one")
            .copy(source = "https://simple.wikipedia.org/w/index.php?oldid=123")
        assertEquals("Neral is a process with several phases.", PromptBuilder.excerptForKnowledge("What is Neral?", original))
        assertEquals(original.text, PromptBuilder.excerptForKnowledge("What aliases redirect to Neral?", original))
        val file = original.copy(source = "file://notes.txt")
        assertEquals(file.text, PromptBuilder.excerptForKnowledge("What is Neral?", file))
    }
}
