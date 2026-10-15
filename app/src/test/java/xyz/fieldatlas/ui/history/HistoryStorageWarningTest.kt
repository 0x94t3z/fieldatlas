package xyz.fieldatlas.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.research.HistoryStorage

class HistoryStorageWarningTest {
    @Test fun freeSpaceIsShownInMegabytesOrGigabytes() {
        assertEquals("212 MB", formatFreeSpace(212L * 1024 * 1024))
        assertEquals("1.5 GB", formatFreeSpace(1536L * 1024 * 1024))
    }

    @Test fun lowSpaceSaysHowMuchIsFreeAndWhatToDo() {
        val text = storageWarningText(HistoryStorage(freeBytes = 150L * 1024 * 1024))
        assertTrue(text, text.startsWith("150 MB free."))
        assertTrue(text, "delete answers you no longer need" in text)
    }

    @Test fun aFailedSaveSaysTheAnswerWasNotKept() {
        val text = storageWarningText(HistoryStorage(freeBytes = 10L * 1024 * 1024 * 1024, lastSaveFailed = true))
        assertTrue(text, text.startsWith("There wasn't room to save it to History."))
    }
}
