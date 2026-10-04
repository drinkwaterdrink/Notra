# Notra — Implementation Status

**Blueprint version:** 0.1  
**Current phase:** Notes editor and autosave accepted
**Current packet:** I-002 complete and reviewed; I-003 is next

| Packet | Status | Purpose | Verification |
|---|---|---|---|
| I-001 | COMPLETE | Application Foundation | Debug APK, unit test, lint, four emulator tests, force-stop persistence probe |
| I-001A | COMPLETE | Stable Toolchain Modernization | Debug APK, JVM test, lint, Android test APK, four emulator tests, schema hash |
| I-002 | COMPLETE | Notes Editor & Autosave | Debug APK, 11 JVM tests, lint, 16 emulator tests, force-stop probe, Room schema hash |
| I-002R | COMPLETE | Editor Reliability Repairs | 21 emulator tests, four regression failures reproduced before fix, schema unchanged, independent code audit accepted |
| I-003 | NOT STARTED | Library Organization | — |
| I-004 | NOT STARTED | Spatial Board Engine | — |
| I-005 | NOT STARTED | Adaptive Cards, Resize & Stacks | — |
| I-006 | NOT STARTED | Search | — |
| I-007 | NOT STARTED | Attachments | — |
| I-008 | NOT STARTED | Note Locking | — |
| I-009 | NOT STARTED | Linking, Smart Collections & History | — |
| I-010 | NOT STARTED | Customization & Final UX Polish | — |
| I-011 | NOT STARTED | Backup / Restore / Export | — |
| I-012 | DEFERRED | AI Provider Framework | — |
| I-013 | DEFERRED | AI Note Assist | — |
| I-014 | DEFERRED | AI Organization | — |
| I-015 | DEFERRED | Semantic Intelligence | — |
| I-016 | DEFERRED | Multiple Boards | — |
| I-017 | DEFERRED | Sync & PC Foundation | — |

## Packet completion template

### I-001 — Application Foundation
- **Status:** COMPLETE
- **Date:** 2026-09-27 (America/New_York)
- **Commit/PR:** I-001 foundation commit on `main`; see Git history. No PR.
- **Summary:** Bootstrapped a single-module Kotlin/Compose Android app with Notra identity, four destination shells, semantic dark theme, Room note foundation and exported version-1 schema, DataStore preferences, a focused note repository, and an app-managed attachment directory boundary. No later-packet feature implementation was added.
- **Tests/checks actually run:** `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, `:app:connectedDebugAndroidTest` on an API 36 emulator, `git diff --check`, and a separate ADB install → Room/DataStore seed tests → app force-stop/relaunch → persistence verification test.
- **Results:** Debug APK built; one JVM unit test passed; four connected Android tests passed (navigation, Room reopen, DataStore reopen, persisted probe); force-stop/relaunch persistence verification passed. Lint: 0 errors, 7 dependency/tool-version warnings. `git diff --check` passed.
- **Acceptance criteria:** PASS for runnable app, navigation, Room and DataStore persistence, and process restart persistence on emulator. Crash-free within tested flows; broader device testing is not claimed.
- **Known issues:** No physical-device verification. Shell screens intentionally have no note editing or Board interaction. The local test toolchain is ignored by Git; standard JDK 17/Android SDK 36 setup is documented in README.
- **Blueprint deviations:** None. `minSdk 26`, `targetSdk 36`, and Compose 1.11 BOM were selected for AGP 8.13 / SDK 36 compatibility.
- **Notes for next packet:** I-002 should introduce the versioned structured note document, editing and autosave on top of the existing Room note identity/repository. Preserve a migration path from the exported version-1 schema. Do not put note editing into the current shell implicitly.

### I-001A — Stable Toolchain Modernization
- **Status:** COMPLETE
- **Date:** 2026-09-28 (America/New_York)
- **Commit/PR:** `c929b74ffd4b27b30924813765443107a98e4dc0` on `main`. No PR.
- **Summary:** Updated AGP 8.13.2 → 9.4.0, Gradle wrapper 8.13 → 9.6.0, compileSdk 36 → 37, Compose BOM 2026.04.01 → 2026.09.00 (resolved Compose UI 1.12.1), Activity 1.11.0 → 1.13.0, Lifecycle 2.9.4 → 2.11.0, and KSP 2.2.20-2.0.3 → 2.3.10. AGP 9.4 uses built-in Kotlin 2.2.10, so the redundant Kotlin Android plugin was removed and Compose compiler plugin aligned to 2.2.10. Kept minSdk 26, targetSdk 36, Room 2.8.5, DataStore 1.2.1, app identity, and all application source unchanged.
- **Version rationale:** See the I-001A history and toolchain packet for official Android compatibility references.
- **Tests/checks actually run:** `gradlew.bat help`; wrapper generation; `:app:assembleDebug`; `:app:testDebugUnitTest`; `:app:lintDebug`; `:app:assembleDebugAndroidTest`; `:app:connectedDebugAndroidTest` on API 36; dependency insight; `git diff --check`; Room schema hash comparison.
- **Results:** All listed Gradle tasks succeeded. One JVM test and four connected Android tests passed. Compose UI resolved to 1.12.1. Room schema v1 remained unchanged.
- **Acceptance criteria:** PASS.
- **Known issues:** No API 37 emulator or physical-device verification.
- **Blueprint deviations:** None.
- **Notes for next packet:** I-002 may use Compose 1.12.1 state-based text APIs while preserving Room schema v1 where possible.

### I-002 — Notes Editor & Autosave
- **Status:** COMPLETE; independently reviewed and accepted after I-002R.
- **Date:** 2026-09-28 (America/New_York)
- **Commit/PR:** Initial implementation `20604f622c24459916d7e7166d096d746d5aca86` on `review/i-002`; reliability repairs in `33383933ce42c2e279a9e97da5d262cb62060c71`. Review/merge PR recorded in Git history.
- **Summary:** Added portable V1 block documents; create, open, edit, autosave, recover, and soft-delete flows; a focused rich-text/block editor; a minimal active-note list; and Shell → Editor navigation. The existing four shell destinations remain.
- **Files/architecture:** `document/NoteDocument.kt` owns V1 DTOs, validation, canonical ranges, JSON encoding/decoding, legacy/future handling, and plain-text derivation. `editor/RichTextMapper.kt` maps persisted UTF-16 marks to Compose tracked styles and owns local inline-style undo. `editor/EditorViewModel.kt` owns session state, block operations, autosave, recovery coordination, and safe exit. `editor/EditorScreen.kt` renders editor UI. `data/RecoveryJournal.kt` owns app-private draft files. Note repository/DAO own conditional revision saves and soft delete; `MainActivity.kt` owns minimal note access and Navigation 3 routes.
- **Serialization/navigation:** `kotlinx.serialization-json:1.9.0`; no Compose classes in payloads. Navigation 3 runtime/UI `1.2.0`; editor routes carry stable note UUIDs.
- **Autosave/recovery policy:** 650 ms idle debounce; serialized revision-checked saves; Back and lifecycle-stop flush; app-private conflated recovery snapshots with atomic replacement. Queued drafts are accepted only while the session is active and when both generation and durable Room revision remain current. Save failures remain visible and discard truly clears the recovery state before leaving.
- **Tests/checks actually run:** I-002: `:app:assembleDebug`, 11 JVM tests, lint, Android test APK, 16 connected tests on API 36, separate force-stop probe, `git diff --check`, Room schema hash. I-002R: build, same 11 JVM tests, lint, Android test APK, 21 connected tests, `git diff --check`, schema/document/authoritative-file checks.
- **Results:** I-002 passed all initial checks. Independent audit found four reliability defects; regression tests reproduced them against the original behavior, and I-002R fixed them. Final connected suite: 21 passed. Room v1 schema remained unchanged with SHA-256 `E71F2FD94B0D277CC503A1656D405A91C75EA62E7986D5A87BB11491C8D3FE69`.
- **Acceptance criteria:** PASS after I-002R and independent code review.
- **Known issues:** Text/style undo remains segmented; structural undo is separate; soft-keyboard Enter splitting/Backspace merging are deferred to I-010; recovery writes still have a brief unavoidable asynchronous window before the latest keystroke reaches disk; no physical-device verification yet.
- **Blueprint deviations:** None. Room schema version 1 and `EDITOR_DOCUMENT_FORMAT.md` V1 semantics were preserved.
- **Notes for next packet:** I-003 can build Library organization on the active-note repository and V1 plain-text derivation. Keep document DTOs Compose-independent.

### I-002R — Editor Reliability Repairs
- **Status:** COMPLETE; independent audit accepted.
- **Date:** 2026-10-03 (America/New_York)
- **Commit/PR:** `33383933ce42c2e279a9e97da5d262cb62060c71` on `review/i-002`; review/merge PR recorded in Git history.
- **Root causes fixed:** stale queued recovery snapshots could overwrite a newer valid draft after Room revision advancement; discard retained recovery data; block removal/restoration accumulated observers; checklist type-conversion undo lost checked state.
- **Implementation:** queued journal writes now validate active session, deletion state, generation and durable revision while serialized under the save mutex; discard deactivates persistence synchronously and clears recovery under the mutex; failed journal deletion is surfaced; each active block owns one tracked observer; checklist structural undo/redo captures checked state.
- **Regression tests:** Added deterministic tests for stale recovery ordering + fresh reopen, discard ordering with in-flight/queued/lifecycle work, observer removal/restoration, checklist undo/redo, and the real failure-dialog discard/reopen flow.
- **Tests/checks actually run:** Build, 11 JVM tests, lint, Android test APK, 21 connected tests on API 36, `git diff --check`, Room/document/authoritative-file integrity checks.
- **Results:** Four required regressions failed against original I-002 behavior and passed after repair. Final suite passed with 0 connected failures. Room/document contracts unchanged.
- **Acceptance criteria:** PASS.
- **Known issues:** Existing non-blocking I-002 limitations remain; no physical-device verification yet.
- **Blueprint deviations:** None.
- **Readiness:** Accepted for merge to `main`; I-003 remains not started.

When completing a packet, record:

### I-XXX — [name]
- **Status:** COMPLETE / PARTIAL / BLOCKED
- **Date:**
- **Commit/PR:**
- **Summary:**
- **Tests/checks actually run:**
- **Results:**
- **Acceptance criteria:** PASS / FAIL / NOT VERIFIED
- **Known issues:**
- **Blueprint deviations:**
- **Notes for next packet:**
