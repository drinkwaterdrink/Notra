package com.notra.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import com.notra.app.document.DocumentCodec
import com.notra.app.document.DocumentRead
import com.notra.app.document.MarkType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForEditor() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun createEditBackReopenAndSoftDelete() {
        compose.onNodeWithTag("new_note").performClick()
        waitForEditor()
        compose.onNodeWithTag("title_field").performTextInput("UI saved title")
        compose.onNodeWithTag("block_0").performTextInput("Saved body text")
        compose.onNodeWithTag("editor_back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("destination_Library")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("destination_Library").performClick()
        compose.onNodeWithText("UI saved title").performClick()
        waitForEditor()
        compose.onNodeWithTag("title_field").assertExists()
        compose.onNodeWithText("Saved body text").assertExists()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Delete note").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        try { compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("destination_Library")).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) { throw AssertionError(compose.onRoot().printToString(), failure) }
        compose.onNodeWithText("UI saved title").assertDoesNotExist()
    }

    @Test fun selectedBoldAndChecklistPersistInRoom() {
        val app = compose.activity.application as NotraApplication
        compose.onNodeWithTag("new_note").performClick()
        waitForEditor()
        compose.onNodeWithTag("block_0").performTextInput("Bold")
        compose.onNodeWithTag("block_0").performTextInputSelection(TextRange(0, 4))
        compose.onNodeWithTag("mark_BOLD").performClick()
        compose.onNodeWithTag("insert_checklist").performClick()
        compose.onNodeWithTag("block_1").performTextInput("Done")
        compose.onNodeWithTag("check_1").performClick()
        compose.onNodeWithTag("editor_back").performClick()
        try { compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("destination_Library")).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) { throw AssertionError(compose.onRoot().printToString(), failure) }
        lateinit var noteId: String
        runBlocking {
            val note = app.container.notes.observeActive().first().first { it.documentPayload.contains("Done") }
            noteId = note.id
            val doc = (DocumentCodec.decode(note.documentSchemaVersion, note.documentPayload) as DocumentRead.Valid).document
            assertTrue(doc.blocks[0].marks.any { it.type == MarkType.BOLD && it.start == 0 && it.end == 4 })
            assertTrue(doc.blocks[1].checked)
        }
        compose.onNodeWithTag("destination_Library").performClick()
        compose.onNodeWithTag("note_$noteId").performClick()
        waitForEditor()
        compose.onNodeWithText("Bold").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithTag("editor_back").performClick()
        runBlocking {
            val persisted = app.container.notes.find(noteId)!!
            val doc = (DocumentCodec.decode(1, persisted.documentPayload) as DocumentRead.Valid).document
            assertTrue(doc.blocks[0].marks.any { it.type == MarkType.BOLD })
            assertTrue(doc.blocks[1].checked)
        }
    }

    @Test fun backgroundFlushesDirtyNote() {
        val app = compose.activity.application as NotraApplication
        compose.onNodeWithTag("new_note").performClick()
        waitForEditor()
        compose.onNodeWithTag("title_field").performTextInput("Background flush probe")
        compose.onNodeWithTag("block_0").performTextInput("Background body")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        runBlocking {
            val note = app.container.notes.observeActive().first().first { it.title == "Background flush probe" }
            assertTrue(note.documentPayload.contains("Background body"))
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test fun systemBackFlushesBeforePop() {
        val app = compose.activity.application as NotraApplication
        compose.onNodeWithTag("new_note").performClick()
        waitForEditor()
        compose.onNodeWithTag("title_field").performTextInput("System back probe")
        compose.onNodeWithTag("block_0").performTextInput("Latest before back")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("destination_Library")).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val note = app.container.notes.observeActive().first().first { it.title == "System back probe" }
            assertTrue(note.documentPayload.contains("Latest before back"))
        }
    }
}
