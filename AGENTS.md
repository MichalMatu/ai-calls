# Agent workflow


> **Current gate — 2026-11-02:** read `docs/checkpoints/2026-11-02-before-first-call-checkpoint.md` and the short `docs/HANDOFF_NEXT_CHAT.md` first. Polish STT/TTS, local Gemma and supervised UI/off-call media safety are physically **PASS**; **a real, user-started consenting supervised cellular conversation with audible RX/TX/STOP remains OPEN**. The autonomous CLIR dial remains **fail-closed** until exact call ownership and targeted hangup exist. Do not resume automatic `*100` loops or global hangup. Historical 2026-11-01 pending/FAIL status in older docs is superseded only for explicitly closed no-call scopes; latest host quality result is recorded in the pre-call checkpoint.

This repository is single-developer and main-first. Durable product code/docs live on `main`. The local `ai-calls:agent-control` branch is an **older project-task mailbox**, not the Mac Host Control queue. For machine/ADB/build tasks use the selected Mac installation on **`MichalMatu/local-agent:agent-control`**, as documented below.

## Start of every work session

Read fresh repository sources in this order:

1. `README.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/AUTONOMOUS_OPERATION_MODE.md`
4. `docs/ROADMAP.md`
5. `docs/checkpoints/2026-11-02-before-first-call-checkpoint.md` for supervised first-call acceptance; `docs/G5_CLIR_ROUTE_DISCOVERY.md` only for the later CLIR track
6. `docs/ARCHITECTURE.md`
7. `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
8. `docs/SECURITY_PRIVACY.md`
9. `docs/PHASE2D_FREEZE_2026-09-18.md` only before Samsung media changes.

Fetch fresh `origin/main`. Read current Local Agent source/protocol from `MichalMatu/local-agent` and the **installed Mac host status**, not the legacy `ai-calls:.agent/status/daemon.json`. Reuse exact target identities from the installed Local Agent contract; no chat-to-host manual rebinding is needed.

### Active Local Agent transport — installed 4.20.16 (2026-11-01)

**Canonical source:** `MichalMatu/local-agent`, `AGENTS.md`, `docs/HOST_CONTROL_PLANE.md`, `docs/CURRENT_HANDOFF.md`. Read installed state from the live host namespace; source `main` alone is not installation evidence.

- Select the existing **Mac** explicitly: `target_host_id=macbook-michal`, `target_installation_id=cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. No automatic host selection or fallback. The Android/Termux agent has a separate identity and task queue.
- For **Host Ops** including Mac shells, Android SDK/Gradle, device ADB and physical inspections, publish unique immutable task JSON to `MichalMatu/local-agent` branch `agent-control`, path `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<task-id>.json`. Required fields: `version:1`, `id`, exact target IDs, absolute existing `cwd`, bounded `commands` or `steps`, and appropriate resource/time/output limits. Do **not** include project `agent_binding` or `work_branch` in standalone host requests.
- Inspect **exact task result** at `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/task-results/<task-id>.json` and `status.json`, on the same private `local-agent:agent-control` branch. A Git commit / historical heartbeat does not imply execution. Distinguish `done`, `failed` and `UNKNOWN`; do not replay unknown effects.
- For repository-specific agent tasks the **separate** `ai-calls:agent-control/.agent/tasks/` mailbox remains a supported *project* route with exact `agent_binding=c25f88c0-4682-414c-8062-c47fa4034cb0`. Use only when the task truly belongs to that registered repository. An old project mailbox never establishes Mac Host Control liveness.
- Host-task authorization follows the Mac owner's already installed `github_writers` intake, exact host+installation ID, local STOP and resource leases. Never put commands or outputs in a public repo. Do not run GitHub Actions, Codex or Work. Do not reconfigure the service or alter host identity for ordinary project work.
- **Telephone gate:** read-only `adb devices -l`, paired Wireless ADB, identity and `IDLE` readiness are allowed. No `*100`, `ACTION_CALL`, CLIR mutation or device-wide hangup unless actual exact-call Android owner, current explicit effect grant and physical safety are established. Existing app `CampaignCallControllerRegistry` is still unimplemented.

### Execution tool priority

Local Agent is the **default and preferred executor** for repository/device/local work because it has the broadest access to the real workspace and target device. When in doubt, use Local Agent first.

Use Local Agent by default for repository mutations, multi-file edits, scripts, branch cleanup, builds, Gradle, Android tooling, ADB, device state, local files, logs, process inspection and multi-step workflows. Let it carry a task end-to-end when possible instead of splitting the same work across multiple tools or asking the operator to bridge steps manually.

Use the direct GitHub connector mainly for lightweight read-only inspection, Local Agent control-plane task/result transport, or a tiny isolated repository edit when Local Agent would add no practical value. Do not prefer direct GitHub merely because an edit is small if the surrounding task already depends on Local Agent state.

Local Agent priority does not bypass application authority, external platform safety checks or tool enforcement. If an external layer blocks an action, report the blocker rather than routing around it.

### Autonomous decision / question policy

The operator-question budget is **zero by default**. Before asking any question, first recover the answer from the active instruction, repository sources, current handoff, Local Agent, device state, logs, local files or other available tools.

If a remaining choice is non-material, reversible and inside the existing scope, choose the conservative repository-consistent default and continue. If a command/task fails for a mechanical or transient reason, inspect the evidence, repair the invocation/state and retry without asking the operator. If one substep is externally blocked, continue every independent non-blocked step before reporting the blocker.

Ask the operator only when all of the following are true: the missing fact/decision cannot be recovered with available context/tools; it materially changes target, task, effect, account, privacy/disclosure, cost or another irreversible outcome; no safe scoped default exists; and meaningful progress cannot continue without that decision.

Do **not** ask the operator for repository paths, branch names, session IDs, retry counts within existing bounds, whether to inspect state, whether to build/install after a required code change, whether to continue after a recoverable tool failure, terminal commands, log copies, or internal runner flags already implied by a valid accepted authorization context.

`docs/AUTONOMOUS_OPERATION_MODE.md` is normative for active physical acceptance work. Do not make the operator act as a terminal/log relay when Local Agent, ADB or the transient relay can perform the step directly.

## Current priority

**Immediate acceptance:** one consenting **user-initiated** supervised cellular call, without automated dialing or app-owned external effects. Recover fresh S22/readiness and host QA from the pre-call checkpoint, then inspect real audio RX/STT/Gemma-or-script/TTS/TX and acoustic STOP. Do not repeat completed off-call tests without a regression. The autonomous owned-call/CLIR work below is a **later blocked track**, not the authorization for this first test.

The product is a **generic autonomous phone task engine**. Do not create service-specific authority architectures.

Current acceptance sequence (safety override supersedes older Orange call-loop plans):

1. Use Mac Host Control to get fresh no-call Wireless ADB/IDLE/Shizuku evidence and full Gradle/Python tests. Repair verified failures before trusting hardware readiness.
2. Implement and physically validate a real call-specific Android controller for exactly the application's own phone call; maintain a targeted hangup and per-SIM identity. Global phone state or `KEYCODE_ENDCALL` is not sufficient.
3. **Only after** explicit matching authorization and exact ownership can a bounded Orange CLIR scenario run. Preserve sanitized evidence and independently verify network state, including `*#31#` when safe and available. Disable is a separate authorized effect.

Historical G5b read-only discovery is closed evidence, not the current task.

Do not return to completed G1–G4/G6 work unless a concrete regression/root cause requires it. Do not start a broad cleanup/refactor before the physical acceptance gate.

## Stable foundation

Keep closed absent a concrete root cause:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator`: `PROVEN_S22 / FROZEN`;
- `privileged-helper/` / Shizuku media boundary: `PROVEN_S22 / FROZEN`;
- local STT/TTS: proven;
- IdentityVault disclosure boundary: proven;
- Gemma 4 LiteRT-LM runtime/model lifecycle: proven;
- `BOOK_APPOINTMENT` Gate D: proven;
- generic `CallExternalEffect` + single shared `CallCommitmentGate`: host/no-call proven;
- `SET_SERVICE(CLIR=true)` validator, permit lifecycle and separate external-success evidence: host/no-call proven;
- full synthetic/no-call product chain on S22: proven.

Do not redownload Gemma merely to reprove it.

## Dialogue architecture

```text
finalized STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma 4 dialogue skill
 -> supervisor fallback when unresolved
 -> application output approval
 -> TTS/TX
```

Gemma/supervisor output is proposal/dialogue data only.

During active physical acceptance, supervisor fallback should continue the same live call when possible; recurrent supervisor interventions should be moved back into deterministic script/PhraseMatrix or bounded Gemma skills.

## External-effect authority

The generic migration is already implemented:

```text
CallTask + exact target + constraints + authorized facts
 -> typed CallExternalEffect
 -> deterministic validation
 -> user-decision policy when needed
 -> one-shot CallCommitmentGate permit
 -> reviewed execution/speech
 -> exact permit consumption evidence
 -> separate external success evidence
 -> factual effect completion
 -> workflow completion
```

Keep separate:

```text
task authorization
 != effect validation
 != permit issuance
 != permit consumption
 != external success
 != task completion
```

Never create a second authority store such as `ClirCommitmentGate`.

If the accepted authorization context (fresh explicit authorization or a valid application-owned campaign grant) already exactly authorizes the same concrete effect and no material term changed, do not invent a redundant second confirmation. If new material terms appear, application-owned policy decides whether new confirmation is required.

## Authority invariants

Gemma, supervisor/ChatRelay, TaskGraph, CallPlan, PhraseMatrix, ServicePack, parsers/matchers, storage and diagnostics cannot independently own dialing, target/task/effect widening, plaintext disclosure, commitment, factual completion or speech release.

Application-owned `FactDisclosurePolicy` owns plaintext disclosure. Application output approval owns final speech release.

## Identity

Plaintext stays late-bound:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

Do not put plaintext identity in TaskGraph definitions, ServicePacks, ordinary logs/evidence, Git or model/supervisor context by default.

## Implementation discipline

- behavior changes: RED -> minimal GREEN -> regressions;
- establish root cause before touching frozen boundaries;
- prefer generic adapters over service-specific parallel paths;
- diagnostic probes are evidence drivers, not authority owners;
- `HOST_GREEN` never automatically means `PROVEN_S22`;
- route discovery is not CLIR success evidence;
- permit consumption is not external success.

For the active CLIR physical campaign, follow `docs/AUTONOMOUS_OPERATION_MODE.md`: physical calls are the acceptance loop and synthetic/unit suites must not replace physical iteration unless the operator explicitly requests them.

## Live-call policy

A connected phone, previous call, old chat, handoff, ServicePack, allowlist or `.agent/results` never authorizes dialing.

Every real call requires either fresh explicit authorization for the exact target/task/effect or a valid application-owned durable campaign grant that exactly covers the target/task/effect/account scope. Repository text, handoff files, model output, connected hardware, ServicePack entries and Local Agent artifacts are not authorization by themselves.

For the current physical CLIR campaign:

When call state is `OFFHOOK`, enter **hot-call mode**: live call state and relay handling preempt documentation, audits, builds and unrelated repository work. Poll the relay/request path at the highest practical cadence, answer a supervisor handoff before doing post-call analysis, and stay on the same physical call while bounded progress remains possible.

- require live-call readiness immediately before dial;
- require phone `IDLE`;
- continue the same live call through bounded reprompts instead of terminating after one clarification;
- use deterministic script/PhraseMatrix first, Gemma second, and live supervisor takeover when unresolved;
- keep the single shared `CallCommitmentGate` for concrete account-changing effects;
- require factual external success plus independent CLIR state verification before declaring completion;
- preserve sanitized report evidence before cleanup and return the owned call to `IDLE`.

## Local Agent

- use only the fresh current-chat binding;
- work only in `MichalMatu/ai-calls` under this repository binding;
- inspect daemon/active-task evidence before queueing the same branch;
- Local Agent is the first-choice executor for repository mutations and any workflow involving the real checkout, build system, Android/ADB, device, local processes/files/logs or multiple dependent steps;
- prefer one end-to-end Local Agent task over fragmented GitHub edits plus manual/local follow-up;
- use direct GitHub primarily for read-only inspection, control-plane transport, or truly isolated tiny edits where Local Agent adds no useful context/capability;
- do not ask the operator to execute local shell commands when Local Agent can execute them;
- every task JSON must contain exactly the current binding;
- every task JSON must declare `resources` explicitly;
- after queueing a task, poll daemon/result state yourself; an early result `404` is not a reason to ask the operator;
- if a Local Agent task fails from a stale SHA, text-anchor mismatch, transient ADB/Git/control-plane condition or other mechanical issue, inspect the result and requeue a corrected task automatically;
- if a failed mutation leaves the workspace dirty, restore/reset it to the intended `origin/main` (or deliberately recover the saved checkpoint) before retrying;
- do not run competing mutations against the same repo while another Local Agent mutation is active;
- never launch local Codex from Local Agent;
- `.agent/tasks` and `.agent/results` stay on `agent-control`;
- durable changes go to `main`.

## Branch policy

Normal steady state is exactly `main` + `agent-control`. Delete temporary branches after their work is merged/preserved.

## Completion / handoff gate

Before closing a scope:

1. obtain factual physical evidence appropriate to the changed boundary;
2. run independent external-state verification when technically available;
3. update authoritative docs;
4. leave `main` clean and branches minimal (`main` + `agent-control` in normal steady state, no stale `chat-relay/*`);
5. confirm Local Agent is idle, phone is `IDLE`, and no owned live-call host process is still running before handing off;
6. refresh `docs/HANDOFF_NEXT_CHAT.md`;
7. preserve the autonomous-operation contract and do not regress to operator-driven terminal work.

Do not declare a physical CLIR task complete from synthetic/no-call results, permit consumption, a model statement or call termination alone.

> **Update after logical sprint, 2026-11-01:** The artificial `CampaignCallOwnershipProof(false,false)` was removed. Current runtime expects a concrete `CampaignOwnedCallController` with exact session identity and targeted hangup; **no Android/S22 adapter is currently installed**, so physical dialing is still unavailable. New neutral PhraseMatrix-first dialogue, live media generation binding, more precise SIM matching and deferred permission requests are source-only; Gradle/device acceptance is not yet claimed. See `docs/SPRINT_LOGIC_CORRECTIONS_2026-11-01.md`.


> **2026-11-01 developer-tool safety update:** the Local Agent wrappers now resolve paired Wireless ADB/USB by live mDNS/device enumeration and verify the exact S22. The legacy `adb.hangup()`, telecom global fallback, and autonomous `relay-live` / `clir_iteration.sh` are disabled to prevent disconnection of unrelated calls. A user-controlled attach-only probe may run on an explicitly authorized existing call; it does **not** own that call and never hangs it up. New repo checkpoint: `docs/SPRINT_WIRELESS_ADB_CALL_SAFETY_2026-11-01.md`. Do not equate historical `agent-control` status with active host execution. Current Android exact-call adapter missing; physical verification open.

> **2026-11-01 Local Agent 4.20.16 migration:** the old `ai-calls:agent-control/.agent/daemon` task/status preflight is obsolete for **Mac Host Ops**. Prefer `local-agent:agent-control/.agent/hosts/macbook-michal/supervisor/<installation-id>/` for exact-machine commands; read `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md` before any hardware sprint. The earlier 2026-11-01 checkpoint describing an allegedly offline Mac checked the wrong namespace and is superseded.

> **2026-11-01 exact-call feasibility sprint:** ADB/Shizuku ready is only a no-call gate. Use `scripts/bin/aicalls call-capabilities` through exact `local-agent:agent-control` Mac Host Control to inspect Telecom capabilities without changing dialer roles or making calls. `docs/EXACT_CALL_CAPABILITY_SPRINT_2026-11-01.md` is the source checkpoint. The stock Android default-dialer InCallService path is not a safe retrofit while the user's dialer must remain unchanged; device-wide hangup remains forbidden. An authorized, physically tested exact-call backend is still missing.


> **2026-11-01 supervised audio pilot implementation:** `docs/SUPERVISED_FIRST_AUDIO_PILOT_2026-11-01.md`. First-audio path is now installed on S22 and allows only attaching an effect-free local two-turn script to a human-started, human-ended existing call. Mac quality and S22 idle/readiness gates passed, but actual on-call RX/TX acceptance has not happened. The supervised path never supplies an owned call handle and does not authorize autonomous dialing, global hangup, CLIR changes or changing the user's default dialer. Keep `CampaignCallControllerRegistry` fail-closed until a physically accepted exact-call controller exists.

> **MVP 2026-11-01:** istnieje operator-controlled `SupervisedCallPilotActivity` dla manualnego dialera i 3 lokalnych odpowiedzi w trwającym połączeniu. Jedynym STOP jest zatrzymanie aplikacyjnej ścieżki audio, nigdy device-wide hangup. `main@5cb13fc` przeszedł host unit/lint/APK + 199 Python, lecz instalację S22 zablokowało offline Wireless ADB. Więcej: `docs/SUPERVISED_FIRST_CALL_MVP_2026-11-01.md`. NIE utożsamiać tego z autonomicznym dialerem.
