package com.notra.app.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

@Serializable
enum class BlockType { PARAGRAPH, HEADING_1, HEADING_2, HEADING_3, BULLET_ITEM, NUMBERED_ITEM, CHECKLIST_ITEM, QUOTE, CODE, DIVIDER }

@Serializable
enum class MarkType { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, HIGHLIGHT }

@Serializable
data class TextMark(val type: MarkType, val start: Int, val end: Int)

@Serializable
data class DocumentBlock(
    val id: String,
    val type: BlockType,
    val text: String = "",
    val checked: Boolean = false,
    val marks: List<TextMark> = emptyList()
)

@Serializable
data class NoteDocumentV1(val schemaVersion: Int = 1, val blocks: List<DocumentBlock>) {
    companion object {
        fun empty(): NoteDocumentV1 = NoteDocumentV1(blocks = listOf(DocumentBlock(UUID.randomUUID().toString(), BlockType.PARAGRAPH)))
    }
}

sealed interface DocumentRead {
    data class Valid(val document: NoteDocumentV1) : DocumentRead
    data class Unsupported(val version: Int) : DocumentRead
    data class Invalid(val reason: String) : DocumentRead
}

/** Portable V1 contract. All offsets are UTF-16 String offsets. */
object DocumentCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun decode(columnVersion: Int, payload: String): DocumentRead {
        if (columnVersion == 0 && payload.isBlank()) return DocumentRead.Valid(NoteDocumentV1.empty())
        if (columnVersion > 1) return DocumentRead.Unsupported(columnVersion)
        if (columnVersion != 1) return DocumentRead.Invalid("Unsupported legacy document")
        return try {
            val root = json.parseToJsonElement(payload).jsonObject
            val embeddedVersion = root["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return DocumentRead.Invalid("Missing document version")
            if (embeddedVersion > 1) return DocumentRead.Unsupported(embeddedVersion)
            if (embeddedVersion != 1) return DocumentRead.Invalid("Invalid document version")
            DocumentRead.Valid(canonical(json.decodeFromString<NoteDocumentV1>(payload)))
        } catch (_: IllegalArgumentException) {
            DocumentRead.Invalid("Invalid document content")
        } catch (_: SerializationException) {
            DocumentRead.Invalid("Invalid document JSON")
        }
    }

    fun encode(document: NoteDocumentV1): String = json.encodeToString(canonical(document))

    fun canonical(document: NoteDocumentV1): NoteDocumentV1 {
        require(document.schemaVersion == 1 && document.blocks.isNotEmpty())
        require(document.blocks.map { it.id }.distinct().size == document.blocks.size)
        return document.copy(blocks = document.blocks.map { block ->
            require(UUID.fromString(block.id).toString() == block.id)
            if (block.type == BlockType.DIVIDER) require(block.text.isEmpty() && block.marks.isEmpty())
            block.copy(checked = block.type == BlockType.CHECKLIST_ITEM && block.checked, marks = canonicalMarks(block.text, block.marks))
        })
    }

    fun canonicalMarks(text: String, marks: List<TextMark>): List<TextMark> =
        marks.onEach { require(it.start >= 0 && it.end > it.start && it.end <= text.length) }
                .groupBy { it.type }
                .flatMap { (type, ranges) ->
                    val merged = mutableListOf<TextMark>()
                    ranges.sortedWith(compareBy<TextMark> { it.start }.thenBy { it.end }).forEach { mark ->
                        val last = merged.lastOrNull()
                        if (last != null && mark.start <= last.end) merged[merged.lastIndex] = last.copy(end = maxOf(last.end, mark.end))
                        else merged.add(mark.copy(type = type))
                    }
                    merged
                }.sortedWith(compareBy<TextMark> { it.start }.thenBy { it.end }.thenBy { it.type.ordinal })

    fun plainText(document: NoteDocumentV1): String {
        val lines = mutableListOf<String>()
        var number = 0
        document.blocks.forEach { block ->
            if (block.type == BlockType.NUMBERED_ITEM) number++ else number = 0
            lines += when (block.type) {
                BlockType.BULLET_ITEM -> "• ${block.text}"
                BlockType.NUMBERED_ITEM -> "$number. ${block.text}"
                BlockType.CHECKLIST_ITEM -> "${if (block.checked) "[x]" else "[ ]"} ${block.text}"
                BlockType.DIVIDER -> "---"
                else -> block.text
            }
        }
        return lines.joinToString("\n")
    }
}
