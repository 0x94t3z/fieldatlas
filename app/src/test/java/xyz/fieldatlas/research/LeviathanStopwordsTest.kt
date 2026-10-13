package xyz.fieldatlas.research

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app skips native search when only Leviathan's stopwords remain; the list must be Leviathan's own. */
class LeviathanStopwordsTest {
    @Test fun theStopwordsMatchTheVendoredCrate() {
        val source = File("../third_party/leviathan/src/text.rs").readText()
        val block = Regex("const STOPWORDS: &\\[&str] = &\\[(.*?)];", RegexOption.DOT_MATCHES_ALL).find(source)!!.groupValues[1]
        assertEquals(Regex("\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toSet(), LEVIATHAN_STOPWORDS)
    }
}
