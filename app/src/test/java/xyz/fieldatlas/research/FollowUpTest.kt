package xyz.fieldatlas.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun aFollowUpPointingBackIsAboutTheNameTheAnswerGave() {
        assertEquals("Tokyo: how many people live there",
            FollowUp.readAs("What is the capital of Japan", "how many people live there",
                answer = "The capital of Japan is Tokyo, which is the biggest city in the country,[1][2]."))
        assertEquals("Mercury (which planet is closest to the sun): how long is a year on it", FollowUp.readAs("which planet is closest to the sun", "how long is a year on it",
            answer = "Mercury is the planet closest to the Sun [S1]."))
        // An unverified model guess names nothing: a wrong "Jupiter" would send every follow-up the wrong way.
        assertEquals("which planet is closest to the sun: how long is a year on it", FollowUp.readAs("which planet is closest to the sun", "how long is a year on it",
            answer = "## Model explanation—not verified against saved sources\n\nBased on the provided excerpt, Jupiter is the planet closest to the Sun."))
        assertEquals("Antonio Vivaldi: where was he from", FollowUp.readAs("who composed the four seasons", "where was he from",
            answer = "The Four Seasons (Italian: Le quattro stagioni) are a group of four violin concerti by Italian composer Antonio Vivaldi, each of which [S3]."))
        assertEquals("Hummingbirds: how fast do its wings beat", FollowUp.readAs("what bird can fly backwards", "how fast do its wings beat",
            answer = "Hummingbirds are the only group of birds able to fly backwards [S1]."))
        assertEquals("Canada (which country has the most lakes): roughly how many", FollowUp.readAs("which country has the most lakes", "roughly how many",
            answer = "Canada has the most lakes, with more than 60% of the world's lakes located there [S1]."))
        assertEquals("Wellington (whats the capital of new zealand): is it really that windy", FollowUp.readAs("whats the capital of new zealand", "is it really that windy",
            answer = "The capital of New Zealand has been Wellington since 1865, following the decision by three Australian commissioners to settle the location [S1]."))
        assertEquals("Qin Shi Huang: is it true he was buried with a clay army", FollowUp.readAs("Who was the first emperor of China?", "is it true he was buried with a clay army",
            answer = "The first emperor of China was Qin Shi Huang, also known as Ying Zheng, who was born in 259 B.C. [S1]."))
    }

    @Test fun anAnswerThatNamesNothingKeepsTheEarlierQuestion() {
        // It says it does not know; "Egypt" is mentioned, not given as the answer.
        assertEquals("which country has the most pyramids: can tourists go see them",
            FollowUp.readAs("which country has the most pyramids", "can tourists go see them",
                answer = "Based on the provided excerpt, there is no information on which country holds the most; it states that over 100 pyramids exist in Egypt."))
        assertEquals("who wrote romeo and juliet: where was he born",
            FollowUp.readAs("who wrote romeo and juliet", "where was he born",
                answer = "The provided excerpts do not state who wrote Romeo and Juliet. They only confirm that Pyotr Ilyich Tchaikovsky composed an orchestral work of that name."))
        // Only a quoted excerpt, no answer of its own.
        assertEquals("what is the tallest kind of tree in the world: where can i see them",
            FollowUp.readAs("what is the tallest kind of tree in the world", "where can i see them",
                answer = "## From your saved sources\n\n> A tree is a tall plant with a trunk and branches made of wood. [S1]"))
        // The question asked about Kailash itself, not for a name, so Tibet is not what "it" means.
        assertEquals("where is mount kailash: how high is it", FollowUp.readAs("where is mount kailash", "how high is it",
            answer = "Mount Kailash is a 6,638 metres mountain located in the Gangdise Shan mountain range in Tibet [S1]."))
        // A month is when, not what.
        assertNull(FollowUp.answerName("what is the first month of the monsoon", "June is the first month of the monsoon."))
    }

    @Test fun aChainKeepsItsLeadNotEveryFollowUp() {
        assertEquals("tell me about fiji: do we need a visa",
            FollowUp.readAs("tell me about fiji: which island should we stay on", "do we need a visa", root = "tell me about fiji"))
        // A reading that moved to a new subject is a question of its own.
        assertEquals("best street food in Hanoi: is it cheap",
            FollowUp.readAs("best street food in Hanoi?", "is it cheap", root = "best street food in Bangkok"))
        // A name the earlier answer gave stays the subject of the chain.
        assertEquals("Tokyo: is it expensive", FollowUp.readAs("Tokyo: how many people live there", "is it expensive", root = "What is the capital of Japan"))
    }

    @Test fun spellingsFoldTheSameFromBothSides() {
        for ((british, american) in listOf("colour" to "color", "colours" to "colors", "coloured" to "colored", "favourite" to "favorite",
                "centre" to "center", "metres" to "meters", "anaemia" to "anemia", "paediatric" to "pediatric", "oesophagus" to "esophagus",
                "diarrhoea" to "diarrhea", "catalogue" to "catalog", "licence" to "license", "defence" to "defense", "harbour" to "harbor")) {
            assertEquals(british, QuestionText.foldSpelling(american), QuestionText.foldSpelling(british))
        }
        for (kept in listOf("hour", "tour", "your", "rise", "rising", "called", "falling", "does", "shoe", "evidence", "science", "wise"))
            assertEquals(kept, kept, QuestionText.foldSpelling(kept))
    }

    @Test fun aReadingThatNamesTheEarlierAnswerIsAboutThatName() {
        val first = FollowUp.Turn("What is the capital of Japan", "What is the capital of Japan", "The capital of Japan is Tokyo [S1].")
        assertEquals(true, FollowUp.namesSubject("Tokyo: how many people live there", listOf(first)))
        assertEquals("Tokyo", FollowUp.namedSubject("Tokyo: how many people live there", listOf(first)))
        assertEquals("Wellington", FollowUp.namedSubject("Wellington (whats the capital of new zealand): is it really that windy", listOf(first)))
        assertEquals(true, FollowUp.namesSubject("Wellington (whats the capital of new zealand): is it really that windy", listOf(first)))
        // A joined reading opens with the earlier question, so the earlier answer's passages still lead.
        assertEquals(false, FollowUp.namesSubject("What is the capital of Japan: how many people live there", listOf(first)))
        // Later in the chain the earlier answer was about Tokyo itself.
        val second = FollowUp.Turn("how many people live there", "Tokyo: how many people live there", "About 14 million people live in Tokyo [S1].")
        assertEquals(false, FollowUp.namesSubject("Tokyo: is it expensive", listOf(first, second)))
        assertEquals(false, FollowUp.namesSubject("Tokyo: is it expensive", emptyList()))
    }

    @Test fun aFigureAskedInEverydayWordsSearchesTheWordArticlesUse() {
        assertEquals(listOf("population"), QueryExpansion.figureTerms("Tokyo: how many people live there"))
        assertEquals(listOf("height", "metres"), QueryExpansion.figureTerms("how tall is it"))
        assertEquals(emptyList<String>(), QueryExpansion.figureTerms("what is the capital of Japan"))
    }

    @Test fun aLongChainStartsAgainFromTheFirstQuestion() {
        val long = "x".repeat(210)
        assertEquals("Best vegan food in Lisbon: and cheap ones?",
            FollowUp.readAs(long, "and cheap ones?", root = "Best vegan food in Lisbon"))
        assertEquals("Best vegan food in Lisbon", FollowUp.readAs("Best vegan food in Lisbon", "   "))
    }

    @Test fun theModelIsShownTheConversationInPlainProse() {
        val turns = listOf(
            FollowUp.Turn("What is the capital of Japan", "What is the capital of Japan",
                "The capital of Japan is Tokyo, which is the biggest city in the country,[1][2]. It has been the capital since 1868 [S1]. A third sentence."),
            FollowUp.Turn("how many people live there", "Tokyo: how many people live there",
                "## Model explanation—not verified against saved sources\n\nThe provided excerpts do not contain information about the population.\n\n## Saved excerpts\n\n> Tokyo is the capital."),
            FollowUp.Turn("is it expensive", "Tokyo: is it expensive", "## From your saved sources\n\n> Tokyo is expensive [S1]"),
        )
        assertEquals("EARLIER IN THIS CONVERSATION:\nQ: What is the capital of Japan\nA: The capital of Japan is Tokyo, which is the biggest city in the country. It has been the capital since 1868.",
            FollowUp.conversation(turns.take(1)))
        // The first question always stays; between it and the last turn only the latest is kept.
        assertEquals("EARLIER IN THIS CONVERSATION:\nQ: What is the capital of Japan\nA: The capital of Japan is Tokyo, which is the biggest city in the country. It has been the capital since 1868." +
            "\nQ: is it expensive\nA: " + FollowUp.NOTHING_FOUND, FollowUp.conversation(turns))
        assertEquals(FollowUp.NOTHING_FOUND, FollowUp.summary(turns[1].answer))
        assertEquals("", FollowUp.conversation(emptyList()))
        val long = FollowUp.summary("Word ".repeat(120).trim() + ". Second sentence here.")
        assert(long.length <= 321 && long.endsWith("…")) { long }
    }
}
