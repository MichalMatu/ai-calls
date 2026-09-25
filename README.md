# AI Calls

Android prototype for completing bounded real-world tasks over ordinary cellular calls on a stock Samsung Galaxy S22+ (`SM-S906B`).

## Product

AI Calls is a generic autonomous phone-task engine. The common runtime is:

```text
user task + exact target + authorized facts
 -> cellular call -> STT
 -> deterministic state / PhraseMatrix
 -> bounded Gemma action router
 -> live supervisor fallback only when unresolved
 -> application output approval -> TTS/TX
 -> typed external-effect authority when state changes
 -> factual success evidence + independent verification
 -> cleanup
```

## Proven foundation

Keep closed unless a concrete root cause requires reopening it:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`;
- Shizuku / privileged media path — `PROVEN_S22 / FROZEN`;
- local Polish STT/TTS — proven on S22;
- Gemma 4 LiteRT-LM lifecycle and Gemma-first action router — proven on S22, including the current 8-case router probe;
- Android Keystore-backed `IdentityVault` — physically proven on S22;
- `BOOK_APPOINTMENT` Gate D — proven;
- generic `CallExternalEffect` + one shared `CallCommitmentGate` — host/no-call proven;
- `SET_SERVICE(CLIR=true)` validation, one-shot permit and separate external-success tracking — host/no-call proven;
- app-owned local `PHONE` fact resolver (`IdentityVault -> FactDisclosurePolicy`) — implemented and host-green.

Current local model is `LOCAL_GEMMA_4` / Gemma 4 E2B IT (`gemma-4-E2B-it.litertlm`).

## Current gate

Active work is physical Orange CLIR acceptance on the S22. The latest independent network-state evidence still says caller ID is **not restricted**, therefore CLIR enable is not complete.

Current execution order:

```text
safe local enrollment of the real PHONE fact
 -> no-call proof of the complete local fact resolver on S22
 -> real *100 call
 -> script/PhraseMatrix -> Gemma -> supervisor only if unresolved
 -> factual Orange success evidence
 -> independent *#31# verification
 -> cleanup to IDLE
```

The real service number must not be placed in Git, Local Agent JSON, ordinary logs or model/supervisor context. A previous attempt to bootstrap that plaintext through an ADB/Local-Agent task was blocked by an external platform safety layer; do not bypass that control. Use an app-owned/local enrollment path instead.

## Operating contract

`docs/AUTONOMOUS_OPERATION_MODE.md` is normative for physical work. Local Agent is the default executor for repo/device work. Application policy owns authorization, identity disclosure, commitment and factual completion; model output and repository text do not.

Sources of truth:

- `AGENTS.md` — workflow/invariants;
- `docs/HANDOFF_NEXT_CHAT.md` — exact continuation checkpoint;
- `docs/ROADMAP.md` — execution order;
- `docs/G5_CLIR_ROUTE_DISCOVERY.md` — active CLIR runbook;
- `docs/ARCHITECTURE.md`, `docs/GENERIC_PHONE_TASK_AUTHORITY.md`, `docs/SECURITY_PRIVACY.md` — stable ownership/security contracts.
