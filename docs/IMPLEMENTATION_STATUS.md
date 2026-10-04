# Notra — Implementation Status

**Blueprint version:** 0.1  
**Current phase:** Editor reliability repairs verified; awaiting independent review
**Current packet:** I-002R complete; I-003 remains not started

| Packet | Status | Purpose | Verification |
|---|---|---|---|
| I-001 | COMPLETE | Application Foundation | Debug APK, unit test, lint, four emulator tests, force-stop persistence probe |
| I-001A | COMPLETE | Stable Toolchain Modernization | Debug APK, JVM test, lint, Android test APK, four emulator tests, schema hash |
| I-002 | COMPLETE | Notes Editor & Autosave | Debug APK, 11 JVM tests, lint, Android test APK, 16 emulator tests, force-stop probe, Room schema hash |
| I-002R | COMPLETE | Editor Reliability Repairs | Debug APK, 11 JVM tests, lint, Android test APK, 21 emulator tests including five new regressions, unchanged Room/document contracts |
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
- **Version rationale:** [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes) specifies API 37, Gradle 9.6.0, SDK Build Tools 36.0.0, JDK 17, and built-in Kotlin dependencies. [Android built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin) requires removing `org.jetbrains.kotlin.android`. The [stable Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Activity](https://developer.android.com/jetpack/androidx/releases/activity), and [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) pages support the selected direct versions. [Room](https://developer.android.com/jetpack/androidx/releases/room) and [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) remain on their current stable versions. KSP 2.3.10 was checked against the [KSP releases](https://github.com/google/ksp/releases).
- **Tests/checks actually run:** `gradlew.bat help`; `gradlew.bat wrapper --gradle-version 9.6.0 --distribution-type bin`; `:app:assembleDebug`; `:app:testDebugUnitTest`; `:app:lintDebug`; `:app:assembleDebugAndroidTest`; `:app:connectedDebugAndroidTest` on the existing API 36 emulator; `:app:dependencyInsight --dependency androidx.compose.ui:ui --configuration debugRuntimeClasspath`; `git diff --check`; SHA-256 comparison of exported Room schema v1 before and after.
- **Results:** All listed Gradle tasks succeeded. One JVM test and four connected Android tests passed (four destinations, Room reopen, DataStore reopen, retained persistence probe). Lint: 0 errors, 3 warnings (intentional targetSdk 36, Gradle 9.8 newer than AGP 9.4's supported default 9.6.0, pre-existing backup-rule compatibility warning). Compose UI resolved to 1.12.1. Schema v1 SHA-256 remained `E71F2FD94B0D277CC503A1656D405A91C75EA62E7986D5A87BB11491C8D3FE69`; no schema diff. `git diff --check` passed.
- **Acceptance criteria:** PASS for stable-only migration, build, unit tests, lint, Android test APK, connected tests, four shells, Room/DataStore persistence, and unchanged schema.
- **Known issues:** Connected tests ran on an API 36 emulator; no API 37 emulator or physical device was tested. Existing lint warnings remain as described above.
- **Blueprint deviations:** None. No application behavior or data semantics changed.
- **Notes for next packet:** I-002 can use Compose 1.12.1's stable text APIs. Keep targetSdk 36 until a separate behavior migration, and preserve Room schema v1 with an explicit migration if I-002 changes persistence fields.

### I-002 — Notes Editor & Autosave
- **Status:** COMPLETE
- **Date:** 2026-09-28 (America/New_York)
- **Commit/PR:** Uncommitted working tree, as requested. No PR.
- **Summary:** Added portable V1 block documents; create, open, edit, autosave, recover, and soft-delete flows; a focused rich-text/block editor; a minimal active-note list; and Shell → Editor navigation. The existing four shell destinations remain.
- **Files/architecture:** `document/NoteDocument.kt` owns V1 DTOs, validation, canonical ranges, JSON encoding/decoding, legacy/future handling, and plain-text derivation. `editor/RichTextMapper.kt` maps persisted UTF-16 marks to Compose tracked styles and owns local inline-style undo. `editor/EditorViewModel.kt` owns session state, block operations, autosave, recovery coordination, and safe exit. `editor/EditorScreen.kt` renders editor UI. `data/RecoveryJournal.kt` owns app-private draft files. `NoteRepository`/DAO own conditional revision saves and soft delete; `MainActivity.kt` owns minimal note access and Navigation 3 routes.
- **Serialization/navigation:** `kotlinx.serialization-json:1.9.0` with the Kotlin 2.2.10 serialization plugin; no Compose classes in payloads. Navigation 3 runtime/UI `1.2.0` with the Lifecycle `2.11.0` ViewModel NavEntry decorator; Editor routes carry stable note UUIDs.
- **Autosave/recovery policy:** 650 ms idle debounce; serialized revision-checked saves; live-snapshot comparison on flush; Back and lifecycle-stop flush; immediate create/delete. A conflated IO writer stores the latest title/document snapshot under `filesDir/drafts/<noteId>.json` using temp-file atomic replacement. A matching-revision draft is restored on open; obsolete/corrupt drafts cannot replace Room data. Save failures remain visible and Back offers Retry, Keep editing, and Leave without latest changes.
- **Tests/checks actually run:** `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebugAndroidTest`, `:app:connectedDebugAndroidTest` on an API 36 emulator; separate ADB install → seed note → force-stop → relaunch → verify note probe; `git diff --check`; SHA-256 comparison of the exported Room v1 schema.
- **Results:** Debug APK and Android test APK built; 11 JVM tests and 16 connected tests passed; lint 0 errors and 4 warnings (intentional targetSdk 36, newer Gradle available, newer serialization runtime available, pre-existing backup-rule warning). The Room v1 schema remained unchanged with SHA-256 `E71F2FD94B0D277CC503A1656D405A91C75EA62E7986D5A87BB11491C8D3FE69`. The separate force-stop/relaunch probe passed. `git diff --check` passed.
- **Acceptance criteria:** PASS for tested note creation/reopen, title/body and rich-mark persistence, supported V1 blocks, checklist, truthful save-error behavior, Back/background flush, recovery, soft-delete, corrupt/future data protection, shell regressions, Room/DataStore persistence, and unchanged schema. No I-003+ behavior was added.
- **Known issues:** Text edits before an inline-style toolbar action fall outside the subsequent Undo history: formatting starts a new Compose text-undo segment, while a small separate style history supports sequential mark undo/redo. Structural undo is separate from focused text undo. Paragraph splitting with soft-keyboard Enter and soft-keyboard Backspace merge are deferred to I-010; explicit block insertion and hardware Backspace on empty blocks work. Connected tests used an API 36 emulator, not a physical device. Recovery writes are asynchronous, leaving a brief window before the IO worker records the newest keystroke.
- **Blueprint deviations:** None. Room schema version 1 was preserved.
- **Notes for next packet:** I-003 can build Library organization on the active-note repository and V1 plain-text derivation. Keep document DTOs independent from Compose; improve the noted IME/undo details in I-010 without altering V1 persistence semantics.

### I-002R — Editor Reliability Repairs
- **Status:** COMPLETE; awaiting independent review before merge.
- **Date:** 2026-10-03 (America/New_York)
- **Commit/PR:** Uncommitted repairs on `review/i-002`, based on `76fdd97`. No commit, push, or merge performed; `main` unchanged.
- **Root causes:** Queued drafts checked generation but not durable revision; discard only navigated away; block removals left snapshot collectors alive and restoration added more; type-conversion undo omitted checklist checked state.
- **Summary/files:** `editor/EditorViewModel.kt` now checks session activity, deletion state, generation, and base revision under the save mutex before queued journal writes. Discard synchronously deactivates the session, cancels/joins autosave, clears the journal under the same mutex, then leaves. Cancellation propagates instead of masquerading as a save error. Block observers have one tracked Job per block ID, cancelled on removal/replacement. Type undo/redo restores type and checked state. `data/RecoveryJournal.kt` reports failed draft deletion instead of claiming a successful clear. No UI redesign, dependency change, Room change, or V1 document-contract change.
- **Regression tests:** `EditorReliabilityTest.kt` gates save A, queues B against the old revision, succeeds A, fails B, waits for the stale queue worker, checks the on-disk revision/content, and reopens B. It also checks discard against an in-flight save/queued drafts/lifecycle work, repeated remove/restore with no orphan observers and exactly one dirty transition per edit, and checklist type undo/redo. `EditorErrorTest.kt` exercises the real failure dialog's discard action and a fresh ViewModel owner on reopen. Internal generation/queue-completion diagnostics are test seams only.
- **Tests/checks actually run:** Targeted `:app:connectedDebugAndroidTest` runs against the original behavior; then `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon` using the cached Gradle 9.6.0 distribution and JDK 17; `git diff --check`; exported Room v1 SHA-256 and Git diff checks for Room/document/authoritative files; branch and `main` ref checks.
- **Results:** The four required new regressions first failed on the original behavior (stale recovery revision, retained abandoned draft, orphan/duplicate observers, lost checklist state). After repairs, all 11 JVM tests and all 21 connected tests passed on the API 36 emulator, including five new tests and all 16 existing tests. Debug app and Android test APK built. Lint: 0 errors, 4 existing warnings (targetSdk 36, newer Gradle available, newer serialization runtime available, backup-rule compatibility). Compose test-rule deprecation warnings remain. An initial connected attempt could not install while the emulator was booting and ran no tests; all reported test outcomes come from subsequent completed runs.
- **Acceptance criteria:** PASS for R-001 revision invariant and fresh recovery; R-002 true discard and inactive-session persistence guard; R-003 one observer per active block/no orphan dirty transitions; checklist checked-state undo/redo; existing regressions; required builds/lint; unchanged persistence contracts; `git diff --check`.
- **Schema/document status:** Room remains version 1; exported schema SHA-256 remains `E71F2FD94B0D277CC503A1656D405A91C75EA62E7986D5A87BB11491C8D3FE69`. `docs/EDITOR_DOCUMENT_FORMAT.md` and document DTO/codec are unchanged. Blueprint, decision ledger, and design reference are unchanged.
- **Known issues:** Existing I-002 limitations remain: segmented inline-style/text undo, separate structural undo, deferred soft-keyboard Enter splitting/Backspace merging, a brief asynchronous recovery-write window, and no physical-device verification. The connected suite includes the retained persistence probes; a separate force-stop/relaunch sequence was not rerun for this repair packet.
- **Blueprint deviations:** None. No I-003 or later features added.
- **Readiness:** Repairs are ready for independent review before merging `review/i-002`; I-003 remains not started.

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
