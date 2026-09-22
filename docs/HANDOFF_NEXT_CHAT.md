# Handoff — stabilized foundation, Gate D next

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

This handoff is a state snapshot, not a prompt. The authoritative execution order is `docs/ROADMAP.md`.

## Product pivot

Orange mapping is complete enough for its current purpose: proving and stress-testing the deterministic cellular speech path. Do **not** continue broad Orange mapping by default.

The next main milestone is **Gate D — hybrid multi-turn Task Engine**, with `BOOK_APPOINTMENT` as the first real end-to-end task.

Target architecture:

```text
User goal
 -> CallTask + constraints/preferences/authorized facts
 -> TaskGraph
 -> CallWorkflow
 -> deterministic PhraseMatrix/parsers first
 -> bounded LLM supervisor only for ambiguity/unknown
 -> existing transition ID + typed slot proposals only
 -> deterministic validation
 -> CallPlan / output approval
 -> proposal / confirmation / commitment
 -> structured completion
```

The LLM remains part of the product, but as a bounded supervisor/classifier. It does not own target selection, arbitrary runtime speech, credentials, workflow authority or commitment.

Skills are also preserved as a future layer, primarily for building/updating bounded `CallTask`s, selecting existing TaskGraphs/service packs, gathering pre-call facts/preferences and suggesting existing transitions/slots. Skills must remain behind the same application authority owners.

## Proven foundation

The frozen Samsung media foundation remains:

```text
DONE / PROVEN_S22 / FROZEN
```

Physically proven path:

```text
cellular RX
 -> local STT
 -> PhraseMatrix
 -> CallPlan validation
 -> application-owned output approval
 -> local TTS
 -> cellular TX
 -> bounded next-turn handling
 -> cleanup to IDLE
```

The generic `serviceintent/` resolver remains provider-neutral and authority-free. Its bounded-ID + revalidation pattern is the design precedent for the Gate D LLM supervisor.

Authority remains owned by:

- `CallTask`;
- `CallResolvedTarget`;
- `CallWorkflow`;
- `CallConfirmationPolicy`;
- `CallCommitmentGate`;
- application-owned output approval.

## Stabilization checkpoint

Last code/data Orange checkpoint before documentation/pivot work:

```text
40b04c6c269ed327abb0742cb66f6f64b8f2b545
```

Full stabilization seal passed on:

```text
54650bc835af0085fa6b0c952d5fdb92b2251936
.agent/results/chatgpt-stabilization-checkpoint-v267-20260922.json
```

Evidence included:

```text
46 Orange Python tests: OK
bash scripts/verify_host.sh: GREEN
no privileged-helper/ or audio-bridge/ changes since code/data checkpoint
no serviceintent/ changes since code/data checkpoint
FINAL_CALL_STATE=0
STABILIZATION_CHECKPOINT_VERIFIED=true
```

Final handoff sanity later passed on:

```text
3cee94299186d0b110b55ee64fd1f27c370c5b73
.agent/results/chatgpt-stabilization-handoff-final-v269-20260922.json
```

The repository documentation was then updated directly to record the Gate D product pivot. Always use fresh `origin/main` rather than assuming an older hash is current HEAD.

## Orange side-track state

Source of truth: `service-packs/orange/service_tree.v1.json`.

Checkpoint summary:

- `orange.root`, `orange.root.reprompt`, `orange.activation.clarification_barrier` physically verified;
- 19 physically verified observed root edges;
- 16 service seeds, all `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- latest physical evidence: `chatgpt-orange-caller-id-restriction-info-live-v264-20260922`;
- latest live path used `backend_generate_calls=0`, then `OBSERVE_ONLY`, then cleanup to `IDLE`.

Orange is now a regression/evidence pack and future service-pack-format source. Resume broad mapping only when it directly supports a generic product capability or a concrete Orange product use case.

Orange-only operating procedure remains in `docs/ORANGE_MAPPING_RUNBOOK.md`.

## Gate D immediate execution order

Follow `docs/ROADMAP.md`. The intended next work is:

1. define `TaskGraph v1` models, typed transitions, slots, guards, recovery and replayable event log;
2. implement `BOOK_APPOINTMENT` entirely on host;
3. create a deterministic simulated receptionist harness with multiple dialogue scenarios;
4. add typed date/time/offer parsing and PhraseMatrix dialogue-act coverage;
5. exercise proposal -> confirmation -> commitment -> completion in simulation;
6. add recovery, cancellation, takeover and unauthorized-information paths;
7. add a bounded LLM supervisor that returns only existing transition IDs + typed slots;
8. reject stale, invalid, malicious and authority-bearing model output fail-closed;
9. integrate the graph into a real product session owner rather than growing diagnostics into orchestrators;
10. only then move to small real-world appointment tests.

## Real-world appointment test policy

After `BOOK_APPOINTMENT` is host-green against simulation, real calls may use ordinary public business/reception numbers found from current public sources.

Use only non-emergency, non-urgent ordinary appointment/business lines.

### Test-only mode

Disclose the test at the **start**, before staff spends time checking schedules or entering data. Ask whether a short AI-assistant test without making a reservation is acceptable.

Do not wait until the end to say the call was only a test. A test-only call must not create or hold a real appointment, ticket, order or other commitment.

### Genuine-task mode

If the operator/user genuinely wants an appointment, the call may execute the real booking task using only authorized facts and the normal proposal/confirmation/commitment path. Do not create a real booking and then retract it merely because it was also a product test.

### Target budget

Default:

- one meaningful call per organization/reception;
- second call only after an early technical failure or explicit agreement to repeat;
- no repeated probing of the same staff to tune wording;
- no broad unsolicited call campaigns;
- retain target source, test mode, call count and structured outcome.

## Repository/cleanup state

Expected remote branches:

```text
main
agent-control
```

A dead temporary worktree registration was pruned. Historical local branch `chat-relay/orange-chatgpt-pump-v1` was intentionally preserved because it contains 21 commits not present on any remote. It has no active worktree and is not part of the continuation path.

Do not perform style-driven refactors of the frozen Samsung path.

## Final invariants

- one active cellular call at a time;
- deterministic path handles known/common turns first;
- LLM/Skills are bounded proposal/classification layers, not authority owners;
- no model-generated target widening or credentials;
- arbitrary model text never bypasses output approval;
- required commitments pass through proposal/confirmation/`CallCommitmentGate`;
- test-only real calls disclose the test before meaningful staff effort or any stateful action;
- genuine tasks may create real commitments only when explicitly user-authorized;
- every physical call cleans up to `IDLE`;
- `HOST_GREEN` is distinct from `PROVEN_S22`;
- frozen media remains untouched during ordinary Gate D work.
