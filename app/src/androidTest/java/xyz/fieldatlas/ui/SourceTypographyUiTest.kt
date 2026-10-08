package xyz.fieldatlas.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import xyz.fieldatlas.research.Evidence
import xyz.fieldatlas.research.ResearchMetrics
import xyz.fieldatlas.ui.research.AnswerScreen
import xyz.fieldatlas.ui.research.ResearchUiState
import xyz.fieldatlas.ui.research.SourcePreviewSheet
import xyz.fieldatlas.ui.sources.SourceTextContent
import xyz.fieldatlas.ui.sources.SourcesScreen
import xyz.fieldatlas.ui.theme.FieldAtlasTheme

/** Typography fixtures only: never alter real saved answers or source text. */
class SourceTypographyUiTest {
    @get:Rule val compose = foregroundComposeRule()
    private val title = "Maintenance guide: General strategy"
    private val passage = "Original saved passage. Keep the full wording and all citations."
    private val evidence = Evidence("guide", "chunk", title, "en.wikipedia.org", passage, 1.0)

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test fun sourceListAndDisclosureUseTheSharedCompactReadingScale() {
        val answer = "A saved result.\n\n[S1]"
        compose.setContent { FieldAtlasTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnswerScreen(ResearchUiState(question = "Explain the guide", answer = answer, sources = listOf(evidence),
                    metrics = ResearchMetrics(10, 20, 4000, 20, setOf("S1"), false)), {}, {}, {})
            }
        } }
        compose.onNodeWithText("A saved result.").performScrollTo()
        assertEquals(15.sp, textLayout("A saved result.").layoutInput.style.fontSize)
        assertEquals(24.sp, textLayout("A saved result.").layoutInput.style.lineHeight)
        compose.onNodeWithText(title).performScrollTo()
        assertEquals(14.sp, textLayout(title).layoutInput.style.fontSize)
        compose.onNodeWithText("Answer details").performScrollTo()
        assertEquals(13.sp, textLayout("Answer details").layoutInput.style.fontSize)
    }

    @Test fun sourcePreviewHasReadableCompactProseAndKeepsItsNavigation() {
        var opened = -1
        compose.setContent { FieldAtlasTheme {
            SourcePreviewSheet(listOf(evidence), 0, true, null, {}, { opened = it }, {})
        } }
        assertEquals(16.sp, textLayout(title).layoutInput.style.fontSize)
        assertEquals(15.sp, textLayout(passage).layoutInput.style.fontSize)
        assertEquals(24.sp, textLayout(passage).layoutInput.style.lineHeight)
        compose.onNodeWithText("Cited in this answer").assertIsDisplayed()
        compose.onNodeWithText("Full source").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, opened) }
    }

    @Test fun fullSourceUsesTheSameProseSizeAndRetainsLiteralText() {
        compose.setContent { FieldAtlasTheme { SourceTextContent(passage, null) } }
        assertEquals(15.sp, textLayout(passage).layoutInput.style.fontSize)
        assertEquals(24.sp, textLayout(passage).layoutInput.style.lineHeight)
        compose.onNodeWithText(passage).assertIsDisplayed()
    }

    @Test fun technicalIdentifiersUsesTheSameCompactDisclosureScaleExpandedAndCollapsed() {
        compose.setContent { FieldAtlasTheme {
            SourcesScreen(evidence, 1, 1, "Example licence", {})
        } }
        compose.onNodeWithText("Technical identifiers").performScrollTo()
        assertEquals(13.sp, textLayout("Technical identifiers").layoutInput.style.fontSize)
        assertEquals(21.sp, textLayout("Technical identifiers").layoutInput.style.lineHeight)
        compose.onNodeWithText("Technical identifiers").performClick()
        compose.onNodeWithText("Hide technical identifiers").performScrollTo()
        assertEquals(13.sp, textLayout("Hide technical identifiers").layoutInput.style.fontSize)
        compose.onNodeWithText("Document").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chunk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hide technical identifiers").performScrollTo().performClick()
        compose.onNodeWithText("Technical identifiers").assertIsDisplayed()
        compose.onNodeWithText("Document").assertDoesNotExist()
    }

    @Test fun technicalIdentifiersRemainsReachableInDarkModeAtDoubleTextSize() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SourcesScreen(evidence, 1, 1, "Example licence", {})
            }
        } }
        compose.onNodeWithText("Technical identifiers").performScrollTo().performClick()
        compose.onNodeWithText("Chunk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hide technical identifiers").performScrollTo().performClick()
        compose.onNodeWithText("Technical identifiers").assertIsDisplayed()
    }

    @Test fun uncitedDarkPreviewAtDoubleTextSizeDoesNotClaimCitation() {
        compose.setContent { FieldAtlasTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SourcePreviewSheet(listOf(evidence), 0, false, null, {}, {}, {})
            }
        } }
        compose.onNodeWithText("Cited in this answer").assertDoesNotExist()
        compose.onNodeWithText("Provided · not cited").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Full source").performScrollTo().assertIsDisplayed()
    }
}
