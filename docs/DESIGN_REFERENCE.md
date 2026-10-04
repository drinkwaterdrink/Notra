# Notra — Design & Interaction Reference

## Approved direction

**Dark Editorial Utility / Compact Premium**

The experience should feel like a meticulously designed professional personal workspace, not a playful sticky-note toy and not a generic AI dashboard.

The first physical-device APK validated the underlying app foundation but is **not** the visual target. It exposed several patterns to retire before more feature UI is layered on top:
- oversized page titles and explanatory copy consuming too much of the viewport;
- large placeholder panels with excessive dead space;
- a generic opaque Material bottom navigation treatment;
- product-facing copy that mentions implementation packet IDs;
- editor actions spread across two permanent bottom rows;
- a custom editor bottom row overlapping Samsung's native navigation bar.

These are scaffold artifacts, not approved product conventions.

## Product shell

Notra is **content-first**.

### Board

The Board should read as a workspace immediately, not as a page containing a board card.

Target composition:
- full-bleed spatial surface occupies nearly the entire usable viewport;
- subtle graphite/mineral/dot texture may provide spatial orientation without becoming a visible engineering grid;
- compact top chrome floats over or lightly separates from the canvas;
- global Quick Capture remains accessible without becoming a fifth navigation destination;
- compact floating HUDs may host zoom/center/lock/status controls when those functions exist;
- primary bottom navigation is visually restrained so the canvas remains dominant.

Do not put the Board inside a large rounded container.

### Library / Search / Settings

These are structured surfaces rather than hero pages.

Use:
- compact top title/app chrome;
- useful actions/search/filter affordances close to the title;
- dense, readable content beginning high in the viewport;
- progressive disclosure for secondary controls.

Avoid:
- giant heading + subtitle + large CTA + decorative empty panel as the default screen template;
- large unused vertical zones;
- explanatory development text such as `I-004` or `I-006` in user-facing builds.

## Primary navigation

Primary destinations remain:
- Board
- Library
- Search
- Settings

The current four-destination information architecture is correct, but the visual treatment should evolve from the stock full-width Material NavigationBar toward a compact Notra-specific dock/rail treatment while retaining Android accessibility, touch-target, and inset behavior.

Quick Capture is globally accessible but is not a fifth destination. A floating action or compact capture trigger may sit above/adjacent to navigation as long as it is visually distinct from destination navigation.

## Android system UI & insets

System-bar correctness is part of visual quality, not a cleanup item.

Requirements:
- support edge-to-edge drawing intentionally;
- interactive content respects `WindowInsets.safeDrawing` or equivalent component-specific insets;
- bottom app/editor chrome must remain above both gesture navigation and 3-button navigation;
- editor formatting controls must move above the software keyboard using IME-aware insets;
- status/top controls must remain clear of the status bar and display cutouts;
- do not apply the same inset twice when Material components already consume it;
- test both keyboard-hidden and keyboard-visible states.

A physical-device screenshot from the first I-002 build showed the editor insertion/action row underneath Samsung's native navigation buttons. That layout is invalid and must not persist into later packets.

## Editor

The editor should feel like a focused document surface, not a developer toolbar wrapped around text.

Target structure:
- compact safe-area-aware top row: Back, quiet save/error state, Undo/Redo, More;
- separate title with strong but restrained hierarchy;
- document content owns most of the viewport;
- one compact contextual formatting dock above the navigation bar/IME;
- secondary block insertion/style actions move into a sheet, popover, expandable palette, or horizontal overflow rather than requiring a permanently visible second action row;
- controls may become more visible while text is focused and recede when not needed.

Formatting remains fast to access, but content should dominate.

## Spatial Board

The Board is the north-star interaction.

Mechanics:
- true free placement;
- persistent X/Y position;
- independent card dimensions;
- overlap allowed;
- one-finger empty-canvas pan;
- direct card drag;
- two-finger pan/zoom;
- wide useful zoom range;
- board viewport persists;
- Board Lock prevents accidental moves;
- cards simplify semantically at distant zoom levels.

The reference screen recording supplied during planning demonstrates desired spatial freedom and smoothness only. Its visual theme is not a design reference.

## Kinetic Canvas inspiration reference

The user-provided `Kinetic Canvas Visual Workspace (1).html` is an approved **inspiration source**, not an implementation spec.

Useful ideas to translate into native Notra language:
- full-viewport canvas rather than a canvas-in-a-card;
- restrained spatial orientation texture;
- compact floating zoom/center/status controls;
- dark layered note nodes with subtle border, depth and active-drag lift;
- strong contrast between spatial Board and structured Library/grid organization;
- compact tag/color/filter language where those product features exist;
- focused editor presentation distinct from the browsing surface.

Do **not** copy by default:
- Graph view;
- S-Pen drawing studio;
- voice/audio notes;
- due dates/reminders;
- sound effects;
- emoji-heavy controls;
- high-saturation cyan/purple effects;
- desktop-hover-dependent behavior;
- permanent connect/archive controls on every resting card;
- the prototype's feature inventory when it conflicts with the Notra roadmap.

## Card density

Default cards should be significantly smaller than the early visual mockups.

Target feel:
- denser than a balanced showcase dashboard;
- more breathable than a purely utilitarian dense grid;
- enough visible notes to make spatial organization useful.

## Card resizing

Cards are continuously resizable.

Conceptual magnetic landmarks:
- Micro
- Compact
- Standard
- Expanded

These are helpful snaps, not hard limits.

Content changes responsively:
- tiny text card → title/meta;
- larger text card → body preview;
- tiny checklist → completion summary;
- larger checklist → interactive rows;
- tiny image/PDF → identity/thumbnail;
- larger versions → richer preview.

## Motion

Desired:
- direct tracking;
- restrained elevation/lift on pickup;
- one subtle pickup haptic;
- optional snap haptic;
- critically damped settle;
- contextual alignment guides.

Avoid:
- bounce;
- wobble;
- unnecessary rotation;
- exaggerated inertia;
- laggy spring-follow behavior.

## Material language

Primary surfaces:
- warm near-black graphite background;
- charcoal elevated surfaces;
- quiet fine borders;
- optional microscopic mineral/paper grain;
- broad subtle shadows;
- muted accent selected by user.

Default note palette:
- stone
- sage
- ochre
- terracotta
- desaturated blue
- plum
- neutral dark

Avoid oversaturated sticky-note rainbow as the default.

## Controls

At rest, cards are primarily content.

Selection reveals card chrome contextually:
- resize affordance;
- actions;
- selection state.

Do not permanently show resize/pin/menu controls on every card.

## Figma

Planning exploration file:
https://www.figma.com/design/uM7iGtb6lLBCTfwcDWDLGF

The initial Figma mockup is exploratory, not implementation-perfect. The later Dark Editorial Utility direction, physical-device findings, and this document override any conflict with the earliest mockup.
