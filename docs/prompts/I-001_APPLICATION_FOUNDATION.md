# I-001 — Application Foundation Kickoff

You are implementing the first packet of **Notra**, a native Android local-first notes and knowledge workspace.

## Mandatory orientation

Before editing any code, read in full:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/IMPLEMENTATION_STATUS.md`
5. `docs/DESIGN_REFERENCE.md`

The repository and these documents are the persistent project context.

## Scope

Implement **I-001 — Application Foundation only**.

Do not begin I-002.

Do not implement later features merely because they appear in the Master Blueprint.

Specifically do **not** implement yet:
- functional spatial Board dragging/panning/zooming;
- adaptive resizing;
- Stacks;
- rich note editor;
- attachment picking/viewing;
- PDFs;
- locking/encryption;
- AI;
- provider connections;
- embeddings;
- cloud sync;
- PC functionality;
- reminders/calendar.

Future requirements may influence foundational data shape only where the Blueprint explicitly requires it.

## Phase 1 — Inspect before editing

First inspect the repository and report:

1. current project structure;
2. whether an Android project already exists;
3. Android/Gradle/Kotlin/Compose configuration;
4. current dependencies;
5. existing architecture/data layer;
6. existing tests;
7. overlap with I-001 if any;
8. conflicts between repository reality and the Blueprint;
9. recommended minSdk/targetSdk and dependency strategy for this personal modern-Android app;
10. exact implementation approach;
11. exact builds/tests/static checks you plan to run.

Do not rewrite working code merely to impose a preferred architecture.

## Phase 2 — Implement I-001

Create the foundation required by the Blueprint:

- runnable native Kotlin/Jetpack Compose application;
- application identity using working name **Notra**;
- primary navigation shell:
  - Board
  - Library
  - Search
  - Settings
- Dark Editorial Utility semantic theme foundation;
- semantic colors/surfaces/typography/shapes rather than scattered arbitrary values;
- initial domain/data models needed by Blueprint foundations;
- Room database foundation;
- DataStore preferences foundation;
- clear repository ownership boundaries;
- stable UUID-based identities;
- future-friendly lifecycle/revision/tombstone fields where specifically justified;
- app-managed attachment-storage directory abstraction only, without attachment UI;
- screen-level ViewModel/state architecture using unidirectional data flow;
- test infrastructure;
- safe development/preview seed mechanism if useful.

The four destination screens are shell states, not implementations of future packets.

They should already look intentionally Notra-like rather than untouched Android Studio starter UI, but **do not spend I-001 implementing the final Board**.

## Architecture constraints

Prefer straightforward architecture.

Do not add:
- networking libraries;
- cloud SDKs;
- AI SDKs;
- vector databases;
- sync engines;
- microservices;
- unnecessary multi-module complexity;
- generic repository/use-case/DI ceremony without a current concrete job.

If dependency injection is unnecessary at this stage, do not add a DI framework just because it is common.

Use Compose UDF/state ownership conventions.

Keep ViewModels at screen/navigation boundaries instead of injecting them into small leaf components.

## Phase 3 — Verification

After implementation, perform real verification.

At minimum:
- build the actual Android application;
- run relevant automated tests;
- run static checks available in the project;
- verify Board / Library / Search / Settings navigation;
- verify a Room persistence round-trip;
- verify DataStore preference persistence;
- verify process/app restart does not destroy persisted development data where testable;
- inspect for crashes or broken states.

Never claim a command/check/test ran if it did not.

If something cannot be verified, mark it NOT VERIFIED and explain why.

## Phase 4 — Project record

Update `docs/IMPLEMENTATION_STATUS.md`.

Only mark I-001 COMPLETE if its criteria actually pass.

Record:
- date;
- commit/PR if applicable;
- major changes;
- verification performed;
- results;
- blueprint deviations;
- known issues;
- notes for I-002.

Do not modify LOCKED decisions in `docs/DECISION_LEDGER.md` unless explicitly instructed.

## Final response

Provide:

### What changed
### Important files
### Architecture decisions
### Verification actually run
### I-001 acceptance criteria — PASS / FAIL / NOT VERIFIED
### Problems / deviations
### Next packet readiness

Then stop.

Do not implement I-002.
