package com.notra.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.notra.app.data.*
import com.notra.app.document.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class LibraryRepositoryTest {
    private lateinit var db: NotraDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var drafts: RecoveryJournal
    private lateinit var directory: File
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotraDatabase::class.java).build()
        directory = File(context.cacheDir, "library-test-${UUID.randomUUID()}")
        drafts = RecoveryJournal(directory); repo = LibraryRepository(db, drafts)
    }
    @After fun close() { db.close(); directory.deleteRecursively() }
    private suspend fun latest(n: NoteEntity) = requireNotNull(db.notes().get(n.id))
    private suspend fun action(n: NoteEntity, action: String, value: Boolean = true, folder: String? = null, tag: String? = null) { repo.mutate(mapOf(n.id to latest(n).revision), action, folder, tag, value) }
    private suspend fun fails(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; assertTrue("Must reject mutation", failed) }

    @Test fun folderHierarchyRenameMoveCyclesAndDuplicates() = runBlocking {
        val root = repo.createFolder(" Root ", null); val child = repo.createFolder("Child", root.id); val grand = repo.createFolder("Grand", child.id)
        fails { repo.changeFolder(root.id, parent = root.id, moving = true) }
        fails { repo.changeFolder(root.id, parent = grand.id, moving = true) }
        fails { repo.createFolder("root", null) }; fails { repo.createFolder(" ", null) }
        repo.changeFolder(child.id, "Renamed")
        repo.changeFolder(child.id, parent = null, moving = true)
        assertEquals("Renamed", db.library().folders().find { it.id == child.id }!!.name)
        assertNull(db.library().folders().find { it.id == child.id }!!.parentFolderId)
        assertEquals(child.id, db.library().folders().find { it.id == grand.id }!!.parentFolderId)
    }
    @Test fun dissolvePreservesEveryNoteStateAndChildrenAtBothLevels() = runBlocking {
        val parent = repo.createFolder("Parent", null); val folder = repo.createFolder("Target", parent.id); val child = repo.createFolder("Child", folder.id)
        val note = repo.createNote(folder.id); val tag = repo.createTag("Tag")
        action(note, "tag", tag = tag.id); action(note, "pin"); action(note, "favorite"); action(note, "archive"); action(note, "trash")
        val before = latest(note)
        repo.dissolveFolder(folder.id)
        val after = latest(note)
        assertEquals(before.copy(folderId = parent.id, revision = after.revision, updatedAt = after.updatedAt), after)
        assertEquals(before.revision + 1, after.revision)
        assertEquals(parent.id, db.library().folders().find { it.id == child.id }!!.parentFolderId)
        assertEquals(listOf(NoteTag(note.id, tag.id)), repo.links.first())
        repo.dissolveFolder(parent.id)
        assertNull(latest(note).folderId); assertNull(db.library().folders().find { it.id == child.id }!!.parentFolderId)
    }
    @Test fun dissolveNameCollisionRollsBackAllData() = runBlocking {
        val target = repo.createFolder("Target", null); repo.createFolder("Same", target.id); repo.createFolder("same", null)
        val note = repo.createNote(target.id)
        fails { repo.dissolveFolder(target.id) }
        assertEquals(target.id, latest(note).folderId); assertEquals(3, db.library().folders().size)
    }
    @Test fun createAndBulkMoveRemainIndependentFromContent() = runBlocking {
        val folder = repo.createFolder("Folder", null)
        val a = repo.createNote(folder.id); val b = NoteRepository(db.notes()).create()
        assertEquals(folder.id, a.folderId); assertNull(b.folderId)
        repo.mutate(mapOf(a.id to a.revision, b.id to b.revision), "move", folder = folder.id)
        assertEquals(folder.id, latest(b).folderId)
        repo.mutate(mapOf(a.id to latest(a).revision, b.id to latest(b).revision), "move")
        assertNull(latest(a).folderId); assertNull(latest(b).folderId)
        assertEquals(a.documentPayload, latest(a).documentPayload)
    }
    @Test fun tagsAreManyToManyAndDeletionNeverDeletesNotes() = runBlocking {
        val a = repo.createNote(null); val b = repo.createNote(null)
        val one = repo.createTag(" First  tag "); val two = repo.createTag("Second")
        fails { repo.createTag("first tag") }
        action(a, "tag", tag = one.id); action(a, "tag", tag = two.id); action(b, "tag", tag = one.id)
        assertEquals(3, repo.links.first().size)
        repo.renameTag(one.id, "Renamed"); assertEquals(3, repo.links.first().size)
        action(a, "tag", value = false, tag = two.id)
        repo.deleteTag(one.id); assertTrue(repo.links.first().isEmpty()); assertEquals(2, repo.notes.first().size)
    }
    @Test fun archiveTrashRestoreAndPermanentDeletePreserveContract() = runBlocking {
        val folder = repo.createFolder("Folder", null); val n = repo.createNote(folder.id); val tag = repo.createTag("Tag")
        action(n, "pin"); action(n, "favorite"); action(n, "tag", tag = tag.id)
        action(n, "archive"); val archived = latest(n)
        assertTrue(NoteRepository(db.notes()).observeActive().first().isEmpty())
        action(n, "trash"); assertEquals(archived.archivedAt, latest(n).archivedAt)
        action(n, "restore"); assertNull(latest(n).deletedAt); assertEquals(archived.archivedAt, latest(n).archivedAt)
        assertEquals(folder.id, latest(n).folderId); assertTrue(latest(n).pinned && latest(n).favorite)
        action(n, "archive", false); action(n, "trash"); action(n, "restore")
        assertNull(latest(n).archivedAt); assertNull(latest(n).deletedAt)
        fails { repo.permanentDelete(mapOf(n.id to latest(n).revision)) }
        action(n, "trash")
        drafts.write(RecoveryDraft(n.id, latest(n).revision, 1, "Draft", n.documentPayload))
        repo.permanentDelete(mapOf(n.id to latest(n).revision))
        assertNull(db.notes().get(n.id)); assertNull(drafts.read(n.id)); assertTrue(repo.links.first().isEmpty())
    }
    @Test fun staleMetadataAndEditorWritesFailWithoutErasingAnything() = runBlocking {
        val a = repo.createNote(null); val b = repo.createNote(null); val notes = NoteRepository(db.notes())
        action(b, "pin")
        fails { repo.mutate(mapOf(a.id to a.revision, b.id to b.revision), "favorite") }
        assertFalse(latest(a).favorite); assertEquals(a.revision, latest(a).revision)
        action(a, "favorite")
        fails { notes.save(a.id, a.revision, "stale", a.documentPayload) }
        assertTrue(latest(a).favorite); assertEquals("", latest(a).title)
        val saved = notes.save(a.id, latest(a).revision, "valid", a.documentPayload)
        assertTrue(saved.favorite); assertEquals("valid", saved.title)
    }
    @Test fun preferencesAndOrganizationSurviveReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = PreferencesRepository(context); val prior = prefs.libraryPreferences.first()
        try {
            prefs.setLibrary(LibraryPreferences(true, "TITLE_DESC", false))
            assertEquals(LibraryPreferences(true, "TITLE_DESC", false), PreferencesRepository(context).libraryPreferences.first())
        } finally { prefs.setLibrary(prior) }
        val name = "library-reopen-${UUID.randomUUID()}.db"
        val persistent = Room.databaseBuilder(context, NotraDatabase::class.java, name).build()
        val p = LibraryRepository(persistent, drafts); val note = p.createNote(null)
        p.mutate(mapOf(note.id to note.revision), "pin"); val expected = persistent.notes().get(note.id)
        persistent.close()
        val reopened = Room.databaseBuilder(context, NotraDatabase::class.java, name).build()
        try { assertEquals(expected, reopened.notes().get(note.id)); assertFalse(expected!!.favorite) } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
