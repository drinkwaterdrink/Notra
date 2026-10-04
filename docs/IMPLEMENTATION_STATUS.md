# Notra — Implementation Status

**Blueprint version:** 0.1  
**Current phase:** Physical-device UX foundation
**Current packet:** I-002P COMPLETE; Samsung review accepted, independent code audit/merge pending

| Packet | Status | Purpose | Verification |
|---|---|---|---|
| I-001 | COMPLETE | Application Foundation | Debug APK, unit test, lint, four emulator tests, force-stop persistence probe |
| I-001A | COMPLETE | Stable Toolchain Modernization | Debug APK, JVM test, lint, Android test APK, four emulator tests, schema hash |
| I-002 | COMPLETE | Notes Editor & Autosave | Debug APK, 11 JVM tests, lint, 16 emulator tests, force-stop probe, Room schema hash |
| I-002R | COMPLETE | Editor Reliability Repairs | 21 emulator tests, four regression failures reproduced before fix, schema unchanged, independent code audit accepted |
| I-002P | COMPLETE | Physical-Device UX Foundation | Automated verification passed; second physical Samsung review accepted; independent code audit/merge pending |
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
- **Known issues:** Shell screens intentionally have no note editing or Board interaction. The local test toolchain is ignored by Git; standard JDK 17/Android SDK 36 setup is documented in README.
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
- **Known issues:** No API 37 emulator physical verification during this packet.
- **Blueprint deviations:** None.
- **Notes for next packet:** I-002 may use Compose 1.12.1 state-based text APIs while preserving Room schema v1 where possible.

### I-002 — Notes Editor & Autosave
- **Status:** COMPLETE; independently reviewed and accepted after I-002R.
- **Date:** 2026-09-28 (America/New_York)
- **Commit/PR:** Initial implementation `20604f622c24459916d7e7166d096d746d5aca86` on `review/i-002`; reliability repairs in `33383933ce42c2e279a9e97da5d262cb62060c71`; merged through PR #1.
- **Summary:** Added portable V1 block documents; create, open, edit, autosave, recover, and soft-delete flows; a focused rich-text/block editor; a minimal active-note list; and Shell → Editor navigation. The existing four shell destinations remain.
- **Files/architecture:** `document/NoteDocument.kt` owns V1 DTOs, validation, canonical ranges, JSON encoding/decoding, legacy/future handling, and plain-text derivation. `editor/RichTextMapper.kt` maps persisted UTF-16 marks to Compose tracked styles and owns local inline-style undo. `editor/EditorViewModel.kt` owns session state, block operations, autosave, recovery coordination, and safe exit. `editor/EditorScreen.kt` renders editor UI. `data/RecoveryJournal.kt` owns app-private draft files. Note repository/DAO own conditional revision saves and soft delete; `MainActivity.kt` owns minimal note access and Navigation 3 routes.
- **Serialization/navigation:** `kotlinx.serialization-json:1.9.0`; no Compose classes in payloads. Navigation 3 runtime/UI `1.2.0`; editor routes carry stable note UUIDs.
- **Autosave/recovery policy:** 650 ms idle debounce; serialized revision-checked saves; Back and lifecycle-stop flush; app-private conflated recovery snapshots with atomic replacement. Queued drafts are accepted only while the session is active and when both generation and durable Room revision remain current. Save failures remain visible and discard truly clears the recovery state before leaving.
- **Tests/checks actually run:** I-002: `:app:assembleDebug`, 11 JVM tests, lint, Android test APK, 16 connected tests on API 36, separate force-stop probe, `git diff --check`, Room schema hash. I-002R: build, same 11 JVM tests, lint, Android test APK, 21 connected tests, `git diff --check`, schema/document/authoritative-file checks.
- **Results:** I-002 passed all initial checks. Independent audit found four reliability defects; regression tests reproduced them against the original behavior, and I-002R fixed them. Final connected suite: 21 passed. Room v1 schema remained unchanged with SHA-256 `E71F2FD94B0D277CC503A1656D405A91C75EA62E7986D5A87BB11491C8D3FE69`.
- **Acceptance criteria:** PASS after I-002R and independent code review.
- **Physical-device follow-up:** The merged APK was installed and exercised on a Samsung phone. Core create/edit/reopen behavior was sufficient to begin UX review. Screenshots exposed an invalid editor lower action row beneath native 3-button navigation plus a shell that remains intentionally scaffold-like. These are now assigned to I-002P rather than deferred to final polish.
- **Known issues:** Text/style undo remains segmented; structural undo is separate; soft-keyboard Enter splitting/Backspace merging remain deferred; recovery writes have a brief asynchronous window before the latest keystroke reaches disk. Physical-device visual/inset issues are tracked in I-002P.
- **Blueprint deviations:** None. Room schema version 1 and `EDITOR_DOCUMENT_FORMAT.md` V1 semantics were preserved.
- **Notes for next packet:** Complete I-002P before layering I-003 Library UI on the shell.

### I-002R — Editor Reliability Repairs
- **Status:** COMPLETE; independent audit accepted.
- **Date:** 2026-10-03 (America/New_York)
- **Commit/PR:** `33383933ce42c2e279a9e97da5d262cb62060c71` on `review/i-002`; merged through PR #1.
- **Root causes fixed:** stale queued recovery snapshots could overwrite a newer valid draft after Room revision advancement; discard retained recovery data; block removal/restoration accumulated observers; checklist type-conversion undo lost checked state.
- **Implementation:** queued journal writes now validate active session, deletion state, generation and durable revision while serialized under the save mutex; discard deactivates persistence synchronously and clears recovery under the mutex; failed journal deletion is surfaced; each active block owns one tracked observer; checklist structural undo/redo captures checked state.
- **Regression tests:** Added deterministic tests for stale recovery ordering + fresh reopen, discard ordering with in-flight/queued/lifecycle work, observer removal/restoration, checklist undo/redo, and the real failure-dialog discard/reopen flow.
- **Tests/checks actually run:** Build, 11 JVM tests, lint, Android test APK, 21 connected tests on API 36, `git diff --check`, Room/document/authoritative-file integrity checks.
- **Results:** Four required regressions failed against original I-002 behavior and passed after repair. Final suite passed with 0 connected failures. Room/document contracts unchanged.
- **Acceptance criteria:** PASS.
- **Known issues:** Existing non-blocking I-002 limitations remain; physical-device layout findings are handled in I-002P.
- **Blueprint deviations:** None.
- **Readiness:** Accepted and merged to `main`.

### I-002P — Physical-Device UX Foundation
- **Status:** COMPLETE — automated verification and primary physical Samsung acceptance passed; pending independent code audit/merge.
- **Date:** Implemented 2026-10-03; physical review accepted 2026-10-04 (America/New_York).
- **Commit/PR:** Review checkpoint on `review/i-002p`, based on `61ff954c2058a718103ec3b04885ab83e95b5f11`; see Git history. Not merged into `main`.
- **Summary:** Replaced oversized shell panels with compact content-first chrome, a full-window static graphite Board substrate, compact note rows, restrained Search/Settings surfaces, an accessible four-destination dock, and separate global New Note capture. Removed visible packet/development copy. Compressed the editor to one primary formatting dock with progressively disclosed block actions.
- **Files changed:** `app/src/main/AndroidManifest.xml`; `app/src/main/java/com/notra/app/MainActivity.kt`; `app/src/main/java/com/notra/app/ui/Chrome.kt` (new); `app/src/main/java/com/notra/app/ui/NotraShell.kt` (new); `app/src/main/java/com/notra/app/editor/EditorScreen.kt`; `app/src/androidTest/java/com/notra/app/EditorFlowTest.kt`; `app/src/androidTest/java/com/notra/app/UxFoundationTest.kt` (new); this status document.
- **Inset strategy:** Activity enables edge-to-edge with light system-bar icons and `adjustResize`. Reusable `TopChrome`/`BottomChrome` own their respective `WindowInsets.safeDrawing` edges. Scaffold body applies and consumes its provided padding. Editor Scaffold uses `imePadding()` so the dock follows the keyboard; already-consumed edges are not added twice. Material menus/dialogs retain their own window handling. No device-specific navigation spacer.
- **Editor structure:** Five direct inline formatting controls plus one block/insertion palette control, each with a 48dp touch target. Block styles, removal, list/checklist/divider insertion remain available through menus. Back, save/error state, Undo, Redo, and More remain in compact top chrome. Existing ViewModel, document mapping, autosave, recovery, and discard semantics are unchanged.
- **Tests/checks actually run:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon` on API 36 with 3-button navigation; a second `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.notra.app.UxFoundationTest --no-daemon` with gesture navigation, tall simulated cutout, and font scale 1.3; `git diff --check`; schema hash and unchanged-file checks; ADB APK install; final Board/editor screenshots with keyboard hidden/visible; `apksigner verify --verbose`; exported APK hash comparison.
- **Results:** All five Gradle tasks succeeded on final source. 11 JVM tests passed. All 24 connected tests passed (21 existing regressions plus 3 targeted UX tests); all 3 targeted tests passed in the gesture/cutout/large-text configuration. Lint: 0 errors, 4 warnings (existing tool/dependency/backup configuration warnings). Tests checked safe window bounds, visible IME, full dock touch targets, all destinations, formatting and palette reachability, checklist interaction, and note create/back/reopen. Final screenshots confirmed legible shell title and an unclipped dock above native navigation/IME on the emulator. APK installed and signature verified.
- **Preservation checks:** Room v1 schema SHA-256 remains `E71F2FD94B0D277CC503A1656D405A91C75EA62E7986D5A87BB11491C8D3FE69`. Data/persistence code, NoteDocument V1 DTO/codec, RichTextMapper, EditorViewModel, blueprint, ledger, design reference, and document-format specification remain unchanged. No dependencies or I-003+ features added.
- **APK handoff:** Repository-root `Notra-I-002P-debug.apk`, 17,778,724 bytes; SHA-256 `FB7832C2B8EAC42AACC9BAE2E01BCE236A08EA43BD9C4ECDD058B096C087CFB0`. It matches `app/build/outputs/apk/debug/app-debug.apk`. The earlier `Notra-debug.apk` is preserved; neither APK is staged.
- **Acceptance criteria:** PASS for the implemented scope and primary physical-device acceptance, as confirmed by the user. Automated IME clearance checks passed; a separate Samsung keyboard-specific result was not explicitly reported. Automated bounds tests are not represented as physical Samsung tests.
- **Physical-device verification:** Second review completed on the physical Samsung test device, reported and accepted by the user on 2026-10-04. Editor bottom chrome no longer overlaps native 3-button navigation; shell navigation clears system navigation; status-bar clearance is correct. Shell density is materially improved, Board reads as a workspace substrate, Library starts higher, Search/Settings are restrained, and four-destination navigation is comfortable and visually clearer.
- **Known issues:** Remaining product sparsity is expected because I-003+ feature packets are not implemented. Existing I-002 limitations remain unchanged. No additional implementation or design changes were made during checkpointing.
- **Blueprint deviations:** None. Primary Samsung physical acceptance is satisfied; independent code audit/merge remains pending.
- **I-003 readiness:** Physical foundation accepted; await independent code audit/merge and explicit authorization for the next packet. I-003 has not started.

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
