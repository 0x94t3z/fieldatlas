package xyz.fieldatlas.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.fieldatlas.ui.presentation.downloadLine
import xyz.fieldatlas.ui.presentation.inLine
import xyz.fieldatlas.ui.presentation.progressBytes
import xyz.fieldatlas.ui.presentation.timeLeft

class DownloadQueueTest {
    private class FakeEngine : DownloadEngine {
        val started = mutableListOf<String>()
        val stopped = mutableListOf<Pair<String, Boolean>>()
        var refuse: String? = null
        override fun start(item: DownloadItem): Boolean {
            if (refuse == item.key) return false
            started += item.key; return true
        }
        override fun failure(item: DownloadItem) = "Could not start"
        override fun stop(item: DownloadItem, keepForLater: Boolean) { stopped += item.key to keepForLater }
    }

    private fun pack(id: String, mb: Long) = KnowledgeCatalogEntry(id = id, version = "2026.10.05", title = id, description = "",
        bytes = mb * 1_000_000, sha256 = "0".repeat(64), url = "https://example.org/$id.fapack", license = "CC0-1.0")

    private val travel = DownloadItem.Knowledge(pack("travel", 215))
    private val vegan = DownloadItem.Knowledge(pack("vegan", 41))

    private fun queue(engine: FakeEngine, wifi: Boolean = true, onlyOnWifi: Boolean = true, clock: LongArray = longArrayOf(0),
                      finished: MutableList<String> = mutableListOf()) =
        DownloadQueue(engine, onlyOnWifi, now = { clock[0] }, onFinished = { finished += it.key }).apply { onNetwork(wifi) }

    @Test fun aSecondDownloadWaitsItsTurnAndStartsByItself() {
        val engine = FakeEngine(); val finished = mutableListOf<String>()
        val q = queue(engine, finished = finished)
        q.enqueue(travel); q.enqueue(vegan); q.enqueue(DownloadItem.Voice)
        assertEquals(listOf(travel.key), engine.started)
        assertEquals(listOf(vegan, DownloadItem.Voice), q.state.value.queued)
        q.onEnded(travel, DownloadOutcome.Done)
        assertEquals(listOf(travel.key), finished)
        assertEquals(vegan, q.state.value.active)
        assertEquals(listOf(travel.key, vegan.key), engine.started)
        // Asking again for what is already running or in line changes nothing.
        q.enqueue(vegan); q.enqueue(DownloadItem.Voice)
        assertEquals(listOf<DownloadItem>(DownloadItem.Voice), q.state.value.queued)
    }

    @Test fun pausingLetsTheNextStartAndPauseAllEmptiesTheLine() {
        val engine = FakeEngine()
        val q = queue(engine)
        q.enqueue(travel); q.enqueue(vegan); q.enqueue(DownloadItem.Voice)
        q.pause()
        assertEquals(listOf(travel.key to false), engine.stopped)
        q.onEnded(travel, DownloadOutcome.Stopped)
        assertEquals(vegan, q.state.value.active)
        q.pauseAll()
        q.onEnded(vegan, DownloadOutcome.Stopped)
        assertNull(q.state.value.active)
        assertTrue(q.state.value.queued.isEmpty())
        assertTrue(q.state.value.failures.isEmpty())
    }

    @Test fun aFailureIsKeptPerItemUntilRetriedAndTheLineMovesOn() {
        val engine = FakeEngine()
        val q = queue(engine)
        q.enqueue(travel); q.enqueue(vegan)
        q.onEnded(travel, DownloadOutcome.Failed("Can't reach the download server."))
        assertEquals("Can't reach the download server.", q.state.value.failures.getValue(travel.key).message)
        assertEquals(vegan, q.state.value.active)
        q.enqueue(travel)
        assertFalse(travel.key in q.state.value.failures)
        assertEquals(listOf<DownloadItem>(travel), q.state.value.queued)
    }

    @Test fun aDownloadThatCannotStartIsReportedAndTheNextOneRuns() {
        val engine = FakeEngine().apply { refuse = travel.key }
        val q = queue(engine)
        q.enqueue(travel)
        assertNull(q.state.value.active)
        assertEquals("Could not start", q.state.value.failures.getValue(travel.key).message)
    }

    @Test fun onMobileDataItWaitsForWifiThenStartsByItself() {
        val engine = FakeEngine()
        val q = queue(engine, wifi = false)
        q.enqueue(travel)
        assertTrue(q.state.value.waitingForWifi)
        assertTrue(engine.started.isEmpty())
        q.onNetwork(true)
        assertFalse(q.state.value.waitingForWifi)
        assertEquals(listOf(travel.key), engine.started)
    }

    @Test fun leavingWifiHoldsTheDownloadWithoutCountingItAsAPause() {
        val engine = FakeEngine()
        val q = queue(engine)
        q.enqueue(travel); q.enqueue(vegan)
        q.onNetwork(false)
        assertEquals(listOf(travel.key to true), engine.stopped)
        q.onEnded(travel, DownloadOutcome.Stopped)
        assertEquals(travel, q.state.value.active)
        assertTrue(q.state.value.waitingForWifi)
        assertEquals(listOf<DownloadItem>(vegan), q.state.value.queued)
        q.onNetwork(true)
        assertEquals(listOf(travel.key, travel.key), engine.started)
    }

    @Test fun mobileDataCanBeAllowedOnceOrTurnedOnForAll() {
        val engine = FakeEngine()
        val q = queue(engine, wifi = false)
        q.enqueue(travel)
        q.useMobileDataNow()
        assertEquals(listOf(travel.key), engine.started)
        // Allowed once: the same download is not stopped again for being on mobile data.
        q.onNetwork(false)
        assertTrue(engine.stopped.isEmpty())
        q.onEnded(travel, DownloadOutcome.Done)
        q.enqueue(vegan)
        assertTrue(q.state.value.waitingForWifi)
        q.setOnlyOnWifi(false)
        assertEquals(listOf(travel.key, vegan.key), engine.started)
        // Setup asks for a download explicitly; it goes ahead on mobile data.
        val setup = queue(FakeEngine(), wifi = false)
        setup.enqueue(travel, allowMobileData = true)
        assertFalse(setup.state.value.waitingForWifi)
    }

    @Test fun pausingAWaitingDownloadTakesItOutOfWaiting() {
        val engine = FakeEngine()
        val q = queue(engine, wifi = false)
        q.enqueue(travel); q.enqueue(vegan)
        q.pause()
        assertEquals(vegan, q.state.value.active)
        assertTrue(q.state.value.waitingForWifi)
        assertTrue(engine.stopped.isEmpty())
    }

    @Test fun timeLeftComesFromTheRecentSpeed() {
        val clock = longArrayOf(0)
        val q = queue(FakeEngine(), clock = clock)
        q.enqueue(travel)
        q.onProgress(travel, 0)
        assertNull(q.state.value.etaSeconds)
        clock[0] = 5_000; q.onProgress(travel, 50_000_000)  // 10 MB/s
        assertEquals(16L, q.state.value.etaSeconds)  // 165 MB left
        assertEquals(10_000_000L, q.lastSpeed)
        assertFalse(q.state.value.checking)
        q.onProgress(travel, travel.bytes)
        assertTrue(q.state.value.checking)
    }

    @Test fun aDownloadResumedOutsideTheQueueIsAdopted() {
        val q = queue(FakeEngine())
        q.adopt(travel, 120_000_000)
        assertEquals(travel, q.state.value.active)
        assertEquals(120_000_000L, q.state.value.bytes)
        q.adopt(vegan, 0)
        assertEquals(travel, q.state.value.active)
    }

    @Test fun theWordsAreTheSameEverywhere() {
        assertEquals("118 of 215 MB", progressBytes(118_400_000, 215_000_000))
        assertEquals("1.2 of 1.4 GB", progressBytes(1_234_000_000, 1_400_000_000))
        assertEquals("20 s", timeLeft(14)); assertEquals("1 min", timeLeft(75)); assertEquals("1 h 5 min", timeLeft(3_900))
        assertEquals("2nd in line", inLine(0)); assertEquals("3rd in line", inLine(1)); assertEquals("11th in line", inLine(9))
        val status = DownloadQueueState(active = vegan, bytes = 18_000_000, etaSeconds = 20, queued = listOf(travel))
        assertEquals("18 of 41 MB · 20 s left · then 1 more", downloadLine(status, vegan))
        assertEquals("Waiting for Wi-Fi · carries on by itself", downloadLine(status.copy(waitingForWifi = true), vegan))
        assertEquals("Checking the file…", downloadLine(status.copy(bytes = vegan.bytes), vegan))
    }
}
