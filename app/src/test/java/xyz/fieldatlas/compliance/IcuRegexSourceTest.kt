package xyz.fieldatlas.compliance

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's regex engine (ICU) reads "[:" as the start of a POSIX class name, so a pattern such as "[:=]" that
 * compiles on the JVM can fail on a phone and take the whole class down with it. Every code line is checked here,
 * so the unit suite catches it without a phone.
 */
class IcuRegexSourceTest {
    @Test fun noPatternOpensACharacterClassWithAColon() {
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val code = line.trimStart()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) null
                else Regex("\"(?:[^\"\\\\]|\\\\.)*\"").findAll(line).firstOrNull { "[:" in it.value }?.let { "${file.name}:${i + 1}: ${it.value.take(80)}" }
            }
        }.toList()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
