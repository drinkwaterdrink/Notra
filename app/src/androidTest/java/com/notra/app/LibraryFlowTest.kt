package com.notra.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.notra.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = ApplicationProvider.getApplicationContext<NotraApplication>().container
    private fun library() { compose.onNodeWithTag("destination_Library").performClick() }
    private fun click(text: String) { val node = compose.onNodeWithText(text, useUnmergedTree = false); runCatching { node.performScrollTo() }; node.performClick() }
    private fun waitTag(tag: String) { compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() } }
    private fun options() { compose.onNodeWithTag("library_options").performClick() }
    private fun location(text: String) { compose.onNodeWithTag("library_location").performClick(); click(text) }

    @Test fun createNestedFoldersAndCaptureAtLocationThenBackUp() {
        val name = "Folder-${UUID.randomUUID().toString().take(6)}"
        library(); options(); click("New folder")
        compose.onNodeWithTag("organization_name").performTextInput(name); click("Apply")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).performClick()
        options(); click("New folder"); compose.onNodeWithTag("organization_name").performTextInput("Child"); click("Apply")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Child")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Child").performClick()
        compose.onNodeWithTag("new_note").performClick(); waitTag("title_field")
        compose.onNodeWithTag("title_field").performTextInput("Folder capture $name")
        compose.onNodeWithTag("editor_back").performClick(); waitTag("library_location")
        runBlocking {
            val allFolders = container.library.folders.firstValue()
            val parent = allFolders.first { it.name == name }
            val folder = allFolders.first { it.name == "Child" && it.parentFolderId == parent.id }
            val note = container.library.notes.firstValue().first { it.title == "Folder capture $name" }
            assertEquals(folder.id, note.folderId)
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("library_location").assertTextEquals("Library / $name")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("library_location").assertTextEquals("Library")
    }
    @Test fun selectionBackBulkTrashConfirmationAndRestore() {
        val title = "Bulk-${UUID.randomUUID().toString().take(6)}"
        val notes = runBlocking { (1..2).map { container.library.createNote(null).let { row -> container.notes.save(row.id, row.revision, "$title $it", row.documentPayload) } } }
        library(); waitTag("note_${notes[0].id}")
        compose.onNodeWithTag("note_${notes[0].id}").performTouchInput { longClick() }
        compose.onNodeWithTag("note_${notes[1].id}").performClick()
        compose.onNodeWithText("2 selected · Cancel").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("library_location").assertIsDisplayed()
        compose.onNodeWithTag("note_${notes[0].id}").performTouchInput { longClick() }
        compose.onNodeWithTag("note_${notes[1].id}").performClick(); click("Actions"); click("Move to Trash")
        runBlocking { notes.forEach { assertNull(container.notes.find(it.id)!!.deletedAt) } }
        click("Confirm")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("note_${notes[0].id}")).fetchSemanticsNodes().isEmpty() }
        location("Trash"); waitTag("note_${notes[0].id}")
        compose.onNodeWithTag("note_${notes[0].id}").performClick(); click("Restore")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("note_${notes[0].id}")).fetchSemanticsNodes().isEmpty() }
        runBlocking { assertNull(container.notes.find(notes[0].id)!!.deletedAt) }
    }
    @Test fun listGridPreferenceSurvivesActivityRecreationAndTagActionsAreReachable() {
        library()
        val before = runBlocking { container.preferences.libraryPreferences.firstValue() }
        val snapshot = runBlocking { container.library.notes.firstValue() }
        compose.onNodeWithTag("library_view").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(if (before.grid) "Grid" else "List")).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        library(); compose.onNodeWithTag("library_view").assertTextEquals(if (before.grid) "Grid" else "List")
        runBlocking { assertEquals(snapshot, container.library.notes.firstValue()); container.preferences.setLibrary(before) }
        options(); click("Manage tags"); click("New tag")
        val tag = "Tag-${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithTag("organization_name").performTextInput(tag); click("Apply")
        options(); click("Manage tags"); click("Rename $tag")
        compose.onNodeWithTag("organization_name").performTextClearance()
        compose.onNodeWithTag("organization_name").performTextInput("$tag renamed"); click("Apply")
        options(); click("Manage tags"); click("Delete $tag renamed")
        compose.onNodeWithText("Only this tag and its relationships are removed. Notes are preserved.").assertIsDisplayed()
        click("Delete tag")
    }
    @Test fun permanentDeleteRequiresConfirmationAndGlobalCaptureIsUnfiled() {
        val title = "Permanent-${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithTag("new_note").performClick(); waitTag("title_field")
        compose.onNodeWithTag("title_field").performTextInput(title)
        compose.onNodeWithTag("editor_back").performClick(); waitTag("destination_Library")
        val row = runBlocking { container.library.notes.firstValue().first { it.title == title } }
        assertNull(row.folderId)
        runBlocking { container.library.mutate(mapOf(row.id to row.revision), "trash") }
        library(); location("Trash"); waitTag("note_${row.id}")
        compose.onNodeWithTag("note_${row.id}").performClick(); click("Permanent delete")
        runBlocking { assertNotNull(container.notes.find(row.id)) }
        compose.onNodeWithText("These notes and their recovery drafts will be permanently removed. This cannot be undone.").assertIsDisplayed()
        click("Confirm")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("note_${row.id}")).fetchSemanticsNodes().isEmpty() }
        runBlocking { assertNull(container.notes.find(row.id)); assertNull(container.drafts.read(row.id)) }
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
