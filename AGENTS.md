# Agent workflow

## 2026-11-03 — historical dialing regression and real Telecom controller source

Git history proves a dial regression: before `6e732af`, `AppOwnedClirCampaignExecutor` used `ACTION_CALL` and generic `TelecomManager.endCall()`. Commit `6e732af` added a constant-false ownership check; `c48685b` introduced `CampaignCallControllerRegistry` without a provider; `a019795` separately removed ADB-script automatic dial/hangup. These were real removed paths, but the old hangup did **not** select one specific Call.

New code `0226d61` → `294cc2d` → `33e27cc` → `3ee183c` implements Android default-dialer-role-gated `InCallService`, `TelecomManager.placeCall`, exact matched `Call.disconnect()`, minimal human call controls and one-tap START continuation after real OS role/notification consent. This is **candidate production code**, not a live-call physical PASS. It does not mutate the Google Phone role without OS user selection.

Mac task `aicalls-20261103-controller-build-cache-adb-recover-v3`: **DONE/0**, `EXACT_CONTROLLER_BUILD_LINT_UNIT=PASS` and `APK_CACHE_READY=true` for `3ee183c`; device disappeared from ADB, so **not installed**. Separate `aicalls-20261103-cached-apk-last-s22-usb-diagnose-v2`: DONE/0, cached APK verified, `S22_TRANSPORT_NOT_AVAILABLE=true`, `NO_INSTALL=true`. Real role grant, live dial, RX/STT/Gemma/TTS/TX, selective live teardown, and `MODE_IN_COMMUNICATION` root-cause acceptance remain OPEN. Detailed checkpoint: `docs/checkpoints/2026-11-03-historical-dial-regression-controller-recovery.md`.


> **Current 2026-11-03 source/phone gate:** STOP now covers supervised audio and an actually owned campaign session, revokes TX promptly and requests targeted release once. Focused unit/build/lint and S22 IDLE installation are PASS on `4c47c308`; later source `4b79a302` adds race fixes and physical two-press STOP-idempotence acceptance **PASS** (Mac `aicalls-20261103-stop-final-regression-s22-v1` DONE/0: focused unit, build/lint, both APKs installed, exact S22 post-test IDLE). Full autonomous dial/in-call RX-TX/owned teardown remain OPEN: `CampaignCallControllerRegistry` has no platform call handle. See `docs/checkpoints/2026-11-03-stop-friction-sprint.md`. Do not infer full-call PASS from an off-call APK install.

## Mission and owner delegation — normative, 2026-11-02

**AI Calls is an autonomous, local-AI telephone task executor, not an app whose final workflow requires the owner to dial, click through dialogue, or hang up manually.** The user assigns a concrete outcome; the on-device agent executes the authorized call(s), understands the counterparty, acts within the user's task scope, establishes an actual result, ends its session and reports back. Ordinary lawful destinations are not inherently restricted to Orange or a fixed developer allowlist. Appointment booking, cancellations, read-only inquiries and user-authorized account/service data operations belong to the same generic task engine.

**Owner's broad standing engineering mandate:** work independently across source, architecture, tests, diagnostics, build, install, device inspection, integration, cleanup and documentation; use the designated GitHub Connector + Mac Local Agent workflow. Within a **concrete already-authorized user task or valid application grant**, handle retry/resume, number selection from the authorized target set, IVR navigation, continued dialogue and termination without repeatedly asking the owner to click or approve an identical step. Own the whole workflow and repair verified defects rather than reverting to a manual supervised pilot as the intended final product. Prioritize a *real* autonomous call → live local AI → factual task outcome → verified teardown acceptance.

**Scope of this mandate:** permission to develop and work autonomously is not a universal action token for arbitrary numbers, other people's accounts, personal-data disclosures, purchases, emergency/premium targets or changes to a materially different service. A live external action still needs a concrete goal, permitted destination and task/effect/account/disclosure scope, with a durable, revocable application-owned grant when required. Observe Android/runtime permissions, call-party privacy/consent, costs and external tool constraints. Never treat this Markdown document, chat prose or model output as a substitute for the application's authority store. Do not use a different tool to evade an external denial.

**No redundant user interaction:** do not ask again for source/branch/device paths, debugging/log collection, running accepted builds, routine non-effectful tests or a repeat attempt fully covered by an unchanged existing authorization. Prefer a single clear application setup/approval control that obtains OS permissions once, then an autonomous task loop. Distinguish product intent from actual capability in reports: `HOST_GREEN` and off-call S22 PASS are not live dial/audio/teardown PASS.

**ZERO zbędnych blokad produktu i pracy deweloperskiej (obowiązująca reguła):** żadnych dodatkowych dialogów po przyjęciu tego samego zakresu zadania, żadnych blokad „tylko Orange” w silniku ogólnym, żadnych stałych wymagań ręcznego wybierania numeru, ręcznej obsługi IVR ani kopiowania logów, żadnych syntetycznych strażników z góry zwracających `false` zamiast implementacji. Każdy produktowy stop bez realnego powodu należy oznaczyć dokładną lokalizacją, usunąć niepotrzebną przeszkodę albo zaimplementować brakującą funkcję i pokryć to testem. **Nie oznacza to pomijania uprawnień Androida, zakresu rzeczywistego zlecenia, zgody na ujawnienie danych ani ochrony przed zakończeniem cudzego połączenia.** Jednoczesna obecność tych kontroli nie jest sprzeczna z pełną autonomią w zatwierdzonym zakresie.


See [product README](README.md), [Architecture](docs/ARCHITECTURE.md), [Autonomous operation](docs/AUTONOMOUS_OPERATION_MODE.md), [Generic authority](docs/GENERIC_PHONE_TASK_AUTHORITY.md) and [Security](docs/SECURITY_PRIVACY.md).



> **Current product/engineering state — 2026-11-02:** AI Calls must autonomously execute user-delegated telephone tasks with the locally running AI. Begin with the current `README.md`, `docs/HANDOFF_NEXT_CHAT.md` and `docs/ROADMAP.md`. Local Polish STT/TTS, Gemma, Shizuku and one-tap no-call hardware tests are PASS. Autonomous live-call dial/AI RX-TX/actual call teardown are still OPEN engineering work, not permanent product prohibitions. Existing Android/media and task-authorization checks must be validated and implemented correctly; do not misrepresent test gaps as product capability. The developer may independently continue all authorized, nonblocked implementation and test work without repeated approval.

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
- **Current execution distinction:** the owner authorizes autonomous engineering and intends app-owned real calls, but the specific application-controlled call dial/teardown mechanism must still be implemented and verified. Read-only ADB inspection and no-effect tests may proceed autonomously. Actual calls and external effects require a concrete scoped authorization and current device/telephony checks. Global device hangup is not an exact-call handle; the existing `CampaignCallControllerRegistry` is still unimplemented. See the latest checkpoint for any external-tool execution block.

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

**Primary product acceptance:** autonomously execute a concrete, authorized telephone task through local AI: select an approved target, dial, conduct a real conversation, obtain factual task evidence, finish/clean up the intended call and return to IDLE. **Next feasible hardware gate:** prove the unresolved in-call audio path using the already-installed one-tap supervised pilot where necessary; this is only a stepping stone, never the final product. Exact-call controller/cleanup is a first-class engineering workstream, not a permanently deferred feature. Preserve previous off-call PASS and separately report the currently blocked real dial/audio/cleanup pieces.

The product is a **generic autonomous phone task engine** that can call a user-authorized ordinary number, resume an interrupted task within its grant, handle IVR and end its own session. Do not narrow its purpose to Orange CLIR or create service-specific authority architectures.

Engineering acceptance sequence for the autonomous product (Orange is one example):

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

## Historical notes

The 2026-11-01 interim experiments and their previous limitations belong to the versioned checkpoint documents and Git history, not to the live product mandate. Do not treat past supervised-test instructions as the definition of AI Calls or claim historical test status is current. The current state and unresolved engineering defects are recorded in [handoff](docs/HANDOFF_NEXT_CHAT.md), [roadmap](docs/ROADMAP.md) and the linked checkpoints.
