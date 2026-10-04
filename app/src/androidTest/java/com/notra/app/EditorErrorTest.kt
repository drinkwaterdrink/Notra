package com.notra.app

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notra.app.data.NoteEntity
import com.notra.app.data.NoteStore
import com.notra.app.data.RecoveryDraft
import com.notra.app.data.RecoveryJournal
import com.notra.app.document.BlockType
import com.notra.app.document.DocumentBlock
import com.notra.app.document.DocumentCodec
import com.notra.app.document.NoteDocumentV1
import com.notra.app.editor.EditorScreen
import com.notra.app.ui.NotraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@RunWith(AndroidJUnit4::class)
class EditorErrorTest {
    @get:Rule val compose = createComposeRule()

    private class FakeStore(var row: NoteEntity, var fail: Boolean = false, var delayMs: Long = 0) : NoteStore {
        val active = MutableStateFlow(listOf(row))
        override fun observeActive(): Flow<List<NoteEntity>> = active
        override suspend fun find(id: String): NoteEntity? = row.takeIf { it.id == id }
        override suspend fun create(): NoteEntity = row
        override suspend fun save(id: String, expectedRevision: Long, title: String, payload: String): NoteEntity {
            if (delayMs > 0) delay(delayMs)
            if (fail) error("Injected save failure")
            check(row.revision == expectedRevision)
            row = row.copy(title = title, documentPayload = payload, documentSchemaVersion = 1, revision = row.revision + 1)
            active.value = listOf(row)
            return row
        }
        override suspend fun softDelete(id: String, expectedRevision: Long): Boolean {
            row = row.copy(deletedAt = 1)
            active.value = emptyList()
            return true
        }
    }

    private fun fixture(): Pair<FakeStore, RecoveryJournal> {
        val id = UUID.randomUUID().toString()
        val doc = NoteDocumentV1(blocks = listOf(DocumentBlock(UUID.randomUUID().toString(), BlockType.PARAGRAPH, "Old")))
        val row = NoteEntity(id = id, title = "Durable", documentPayload = DocumentCodec.encode(doc), documentSchemaVersion = 1, createdAt = 1, updatedAt = 1, revision = 2)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return FakeStore(row) to RecoveryJournal(File(context.cacheDir, "draft-test-$id"))
    }

    @Test fun saveFailureShowsChoicesAndKeepsDraft() {
        val (store, journal) = fixture()
        store.fail = true
        compose.setContent { NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").performTextInput(" latest")
        compose.onNodeWithTag("editor_back").performClick()
        compose.onNodeWithText("Note not saved").assertExists()
        compose.onNodeWithText("Retry").assertExists()
        compose.onNodeWithText("Keep editing").assertExists()
        compose.onNodeWithText("Leave without latest changes").assertExists()
        runBlocking { assertNotNull(journal.read(store.row.id)) }
        assertEquals("Durable", store.row.title)
    }

    @Test fun newerDraftRestoresAndCorruptDocumentIsProtected() {
        val (store, journal) = fixture()
        store.fail = true
        val recoveredDoc = NoteDocumentV1(blocks = listOf(DocumentBlock(UUID.randomUUID().toString(), BlockType.PARAGRAPH, "Recovered body")))
        runBlocking { journal.write(RecoveryDraft(store.row.id, store.row.revision, 3, "Recovered title", DocumentCodec.encode(recoveredDoc))) }
        compose.setContent { NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").assertTextContains("Recovered title")
        compose.onNodeWithText("Recovered unsaved changes").assertExists()
        assertEquals("Durable", store.row.title)
    }

    @Test fun leaveWithoutLatestDiscardsDraftBeforeReopen() {
        val (store, journal) = fixture()
        store.fail = true
        var visible by mutableStateOf(true)
        var session by mutableStateOf(0)
        compose.setContent {
            if (visible) key(session) {
                val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
                DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = { visible = false }) }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").performTextInput(" abandoned")
        compose.onNodeWithTag("editor_back").performClick()
        compose.onNodeWithText("Note not saved").assertExists()
        compose.onNodeWithText("Leave without latest changes").performClick()
        compose.waitUntil(10_000) { !visible }
        runBlocking { assertNull(journal.read(store.row.id)) }
        assertEquals("Durable", store.row.title)
        // A fresh owner is required: a key alone does not replace an Activity's ViewModel.
        compose.runOnIdle { session++; visible = true }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").assertTextEquals("Durable")
        runBlocking { assertNull(journal.read(store.row.id)) }
    }

    @Test fun futurePayloadCannotBeOverwritten() {
        val (store, journal) = fixture()
        store.row = store.row.copy(documentSchemaVersion = 2, documentPayload = "future raw payload")
        compose.setContent { NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = {}) } }
        compose.onNodeWithText("This document uses unsupported version 2. Its original data is preserved.").assertExists()
        assertEquals("future raw payload", store.row.documentPayload)
    }

    @Test fun obsoleteDraftDoesNotReplaceDurableNote() {
        val (store, journal) = fixture()
        val other = NoteDocumentV1(blocks = listOf(DocumentBlock(UUID.randomUUID().toString(), BlockType.PARAGRAPH, "Stale")))
        runBlocking { journal.write(RecoveryDraft(store.row.id, store.row.revision - 1, 9, "Stale title", DocumentCodec.encode(other))) }
        compose.setContent { NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").assertTextContains("Durable")
        assertEquals("Durable", store.row.title)
    }

    @Test fun corruptDraftDoesNotReplaceDurableNote() {
        val (store, journal) = fixture()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "draft-test-${store.row.id}").apply { mkdirs() }
        File(directory, "${store.row.id}.json").writeText("broken")
        compose.setContent { NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").assertTextContains("Durable")
        assertEquals("Durable", store.row.title)
    }

    @Test fun newerEditDuringSaveWins() {
        val (store, journal) = fixture()
        store.delayMs = 450
        compose.setContent { NotraTheme { EditorScreen(store.row.id, store, journal, onLeave = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").performTextInput(" first")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Saving").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").performTextInput(" second")
        compose.onNodeWithTag("editor_back").performClick()
        compose.waitUntil(10_000) { store.row.title.contains("second") }
        assertEquals("Durable first second", store.row.title)
        assertTrue(store.row.revision >= 3)
    }
}
