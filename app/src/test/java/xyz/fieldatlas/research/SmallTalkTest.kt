package xyz.fieldatlas.research

import org.junit.Assert.*
import org.junit.Test

class SmallTalkTest {
    @Test fun greetingsThanksAndQuestionsAboutTheAppGetAnInstantReply() {
        for (message in listOf("hello", "Hi!", "hey there", "Good morning", "hi field atlas", "Hello :)", "yo")) {
            assertEquals(message, SmallTalk.GREETING, SmallTalk.reply(message))
        }
        for (message in listOf("thanks", "Thank you so much!", "thx", "thanks again", "ok thanks", "Cheers")) {
            assertEquals(message, SmallTalk.THANKS, SmallTalk.reply(message))
        }
        for (message in listOf("how are you?", "hi, how are you doing", "what's up", "How’s it going")) {
            assertEquals(message, SmallTalk.HOW_ARE_YOU, SmallTalk.reply(message))
        }
        for (message in listOf("who are you", "Hi! What can you do?", "what is this app")) {
            assertEquals(message, SmallTalk.IDENTITY, SmallTalk.reply(message))
        }
        assertEquals(SmallTalk.GOODBYE, SmallTalk.reply("bye"))
        assertEquals(SmallTalk.THANKS, SmallTalk.reply("thanks, bye"))
    }

    @Test fun anythingElseInTheMessageKeepsItsRoute() {
        for (message in listOf(
            "hi, my friend is choking", "hello how do I treat a burn", "thanks, and what about burns?",
            "help", "hi help", "Hello world program in Python", "hey jude", "Who are you Tolkien characters",
            "thank you for the music", "what are you supposed to do for a sprain", "good morning in Spanish",
            "so much", "okay", "sup with cats", "", "   ", "who are the Beatles",
        )) {
            assertNull(message, SmallTalk.reply(message))
        }
    }

    @Test fun smallTalkIsAnsweredBeforeAnySearchAndNeedsNoModel() {
        assertEquals(SmallTalk.GREETING, QuestionRequirements.response("hello", false))
        // A greeting sent with a file is about the file.
        assertNull(QuestionRequirements.response("hello", true))
    }
}
