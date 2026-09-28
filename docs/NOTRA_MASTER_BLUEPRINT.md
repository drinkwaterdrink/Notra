# Notra — Master Implementation Blueprint

**Blueprint version:** 0.1  
**Date:** 2026-09-27  
**Platform:** Native Android  
**Working name:** Notra  
**Primary user:** Single-user / personal use  
**North-star screen:** Spatial Board  
**Design direction:** Dark Editorial Utility / Compact Premium  
**Technical direction:** Native Kotlin + Jetpack Compose, local-first Room database, app-managed attachment storage, modular future AI/sync boundaries.

> Notra combines fast capture, structured organization, rich documents, links/backlinks, and a tactile spatial Board where notes can be freely positioned, resized, overlapped, stacked, and visually arranged.

---

# 1. Product Definition

Notra has two complementary views over the same note data:

**Board** — spatial and personal.  
**Library** — structured and deterministic.

Neither view owns the note. The app must remain fully useful offline.

## Product principles

1. Capture must remain extremely fast.
2. Search/Library must recover information regardless of Board arrangement.
3. Board manipulation must feel exceptionally direct.
4. Work must be autosaved and recoverable.
5. Power is progressively disclosed instead of permanently cluttering the UI.
6. AI is optional and never required for core use.
7. Architecture remains simple until requirements justify complexity.

## Initial non-goals

Deferred from Android core v1:
- PC/cloud sync
- multiple Boards
- calendar/reminders
- biometric unlock
- hardware keyboard specialization
- AI
- semantic/vector search
- PDF annotation

---

# 2. Feature Catalog

## F-001 Local-First Notes Core — CORE

Every note exists primarily in local storage.

Requirements:
- create/open/edit/delete notes offline;
- autosave;
- no manual Save button;
- recover safely from process/lifecycle interruption;
- stable IDs;
- future-compatible revision/tombstone metadata without implementing sync.

Acceptance:
- created note survives process restart;
- airplane mode does not degrade core note behavior;
- failed persistence does not masquerade as saved state.

## F-002 Spatial Home Board — CORE / SIGNATURE

Large logical coordinate space containing note and Stack placements.

Gestures:
- tap card → open;
- drag card → move;
- drag empty canvas → pan;
- two-finger transform → pan + zoom;
- selected card → contextual controls;
- resize selected card → continuous resize;
- Board Lock → card positions fixed while one-finger navigation remains possible.

Rules:
- no automatic reflow;
- overlap allowed;
- viewport persists;
- world coordinates are independent of physical pixels;
- persistence happens at meaningful manipulation boundaries, not every pointer frame.

Acceptance:
- moving A never changes B coordinates;
- position/viewport survive restart;
- zoom changes viewport, not stored geometry.

## F-003 Adaptive Resizable Cards — CORE / SIGNATURE

Cards resize continuously with magnetic semantic landmarks:
Micro → Compact → Standard → Expanded.

Card contents adapt to available space.

At far zoom, render semantic summaries rather than illegible text.

Acceptance:
- continuous resizing within type-specific bounds;
- exact dimensions persist;
- preview adapts without altering source note data.

## F-004 Stacks — CORE

Stacks group notes spatially.

Ordinary overlap remains ordinary overlap. Stack creation requires deliberate target activation.

Supports:
- collapsed Stack;
- focused expansion;
- rename;
- member order;
- add/remove;
- dissolve;
- move as one;
- Create Folder from Stack.

Folder conversion explicitly asks whether Stack should remain.

## F-005 Library & Folder Hierarchy — CORE

Library supports:
- nested folders;
- Unfiled;
- list/grid;
- bulk selection;
- move;
- archive;
- trash;
- pin/favorite;
- lock;
- filter/sort.

A note belongs to zero or one primary Folder.

## F-006 Tags — IMPORTANT

Many-to-many tags independent of Folder/Stack.

Searchable/filterable.

## F-007 Note Links & Backlinks — IMPORTANT

`[[` invokes note-link selection.

Backlinks are available without dominating the main editor.

Missing targets remain understandable instead of corrupting documents.

## F-008 Universal Search — CORE

Search title/body/tags/folders/attachment names and metadata.

Filters include:
- folder
- tag
- pinned
- favorite
- attachment type
- locked
- date/recency

Actions:
- Open
- Show on Board
- Show in Folder

Use an FTS-backed local index.

Locked plaintext must not persist in FTS while locked.

## F-009 Rich Document Editor — CORE

Structured document model.

Initial blocks/features:
- paragraph
- heading
- bold/italic/underline/strikethrough
- highlight
- bullets/numbered lists
- checklist
- quote
- code
- divider
- URL
- internal note link
- image
- PDF
- file

Contextual formatting and insertion UI.

Undo/redo.

Autosave.

## F-010 Images & File Attachments — CORE

Use Android system pickers.

Successfully imported content is copied into app-managed storage.

Database stores metadata/path rather than large binary blobs.

Board/editor previews use sampled/cached representations.

## F-011 PDF Experience — IMPORTANT

Initial:
- attach
- first-page preview
- full viewer
- page navigation
- share/export

PDF annotation deferred.

## F-012 Pins, Favorites, Archive, Trash & Sort — CORE

Pin and Favorite remain distinct.

Deletion → Trash.

Trash → Restore or Permanent Delete.

Sort includes modified/created/title/manual where appropriate/pinned-first/folder.

## F-013 Note Locking — CORE

Passcode-first.

Use established Android crypto/security facilities.

Locked content:
- privacy-safe previews;
- no persistent plaintext FTS;
- consistent protection for relevant attachments;
- no custom cryptography.

Biometric unlock later.

## F-014 Smart Collections — IMPORTANT

Saved filters such as:
- PDFs
- Images
- Locked
- Recent
- Unfiled
- Pinned
- Has Checklist
- user-defined filter combinations

Smart Collection deletion never deletes notes.

## F-015 Customization — IMPORTANT

Appearance:
- system/dark/light
- accent
- note palette
- board background
- texture
- corners
- depth

Board:
- default card size
- guides
- snapping/intensity
- haptics
- Board Lock behavior
- default zoom
- preview density

Editor:
- font size
- line spacing
- default note type

Motion:
- Reduced
- Subtle
- Balanced

## F-016 Backup / Export / Restore — IMPORTANT

Versioned portable backup containing structured note data, relationships, Board placement, Stacks, attachments, and portable settings.

Validate before mutation.

Failure leaves current library untouched.

Human-readable per-note export where practical.

## F-017 Version History — IMPORTANT

Meaningful revision snapshots, not every keystroke.

Restore creates a new current state rather than erasing history.

## F-018 AI Provider Framework — LATER

Profiles for:
- OpenRouter
- NanoGPT
- Google AI Studio
- custom compatible endpoint

Profile contains provider type, display name, endpoint where relevant, secure credential reference, model, capability info/options.

Dynamic model discovery where provider supports it.

## F-019 AI Note Assist — LATER

Actions may include grammar, formatting, shortening, expansion, tone, headings, checklist conversion, summary, action extraction, title suggestion.

Preview before Apply.

## F-020 AI Organization — LATER

Suggest folder/tag/Stack, related notes, inbox organization, duplicate/outdated review, AI Arrange.

AI structural changes are proposals, not silent mutations.

AI Arrange uses ghost preview before Apply.

## F-021 Semantic Intelligence — LATER

Semantic search, related notes, Ask My Notes.

Concrete embedding/vector implementation selected only when this packet starts.

## F-022 Multiple Boards — LATER

Schema may support many; initial UX exposes Home only.

Folders are not Boards.

A note may later have placements on multiple Boards.

## F-023 Hybrid Android ↔ PC Sync — LATER

Future local-first sync/companion layer.

Must not be bolted directly into persistence without a separate conflict/sync blueprint.

---

# 3. Primary Journeys

## J-001 Capture

Board → Quick Capture → type → autosave → Compact card appears in predictable Inbox/new-note region.

## J-002 Arrange

Drag card → lift state → optional guides/snap → release → commit coordinates.

Simple overlap does not group.

Deliberate Stack target → release → Stack.

## J-003 Resize

Select → resize affordance → continuous resize → adaptive preview → optional magnetic landmarks → persist dimensions.

## J-004 Structural Organization

Library → select → Move → Folder.

Folder change does not alter Board coordinates.

Stack→Folder is explicit.

## J-005 Search

Search → query → local FTS results → filters → Open / Show on Board / Show in Folder.

## J-006 Attach

Editor → Insert → system picker → successful copy → attachment block → autosave.

Interrupted copy leaves no partial logical attachment.

## J-007 Lock

Note → Lock → passcode setup/confirm → protected content → privacy-safe surfaces.

## J-008 AI Edit — later

Action → explicit provider/profile → request → proposed diff/preview → Apply/Cancel.

Failure changes nothing.

---

# 4. Information Architecture

Bottom navigation:
- Board
- Library
- Search
- Settings

Quick Capture is globally accessible but not a navigation destination.

Context actions use compact menus/bottom sheets.

---

# 5. Screen Register

| ID | Screen |
|---|---|
| S-001 | Home Board |
| S-002 | Library |
| S-003 | Folder View |
| S-004 | Note Editor |
| S-005 | Universal Search |
| S-006 | Stack Focus |
| S-007 | Selection / Actions Sheet |
| S-008 | Attachment Viewer |
| S-009 | Settings |
| S-010 | Appearance & Board Settings |
| S-011 | Privacy & Lock |
| S-012 | Backup & Restore |
| S-013 | AI & Provider Profiles (later) |
| S-014 | AI Change Preview (later) |

---

# 6. Critical UX

## S-001 Home Board

Nearly all available space belongs to the canvas.

Resting cards are content-first with minimal chrome.

Selected cards reveal:
- resize affordance;
- contextual actions;
- selection state.

Drag:
- immediate/direct;
- restrained lift;
- optional useful alignment guides;
- no game-like bounce/rotation.

Zoom:
- viewport transform only;
- semantic simplification at distant scales.

Accessibility:
- every gesture has a menu alternative;
- Reduced Motion preserves state feedback without spring/scale theatrics.

## S-002 Library

Compact premium density.

Folders + notes.

List/grid preference.

Bulk actions.

Persistent sort/filter where useful.

## S-004 Editor

Quiet document surface.

Top:
Back / title-state / Undo / Redo / More.

Contextual keyboard formatting toolbar.

`+` block insertion.

Autosave usually invisible unless exceptional.

## S-005 Search

Immediate focus.

Filter chips/controls remain clear.

Locked content never leaks snippets.

Zero-state explains filters and offers reset.

## S-006 Stack Focus

Focused expansion, not uncontrolled scattering.

Rename/add/remove/reorder/create Folder/dissolve.

---

# 7. Design System

## Identity

**Dark Editorial Utility / Compact Premium**

Utility before decoration.

Quiet materiality.

Dense enough for real work.

Content is the primary chrome.

Motion communicates continuity.

## Palette direction

Semantic tokens:
- background: warm near-black graphite
- canvas: slightly lifted graphite
- surface: charcoal
- surfaceElevated: lighter charcoal
- surfaceSelected: charcoal + restrained accent edge
- textPrimary: warm near-white
- textSecondary: neutral gray
- textMuted
- border
- divider
- accent: muted periwinkle/indigo default direction
- success/warning/error

Default note palette:
stone / sage / ochre / terracotta / desaturated blue / plum / neutral dark.

## Typography

Use a deliberate modern editorial/geometric sans.

Do not fetch a runtime font from the network.

Final bundled font/licensing is verified during implementation.

Must tolerate Android font scaling.

## Shape

Moderate card rounding.

Pills only for semantically pill-shaped controls.

## Texture

Optional microscopic mineral/paper grain.

Must never harm contrast.

Adjustable/disableable.

## Elevation

Rest low.

Selected medium.

Dragged clearly elevated.

Sheets/modal strongest.

## Motion

Direct manipulation.

Critically damped.

Selective haptics:
- pickup
- snap
- resize landmark
- Stack creation
- destructive confirmation

Not every tap vibrates.

---

# 8. Data Model

## Note

Conceptual fields:
- id UUID
- title
- documentPayload
- plainTextSnapshot (derived/searchable when allowed)
- folderId?
- createdAt
- updatedAt
- archivedAt?
- deletedAt?
- pinned
- favorite
- cardStyle
- securityState
- revision/sync-friendly lifecycle metadata

Document payload has explicit schema version.

## Folder
- id
- name
- parentFolderId?
- sortOrder
- appearance
- timestamps

Prevent cyclic ancestry.

## Tag
- id
- name
- optional appearance

NoteTag relation many-to-many.

## Attachment
- id
- noteId
- localRelativePath
- displayName
- mimeType
- byteSize
- createdAt
- encryption state
- source/thumbnail metadata as needed

Do not store large binaries directly in Room.

## Board
- id
- name
- isHome
- viewport state
- timestamps

Initial UI exposes Home only.

## BoardItem
- id
- boardId
- type NOTE/STACK
- targetId
- worldX/worldY
- worldWidth/worldHeight
- zOrder
- timestamps

World units are independent of device pixels.

## Stack
- id
- boardId
- name?
- presentation settings
- timestamps

## StackMember
- stackId
- noteId
- order

## SmartCollection
- id
- name
- serialized filter definition
- appearance/order

## NoteRevision
- id
- noteId
- timestamp
- serialized snapshot/version representation
- source/reason

## SearchIndex

FTS content for eligible unlocked material.

## Preferences

DataStore for small settings such as theme/board/editor/motion preferences.

---

# 9. State Architecture

Persisted domain state:
- Room
- app-managed files

Preferences:
- DataStore

Screen state:
- screen-level ViewModels/state holders
- immutable UI state
- event-driven unidirectional flow

Transient Board state must not be persisted every pointer frame:
- active gesture
- dragged object
- temporary resize
- snap candidate
- selection
- hover/Stack target
- viewport transform in progress

Commit transient transforms at meaningful interaction boundaries.

---

# 10. Technical Architecture

Initial stack:
- Kotlin
- Jetpack Compose
- Room
- DataStore
- Coroutines + Flow
- app-specific file storage
- system Photo Picker / Storage Access Framework
- Android Keystore / established crypto when locking is implemented
- WorkManager only for work that truly must persist outside foreground lifecycle

Architecture:

Compose UI
→ ViewModels / State holders
→ focused use cases where coordination is meaningful
→ repositories
→ Room + attachment store

No generic layer earns existence merely because a “clean architecture” diagram includes it.

Use cases become explicit when logic is meaningful, e.g.:
- MoveBoardItems
- CreateStack
- ResizeBoardItem
- ImportAttachment
- LockNote
- RestoreRevision
- SearchNotes

---

# 11. Spatial Board Engine

## Coordinates

Centralize:
- worldToScreen
- screenToWorld

Viewport:
- panX
- panY
- zoom

Objects store world-space geometry.

## Rendering

Compute visible world rectangle.

Compose/render items intersecting viewport + reasonable prefetch margin.

At far zoom use simplified semantic card renderers.

Do not introduce a quadtree/spatial database index unless profiling proves it necessary.

## Gesture arbitration

- 2+ pointers → viewport transform
- 1 pointer on selected resize handle → resize
- 1 pointer on card:
  - under movement threshold → open
  - beyond threshold → drag
- 1 pointer on empty canvas → pan
- Board Lock → card drag suppressed; pan remains

## Drag lifecycle

Idle → PressCandidate → Dragging → optional SnapCandidate/StackCandidate → Commit → Idle

Persistence at Commit.

## Resize lifecycle

Idle → Selected → Resizing → Commit → Selected

---

# 12. Rich Editor Architecture

UI edits an in-memory versioned `NoteDocument`.

ViewModel owns editable state.

Autosave serializes the structured document atomically.

Derived plain-text snapshot is updated with commits for eligible notes.

FTS updates logically with note commits.

Attachment block appears only after import copy succeeds.

Attachment binary cleanup must respect undo/history retention.

---

# 13. Privacy & Security

Initial core requires no account.

No note data leaves device in non-AI/non-sync product.

No broad all-files permission.

Logs must not print:
- note bodies
- locked titles/content
- credentials
- AI request bodies by default

Future AI UI makes transmitted content/provider clear.

API keys are excluded from normal backups.

---

# 14. Failure / Recovery Requirements

| Scenario | Required behavior |
|---|---|
| Offline start | Full core works |
| Picker unavailable/cancelled | Note unchanged |
| Storage fills during import | Partial file removed; actionable error |
| DB write fails | Draft retained; unsaved state explicit |
| Kill after committed edit | Latest committed state restored |
| Kill mid-drag | Last committed placement remains |
| Corrupt attachment | Note opens; attachment reports issue |
| Broken internal link | Missing reference remains understandable |
| Failed backup import | Existing library untouched |
| AI unavailable later | Core unaffected |
| Sync unavailable later | Local editing continues |

---

# 15. Testing

## Unit
- coordinate transforms
- viewport math
- snap candidate selection
- adaptive presentation thresholds
- Stack mutation rules
- Folder cycle prevention
- sort/filter
- document serialization
- plain-text extraction
- backup validation

## Persistence
- DAO behavior
- FTS synchronization
- migrations
- trash/restore
- revisions
- attachment metadata transactions

## UI / Compose
- create/open/edit
- Board drag
- pan/zoom
- resize
- Stack creation
- bulk Library actions
- search filters
- locked redaction
- attachment insertion

## Accessibility
- TalkBack semantics
- large font
- touch targets
- gesture alternatives
- Reduced Motion
- color-independent state

## Manual polish
Physical Android device:
- drag latency
- gesture conflict
- keyboard/editor
- haptics
- texture/contrast
- card density
- zoom readability
- nested folders
- attachment-heavy notes

Jank during direct Board manipulation is release-blocking.

---

# 16. Implementation Packets

## I-001 — Application Foundation

Create a runnable trustworthy shell:
- Kotlin/Compose application
- Board/Library/Search/Settings navigation shell
- Dark Editorial Utility semantic theme foundation
- Room schema foundation
- DataStore
- base repositories/domain models
- app-managed file directory abstraction
- test infrastructure
- safe dev seed path

No Board engine, rich editor, AI, attachments UI, encryption, or sync.

## I-002 — Notes Editor & Autosave

- structured NoteDocument
- simple rich editing
- create/open/edit/delete
- autosave
- undo/redo
- process/lifecycle recovery

Usable basic notes app after packet.

## I-003 — Library Organization

- nested Folders
- tags
- pin/favorite
- archive/trash
- list/grid
- sorting/filtering
- bulk actions

## I-004 — Spatial Board Engine

Interaction correctness before visual flourish:
- coordinate model
- pan
- zoom
- drag
- viewport persistence
- Board Lock
- visible-item filtering

No resize/Stacks yet.

## I-005 — Adaptive Cards, Resize & Stacks

- adaptive renderers
- continuous resizing
- semantic landmarks
- snap guides
- Stack intent detection
- collapsed Stack
- Stack Focus
- Stack→Folder

**North-star UX milestone.**

## I-006 — Search

- FTS
- filters
- Show on Board
- Show in Folder
- Smart Collection primitives

## I-007 — Attachments

- Photo Picker
- file/PDF system picker
- app-managed imports- thumbnails
- PDF viewer
- share/export

## I-008 — Note Locking

- passcode setup
- established crypto
- protected payload
- locked attachment handling
- FTS removal/redaction
- security tests

## I-009 — Linking, Smart Collections & History

- note links/backlinks
- Smart Collections
- NoteRevision/history/restore

## I-010 — Customization & Final UX Polish

- final design-system component pass
- appearance controls
- board behavior
- texture
- motion levels
- haptics
- accessibility
- Reduced Motion

## I-011 — Backup / Restore / Export

- versioned backup format
- validation
- transactional restore
- attachments
- human-readable export

At completion, non-AI Android v1 is feature-complete.

## I-012 — AI Provider Framework — later
## I-013 — AI Note Assist — later
## I-014 — AI Organization — later
## I-015 — Semantic Intelligence — later
## I-016 — Multiple Boards — later
## I-017 — Sync & PC Foundation — later

---

# 17. Traceability

| Feature group | Main screen/journey | Packet |
|---|---|---|
| Local note core | Editor / capture | I-001/I-002 |
| Folders/tags/lifecycle | Library | I-003 |
| Board movement | Home Board | I-004 |
| Resize/Stacks | Home Board / Stack Focus | I-005 |
| Search | Search | I-006 |
| Attachments/PDF | Editor/Viewer | I-007 |
| Locks | Editor/Privacy | I-008 |
| Links/Collections/History | Editor/Library | I-009 |
| Customization/polish | Settings/all | I-010 |
| Backup | Backup & Restore | I-011 |
| AI providers | AI Settings | I-012 |
| AI editing | Editor | I-013 |
| AI organization | Board/Library | I-014 |
| Semantic search | Search | I-015 |
| Multi-board | Board | I-016 |
| Sync/PC | future | I-017 |

---

# 18. Core Acceptance Scenarios

## A — Free Placement
Dragging A over B without Stack target activation keeps two independent cards.

## B — Intentional Stack
Holding A over B until Stack target activates then dropping creates a Stack while Folder membership stays unchanged.

## C — Adaptive Resize
Growing a checklist reveals more actionable rows; shrinking it collapses detail without data loss.

## D — Search Independence
A note outside the current Board viewport remains findable from its indexed content.

## E — Spatial Persistence
Committed card geometry and viewport restore after restart.

## F — Offline Reliability
Core create/edit/search/organize functionality works without network.

## G — Locked Privacy
Locked note content is absent from Board/Library/search previews while locked.

## H — Attachment Safety
Failed import leaves no dangling logical attachment or orphan partial file.

---

# 19. Audit Rules

Before declaring any packet complete ask:

- What important decision would the next coding agent still need to guess?
- Did we accidentally introduce future scope?
- Did we preserve offline/local ownership?
- Are failure/recovery states real?
- Is the UX consistent with Dark Editorial Utility?
- Did verification actually run?
- Do tests prove important behavior?
- Did any LOCKED decision get changed?

If yes to the last question, stop and surface the conflict.