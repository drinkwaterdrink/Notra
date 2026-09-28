# Notra — Editor Document Format

**Format version:** 1  
**Applies starting:** I-002  
**Purpose:** Stable, portable, Compose-independent representation of editable note content.

## 1. Separation of Concerns

The persisted document format is a product/data contract.

It must not serialize:
- `TextFieldState`
- `SpanStyle`
- `ParagraphStyle`
- `TrackedRange`
- Compose UI classes
- ViewModel/UI state

Compose text objects are editor-session state only.

The Room `NoteEntity.documentPayload` stores a serialized portable document. `documentSchemaVersion` tells the app which document decoder/migrator to use.

Room schema version and document schema version are independent. Changing how a note document is encoded does not by itself require a Room migration.

## 2. NoteDocumentV1

Conceptual JSON contract:

```json
{
  "schemaVersion": 1,
  "blocks": [
    {
      "id": "stable-uuid",
      "type": "PARAGRAPH",
      "text": "Example text",
      "checked": false,
      "marks": [
        {
          "type": "BOLD",
          "start": 0,
          "end": 7
        }
      ]
    }
  ]
}
```

The exact Kotlin DTO names may differ, but the persisted semantics must remain equivalent.

### Document fields

- `schemaVersion`: integer. Must be `1` for this format.
- `blocks`: ordered list. List order is document order.

### Block fields

- `id`: stable UUID string. It survives edits and saves.
- `type`: one of the V1 block types below.
- `text`: textual content. Divider uses an empty string.
- `checked`: meaningful only for checklist items; false otherwise or omitted by the serializer.
- `marks`: zero or more inline formatting ranges.

## 3. V1 Block Types

V1 supports:

- `PARAGRAPH`
- `HEADING_1`
- `HEADING_2`
- `HEADING_3`
- `BULLET_ITEM`
- `NUMBERED_ITEM`
- `CHECKLIST_ITEM`
- `QUOTE`
- `CODE`
- `DIVIDER`

Consecutive bullet/numbered items are rendered as a visual list run.

Numbering is derived from ordered consecutive `NUMBERED_ITEM` blocks rather than persisted as visible numbers.

Nested list depth is not part of V1.

Attachments, URLs/internal note links, tables, drawings, and AI annotations are later schema additions and must not be faked into V1.

## 4. V1 Inline Marks

Supported inline mark types:

- `BOLD`
- `ITALIC`
- `UNDERLINE`
- `STRIKETHROUGH`
- `HIGHLIGHT`

Each mark stores:
- `type`
- `start`: inclusive text offset
- `end`: exclusive text offset

Offsets use Kotlin/Compose string indexing semantics and must be validated against the block's current text length.

Rules:
- zero-length marks are omitted;
- invalid/out-of-bounds ranges are rejected or normalized before persistence;
- different mark types may overlap;
- overlapping/adjacent ranges of the same type should be canonicalized when practical;
- marks must remain deterministic across serialize → deserialize → serialize round trips.

Custom highlight colors are not part of V1; use the Notra semantic highlight token.

## 5. Runtime Editor Mapping

Each textual block may own a Compose `TextFieldState` while the editor is open.

Compose 1.12 `TextFieldTextStyles`, `TrackedRange`, and `TextFieldState.edit` should be used where they simplify inline rich-text editing.

The editor must explicitly map:

**Persisted block text + marks → runtime TextFieldState + styles**

and:

**Runtime TextFieldState + styles → persisted block text + marks**

That mapping must have unit tests.

A persisted note must never depend on Compose implementation details to decode.

## 6. Block Editing Semantics

Default new document:
- one empty `PARAGRAPH` block.

Title remains a separate Note field, not a document block.

Expected behavior:

### Paragraph
IME Next/Enter creates/splits to another paragraph where the editor implementation can reliably support it.

### Heading
Creating the next block after a heading defaults to `PARAGRAPH`.

### Bullet / Numbered / Checklist
Creating the next item continues the same block type.

Leaving an empty list/checklist item converts/exits to `PARAGRAPH` rather than creating endless empty list items.

### Quote
Next non-empty block may continue quote; an empty quote exits to paragraph.

### Code
Code blocks may contain hard newlines. Do not apply proportional heading/list formatting inside code.

### Divider
Divider contains no editable text and is skipped by normal text focus traversal.

Where soft-keyboard behavior differs from hardware-keyboard behavior, prioritize reliable Android IME behavior and cover both with tests where feasible.

## 7. Legacy / Future Handling

### documentSchemaVersion = 0

I-001 created the column before an editor existed.

Treat version 0 with empty/blank payload as a legacy empty document and migrate in memory to V1 with one empty paragraph.

Do not rewrite the database merely by observing it. Persist V1 when the note is actually saved.

### Future version > supported version

Do not attempt lossy decoding.

Do not overwrite the payload.

Surface a controlled unsupported-document state.

### Invalid/corrupt V1 payload

Do not replace corrupt raw data with an empty document automatically.

Return a decoding failure to the editor/repository layer and preserve the original payload for recovery.

## 8. Serialization Requirements

Use a stable explicit serializer such as `kotlinx.serialization` rather than ad-hoc string concatenation.

Requirements:
- deterministic round-trip behavior;
- explicit schema version;
- safe failure path;
- no reflection-dependent persistence format;
- no UI classes in JSON;
- tests for special characters, emoji/surrogate pairs, multiline code, overlapping different mark types, empty blocks, and checklist state.

## 9. Plain Text Derivation

Provide a pure function that derives readable plain text from `NoteDocumentV1`.

This is useful for:
- Library previews now;
- FTS/search later;
- exports later;
- AI context later.

Do not add a new Room column solely for this during I-002. I-006 can decide how derived text participates in FTS.

## 10. Forward Compatibility Principle

When later packets add attachments, note links, or other structured content:

1. do not reinterpret an existing V1 field to mean something new;
2. add a new document schema version when persistence semantics change;
3. provide deterministic migration from older supported versions;
4. keep block IDs stable whenever the logical block survives migration.
