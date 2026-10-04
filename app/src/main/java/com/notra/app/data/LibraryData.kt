package com.notra.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.notra.app.document.DocumentCodec
import com.notra.app.document.NoteDocumentV1
import java.util.Locale
import java.util.UUID

@Entity(tableName = "folders", indices = [Index("parentFolderId")])
data class FolderEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val parentFolderId: String?, val sortOrder: Long = 0, val createdAt: Long, val updatedAt: Long)
@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class TagEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val normalizedName: String, val createdAt: Long, val updatedAt: Long)
@Entity(tableName = "note_tags", primaryKeys = ["noteId", "tagId"], indices = [Index("tagId")], foreignKeys = [
    ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)])
data class NoteTag(val noteId: String, val tagId: String)
data class LibraryPreferences(val grid: Boolean = false, val sort: String = "UPDATED_NEWEST", val pinnedFirst: Boolean = true)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN folderId TEXT")
        db.execSQL("ALTER TABLE notes ADD COLUMN archivedAt INTEGER")
        db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX index_notes_folderId ON notes(folderId)")
        db.execSQL("CREATE INDEX index_notes_deletedAt_archivedAt ON notes(deletedAt, archivedAt)")
        db.execSQL("CREATE TABLE folders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, parentFolderId TEXT, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX index_folders_parentFolderId ON folders(parentFolderId)")
        db.execSQL("CREATE TABLE tags (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, normalizedName TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_tags_normalizedName ON tags(normalizedName)")
        db.execSQL("CREATE TABLE note_tags (noteId TEXT NOT NULL, tagId TEXT NOT NULL, PRIMARY KEY(noteId,tagId), FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_note_tags_tagId ON note_tags(tagId)")
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM notes") fun observeNotes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM folders") fun observeFolders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM tags ORDER BY normalizedName") fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM note_tags") fun observeLinks(): Flow<List<NoteTag>>
    @Query("SELECT * FROM folders") suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM notes WHERE folderId = :folder") suspend fun folderNotes(folder: String): List<NoteEntity>
    @Query("SELECT * FROM notes WHERE id IN (SELECT noteId FROM note_tags WHERE tagId = :tag)") suspend fun taggedNotes(tag: String): List<NoteEntity>
    @Insert suspend fun insertFolder(folder: FolderEntity)
    @Update suspend fun updateFolder(folder: FolderEntity)
    @Query("DELETE FROM folders WHERE id = :id") suspend fun deleteFolder(id: String)
    @Insert suspend fun insertTag(tag: TagEntity)
    @Update suspend fun updateTag(tag: TagEntity)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addLink(link: NoteTag)
    @Query("DELETE FROM note_tags WHERE noteId = :note AND tagId = :tag") suspend fun removeLink(note: String, tag: String)
    @Query("UPDATE notes SET folderId = :folder, archivedAt = :archive, deletedAt = :trash, pinned = :pin, favorite = :favorite, updatedAt = :now, revision = revision + 1 WHERE id = :id AND revision = :revision")
    suspend fun metadata(id: String, revision: Long, folder: String?, archive: Long?, trash: Long?, pin: Boolean, favorite: Boolean, now: Long): Int
    @Query("DELETE FROM notes WHERE id = :id AND revision = :revision AND deletedAt IS NOT NULL") suspend fun permanentDelete(id: String, revision: Long): Int
}

fun normalizedName(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

/** Folder integrity is enforced in one database transaction at this boundary.
 * Nullable folder references stay additive in v2; no notes-table rebuild is required.
 */
class LibraryRepository(private val db: NotraDatabase, private val drafts: RecoveryJournal) {
    private val dao = db.library()
    val notes = dao.observeNotes()
    val folders = dao.observeFolders()
    val tags = dao.observeTags()
    val links = dao.observeLinks()
    private fun validateFolder(all: List<FolderEntity>, id: String?, name: String, parent: String?) {
        require(name.trim().isNotEmpty()) { "Enter a folder name" }
        require(parent == null || all.any { it.id == parent }) { "Parent folder no longer exists" }
        var cursor = parent
        val seen = mutableSetOf<String>()
        while (cursor != null) {
            require(cursor != id && seen.add(cursor)) { "A folder cannot be moved into itself or a descendant" }
            cursor = all.firstOrNull { it.id == cursor }?.parentFolderId
        }
        require(all.none { it.id != id && it.parentFolderId == parent && normalizedName(it.name) == normalizedName(name) }) { "A folder with this name already exists here. Rename it first." }
    }
    suspend fun createFolder(name: String, parent: String?): FolderEntity = db.withTransaction {
        validateFolder(dao.folders(), null, name, parent)
        val now = System.currentTimeMillis()
        FolderEntity(name = name.trim(), parentFolderId = parent, createdAt = now, updatedAt = now).also { dao.insertFolder(it) }
    }
    suspend fun changeFolder(id: String, name: String? = null, parent: String? = null, moving: Boolean = false) = db.withTransaction {
        val all = dao.folders(); val row = requireNotNull(all.find { it.id == id }) { "Folder no longer exists" }
        val next = row.copy(name = name?.trim() ?: row.name, parentFolderId = if (moving) parent else row.parentFolderId, updatedAt = System.currentTimeMillis())
        validateFolder(all, id, next.name, next.parentFolderId); dao.updateFolder(next)
    }
    suspend fun dissolveFolder(id: String) = db.withTransaction {
        val all = dao.folders(); val row = requireNotNull(all.find { it.id == id })
        val children = all.filter { it.parentFolderId == id }
        val remaining = all.filter { it.id != id && it.parentFolderId != id }
        children.forEach { validateFolder(remaining, it.id, it.name, row.parentFolderId) }
        children.forEach { dao.updateFolder(it.copy(parentFolderId = row.parentFolderId, updatedAt = System.currentTimeMillis())) }
        dao.folderNotes(id).forEach { update(it, it.copy(folderId = row.parentFolderId)) }
        dao.deleteFolder(id)
    }
    suspend fun createNote(folder: String?): NoteEntity = db.withTransaction {
        require(folder == null || dao.folders().any { it.id == folder }) { "Folder no longer exists" }
        val now = System.currentTimeMillis()
        NoteEntity(title = "", documentPayload = DocumentCodec.encode(NoteDocumentV1.empty()), documentSchemaVersion = 1,
            createdAt = now, updatedAt = now, folderId = folder).also { db.notes().insert(it) }
    }
    suspend fun createTag(name: String): TagEntity = db.withTransaction {
        val normalized = normalizedName(name)
        require(normalized.isNotBlank()) { "Enter a tag name" }
        require(dao.tags().none { it.normalizedName == normalized }) { "This tag already exists" }
        val now = System.currentTimeMillis()
        TagEntity(name = name.trim(), normalizedName = normalized, createdAt = now, updatedAt = now).also { dao.insertTag(it) }
    }
    suspend fun renameTag(id: String, name: String) = db.withTransaction {
        val all = dao.tags(); val row = requireNotNull(all.find { it.id == id })
        val normalized = normalizedName(name)
        require(normalized.isNotBlank() && all.none { it.id != id && it.normalizedName == normalized }) { "Enter a unique tag name" }
        dao.updateTag(row.copy(name = name.trim(), normalizedName = normalized, updatedAt = System.currentTimeMillis()))
        dao.taggedNotes(id).forEach { update(it, it) }
    }
    suspend fun deleteTag(id: String) = db.withTransaction {
        dao.taggedNotes(id).forEach { update(it, it) }; dao.deleteTag(id)
    }
    private suspend fun update(old: NoteEntity, next: NoteEntity) {
        check(dao.metadata(old.id, old.revision, next.folderId, next.archivedAt, next.deletedAt, next.pinned, next.favorite, System.currentTimeMillis()) == 1) { "Note changed. Reload and retry." }
    }
    /** All selected revisions are checked before any mutation; conflicts roll back the batch. */
    suspend fun mutate(expected: Map<String, Long>, action: String, folder: String? = null, tag: String? = null, value: Boolean = true) = db.withTransaction {
        require(expected.isNotEmpty()) { "Select a note" }
        if (action == "move") require(folder == null || dao.folders().any { it.id == folder }) { "Folder no longer exists" }
        if (action == "tag") require(dao.tags().any { it.id == tag }) { "Tag no longer exists" }
        val rows = expected.map { (id, rev) -> requireNotNull(db.notes().get(id)).also { check(it.revision == rev) { "Note changed. Reload and retry." } } }
        rows.forEach { old ->
            val now = System.currentTimeMillis()
            val next = when (action) {
                "move" -> old.copy(folderId = folder)
                "pin" -> old.copy(pinned = value)
                "favorite" -> old.copy(favorite = value)
                "archive" -> { require(old.deletedAt == null); old.copy(archivedAt = if (value) now else null) }
                "trash" -> { require(old.deletedAt == null); old.copy(deletedAt = now) }
                "restore" -> { require(old.deletedAt != null); old.copy(deletedAt = null) }
                "tag" -> { if (value) dao.addLink(NoteTag(old.id, requireNotNull(tag))) else dao.removeLink(old.id, requireNotNull(tag)); old }
                else -> error("Unknown Library action")
            }
            update(old, next)
        }
    }
    suspend fun permanentDelete(expected: Map<String, Long>) = db.withTransaction {
        require(expected.isNotEmpty())
        expected.forEach { (id, rev) -> val row = requireNotNull(db.notes().get(id)); check(row.revision == rev && row.deletedAt != null) { "Only unchanged Trash notes can be permanently deleted" } }
        // Clear first: failure prevents database deletion and is surfaced for retry.
        expected.forEach { (id, _) -> drafts.clear(id) }
        expected.forEach { (id, rev) -> check(dao.permanentDelete(id, rev) == 1) }
    }
}
