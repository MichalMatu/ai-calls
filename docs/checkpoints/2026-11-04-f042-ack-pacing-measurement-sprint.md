# F-042 — acknowledgement PCM pacing and measurement sprint (2026-11-04)

## Problem and scope
The optional, approved quick acknowledgement was serialized with the actual answer, but after writing the acknowledgement PCM the worker always slept `ChatRelayTurnPacing.postTxHoldMs(pcm.size)`. On a blocking cellular TX pipe, the PCM write may already consume most of the clip's playback duration. Waiting the full duration **again** unnecessarily delays the approved full answer. This is a latency defect within the existing F-042 scope, not a new permission or dialogue policy.

The live caller's remote acoustic perception is still unproven. No external call is authorized by this checkpoint.

## Source correction
- `ChatRelayTurnPacing.acknowledgementPostWriteHoldMs(pcmBytes, txWriteWallMs)` reuses the existing `postWriteHoldMs` policy: remaining estimated playback plus 250 ms guard, with at least that guard retained. No extra arbitrary turn cap or wait for model completion.
- `ChatRelayLiveCallProbe.writeQuickAcknowledgement` measures actual synchronous PCM write wall time, records `turn_N_quick_ack_write_wall_ms`, and sleeps the remaining hold only after a complete authorized write. Partial writes or STOP do not schedule this hold. The same serial output worker still prevents acknowledgement/full-answer overlap; the Gemma worker is unaffected.
- New estimated EOS→ack field `turn_N_eos_to_quick_ack_tx_ms` and unambiguous `turn_N_eos_to_full_tx_ms` (retaining `turn_N_eos_to_first_tx_ms` compatibility). These measure local estimated speech end and TX write start, **not far-end audibility**.

Example for 32,000 bytes mono PCM16/16 kHz (1 second clip): TX write 0 ms → hold 1250 ms; TX write 400 ms → hold 850 ms; TX write 1200 ms → hold 250 ms. These are deterministic pacing calculations, **not measured carrier latency improvements**.

## Test ledger
- RED source test commit `c6e69b71c26d441fdcd68d9a522a83c67eeb7d36`: focused acknowledgement pacing assertions added. First isolated Mac RED task `aicalls-20261104-f042-ack-pacing-red-v1` failed before compilation because that disposable clone lacked `ANDROID_HOME`; not accepted as a RED.
- Corrected Mac RED `aicalls-20261104-f042-ack-pacing-red-v2`: **DONE/0**, expected Kotlin unresolved reference to `acknowledgementPostWriteHoldMs` in three new test assertions, and `NO_DEVICE_OR_CALL_ACTIONS=true`. This is the actual RED evidence.
- Implementation `b1546f852e7ed6ac74bf52f15a7cd970ba22425f` and integration `032b6f5138a1c10f8d7f646065a5aa539e2a766a`.
- Mac GREEN/full QA task `aicalls-20261104-f042-ack-pacing-green-full-v1` requested on exact `032b6f5`; result must be read before claiming PASS.
- Read-only S22 preflight `aicalls-20261104-f042-s22-readonly-preflight-v1` requested separately. No call, TTS remote audio or installation is inferred from that request.

## Remaining acceptance
Run only under the existing exact app-owned campaign grant and approved action path. In a real authorized call compare EOS→ack, EOS→full answer, STT/Gemma/TTS and whether the other party actually hears the two phrases without overlap. STOP/revoke and exact own-Call cleanup must remain verified. Keep RAW RX/TX and transcripts private on the device/Mac. F-042 stays **REAL_CALL_ACOUSTIC_PENDING** until independent live evidence exists.

## Verified Mac and device-readiness receipts

- Mac full GREEN `aicalls-20261104-f042-ack-pacing-green-full-v1`: **DONE/0** on the exact application source `032b6f5138a1c10f8d7f646065a5aa539e2a766a`; `ANTI_FRICTION=PASS`, `F042_GREEN_FULL_HOST_QA=PASS`, three successful Android Gradle phases, **248 Python tests PASS**, `host_quality_gate_green=true`, `F042_ACK_PACING_AND_EOS_DIAGNOSTICS=PASS`. No calls, device mutations or private exports.
- First read-only S22 request `aicalls-20261104-f042-s22-readonly-preflight-v1` never started (`task_budget_exhausted`, exit 125). Corrected `aicalls-20261104-f042-s22-readonly-preflight-v2`: **DONE/0**, exact serial/model online (`SM-S906B`), `TELEPHONY_IDLE=true`, dialer role true, app-owned campaign service inactive, grant and continuation preference files readable. `SAFE_TO_CONSIDER_INSTALL=true`. Purely read-only; does not prove that continuation is absent or authorize a call.
- Guarded APK deployment request `aicalls-20261104-f042-s22-safe-apk-deploy-v1` has been submitted separately; its exact result, including its additional no-continuation precheck and before/after preference fingerprints, must be verified before claiming installation. It contains **no dialing or microphone operations**.
