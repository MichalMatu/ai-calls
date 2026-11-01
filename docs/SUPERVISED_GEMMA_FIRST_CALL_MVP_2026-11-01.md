# AI Calls — supervised Gemma-first real-call MVP (2026-11-01)

## Root cause of the first user test

The first supervised-call button launched Android `ACTION_DIAL` only. When Google Phone took foreground, AI Calls did **not** attach or listen: a separate explicit `Uruchom AI w trwającej rozmowie` action, live-call checks, and the optional confirmation were required **after manually returning to the app**. The first backend was deliberately a fixed 3-turn audio script, not an LLM. An outgoing call alone was never evidence that the AI started.

## New implementation on main

Reviewed code head: `9017a12f1f85e2e2697ebcb923f462679cf6e5cb`.

- `SupervisedCallPilotActivity` gives exact instructions for returning from Google Phone, checks the local Gemma 4 model, offers Gemma-first or script mode and prompts for explicit audio attachment when a user returns to the app with a live call. Android permissions, Shizuku Binder, call state, and in-call audio mode remain mandatory.
- `ChatRelayLiveCallProbe` accepts `supervisedGemmaEnabled` **only** for an explicitly read-only supervised session. Missing model fails before media starts. It routes the existing cellular RX -> local STT -> Gemma 4 -> reviewed output -> local TTS -> cellular TX; on model error/unsafe output it uses the neutral three-turn script fallback. Report metrics include Gemma and script-fallback reply counts.
- `SupervisedGemmaDialogueBackend` uses existing on-device LiteRT-LM Gemma 4 E2B IT, not an internet API or new model/runtime. A narrow speech policy rejects long/multiline output, personal/account identifiers, URLs, commitment and service-change terms before audio transmission. This is a supervised dialogue pilot, **not** permission for external effects.
- STOP AI, leaving the foreground app, lost telephone readiness or revoked consent stops only the app media and never hangs up a cellular call. Google Phone remains default; no call-specific autonomous owner or targeted hangup has been added.
- Regression: `SupervisedGemmaSpeechPolicyTest` covers positive neutral response and negative secrets, commitments, URLs, numbers, multiline/overlong responses. An initial test exposed Java regex `\\b` behavior with accented Polish; the policy was corrected to Unicode letter boundaries and rerun.

## Verified receipts

- Mac Host Control `aicalls-gemma-supervised-host-20261101-v1`: failed with **one** new test; Android production Kotlin compiled; the failure was a Unicode boundary on `Zgadzam się`.
- Mac Host Control `aicalls-gemma-supervised-host-20261101-v2`: **done / exit 0** on exact `9017a12`. All Android unit tests, Lint, `assembleDebug` and AndroidTest APK assembly PASS. Existing Python/host quality suite **199/199 PASS**. No GitHub Actions.
- Exact S22 read-only task `aicalls-gemma-model-s22-readonly-20261101-v1`: **done / exit 0**; serial/model match, `mCallState=0`, and reviewed Gemma 4 E2B IT file **MISSING** in app's external models directory. No model inferred as ready.
- S22 deploy task `aicalls-gemma-supervised-deploy-s22-20261101-v1`: **actual `adb install -r` = `Success`** with existing data preserved and app launcher `Status: ok`; task **failed afterwards** in the independent UI-smoke script because its Python ADB path was written as a literal shell variable. This is a harness failure after successful installation, not APK build/install failure.
- Separate S22 no-call UI smoke `aicalls-gemma-supervised-ui-s22-20261101-v2` is queued for direct result verification.

## Still open — do not mark complete prematurely

1. Install/download the reviewed Gemma 4 E2B IT model (~2.59 GB) through the existing in-app confirmation, preferably on Wi-Fi. Model readiness should change to READY; do not claim the model is installed on the S22 before that happens.
2. Confirm the new toggle, model readiness text and STOP AI through direct no-call UI smoke.
3. User starts a **consenting** test call manually, returns to AI Calls, approves attach and verifies one real STT/Gemma/TTS/TX exchange. Inspect the sanitized `chat-relay-live-call-report.txt` for Gemma vs fallback counters and PCM RX/TX, not the transcript or personal content.
4. Autonomous dialing and selective own-call termination remain separately BLOCKED by `CampaignCallControllerRegistry`. No global hangup, *100 action, role switch or silent call initiation.

**Acceptance boundary:** code/build and safe APK installation are proved, but a physically heard **Gemma** live conversation is not yet proved.
