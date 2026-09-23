# Agent workflow

This repository is single-developer and main-first. Durable product code and current documentation live on `main`; `agent-control` is only Local Agent task/result traffic.

## Start of every work session

Read fresh repository sources in this order:

1. `README.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/ROADMAP.md`
4. `docs/ARCHITECTURE.md`
5. `docs/SECURITY_PRIVACY.md`
6. `docs/HANDOFF_PROTOCOL.md`
7. `docs/PHASE2D_FREEZE_2026-09-18.md` before any Samsung media change
8. Orange ServicePack/runbook only if Orange-specific work is explicitly resumed.

Fetch fresh `origin/main` before writes. If Local Agent / Local Chat Bridge is used, fetch fresh `agent-control:.agent/status/daemon.json` and use only the current chat's fresh immutable binding.

Do not create a status document for every experiment. Keep durable decisions in authoritative docs and detailed evidence in Git / `.agent/results`.

## Current priority

Gate D `BOOK_APPOINTMENT` remains `DONE / HOST_GREEN / PROVEN_S22 / MERGED`.

Gemma 4 dialogue resilience, direct no-call inference, application-owned SAF lifecycle, provider cleanup, model readiness, immutable acquisition source, streaming downloader and explicit download lifecycle are now **HOST_GREEN / PROVEN_S22**.

The full reviewed 2,588,147,712-byte network acquisition was physically executed on the S22 through the explicit two-step product UI. Staging grew during the transfer, `Gemma4ModelInstaller` atomically replaced the active model, the final SHA-256 exactly matched the pinned catalog identity, app-owned ownership/SELinux remained correct, staging was absent afterwards, UI readiness returned `READY`, and a synthetic no-call Gemma skill inference remained green.

There is no remaining model-acquisition proof gate. Choose the next roadmap scope explicitly; do not infer that a live call is next or authorized.

Do not start a live call without fresh explicit target/task authorization.

## Dialogue architecture

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM: existing deterministic CallPlan/TaskGraph owner path
 -> unresolved/ambiguous/cold: bounded Gemma 4 dialogue skill classifier
 -> app-owned skill policy selects reviewed response
 -> local model failure / low confidence / TAKE_OVER: injected-response / ChatRelay fallback
 -> application output approval
 -> TTS/TX
```

`PhraseMatrix` bands remain `HOT / WARM / UNCERTAIN / COLD / AMBIGUOUS`. WARM may route only when one candidate wins the reviewed threshold/margin; ambiguity remains fail-closed.

## Gemma 4

Gemma 4 is the only target local model unless the user explicitly reopens Qwen.

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Do not revive the disproven Edge Gallery HTTP `127.0.0.1:8080` path. Runtime is direct in-process LiteRT-LM through `Gemma4LiteRtTextBackend` with an app-owned model file.

Edge Gallery/ADB may be used only as development sources for bytes, never as production runtime authority or a required dependency.

## Application-owned model lifecycle and readiness

The reviewed import boundary is:

```text
Android SAF source
 -> AndroidGemma4ModelImporter
 -> Gemma4ModelInstaller
 -> app-owned sibling staging file
 -> streaming pinned SHA-256
 -> flush + fsync
 -> atomic same-filesystem replacement
 -> active app-owned model path
```

Import/activation is fail-closed and `PROVEN_S22`. Ordinary readiness is deliberately cheap and uses the pinned catalog identity plus expected file metadata rather than hashing 2.6 GB every turn:

```text
MISSING / INVALID / READY
```

`LOCAL_GEMMA_4` product call preparation checks this readiness before STT/TTS and before backend construction. UI exposes the same semantic state. Full SHA-256 remains an import-time verification boundary. Developer diagnostic factories are not product readiness owners; merely constructing a Gemma backend still does not create a LiteRT engine.

## Reviewed model acquisition

The current reviewed candidate source is:

```text
repository=litert-community/gemma-4-E2B-it-litert-lm
revision=6e5c4f1e395deb959c494953478fa5cec4b8008f
file=gemma-4-E2B-it.litertlm
license=apache-2.0
auth=none
```

Never use mutable `main` as model identity. `Gemma4ModelAcquisitionCatalog` may identify a reviewed network source, but `Gemma4ModelInstaller` still owns expected bytes, SHA-256 verification, fsync/staging cleanup and atomic activation. `Gemma4ModelDownloader` must stream; do not buffer the model in memory. Redirects must remain HTTPS and on the reviewed Hugging Face/CDN host family.

The downloader is exposed only behind an explicit two-step product UI: the first button opens a source/license/size/revision confirmation and only the separate positive action starts transfer. Progress and cancellation are implemented; SAF import and network download share one operation gate. Retry intentionally restarts from byte 0.

The confirmation/cancel boundary and the full reviewed network acquisition are `PROVEN_S22`. Physical evidence includes streamed staging growth, atomic replacement to a new inode, exact final size `2588147712`, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, app-owned SELinux/UID, no remaining `.importing` file, UI `READY`, and a green post-download no-call Gemma inference.

## Skills

Dialogue Skills are bounded model output, not authority. Gemma may return only typed `skill/confidence/reason`; the application owns allowed skills and exact reviewed speech.

Skills/model/model import never bypass `CallWorkflow`, `CallPlan`, output approval, `FactDisclosurePolicy`, `CallConfirmationPolicy`, `CallCommitmentGate` or factual completion ownership.

## Product knowledge/data layers

Keep these distinct:

- `TaskGraph` = bounded task state/transitions/slots;
- `ServicePack` = counterparty/service knowledge and evidence;
- `IdentityVault` = encrypted durable personal values;
- `DialogueState` = transient validated facts learned in the current call.

ServicePack/model/matcher confidence never creates authority.

Plaintext identity remains late-bound:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> exact task / target / state / generation
 -> optional user approval
 -> ALLOW
 -> resolve plaintext late
```

## Authority invariants

Keep existing owners; do not create a second authority store.

- `CallTask` owns task/constraints/preferences/authorized scope.
- `CallResolvedTarget` is concrete but cannot widen a dial allowlist.
- `CallWorkflow` owns proposal/user-decision state and terminal outcome.
- `CallConfirmationPolicy` evaluates one typed proposal.
- `CallCommitmentGate` owns one exact one-shot commitment permit.
- `FactDisclosurePolicy` owns personal-data disclosure decisions.
- application output approval owns final speech release.

Gemma, model import/storage, matchers, TaskGraph supervisors, ServicePacks, Skills, parsers and injected-response tooling do not independently own dialing, target widening, plaintext disclosure, user confirmation, commitment, completion or speech release.

## Frozen boundaries

- Samsung cellular RX/TX and `CallMediaSessionCoordinator`: `DONE / PROVEN_S22 / FROZEN`.
- `privileged-helper/` and proven media path: do not modify without a separate media root cause.
- Gate D proposal/confirmation/commitment/completion ownership: finished; reopen only for a proven regression.
- IdentityVault Android encryption boundary: proven; do not bypass it.
- Orange exact-phrase mappings are diagnostic fixtures, not a stable IVR API.
- Interactive ChatRelay is developer/injected-response fallback infrastructure, not product runtime authority.

## Implementation discipline

- Behavior changes use RED -> prove intended failure -> minimal GREEN -> regressions.
- Establish root cause before fixing.
- Prefer small cohesive modules over broad abstractions.
- Diagnostic probes are evidence drivers, not product runtime owners.
- Resumed speech/newer generations must invalidate stale model/candidate output.
- `HOST_GREEN` is never automatically `PROVEN_S22`.
- Do not reprove or rewrite frozen cellular/media behavior for a model/storage-only change.

## Live-call policy

A connected phone, old chat, handoff, ServicePack, previous allowlist or `.agent/results` never authorizes dialing.

Every real call requires fresh explicit authorization in the current session for one concrete target/number and one concrete task.

For test-only public business/reception calls, disclose AI/test purpose at the start and obtain consent; stop if declined. Genuine user-authorized tasks must stay within authorized facts and normal workflow/commitment/completion owners.

Do not use emergency, crisis, urgent-care, premium-rate or unrelated critical-service numbers for testing.

## Local Agent / Local Chat Bridge

`MichalMatu/local-agent` is an execution worker, not source of truth.

- Work only in the exact bound repository.
- Never infer another repository or copy an old `agent_binding`.
- Every Local Agent task must contain the fresh current binding exactly.
- Check fresh daemon/current-task evidence before queueing or writing the same worktree.
- Do not aggressively poll healthy long tasks; require terminal evidence.
- Use direct GitHub edits for exact reviewable diffs.
- Use Local Agent for Gradle/Android/ADB/device/local commands.
- `.agent/tasks` and `.agent/results` stay on `agent-control`.
- Never launch local Codex from Local Agent.

## Branch policy

Work directly on `main` unless temporary isolation is genuinely required. Preserve only branches with intentional unique work.

## Completion / handoff gate

Before declaring a slice complete:

1. run targeted tests;
2. run `bash scripts/verify_host.sh` for code behavior;
3. run only the physical gate required by the changed device/model/dialogue boundary;
4. distinguish `HOST_GREEN` from `PROVEN_S22`;
5. update authoritative docs when gate/continuation meaning changes;
6. leave `main` clean;
7. never carry live-call authorization into a new chat.

For a new chat, refresh `docs/HANDOFF_NEXT_CHAT.md` and `docs/NEXT_CHAT_PROMPT.md` according to `docs/HANDOFF_PROTOCOL.md`.
