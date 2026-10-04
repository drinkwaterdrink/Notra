package com.notra.app

import com.notra.app.data.*
import com.notra.app.library.*
import org.junit.Assert.*
import org.junit.Test

class LibraryQueryTest {
    private val a = NoteEntity(id = "a", title = "Alpha", createdAt = 3, updatedAt = 1, pinned = true)
    private val b = NoteEntity(id = "b", title = "Beta", createdAt = 1, updatedAt = 3, favorite = true)
    private val c = NoteEntity(id = "c", title = "Charlie", createdAt = 2, updatedAt = 2)
    @Test fun allSortsAreDeterministicAndPinsRemainIndependent() {
        val expected = listOf(listOf("b","c","a"), listOf("a","c","b"), listOf("a","c","b"), listOf("b","c","a"), listOf("a","b","c"), listOf("c","b","a"))
        LibrarySort.entries.forEachIndexed { index, sort ->
            val data = LibraryData(notes = listOf(c,b,a))
            assertEquals(expected[index], libraryNotes(data, LibraryLocation(), LibraryFilter(), sort, false).map { it.id })
            assertEquals("a", libraryNotes(data, LibraryLocation(), LibraryFilter(), sort, true).first().id)
        }
        val tie = a.copy(id = "z")
        assertEquals(listOf("a","z"), libraryNotes(LibraryData(notes = listOf(tie,a)), LibraryLocation(), LibraryFilter(), LibrarySort.UPDATED_NEWEST, false).map { it.id })
        assertFalse(a.favorite); assertFalse(b.pinned)
    }
    @Test fun lifecycleLocationsAndFiltersNeverLeakTrashOrArchive() {
        val archived = c.copy(id = "archive", archivedAt = 10, favorite = true)
        val trashed = c.copy(id = "trash", archivedAt = 10, deletedAt = 20, favorite = true)
        val folder = c.copy(id = "folder", folderId = "f")
        val data = LibraryData(notes = listOf(a,b,c,archived,trashed,folder), links = listOf(NoteTag(b.id,"tag")))
        fun query(mode: LibraryMode, filter: LibraryFilter = LibraryFilter()) = libraryNotes(data, LibraryLocation(mode, "f"), filter, LibrarySort.TITLE_ASC, false).map { it.id }.toSet()
        assertEquals(setOf("a","b","c"),query(LibraryMode.ROOT))
        assertEquals(setOf("folder"),query(LibraryMode.FOLDER))
        assertEquals(setOf("b"),query(LibraryMode.FAVORITES))
        assertEquals(setOf("archive"),query(LibraryMode.ARCHIVE))
        assertEquals(setOf("trash"),query(LibraryMode.TRASH))
        assertEquals(setOf("b"),query(LibraryMode.ROOT,LibraryFilter(tag = "tag")))
        assertEquals(setOf("a"),query(LibraryMode.UNFILED,LibraryFilter(pinned = true)))
        assertEquals(setOf("b"),query(LibraryMode.ROOT,LibraryFilter(favorite = true)))
    }
}
