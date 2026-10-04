package com.notra.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.notra.app.data.*
import com.notra.app.document.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NotraDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())
    @Test fun realV1NotesMigrateAndReopenWithoutLoss() = runBlocking {
        val name = "library-migration.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        val rich = NoteDocumentV1(blocks = listOf(DocumentBlock(id = java.util.UUID.randomUUID().toString(), type = BlockType.CHECKLIST_ITEM, text = "Rich 😀", checked = true, marks = listOf(TextMark(MarkType.BOLD, 0, 4)))))
        val active = NoteEntity(title = "Original", documentPayload = DocumentCodec.encode(rich), documentSchemaVersion = 1, createdAt = 11, updatedAt = 22, revision = 7)
        val deleted = NoteEntity(title = "Trashed", documentPayload = "legacy raw", documentSchemaVersion = 0, createdAt = 33, updatedAt = 44, deletedAt = 45, revision = 9)
        helper.createDatabase(name, 1).also { db ->
            listOf(active, deleted).forEach { n -> db.execSQL("INSERT INTO notes(id,title,documentPayload,documentSchemaVersion,createdAt,updatedAt,deletedAt,revision) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(n.id, n.title, n.documentPayload, n.documentSchemaVersion, n.createdAt, n.updatedAt, n.deletedAt, n.revision)) }
            db.close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, NotraDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals(active, db.notes().get(active.id))
            assertEquals(deleted, db.notes().get(deleted.id))
            assertTrue(db.library().folders().isEmpty()); assertTrue(db.library().tags().isEmpty())
            db.library().insertFolder(FolderEntity(name = "After migration", parentFolderId = null, createdAt = 1, updatedAt = 1))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
