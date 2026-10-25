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

    @Test fun readyFilesTransferWithoutCleanupAndRestoreWithoutReadingAgain() = runBlocking {
        val reads = mutableListOf<String>()
        val removed = mutableListOf<File>()
        val session = AttachmentSession(this, AttachmentReader { input ->
            reads += input.id
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "Text from ${input.id}")))
        }, { removed += it })
        val image = AttachmentInput("photo", "photo.jpg", File("owned-photo"), AttachmentKind.IMAGE)
        val note = AttachmentInput("note", "note.txt", File("owned-note"), AttachmentKind.TEXT)
        session.add(image.displayName) { image }
        session.add(note.displayName) { note }
        yield()
        val original = session.state.value.toList()

        val transferred = session.take(setOf(original.first().id))
        assertEquals(listOf(OwnedAttachment(original.first(), image)), transferred)
        assertEquals(listOf(original.last()), session.state.value)
        assertEquals(listOf(note), session.staged())
        assertTrue(removed.isEmpty())

        // Clearing a foreground draft must not delete the photo held by a background answer.
        session.clear()
        assertEquals(listOf(note.localFile), removed)
        session.restoreOwned(transferred)
        assertEquals(listOf(original.first()), session.state.value)
        assertEquals(image.localFile, session.state.value.single().previewFile)
        assertEquals("Text from photo", session.state.value.single().extracted!!.pages.single().text)
        assertEquals(listOf(image), session.staged())
        assertEquals(listOf("photo", "note"), reads)

        session.clear()
        session.clear()
        assertEquals(listOf(note.localFile, image.localFile), removed)
        assertTrue(session.staged().isEmpty())
    }

    @Test fun transferRejectsAnIncompleteSelectionWithoutLosingAnyOwnership() = runBlocking {
        val wait = CompletableDeferred<Unit>()
        val removed = mutableListOf<File>()
        val session = AttachmentSession(this, AttachmentReader { input ->
            if (input.id == "reading") wait.await()
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "Text")))
        }, { removed += it })
        session.add("ready.txt") { AttachmentInput("ready", "ready.txt", File("ready"), AttachmentKind.TEXT) }
        session.add("reading.txt") { AttachmentInput("reading", "reading.txt", File("reading"), AttachmentKind.TEXT) }
        yield()
        val before = session.state.value.toList()
        assertEquals(listOf(AttachmentPhase.Ready, AttachmentPhase.Reading), before.map { it.phase })
        assertThrows(IllegalArgumentException::class.java) { session.take(before.map { it.id }.toSet()) }
        assertThrows(IllegalArgumentException::class.java) { session.take(setOf(before.first().id, "unknown")) }
        assertEquals(before, session.state.value)
        assertEquals(listOf("ready", "reading"), session.staged().map { it.id })
        assertTrue(removed.isEmpty())

        wait.complete(Unit)
        yield()
        val owned = session.take(session.state.value.map { it.id }.toSet())
        assertEquals(2, owned.size)
        assertTrue(session.state.value.isEmpty())
        assertTrue(session.staged().isEmpty())
        assertTrue(removed.isEmpty())
        session.restoreOwned(owned)
        session.clear()
        assertEquals(setOf(File("ready"), File("reading")), removed.toSet())
        assertEquals(2, removed.size)
    }

    @Test fun restoreOwnedChecksCapacityAndDuplicatesBeforeMutating() = runBlocking {
        val removed = mutableListOf<File>()
        val session = AttachmentSession(this, AttachmentReader { input ->
            ExtractedAttachment(input.id, input.displayName, listOf(AttachmentPage(1, "Text")))
        }, { removed += it })
        repeat(3) { index ->
            session.add("$index.txt") { AttachmentInput("$index", "$index.txt", File("$index"), AttachmentKind.TEXT) }
        }
        yield()
        val first = session.state.value.first()
        val transferred = session.take(setOf(first.id))
        val remaining = session.state.value.toList()
        assertThrows(IllegalArgumentException::class.java) { session.restoreOwned(transferred + transferred) }
        assertEquals(remaining, session.state.value)
        session.add("fourth.txt") { AttachmentInput("fourth", "fourth.txt", File("fourth"), AttachmentKind.TEXT) }
        yield()
        val full = session.state.value.toList()
        assertThrows(AttachmentException::class.java) { session.restoreOwned(transferred) }
        assertEquals(full, session.state.value)
        assertTrue(removed.isEmpty())
        session.clear()
        session.restoreOwned(transferred)
        assertThrows(IllegalArgumentException::class.java) { session.restoreOwned(transferred) }
        assertEquals(listOf(first), session.state.value)
        session.clear()
        assertEquals(4, removed.size)
        assertEquals(4, removed.distinct().size)
    }
}
