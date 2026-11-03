# AI Calls — F-040: empty final STT must not end an authorized call (2026-11-03)

## Scope

- Base `main` `7b5f565c28221a80d4e2fed920c907227a4d6446` with F-039 primary START already installed on Samsung `SM-S906B` (`RFCT70L7E8J`), app package `pl.michalmatu.aicallbridge`.
- Existing genuinely observed phone call: 5 turns, RX/TX PCM and local Gemma decisions, but previous-version final `stt_recognition_error_7`; **no physical follow-up call after F-035**, no proof of remote audibility or CLIR effect. DO NOT claim user-facing live task success from host/unit tests.
- External host-initiated cellular call tool request was previously **refused**. Do not resubmit through alternative task/ADB/browser; host QA, safe read-only device readiness and no-call tests are distinct and allowed workflows. No private grants/audio/transcripts/phone numbers in Git.

## Confirmed source defect

`OnDeviceSpeechInput.completeTranscript` emits `empty_transcript` if Android reports an empty final result (including an ended segmented session with no words). `LocalSpeechTextPipeline` prefixes errors with `stt_`, yielding `stt_empty_transcript`. `ChatRelayLiveCallProbe.Run.beginNextTurn.Listener.onError` only retries reasons recognized by `LocalSpeechRecoverableFailure`, previously only SpeechRecognizer 7 (`ERROR_NO_MATCH`) and 6 (`ERROR_SPEECH_TIMEOUT`). Thus a silent/empty final transcription aborted the entire otherwise-authorized call and its pending task, a redundant artificial blocker of the same class as F-035.

## Fix and safety

- `app/src/main/kotlin/pl/michalmatu/aicallbridge/localspeech/LocalSpeechRecoverableFailure.kt`: recognize `stt_empty_transcript` as **transient no recognized words**.
- Reuse existing F-035 call-owned retry path, including `downlinkPump.abandonActiveCapture()`, same turn, live-authorization check, exact media generation, STOP and grant/remote-call checks. No new number dialing, no fresh grant, no retry ceiling and no UI prompt.
- `LocalSpeechRecoverableFailureTest.kt`: assert no-match 7, speech timeout 6 and empty final transcript all recover; named nontransient errors (permissions, recognizer/model, pipe/TTS, malformed/other STT outcomes, call ended) remain errors. Do not accidentally blanket-retry technical failures or conceal failed tasks.
- Canonical tracking: [F-040 in BLOCKERS.md](../../BLOCKERS.md). Change is source-only until Mac QA and separately warranted S22 acceptance. The previously verified F-039 UI remains unchanged.

## Validation

- Mac Host task `aicalls-20261103-f040-empty-stt-final-host-qa-v1` pinned `5b8ee947d4f1ba7cb922fcdb95af66851caa2e5a`, anti-friction, full `scripts/verify_host.sh` and STT route wiring check. **Check result before reporting PASS**.
- No-call S22 readiness task `aicalls-20261103-s22-safe-campaign-livecall-readiness-v1`: evaluate installed campaign grant/role/permission and Shizuku readiness, but **does not start call or alter grant**. Its output must be sanitized.
- When Mac QA passes, install tested source on idle exact S22 with `adb install -r` only and verify preserved grant and continuation preferences. Do not change OS role, dial, capture audio or auto-start service for this verification.
- Real call acceptance remains OPEN. Repeat only if independently permitted and exactly authorized; otherwise state missing real-call evidence without claiming the application itself has a false denial.

## Confirmed Mac and device readiness results

- **Mac host QA done/0**, exact task `aicalls-20261103-f040-empty-stt-final-host-qa-v1` on source `5b8ee947d4f1ba7cb922fcdb95af66851caa2e5a`: `ANTI_FRICTION=PASS`, `FULL_HOST_GATE=PASS`, three successful Gradle phases, **247 Python tests PASS**, `host_quality_gate_green=true`, `F040_WHOLE_STT_RECOVERY_WIRING=PASS`, `F040_ONLY_EXPECTED_FILES=true` (exactly code, Kotlin regression, canonical blocker register). No device action or external call.
- **Read-only Samsung readiness done/0**, task `aicalls-20261103-s22-safe-campaign-livecall-readiness-v1`: `EXACT_S22=true`, `IDLE=true`, `DEFAULT_DIALER_AI_CALLS=true`, `CAMPAIGN_AGENT_READINESS=READY`, `CAMPAIGN_AGENT_START=NOT_REQUESTED`, `CAMPAIGN_AGENT_SCOPE=EXISTING_GRANT_ONLY`; Android RECORD_AUDIO, Shizuku binder and Shizuku permission all ready (`LIVE_CALL_READINESS=true`, failure reason none). No external call and no grant/audio/number exports. **This is readiness, not a completed phone call.**
- **Device F-040 installation queued**: `aicalls-20261103-f040-s22-install-safe-offcall-v1` (pin `5b8ee947d4f1ba7cb922fcdb95af66851caa2e5a`). Safe `adb install -r` only if exact device IDLE and no running campaign, verify authorization + continuation hashes unchanged and re-run *read-only* campaign preflight with `campaign_agent_start=NOT_REQUESTED`. **Read result before claiming deployment.**

## Current status

`SOURCE_QA_VERIFIED` — Mac full host receipt and S22 readiness checks PASS. Device F-040 package installation result PENDING at this revision. Real in-call F-035/F-040 recovery, audible far-end AI and CLIR outcome are still OPEN.
