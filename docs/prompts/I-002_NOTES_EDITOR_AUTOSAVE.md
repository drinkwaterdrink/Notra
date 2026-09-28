# I-002 — Notes Editor & Autosave

## Objective

Turn the I-001 foundation into a genuinely usable local notes application by implementing:

- versioned structured note documents;
- create/open/edit/soft-delete flows;
- a rich text/block editor;
- autosave;
- recovery of unsaved work;
- editor-local undo/redo;
- a minimal way to reopen existing notes.

This packet must establish a trustworthy document model without implementing Library organization, the spatial Board engine, attachments, search, locks, AI, or sync.

---

# Mandatory orientation

Before editing, read in full:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DESIGN_REFERENCE.md`
6. `docs/EDITOR_DOCUMENT_FORMAT.md`
7. this packet

Inspect current repository state and commit history before making changes.

I-001 and I-001A are accepted baselines.

---

# Current technical baseline

Preserve unless a verified requirement forces a change:

- minSdk 26
- targetSdk 36
- compileSdk 37
- stable Compose 1.12.1 line
- Room 2.8.5
- DataStore 1.2.1
- single Android app module
- local-first architecture

Official Compose guidance recommends state-based text fields. `TextFieldState` can be hoisted into a ViewModel, exposes `UndoState`, and Compose 1.12 exposes tracked rich-text styles through `TextFieldTextStyles` / `TrackedRange`.

Use those capabilities rather than recreating a text engine unnecessarily.

---

# Scope

Implement F-001 + F-009 core behavior required for I-002.

## In scope

### Note lifecycle
- create note;
- open note by stable UUID;
- edit title;
- edit structured document;
- reopen persisted notes;
- soft-delete note using existing lifecycle metadata;
- autosave;
- explicit save-error/recovery behavior.

### Document V1
Implement exactly the semantics in `docs/EDITOR_DOCUMENT_FORMAT.md`.

### Block types
- Paragraph
- Heading 1
- Heading 2
- Heading 3
- Bullet item
- Numbered item
- Checklist item
- Quote
- Code
- Divider

### Inline formatting
- Bold
- Italic
- Underline
- Strikethrough
- Highlight

### Basic access
A user must have a minimal product-quality path to:
- create a note from the shell;
- reopen an existing note from a basic active-note list;
- return from editor safely.

The minimal note list is an I-002 access surface, not the full I-003 Library.

## Explicitly out of scope

Do not implement:
- folders;
- tags;
- nested Library hierarchy;
- Smart Collections;
- bulk Library management;
- Board pan/zoom/drag;
- Board card placement;
- resizing;
- Stacks;
- image/file/PDF import;
- URL/internal note links;
- note locking/encryption;
- FTS/semantic search;
- AI;
- cloud/PC sync;
- reminders/calendar;
- final design polish.

---

# Architectural decisions

## 1. Persistence format is independent from Compose

Do not serialize Compose text/state/style classes.

Use the V1 document contract.

A note's persisted fields remain:
- title in the existing Note column;
- body in `documentPayload`;
- document format selected by `documentSchemaVersion`.

Prefer keeping Room schema version 1 unchanged during I-002.

The existing database already has the fields needed for this packet. Do not create a Room migration merely to reorganize code.

If you discover a truly unavoidable schema change, stop and report why before making it.

## 2. Serialization

Use a maintained explicit serializer; `kotlinx.serialization` is the preferred fit unless repository/toolchain verification reveals a concrete incompatibility.

If adding serialization:
- use stable releases/plugins compatible with the current Kotlin/AGP baseline;
- keep DTOs/domain format Compose-independent;
- use deterministic decoding/encoding;
- fail safely on corrupt or future documents.

## 3. Navigation

I-002 introduces a real editor route, so navigation now has a concrete job.

Prefer the current stable Compose-first Android navigation solution. Navigation3 is acceptable and currently stable; verify the exact stable release before adding it.

Required conceptual routes:
- Shell
- Editor(noteId)

Keep Board/Library/Search/Settings as the shell's bottom destinations rather than creating four unrelated top-level editor back stacks.

Back from Editor must go through the editor's save/flush policy before popping.

Do not build a custom navigation framework.

## 4. Editor ownership

Create dedicated editor files/state boundaries.

Do not continue growing `MainActivity.kt` into the editor implementation.

Expected responsibilities:

### Editor ViewModel / state holder
Owns:
- loaded note identity;
- title state;
- ordered editor block states;
- current save state;
- active/focused block;
- document serialization/deserialization;
- structural editor operations;
- autosave coordination;
- recovery state.

### Composables
Render state and send intent/events.

Leaf composables must not directly call Room.

### Repository
Owns durable Note persistence operations.

---

# Runtime rich-text model

Use one state-based Compose text state per editable text block unless a better implementation is proven during repository work.

Map persisted mark ranges into Compose 1.12 tracked styles.

Formatting controls operate on the current selection/current text block.

Formatting behavior should match conventional editors:
- selected text toggles the requested mark;
- toolbar selected state reflects selection formatting where practical;
- typing after applying a style should behave predictably rather than immediately dropping the style;
- unrelated marks may overlap.

Do not use visual-only formatting that disappears after reopening the note.

---

# Block interaction behavior

Implement reliable Android behavior rather than clever fragile behavior.

At minimum:

- New note begins with one Paragraph.
- Block IDs survive ordinary edits and saves.
- Block type can be changed from a compact block-style control.
- Divider can be inserted.
- Checklist can be checked/unchecked without opening another screen.
- Consecutive numbered items display coherent numbering.
- Code blocks use a code-appropriate text treatment and allow hard newlines.
- Empty trailing blocks do not proliferate uncontrollably.

Where practical:
- heading → next block defaults to Paragraph;
- list/checklist → next item continues same type;
- empty list/checklist item exits to Paragraph;
- backspace at the start of an empty block removes/merges sensibly.

Do not compromise text correctness to implement a sophisticated Enter/backspace rule. If Android IME behavior prevents a rule from being reliable, implement the simpler deterministic behavior, document it, and add it to I-010 polish notes.

---

# Editor UI

The editor should already feel recognizably Notra, while final visual refinement remains I-010.

Direction:
- dark graphite app chrome;
- quiet focused writing surface;
- compact premium density;
- crisp type hierarchy;
- restrained borders/depth;
- no giant decorative title;
- no generic colorful Material starter look.

Required UI:

## Top bar
- Back
- save/error state only when useful
- Undo
- Redo
- More

## Title
- separate single-line editable title;
- empty title stored as empty;
- UI fallback displays `Untitled` where a label is required.

## Body
- ordered blocks;
- clear focus;
- efficient vertical scrolling;
- no card-around-every-paragraph aesthetic.

## Formatting / insertion
A compact toolbar reachable while typing.

At minimum:
- block style/type
- Bold
- Italic
- Underline
- Strikethrough
- Highlight
- Checklist/list controls
- insert Divider

Do not add attachment/link actions that pretend later features exist.

---

# Minimal note access outside Editor

I-002 must be usable after completion.

## Create
Provide a restrained New Note / Quick Capture action from the shell, preferably Board and Library.

Creating a note:
1. creates a durable Note row immediately with a stable UUID;
2. initializes a valid empty V1 document;
3. opens Editor for that ID.

## Reopen
The Library shell may temporarily show a simple active-note list:
- title or Untitled fallback;
- compact plain-text preview;
- updated recency;
- tap → Editor.

This is not permission to implement Folders, tags, sorting UI, grid modes, or I-003 scope.

## Delete
Editor More → Delete may soft-delete by setting `deletedAt`.

The full Trash UI belongs to I-003.

After soft-delete, return safely to shell and remove the note from the active list.

---

# Autosave contract

Autosave must be reliable and must never lie.

## Save state

Represent at least:
- Saved
- Pending/Dirty
- Saving
- Error

Normal Saved state may remain visually quiet.

A save error must be visible and actionable.

## Trigger model

Use:
1. a short idle debounce after edits;
2. a flush when leaving the editor;
3. a flush on appropriate lifecycle stop/background transition;
4. immediate persistence for destructive/structural state where delaying it would create surprising behavior.

Use a named autosave policy/constant rather than scattered delays.

Do not write Room synchronously on the main thread.

## Concurrency

Prevent stale snapshots from overwriting newer editor state.

A valid approach:
- monotonically increasing editor generation;
- serialized saves;
- save captures a generation;
- editor is considered Saved only when durable generation matches latest local generation;
- if newer edits arrive during a save, newest state remains Pending and gets saved afterward.

Equivalent robust implementations are acceptable.

## Exit behavior

Back navigation:
- flush latest state before popping;
- if flush succeeds, leave;
- if save fails, do not silently discard.

On failure offer clear choices such as:
- Retry
- Keep editing
- Leave without latest changes

Never imply success when latest state is not durable.

---

# Recovery journal

Implement a lightweight app-private recovery mechanism for editor changes that have not yet reached the durable Room snapshot.

Purpose: protect work during process death/crash/save failure without writing Room for every key event.

Recommended design:
- `filesDir/drafts/<noteId>.json` or equivalent;
- only latest snapshot matters;
- writes occur on IO;
- rapid edits are conflated/coalesced so they do not build an unbounded queue;
- write temp + atomic replace/rename where supported;
- draft stores enough information to restore title + document + base/durable revision/generation;
- successful durable save of the same/newer generation clears obsolete draft.

On editor open:
- load durable note;
- inspect valid recovery draft;
- if draft represents newer unsaved work, restore it;
- surface a subtle `Recovered unsaved changes` state/message.

Corrupt draft:
- must not destroy or overwrite the Room note;
- fail safely;
- log only non-sensitive diagnostic metadata.

Do not use DataStore for full note bodies.

If you find a materially simpler mechanism that provides equivalent crash/process-death guarantees, explain it before substituting it.

---

# Undo / redo

Use Compose's `TextFieldState.undoState` for rich-text editing where appropriate.

At minimum:
- Undo/Redo operate correctly for text and inline-style edits in the currently focused text block.
- New edits after Undo invalidate incompatible Redo history as expected.
- Undo/Redo must not corrupt persisted mark ranges.

Structural operations (block insert/delete/type changes) should have a reversible session behavior where practical.

Do not invent a huge custom document-history engine solely to unify every kind of action in this packet.

If structural undo remains separate from focused-text undo, document the limitation clearly for I-010 rather than pretending there is one perfect global history.

---

# Plain-text derivation

Implement the pure V1 plain-text derivation defined by the document-format spec.

Use it for the temporary Library preview.

Do not add FTS in I-002.

Do not add a new Room search column solely for this.

---

# Error / edge states

Implement and test relevant states:

- note ID not found;
- note soft-deleted while route is open;
- blank note;
- very long note;
- emoji/surrogate pairs with inline styles;
- corrupt document payload;
- unsupported future document schema;
- autosave failure;
- recovery draft newer than Room;
- corrupt recovery draft;
- app backgrounding while dirty;
- navigating back while save is pending;
- deletion while save is pending.

For corrupt/future document payload:
- do not overwrite;
- show safe read/recovery error state;
- allow back navigation;
- retain raw data.

---

# Testing requirements

## Pure/unit tests

Document format:
- empty V1 round trip;
- all block types;
- all mark types;
- overlapping different mark types;
- canonical range validation;
- emoji/surrogate-pair ranges;
- multiline code;
- checklist state;
- deterministic serialize/deserialize behavior;
- version 0 empty → V1 migration;
- unsupported future version rejected;
- corrupt payload rejected without replacement;
- plain-text derivation.

Editor/persistence:
- note creation produces stable UUID + valid V1 payload;
- title/body save together;
- revision advances appropriately;
- soft-delete hides note from active list;
- stale save cannot mark newer state Saved.

Recovery:
- newer draft restored;
- obsolete draft ignored/cleared;
- corrupt draft cannot replace durable note.

## Instrumented/UI tests

At minimum:
- create note → type → leave → reopen → text persists;
- title persists;
- navigate shell → editor → back correctly;
- Bold (or another mark) persists after reopen;
- checklist state persists;
- note survives force-stop/relaunch after save;
- soft-delete removes it from active basic list;
- autosave error state is exercised with a fake/failing repository if practical;
- recovered draft state is exercised.

Use fakes/test repositories where failure injection is needed; do not sabotage production storage.

## Existing regression checks

Re-run:
- I-001 shell navigation test;
- Room persistence;
- DataStore persistence.

Build/test/lint:
- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebugAndroidTest`
- `:app:connectedDebugAndroidTest` when emulator/device is available
- `git diff --check`

---

# Room schema gate

Before implementation:
- hash or otherwise capture the committed Room v1 schema.

After implementation:
- verify the Room v1 schema file remains unchanged if no database schema change was required.

If the Room schema changes unexpectedly, treat that as a defect.

Document-format changes inside `documentPayload` do not justify rewriting the exported Room v1 schema.

---

# Completion gate

I-002 is COMPLETE only when:

- user can create a note;
- user can reopen it;
- title/body persist;
- V1 rich formatting persists;
- supported blocks persist;
- basic checklist works;
- autosave state is truthful;
- back/background flush behavior is verified;
- recovery draft behavior is verified;
- delete is soft-delete;
- corrupt/future documents are preserved rather than overwritten;
- existing I-001/I-001A regressions remain green;
- no I-003+ feature scope was silently implemented.

---

# Project record

Update `docs/IMPLEMENTATION_STATUS.md`.

Record:
- commit/PR;
- files/architecture;
- exact document serialization choice;
- navigation choice/version;
- autosave policy;
- recovery mechanism;
- tests actually run/results;
- known editor limitations;
- blueprint deviations;
- next-packet notes.

Do not change LOCKED decisions without explicit instruction.

---

# Final report

Provide:

### What changed
### Document model implemented
### Editor behavior
### Autosave/recovery behavior
### Navigation/data changes
### Important files
### Tests/checks actually run
### I-002 acceptance criteria — PASS / FAIL / NOT VERIFIED
### Known limitations / deviations
### I-003 readiness

Do not start I-003.
