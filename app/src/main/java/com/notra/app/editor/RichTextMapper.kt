package com.notra.app.editor

import androidx.compose.foundation.text.input.ExpandPolicy
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.notra.app.document.DocumentBlock
import com.notra.app.document.DocumentCodec
import com.notra.app.document.MarkType
import com.notra.app.document.TextMark

/** The only bridge between Compose's tracked ranges and portable UTF-16 document marks. */
object RichTextMapper {
    private val highlight = Color(0x665E6B9A)
    private fun style(type: MarkType): SpanStyle = when (type) {
        MarkType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        MarkType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        MarkType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MarkType.HIGHLIGHT -> SpanStyle(background = highlight)
    }

    private fun type(style: SpanStyle): MarkType? = when {
        style.fontWeight == FontWeight.Bold -> MarkType.BOLD
        style.fontStyle == FontStyle.Italic -> MarkType.ITALIC
        style.textDecoration == TextDecoration.Underline -> MarkType.UNDERLINE
        style.textDecoration == TextDecoration.LineThrough -> MarkType.STRIKETHROUGH
        style.background == highlight -> MarkType.HIGHLIGHT
        else -> null
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun state(block: DocumentBlock): TextFieldState = TextFieldState(block.text).also { state ->
        state.edit {
            block.marks.forEach { addStyle(style(it.type), TextRange(it.start, it.end), ExpandPolicy.AtEnd) }
        }
        state.undoState.clearHistory()
    }

    fun marks(state: TextFieldState): List<TextMark> {
        val text = state.text.toString()
        if (text.isEmpty()) return emptyList()
        val spans = state.textStyles.getSpanStyles(TextRange(0, text.length))
        return DocumentCodec.canonicalMarks(text, spans.mapNotNull { range ->
            val markType = type(range.item) ?: return@mapNotNull null
            if (range.end <= range.start) null else TextMark(markType, range.start, range.end)
        })
    }

    fun selectionHas(state: TextFieldState, type: MarkType): Boolean {
        val range = state.selection
        if (range.collapsed) return false
        var cursor = range.min
        marks(state).filter { it.type == type && it.end > range.min && it.start < range.max }
            .sortedBy { it.start }.forEach { if (it.start <= cursor) cursor = maxOf(cursor, it.end) }
        return cursor >= range.max
    }

    fun toggle(state: TextFieldState, type: MarkType): Boolean {
        val selection = state.selection
        if (selection.collapsed) return false
        val old = marks(state)
        val enabled = selectionHas(state, type)
        val next = if (enabled) old.flatMap { mark ->
            if (mark.type != type || mark.end <= selection.min || mark.start >= selection.max) listOf(mark)
            else buildList {
                if (mark.start < selection.min) add(mark.copy(end = selection.min))
                if (mark.end > selection.max) add(mark.copy(start = selection.max))
            }
        } else old + TextMark(type, selection.min, selection.max)
        applyMarks(state, next)
        return true
    }

    fun applyMarks(state: TextFieldState, marks: List<TextMark>) {
        val selection = state.selection
        state.edit {
            getSpanStyles(TextRange(0, length)).forEach { removeStyle(it) }
            DocumentCodec.canonicalMarks(asCharSequence().toString(), marks).forEach { addStyle(style(it.type), TextRange(it.start, it.end), ExpandPolicy.AtEnd) }
            this.selection = selection
        }
    }
}

/** Small style history beside Compose's text undo; style edits start a new text-undo segment. */
@OptIn(ExperimentalFoundationApi::class)
class RichTextUndoController(private val state: TextFieldState) {
    private data class StyleAction(val text: String, val before: List<TextMark>, val after: List<TextMark>)
    private val styleUndo = ArrayDeque<StyleAction>()
    private val styleRedo = ArrayDeque<StyleAction>()

    fun toggle(type: MarkType): Boolean {
        val before = RichTextMapper.marks(state)
        if (!RichTextMapper.toggle(state, type)) return false
        val after = RichTextMapper.marks(state)
        state.undoState.clearHistory()
        styleUndo.addLast(StyleAction(state.text.toString(), before, after))
        styleRedo.clear()
        return true
    }

    fun undo() {
        if (state.undoState.canUndo) { state.undoState.undo(); return }
        val action = styleUndo.lastOrNull()?.takeIf { it.text == state.text.toString() } ?: return
        styleUndo.removeLast()
        RichTextMapper.applyMarks(state, action.before)
        state.undoState.clearHistory()
        styleRedo.addLast(action)
    }

    fun redo() {
        if (state.undoState.canRedo) { state.undoState.redo(); return }
        val action = styleRedo.lastOrNull()?.takeIf { it.text == state.text.toString() } ?: return
        styleRedo.removeLast()
        RichTextMapper.applyMarks(state, action.after)
        state.undoState.clearHistory()
        styleUndo.addLast(action)
    }

    val canUndo: Boolean get() = state.undoState.canUndo || styleUndo.lastOrNull()?.text == state.text.toString()
    val canRedo: Boolean get() = state.undoState.canRedo || styleRedo.lastOrNull()?.text == state.text.toString()
}
