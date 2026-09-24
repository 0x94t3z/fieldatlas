package xyz.fieldatlas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorBusTest {
    @Test fun startsEmpty() {
        val bus = AppErrorBus()
        assertTrue(bus.notices.value.isEmpty())
        assertEquals("", bus.renderText())
    }

    @Test fun reportsKeepOrderAreaAndMessage() {
        val bus = AppErrorBus()
        bus.report("Voice", "mic busy")
        bus.report("Library", java.io.IOException("disk full"))
        assertEquals(
            "[Voice] mic busy\n[Library] disk full",
            bus.renderText(),
        )
    }

    @Test fun blankMessagesAreIgnored() {
        val bus = AppErrorBus()
        bus.report("Model", "  ")
        assertTrue(bus.notices.value.isEmpty())
    }

    @Test fun oldestNoticesFallOffFirst() {
        val bus = AppErrorBus(maxNotices = 3)
        listOf("a", "b", "c", "d").forEach { bus.report("Research", it) }
        assertEquals(listOf("b", "c", "d"), bus.notices.value.map { it.message })
    }

    @Test fun dismissAndClearBothWork() {
        val bus = AppErrorBus()
        bus.report("Model", "one")
        bus.report("Model", "two")
        bus.dismiss(bus.notices.value.first().id)
        assertEquals(listOf("two"), bus.notices.value.map { it.message })
        bus.clear()
        assertTrue(bus.notices.value.isEmpty())
    }

    @Test fun throwableWithoutMessageFallsBackToClassName() {
        val bus = AppErrorBus()
        bus.report("Model", IllegalStateException())
        assertEquals("[Model] IllegalStateException", bus.renderText())
    }
}
