package com.notra.app.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notra.app.data.NoteStore
import com.notra.app.data.RecoveryDraft
import com.notra.app.data.RecoveryJournal
import com.notra.app.document.BlockType
import com.notra.app.document.DocumentBlock
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import com.notra.app.document.MarkType
import com.notra.app.document.NoteDocumentV1
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private const val AUTOSAVE_IDLE_MS = 650L

enum class SaveStatus { SAVED, PENDING, SAVING, ERROR }
sealed interface EditorLoad {
    data object Loading : EditorLoad
    data object Ready : EditorLoad
    data object Missing : EditorLoad
    data object Deleted : EditorLoad
    data class Unsupported(val version: Int) : EditorLoad
    data class Invalid(val reason: String) : EditorLoad
}

class EditorBlock(block: DocumentBlock) {
    val id = block.id
    var type by mutableStateOf(block.type)
    var checked by mutableStateOf(block.checked)
    val text = RichTextMapper.state(block)
    val undoController = RichTextUndoController(text)
    fun snapshot(): DocumentBlock = DocumentBlock(id, type, if (type == BlockType.DIVIDER) "" else text.text.toString(), checked, if (type == BlockType.DIVIDER) emptyList() else RichTextMapper.marks(text))
}

private data class StructuralAction(val undo: () -> Unit, val redo: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
class EditorViewModel(
    val noteId: String,
    private val notes: NoteStore,
    private val journal: RecoveryJournal
) : ViewModel() {
    val title = TextFieldState()
    val blocks = mutableStateListOf<EditorBlock>()
    var load by mutableStateOf<EditorLoad>(EditorLoad.Loading); private set
    var saveStatus by mutableStateOf(SaveStatus.SAVED); private set
    var saveError by mutableStateOf<String?>(null); private set
    var recovered by mutableStateOf(false); private set
    var focusedBlockId by mutableStateOf<String?>(null)
    var exitFailure by mutableStateOf(false); private set
    var deleting by mutableStateOf(false); private set

    private var revision = 0L
    private var generation = 0L
    private var durableTitle = ""
    private var durablePayload = ""
    @Volatile private var durableGeneration = 0L
    private val writeMutex = Mutex()
    private val journalQueue = Channel<RecoveryDraft>(Channel.CONFLATED)
    private var autosaveJob: Job? = null
    private val undo = ArrayDeque<StructuralAction>()
    private val redo = ArrayDeque<StructuralAction>()

    init {
        viewModelScope.launch {
            for (draft in journalQueue) {
                try {
                    writeMutex.withLock {
                        if (draft.generation > durableGeneration) journal.write(draft)
                    }
                } catch (_: Exception) {
                    saveStatus = SaveStatus.ERROR
                    saveError = "Recovery draft could not be written"
                }
            }
        }
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val row = try { notes.find(noteId) } catch (_: Exception) { load = EditorLoad.Invalid("Could not open note"); return }
        if (row == null) { load = EditorLoad.Missing; return }
        if (row.deletedAt != null) { load = EditorLoad.Deleted; return }
        val decoded = DocumentCodec.decode(row.documentSchemaVersion, row.documentPayload)
        val document = when (decoded) {
            is DocumentRead.Valid -> decoded.document
            is DocumentRead.Unsupported -> { load = EditorLoad.Unsupported(decoded.version); return }
            is DocumentRead.Invalid -> { load = EditorLoad.Invalid(decoded.reason); return }
        }
        revision = row.revision
        durableTitle = row.title
        durablePayload = DocumentCodec.encode(document)
        val draft = journal.read(noteId)?.takeIf { it.durableRevision == revision }
        val restored = draft?.let { DocumentCodec.decode(1, it.documentPayload) as? DocumentRead.Valid }
        val startingDocument = restored?.document ?: document
        val startingTitle = if (restored != null) draft.title else row.title
        title.edit { replace(0, length, startingTitle) }
        title.undoState.clearHistory()
        startingDocument.blocks.forEach { addRuntimeBlock(EditorBlock(it)) }
        if (restored != null) {
            recovered = true
            generation = draft.generation
            saveStatus = SaveStatus.PENDING
            scheduleSave()
        } else {
            journal.clear(noteId)
        }
        val startingTitleSnapshot = title.text.toString()
        viewModelScope.launch {
            var previous = startingTitleSnapshot
            snapshotFlow { title.text.toString() }.collect { current ->
                if (current != previous) { previous = current; markDirty() }
            }
        }
        load = EditorLoad.Ready
    }

    private fun addRuntimeBlock(block: EditorBlock, index: Int = blocks.size) {
        blocks.add(index, block)
        viewModelScope.launch {
            var previous = block.snapshot()
            snapshotFlow { block.snapshot() }.collect { current ->
                if (current != previous) { previous = current; markDirty() }
            }
        }
    }

    fun snapshot(): NoteDocumentV1 = DocumentCodec.canonical(NoteDocumentV1(blocks = blocks.map(EditorBlock::snapshot)))

    private fun draft(): RecoveryDraft = RecoveryDraft(noteId, revision, generation, title.text.toString(), DocumentCodec.encode(snapshot()))

    private fun markDirty() {
        if (load != EditorLoad.Ready || deleting) return
        generation++
        saveStatus = SaveStatus.PENDING
        saveError = null
        journalQueue.trySend(draft())
        scheduleSave()
    }

    private fun scheduleSave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch { delay(AUTOSAVE_IDLE_MS); flush() }
    }

    suspend fun flush(): Boolean = writeMutex.withLock {
        if (load != EditorLoad.Ready || deleting) return@withLock false
        while (true) {
            val actualTitle = title.text.toString()
            val actualPayload = DocumentCodec.encode(snapshot())
            if (generation == durableGeneration && (actualTitle != durableTitle || actualPayload != durablePayload)) {
                generation++
                saveStatus = SaveStatus.PENDING
            }
            if (durableGeneration >= generation && saveStatus != SaveStatus.ERROR) break
            val current = draft()
            try {
                journal.write(current)
                saveStatus = SaveStatus.SAVING
                val saved = notes.save(noteId, revision, current.title, current.documentPayload)
                revision = saved.revision
                durableGeneration = current.generation
                durableTitle = current.title
                durablePayload = current.documentPayload
                if (generation == current.generation) {
                    if (title.text.toString() == current.title && DocumentCodec.encode(snapshot()) == current.documentPayload) {
                        journal.clear(noteId)
                        saveStatus = SaveStatus.SAVED
                        saveError = null
                        recovered = false
                    } else saveStatus = SaveStatus.PENDING
                } else saveStatus = SaveStatus.PENDING
            } catch (_: Exception) {
                saveStatus = SaveStatus.ERROR
                saveError = "Could not save this note. Your recovery draft is kept when available."
                return@withLock false
            }
        }
        true
    }

    fun flushOnStop() { viewModelScope.launch { flush() } }
    fun leave(onSuccess: () -> Unit) {
        viewModelScope.launch {
            autosaveJob?.cancel()
            if (flush()) onSuccess() else exitFailure = true
        }
    }
    fun retryExit(onSuccess: () -> Unit) { exitFailure = false; leave(onSuccess) }
    fun keepEditing() { exitFailure = false }
    fun leaveWithoutLatest(onSuccess: () -> Unit) { exitFailure = false; onSuccess() }

    suspend fun checkAvailability() {
        if (load != EditorLoad.Ready) return
        val row = notes.find(noteId)
        if (row == null) load = EditorLoad.Missing
        else if (row.deletedAt != null) load = EditorLoad.Deleted
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            autosaveJob?.cancel()
            writeMutex.withLock {
                deleting = true
                try {
                    val row = notes.find(noteId)
                    if (row == null || row.deletedAt != null || !notes.softDelete(noteId, row.revision)) {
                        error("Note could not be deleted")
                    }
                    journal.clear(noteId)
                    onDeleted()
                } catch (_: Exception) {
                    deleting = false
                    saveStatus = SaveStatus.ERROR
                    saveError = "Could not delete this note"
                }
            }
        }
    }

    fun setBlockType(id: String, type: BlockType) {
        val block = blocks.firstOrNull { it.id == id } ?: return
        if (block.type == type) return
        val old = block.type
        block.type = type
        if (type != BlockType.CHECKLIST_ITEM) block.checked = false
        undo.addLast(StructuralAction({ block.type = old; markDirty() }, { block.type = type; markDirty() }))
        redo.clear(); markDirty()
    }
    fun toggleChecked(id: String) {
        val block = blocks.firstOrNull { it.id == id && it.type == BlockType.CHECKLIST_ITEM } ?: return
        block.checked = !block.checked
        undo.addLast(StructuralAction({ block.checked = !block.checked; markDirty() }, { block.checked = !block.checked; markDirty() }))
        redo.clear(); markDirty()
    }
    fun insertAfter(id: String?, type: BlockType? = null) {
        val index = blocks.indexOfFirst { it.id == id }.let { if (it < 0) blocks.size else it + 1 }
        val prior = blocks.getOrNull(index - 1)
        if (type == null && prior != null && prior.text.text.isEmpty() && prior.type != BlockType.DIVIDER) {
            if (prior.type == BlockType.BULLET_ITEM || prior.type == BlockType.NUMBERED_ITEM || prior.type == BlockType.CHECKLIST_ITEM || prior.type == BlockType.QUOTE) setBlockType(prior.id, BlockType.PARAGRAPH)
            focusedBlockId = prior.id
            return
        }
        val nextType = type ?: when (prior?.type) {
            BlockType.BULLET_ITEM, BlockType.NUMBERED_ITEM, BlockType.CHECKLIST_ITEM -> if (prior.text.text.isEmpty()) BlockType.PARAGRAPH else prior.type
            else -> BlockType.PARAGRAPH
        }
        val block = EditorBlock(DocumentBlock(UUID.randomUUID().toString(), nextType))
        addRuntimeBlock(block, index)
        focusedBlockId = if (nextType == BlockType.DIVIDER) prior?.id else block.id
        undo.addLast(StructuralAction({ blocks.remove(block); markDirty() }, { addRuntimeBlock(block, index.coerceAtMost(blocks.size)); markDirty() }))
        redo.clear(); markDirty()
    }
    fun removeEmpty(id: String) {
        val index = blocks.indexOfFirst { it.id == id }
        if (index <= 0 || blocks[index].text.text.isNotEmpty()) return
        val block = blocks.removeAt(index)
        focusedBlockId = blocks[index - 1].id
        undo.addLast(StructuralAction({ addRuntimeBlock(block, index); markDirty() }, { blocks.remove(block); markDirty() }))
        redo.clear(); markDirty()
    }
    fun removeBlock(id: String) {
        val index = blocks.indexOfFirst { it.id == id }
        if (index < 0 || blocks.size == 1) return
        val block = blocks.removeAt(index)
        focusedBlockId = blocks.getOrNull((index - 1).coerceAtLeast(0))?.id
        undo.addLast(StructuralAction({ addRuntimeBlock(block, index.coerceAtMost(blocks.size)); markDirty() }, { blocks.remove(block); markDirty() }))
        redo.clear(); markDirty()
    }
    fun toggleMark(type: MarkType) {
        val block = blocks.firstOrNull { it.id == focusedBlockId } ?: return
        if (block.undoController.toggle(type)) markDirty()
    }
    fun isMarkSelected(type: MarkType): Boolean = blocks.firstOrNull { it.id == focusedBlockId }?.text?.let { RichTextMapper.selectionHas(it, type) } ?: false
    fun undo() {
        if (focusedBlockId == null && title.undoState.canUndo) { title.undoState.undo(); return }
        val controller = blocks.firstOrNull { it.id == focusedBlockId }?.undoController
        if (controller?.canUndo == true) controller.undo()
        else if (undo.isNotEmpty()) { val action = undo.removeLast(); action.undo(); redo.addLast(action) }
    }
    fun redo() {
        if (focusedBlockId == null && title.undoState.canRedo) { title.undoState.redo(); return }
        val controller = blocks.firstOrNull { it.id == focusedBlockId }?.undoController
        if (controller?.canRedo == true) controller.redo()
        else if (redo.isNotEmpty()) { val action = redo.removeLast(); action.redo(); undo.addLast(action) }
    }
    val canUndo: Boolean get() = (if (focusedBlockId == null) title.undoState.canUndo else blocks.firstOrNull { it.id == focusedBlockId }?.undoController?.canUndo == true) || undo.isNotEmpty()
    val canRedo: Boolean get() = (if (focusedBlockId == null) title.undoState.canRedo else blocks.firstOrNull { it.id == focusedBlockId }?.undoController?.canRedo == true) || redo.isNotEmpty()
}
