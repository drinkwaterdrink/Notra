# Notra — Decision Ledger

**Last updated:** 2026-10-03

| ID | Status | Decision | Consequence |
|---|---|---|---|
| D-001 | LOCKED | Working product name is **Notra**. | Use Notra in project docs and app identity for now. |
| D-002 | LOCKED | Native Android-first. | Optimize for Android UX/tooling instead of cross-platform parity. |
| D-003 | LOCKED | Local-first source of truth. | Core use remains fully functional offline. |
| D-004 | LOCKED | Spatial Board is the signature home experience. | Board interaction receives first-class engineering/design effort. |
| D-005 | LOCKED | Structured Library represents the same notes. | Board organization is never required for retrieval. |
| D-006 | LOCKED | Dark Editorial Utility visual direction. | Dark, professional, restrained, tactile visual system. |
| D-007 | LOCKED | Compact-premium density, between Dense Workspace and Balanced. | Default cards are substantially smaller than showcase mockups. |
| D-008 | LOCKED | Notes are adaptive and continuously resizable. | Card content progressively changes with available area. |
| D-009 | LOCKED | Board uses true free placement. | Moving one object does not reflow others. |
| D-010 | LOCKED | Overlap is allowed. | Ordinary overlap must not accidentally create a Stack. |
| D-011 | LOCKED | Stacks are spatial grouping; Folders are durable hierarchy. | A note can remain in its Folder while also being in a Stack. |
| D-012 | LOCKED | Stack→Folder conversion is explicit. | No silent organizational mutation. |
| D-013 | LOCKED | Initial product has one Home Board. | Multi-board UI is deferred. |
| D-014 | DEFERRED | Multiple Boards. | Data design should not block later addition. |
| D-015 | LOCKED | Folders, tags, full search, pins/favorites, sorting, locking. | Core organization is comprehensive. |
| D-016 | LOCKED | Rich text, images, PDFs, and general files. | Notes use a structured document model. |
| D-017 | LOCKED | Deep customization. | Appearance, density, motion, haptics, and board behavior are adjustable. |
| D-018 | LOCKED | Future BYOK AI provider profiles. | Anticipate OpenRouter, NanoGPT, Google AI Studio, custom endpoints. |
| D-019 | LOCKED | AI changes are previewable/reviewable by default. | AI does not silently rewrite/reorganize user content. |
| D-020 | DEFERRED | Calendar/reminders. | Not part of early Android core. |
| D-021 | DEFERRED | Biometric unlock. | Passcode-first locking. |
| D-022 | DEFERRED | Hardware-keyboard optimization. | Mobile touch UX comes first. |
| D-023 | DEFERRED | Android↔PC sync/companion. | Future direction is hybrid/local-first. |
| D-024 | LOCKED | Motion should feel tactile but professional, not game-like. | Direct 1:1 drag, restrained lift, minimal settling. |
| D-025 | LOCKED | User-provided movement recording is a mechanics reference, not theme reference. | Preserve placement/zoom smoothness concepts but not its visual style. |
| D-026 | LOCKED | Android minimum SDK is 26 for the current native app baseline. | Preserve API 26 support unless a future requirement justifies an explicit migration. |
| D-027 | LOCKED | Interactive UI must respect Android safe-drawing, navigation-bar, cutout, and IME insets on every supported navigation mode. | No toolbar, button, card control, or navigation element may sit beneath Samsung 3-button navigation, gesture navigation, the status bar, a cutout, or the software keyboard. Physical-device inset defects are release blockers for the affected packet. |
| D-028 | LOCKED | The product shell is content-first and compact; avoid generic Material hero-page scaffolding. | Board becomes a full-bleed workspace with compact/floating chrome; Library/Search/Settings use restrained page chrome rather than oversized titles, explanatory panels, and large dead zones. |
| D-029 | LOCKED | The user-provided **Kinetic Canvas Visual Workspace** is inspiration for spatial density, layered dark surfaces, compact HUDs, canvas/grid contrast, and focused editing—not a feature-scope source. | Do not import Graph view, S-Pen drawing, audio notes, due dates/reminders, sound effects, emoji-heavy controls, permanent node chrome, or other prototype extras unless separately approved in the blueprint. |
