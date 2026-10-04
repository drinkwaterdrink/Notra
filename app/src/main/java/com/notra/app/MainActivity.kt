package com.notra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.notra.app.data.NoteEntity
import com.notra.app.data.NoteStore
import com.notra.app.data.PreferencesRepository
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import com.notra.app.editor.EditorScreen
import com.notra.app.ui.NotraColors
import com.notra.app.ui.NotraTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable private data object ShellRoute : NavKey
@Serializable private data class EditorRoute(val noteId: String) : NavKey

enum class Destination(val label: String, val number: String) {
    BOARD("Board", "01"), LIBRARY("Library", "02"), SEARCH("Search", "03"), SETTINGS("Settings", "04")
}

class ShellViewModel(private val preferences: PreferencesRepository, private val notes: NoteStore) : ViewModel() {
    private val selection = MutableStateFlow(Destination.BOARD)
    val destination: StateFlow<Destination> = selection
    val reducedMotion = preferences.reducedMotion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val activeNotes = notes.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var createError by mutableStateOf(false); private set
    private var creating = false
    fun select(destination: Destination) { selection.value = destination }
    fun setReducedMotion(enabled: Boolean) { viewModelScope.launch { preferences.setReducedMotion(enabled) } }
    fun newNote(open: (String) -> Unit) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try { open(notes.create().id); createError = false }
            catch (_: Exception) { createError = true }
            finally { creating = false }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NotraApplication).container
        setContent {
            val backStack = rememberNavBackStack(ShellRoute)
            val shell: ShellViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ShellViewModel(container.preferences, container.notes) as T
            })
            NotraTheme {
                NavDisplay(backStack = backStack, onBack = {}, entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ), entryProvider = entryProvider {
                    entry<ShellRoute> { NotraShell(shell, onNew = { shell.newNote { backStack.add(EditorRoute(it)) } }, onOpen = { backStack.add(EditorRoute(it)) }) }
                    entry<EditorRoute> { route -> EditorScreen(route.noteId, container.notes, container.drafts, onLeave = { backStack.removeLastOrNull() }) }
                })
            }
        }
    }
}

@Composable
fun NotraShell(model: ShellViewModel, onNew: () -> Unit, onOpen: (String) -> Unit) {
    val destination by model.destination.collectAsState()
    val reducedMotion by model.reducedMotion.collectAsState()
    val active by model.activeNotes.collectAsState()
    Scaffold(containerColor = NotraColors.Background, bottomBar = {
        NavigationBar(containerColor = NotraColors.Surface) {
            Destination.entries.forEach { item ->
                NavigationBarItem(modifier = Modifier.testTag("destination_${item.label}"), selected = destination == item,
                    onClick = { model.select(item) }, icon = { Text(item.number, style = MaterialTheme.typography.labelSmall) },
                    label = { Text(item.label) }, alwaysShowLabel = true)
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp)) {
            Text("NOTRA / PERSONAL WORKSPACE", color = NotraColors.Accent, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(24.dp))
            Text(destination.label, modifier = Modifier.testTag("screen_title"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(when (destination) {
                Destination.BOARD -> "Your thinking space"
                Destination.LIBRARY -> "A place for everything"
                Destination.SEARCH -> "Find what matters"
                Destination.SETTINGS -> "Make Notra yours"
            }, color = NotraColors.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            if (destination == Destination.BOARD || destination == Destination.LIBRARY) {
                Button(onClick = onNew, modifier = Modifier.testTag("new_note")) { Text("New Note") }
                if (model.createError) Text("Could not create note. Try again.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(18.dp))
            }
            when (destination) {
                Destination.BOARD -> EmptyPanel("THE BOARD", "Your thinking space", "Spatial interaction arrives in I-004.")
                Destination.LIBRARY -> {
                    if (active.isEmpty()) Text("No notes yet", color = NotraColors.Muted)
                    active.forEach { note -> NoteListRow(note, onOpen) }
                }
                Destination.SEARCH -> EmptyPanel("SEARCH", "Retrieval from anywhere.", "Search arrives in I-006.")
                Destination.SETTINGS -> Card(colors = CardDefaults.cardColors(containerColor = NotraColors.Surface), border = BorderStroke(1.dp, NotraColors.Border), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Reduced motion", fontWeight = FontWeight.Medium)
                            Text("Stored on this device", color = NotraColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = reducedMotion, onCheckedChange = model::setReducedMotion)
                    }
                }
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
    Column(Modifier.fillMaxWidth().testTag("note_${note.id}").clickable { onOpen(note.id) }.padding(vertical = 14.dp)) {
        Text(note.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.titleMedium)
        Text(preview, color = NotraColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text("Updated ${android.text.format.DateFormat.format("MMM d · h:mm a", note.updatedAt)}", color = NotraColors.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyPanel(kicker: String, title: String, detail: String) {
    Box(Modifier.fillMaxWidth().height(260.dp).background(NotraColors.Canvas, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(kicker, color = NotraColors.Accent, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = NotraColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
