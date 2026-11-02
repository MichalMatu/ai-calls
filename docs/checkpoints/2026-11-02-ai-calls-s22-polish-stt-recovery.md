# AI Calls — S22 Google on-device Polish STT restored (2026-11-02)

## Verified environment

- Exact physical Galaxy S22+ `SM-S906B`, ADB serial `RFCT70L7E8J`, verified USB.
- Android 16 / One UI 8, phone locale `en-GB`, app recognition `pl-PL`.
- Google recognition provider `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService`; Samsung Google Phone dialer unchanged.
- AI Calls app from `main@1d58c51e6414363b3227fdbd05f2ec45dba9e9b0`, installed preserving data with `adb install -r`.
- Shizuku server previously verified on the device; cellular `mCallState=0` (IDLE) after all tests.

## Problem and fix

Prior STT startup failed with `recognition_error_10` (`ERROR_TOO_MANY_REQUESTS`) even after reboot and enabling Bixby `com.samsung.android.bixby.agent`, `com.samsung.android.bixby.ondevice.plpl`, `com.samsung.android.bixby.ondevice.engb`. Both `en-GB` and `pl-PL` failed independently. The old app also waited 40 seconds without a callback.

The source fix merged via PR #22 (`main@1d58c51`) registers `RecognitionListener` before `checkRecognitionSupport`, catches failures and bounds startup to 10 seconds per phase; host Gradle unit/build passed. Physical check before Google component restoration surfaced `error:recognition_error_10` immediately, proving fail-fast behavior but not provider repair.

A read-only inspection then found these Google components disabled for the owner user (likely from earlier optimization):
- `com.google.android.as` — Android System Intelligence.
- `com.google.android.as.oss` — Private Compute Services.

Both were enabled via `pm enable --user 0`, verified enabled, without clearing any data or changing the recognition provider. **Do not disable these packages again as an optimization without regression testing**. Restoration of the pair is strongly associated with the recovery, but the individually necessary component is NOT isolated.

## Physical acceptance receipts (private Mac Host Control)

Host: `MichalMatu/local-agent:agent-control`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`.

1. `aicalls-s22-google-stt-readonly-dependencies-20261102-v1`: done/0; confirmed both AS/OSS packages disabled, Google STT registered and enabled, no data mutations.
2. `aicalls-s22-restore-google-as-oss-test-stt-20261102-v1`: done/0; both packages enabled/verified, Android STT startup instrumentation `OK (1 test)` with `LOCAL_STT_STARTUP_OUTCOME=ready`, no call.
3. `aicalls-s22-tts-to-stt-production-roundtrip-20261102-v1`: done/0; on-device Polish TTS generated **88,756** PCM16/16k bytes; `stt_ready=true`, `pcm_eof_sent=true`; recognized the generated Polish sentence correctly; **`production_roundtrip_success=true`** and `probe_complete=true`.
4. `aicalls-s22-local-speech-text-tts-complete-20261102-v1`: done/0; on-device synthesized speech **89,598** PCM bytes -> STT transcribed the test sentence -> app-owned deterministic text backend approved response -> Polish TTS output **84,826** PCM bytes; `backend_received_transcript=true`, `backend_complete_response=true`, `approved_output_pcm_nonempty=true`, **`text_pipeline_success=true`** and `probe_complete=true`.

All four tasks were exact-USB-S22 scoped. The last two verified cellular IDLE after the test. No telephone call, audio recording, external effects, dialer changes or destructive storage resets.

## Acceptance limits and next step

- **Accepted**: Polish on-device STT production PCM path; speech -> deterministic complete-text backend -> approved Polish TTS; off-call.
- **Not yet accepted**: supervised *live cellular* downlink/STT/text/TTS/uplink, audible remote-party result and STOP behavior. Deterministic backend test is not the Gemma end-to-end phone acceptance.
- Next: user-supervised, explicitly consented **manually originated** real call and inspect only sanitized PCM counters plus acoustic STOP. Keep Google Phone the human call owner; do not auto-dial, automate CLIR, change default dialer, or issue global hangup. Existing exact-call ownership safety block remains.

Do not rerun the passing offline tests unless a relevant code/device configuration change occurs.
