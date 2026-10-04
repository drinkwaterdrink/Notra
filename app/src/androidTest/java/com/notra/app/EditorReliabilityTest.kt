package com.notra.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notra.app.data.NoteEntity
import com.notra.app.data.NoteStore
import com.notra.app.data.RecoveryJournal
import com.notra.app.document.BlockType
import com.notra.app.document.DocumentBlock
import com.notra.app.document.DocumentCodec
import com.notra.app.document.NoteDocumentV1
import com.notra.app.editor.EditorLoad
import com.notra.app.editor.EditorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class EditorReliabilityTest {
    @get:Rule val compose = createComposeRule()
    private val owners = mutableListOf<ViewModelStore>()

    private class Store(@Volatile var row: NoteEntity) : NoteStore {
        var fail = false
        var gateFirstSave = false
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        var saves = 0
        override fun observeActive() = MutableStateFlow(listOf(row))
        override suspend fun find(id: String) = row
        override suspend fun create() = row
        override suspend fun softDelete(id: String, expectedRevision: Long) = false
        override suspend fun save(id: String, expectedRevision: Long, title: String, payload: String): NoteEntity {
            saves++
            if (gateFirstSave && saves == 1) {
                firstSaveStarted.complete(Unit)
                releaseFirstSave.await()
            } else if (fail) error("Injected failure")
            check(expectedRevision == row.revision)
            row = row.copy(title = title, documentPayload = payload, revision = row.revision + 1)
            return row
        }
    }

    private fun fixture(): Pair<Store, RecoveryJournal> {
        val id = UUID.randomUUID().toString()
        val doc = NoteDocumentV1(blocks = listOf(
            DocumentBlock(UUID.randomUUID().toString(), BlockType.PARAGRAPH, "Old"),
            DocumentBlock(UUID.randomUUID().toString(), BlockType.CHECKLIST_ITEM, "Task", checked = true)
        ))
        val row = NoteEntity(id = id, title = "Durable", documentPayload = DocumentCodec.encode(doc), documentSchemaVersion = 1, createdAt = 1, updatedAt = 1, revision = 2)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Store(row) to RecoveryJournal(File(context.cacheDir, "reliability-$id"))
    }

    private fun open(store: Store, journal: RecoveryJournal): EditorViewModel {
        lateinit var model: EditorViewModel
        compose.runOnIdle {
            model = EditorViewModel(store.row.id, store, journal)
            ViewModelStore().also { it.put(UUID.randomUUID().toString(), model); owners.add(it) }
        }
        compose.waitUntil(10_000) { model.load == EditorLoad.Ready }
        return model
    }

    @After fun closeSessions() { compose.runOnIdle { owners.forEach(ViewModelStore::clear) } }

    @Test fun staleQueuedDraftCannotReplaceNewRevisionRecovery() {
        val (store, journal) = fixture()
        store.gateFirstSave = true
        store.fail = true // A succeeds after its gate; B fails.
        val model = open(store, journal)
        compose.setContent { Text(model.title.text.toString()) }
        val processed = AtomicLong(0)
        compose.runOnIdle { model.onQueuedDraftProcessed = { processed.set(it.generation) } }
        compose.runOnIdle { model.title.edit { replace(0, length, "A") } }
        compose.waitUntil(10_000) { model.dirtyGeneration > 0 }
        compose.runOnIdle { model.viewModelScope.launch { model.flush() } }
        compose.waitUntil(10_000) { store.firstSaveStarted.isCompleted }
        compose.runOnIdle { model.title.edit { replace(0, length, "B") } }
        compose.waitUntil(10_000) { model.dirtyGeneration >= 2 }
        val bGeneration = model.dirtyGeneration
        compose.runOnIdle { store.releaseFirstSave.complete(Unit) }
        // This handshake proves the stale queued B worker ran after the failed B flush.
        compose.waitUntil(10_000) { processed.get() >= bGeneration }
        assertEquals("A", store.row.title)
        assertEquals(3L, store.row.revision)
        val draft = runBlocking { journal.read(store.row.id) }!!
        assertEquals(3L, draft.durableRevision)
        assertEquals("B", draft.title)
        compose.runOnIdle { owners.forEach(ViewModelStore::clear); owners.clear() }
        val reopened = open(store, journal)
        assertEquals("B", reopened.title.text.toString())
        assertTrue(reopened.recovered)
    }

    @Test fun discardSerializesWithSaveAndDeactivatesQueuedAndLifecycleWork() {
        val (store, journal) = fixture()
        store.gateFirstSave = true
        val model = open(store, journal)
        compose.setContent { Text(model.title.text.toString()) }
        val processed = AtomicLong(0)
        val left = AtomicBoolean(false)
        compose.runOnIdle { model.onQueuedDraftProcessed = { processed.set(it.generation) } }
        compose.runOnIdle { model.title.edit { replace(0, length, "A") } }
        compose.waitUntil(10_000) { model.dirtyGeneration > 0 }
        compose.runOnIdle { model.viewModelScope.launch { model.flush() } }
        compose.waitUntil(10_000) { store.firstSaveStarted.isCompleted }
        compose.runOnIdle { model.title.edit { replace(0, length, "B") } }
        compose.waitUntil(10_000) { model.dirtyGeneration >= 2 }
        val queuedGeneration = model.dirtyGeneration
        compose.runOnIdle {
            model.leaveWithoutLatest { left.set(true) }
            assertFalse(left.get()) // Discard must wait for the save holding the mutex.
            store.releaseFirstSave.complete(Unit)
        }
        compose.waitUntil(10_000) { left.get() && processed.get() >= queuedGeneration }
        runBlocking { assertNull(journal.read(store.row.id)) }
        assertEquals("A", store.row.title)
        val durable = store.row
        compose.runOnIdle {
            model.title.edit { replace(0, length, "Late abandoned edit") }
            model.flushOnStop()
        }
        compose.waitForIdle()
        runBlocking { assertFalse(model.flush()); assertNull(journal.read(store.row.id)) }
        assertEquals(durable, store.row)
        val reopened = open(store, journal)
        assertEquals("A", reopened.title.text.toString())
        assertFalse(reopened.recovered)
    }

    @Test fun removedAndRestoredBlockHasOneObserverAndNoOrphan() {
        val (store, journal) = fixture()
        store.fail = true
        val model = open(store, journal)
        val block = model.blocks[1]
        compose.setContent { Text(model.title.text.toString() + model.blocks.joinToString { it.text.text.toString() }) }
        repeat(5) {
            compose.runOnIdle { model.removeBlock(block.id) }
            compose.waitForIdle()
            compose.runOnIdle { model.undo() }
            compose.waitForIdle()
        }
        compose.runOnIdle { model.removeBlock(block.id) }
        compose.waitForIdle()
        val removedGeneration = model.dirtyGeneration
        compose.runOnIdle { block.text.edit { append(" while removed") } }
        compose.waitForIdle()
        assertEquals(removedGeneration, model.dirtyGeneration)
        compose.runOnIdle { model.undo() }
        compose.waitForIdle()
        val before = model.dirtyGeneration
        compose.runOnIdle { block.text.edit { append(" one edit") } }
        compose.waitUntil(10_000) { model.dirtyGeneration > before }
        compose.waitForIdle()
        assertEquals(before + 1, model.dirtyGeneration)
    }

    @Test fun checklistTypeUndoAndRedoRestoreCheckedState() {
        val (store, journal) = fixture()
        val model = open(store, journal)
        val block = model.blocks[1]
        compose.runOnIdle {
            model.setBlockType(block.id, BlockType.PARAGRAPH)
            assertFalse(block.checked)
            model.undo()
            assertEquals(BlockType.CHECKLIST_ITEM, block.type)
            assertTrue(block.checked)
            model.redo()
            assertEquals(BlockType.PARAGRAPH, block.type)
            assertFalse(block.checked)
            model.undo()
            assertTrue(block.checked)
        }
    }
}
