package com.notra.app.data

import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
data class RecoveryDraft(
    val noteId: String,
    val durableRevision: Long,
    val generation: Long,
    val title: String,
    val documentPayload: String
)

/** Private, atomic latest-snapshot journal. Invalid drafts are ignored, never promoted to Room. */
class RecoveryJournal(private val directory: File) {
    private val json = Json { encodeDefaults = true }
    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(directory, "$id.json")
    }

    suspend fun read(id: String): RecoveryDraft? = withContext(Dispatchers.IO) {
        val raw = runCatching { file(id).takeIf(File::exists)?.readText() }.getOrNull() ?: return@withContext null
        val draft = runCatching { json.decodeFromString<RecoveryDraft>(raw) }.getOrNull() ?: return@withContext null
        if (draft.noteId != id || draft.generation <= 0 || draft.durableRevision < 0 || DocumentCodec.decode(1, draft.documentPayload) !is DocumentRead.Valid) null else draft
    }

    suspend fun write(draft: RecoveryDraft) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = file(draft.noteId)
        val temp = File(directory, "${draft.noteId}.${UUID.randomUUID()}.tmp")
        try {
            temp.writeText(json.encodeToString(draft))
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    suspend fun clear(id: String) = withContext(Dispatchers.IO) {
        val target = file(id)
        check(!target.exists() || target.delete()) { "Recovery draft could not be cleared" }
    }
}
