package com.notra.app.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notra.app.data.NoteStore
import com.notra.app.data.RecoveryJournal
import com.notra.app.document.BlockType
import com.notra.app.document.MarkType
import com.notra.app.ui.NotraColors
import com.notra.app.ui.TopChrome
import com.notra.app.ui.BottomChrome
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(noteId: String, notes: NoteStore, journal: RecoveryJournal, onLeave: () -> Unit) {
    val model: EditorViewModel = viewModel(key = "editor_$noteId", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(noteId, notes, journal) as T
    })
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(model, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> model.flushOnStop()
                Lifecycle.Event.ON_START -> lifecycleOwner.lifecycleScope.launch { model.checkAvailability() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { if (model.load == EditorLoad.Ready) model.leave(onLeave) else onLeave() }
    when (val state = model.load) {
        EditorLoad.Loading -> CenterMessage("Opening note…", onLeave)
        EditorLoad.Missing -> CenterMessage("This note was not found.", onLeave)
        EditorLoad.Deleted -> CenterMessage("This note was deleted.", onLeave)
        is EditorLoad.Unsupported -> CenterMessage("This document uses unsupported version ${state.version}. Its original data is preserved.", onLeave)
        is EditorLoad.Invalid -> CenterMessage("This document could not be read. Its original data is preserved.", onLeave)
        EditorLoad.Ready -> ReadyEditor(model, onLeave)
    }
    if (model.exitFailure) AlertDialog(onDismissRequest = model::keepEditing, title = { Text("Note not saved") },
        text = { Text(model.saveError ?: "Your latest edits are not saved.") },
        confirmButton = { TextButton(onClick = { model.retryExit(onLeave) }) { Text("Retry") } },
        dismissButton = {
            Column {
                TextButton(onClick = model::keepEditing) { Text("Keep editing") }
                TextButton(onClick = { model.leaveWithoutLatest(onLeave) }) { Text("Leave without latest changes") }
            }
        })
}

@Composable
private fun CenterMessage(message: String, onLeave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(NotraColors.Background).safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(message, color = NotraColors.Text)
        TextButton(onClick = onLeave) { Text("Back") }
    }
}

@Composable
private fun ReadyEditor(model: EditorViewModel, onLeave: () -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    // IME consumes the keyboard region once for the whole editor. Custom bars own
    // their system/cutout edges; content consumes Scaffold's padding below.
    Scaffold(modifier = Modifier.imePadding(), containerColor = NotraColors.Background, contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopChrome {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp).testTag("editor_top"), verticalAlignment = Alignment.CenterVertically) {
                    DockButton("Back", "‹", "editor_back", onClick = { model.leave(onLeave) })
                    Spacer(Modifier.weight(1f))
                    val stateText = when (model.saveStatus) {
                        SaveStatus.SAVED -> ""
                        SaveStatus.PENDING -> "Pending"
                        SaveStatus.SAVING -> "Saving"
                        SaveStatus.ERROR -> "Save error"
                    }
                    if (stateText.isNotEmpty()) Text(stateText, color = if (model.saveStatus == SaveStatus.ERROR) MaterialTheme.colorScheme.error else NotraColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("save_status"))
                    DockButton("Undo", "↶", "undo", enabled = model.canUndo, onClick = model::undo)
                    DockButton("Redo", "↷", "redo", enabled = model.canRedo, onClick = model::redo)
                    Box {
                        TextButton(onClick = { moreOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("More") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("Delete note") }, onClick = { moreOpen = false; deleteConfirm = true })
                        }
                    }
                }
                if (model.recovered) Text("Recovered unsaved changes", color = NotraColors.Accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp))
                model.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp).clickable { model.flushOnStop() }) }
            }
        },
        bottomBar = { Box(Modifier.background(NotraColors.Surface)) { BottomChrome { FormattingToolbar(model) } } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 22.dp).testTag("editor_body")) {
            item {
                BasicTextField(state = model.title, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp).onFocusChanged { if (it.isFocused) model.focusedBlockId = null }.testTag("title_field"),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(color = NotraColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(NotraColors.Accent),
                    decorator = { inner -> Box { if (model.title.text.isEmpty()) Text("Untitled", color = NotraColors.Muted, fontSize = 24.sp); inner() } })
            }
            itemsIndexed(model.blocks, key = { _, block -> block.id }) { index, block ->
                BlockField(block, index, model)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("Delete note?") },
        text = { Text("This note will leave your active notes.") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; model.delete(onLeave) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } })
}

@Composable
private fun BlockField(block: EditorBlock, index: Int, model: EditorViewModel) {
    val requester = remember(block.id) { FocusRequester() }
    LaunchedEffect(model.focusedBlockId) { if (model.focusedBlockId == block.id && block.type != BlockType.DIVIDER) requester.requestFocus() }
    if (block.type == BlockType.DIVIDER) {
        Row(Modifier.fillMaxWidth().testTag("block_$index"), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 15.dp).height(1.dp).background(NotraColors.Border))
            TextButton(onClick = { model.removeBlock(block.id) }) { Text("Remove") }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        val marker = when (block.type) {
            BlockType.BULLET_ITEM -> "•"
            BlockType.NUMBERED_ITEM -> {
                var count = 0
                for (i in index downTo 0) { if (model.blocks[i].type != BlockType.NUMBERED_ITEM) break; count++ }
                "$count."
            }
            BlockType.CHECKLIST_ITEM -> if (block.checked) "☑" else "☐"
            BlockType.QUOTE -> "│"
            else -> null
        }
        if (block.type == BlockType.CHECKLIST_ITEM) {
            Box(Modifier.width(48.dp).heightIn(min = 48.dp).toggleable(block.checked, role = Role.Checkbox,
                onValueChange = { model.toggleChecked(block.id) }).semantics { contentDescription = "Checklist item" }.testTag("check_$index"),
                contentAlignment = Alignment.TopStart) { Text(marker.orEmpty(), color = NotraColors.Accent) }
        } else if (marker != null) {
            Text(marker, modifier = Modifier.width(28.dp), color = NotraColors.Accent)
        }
        val style = when (block.type) {
            BlockType.HEADING_1 -> TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = NotraColors.Text)
            BlockType.HEADING_2 -> TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = NotraColors.Text)
            BlockType.HEADING_3 -> TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = NotraColors.Text)
            BlockType.CODE -> TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = NotraColors.Text)
            BlockType.QUOTE -> TextStyle(fontSize = 16.sp, color = NotraColors.Muted)
            else -> TextStyle(fontSize = 16.sp, color = NotraColors.Text)
        }
        BasicTextField(state = block.text,
            modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { if (it.isFocused) model.focusedBlockId = block.id }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && block.text.text.isEmpty() && block.text.selection.collapsed && block.text.selection.start == 0) {
                        model.removeEmpty(block.id); true
                    } else false
                }.testTag("block_$index"),
            textStyle = style, cursorBrush = SolidColor(NotraColors.Accent),
            decorator = { inner -> Box(Modifier.fillMaxWidth()) { if (block.text.text.isEmpty()) Text("Write…", color = NotraColors.Muted, style = style); inner() } })
    }
}

@Composable
private fun DockButton(description: String, label: String, tag: String, enabled: Boolean = true,
    onClick: () -> Unit, selected: Boolean? = null, style: TextStyle = MaterialTheme.typography.titleMedium) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp),
        modifier = Modifier.width(48.dp).heightIn(min = 48.dp).testTag(tag).semantics {
            contentDescription = description
            if (selected != null) stateDescription = if (selected) "Applied" else "Not applied"
        }) {
        Text(label, style = style, color = if (!enabled) NotraColors.Muted.copy(alpha = .45f)
            else if (selected == true) NotraColors.Accent else NotraColors.Text)
    }
}

@Composable
private fun FormattingToolbar(model: EditorViewModel) {
    var styleOpen by remember { mutableStateOf(false) }
    var insertOpen by remember { mutableStateOf(false) }
    val focused = model.focusedBlockId
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp).testTag("editor_dock"),
        verticalAlignment = Alignment.CenterVertically) {
        listOf(MarkType.BOLD to "B", MarkType.ITALIC to "I", MarkType.UNDERLINE to "U", MarkType.STRIKETHROUGH to "S", MarkType.HIGHLIGHT to "H").forEach { (type, label) ->
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (type == MarkType.BOLD) FontWeight.Bold else FontWeight.Medium,
                fontStyle = if (type == MarkType.ITALIC) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when (type) { MarkType.UNDERLINE -> TextDecoration.Underline; MarkType.STRIKETHROUGH -> TextDecoration.LineThrough; else -> null })
            DockButton(type.name.lowercase().replaceFirstChar { it.uppercase() }, label, "mark_${type.name}", focused != null,
                onClick = { model.toggleMark(type) }, selected = model.isMarkSelected(type), style = textStyle)
        }
        Box {
            DockButton("Block and insertion actions", "+", "insert_menu", onClick = { insertOpen = true })
            DropdownMenu(expanded = insertOpen, onDismissRequest = { insertOpen = false }) {
                DropdownMenuItem(text = { Text("Block style…") }, modifier = Modifier.testTag("block_style"),
                    onClick = { insertOpen = false; styleOpen = true })
                DropdownMenuItem(text = { Text("Next block") }, modifier = Modifier.testTag("insert_block"),
                    onClick = { insertOpen = false; model.insertAfter(focused) })
                listOf(BlockType.BULLET_ITEM to "Bullet list", BlockType.NUMBERED_ITEM to "Numbered list",
                    BlockType.CHECKLIST_ITEM to "Checklist", BlockType.DIVIDER to "Divider").forEach { (type, label) ->
                    DropdownMenuItem(text = { Text(label) }, modifier = Modifier.testTag(if (type == BlockType.CHECKLIST_ITEM) "insert_checklist" else "insert_${type.name}"),
                        onClick = { insertOpen = false; model.insertAfter(focused, type) })
                }
            }
            DropdownMenu(expanded = styleOpen, onDismissRequest = { styleOpen = false }) {
                BlockType.entries.filter { it != BlockType.DIVIDER }.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                        enabled = focused != null,
                        onClick = { focused?.let { model.setBlockType(it, type) }; styleOpen = false })
                }
                DropdownMenuItem(text = { Text("Remove block") }, enabled = focused != null,
                    onClick = { focused?.let(model::removeBlock); styleOpen = false })
            }
        }
    }
}
