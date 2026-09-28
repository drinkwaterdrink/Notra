package com.notra.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The document payload is versioned when the editor arrives in I-002. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val documentPayload: String = "",
    val documentSchemaVersion: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val revision: Long = 0
)

@Dao
interface NoteDao {
    @Insert suspend fun insert(note: NoteEntity)
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: String): NoteEntity?
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC") fun observeActive(): Flow<List<NoteEntity>>
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = true)
abstract class NotraDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
}
