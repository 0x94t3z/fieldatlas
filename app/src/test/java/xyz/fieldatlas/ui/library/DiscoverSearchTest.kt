package xyz.fieldatlas.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverSearchTest {
    @Test fun everyWordMustAppearInTheNameOrDescription() {
        assertTrue(DiscoverSearch.matches("spanish voice", "Spanish reading voice", "Español · Supertonic 3"))
        assertTrue(DiscoverSearch.matches("ageing", "Biology & longevity", "Biology, health, ageing"))
        assertFalse(DiscoverSearch.matches("spanish voice", "Spanish encyclopedia", "Wikipedia en español"))
    }

    @Test fun caseAndAccentsDoNotMatter() {
        assertTrue(DiscoverSearch.matches("ESPANOL", "Spanish encyclopedia", "Wikipedia en español"))
        assertTrue(DiscoverSearch.matches("café", "Travel places", "Cafe, hotels, sights"))
    }

    @Test fun todaysCatalogueIsNeverFoldedSoANewEditionShows() {
        assertFalse(DiscoverSearch.folds(7))
        assertFalse(DiscoverSearch.folds(DiscoverSearch.FOLD_OVER))
        assertTrue(DiscoverSearch.folds(DiscoverSearch.FOLD_OVER + 1))
    }

    @Test fun nothingTypedMatchesEverything() {
        assertTrue(DiscoverSearch.matches("  ", "Encyclopedia"))
        assertTrue(DiscoverSearch.matches("", null))
    }
}
