package com.notra.app.library

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notra.app.data.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.Locale

enum class LibraryMode(val label: String) { ROOT("Library"), FOLDER("Folder"), UNFILED("Unfiled"), FAVORITES("Favorites"), ARCHIVE("Archive"), TRASH("Trash") }
enum class LibrarySort(val label: String) { UPDATED_NEWEST("Updated newest"), UPDATED_OLDEST("Updated oldest"), CREATED_NEWEST("Created newest"), CREATED_OLDEST("Created oldest"), TITLE_ASC("Title A–Z"), TITLE_DESC("Title Z–A") }
data class LibraryFilter(val tag: String? = null, val pinned: Boolean = false, val favorite: Boolean = false)
data class LibraryLocation(val mode: LibraryMode = LibraryMode.ROOT, val folder: String? = null)
data class LibraryData(val notes: List<NoteEntity> = emptyList(), val folders: List<FolderEntity> = emptyList(), val tags: List<TagEntity> = emptyList(), val links: List<NoteTag> = emptyList())
sealed interface LibraryDialog {
    data class FolderName(val id: String? = null) : LibraryDialog
    data class FolderActions(val id: String) : LibraryDialog
    data class FolderMove(val id: String) : LibraryDialog
    data class ConfirmFolderDelete(val id: String) : LibraryDialog
    data class NoteActions(val revisions: Map<String, Long>) : LibraryDialog
    data class MoveNotes(val revisions: Map<String, Long>) : LibraryDialog
    data class NoteTags(val revisions: Map<String, Long>) : LibraryDialog
    data class ConfirmNotes(val revisions: Map<String, Long>, val permanent: Boolean) : LibraryDialog
    data object TagManager : LibraryDialog
    data class TagName(val id: String? = null) : LibraryDialog
    data class ConfirmTagDelete(val id: String) : LibraryDialog
    data object Options : LibraryDialog
    data object Locations : LibraryDialog
}

fun libraryNotes(data: LibraryData, location: LibraryLocation, filter: LibraryFilter, sort: LibrarySort, pinnedFirst: Boolean): List<NoteEntity> {
    val eligible = data.notes.filter { note ->
        val lifecycle = when (location.mode) {
            LibraryMode.TRASH -> note.deletedAt != null
            LibraryMode.ARCHIVE -> note.deletedAt == null && note.archivedAt != null
            else -> note.deletedAt == null && note.archivedAt == null
        }
        lifecycle && when (location.mode) {
            LibraryMode.ROOT, LibraryMode.UNFILED -> note.folderId == null
            LibraryMode.FOLDER -> note.folderId == location.folder
            LibraryMode.FAVORITES -> note.favorite
            else -> true
        } && (!filter.pinned || note.pinned) && (!filter.favorite || note.favorite) &&
            (filter.tag == null || data.links.any { it.noteId == note.id && it.tagId == filter.tag })
    }
    val comparator = when (sort) {
        LibrarySort.UPDATED_NEWEST -> compareByDescending<NoteEntity> { it.updatedAt }
        LibrarySort.UPDATED_OLDEST -> compareBy { it.updatedAt }
        LibrarySort.CREATED_NEWEST -> compareByDescending { it.createdAt }
        LibrarySort.CREATED_OLDEST -> compareBy { it.createdAt }
        LibrarySort.TITLE_ASC -> compareBy { it.title.lowercase(Locale.ROOT) }
        LibrarySort.TITLE_DESC -> compareByDescending { it.title.lowercase(Locale.ROOT) }
    }.thenBy { it.id }
    return eligible.sortedWith(if (pinnedFirst) compareByDescending<NoteEntity> { it.pinned }.then(comparator) else comparator)
}

class LibraryViewModel(private val repository: LibraryRepository, private val preferences: PreferencesRepository) : ViewModel() {
    var data by mutableStateOf(LibraryData()); private set
    var location by mutableStateOf(LibraryLocation()); private set
    var filter by mutableStateOf(LibraryFilter()); private set
    var preference by mutableStateOf(LibraryPreferences()); private set
    var selection by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    var dialog by mutableStateOf<LibraryDialog?>(null)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    val physical: Boolean get() = location.mode == LibraryMode.ROOT || location.mode == LibraryMode.FOLDER
    val createFolderId: String? get() = if (location.mode == LibraryMode.FOLDER) location.folder else null
    val sort: LibrarySort get() = LibrarySort.entries.find { it.name == preference.sort } ?: LibrarySort.UPDATED_NEWEST
    val notes get() = libraryNotes(data, location, filter, sort, preference.pinnedFirst)
    val folders get() = if (physical) data.folders.filter { it.parentFolderId == createFolderId }.sortedWith(compareBy<FolderEntity> { it.sortOrder }.thenBy { normalizedName(it.name) }.thenBy { it.id }) else emptyList()
    val breadcrumbs: List<FolderEntity> get() {
        val result = mutableListOf<FolderEntity>(); var id = createFolderId; val seen = mutableSetOf<String>()
        while (id != null && seen.add(id)) { val folder = data.folders.find { it.id == id } ?: break; result.add(0, folder); id = folder.parentFolderId }
        return result
    }
    init {
        viewModelScope.launch { combine(repository.notes, repository.folders, repository.tags, repository.links) { n, f, t, l -> LibraryData(n, f, t, l) }.collect { current ->
            data = current
            selection = selection.filterKeys { id -> current.notes.any { it.id == id } }
            if (location.mode == LibraryMode.FOLDER && current.folders.none { it.id == location.folder }) navigate(LibraryLocation())
        } }
        viewModelScope.launch { preferences.libraryPreferences.collect { preference = it } }
    }
    fun navigate(next: LibraryLocation) { location = next; selection = emptyMap(); dialog = null; error = null }
    fun back(): Boolean {
        if (selection.isNotEmpty()) { selection = emptyMap(); return true }
        if (location.mode != LibraryMode.ROOT) {
            val parent = data.folders.find { it.id == location.folder }?.parentFolderId
            navigate(if (parent != null) LibraryLocation(LibraryMode.FOLDER, parent) else LibraryLocation()); return true
        }
        return false
    }
    fun select(note: NoteEntity) { selection = if (note.id in selection) selection - note.id else selection + (note.id to note.revision) }
    fun clearSelection() { selection = emptyMap() }
    fun changeFilter(next: LibraryFilter) { filter = next; selection = emptyMap() }
    fun changePreference(next: LibraryPreferences) { work { preferences.setLibrary(next) } }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { action(); dialog = null; selection = emptyMap() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not complete this action. Try again." }
            finally { busy = false }
        }
    }
    fun newNote(open: (String) -> Unit) { val folder = createFolderId; work { open(repository.createNote(folder).id) } }
    fun folder(name: String, id: String?) { val parent = createFolderId; work { if (id == null) repository.createFolder(name, parent) else repository.changeFolder(id, name) } }
    fun moveFolder(id: String, parent: String?) = work { repository.changeFolder(id, parent = parent, moving = true) }
    fun deleteFolder(id: String) = work { repository.dissolveFolder(id) }
    fun tag(name: String, id: String?) = work { if (id == null) repository.createTag(name) else repository.renameTag(id, name) }
    fun deleteTag(id: String) = work { repository.deleteTag(id) }
    fun mutate(revisions: Map<String, Long>, action: String, folder: String? = null, tag: String? = null, value: Boolean = true) = work { repository.mutate(revisions, action, folder, tag, value) }
    fun permanentDelete(revisions: Map<String, Long>) = work { repository.permanentDelete(revisions) }
}
