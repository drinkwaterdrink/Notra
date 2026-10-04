package com.notra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Custom chrome owns its safe area. Scaffold content consumes its resulting padding.
 * Material menus/dialogs retain their own inset handling; do not wrap them in this chrome.
 */
@Composable
fun TopChrome(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(NotraColors.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        content = content)
}

@Composable
fun BottomChrome(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
        content()
    }
}
