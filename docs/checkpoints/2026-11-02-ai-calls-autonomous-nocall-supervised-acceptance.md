# AI Calls — supervised no-call autonomous acceptance, 2026-11-02

Scope: complete as much of the real S22+ supervised-call flow as possible **without initiating a carrier call, disclosing identities, or taking external effects**, with no operator shell/log handoffs.

## Source and phone

- Product main at task launch: `bc078449020f26cf69b49799fc91ddaaf6c7732c`; app source unchanged since installed `1d58c51e6414363b3227fdbd05f2ec45dba9e9b0` (subsequent changes documentation only).
- Exact physical Galaxy S22+ `SM-S906B` / `RFCT70L7E8J`, connected through USB ADB, Android 16 / One UI 8.
- User 0, Google Phone still default dialer, cellular IDLE at every stage.
- Both Google offline dependencies `com.google.android.as` and `com.google.android.as.oss` still enabled. See `docs/checkpoints/2026-11-02-ai-calls-s22-polish-stt-recovery.md` for **previously completed** Polish TTS->PCM->STT and STT->text backend->approved TTS PASS. Those accepted tests were **not rerun**.

## Fresh physical no-call receipts (Mac Host Control, branch agent-control)

1. `aicalls-s22-autonomous-nocall-gemma-ui-media-20261102-v1`: **done/0**
   - Live readiness probe: `record_audio_granted=true`, `shizuku_binder_available=true`, `shizuku_supported=true`, `shizuku_permission_granted=true`, `live_call_readiness=true`, no failure.
   - `AndroidSupervisedGemmaEndToEndContractTest#supervisedGemmaProducesOnlyAppOwnedSpeechOnRealDevice`: **OK (1 test)**, 14.38s. This test's assertions require `modelReplies=1` and `fallbackReplies=0`, and exact approved speech policy. This verifies actual Gemma dialogue skill and safe utterance selection on phone-local synthetic text, **not** cellular RX/TX or a full combined Gemma speech pipeline.
   - Production call-media coordinator with Shizuku off-call: **`production_media_off_call_smoke=PASS`**, expected `cellular call is not active (AudioManager not IN_CALL)` refusal; states `BINDING>PREPARING>STOPPING>FAILED>IDLE`. Thus the real media backend fails closed off-call and releases its lease, not falsely becoming ACTIVE.
   - Pilot UI: Gemma model **READY**, STOP AI disabled while no session. Initial navigation harness missed the attach control because Android displays Button text in uppercase; this was a harness case-matching issue, **not** missing UI.
2. `aicalls-s22-pilot-no-call-button-rejection-20261102-v2`: **done/0**
   - Corrected case-insensitive UI text match found the exact pilot attach button and tapped it while cellular IDLE.
   - UI displayed `Najpierw rozpocznij rozmowę w zwykłym dialerze.`
   - `SAFE_NO_CALL_ATTACH_REJECTION=PASS`, `STOP_BUTTON_ENABLED_WHEN_IDLE=false`, cellular remained IDLE, no dial, hangup or external effect.

No firmware/code changes, no default dialer or USB/ADB settings changes, no raw calls or audio recordings; only bounded diagnostic activity and harmless UI navigation occurred.

## Acceptance boundary

**Accepted (physical):** local STT/TTS and deterministic approved text pipeline (prior checkpoint); phone-local supervised Gemma approved reply; Shizuku ready; off-call media refusal and cleanup; supervised UI refuses attach in IDLE.

**Not accepted:** active cellular RX PCM -> local STT -> Gemma/script -> approved TTS -> cellular TX perceived by remote party; three actual audio turns; acoustic STOP; automatic exact-call ownership/targeted hangup. A genuinely connected consenting test participant and manually initiated call are indispensable for those measurements. Do **not** attempt an autonomous dial, use a guessed number, change the Google Phone default dialer, automate CLIR or call global hangup to bypass the gate.

## Least-operator next action

The next physical acceptance is a **single user-supervised, consented manual test call** started from Google Phone to a user-chosen, consenting test partner (or another explicitly agreed safe endpoint), followed by the **in-app** confirmation to attach AI. Mac Local Agent may then observe cellular call state and sanitized `chat-relay-live-call-report.txt` counters, but cannot safely infer missing target authorization or remote audibility. A second participant must verify what they hear; STOP turns off AI only, and the user ends their own call.

Do not duplicate any preceding PASS tests absent relevant source/phone changes.
