package com.notra.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The document payload is versioned when the editor arrives in I-002. */
@Entity(tableName = "notes", indices = [Index("folderId"), Index(value = ["deletedAt", "archivedAt"])])
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val documentPayload: String = "",
    val documentSchemaVersion: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val revision: Long = 0,
    val folderId: String? = null,
    val archivedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false
)

@Dao
interface NoteDao {
    @Insert suspend fun insert(note: NoteEntity)
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: String): NoteEntity?
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY updatedAt DESC") fun observeActive(): Flow<List<NoteEntity>>
    @Query("UPDATE notes SET title = :title, documentPayload = :payload, documentSchemaVersion = 1, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id AND revision = :expectedRevision AND deletedAt IS NULL")
    suspend fun save(id: String, expectedRevision: Long, title: String, payload: String, updatedAt: Long): Int
    @Query("UPDATE notes SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :id AND revision = :expectedRevision AND deletedAt IS NULL")
    suspend fun softDelete(id: String, expectedRevision: Long, deletedAt: Long): Int
    @Transaction
    suspend fun saveAndRead(id: String, expectedRevision: Long, title: String, payload: String, updatedAt: Long): NoteEntity {
        check(save(id, expectedRevision, title, payload, updatedAt) == 1) { "Note was changed or deleted" }
        return requireNotNull(get(id))
    }
}

@Database(entities = [NoteEntity::class, FolderEntity::class, TagEntity::class, NoteTag::class], version = 2, exportSchema = true)
abstract class NotraDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun library(): LibraryDao
}
