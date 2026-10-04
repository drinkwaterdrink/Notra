package com.notra.app.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notra.app.data.NoteEntity
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import com.notra.app.ui.NotraColors

@Composable
fun LibraryScreen(model: LibraryViewModel, modifier: Modifier, onOpen: (String) -> Unit) {
    BackHandler(enabled = model.selection.isNotEmpty() || model.location.mode != LibraryMode.ROOT) { model.back() }
    Column(modifier.testTag("library_notes")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp)) {
            if (model.selection.isNotEmpty()) {
                TextButton(onClick = model::clearSelection, modifier = Modifier.weight(1f)) { Text("${model.selection.size} selected · Cancel") }
                TextButton(onClick = { model.dialog = LibraryDialog.NoteActions(model.selection) }, enabled = !model.busy) { Text("Actions") }
            } else {
                TextButton(onClick = { model.dialog = LibraryDialog.Locations }, modifier = Modifier.weight(1f).testTag("library_location")) {
                    Text(if (model.physical) (listOf("Library") + model.breadcrumbs.map { it.name }).joinToString(" / ") else model.location.mode.label,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { model.changePreference(model.preference.copy(grid = !model.preference.grid)) }, modifier = Modifier.testTag("library_view"), enabled = !model.busy) {
                    Text(if (model.preference.grid) "List" else "Grid")
                }
                TextButton(onClick = { model.dialog = LibraryDialog.Options }, modifier = Modifier.testTag("library_options")) { Text("Options") }
            }
        }
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("library_error")) }
        if (model.filter != LibraryFilter()) Text("Filtered · reset in Options", style = MaterialTheme.typography.labelSmall, color = NotraColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
        val folders = model.folders; val notes = model.notes
        LazyVerticalGrid(columns = if (model.preference.grid) GridCells.Adaptive(136.dp) else GridCells.Fixed(1),
            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(if (model.preference.grid) 8.dp else 0.dp)) {
            items(folders, key = { "folder_${it.id}" }) { folder ->
                Surface(color = NotraColors.Surface, border = BorderStroke(1.dp, NotraColors.Border.copy(alpha = .4f)),
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { model.navigate(LibraryLocation(LibraryMode.FOLDER, folder.id)) },
                        onLongClickLabel = "Folder actions", onLongClick = { model.dialog = LibraryDialog.FolderActions(folder.id) })
                        .semantics { contentDescription = "Folder ${folder.name}" }.testTag("folder_${folder.id}")) {
                    Column(Modifier.padding(12.dp).heightIn(min = 48.dp)) {
                        Text("FOLDER", style = MaterialTheme.typography.labelSmall, color = NotraColors.Muted)
                        Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            items(notes, key = { it.id }) { note -> NotePreview(note, model, onOpen) }
            if (folders.isEmpty() && notes.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No notes here", color = NotraColors.Muted, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
    LibraryDialogs(model, onOpen)
}

@Composable
private fun NotePreview(note: NoteEntity, model: LibraryViewModel, onOpen: (String) -> Unit) {
    val chosen = note.id in model.selection
    val preview = when (val read = DocumentCodec.decode(note.documentSchemaVersion, note.documentPayload)) {
        is DocumentRead.Valid -> DocumentCodec.plainText(read.document).take(180)
        else -> "Document needs attention"
    }
    Surface(color = if (chosen) NotraColors.Accent.copy(alpha = .15f) else if (model.preference.grid) NotraColors.Surface else NotraColors.Background,
        border = if (model.preference.grid || chosen) BorderStroke(1.dp, if (chosen) NotraColors.Accent else NotraColors.Border) else null,
        modifier = Modifier.fillMaxWidth().testTag("note_${note.id}").semantics { selected = chosen }
            .combinedClickable(onClick = {
                if (model.selection.isNotEmpty()) model.select(note)
                else if (note.deletedAt != null) model.dialog = LibraryDialog.NoteActions(mapOf(note.id to note.revision))
                else onOpen(note.id)
            }, onLongClickLabel = "Select note", onLongClick = { model.select(note) })) {
        Column(Modifier.padding(horizontal = if (model.preference.grid) 12.dp else 0.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
            Text(note.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodySmall, color = NotraColors.Muted,
                maxLines = if (model.preference.grid) 4 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            val tags = model.data.tags.filter { tag -> model.data.links.any { it.noteId == note.id && it.tagId == tag.id } }.map { it.name }
            val indicators = listOfNotNull(if (note.pinned) "Pinned" else null, if (note.favorite) "Favorite" else null) + tags
            if (indicators.isNotEmpty()) Text(indicators.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = NotraColors.Accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Updated ${android.text.format.DateFormat.format("MMM d · h:mm a", note.updatedAt)}", color = NotraColors.Muted,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
            if (!model.preference.grid) HorizontalDivider(Modifier.padding(top = 10.dp), color = NotraColors.Border.copy(alpha = .4f))
        }
    }
}

@Composable
private fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun Panel(model: LibraryViewModel, title: String, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = { if (!model.busy) model.dialog = null }, title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            content()
        } }, confirmButton = { TextButton(onClick = { model.dialog = null }, enabled = !model.busy) { Text("Close") } })
}

@Composable
private fun LibraryDialogs(model: LibraryViewModel, onOpen: (String) -> Unit) {
    when (val dialog = model.dialog) {
        null -> Unit
        LibraryDialog.Locations -> Panel(model, "Location") {
            Action("Library") { model.navigate(LibraryLocation()) }
            model.breadcrumbs.forEach { folder -> Action(folder.name) { model.navigate(LibraryLocation(LibraryMode.FOLDER, folder.id)) } }
            listOf(LibraryMode.UNFILED, LibraryMode.FAVORITES, LibraryMode.ARCHIVE, LibraryMode.TRASH).forEach { mode ->
                Action(mode.label) { model.navigate(LibraryLocation(mode)) }
            }
        }
        LibraryDialog.Options -> Panel(model, "Library options") {
            if (model.physical) Action("New folder") { model.dialog = LibraryDialog.FolderName() }
            Action("Manage tags") { model.dialog = LibraryDialog.TagManager }
            Text("Sort", style = MaterialTheme.typography.labelMedium)
            LibrarySort.entries.forEach { sort -> Action((if (model.sort == sort) "✓ " else "") + sort.label, !model.busy) { model.changePreference(model.preference.copy(sort = sort.name)) } }
            Action("Pinned first: ${if (model.preference.pinnedFirst) "On" else "Off"}", !model.busy) { model.changePreference(model.preference.copy(pinnedFirst = !model.preference.pinnedFirst)) }
            Action("Pinned filter: ${if (model.filter.pinned) "On" else "Off"}") { model.changeFilter(model.filter.copy(pinned = !model.filter.pinned)) }
            Action("Favorite filter: ${if (model.filter.favorite) "On" else "Off"}") { model.changeFilter(model.filter.copy(favorite = !model.filter.favorite)) }
            model.data.tags.forEach { tag -> Action("Tag: ${tag.name}${if (model.filter.tag == tag.id) " ✓" else ""}") { model.changeFilter(model.filter.copy(tag = tag.id)) } }
            Action("Reset filters") { model.changeFilter(LibraryFilter()) }
        }
        is LibraryDialog.FolderName -> NameDialog(model, "Folder name", dialog.id?.let { id -> model.data.folders.find { it.id == id }?.name } ?: "") { model.folder(it, dialog.id) }
        is LibraryDialog.FolderActions -> Panel(model, "Folder actions") {
            Action("Open") { model.navigate(LibraryLocation(LibraryMode.FOLDER, dialog.id)) }
            Action("Rename") { model.dialog = LibraryDialog.FolderName(dialog.id) }
            Action("Move folder") { model.dialog = LibraryDialog.FolderMove(dialog.id) }
            Action("Delete folder") { model.dialog = LibraryDialog.ConfirmFolderDelete(dialog.id) }
        }
        is LibraryDialog.FolderMove -> Panel(model, "Move folder to") {
            Action("Library root", !model.busy) { model.moveFolder(dialog.id, null) }
            model.data.folders.forEach { folder -> Action(folderPath(model, folder.id), !model.busy) { model.moveFolder(dialog.id, folder.id) } }
        }
        is LibraryDialog.ConfirmFolderDelete -> Panel(model, "Delete folder?") {
            Text("Notes and child folders will move to its parent, or Unfiled / Library root. Nothing inside is deleted.")
            Action("Delete folder", !model.busy) { model.deleteFolder(dialog.id) }
        }
        is LibraryDialog.NoteActions -> Panel(model, "Note actions") {
            val rows = model.data.notes.filter { it.id in dialog.revisions }
            val trash = rows.isNotEmpty() && rows.all { it.deletedAt != null }
            if (rows.size == 1 && !trash) Action("Open") { model.dialog = null; onOpen(rows.first().id) }
            Action("Move") { model.dialog = LibraryDialog.MoveNotes(dialog.revisions) }
            Action("Tags") { model.dialog = LibraryDialog.NoteTags(dialog.revisions) }
            Action("Pin", !model.busy) { model.mutate(dialog.revisions, "pin") }
            Action("Unpin", !model.busy) { model.mutate(dialog.revisions, "pin", value = false) }
            Action("Favorite", !model.busy) { model.mutate(dialog.revisions, "favorite") }
            Action("Unfavorite", !model.busy) { model.mutate(dialog.revisions, "favorite", value = false) }
            if (trash) {
                Action("Restore", !model.busy) { model.mutate(dialog.revisions, "restore") }
                Action("Permanent delete") { model.dialog = LibraryDialog.ConfirmNotes(dialog.revisions, true) }
            } else {
                Action("Archive", !model.busy) { model.mutate(dialog.revisions, "archive") }
                Action("Unarchive", !model.busy) { model.mutate(dialog.revisions, "archive", value = false) }
                Action("Move to Trash") { model.dialog = LibraryDialog.ConfirmNotes(dialog.revisions, false) }
            }
        }
        is LibraryDialog.MoveNotes -> Panel(model, "Move notes to") {
            Action("Unfiled", !model.busy) { model.mutate(dialog.revisions, "move") }
            model.data.folders.forEach { folder -> Action(folderPath(model, folder.id), !model.busy) { model.mutate(dialog.revisions, "move", folder = folder.id) } }
        }
        is LibraryDialog.NoteTags -> Panel(model, "Note tags") {
            model.data.tags.forEach { tag ->
                Action("Add ${tag.name}", !model.busy) { model.mutate(dialog.revisions, "tag", tag = tag.id) }
                Action("Remove ${tag.name}", !model.busy) { model.mutate(dialog.revisions, "tag", tag = tag.id, value = false) }
            }
            Action("Manage tags") { model.dialog = LibraryDialog.TagManager }
        }
        is LibraryDialog.ConfirmNotes -> Panel(model, if (dialog.permanent) "Permanently delete notes?" else "Move notes to Trash?") {
            Text(if (dialog.permanent) "These notes and their recovery drafts will be permanently removed. This cannot be undone." else "Your notes and organization are preserved. Restore them from Trash.")
            Action("Confirm", !model.busy) { if (dialog.permanent) model.permanentDelete(dialog.revisions) else model.mutate(dialog.revisions, "trash") }
        }
        LibraryDialog.TagManager -> Panel(model, "Manage tags") {
            Action("New tag") { model.dialog = LibraryDialog.TagName() }
            model.data.tags.forEach { tag ->
                Text(tag.name, style = MaterialTheme.typography.titleSmall)
                Action("Rename ${tag.name}") { model.dialog = LibraryDialog.TagName(tag.id) }
                Action("Delete ${tag.name}") { model.dialog = LibraryDialog.ConfirmTagDelete(tag.id) }
            }
        }
        is LibraryDialog.TagName -> NameDialog(model, "Tag name", dialog.id?.let { id -> model.data.tags.find { it.id == id }?.name } ?: "") { model.tag(it, dialog.id) }
        is LibraryDialog.ConfirmTagDelete -> Panel(model, "Delete tag?") {
            Text("Only this tag and its relationships are removed. Notes are preserved.")
            Action("Delete tag", !model.busy) { model.deleteTag(dialog.id) }
        }
    }
}

private fun folderPath(model: LibraryViewModel, id: String): String {
    val names = mutableListOf<String>(); val seen = mutableSetOf<String>(); var cursor: String? = id
    while (cursor != null && seen.add(cursor)) { val row = model.data.folders.find { it.id == cursor } ?: break; names.add(0, row.name); cursor = row.parentFolderId }
    return names.joinToString(" / ")
}

@Composable
private fun NameDialog(model: LibraryViewModel, title: String, initial: String, submit: (String) -> Unit) {
    var name by remember(model.dialog) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = { if (!model.busy) model.dialog = null }, title = { Text(title) },
        text = { Column { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("organization_name"))
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { submit(name) }, enabled = !model.busy) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { model.dialog = null }, enabled = !model.busy) { Text("Cancel") } })
}
