# Orange mapping runbook

This is the durable operating procedure for evidence-backed Orange service-tree work. It is documentation, not live-call authorization and not a prompt to paste into another chat.

## Scope

Repository: `MichalMatu/android-ai-call-bridge`.

Durable product branch: `main`.

Local Agent task/result traffic: `agent-control` only.

Physical Orange target, when a current operator session explicitly authorizes live testing: `510100100`.

The service-tree source of truth is `service-packs/orange/service_tree.v1.json`.

## Session start order

1. Read `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, this runbook, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/PHASE2D_FREEZE_2026-09-18.md`, and the current Orange service tree.
2. Fetch fresh `origin/main` and fresh `agent-control:.agent/status/daemon.json`.
3. Verify repository identity and use only the current chat/daemon `agent_binding`.
4. Inspect the latest terminal Local Agent result, current `git status`, and branch/worktree state before writing or queueing work.
5. Run or inspect the canonical host baseline before any new physical slice if the current checkpoint has not already been proven in the same session.
6. Never infer live-call authorization from this file, Git history, a previous chat, or old `.agent/results`. Physical calls require explicit authorization in the current operator session.

## Evidence semantics

Keep these levels separate:

- `DISCOVERED` service seed: a capability is known or physically touched, but the complete service route is not proven;
- `VERIFIED` node: that exact IVR node/barrier was physically observed;
- `VERIFIED` observed edge: that exact physical transition was observed;
- `service_route_verified=true`: the intended deterministic service route itself was physically reached and proven;
- `HOST_GREEN`: deterministic host tests/build/lint pass;
- `PROVEN_S22`: the required behavior was physically reproduced on the target S22+.

A root reprompt can verify an edge while the corresponding service seed remains only `DISCOVERED`.

## Closed reviewed utterances

Do not repeat a reviewed utterance after it has been physically exercised and closed by a known reprompt/barrier merely to collect duplicate evidence. The current tree and persistence tests are authoritative for closed branches.

If a later operator-native wording is intentionally tested for the same service, create a distinct reviewed action/evidence record rather than overwriting old evidence.

## Per-seed implementation order

For one new low-risk seed at a time:

1. Select one not-yet-closed informational/diagnostic capability. Public operator documentation may justify a `DISCOVERED` seed only; it never creates `VERIFIED` route evidence.
2. Define exactly one reviewed, deterministic, non-committing utterance and typed action ID.
3. Add RED tests for runner/action exposure, Gate C reviewed-turn behavior, and the service-tree seed.
4. Prove RED failures are the intended missing behavior.
5. Implement the minimal GREEN through existing application-owned authority:
   - `OrangeLiveAction` reviewed constant;
   - `GateCLiveCallScenario` root-only reviewed `SAY` rule;
   - diagnostic runner action allowlist/reviewed response;
   - `DISCOVERED` service-tree seed only.
6. Run targeted Python/Kotlin tests and `bash scripts/verify_host.sh`.
7. Verify no Orange-specific dependency leaked into generic `serviceintent/` and no frozen media/helper code changed.
8. If production Kotlin changed and a physical test is authorized, install the exact proven `main` SHA on the S22 and require call state `IDLE` before dialing.
9. Execute at most one bounded reviewed speech turn for that wording, then switch to `OBSERVE_ONLY`.
10. Require `backend_generate_calls=0`, exact reviewed approved text, bounded retry policy, cleanup, and final phone state `IDLE`.
11. Stop immediately at authentication, credentials, customer-data, payment, purchase, activation/deactivation, tariff/contract/package change, ticket creation, or other commitment barriers. Record the barrier; do not bypass it.
12. Add a persistence RED test describing only what the physical evidence actually proved.
13. Persist the observed edge/node/barrier and seed outcome in `service_tree.v1.json`; do not promote `service_route_verified` without physical route evidence.
14. Align any older seed tests that intentionally described the pre-live state.
15. Run targeted persistence regressions and the full `bash scripts/verify_host.sh` gate.
16. Run a final S22 `IDLE` proof when a physical call was made.
17. Update only authoritative documentation when product/gate/continuation state changed.
18. Leave `main` clean. Keep Local Agent task/result traffic on `agent-control` only.

## Root acquisition retry policy

Root-acquisition retries are diagnostic exceptions, not semantic matching. Retry only exact evidence-backed preroll fragments or the narrowly tested root `NO_MATCH` case. The current service tree and runner are the source of truth for the accepted preroll set.

Do not broaden these into fuzzy Orange rules. `OBSERVE_ONLY` never retries by speaking and never releases speech.

## Authority and safety invariants

- one active cellular call at a time;
- exact operator-defined allowlisted destination only;
- no arbitrary free-text IVR surface;
- no runtime model-generated IVR speech;
- `OBSERVE_ONLY` never speaks;
- deterministic route tests require `backend_generate_calls=0`;
- models/classifiers may suggest only bounded existing IDs and cannot create dial/speech/commitment authority;
- no invented PESEL, customer/contract IDs, SMS codes, payment data, or other credentials;
- no purchase, activation, tariff/contract/package change or other commitment for discovery;
- every created test call must clean up to `IDLE`;
- do not touch frozen Samsung media or `privileged-helper/` without a separately justified physical regression gate.

## Checkpoint / handoff mode

Before ending a work session:

1. stop adding new service seeds;
2. persist the last physical evidence completely;
3. run targeted tests plus `bash scripts/verify_host.sh` on the final durable `main`;
4. verify frozen-path and generic-`serviceintent` guards;
5. verify only intentional branches remain (`main`, `agent-control` unless another branch has an explicitly documented active purpose);
6. if a phone was used, verify final call state `IDLE` without dialing again;
7. update `README.md`, `docs/ROADMAP.md`, and `docs/HANDOFF_NEXT_CHAT.md` if their state changed;
8. leave the next execution order in `docs/ROADMAP.md` and this runbook, not in a paste-ready prompt.

Detailed experiment logs belong in Git history and `.agent/results`, not in new status documents.
