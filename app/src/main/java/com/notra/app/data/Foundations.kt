package com.notra.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import com.notra.app.document.DocumentCodec
import com.notra.app.document.NoteDocumentV1
import java.util.UUID

private val Context.notraDataStore by preferencesDataStore(name = "notra_preferences")

interface NoteStore {
    fun observeActive(): Flow<List<NoteEntity>>
    suspend fun find(id: String): NoteEntity?
    suspend fun create(): NoteEntity
    suspend fun save(id: String, expectedRevision: Long, title: String, payload: String): NoteEntity
    suspend fun softDelete(id: String, expectedRevision: Long): Boolean
}

class NoteRepository(private val dao: NoteDao) : NoteStore {
    override fun observeActive(): Flow<List<NoteEntity>> = dao.observeActive()
    override suspend fun find(id: String): NoteEntity? = dao.get(id)
    suspend fun add(note: NoteEntity) = dao.insert(note)
    override suspend fun create(): NoteEntity {
        val now = System.currentTimeMillis()
        val note = NoteEntity(id = UUID.randomUUID().toString(), title = "", documentPayload = DocumentCodec.encode(NoteDocumentV1.empty()), documentSchemaVersion = 1, createdAt = now, updatedAt = now)
        dao.insert(note)
        return note
    }
    override suspend fun save(id: String, expectedRevision: Long, title: String, payload: String): NoteEntity {
        return dao.saveAndRead(id, expectedRevision, title, payload, System.currentTimeMillis())
    }
    override suspend fun softDelete(id: String, expectedRevision: Long): Boolean = dao.softDelete(id, expectedRevision, System.currentTimeMillis()) == 1
}

class PreferencesRepository(private val context: Context) {
    private val gridKey = booleanPreferencesKey("library_grid")
    private val sortKey = stringPreferencesKey("library_sort")
    private val pinsKey = booleanPreferencesKey("library_pinned_first")
    val libraryPreferences = context.notraDataStore.data.map { LibraryPreferences(it[gridKey] ?: false, it[sortKey] ?: "UPDATED_NEWEST", it[pinsKey] ?: true) }
    suspend fun setLibrary(value: LibraryPreferences) { context.notraDataStore.edit { it[gridKey] = value.grid; it[sortKey] = value.sort; it[pinsKey] = value.pinnedFirst } }
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    val reducedMotion: Flow<Boolean> = context.notraDataStore.data.map { it[reducedMotionKey] ?: false }
    suspend fun setReducedMotion(value: Boolean) {
        context.notraDataStore.edit { it[reducedMotionKey] = value }
    }
}

/** A path boundary only; imports and attachment metadata belong to I-007. */
class AttachmentDirectory(private val context: Context) {
    fun root(): File = File(context.filesDir, "attachments").apply { mkdirs() }
}

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, NotraDatabase::class.java, "notra.db").addMigrations(MIGRATION_1_2).build()
    val notes = NoteRepository(database.notes())
    val drafts = RecoveryJournal(File(context.filesDir, "drafts"))
    val library = LibraryRepository(database, drafts)
    val preferences = PreferencesRepository(context)
    val attachments = AttachmentDirectory(context)
}
