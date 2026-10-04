package xyz.fieldatlas.attachments

import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentSessionTest {
    @Test fun imagePreviewAvailableDuringOcrAndRemovedWithAttachment() = runBlocking {
        val result = CompletableDeferred<Unit>()
        val owned = File("owned-image")
        val removed = mutableListOf<File>()
        val session = AttachmentSession(this, AttachmentReader { input ->
            result.await()
            throw AttachmentException("No readable text")
        }, { removed += it })
        session.add("photo.jpg") { AttachmentInput("photo", "photo.jpg", owned, AttachmentKind.IMAGE) }
        yield()
        assertEquals(owned, session.state.value.single().previewFile)
        assertEquals(AttachmentKind.IMAGE, session.state.value.single().kind)
        result.complete(Unit)
        yield()
        assertEquals(AttachmentPhase.Error, session.state.value.single().phase)
        assertEquals(owned, session.state.value.single().previewFile)
        session.remove(session.state.value.single().id)
        assertTrue(session.state.value.isEmpty())
        assertEquals(listOf(owned), removed)
    }
    @Test fun removalIgnoresLateResultsAndCleansOwnedInput() = runBlocking {
        val result = CompletableDeferred<Unit>()
        val removed = mutableListOf<File>()
        val session = AttachmentSession(this, AttachmentReader { input ->
            withContext(NonCancellable) { result.await() }
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "Berlin")))
        }, { removed += it })
        session.add("test.txt") { AttachmentInput("source", "test.txt", File("owned"), AttachmentKind.TEXT) }
        yield()
        val id = session.state.value.single().id
        assertFalse(session.ready)
        session.remove(id)
        result.complete(Unit)
        yield()
        assertTrue(session.state.value.isEmpty())
        assertEquals(listOf(File("owned")), removed)
    }
    @Test fun failedReadBlocksSubmitUntilRetrySucceeds() = runBlocking {
        var fail = true
        val session = AttachmentSession(this, AttachmentReader { input ->
            if (fail) throw AttachmentException("Unreadable")
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "notes")))
        }, {})
        session.add("notes.txt") { AttachmentInput("source", "notes.txt", File("owned"), AttachmentKind.TEXT) }
        yield()
        assertEquals(AttachmentPhase.Error, session.state.value.single().phase)
        assertFalse(session.ready)
        fail = false
        session.retry(session.state.value.single().id)
        yield()
        assertTrue(session.ready)
        assertEquals("notes", session.state.value.single().extracted!!.pages.single().text)
        session.clear()
        assertTrue(session.state.value.isEmpty())
    }
    @Test fun cannotQueueFourthAttachment() = runBlocking {
        val session = AttachmentSession(this, AttachmentReader { error("unused") }, {})
        repeat(3) { session.add("test.txt") { awaitCancellation() } }
        assertThrows(AttachmentException::class.java) { session.add("fourth") { awaitCancellation() } }
        session.clear()
    }

    @Test fun restoredFilesAreReadAgainAndReportedForSaving() = runBlocking {
        val reads = mutableListOf<String>()
        val session = AttachmentSession(this, AttachmentReader { input ->
            reads += input.id
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "Restored")))
        }, {})
        val staged = AttachmentInput("file-1", "notes.txt", File("file-1"), AttachmentKind.TEXT)
        session.restore(listOf(staged))
        yield()
        assertEquals(listOf("file-1"), reads)
        assertEquals(AttachmentPhase.Ready, session.state.value.single().phase)
        assertEquals(listOf(staged), session.staged())
        session.clear()
        assertTrue(session.staged().isEmpty())
    }
}
