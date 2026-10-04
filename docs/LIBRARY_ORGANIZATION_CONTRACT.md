# Notra — Library Organization Contract

**Applies starting:** I-003  
**Purpose:** Durable semantics for folders, tags, Library views, note lifecycle organization, and the first Room schema migration.

## 1. Product role

Library is the deterministic structured view over the same notes shown spatially on Board.

Board organization and Library organization are independent:
- moving a note between folders never changes Board coordinates, dimensions, Stack membership, or future Board placement;
- arranging a note on Board never changes its folder or tags;
- a note can be Unfiled and still appear on Board;
- every non-permanently-deleted note must remain recoverable through Library/Search regardless of Board state.

## 2. Folder semantics

A note belongs to zero or one primary Folder.

`folderId == null` means **Unfiled**. Unfiled is a virtual Library view, not a physical Folder row.

Folders may be nested.

Folder fields conceptually include:
- stable UUID;
- name;
- nullable parentFolderId;
- sortOrder;
- optional appearance metadata reserved for later use;
- createdAt / updatedAt.

Rules:
- folder ancestry may never contain a cycle;
- a folder may not be moved into itself or any descendant;
- duplicate folder names are allowed in different parents;
- within one parent, case-insensitive duplicate names should be rejected or require explicit rename rather than silently creating visually indistinguishable siblings;
- moving a folder moves its subtree structurally but does not change contained note metadata beyond what folder ancestry implies.

### Delete folder

Deleting a Folder is intentionally **non-destructive to notes and child folders**.

Deleting folder F:
1. notes directly in F move to F's parent; if F is root-level they become Unfiled;
2. child folders of F move to F's parent; if F is root-level they become root-level folders;
3. F itself is removed;
4. note archive/trash/pin/favorite/tag state is unchanged.

Do not recursively delete a folder subtree as a side effect of deleting one folder.

## 3. Tags

Tags are many-to-many and independent of Folder, Board, Stack, Archive, and pin/favorite state.

Tag fields conceptually include:
- stable UUID;
- display name;
- normalized name used for uniqueness/matching;
- optional appearance metadata;
- createdAt / updatedAt.

Rules:
- one note may have many tags;
- one tag may belong to many notes;
- deleting a tag removes only tag relationships, never notes;
- tag rename preserves relationships;
- case/whitespace-equivalent duplicate tags should not be created silently;
- tag filtering may be implemented in I-003, but full-text search remains I-006.

## 4. Note organization fields

I-003 extends Note persistence with:
- `folderId: String?`
- `archivedAt: Long?`
- `pinned: Boolean`
- `favorite: Boolean`

Existing fields remain authoritative for content/lifecycle:
- `deletedAt` means Trash;
- `revision` continues to protect stale writes;
- title/document fields remain unchanged.

Organization-only mutations must still advance note revision so stale editor/library writes cannot silently overwrite each other.

## 5. Active / Archive / Trash state

These are distinct states.

### Active
`deletedAt == null && archivedAt == null`

Normal Library and Folder views show active notes only unless the current Library mode explicitly requests another lifecycle view.

### Archived
`deletedAt == null && archivedAt != null`

Archived notes:
- are absent from normal Library/folder note lists;
- remain in their folder;
- retain tags, pin, favorite and document content;
- appear in Archive.

Unarchive clears `archivedAt` and returns the note to its previous folder/Unfiled location.

### Trash
`deletedAt != null`

Trash takes precedence over Archive for visibility.

Trashing a note:
- sets `deletedAt`;
- preserves `archivedAt`, folderId, tags, pinned and favorite;
- removes the note from active/archive Library views;
- does not erase content.

Restoring from Trash:
- clears `deletedAt`;
- preserves all other organization state;
- therefore an item trashed while archived returns to Archive, and an active item returns to its prior active Folder/Unfiled location.

Permanent delete is available only from Trash and removes the note row plus dependent tag relationships/recovery artifacts. Future attachment cleanup will extend this operation in I-007.

## 6. Pin vs Favorite

Pin and Favorite are intentionally distinct.

**Pinned** affects prominence/order in applicable Library views. It does not move the note to a folder or collection.

**Favorite** is semantic user classification and enables a Favorites view/filter. It does not imply pinned ordering.

Both states persist through archive/trash/restore unless the user explicitly changes them.

## 7. Library views

I-003 provides structured Library modes, not a generic file manager.

Required views:
- current Folder / root Library;
- Unfiled;
- Favorites;
- Archive;
- Trash.

Root Library shows root-level folders plus active notes whose `folderId == null`.

Folder View shows direct child folders plus active notes whose folderId matches the folder.

A compact location/breadcrumb treatment must make current hierarchy understandable without consuming large vertical space.

Archive and Trash are lifecycle views and should not masquerade as physical folders.

## 8. List / Grid

Library supports List and Grid presentation.

The preference is local UI state persisted in DataStore.

List:
- compact title;
- useful body preview when readable;
- restrained metadata;
- tags/state indicators only when useful.

Grid:
- compact premium cards;
- content-first preview;
- no oversized dashboard cards;
- folders remain visually distinguishable from notes.

Changing view mode never changes data organization.

## 9. Sort / filter

I-003 minimum sort choices:
- Updated newest/oldest;
- Created newest/oldest;
- Title A–Z / Z–A.

Pinned-first is an independent ordering preference where applicable rather than a destructive re-sort of stored notes.

Minimum filters:
- tag;
- pinned;
- favorite.

Lifecycle views already filter by Active/Archive/Trash and should not create contradictory combinations.

Do not implement full-text query search here; that belongs to I-006.

## 10. Selection and actions

Long-press on a note enters multi-select.

I-003 bulk selection is note-focused. Folder structural operations remain explicit per-folder actions unless a later packet justifies bulk folder editing.

Bulk note actions:
- Move to Folder / Unfiled;
- Add/remove Tags;
- Pin/unpin;
- Favorite/unfavorite;
- Archive/unarchive where applicable;
- Move to Trash;
- Restore when in Trash;
- Permanent Delete only from Trash with confirmation.

Every destructive operation must have an understandable confirmation/recovery path appropriate to its severity.

## 11. Create behavior

Global/Board Quick Capture creates an Unfiled note unless a later explicit capture destination is chosen.

Creating a note while inside a Library Folder creates it in that Folder.

Creating a note from Unfiled creates it Unfiled.

New Folder creates inside the currently viewed physical Folder, or at root when at Library root. Virtual modes (Favorites/Archive/Trash/Unfiled) do not become folder parents.

## 12. Migration contract

I-003 introduces Room schema **v2**.

Migration 1 → 2 must be explicit, non-destructive, and covered with Room migration tests.

Existing v1 notes migrate as:
- `folderId = null`
- `archivedAt = null`
- `pinned = false`
- `favorite = false`

Existing IDs, title, document payload/schema, timestamps, deletedAt, revision and content must remain byte/logically equivalent except where SQLite representation necessarily changes.

Add Folder, Tag and NoteTag tables plus useful indices.

Do not use destructive migration fallback.

The v1 exported schema remains committed. Commit the new v2 exported schema.

## 13. Concurrency / revision safety

Library metadata operations and editor content saves may occur close together.

Do not implement organization mutations as blind full-row replacements.

Use transactional/targeted updates that:
- verify expected revision where needed;
- increment revision on note organization changes;
- prevent an older editor snapshot from silently erasing a newer folder/tag/lifecycle change;
- surface conflicts for retry/reload rather than pretending success.

## 14. UI direction

Follow Dark Editorial Utility / Compact Premium.

Library should feel like the structured counterpart to the spatial Board:
- compact hierarchy;
- minimal permanent chrome;
- quick scanning;
- restrained folder identity;
- progressive disclosure for organization actions;
- selection mode changes chrome clearly but does not become visually loud.

Avoid:
- giant hero headings;
- generic file-manager rows;
- permanent action buttons on every note;
- decorative empty space;
- fake Search features;
- Board-specific spatial controls.

## 15. Out of scope for I-003

Do not implement:
- Board pan/zoom/placement;
- Stacks;
- full-text/FTS Search;
- attachments/PDFs;
- note locking;
- backlinks/internal links;
- Smart Collections engine;
- revision-history UI;
- AI;
- cloud/PC sync;
- final appearance/customization settings.
