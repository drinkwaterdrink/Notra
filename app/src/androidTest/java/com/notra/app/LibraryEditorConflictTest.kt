package com.notra.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.notra.app.data.*
import com.notra.app.editor.EditorScreen
import com.notra.app.ui.NotraTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class LibraryEditorConflictTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realMetadataConflictRetainsDraftAndNeverPretendsToSave() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NotraDatabase::class.java).build()
        val directory = File(context.cacheDir, "conflict-${UUID.randomUUID()}")
        val drafts = RecoveryJournal(directory); val repo = LibraryRepository(db, drafts); val notes = NoteRepository(db.notes())
        val note = runBlocking { repo.createNote(null) }
        var left = false
        val shown = mutableStateOf(true)
        try {
            compose.setContent { if (shown.value) NotraTheme { EditorScreen(note.id, notes, drafts, onLeave = { left = true }) } }
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
            runBlocking { repo.mutate(mapOf(note.id to note.revision), "favorite") }
            compose.onNodeWithTag("title_field").performTextInput("Unsaved editor content")
            compose.onNodeWithTag("editor_back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Note not saved")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Note not saved").assertExists()
            assertFalse(left)
            runBlocking {
                val durable = notes.find(note.id)!!
                assertTrue(durable.favorite); assertEquals("", durable.title)
                assertEquals("Unsaved editor content", drafts.read(note.id)!!.title)
            }
            compose.onNodeWithText("Leave without latest changes").performClick()
            compose.waitUntil(10_000) { left }
            runBlocking { assertNull(drafts.read(note.id)); assertTrue(notes.find(note.id)!!.favorite) }
        } finally { compose.runOnIdle { shown.value = false }; compose.waitForIdle(); db.close(); directory.deleteRecursively() }
    }
}
