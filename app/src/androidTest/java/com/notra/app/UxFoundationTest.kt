package com.notra.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real window-inset/bounds checks on the configured emulator, not a Samsung claim. */
@RunWith(AndroidJUnit4::class)
class UxFoundationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun editor() {
        compose.onNodeWithTag("new_note").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("block_0")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun hideKeyboard() {
        compose.runOnUiThread { WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
            .hide(WindowInsetsCompat.Type.ime()) }
        compose.waitUntil(10_000) { !imeVisible() }
        compose.waitForIdle()
    }

    private fun assertSafe(tag: String, keyboard: Boolean = false) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                if (keyboard) WindowInsetsCompat.Type.ime() else 0
            val insets = ViewCompat.getRootWindowInsets(decor)!!.getInsets(types)
            assertTrue("$tag above safe top: $bounds / $insets", bounds.top >= insets.top - 1)
            assertTrue("$tag below safe bottom: $bounds / $insets / ${decor.height}", bounds.bottom <= decor.height - insets.bottom + 1)
            assertTrue("$tag left", bounds.left >= insets.left - 1)
            assertTrue("$tag right", bounds.right <= decor.width - insets.right + 1)
        }
    }

    @Test fun fourDestinationsAreCompactSafeAndFreeOfDevelopmentCopy() {
        listOf("Library", "Search", "Settings", "Board").forEach { destination ->
            compose.onNodeWithTag("destination_$destination").performClick()
            compose.onNodeWithTag("screen_title").assertTextEquals(destination)
            assertSafe("screen_title")
            assertSafe("destination_dock")
            listOf("I-004", "I-006", "PERSONAL WORKSPACE").forEach {
                compose.onAllNodes(hasText(it, substring = true)).assertCountEquals(0)
            }
        }
        val canvas = compose.onNodeWithTag("board_substrate").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            assertEquals(0f, canvas.left, 1f)
            assertEquals(0f, canvas.top, 1f)
            assertEquals(compose.activity.window.decorView.width.toFloat(), canvas.width, 1f)
            assertEquals(compose.activity.window.decorView.height.toFloat(), canvas.height, 1f)
        }
    }

    @Test fun editorDockClearsNavigationAndImeAndSecondaryActionsRemainReachable() {
        editor()
        compose.onNodeWithTag("block_0").performClick().performTextInput("Formatting probe")
        compose.runOnUiThread { WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
            .show(WindowInsetsCompat.Type.ime()) }
        compose.waitUntil(10_000) { imeVisible() }
        compose.waitForIdle()
        assertSafe("editor_top", keyboard = true)
        assertSafe("editor_dock", keyboard = true)
        listOf("BOLD", "ITALIC", "UNDERLINE", "STRIKETHROUGH", "HIGHLIGHT").forEach {
            compose.onNodeWithTag("mark_$it").assertIsEnabled().performClick()
            val bounds = compose.onNodeWithTag("mark_$it").fetchSemanticsNode().boundsInWindow
            val minimum = 48 * compose.activity.resources.displayMetrics.density
            assertTrue("Full touch target for $it", bounds.width >= minimum - 1 && bounds.height >= minimum - 1)
        }
        val insertBounds = compose.onNodeWithTag("insert_menu").fetchSemanticsNode().boundsInWindow
        assertTrue("Full insertion touch target", insertBounds.width >= 48 * compose.activity.resources.displayMetrics.density - 1)
        compose.onNodeWithTag("insert_menu").performClick()
        compose.onNodeWithTag("block_style").performClick()
        listOf("Paragraph", "Heading 1", "Heading 2", "Heading 3", "Bullet item", "Numbered item", "Checklist item", "Quote", "Code", "Remove block")
            .forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Code").performScrollTo().performClick()
        compose.onNodeWithTag("insert_menu").performClick()
        listOf("Next block", "Bullet list", "Numbered list", "Checklist", "Divider").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        // Popups own a separate window: verify display and actual interaction rather
        // than comparing their local coordinates with the Activity's inset bounds.
        compose.onNodeWithTag("insert_checklist").assertIsDisplayed().performClick()
        compose.onNodeWithTag("check_1").performClick()
        hideKeyboard()
        assertSafe("editor_dock")
        assertSafe("editor_top")
        compose.onNodeWithTag("editor_back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("destination_dock")).fetchSemanticsNodes().isNotEmpty() }
        assertSafe("destination_dock")
    }

    @Test fun libraryNoteBeginsNearHeaderAndCanReopen() {
        editor()
        compose.onNodeWithTag("title_field").performTextInput("Compact library probe")
        compose.onNodeWithTag("editor_back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("destination_Library")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("destination_Library").performClick()
        // Root now contains physical folders. Verify compact note density in the
        // folder-free Unfiled view while keeping the original 80dp assertion.
        compose.onNodeWithTag("library_location").performClick()
        compose.onNodeWithText("Unfiled").performClick()
        val header = compose.onNodeWithTag("screen_title").fetchSemanticsNode().boundsInWindow
        val note = compose.onNodeWithText("Compact library probe").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Notes should begin near compact header", note.top - header.bottom < 80 * density)
        compose.onNodeWithText("Compact library probe").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("title_field")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("title_field").assertTextEquals("Compact library probe")
    }
}
