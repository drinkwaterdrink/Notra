# I-002R — Editor Reliability Repairs

This is a narrow corrective packet for the existing `review/i-002` implementation.

Do not add I-003 or later features. Do not redesign the editor. Do not change the persisted V1 document contract or Room schema.

## Mandatory orientation

Read before editing:

1. `AGENTS.md`
2. `docs/NOTRA_MASTER_BLUEPRINT.md`
3. `docs/DECISION_LEDGER.md`
4. `docs/EDITOR_DOCUMENT_FORMAT.md`
5. `docs/prompts/I-002_NOTES_EDITOR_AUTOSAVE.md`
6. current `review/i-002` implementation

## Why this repair exists

Independent code audit found three correctness problems that should be fixed before I-002 is merged to `main`.

### R-001 — Stale queued recovery draft can overwrite a newer recovery draft

Current behavior:

- `markDirty()` enqueues a `RecoveryDraft` that captures the current Room `revision`.
- `flush()` can save an earlier generation, advance `revision`, then write a newer recovery draft using that new revision before attempting the next save.
- A previously queued draft from before the successful save can later run after `flush()` releases `writeMutex`.
- The queue writer currently checks only `draft.generation > durableGeneration`.
- Therefore a queued draft with a stale `durableRevision` can replace the correct newer recovery file.
- If the next Room save failed, reopening rejects that stale recovery file because its `durableRevision` no longer matches the Room row; newest unsaved changes can then be lost.

Required invariant:

A queued draft must never overwrite recovery state if it was created against an obsolete durable Room revision.

Acceptable fix direction:

- while holding the same serialization mutex used by saves, only write queued drafts that are still current for the editor's durable revision;
- at minimum require both:
  - draft generation is newer than `durableGeneration`; and
  - draft `durableRevision` matches the editor's current `revision`;
- preserve the correct draft written by `flush()` before a failed Room save;
- do not solve this by weakening recovery validation on open.

Add a deterministic regression test that forces this sequence:

1. dirty generation A begins a delayed save;
2. user creates generation B while A is saving, so a B draft exists that was captured against the old revision;
3. A save succeeds and advances Room revision;
4. `flush()` writes a correct B draft against the new revision;
5. B Room save fails;
6. previously queued stale B recovery work is allowed to run;
7. final on-disk recovery draft must still carry the new Room revision and B content;
8. a fresh editor open must restore B.

The test must fail on the current implementation and pass after the repair.

### R-002 — “Leave without latest changes” does not actually discard latest changes

Current behavior:

`leaveWithoutLatest()` pops the editor route without clearing or invalidating the recovery draft. A later open can therefore restore the edits the user explicitly chose to leave without.

Required semantics:

“Leave without latest changes” means discard the current unsaved session state and return to the latest durable Room version.

Repair requirements:

- cancel/deactivate autosave for that editor session;
- prevent already queued recovery writes from recreating the draft after it is cleared;
- serialize discard against save/journal work so ordering is deterministic;
- clear the recovery draft;
- only then leave the editor;
- do not modify the durable Room note.

Add regression coverage:

1. inject save failure;
2. make unsaved changes;
3. verify failure dialog appears;
4. choose `Leave without latest changes`;
5. reopen the note;
6. latest durable Room content must appear, not the abandoned draft;
7. recovery journal must no longer contain the abandoned snapshot.

### R-003 — Removed/re-added blocks can accumulate duplicate snapshot observers

Current behavior:

`addRuntimeBlock()` launches a `snapshotFlow` collector in `viewModelScope` for each block. Removing a block does not cancel its collector. Undo/re-add calls `addRuntimeBlock()` again for the same `EditorBlock`, starting an additional collector. Repeated remove/undo cycles can therefore retain orphan collectors and make one logical edit trigger multiple `markDirty()` calls/generations.

Required invariant:

At most one active change observer may exist for each currently observed `EditorBlock`; removed blocks must not leave live collectors behind.

Preferred simple solutions:

- track one Job per block ID and cancel it when that block is removed, replacing/cancelling before re-observing; or
- replace per-block jobs with one lifecycle-safe aggregate observation strategy that naturally tracks only current blocks.

Do not introduce a large editor-observer framework for this.

Add regression coverage that repeatedly removes and restores a block, then makes one edit and proves one logical dirty transition is observed rather than N duplicate transitions. If generation is private, expose only a test seam that does not become production UI/API, or assert through journal/save calls with a fake.

## Additional small structural-undo correction

While touching block undo, preserve checklist state when undoing a type conversion. Current `setBlockType()` clears `checked` when converting away from checklist, but its undo action restores only the old type, not the old checked value.

Fix so undo/redo faithfully restores the relevant block state and add a focused unit/instrumentation test.

## Non-goals

Do not fix in this packet:

- soft-keyboard Enter splitting;
- soft-keyboard Backspace merging beyond existing behavior;
- global unified undo architecture;
- toolbar layout polish;
- folders/tags;
- Board;
- attachments;
- search;
- locking;
- AI;
- sync.

Those remain later work unless a repair is strictly required to make the above fixes correct.

## Verification

After repairs run:

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebugAndroidTest`
- `:app:connectedDebugAndroidTest` when emulator/device is available
- `git diff --check`

Also verify:

- exported Room v1 schema is unchanged;
- `docs/EDITOR_DOCUMENT_FORMAT.md` is unchanged;
- all existing I-002 tests still pass;
- the new stale-recovery race regression passes;
- leave-without-latest truly discards recovery state;
- block observer duplication regression passes;
- checklist type undo restores checked state correctly.

## Final report

Report:

### Root causes confirmed
### Code changes
### New regression tests
### Verification actually run
### Room/document-schema status
### Remaining known I-002 limitations
### Whether `review/i-002` is ready to merge

Do not start I-003.
Do not merge to `main` unless explicitly instructed.