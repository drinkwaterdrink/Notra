package com.notra.app.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Column(Modifier.fillMaxSize().background(NotraColors.Background).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(message, color = NotraColors.Text)
        TextButton(onClick = onLeave) { Text("Back") }
    }
}

@Composable
private fun ReadyEditor(model: EditorViewModel, onLeave: () -> Unit) {
    var moreOpen by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    Scaffold(containerColor = NotraColors.Background,
        topBar = {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { model.leave(onLeave) }, modifier = Modifier.testTag("editor_back")) { Text("‹ Back") }
                    Spacer(Modifier.weight(1f))
                    val stateText = when (model.saveStatus) {
                        SaveStatus.SAVED -> ""
                        SaveStatus.PENDING -> "Pending"
                        SaveStatus.SAVING -> "Saving"
                        SaveStatus.ERROR -> "Save error"
                    }
                    if (stateText.isNotEmpty()) Text(stateText, color = if (model.saveStatus == SaveStatus.ERROR) MaterialTheme.colorScheme.error else NotraColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("save_status"))
                    TextButton(onClick = model::undo, enabled = model.canUndo, modifier = Modifier.testTag("undo")) { Text("Undo") }
                    TextButton(onClick = model::redo, enabled = model.canRedo, modifier = Modifier.testTag("redo")) { Text("Redo") }
                    Box {
                        TextButton(onClick = { moreOpen = true }) { Text("More") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(text = { Text("Delete note") }, onClick = { moreOpen = false; deleteConfirm = true })
                        }
                    }
                }
                if (model.recovered) Text("Recovered unsaved changes", color = NotraColors.Accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp))
                model.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp).clickable { model.flushOnStop() }) }
            }
        },
        bottomBar = { FormattingToolbar(model) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp).testTag("editor_body")) {
            item {
                BasicTextField(state = model.title, modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp).onFocusChanged { if (it.isFocused) model.focusedBlockId = null }.testTag("title_field"),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(color = NotraColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(NotraColors.Accent),
                    decorator = { inner -> Box { if (model.title.text.isEmpty()) Text("Untitled", color = NotraColors.Muted, fontSize = 24.sp); inner() } })
            }
            itemsIndexed(model.blocks, key = { _, block -> block.id }) { index, block ->
                BlockField(block, index, model)
            }
            item { Spacer(Modifier.height(90.dp)) }
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
        if (marker != null) {
            Text(marker, modifier = Modifier.width(28.dp).then(if (block.type == BlockType.CHECKLIST_ITEM) Modifier.clickable { model.toggleChecked(block.id) }.testTag("check_$index") else Modifier), color = NotraColors.Accent)
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
private fun FormattingToolbar(model: EditorViewModel) {
    var styleOpen by remember { mutableStateOf(false) }
    val focused = model.focusedBlockId
    Column(Modifier.fillMaxWidth().background(NotraColors.Surface).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { styleOpen = true }, modifier = Modifier.testTag("block_style")) { Text("Style ▾") }
                DropdownMenu(expanded = styleOpen, onDismissRequest = { styleOpen = false }) {
                    BlockType.entries.filter { it != BlockType.DIVIDER }.forEach { type ->
                        DropdownMenuItem(text = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { focused?.let { model.setBlockType(it, type) }; styleOpen = false })
                    }
                    DropdownMenuItem(text = { Text("Remove block") }, onClick = { focused?.let(model::removeBlock); styleOpen = false })
                }
            }
            listOf(MarkType.BOLD to "B", MarkType.ITALIC to "I", MarkType.UNDERLINE to "U", MarkType.STRIKETHROUGH to "S", MarkType.HIGHLIGHT to "H").forEach { (type, label) ->
                TextButton(onClick = { model.toggleMark(type) }, enabled = focused != null, modifier = Modifier.testTag("mark_${type.name}")) {
                    Text(label, color = if (model.isMarkSelected(type)) NotraColors.Accent else NotraColors.Text)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { model.insertAfter(focused) }, modifier = Modifier.testTag("insert_block")) { Text("+ Block") }
            TextButton(onClick = { model.insertAfter(focused, BlockType.BULLET_ITEM) }) { Text("• List") }
            TextButton(onClick = { model.insertAfter(focused, BlockType.NUMBERED_ITEM) }) { Text("1. List") }
            TextButton(onClick = { model.insertAfter(focused, BlockType.CHECKLIST_ITEM) }, modifier = Modifier.testTag("insert_checklist")) { Text("☐ Check") }
            TextButton(onClick = { model.insertAfter(focused, BlockType.DIVIDER) }) { Text("—") }
        }
    }
}
