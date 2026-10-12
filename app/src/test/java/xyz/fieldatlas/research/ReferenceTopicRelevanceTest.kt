package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

/** Topic eligibility only: retaining a passage does not establish answer completeness or truth. */
class ReferenceTopicRelevanceTest {
    private fun row(title: String, text: String) = Evidence("wp-fixture", "wp-fixture:0",
        "$title — Overview", "fixture://reference", text, 0.0)
    private fun variants(plain: String) = listOf(plain, "According to the saved sources, $plain")
    private fun kept(question: String, vararg rows: Evidence) = EvidenceRelevance.keep(
        rows.toList(), FtsQuery.from(question)!!.terms, question = question)

    @Test fun anUnaskedMultiwordSubjectCannotBorrowOneInteriorTitleWord() {
        val unrelated = row("Personal health application", "A personal health application is a software tool. " +
            "Health information can improve treatment understanding. Users may need help storing records.")
        for (q in variants("Why is a plausible health mechanism not proof that a treatment works, and when is urgent help needed?")) {
            assertTrue(q, kept(q, unrelated).isEmpty())
        }
    }

    @Test fun aProperNamePrefixIsNotTheRequestedSubject() {
        val unrelated = row("Solaris vehicle", "A Solaris vehicle is a bus. Its batteries supply electricity. " +
            "The storage tanks hold fuel, and demand varies during service.")
        for (q in variants("Compare solar electricity and battery storage for evening demand.")) {
            assertTrue(q, kept(q, unrelated).isEmpty())
        }
    }

    @Test fun twoTitleHitsDoNotAuthorizeTheSelfDefinitionOfAnotherQualifiedTopic() {
        val unrelated = row("Museum energy storage", "Museum energy storage is an exhibition project. " +
            "Its visitors learn about demand. The evening tour has several stops.")
        for (q in variants("How does energy storage support evening demand across remote communities?")) {
            assertTrue(q, kept(q, unrelated).isEmpty())
        }
    }

    @Test fun explicitNamesAndPluralSubjectsKeepTheirOwnDefinitions() {
        val rows = listOf(
            "How do Solaris vehicles supply electricity for evening service?" to row("Solaris vehicle",
                "A Solaris vehicle is a bus. Batteries supply electricity during evening service."),
            "How do thermal batteries store energy for evening demand?" to row("Thermal battery",
                "A thermal battery is a device that stores energy. Its heat can be used in the evening."),
            "How do heat-pumps supply warmth during winter?" to row("Heat pump",
                "A heat pump is a device that transfers thermal energy. It can supply warmth during winter."),
        )
        for ((plain, article) in rows) for (q in variants(plain)) assertEquals(q, listOf(article), kept(q, article))
    }

    @Test fun explanatoryPassagesMayAnswerAcrossArticleTopics() {
        val cross = row("Grid operation", "Grid operation is the coordination of generators. " +
            "Battery storage supplies evening demand because stored solar electricity remains available after sunset.")
        for (q in variants("Compare solar electricity and battery storage for evening demand.")) {
            assertEquals(q, listOf(cross), kept(q, cross))
        }
    }

    @Test fun ordinaryNounFormsDoNotDependOnAnOverShortenedStem() {
        for ((single, plural) in mapOf("case" to "cases", "horse" to "horses", "bus" to "buses",
            "box" to "boxes", "study" to "studies", "battery" to "batteries", "key" to "keys")) {
            val article = row(single, "A $single is a component container in this fictional fixture. " +
                "Components remain inside during repeated journeys.")
            for (q in variants("How do $plural protect components during repeated wet journeys?")) {
                assertEquals(q, listOf(article), kept(q, article))
            }
        }
    }

    @Test fun sourceSpecificFindingsAndOrdinaryFilesDoNotNeedAnEncyclopediaDefinition() {
        val study = row("Measured treatment", "The study measured treatment response in patients after twelve weeks.")
            .copy(documentId = "study", title = "Measured treatment — Results")
        val question = "According to the saved sources, how was treatment response measured in patients?"
        assertEquals(listOf(study), kept(question, study))
        val file = study.copy(documentId = "attachment", title = "Results — Overview")
        assertEquals(listOf(file), kept(question, file))
    }

    @Test fun relatedShortTitlesStillRequireTheNarrowingModifier() {
        val leather = row("Wallet", "A wallet is a container for cash. People keep their wallets safe. " +
            "Some wallets contain a private key printed on paper.")
        val tide = row("Tide", "A tide is the rising and falling of the ocean surface. Tides rise and fall each day.")
        val agriculture = row("Agriculture", "Agriculture is the cultivation of crops. It changed human societies. " +
            "Settlements grew, with one disadvantage being seasonal hunger.")
        for (q in variants("How does a hardware wallet keep my private key safe?")) assertTrue(q, kept(q, leather).isEmpty())
        for (q in variants("How do ocean tides rise and fall each day?")) assertEquals(q, listOf(tide), kept(q, tide))
        for (q in variants("Explain two ways agriculture changed human societies, and one disadvantage.")) {
            assertEquals(q, listOf(agriculture), kept(q, agriculture))
        }
    }
}
