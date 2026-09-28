package com.notra.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.notraDataStore by preferencesDataStore(name = "notra_preferences")

class NoteRepository(private val dao: NoteDao) {
    fun observeActive(): Flow<List<NoteEntity>> = dao.observeActive()
    suspend fun find(id: String): NoteEntity? = dao.get(id)
    suspend fun add(note: NoteEntity) = dao.insert(note)
}

class PreferencesRepository(private val context: Context) {
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
    private val database = Room.databaseBuilder(context, NotraDatabase::class.java, "notra.db").build()
    val notes = NoteRepository(database.notes())
    val preferences = PreferencesRepository(context)
    val attachments = AttachmentDirectory(context)
}
