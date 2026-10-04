# I-003 — Library Organization

## Objective

Turn Library from a temporary active-note list into Notra's deterministic structured organization system while preserving the accepted editor, physical-device shell, and future Board independence.

This packet implements the core of:
- F-005 Library & Folder Hierarchy
- F-006 Tags
- the Library-facing subset of F-012 Pins, Favorites, Archive, Trash & Sort

It introduces Room schema v2 through an explicit tested migration.

---

# Mandatory orientation

Before editing, pull the latest `main` and read in full:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DESIGN_REFERENCE.md`
6. `docs/EDITOR_DOCUMENT_FORMAT.md`
7. `docs/LIBRARY_ORGANIZATION_CONTRACT.md`
8. this packet

Inspect the current repository before changing code.

I-001 through I-002P are accepted baselines. Do not regress their behavior.

---

# Scope

## Required product behavior

Implement:
- nested folders;
- root Library and Folder View;
- Unfiled;
- tags and Note↔Tag many-to-many relations;
- list/grid Library presentation;
- pin;
- favorite;
- sort;
- tag/pin/favorite filters;
- multi-select for notes;
- move note(s) between folders / Unfiled;
- archive / unarchive;
- Trash;
- restore from Trash;
- permanent delete from Trash;
- create/rename/move/delete Folder;
- safe folder-cycle prevention;
- richer Library note previews using the existing V1 plain-text derivation;
- persisted Library view/sort preference where specified in the contract.

Follow `docs/LIBRARY_ORGANIZATION_CONTRACT.md` exactly for lifecycle and deletion semantics.

## Explicitly out of scope

Do not implement:
- Board pan/zoom/drag/placement;
- Board cards or BoardItem persistence;
- Stacks;
- FTS/full-text Search;
- Smart Collections engine;
- attachments/PDFs;
- note locking;
- internal links/backlinks;
- revision-history UI;
- AI;
- sync;
- calendar/reminders;
- final customization system.

Do not fake later functionality with dead controls.

---

# Database / migration

## Room v2

I-003 is the first real Room migration.

Create and test explicit Migration 1 → 2.

Do **not** use `fallbackToDestructiveMigration` or delete/recreate the database.

Keep the committed v1 schema. Commit a v2 exported schema.

Existing v1 notes must migrate with:
- `folderId = null`
- `archivedAt = null`
- `pinned = false`
- `favorite = false`

Preserve:
- note ID;
- title;
- document payload;
- document schema version;
- createdAt;
- updatedAt;
- deletedAt;
- revision.

## Minimum new persistence

### Note
Extend with:
- nullable folder ID;
- nullable archivedAt;
- pinned Boolean;
- favorite Boolean.

Avoid rebuilding the notes table unless genuinely required. Prefer additive migration where it preserves integrity safely.

### Folder
Persist at least:
- id;
- name;
- parentFolderId?;
- sortOrder;
- createdAt;
- updatedAt.

Appearance metadata may be omitted until customization if it has no current UI job.

### Tag
Persist at least:
- id;
- display name;
- normalized name used for uniqueness;
- createdAt;
- updatedAt.

### NoteTag
Many-to-many relation with uniqueness on noteId + tagId.

Use appropriate indexes for folder membership, lifecycle queries and tag joins without inventing speculative indexes.

---

# Data integrity rules

## Folder ancestry

Prevent cycles both in domain/repository logic and with tests.

Reject:
- folder → itself;
- folder → descendant.

## Folder deletion

Implement the contract's **dissolve** semantics transactionally:
- direct notes move to parent / Unfiled;
- direct child folders move to parent / root;
- delete only the target folder;
- preserve note archive/trash/pin/favorite/tag state.

Deleting a folder must never delete notes.

## Tag deletion

Deleting a tag removes relationships only.

## Organization mutations

Note organization changes must not use blind whole-row replacement.

Organization mutations should increment note revision and avoid stale editor/library overwrites.

Audit all current editor `save()` behavior against the new revision semantics.

If an editor session encounters a revision conflict caused by Library metadata changes, it must not silently erase those changes. Use reload/retry/conflict handling consistent with the existing truthful-save design.

---

# Repository / state architecture

Keep architecture focused.

Expected separation:

### Library repository/domain boundary
Owns:
- folders;
- tag CRUD/relations;
- Library queries;
- move/archive/trash/pin/favorite mutations;
- folder structural transactions;
- lifecycle restore/permanent-delete operations.

### Library ViewModel/state holder
Owns:
- current physical folder or virtual Library mode;
- breadcrumbs;
- list/grid mode;
- sort;
- filters;
- selection state;
- dialog/sheet state;
- exposed folder/note/tag UI models;
- async mutation/error state.

### UI
Render state + emit intents. Do not call Room directly.

Do not move unrelated editor logic into the Library ViewModel.

---

# Library navigation model

Library has two kinds of locations:

## Physical
- Root
- Folder(folderId)

## Virtual
- Unfiled
- Favorites
- Archive
- Trash

Virtual views are not Folder rows and cannot become folder parents.

Folder navigation should remain inside the Library destination so the four-destination app dock remains conceptually stable.

Use compact breadcrumbs/location chrome.

System Back while inside nested Library hierarchy should go up one Library level before leaving/switching destination where consistent with the current shell architecture.

Do not build a second competing global navigation system.

---

# Library UI

Preserve I-002P's compact shell and inset strategy.

## Root / Folder View

Show:
- compact current location/breadcrumb;
- child folders;
- notes belonging directly to current location;
- compact organizational controls;
- New Note / New Folder access without a giant hero action.

Folders and notes may share one content region but must be visually distinguishable.

Avoid a generic desktop-file-manager aesthetic.

## Note preview

Use V1 plain-text derivation.

Show useful hierarchy such as:
- title / Untitled fallback;
- short body preview;
- modified time;
- restrained pin/favorite/tag indicators when relevant.

Do not display raw JSON or implementation metadata.

## Folder row/card

Show:
- name;
- restrained identity;
- useful child/note count if it is cheap and correct;
- contextual actions progressively disclosed.

Do not permanently show rename/delete/move buttons on every folder.

## List / Grid

Both must be product-quality enough to test.

Persist preference through DataStore.

Grid cards should follow Compact Premium density—not oversized dashboard cards.

## Utility / virtual views

Provide compact access to:
- Unfiled;
- Favorites;
- Archive;
- Trash.

Do not pretend these are physical folders.

---

# Create behavior

## New Note

- Board/global capture → Unfiled.
- Library root → Unfiled.
- inside physical Folder → create assigned to that Folder.
- Unfiled → create Unfiled.
- Favorites/Archive/Trash → New Note should not inherit those virtual states; either create Unfiled or hide the action if that is clearer. Prefer create Unfiled if global Quick Capture remains visible.

After create, open Editor as today.

## New Folder

Available at Library root and inside physical Folder.

Creates at current physical location.

Folder name validation:
- trim outer whitespace;
- disallow blank;
- reject visually duplicate case-insensitive sibling names;
- do not silently rename with suffixes.

---

# Note actions

Single-note contextual actions:
- Open;
- Move;
- Tags;
- Pin/Unpin;
- Favorite/Unfavorite;
- Archive/Unarchive where applicable;
- Trash;
- Restore in Trash;
- Permanent Delete in Trash with confirmation.

Do not put every action permanently on the row/card.

---

# Multi-select

Long-press a note to enter selection mode.

While selection is active:
- tapping notes toggles selection;
- provide clear selected count;
- Back exits selection before navigating away;
- relevant bulk actions are available;
- folder navigation should not accidentally remain in ambiguous selection state.

Minimum bulk actions:
- Move;
- Tags;
- Pin/Unpin;
- Favorite/Unfavorite;
- Archive/Unarchive where valid;
- Trash;
- Restore in Trash;
- Permanent Delete in Trash with confirmation.

I-003 bulk selection is note-focused. Do not implement complicated multi-folder bulk editing.

---

# Tags

Provide a compact usable tag-management path.

At minimum:
- create tag;
- rename tag;
- delete tag;
- assign/remove tags to one or selected notes;
- filter Library by tag.

Avoid a huge standalone tag-management screen if a sheet/dialog pattern is sufficient.

Tag deletion confirmation should state that notes are preserved.

---

# Sort / filter

Minimum sort:
- Updated newest;
- Updated oldest;
- Created newest;
- Created oldest;
- Title A–Z;
- Title Z–A.

Pinned-first is an independent toggle where applicable.

Minimum filters:
- tag;
- pinned;
- favorite.

Persist stable view/sort preferences. Transient ad-hoc filters do not need persistence unless it naturally improves the implementation.

No text query box/FTS in this packet.

---

# Archive / Trash

Follow the Library contract exactly.

### Archive
- archive removes from normal Folder/Unfiled views;
- folder/tags/pin/favorite preserved;
- unarchive restores to same structural location.

### Trash
- trash preserves archivedAt and all organization metadata;
- Trash hides item from Active and Archive;
- restore clears only deletedAt;
- an archived note restored from Trash returns to Archive;
- an active note restored from Trash returns to active Library location.

### Permanent delete
Only in Trash.

Confirm explicitly.

Delete:
- note row;
- NoteTag relations;
- recovery draft if present.

Do not attempt attachment deletion because attachments are I-007 and do not exist yet.

---

# Editor integration

Do not redesign the editor.

The editor must remain able to:
- open notes regardless of folder/tags/pin/favorite;
- autosave content;
- safely react if Library organization changed the note revision while editor was open.

Do not add large metadata chrome to the editor during I-003.

If a compact More-menu entry for Library organization is genuinely required for usability, keep it narrow and reuse the Library action UI rather than duplicating logic.

---

# Board isolation gate

I-003 must not create Board placement state.

Test/verify conceptually that:
- moving Folder does not touch future Board fields;
- pin/favorite/tags do not imply Board movement;
- archive/trash do not invent Board behavior before I-004 defines it.

The data model should remain compatible with later BoardItem references to the same stable Note IDs.

---

# Migration verification

Use Room migration testing facilities.

Required migration test:
1. create a real v1 database using the committed v1 schema;
2. seed at least:
   - active note with rich V1 document;
   - soft-deleted note;
3. migrate 1 → 2;
4. verify original IDs/content/timestamps/revision/deletedAt preserved;
5. verify new organization defaults;
6. reopen with Room v2 successfully.

Do not claim migration safety from a freshly created v2 database alone.

---

# Functional tests

At minimum cover:

## Folders
- create root folder;
- create nested folder;
- rename;
- move folder;
- reject self-cycle;
- reject descendant-cycle;
- delete folder dissolves correctly;
- deleting folder preserves notes.

## Notes / folder movement
- move one note;
- bulk move;
- move to Unfiled;
- create note inside Folder assigns folder;
- Board/global create remains Unfiled.

## Tags
- create/rename/delete;
- assign many tags to note;
- one tag on many notes;
- delete tag preserves notes;
- filter by tag.

## Pin / favorite
- independent states;
- persist/reopen;
- pinned-first ordering works without changing favorite;
- Favorites view contains only favorite eligible notes.

## Lifecycle
- archive/unarchive preserves folder/tags/pin/favorite;
- trash active note → restore returns active;
- trash archived note → restore returns Archive;
- permanent delete removes note/relations and recovery draft;
- Trash does not leak into Active/Archive.

## Sorting
- all required sort modes deterministic;
- list/grid switch does not mutate data;
- preference survives process recreation where practical.

## Selection
- long-press selection;
- multi-select actions;
- Back exits selection;
- destructive bulk action confirmation.

## Existing regressions
Re-run editor/autosave/recovery/inset tests from previous packets.

---

# Accessibility / physical-device gates

Keep all I-002P safe-inset guarantees.

Required:
- interactive targets meet practical touch-target expectations;
- folder/note actions have semantics/content descriptions where icons alone are used;
- selection state is exposed to accessibility;
- list/grid remains usable at 130% font scale;
- no bottom sheet/menu/action bar overlaps Samsung 3-button navigation or IME;
- folder hierarchy remains understandable without color alone.

---

# Verification commands

Run actual verification:

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebugAndroidTest`
- `:app:connectedDebugAndroidTest` when emulator/device is available
- migration-specific tests
- `git diff --check`

Also verify:
- v1 schema file unchanged;
- v2 schema exported;
- `EDITOR_DOCUMENT_FORMAT.md` unchanged;
- NoteDocument V1 unchanged unless an unrelated defect is found and explicitly reported;
- no I-004+ functionality added.

Build a debug APK suitable for another Samsung review after implementation.

---

# Acceptance criteria

I-003 is complete only if all are true:

1. Existing v1 installs migrate to v2 without content loss.
2. User can create and navigate nested folders.
3. Folder cycles cannot be created.
4. Deleting a Folder never deletes notes and follows dissolve semantics.
5. User can move notes between folders and Unfiled.
6. Tags are many-to-many and usable for organization/filtering.
7. Pin and Favorite are independent and persist.
8. List/Grid both work and preference persists.
9. Required sort/filter controls work deterministically.
10. Multi-select supports the required note actions.
11. Archive and Trash semantics match the contract.
12. Restoring an archived-then-trashed note returns it to Archive.
13. Permanent delete is Trash-only and confirmed.
14. Editor/autosave/recovery behavior remains truthful under revision conflicts.
15. Library organization does not introduce Board placement behavior.
16. I-002P inset/accessibility behavior remains green.
17. Physical-device build is produced for review.

---

# Project record

Update `docs/IMPLEMENTATION_STATUS.md` with:
- Room v2 migration design;
- new entities/relations;
- Library navigation model;
- folder deletion semantics;
- archive/trash semantics;
- view/sort preference behavior;
- tests actually run/results;
- migration test result;
- known limitations;
- blueprint deviations;
- I-004 readiness.

Do not change LOCKED decisions silently.

---

# Final report

Provide:

### What changed
### Room v1 → v2 migration
### Library/folder behavior
### Tags
### Pin/favorite/sort/filter behavior
### Archive/trash/permanent-delete behavior
### Multi-select behavior
### Important files
### Tests/checks actually run
### Migration preservation result
### I-003 acceptance criteria — PASS / FAIL / NOT VERIFIED
### Known limitations / deviations
### APK path / physical-device readiness
### I-004 readiness

Keep changes uncommitted when finished so they can be independently audited before merge.

Do not begin I-004.
