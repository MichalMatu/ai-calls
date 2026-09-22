# Handoff — stabilized Orange service-pack checkpoint

Date: 2026-09-22

Repository: `MichalMatu/android-ai-call-bridge`

Durable branch: `main`

Local Agent control/evidence branch: `agent-control`

This handoff is a state snapshot, not a prompt. The execution order is authoritative in `docs/ROADMAP.md` and the Orange operating procedure is in `docs/ORANGE_MAPPING_RUNBOOK.md`.

## Start here next session

1. Read fresh `AGENTS.md`, `README.md`, this file, `docs/ROADMAP.md`, `docs/ORANGE_MAPPING_RUNBOOK.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/PHASE2D_FREEZE_2026-09-18.md`, and `service-packs/orange/service_tree.v1.json`.
2. Fetch fresh `origin/main`, fresh `agent-control:.agent/status/daemon.json`, the latest terminal Local Agent result, `git status`, branches and worktrees.
3. Use only the fresh current-session `agent_binding`; never copy a binding from this file or an old task.
4. Do not infer physical-call authorization from repository documentation or old results. A new live call requires explicit authorization in the current operator session.
5. Follow the next-work order from `docs/ROADMAP.md`; use `docs/ORANGE_MAPPING_RUNBOOK.md` for each Orange slice.

## Stabilized checkpoint

The frozen Samsung media foundation remains `DONE / PROVEN_S22 / FROZEN`.

The deterministic Gate C Orange RX -> local STT -> PhraseMatrix -> CallPlan -> application-owned approval -> local TTS -> cellular TX path remains physically proven with `backend_generate_calls=0`.

The generic `serviceintent/` resolver remains provider-neutral and authority-free. No Orange-specific behavior belongs there.

Last code/data checkpoint before the documentation-only stabilization refresh:

```text
40b04c6c269ed327abb0742cb66f6f64b8f2b545
```

That checkpoint contains the persisted final overnight physical evidence (`v264`) and aligned tests.

The full stabilization seal gate passed on documentation/checkpoint HEAD:

```text
54650bc835af0085fa6b0c952d5fdb92b2251936
```

Terminal evidence:

```text
.agent/results/chatgpt-stabilization-checkpoint-v267-20260922.json
```

`v267` proved:

```text
46 Orange Python tests: OK
bash scripts/verify_host.sh: GREEN
no privileged-helper/ or audio-bridge/ changes since code/data checkpoint
no serviceintent/ changes since code/data checkpoint
remote branches: agent-control, main
FINAL_CALL_STATE=0
STABILIZATION_CHECKPOINT_VERIFIED=true
```

The only post-seal repository change is this documentation-only handoff refresh. Always use fresh `origin/main` rather than assuming any hash in this file is still HEAD.

## Orange service tree state

Source of truth: `service-packs/orange/service_tree.v1.json`.

Current physically verified nodes:

```text
orange.root
orange.root.reprompt
orange.activation.clarification_barrier
```

Current summary:

- 19 physically `VERIFIED` observed root edges;
- 16 service seeds;
- all 16 service seeds remain `DISCOVERED`;
- no complete service route has `service_route_verified=true`;
- `orange.network.manual_selection` is closed at the activation clarification barrier;
- all other currently closed reviewed informational/diagnostic wordings ended in a known root reprompt.

Do not confuse a `VERIFIED` observed edge with a verified service route.

## Latest physical evidence

Latest bounded physical task:

```text
chatgpt-orange-caller-id-restriction-info-live-v264-20260922
```

Exact reviewed speech:

```text
Jak działa zastrzeganie numeru?
```

Observed sequence:

```text
known exact preroll: orange
 -> bounded root-acquisition retry
 -> verified daytime Max root
 -> exact reviewed speech once
 -> backend_generate_calls=0
 -> OBSERVE_ONLY
 -> known root reprompt
 -> hangup/cleanup
 -> FINAL_CALL_STATE=0
```

Persisted result:

```text
orange.root.caller_id_restriction_info
  from: orange.root
  to: orange.root.reprompt
  status: VERIFIED
  observed_outcome: REPROMPT
  service_route_verified: false

orange.caller_id.restriction_info
  status: DISCOVERED
  last_outcome: REPROMPT
  next_evidence: closed_after_reviewed_root_reprompt; service_route_not_verified
```

The exact reviewed wording `Jak działa zastrzeganie numeru?` is closed. Do not repeat it merely for another identical reprompt.

## Closed reviewed root wordings

The service tree is authoritative, but the currently closed reviewed actions include:

```text
Jakie sprawy możesz załatwić?
Chcę sprawdzić fakturę.
Faktura.
Mam problem z internetem.
Roaming.
Chcę sprawdzić ceny w roamingu.
Awaria.
Mam problem z Wi-Fi.
Chcę sprawdzić zasięg.
Jak skonfigurować internet w telefonie?
Jak skonfigurować MMS w telefonie?
Jak włączyć ręczny wybór sieci operatora?
Nie mogę wysyłać SMS-ów.
Podczas rozmów zanika głos.
Nie mogę wykonywać połączeń.
Nie mogę odbierać SMS-ów.
Nie mogę odbierać połączeń.
Nie działają mi dane komórkowe.
Jak działa zastrzeganie numeru?
```

Manual network selection is closed at an activation barrier; the remaining listed diagnostic/informational variants are closed after their physically observed root outcomes.

## Root acquisition evidence

Exact bounded ignorable preroll fragments currently persisted/implemented include:

```text
orange
jakości orange
5g jakości orange
kości orange
wielkości orange
g jakości orange
wie jakości orange
```

These are root-acquisition diagnostic exceptions only. Do not broaden them into fuzzy semantic rules. `OBSERVE_ONLY` never speaks.

## Repository cleanup state

The intended **remote** branch set is exactly:

```text
main
agent-control
```

No temporary remote product/experiment branch is required for continuation.

Local cleanup task:

```text
.agent/results/chatgpt-stabilization-local-cleanup-v268-20260922.json
```

`v268` pruned a dead temporary worktree registration. It also inspected local branch `chat-relay/orange-chatgpt-pump-v1` and found 21 commits not present on any remote, so that local historical branch was deliberately preserved rather than deleted. It has no active worktree and is not part of the continuation path. Do not delete it casually until those unique commits are intentionally archived or judged disposable.

The obsolete paste-ready overnight prompt has been removed. There should be no paste-ready prompt in the repository. The durable procedure is `docs/ORANGE_MAPPING_RUNBOOK.md`.

Do not perform style-driven refactors of the frozen Samsung path. The growing Orange reviewed-action wiring may be made data-driven later only if it becomes a demonstrated maintenance bottleneck and all typed-ID/reviewed-speech/CallPlan/output-approval/fail-closed invariants remain unchanged.

## Next work

Do not invent a new sequence from this handoff. Follow `docs/ROADMAP.md` and `docs/ORANGE_MAPPING_RUNBOOK.md`.

The next session begins with fresh-state/checkpoint verification, then selects one **new** low-risk unclosed informational/diagnostic seed. It must not repeat any closed reviewed wording above just to collect another reprompt.

Public Orange support material may create a `DISCOVERED` seed only. A future live session must stop at auth, credentials, customer-data, payment, purchase, activation/deactivation, tariff/contract/package change, ticket creation or other commitment barriers and must never invent credentials.

## Final invariants

- one active cellular call at a time;
- deterministic reviewed route speech only;
- no model-generated runtime IVR speech;
- `OBSERVE_ONLY` never speaks;
- `backend_generate_calls=0` on deterministic route tests;
- no invented credentials or sensitive customer data;
- no purchase, activation, tariff/contract/package change or other commitment merely for discovery;
- every created physical test call cleans up to `IDLE`;
- observed edge `VERIFIED` is distinct from service route `VERIFIED`;
- `HOST_GREEN` is distinct from `PROVEN_S22`;
- `privileged-helper/` and frozen Samsung media remain untouched during ordinary Orange mapping;
- Local Agent control/evidence traffic stays on `agent-control`;
- durable product code/data/docs stay on `main`.
