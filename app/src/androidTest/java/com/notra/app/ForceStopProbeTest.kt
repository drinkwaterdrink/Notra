package com.notra.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notra.app.document.BlockType
import com.notra.app.document.DocumentBlock
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import com.notra.app.document.NoteDocumentV1
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.UUID

/** Run seed and verify in separate instrumentation commands with an ADB force-stop/relaunch between them. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForceStopProbeTest {
    private val notes get() = ApplicationProvider.getApplicationContext<NotraApplication>().container.notes
    private val marker = "I002 force stop probe"

    @Test fun aSeedDurableNote() = runBlocking {
        val note = notes.create()
        val document = NoteDocumentV1(blocks = listOf(DocumentBlock(UUID.randomUUID().toString(), BlockType.PARAGRAPH, marker)))
        val saved = notes.save(note.id, note.revision, marker, DocumentCodec.encode(document))
        assertEquals(1, saved.revision)
    }

    @Test fun zVerifyDurableNote() = runBlocking {
        val note = notes.observeActive().first().first { it.title == marker }
        val decoded = DocumentCodec.decode(note.documentSchemaVersion, note.documentPayload) as DocumentRead.Valid
        assertTrue(DocumentCodec.plainText(decoded.document).contains(marker))
    }
}
