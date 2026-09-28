# Notra — Implementation Status

**Blueprint version:** 0.1  
**Current phase:** Application foundation and toolchain modernization complete
**Current packet:** I-001A complete; I-002 is next

| Packet | Status | Purpose | Verification |
|---|---|---|---|
| I-001 | COMPLETE | Application Foundation | Debug APK, unit test, lint, four emulator tests, force-stop persistence probe |
| I-001A | COMPLETE | Stable Toolchain Modernization | Debug APK, JVM test, lint, Android test APK, four emulator tests, schema hash |
| I-002 | NOT STARTED | Notes Editor & Autosave | — |
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
