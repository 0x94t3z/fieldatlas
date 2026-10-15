package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpTest {
    @Test fun aNewSubjectReplacesTheOldOne() {
        assertEquals("What are the tipping customs in south korea?",
            FollowUp.readAs("What are the tipping customs in Japan?", "what about south korea?"))
        assertEquals("best street food in Hanoi?", FollowUp.readAs("best street food in Bangkok", "how about Hanoi?"))
        assertEquals("cheap hotels in Rome for kids?", FollowUp.readAs("cheap hotels in Rome near the station", "and for kids?"))
        // A bare "and …" may narrow rather than move the question: both are kept.
        assertEquals("Best vegan food in Lisbon: and cheap ones?", FollowUp.readAs("Best vegan food in Lisbon", "and cheap ones?"))
        assertEquals("is it safe to swim in the sea during winter?",
            FollowUp.readAs("is it safe to swim in the sea in summer?", "what about during winter?"))
    }

    @Test fun anythingElseIsAskedAboutTheEarlierQuestion() {
        assertEquals("Best vegan food in Lisbon: which one is closest to the castle?",
            FollowUp.readAs("Best vegan food in Lisbon", "which one is closest to the castle?"))
        // A new question after "and" is not a new subject.
        assertEquals("how do i get from budapest airport to the city centre: and how much does it cost?",
            FollowUp.readAs("how do i get from budapest airport to the city centre?", "and how much does it cost?"))
        assertEquals("my friend has a bad sunburn on her shoulders, what should we do: and what if blisters show up?",
            FollowUp.readAs("my friend has a bad sunburn on her shoulders, what should we do?", "and what if blisters show up?"))
    }

    @Test fun aLongChainStartsAgainFromTheFirstQuestion() {
        val long = "x".repeat(210)
        assertEquals("Best vegan food in Lisbon: and cheap ones?",
            FollowUp.readAs(long, "and cheap ones?", root = "Best vegan food in Lisbon"))
        assertEquals("Best vegan food in Lisbon", FollowUp.readAs("Best vegan food in Lisbon", "   "))
    }
}
