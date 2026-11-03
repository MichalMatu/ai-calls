# F-042 — parallel neutral acknowledgment + local Gemma latency instrumentation (2026-11-03)

## Evidence and design

User requested a two-track conversational improvement: immediately acknowledge an operator's completed utterance while Gemma produces the actual answer. [Last complete private RX/TX diagnosis](2026-11-03-f041-private-rx-tx-silence-f042-latency.md) established roughly **11.7–11.9 s EOS→TX**, of which **7.3–9.4 s final STT→approved text** and ~1–2 s approved text→TTS TX. Fourth RX segment had no answer before the call ended. The user's observed consultant hangup is consistent with long silence, but `owned_call_ended` alone does not certify which party ended the call. Recorded audio/transcripts remain privately on the Mac, never in Git.

**Why local small Gemma is not necessarily instant like chat:** production uses **on-device 2B Gemma LiteRT-LM** on a Samsung S22, GPU initialization with silent prior CPU fallback, a **new full structured JSON conversation for every turn** with a substantial action-selection system prompt, and waits for a **complete approved JSON and full TTS WAV** before telephony PCM. Interactive chat apps may run much faster hardware and stream the first tokens instead of waiting for an entire classified response and separately synthesized voice. This is a source-level explanation **pending S22 measured backend timings**, not a claim of GPU or CPU being used in the call.

## Implementation (source only until QA/phone proof)

- New `developerrelay/QuickAcknowledgementPolicy.kt`: preapproved neutral Polish phrase **"Proszę chwilę zaczekać."**, optional 300ms delay from the **speech-detected STT capture endpoint**. One per real turn, never on pure silence, never for the supervised read-only pilot and never on revoked grant, ended/stopped call or already available final audio.
- `ChatRelayLiveCallProbe.Run`: starts **one asynchronous, local TTS synthesis** of that phrase per authorized call before turns. This preparation never blocks STT, Gemma or the call and is optional if voice is unavailable. After speech endpoint, Gemma/STT continues normally in parallel; cached PCM is sent only through the **same app-owned output approval policy**, exact media generation, active call and authorized 20ms-frame uplink. Main and quick-ack TX use one serial TX executor; quick-ack playback drains before actual full answer, preventing overlapping speech. STOP revokes outgoing frames promptly. Do not fake task completion or external effect.
- `CallSessionEvidenceRecorder`: filler is persisted separately as `turn-NN-tx-ack.wav` and typed `TX_ACK_PCM` in the private timeline, **not overwriting** approved `turn-NN-tx.wav`; no new recording permission or files in repo.
- `QuickAcknowledgementPolicyTest`: one-per-turn, no supervision, missing clip, invalid authority and no speech, skip when model full audio ready.
- `DialogueActionBackend` and `GateCHybridDiagnostics`: record **model-classifier wall ms** separately per turn as `turn_N_gemma_classifier_wall_ms` with no private transcript or output.
- `Gemma4LiteRtTextBackend.LiteRtGemma4Runtime`: pure Android diagnostic log tag `AiCallBridgePerf` exposes **GPU vs CPU**, GPU fallback exception class, engine ready latency, per-turn Conversation construction time, synchronous generate latency, output character count and sanitized status. No prompt/model answer/path. No engine/provider/action policy or token cap changes.
- `AndroidGemmaActionLatencyProfileTest`: benchmark two synthetic on-device structured JSON action classifications, without Telecom/number, microphone, authorized fact, live campaign, TTS or effects. This will distinguish cold first inference from warm subsequent inference and expose actual acceleration in logs.

No additional approval prompts, arbitrary retry/turn limits, unapproved facts, hidden voice gates or autonomous remote calls are introduced.

## Test and acceptance plan

1. **Host Mac** full `verify_host.sh` (all module tests/build/lint, Python) and `audit_product_friction.py`. Initial `aicalls-20261103-f042-quick-ack-and-gemma-profiling-host-qa-v1` on source `33e94613` **FAILED** with compile-only `generation`/`lease` out of scope in RX capture; the newly introduced signature was promptly fixed in `ChatRelayLiveCallProbe.beginCaptureInputTurn(turn,generation,lease)` and all callsites. Never mark initial failing source PASS.
2. Re-run Mac full source QA `aicalls-20261103-f042-quick-ack-gemma-full-qa-v2` pinned to `c7ca83dbd5e9ff97edb2858c588841bc34254093` (later docs only). **Check result before marking PASS.**
3. After source PASS, on **exact S22**, only when phone is **IDLE** and active app campaign service is not mid-call, install `adb install -r` APK and test APK after checking unchanged authorization/continuation fingerprints and Android dialer role. Run only no-dialing isolated synthetic Gemma action latency test + existing safe UI/role tests where appropriate. Report GPU/CPU and first/warm inference ms; don't conclude remote acoustic behavior from this.
4. Real in-call fast acknowledgment and final audio sequencing need independently user-authorized call evidence. Never start such a call via a previously denied host-control route. Record *only* numerical `quick_ack_tx_elapsed_ms`, `quick_ack_tx_bytes`, `gemma_classifier_wall_ms` and real EOS→TX; keep private WAVs on owner's Mac.

## Source QA and last safety refinement

- `aicalls-20261103-f042-quick-ack-gemma-full-qa-v2` **DONE/0**, pinned `c7ca83dbd5e9ff97edb2858c588841bc34254093`: `PRODUCT_FRICTION_AUDIT=PASS`, `FULL_HOST_QA=PASS`, three successful Android Gradle phases, **248 Python tests PASS**, `host_quality_gate_green=true`, `F042_END_TO_END_SOURCE_INVARIANTS=PASS`, no calls or device mutation.
- The follow-up source-only hardening `895aa726dc4fe9fe18f1090ac0577ea484a4f54d` catches optional TTS prewarm constructor and TX executor submission errors so they **cannot block the existing full answer**. Job `aicalls-20261103-f042-final-no-blocker-qa-v3` launched; read receipt.
- Final change `0be652d4e79ba552708d23d5707733a4770d0f4e` ensures filler is prepared and emitted **only** for explicitly authorized autonomous, app-owned (`!readOnlySupervised && !supervisorRelayEnabled`) campaign calls, never for the independent developer relay or supervised audio test. Job `aicalls-20261103-f042-final-owned-campaign-qa-v4` launched with full host gate and exact source pin. **Read this v4 result before any physical deployment or claim of finished QA.**
- Synthetic policy check in Linux sandbox `F042_PURE_KOTLIN_POLICY_8_CASES=PASS`. This is supplemental and does not replace Mac v4/device tests.
- **S22 has not been updated with F-042 at this checkpoint.** Keep previous installed F-040 source until approved QA. No automatically placed external calls; host's previous tool refusal must not be bypassed.

## Follow-up: final Mac QA and installation with F-043 (2026-11-04)

- Final F-042 safe-source Mac receipt **`aicalls-20261103-f042-final-owned-campaign-qa-v4` DONE/0** pinned `0be652d4e79ba552708d23d5707733a4770d0f4e`: `ANTI_FRICTION=PASS`, `FULL_HOST_QA=PASS`, three successful Android Gradle phases, **248 Python PASS**, `host_quality_gate_green=true`, `F042_AUTHORIZED_APP_OWNED_ONLY=PASS`. v3 optional prewarm/filler failure handling also **DONE/0**. These supersede the original pending status.
- Source F-043 `bcbe0a2a84781bf79033ccbef7293ce5f29dcc47` contains all guarded F-042 quick-ack changes **plus one persistent native Gemma Conversation per phone call**. Full Mac QA on exact F-043 source DONE/0.
- Owner STOP AI confirmed and retained all grants. `aicalls-20261104-f043-s22-post-stop-deploy-two-turn-v3` **DONE/0** installed that exact F-043 APK on real S22; app + test APK PASS and grant/continuation/dialer role intact. S22 synthetic two-turn GPU Gemma **PASS**, conversation reused on second turn; native generate time **6233 ms → 3998 ms**. [F-043 physical checkpoint](2026-11-04-f043-gemma-one-conversation-per-call.md).
- This **does not validate F-042's audible quick-ack in a carrier call**. `turn_N_quick_ack_tx_elapsed_ms`, resulting remote audio, final answer speech overlap and EOS→first spoken acknowledgment remain pending user-led authorized live-call evidence. No host-initiated call or audio upload occurred.

## Current scope/status

**F-042: SOURCE_QA_AND_INSTALL_VERIFIED / REAL_CALL_ACOUSTIC_PENDING.** Final guarded quick-ack source passed Mac QA and is included in the installed S22 F-043 APK; two-turn synthetic Gemma reuse passed. Live quick-ack timing/sound and carrier outcomes remain OPEN. No claim of a user-audible first acknowledgment or achieved subsecond end-to-end latency until physical evidence exists. F-041 private USB export remains VERIFIED.
