package com.notra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.notra.app.ui.NotraShell
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.notra.app.data.NoteStore
import com.notra.app.data.PreferencesRepository
import com.notra.app.editor.EditorScreen
import com.notra.app.ui.NotraTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable private data object ShellRoute : NavKey
@Serializable private data class EditorRoute(val noteId: String) : NavKey

enum class Destination(val label: String) {
    BOARD("Board"), LIBRARY("Library"), SEARCH("Search"), SETTINGS("Settings")
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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
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
