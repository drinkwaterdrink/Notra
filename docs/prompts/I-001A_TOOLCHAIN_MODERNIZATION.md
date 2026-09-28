# I-001A — Stable Toolchain Modernization

This is a narrowly scoped maintenance packet between I-001 and I-002.

## Why this packet exists

I-001 is accepted. During post-I-001 audit, the project was found to be intentionally pinned to the Compose 1.11 / AGP 8.13 / compileSdk 36 generation.

I-002 introduces the rich editor. Current stable Compose 1.12 adds first-party text-style tracking/editing primitives that are directly relevant to building the editor cleanly. Modernize the toolchain **before** editor implementation so I-002 does not combine a build-system migration with product feature work.

## Mandatory orientation

Before editing, read:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DESIGN_REFERENCE.md`

Inspect the current repository and I-001 implementation before changing anything.

## Scope

Modernize the existing I-001 project to a current **stable-only** Android/Compose toolchain that supports stable Compose 1.12.x.

Do not implement I-002 or any product feature.

Do not change application behavior, navigation, data semantics, Room schema, visual direction, or product decisions except where a build/toolchain API migration mechanically requires source changes.

## Current verified direction

As of 2026-09-28, official Android documentation reports:
- Compose Foundation/UI stable line: 1.12.1
- Compose BOM: 2026.09.00
- Activity stable: 1.13.0
- Lifecycle stable: 2.11.0
- Room stable: 2.8.5
- DataStore stable: 1.2.1
- API 37 is required by Compose 1.12
- AGP 9.2+ supports API 37; newer stable AGP versions may be available

Treat these as orientation, not permission to blindly copy versions. Verify the current stable compatibility matrix available in your environment before editing.

Use stable releases only. Do not introduce alpha, beta, or RC dependencies.

## Required outcome

At minimum:

- move `compileSdk` to API 37 so stable Compose 1.12 can be used;
- keep `minSdk = 26` unless a real verified dependency requirement forces otherwise;
- keep `targetSdk = 36` during this maintenance packet unless there is a concrete reason to change it; compileSdk and targetSdk are separate decisions;
- update AGP / Gradle / Kotlin / Compose compiler / KSP only as needed to form a fully supported stable combination;
- update Compose BOM to the current stable BOM compatible with Compose 1.12.x;
- update directly pinned AndroidX dependencies that are needlessly stale when a current stable release is compatible;
- keep Room and DataStore on their current stable versions if they are already current;
- update README build prerequisites if SDK/Gradle/JDK requirements change;
- preserve exported Room schema version 1 unchanged;
- preserve package/application IDs and all I-001 behavior;
- remove any stale comments that incorrectly describe the old toolchain as the newest available line.

Do not add Navigation3, editor code, rich-text code, or any dependency whose only justification belongs to I-002 or later.

## Verification

Run real verification after the migration:

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebugAndroidTest`
- `:app:connectedDebugAndroidTest` if an emulator/device is available
- `git diff --check`

Confirm:
- all four shell destinations still work;
- Room persistence tests still pass;
- DataStore tests still pass;
- version-1 Room schema did not change;
- no later feature scope was introduced.

If connected tests cannot run because API 37 emulator/system image is unavailable, do not claim them. Explain exactly what was and was not verified.

## Project record

Update `docs/IMPLEMENTATION_STATUS.md`.

Mark I-001A COMPLETE only if the migration builds and required non-device verification passes.

Record exact versions selected and why.

## Final response

Report:

### Versions before → after
### Files changed
### Why each toolchain change was necessary
### Verification actually run
### I-001A acceptance status
### Any behavior/schema changes (expected: none)
### I-002 readiness

Then stop.

Do not implement I-002.
