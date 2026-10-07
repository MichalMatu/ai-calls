# Handoff — Orange CLIR physical acceptance

Date: 2026-10-08

## Checkpoint

Repository: `MichalMatu/ai-calls`. Durable code/docs are on `main`; Local Agent transport stays on `agent-control`.

The active implementation checkpoint is PR #20, `work/app-owned-campaign-executor-v1`, which decouples product call execution from ChatGPT/Local Agent/ADB. Always resolve fresh `origin/main` / PR state rather than relying on a copied SHA.

## Proven / closed

- Samsung cellular RX/TX and privileged-helper media path — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- Gemma 4 LiteRT-LM lifecycle — proven.
- Gemma-first dialogue action router — real S22 model probe 8/8.
- generic external-effect authority + single shared `CallCommitmentGate` — host/no-call proven.
- Android Keystore-backed `IdentityVault` — physically proven on S22 by `AndroidIdentityVaultContractTest` on 2026-09-25.
- app-owned local `PHONE` resolver (`IdentityVault -> AuthorizedFactSnapshot -> FactDisclosurePolicy`) — implemented and host-green.
- private app-owned `PHONE` enrollment plus reusable `AuthorizedPhoneFactBackend` — `PROVEN_S22`.
- app-owned durable campaign authorization, exact CLIR-enable scope matcher, current voice-subscription binding and local Campaign tools UI — host-green on the PR head; physical S22 execution pending.
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

The ChatGPT/Local Agent transport dependency has been removed from the product execution design. The Android app now owns the bounded campaign grant, exact scope validation, attempt reservation, readiness check, exact `*100` dial, local script/Gemma runtime, late-bound PHONE disclosure, shared commitment gate and best-effort owned-call cleanup. ChatRelay remains optional developer tooling only.

The current app-owned executor is host-green but not yet `PROVEN_S22`. No accepted factual CLIR-enable success exists yet; the latest independent network evidence still says caller ID is not restricted.

The remaining gate is physical deployment and execution from t## Next tasks — in order

1. Confirm PR #20 exact-head CI is green and merge it to `main`.
2. Build/install the exact merged APK on S22 via deployment tooling only; opening the app is allowed, but do not use Local Agent/ADB to initiate the product call.
3. In `Campaign tools`, authorize the narrow CLIR-enable campaign locally. It must bind exact `*100`, `SET_SERVICE(CLIR=true)`, current default voice subscription, PHONE-only disclosure, bounded attempts and expiry.
4. Start the campaign from the app. The app must own readiness, IDLE check, dial, dialogue, disclosure, commitment and cleanup without ChatGPT/Local Agent/ADB.
5. Preserve only typed/redacted result evidence. Declare enable complete only if Orange factual success and independent `*#31#` both show CLIR enabled.
6. Add/authorize the separately scoped inverse `SET_SERVICE(CLIR=false)` grant only after enable acceptance, then run the same physical acceptance loop.

If app-owned execution exposes a lifecycle or Samsung-specific failure, patch that exact observed failure; do not move dialing back into ChatGPT/Local Agent.

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
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/CampaignAuthorizationGrant.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/CampaignAuthorizationStore.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AppOwnedClirCampaignExecutor.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/CampaignToolActivity.kt`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayProbeActivity.kt`
- `scripts/aicall_tools/calls/chatgpt_relay_live_call.py`

## Start prompt for the next chat

```text
Kontynuuj wyłącznie MichalMatu/ai-calls z aktualnego origin/main. Przeczytaj świeże AGENTS.md, docs/HANDOFF_NEXT_CHAT.md, docs/AUTONOMOUS_OPERATION_MODE.md, docs/ROADMAP.md, docs/CAMPAIGN_AUTHORIZATION_GRANT.md i docs/G5_CLIR_ROUTE_DISCOVERY.md. Product call execution jest app-owned: Campaign tools -> durable exact-scope grant -> readiness/IDLE -> app-owned *100 dial -> local script/Gemma -> late-bound PHONE policy -> shared CallCommitmentGate -> typed/redacted result -> app-owned cleanup. ChatGPT/Local Agent/ADB nie są authority ani product call executor; używaj ich tylko do repo/build/install/diagnostics. PHONE enrollment/disclosure jest PROVEN_S22. Następny gate: exact-head APK -> lokalna autoryzacja kampanii w app -> fizyczny app-owned *100 -> factual Orange success -> niezależne *#31#. Nie przenoś plaintext identity przez Git, Local Agent JSON, trwałe logi ani model/supervisor context.
```
