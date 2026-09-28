# AGENTS.md — Notra

This repository is developed with AI coding agents. These instructions are authoritative for every agent session.

## Required reading before editing

Read, in order:

1. `docs/NOTRA_MASTER_BLUEPRINT.md`
2. `docs/DECISION_LEDGER.md`
3. `docs/IMPLEMENTATION_STATUS.md`
4. `docs/DESIGN_REFERENCE.md`
5. the prompt for the current implementation packet under `docs/prompts/`

## Source-of-truth hierarchy

When instructions conflict, use this priority:

1. Current explicit user instruction
2. LOCKED decisions in `docs/DECISION_LEDGER.md`
3. `docs/NOTRA_MASTER_BLUEPRINT.md`
4. Current implementation-packet prompt
5. Existing repository conventions and implementation reality
6. General engineering convention

Never silently override a LOCKED decision. If repository reality conflicts with a locked product decision, report the conflict before changing behavior.

## Working method

- Inspect the repository before editing.
- Implement only the requested implementation packet.
- Prefer the simplest architecture that fully satisfies the packet.
- Do not build future packets “while you are here.”
- Do not add speculative frameworks, layers, abstractions, cloud services, AI dependencies, or sync infrastructure without a current requirement.
- Preserve working conventions unless a change has a concrete reason.
- Keep the app compiling/runnable after every packet.
- Run real verification; never claim tests or builds ran when they did not.
- Update `docs/IMPLEMENTATION_STATUS.md` after completing a packet.
- Do not mark a packet COMPLETE unless its acceptance criteria pass.
- Record unavoidable blueprint deviations explicitly.

## Product guardrails

Notra is:
- native Android;
- local-first;
- offline-capable;
- single-user/personal-first;
- privacy-conscious;
- built around a spatial Board plus structured Library;
- intentionally polished and professional.

The Board is not:
- a masonry list;
- a forced grid;
- a game-like physics toy;
- a replacement for deterministic search and Library organization.

AI is optional and later. Core note behavior must never depend on AI or network access.

## Design guardrails

Primary direction: **Dark Editorial Utility / Compact Premium**.

Use:
- near-black warm graphite surfaces;
- restrained depth;
- subtle material texture;
- compact information density;
- crisp hierarchy;
- contextual controls;
- professional, critically damped motion;
- selective haptics.

Avoid:
- generic starter-template Material UI;
- excessive glassmorphism;
- giant cards as the normal board state;
- neon-heavy “AI app” styling;
- permanent card chrome;
- bouncy/game-like motion;
- excessive pill-shaped containers.

## Verification contract

Every packet report must include:

1. What changed
2. Important files changed
3. Architectural decisions
4. Commands/checks/tests actually run
5. Results
6. Acceptance criteria: PASS / FAIL / NOT VERIFIED
7. Known issues and deviations
8. Whether the repo is ready for the next packet

Stop after the requested packet.