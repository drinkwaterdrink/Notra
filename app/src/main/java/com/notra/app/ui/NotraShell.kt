package com.notra.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notra.app.Destination
import com.notra.app.ShellViewModel
import com.notra.app.data.NoteEntity
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead

@Composable
fun NotraShell(model: ShellViewModel, onNew: () -> Unit, onOpen: (String) -> Unit) {
    val destination by model.destination.collectAsState()
    val reducedMotion by model.reducedMotion.collectAsState()
    val active by model.activeNotes.collectAsState()
    Box(Modifier.fillMaxSize().background(NotraColors.Background)) {
        if (destination == Destination.BOARD) BoardSubstrate()
        Scaffold(containerColor = Color.Transparent, contentColor = NotraColors.Text, contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopChrome {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(destination.label, modifier = Modifier.weight(1f).testTag("screen_title").semantics { heading() },
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onNew, modifier = Modifier.heightIn(min = 48.dp).testTag("new_note")) { Text("+ New note") }
                    }
                    if (model.createError) Text("Could not create note. Try again.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            },
            bottomBar = { DestinationDock(destination, model::select) }
        ) { padding ->
            val content = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            when (destination) {
                Destination.BOARD -> Box(content.padding(22.dp)) {
                    Column(Modifier.align(Alignment.CenterStart)) {
                        Text("A place to think", style = MaterialTheme.typography.titleMedium, color = NotraColors.Text)
                        Text("Capture a note. Make room for ideas.", style = MaterialTheme.typography.bodySmall, color = NotraColors.Muted)
                    }
                }
                Destination.LIBRARY -> LazyColumn(content.testTag("library_notes"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    item { Text("${active.size} ${if (active.size == 1) "note" else "notes"}", style = MaterialTheme.typography.labelMedium,
                        color = NotraColors.Muted, modifier = Modifier.padding(vertical = 8.dp)) }
                    if (active.isEmpty()) item { Text("No notes yet. Capture your first idea.", color = NotraColors.Muted,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp)) }
                    items(active, key = { it.id }) { note -> NoteListRow(note, onOpen) }
                }
                Destination.SEARCH -> Column(content.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Search is not available yet", style = MaterialTheme.typography.titleSmall)
                    Text("Open your notes from Library.", color = NotraColors.Muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Destination.SETTINGS -> LazyColumn(content, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    item {
                        Text("EXPERIENCE", style = MaterialTheme.typography.labelSmall, color = NotraColors.Muted)
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Reduced motion", style = MaterialTheme.typography.titleSmall)
                                Text("Less movement, the same feedback", color = NotraColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = reducedMotion, onCheckedChange = model::setReducedMotion,
                                modifier = Modifier.semantics { contentDescription = "Reduced motion" })
                        }
                        HorizontalDivider(color = NotraColors.Border.copy(alpha = .4f))
                    }
                }
            }
        }
    }
}

/** Visual substrate only. I-004 can replace it without replacing shell chrome. */
@Composable
private fun BoardSubstrate() {
    Canvas(Modifier.fillMaxSize().background(NotraColors.Canvas).testTag("board_substrate")) {
        val spacing = 28.dp.toPx()
        var x = spacing / 2
        while (x < size.width) {
            var y = spacing / 2
            while (y < size.height) {
                drawCircle(NotraColors.Muted.copy(alpha = .075f), .65.dp.toPx(), Offset(x, y))
                y += spacing
            }
            x += spacing
        }
    }
}

@Composable
private fun DestinationDock(selected: Destination, onSelect: (Destination) -> Unit) {
    BottomChrome {
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp),
            color = NotraColors.Surface, border = BorderStroke(1.dp, NotraColors.Border.copy(alpha = .5f)), shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().selectableGroup().testTag("destination_dock")) {
                Destination.entries.forEach { destination ->
                    val active = destination == selected
                    Column(Modifier.weight(1f).heightIn(min = 64.dp).selectable(active, role = Role.Tab,
                        onClick = { onSelect(destination) }).testTag("destination_${destination.label}").padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        DestinationGlyph(destination, if (active) NotraColors.Accent else NotraColors.Muted)
                        Text(destination.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (active) NotraColors.Text else NotraColors.Muted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        Box(Modifier.width(16.dp).height(2.dp).background(if (active) NotraColors.Accent else Color.Transparent))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationGlyph(destination: Destination, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val stroke = Stroke(1.5.dp.toPx())
        when (destination) {
            Destination.BOARD -> {
                drawRect(color, Offset(w * .1f, w * .1f), Size(w * .34f, w * .5f), style = stroke)
                drawRect(color, Offset(w * .58f, w * .3f), Size(w * .3f, w * .58f), style = stroke)
            }
            Destination.LIBRARY -> repeat(3) { i ->
                val y = w * (.2f + i * .3f)
                drawLine(color, Offset(w * .15f, y), Offset(w * .85f, y), stroke.width)
            }
            Destination.SEARCH -> {
                drawCircle(color, w * .29f, Offset(w * .4f, w * .4f), style = stroke)
                drawLine(color, Offset(w * .62f, w * .62f), Offset(w * .9f, w * .9f), stroke.width)
            }
            Destination.SETTINGS -> repeat(3) { i ->
                val y = w * (.2f + i * .3f)
                val x = w * (if (i == 1) .65f else .35f)
                drawLine(color, Offset(w * .1f, y), Offset(w * .9f, y), stroke.width)
                drawCircle(NotraColors.Surface, w * .08f, Offset(x, y))
                drawCircle(color, w * .08f, Offset(x, y), style = stroke)
            }
        }
    }
}

@Composable
private fun NoteListRow(note: NoteEntity, onOpen: (String) -> Unit) {
    val preview = when (val decoded = DocumentCodec.decode(note.documentSchemaVersion, note.documentPayload)) {
        is DocumentRead.Valid -> DocumentCodec.plainText(decoded.document).take(100)
        else -> "Document needs attention"
    }
    Column(Modifier.fillMaxWidth().testTag("note_${note.id}").clickable { onOpen(note.id) }.padding(vertical = 12.dp)) {
        Text(note.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (preview.isNotBlank()) Text(preview, color = NotraColors.Muted, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        Text("Updated ${android.text.format.DateFormat.format("MMM d · h:mm a", note.updatedAt)}", color = NotraColors.Muted,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
    HorizontalDivider(color = NotraColors.Border.copy(alpha = .4f))
}
