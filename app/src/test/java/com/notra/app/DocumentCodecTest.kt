package com.notra.app

import com.notra.app.document.BlockType
import com.notra.app.document.DocumentBlock
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import com.notra.app.document.MarkType
import com.notra.app.document.NoteDocumentV1
import com.notra.app.document.TextMark
import com.notra.app.editor.RichTextMapper
import com.notra.app.editor.RichTextUndoController
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.runBlocking
import com.notra.app.data.RecoveryDraft
import com.notra.app.data.RecoveryJournal
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class DocumentCodecTest {
    private fun block(type: BlockType, text: String = "", marks: List<TextMark> = emptyList(), checked: Boolean = false) =
        DocumentBlock(UUID.randomUUID().toString(), type, text, checked, marks)

    @Test fun emptyRoundTripAndDeterminism() {
        val doc = NoteDocumentV1.empty()
        val encoded = DocumentCodec.encode(doc)
        val decoded = (DocumentCodec.decode(1, encoded) as DocumentRead.Valid).document
        assertEquals(doc, decoded)
        assertEquals(encoded, DocumentCodec.encode(decoded))
        assertEquals(BlockType.PARAGRAPH, decoded.blocks.single().type)
    }

    @Test fun allBlocksAndPlainText() {
        val doc = NoteDocumentV1(blocks = BlockType.entries.map { type ->
            block(type, if (type == BlockType.DIVIDER) "" else if (type == BlockType.CODE) "line 1\nline 2" else "Hi", checked = type == BlockType.CHECKLIST_ITEM)
        })
        assertEquals(doc, (DocumentCodec.decode(1, DocumentCodec.encode(doc)) as DocumentRead.Valid).document)
        val plain = DocumentCodec.plainText(doc)
        assertTrue(plain.contains("[x] Hi"))
        assertTrue(plain.contains("line 1\nline 2"))
        assertTrue(plain.contains("---"))
    }

    @Test fun marksCanonicalizeAndRespectUtf16Offsets() {
        val text = "A😀B\nC" // emoji occupies UTF-16 offsets 1..3
        val marks = listOf(TextMark(MarkType.BOLD, 0, 2), TextMark(MarkType.BOLD, 2, 3),
            TextMark(MarkType.ITALIC, 1, 3), TextMark(MarkType.UNDERLINE, 0, 1),
            TextMark(MarkType.STRIKETHROUGH, 3, 4), TextMark(MarkType.HIGHLIGHT, 4, 6))
        val doc = NoteDocumentV1(blocks = listOf(block(BlockType.PARAGRAPH, text, marks)))
        val decoded = (DocumentCodec.decode(1, DocumentCodec.encode(doc)) as DocumentRead.Valid).document
        assertEquals(5, decoded.blocks.single().marks.size)
        assertTrue(decoded.blocks.single().marks.contains(TextMark(MarkType.BOLD, 0, 3)))
        assertEquals(DocumentCodec.encode(decoded), DocumentCodec.encode((DocumentCodec.decode(1, DocumentCodec.encode(decoded)) as DocumentRead.Valid).document))
    }

    @Test fun invalidRangesAndDuplicateIdsRejected() {
        val a = block(BlockType.PARAGRAPH, "abc", listOf(TextMark(MarkType.BOLD, 0, 4)))
        assertThrows(IllegalArgumentException::class.java) { DocumentCodec.encode(NoteDocumentV1(blocks = listOf(a))) }
        val b = a.copy(marks = emptyList())
        assertThrows(IllegalArgumentException::class.java) { DocumentCodec.encode(NoteDocumentV1(blocks = listOf(b, b))) }
    }

    @Test fun legacyFutureAndCorruptPayloadSafe() {
        assertTrue(DocumentCodec.decode(0, " ") is DocumentRead.Valid)
        assertTrue(DocumentCodec.decode(2, "future") is DocumentRead.Unsupported)
        assertTrue(DocumentCodec.decode(1, "broken") is DocumentRead.Invalid)
        assertTrue(DocumentCodec.decode(1, "{\"schemaVersion\":2,\"blocks\":[]}") is DocumentRead.Unsupported)
        assertTrue(DocumentCodec.decode(0, "nonempty") is DocumentRead.Invalid)
    }

    @Test fun longDocumentAndSpecialCharactersRoundTrip() {
        val text = "quotes \" \\ tabs\t newlines\n" + "A".repeat(100_000) + "😀"
        val doc = NoteDocumentV1(blocks = listOf(block(BlockType.CODE, text,
            listOf(TextMark(MarkType.HIGHLIGHT, text.length - 2, text.length)))))
        assertEquals(doc, (DocumentCodec.decode(1, DocumentCodec.encode(doc)) as DocumentRead.Valid).document)
    }

    @Test fun runtimeTrackedStylesRoundTripAndToggle() {
        val initial = block(BlockType.PARAGRAPH, "A😀B", listOf(TextMark(MarkType.BOLD, 1, 3), TextMark(MarkType.ITALIC, 0, 4)))
        val state = RichTextMapper.state(initial)
        assertEquals(initial.marks.sortedBy { it.start }, RichTextMapper.marks(state).sortedBy { it.start })
        state.edit { selection = androidx.compose.ui.text.TextRange(1, 3) }
        assertTrue(RichTextMapper.selectionHas(state, MarkType.BOLD))
        RichTextMapper.toggle(state, MarkType.BOLD)
        assertFalse(RichTextMapper.marks(state).any { it.type == MarkType.BOLD })
        assertTrue(RichTextMapper.marks(state).any { it.type == MarkType.ITALIC })
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test fun textStateUndoRedoIncludesInlineStyleChange() {
        val state = RichTextMapper.state(block(BlockType.PARAGRAPH, "abcd"))
        val controller = RichTextUndoController(state)
        state.edit { selection = androidx.compose.ui.text.TextRange(0, 4) }
        assertTrue(controller.toggle(MarkType.BOLD))
        assertTrue(RichTextMapper.marks(state).any { it.type == MarkType.BOLD })
        assertTrue(controller.canUndo)
        controller.undo()
        assertFalse(RichTextMapper.marks(state).any { it.type == MarkType.BOLD })
        assertTrue(controller.canRedo)
        controller.redo()
        assertTrue(RichTextMapper.marks(state).any { it.type == MarkType.BOLD })
        controller.undo()
        state.edit { replace(0, 1, "z") }
        assertFalse(controller.canRedo)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test fun multipleStyleChangesUndoAndRedoInOrder() {
        val state = RichTextMapper.state(block(BlockType.PARAGRAPH, "abcd"))
        val controller = RichTextUndoController(state)
        state.edit { selection = androidx.compose.ui.text.TextRange(0, 4) }
        controller.toggle(MarkType.BOLD)
        controller.toggle(MarkType.ITALIC)
        controller.undo()
        assertEquals(listOf(MarkType.BOLD), RichTextMapper.marks(state).map { it.type })
        controller.undo()
        assertTrue(RichTextMapper.marks(state).isEmpty())
        controller.redo()
        controller.redo()
        assertEquals(setOf(MarkType.BOLD, MarkType.ITALIC), RichTextMapper.marks(state).map { it.type }.toSet())
    }

    @Test fun draftValidationCannotReplaceRoomPayload() = runBlocking {
        val dir = Files.createTempDirectory("notra-draft-test").toFile()
        try {
            val id = UUID.randomUUID().toString()
            val journal = RecoveryJournal(dir)
            val valid = RecoveryDraft(id, 2, 5, "New", DocumentCodec.encode(NoteDocumentV1.empty()))
            journal.write(valid)
            assertEquals(valid, journal.read(id))
            java.io.File(dir, "$id.json").writeText("corrupt")
            assertNull(journal.read(id))
            journal.write(valid.copy(documentPayload = "corrupt"))
            assertNull(journal.read(id))
            journal.clear(id)
            assertNull(journal.read(id))
        } finally { dir.deleteRecursively() }
    }
}
