# Handoff — Orange CLIR physical acceptance

Date: 2026-09-25

## Checkpoint

Repository: `MichalMatu/ai-calls`. Durable code/docs are on `main`; Local Agent transport stays on `agent-control`. At handoff the only remote branches are `main` and `agent-control`.

Current code checkpoint before this documentation close-out:

```text
c9b3f3b34cefa39dab062c78b1cde6081bc4ce21
Resolve authorized phone fact on device
```

The final documentation commit will be newer; always start from fresh `origin/main`.

## Proven / closed

- Samsung cellular RX/TX and privileged-helper media path — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- Gemma 4 LiteRT-LM lifecycle — proven.
- Gemma-first dialogue action router — real S22 model probe 8/8.
- generic external-effect authority + single shared `CallCommitmentGate` — host/no-call proven.
- Android Keystore-backed `IdentityVault` — physically proven on S22 by `AndroidIdentityVaultContractTest` on 2026-09-25.
- app-owned local `PHONE` resolver (`IdentityVault -> AuthorizedFactSnapshot -> FactDisclosurePolicy`) — implemented and host-green.
- live endpointing for the CLIR relay — 1.5 s trailing silence / 60 s capture limit.
- latest APK from `c9b3f3b34...` installed on S22; phone returned to `IDLE` after the vault proof.

Do not reopen frozen media or redesign Gemma/authority boundaries without new physical evidence.

## Current factual CLIR state

Real Orange calls have reached the service-number prompt and Gemma correctly selected `DISCLOSE_AUTHORIZED_FACT(PHONE)`. No call produced accepted factual CLIR success. The latest independent `*#31#` evidence still says caller ID is not restricted.

```text
CLIR enabled = false
physical enable task complete = false
```

## Exact blocker

The real service number is not yet available to the app through a production-safe local enrollment path.

The code can resolve `PHONE` locally from encrypted `IdentityVault`, but plaintext must not be transported via Git, Local Agent JSON, durable logs or model/supervisor context. An attempted ADB/Local-Agent plaintext bootstrap was blocked by an external platform safety layer. Do not route around that block.

This is now the only known prerequisite before the next meaningful real `*100` attempt.

## Next tasks — in order

1. Read fresh `AGENTS.md`, this handoff, `AUTONOMOUS_OPERATION_MODE.md`, `ROADMAP.md` and `G5_CLIR_ROUTE_DISCOVERY.md`; inspect fresh daemon/device state.
2. Check whether an app-owned local `PHONE` enrollment path already exists outside the developer relay. If absent, implement the smallest local-only enrollment path. Requirements: encrypted vault persistence, no plaintext logging, no Git/Local-Agent JSON transport, no model context.
3. Add/run a no-call S22 proof of the complete local disclosure path with a synthetic phone value: exact `PHONE` control -> policy `ALLOW` -> vault read -> local approved speech. Keep evidence redacted.
4. Enroll the real service number locally through that app-owned mechanism.
5. With fresh exact call authorization and phone `IDLE`, run one real `*100` iteration. During `OFFHOOK`, handle dialogue/relay live; do not start unrelated work.
6. Preserve sanitized result evidence and independently run the supported `*#31#` state check.
7. Declare enable complete only if Orange factual success and independent restriction state both agree. Then execute the inverse `SET_SERVICE(CLIR=false)` acceptance loop.

If an external platform control blocks a step, do not bypass it. Continue non-blocked work and report the precise blocker only when operator action is genuinely unavoidable.

## Important files

- `AGENTS.md`
- `docs/AUTONOMOUS_OPERATION_MODE.md`
- `docs/ROADMAP.md`
- `docs/G5_CLIR_ROUTE_DISCOVERY.md`
- `docs/GEMMA_ACTION_ROUTER.md`
- `docs/ARCHITECTURE.md`
- `docs/SECURITY_PRIVACY.md`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/GateCHybridDialogueBackend.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayProbeActivity.kt`
- `scripts/aicall_tools/calls/chatgpt_relay_live_call.py`

## Start prompt for the next chat

```text
Kontynuuj wyłącznie MichalMatu/ai-calls z aktualnego origin/main. Przeczytaj świeże AGENTS.md, docs/HANDOFF_NEXT_CHAT.md, docs/AUTONOMOUS_OPERATION_MODE.md, docs/ROADMAP.md i docs/G5_CLIR_ROUTE_DISCOVERY.md, potem sprawdź świeży Local Agent oraz S22. Local Agent ma pierwszeństwo do repo/buildów/ADB. Kontynuuj od blockeru opisanego w handoffie: bezpieczne app-owned lokalne enrollment PHONE -> no-call proof pełnego resolvera na S22 -> realny *100 -> factual Orange success -> niezależne *#31#. Nie przenoś plaintext identity przez Git, Local Agent JSON, trwałe logi ani model/supervisor context i nie obchodź zewnętrznych blokad bezpieczeństwa. Operator-question budget = zero dla recoverable state/mechanical choices.
```
