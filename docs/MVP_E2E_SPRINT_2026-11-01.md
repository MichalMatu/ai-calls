# AI Calls — supervised MVP E2E sprint, 2026-11-01

## Acceptance target

Test the **existing, manually started and user-supervised** cellular conversation path:
verified S22 -> consented attach to existing call -> Shizuku cellular downlink ->
local Polish STT -> Gemma 4 bounded skill or neutral script fallback ->
application-owned output approval -> Polish local TTS -> cellular PCM uplink ->
STOP / call-loss fail-closed.

This is a three-turn **read-only audio MVP**, not an autonomous infoline/CLIR service task.
No one may infer authorized number, external effect or exact call ownership merely from a
request for generic infoline testing. The Google Phone dialer remains the human call owner.
Never use *100 automation, global hangup, dialer role changes or CLIR mutation in this sprint.

## Physical evidence from current session

- Host receipt `aicalls-mvp-e2e-s22-readiness-20261101-v1`: **done/0**.
  Exact S22 identified, Gemma 4 E2B IT file **2,588,147,712 bytes**, Google Phone
  is default, telephony `mCallState=0` (IDLE), AndroidTest instrumentation package exists.
  System speech-recognition service is from Google TTS; that does not by itself prove
  Polish offline language installation. Android setting `tts_default_synth=null` does not
  by itself prove synthesis failure. A generic Shizuku `pidof` emitted no value; use the
  repository's Binder-aware readiness probe, not this process check, for a final verdict.
- Physical instrumented test `aicalls-mvp-phone-gemma-inference-20261101-v1`: **done/0**.
  App model-readiness contract: **OK (1 test)**. Real on-S22 LiteRT-LM Gemma 4
  JSON dialogue-skill inference: **OK (1 test)**, 30.317 seconds. Both tests ran
  with no cellular call; S22 returned to IDLE. This proves inference, **not**
  supervised Gemma spoken-text release or STT/TTS/audio round-trip.

## Changes made on main

- `AuthorizedPcmUplinkWriter` splits TX into 20 ms PCM frames and rechecks
  live authorization before each write and flush. User STOP immediately revokes
  authorization on the calling thread, even if main-thread cleanup is delayed.
  Already-buffered frames cannot be retracted; verify STOP acoustically in a
  consenting call before claiming full real-time stop safety.
- `SupervisedCallPilotActivity` binds execution to the **exact mode displayed in
  the confirmation dialog** (Gemma or script) and revalidates model readiness.
  Post-confirmation checkbox changes cannot silently switch the approved mode.
- Host JVM regression tests cover authorized complete TX, revocation between
  frames, pre-revoked zero TX and PCM16 alignment.
- Android instrumentation contracts added for offline Polish STT initialization,
  offline Polish TTS -> nonempty 16 kHz PCM16, and a real supervised
  Gemma skill -> exact-app-approved-text route with no cellular call.

## Gates remaining for acceptance

1. Mac host quality result `aicalls-mvp-final-source-qa-20261101-v1`
   on exact product source `aed472dececaef711e2198fd26a995e27c9ad6ba`:
   Gradle unit tests, lint, debug APK, AndroidTest APK and Python tests.
   Do not call it PASS without a terminal receipt.
2. Read-only Binder/media preflight `aicalls-mvp-s22-shizuku-media-preflight-20261101-v1`.
3. Non-destructive installation of the matching APK and AndroidTest APK,
   device execution of the new no-call STT, Gemma-route and TTS tests,
   then a negative no-call UI check. Do not claim this occurred merely
   because an AndroidTest package of an older version exists.
4. A human-consented real call started and ended in Google Phone. In AI Calls,
   select the supervised pilot, explicitly approve attach, inspect sanitized
   report counters `telephony_rx_pcm_bytes`, `turn_1_stt_chars`,
   `supervised_gemma_replies`/`supervised_script_fallback_replies`,
   `telephony_tx_pcm_bytes`, and `chat_relay_live_call_success`.
   Also verify the other party actually hears the audio and STOP leaves the
   cellular call under human control. No recordings/transcripts/identifiers in Git.
5. Autonomous service tasks still need a **real call-specific owner and targeted
   hangup**. Do not remove `CampaignCallControllerRegistry` safety block.


## Sprint execution state at final observation (2026-11-01, 16:27 UTC)

- Exact current GitHub main source before this note: `60838eb5609776674c572bf68b82bd92be6e3ab1` (application code unchanged by this documentation update).
- Four PCM authorization JUnit cases were included in the `7aa1d952` full host Gradle unit suite PASS. No independently verified standalone Kotlin harness receipt is claimed.
- Host tasks were published to the selected Mac: `aicalls-mvp-full-quality-20261101-v1`, `aicalls-mvp-final-source-qa-20261101-v1`,
  `aicalls-mvp-s22-shizuku-media-preflight-20261101-v1` and
  `aicalls-mvp-physical-nocall-stt-gemma-tts-20261101-v1` (the last one checks host quality, updates application and AndroidTest APK with `install -r`, runs bounded no-call tests and verifies Google Phone/IDLE).
- **No terminal results for these tasks have been published yet.** Mac Host Control
  `status.json` remains at `2026-11-01T16:17:16.077281Z` with `state=idle`,
  so its liveness after that instant is **unconfirmed**. Do not claim any of the queued
  builds, installation or physical speech tests PASS or FAIL without exact receipts.
- Most recent physically verified phone state still belongs to the earlier, already-installed APK and no-call Gemma instrumentation. Current source APK has **not been proven deployed**.
- Zero service calls were made. A consensual manual test call is still needed to verify
  remote RX/TX and audible STOP; a claimed full infoline MVP at this stage would be inaccurate.


## Po odzyskaniu wyników (zastępuje wcześniejsze „pending”)

Pełny aktualny checkpoint: `docs/checkpoints/2026-11-01-ai-calls-mvp-e2e-precontinuation.md`. `aicalls-mvp-full-quality-20261101-v1`: **done/0**, exact `7aa1d952`, Gradle/lint/APK + 199 Python PASS. `aicalls-mvp-final-source-qa-20261101-v1` i `aicalls-mvp-physical-nocall-stt-gemma-tts-20261101-v1`: **failed/1** przed budową/instalacją przez nieaktualny literalny SHA, nie błędy aplikacji. `aicalls-mvp-s22-shizuku-media-preflight-20261101-v1`: **failed/1**, rzeczywisty `shizuku_binder_unavailable`. Nowszy APK, testy STT/TTS/Gemma oraz realna rozmowa pozostają niezweryfikowane; wcześniejszy brak resultów był obserwacją historyczną.
