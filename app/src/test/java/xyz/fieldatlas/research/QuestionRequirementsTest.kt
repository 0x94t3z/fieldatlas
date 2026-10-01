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
}
