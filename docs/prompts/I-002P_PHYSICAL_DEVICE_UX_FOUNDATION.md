# I-002P — Physical-Device UX Foundation

## Objective

Correct the physical-device layout defects exposed by the first real APK and establish the compact, content-first Notra application shell **before** I-003 Library Organization and I-004 Spatial Board Engine build significant UI on top of the current scaffold.

This is a structural UX-foundation packet, not final visual polish.

---

## Why this packet exists

The accepted I-002 build was installed on a Samsung phone and verified that the core note/editor architecture works, but it exposed two categories of problems:

### Correctness defect
The editor's lower action row renders underneath Samsung's native 3-button navigation area. Interactive controls must never occupy system navigation, gesture, cutout, or IME regions.

### Structural visual problem
The current shell still looks like development scaffolding rather than Notra:
- oversized screen headings;
- repeated subtitle/CTA/placeholder-panel template;
- excessive unused vertical space;
- stock full-width Material navigation visual treatment;
- Board represented as a card inside a page rather than the workspace itself;
- user-facing development copy such as packet IDs;
- editor actions permanently spread across two bottom rows.

If I-003 and I-004 are built directly on that shell, later polish would require unnecessary rework.

---

## Mandatory orientation

Before editing, read:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DESIGN_REFERENCE.md`
6. `docs/EDITOR_DOCUMENT_FORMAT.md`
7. this packet

Pay special attention to D-027, D-028 and D-029.

I-002 and I-002R are accepted baselines. Do not change their persistence semantics.

---

# Scope

## In scope

### A. App-wide inset policy
Establish a deliberate edge-to-edge/inset strategy for the Compose app.

Requirements:
- use Android/Compose WindowInsets APIs rather than device-specific hardcoded padding;
- interactive content must remain clear of status bars, display cutouts and navigation bars;
- support both gesture navigation and 3-button navigation;
- custom bottom chrome must remain above native navigation controls;
- editor bottom chrome must remain above the IME while typing;
- avoid double-consuming insets when a Material 3 component already handles them;
- system-bar icon appearance must remain legible over Notra's dark surfaces.

Prefer component-specific handling such as `WindowInsets.safeDrawing`, `navigationBarsPadding`, `imePadding`, `windowInsetsPadding`, `Scaffold` inset values, or equivalent current Compose APIs as appropriate.

Do not use fixed bottom spacers intended to match one Samsung device.

### B. Compact shell foundation
Refactor the visual shell away from the current giant-header template.

Preserve the four destinations:
- Board
- Library
- Search
- Settings

But establish a reusable compact Notra shell with:
- restrained top chrome;
- much lower visual overhead before content begins;
- a compact Notra-specific bottom navigation treatment;
- correct safe-area behavior;
- consistent surface/border/typography primitives;
- global Quick Capture access from Board/Library without presenting it as a fifth destination.

The navigation implementation must remain accessible and use adequate touch targets even if its visual footprint is smaller.

### C. Board scaffold becomes full-bleed workspace substrate
Do **not** implement I-004 pan/zoom/card placement.

But replace the current `Board` page + large rounded placeholder card with a non-interactive visual substrate that accurately establishes future composition:
- canvas occupies the content viewport;
- graphite/mineral or extremely subtle orientation texture;
- compact top workspace chrome;
- subtle empty-state treatment only when needed;
- no user-facing `Spatial interaction arrives in I-004` text;
- no fake draggable cards or fake zoom controls.

The canvas substrate should be architecturally replaceable by the real I-004 engine without rebuilding the entire screen shell.

### D. Library scaffold cleanup
Do not implement folders/tags/sorting/filtering from I-003.

Improve only the current minimal active-note access surface:
- compact page chrome;
- notes begin higher in the viewport;
- clearer title/preview/updated hierarchy;
- restrained row/card separators;
- avoid enormous `New Note` button;
- no fake folder/tag UI.

The resulting layout must be a credible foundation for I-003.

### E. Search and Settings scaffold cleanup
Do not implement I-006 search.

Remove giant decorative placeholder panels and implementation packet wording.

Search may show a compact non-functional/disabled product-quality empty state without pretending search already works.

Settings should keep the existing Reduced Motion preference but present it using the same compact shell/surface language.

### F. Editor layout correction and control compression
Preserve I-002 editor semantics.

Required visual structure:
- safe-area-aware compact top row with Back, quiet save/error state, Undo, Redo, More;
- title and document dominate the screen;
- bottom editing controls are fully visible above native navigation and above the IME;
- replace the permanent two-row formatting/action layout with one compact primary formatting dock;
- secondary block insertion/actions should live in an expandable palette, sheet, menu, overflow, or equivalent progressive-disclosure surface;
- current formatting capabilities remain reachable;
- no feature loss solely for visual cleanup.

When keyboard is visible, the formatting dock should feel attached to the editing context rather than stranded behind/under the IME.

### G. Product-facing copy cleanup
Remove development implementation language from visible UI, including strings such as:
- `Spatial interaction arrives in I-004.`
- `Search arrives in I-006.`

Internal packet IDs belong in docs/tests, never normal product surfaces.

---

# Inspiration translation

The user-provided `Kinetic Canvas Visual Workspace (1).html` is inspiration only.

Translate:
- full-screen canvas composition;
- compact floating/control HUD language;
- dark layered surfaces;
- subtle fine borders;
- card/workspace depth;
- dense professional information hierarchy;
- clean contrast between spatial Board and structured collection surfaces;
- focused editor separate from browse mode.

Do not import:
- Graph view;
- S-Pen drawing;
- voice/audio notes;
- due dates/reminders;
- sound effects;
- daily journal feature;
- export feature from the mockup;
- high-saturation neon styling;
- emoji-heavy controls;
- permanent action buttons on resting cards;
- desktop hover behavior;
- any feature not already approved for this packet.

---

# Visual direction

Continue **Dark Editorial Utility / Compact Premium**.

## Background
Warm near-black graphite. Avoid pure black as the only layer.

## Elevated surfaces
Use subtle charcoal tonal separation, fine low-contrast borders, and restrained broad shadow/elevation only where hierarchy needs it.

## Accent
Muted periwinkle/indigo remains the primary active/control accent for now. Do not turn the app into a saturated neon dashboard.

## Typography
Crisp editorial hierarchy. Screen titles should be clearly identifiable without consuming a large portion of the phone display.

## Density
The first physical-device build is too loose. Increase useful information per screen without making touch targets cramped.

## Corners
Rounded, but avoid putting every piece of content into a giant rounded rectangle.

---

# Navigation direction

Keep D-005/D-028 information architecture.

Preferred direction:
- visually compact four-destination bottom dock/navigation surface;
- destination icon + concise label, or another accessible treatment that stays immediately understandable;
- selected state uses restrained accent/tone rather than a large opaque Material pill;
- shell content can visually extend behind/around navigation where safe, while interactive content respects safe drawing;
- Quick Capture is a separate action, not destination 5.

Do not over-customize semantics/accessibility just to achieve a visual trick.

---

# Insets acceptance matrix

Verify at least these states:

1. keyboard hidden + 3-button navigation;
2. keyboard visible + 3-button navigation;
3. keyboard hidden + gesture navigation if an emulator/device configuration is available;
4. keyboard visible + gesture navigation if available;
5. portrait status bar/cutout safe region;
6. bottom destination navigation;
7. editor formatting dock;
8. dialogs/menus/sheets introduced by the editor control compression.

Physical-device screenshots from the existing build are evidence that state (1) currently fails for the editor.

---

# Accessibility

- preserve reasonable minimum touch targets even when controls look visually compact;
- retain content descriptions/semantics where icons replace text;
- maintain sufficient text/background and control-state contrast;
- Reduced Motion continues to work and should remain accessible from Settings;
- do not make important actions depend only on color;
- support normal Android font scaling without overlap/clipping in the primary shell/editor controls.

---

# Out of scope

Do not implement:
- folders;
- tags;
- Library filtering/sorting;
- Board pan/zoom;
- Board card placement;
- Board resize;
- Stacks;
- Search functionality;
- attachments;
- locking;
- links/backlinks;
- AI;
- Graph view;
- S-Pen;
- audio notes;
- calendar/reminders;
- final animation/haptic polish.

Do not change:
- Room schema;
- NoteDocument V1 persistence contract;
- autosave/recovery semantics;
- accepted I-002R reliability behavior.

---

# Testing

## Regression
All existing I-002/I-002R tests must remain green.

Run:
- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebugAndroidTest`
- `:app:connectedDebugAndroidTest` when emulator/device is available
- `git diff --check`

## New UI/inset verification
Add targeted tests where Compose/instrumentation can verify:
- bottom editor controls exist and remain interactable;
- control compression did not remove formatting actions;
- shell navigation still switches all four destinations;
- no development packet-ID strings remain visible on shell screens;
- existing note create/open/back flows still work.

Do not claim automated Compose tests prove real Samsung inset behavior if they do not.

## Physical-device handoff
At completion, build a new debug APK and explicitly request a second real-device check for:
- editor toolbar vs Samsung navigation buttons;
- editor toolbar vs Samsung keyboard;
- shell density;
- bottom navigation comfort;
- top/status-bar clearance;
- visual direction compared with the first APK.

---

# Acceptance criteria

I-002P is PASS only if:

1. No editor control is obscured by native navigation with keyboard hidden.
2. No editor control is obscured by the IME with keyboard visible.
3. Shell destination controls remain clear of native navigation.
4. Board is visually a workspace substrate rather than a page containing a giant board placeholder card.
5. Library content begins substantially higher and presents active notes with a compact useful hierarchy.
6. Search/Settings no longer use oversized decorative placeholder panels.
7. User-facing packet-ID/development copy is gone.
8. Editor uses one compact primary formatting dock with secondary actions progressively disclosed.
9. All existing note persistence/reliability regression tests remain green.
10. Room schema and NoteDocument V1 remain unchanged.
11. No I-003+ feature scope is introduced.

---

# Project record

Update `docs/IMPLEMENTATION_STATUS.md` with:
- files changed;
- inset strategy;
- shell primitives created;
- editor-control structure;
- automated verification;
- physical-device status;
- any remaining visual issues;
- I-003 readiness.

---

# Final report

Report:

### Physical-device issues addressed
### Insets strategy
### Shell changes
### Editor changes
### Files changed
### Verification actually run
### I-002P acceptance criteria — PASS / FAIL / NOT VERIFIED
### What still requires physical-device verification
### I-003 readiness

Do not start I-003.
