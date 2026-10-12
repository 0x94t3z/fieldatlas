package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class QuestionRequirementsTest {
    @Test fun missingFileIsRequestedInsteadOfSubstitutingLibraryMaterial() {
        assertNotNull(QuestionRequirements.response("According to my attached report, what were my blood test results?", false))
        assertNotNull(QuestionRequirements.response("Summarize the uploaded document.", false))
        assertNull(QuestionRequirements.response("According to my attached report, what were my results?", true))
    }

    @Test fun mentionsAndGeneralFileQuestionsAreNotMissingInputRequests() {
        assertNull(QuestionRequirements.response("How do I read an attached report?", false))
        assertNull(QuestionRequirements.response("Explain the phrase \"read the attached report\".", false))
        assertNull(QuestionRequirements.response("Summarize my saved sources on cell division.", false))
    }

    @Test fun liveLocationStatusCannotComeFromAnUnrelatedPaper() {
        assertNotNull(QuestionRequirements.response("Using only my saved sources, which roads near me are closed right now?", false))
        assertNull(QuestionRequirements.response("Explain why roads can be closed during winter.", false))
        assertNull(QuestionRequirements.response("Which museums can I visit in Tokyo?", false))
        assertNull(QuestionRequirements.response("Explain this report about roads near me currently closed.", true))
    }

    @Test fun undisclosedPersonalRecordsAreMissingInputsNotSearchTopics() {
        for (question in listOf("What diagnosis did an unnamed private person receive yesterday?",
            "Which test results did an anonymous patient receive?",
            "What is the bank balance of an unidentified customer?",
            "Using only saved sources, what was a private user's account number?")) {
            assertTrue(question, QuestionRequirements.response(question, false)?.contains("private records") == true)
            assertNull(QuestionRequirements.response(question, true))
        }
    }

    @Test fun privateRecordEducationQuotationsAndPublicFactsKeepTheirRoutes() {
        for (question in listOf("How is a diagnosis explained to an anonymous patient?",
            "What is a diagnosis?", "Explain how private medical records are protected.",
            "What medical records should an anonymous patient bring?",
            "Which test results can an anonymous patient request?",
            "What test results does an anonymous patient need before surgery?",
            "Which private messages does a private user need to retain for a complaint?",
            "What is a private user's account number format?",
            "What is an anonymous patient's diagnosis code format?",
            "What rights protect the medical records of an anonymous patient?",
            "What is a diagnosis for an anonymous patient?",
            "What diagnosis was reported in the published biography?",
            "Explain the question \"What diagnosis did an unnamed private person receive?\".")) {
            assertNull(question, QuestionRequirements.response(question, false))
        }
    }
}
