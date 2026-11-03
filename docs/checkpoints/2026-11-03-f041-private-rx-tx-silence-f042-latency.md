# AI Calls — F-041 real private WAV export, F-042 call-response silence (2026-11-03)

## Scope, device and privacy

- Exact Samsung `SM-S906B` / ADB `RFCT70L7E8J`, app `pl.michalmatu.aicallbridge`, current runtime source pinned by prior F-040 `5b8ee947d4f1ba7cb922fcdb95af66851caa2e5a`. GitHub `main` continues to change for documentation/utilities; read current HEAD.
- User explicitly requested copying saved call audio for diagnosis, reporting that the operator disconnected after a long silence.
- **Actual audio, recognized text and private campaign information remain exclusively on the owner's Mac**, under `~/Documents/AI-Calls/private-exports/`. No raw WAV, transcripts, archive paths with private IDs, SIM/account/phone identifiers or audio bytes are published to GitHub or Local Agent output.
- Read-only USB export did not dial, hang up, capture new media, change grant/permissions, or control the running campaign. Multiple status checks observed a live retry call; analyzed only previously **COMPLETE** sessions.

## F-041: verified exporter defect, fixed

On physical S22, `run-as <pkg> ls -d files/call-session-evidence` succeeded, but the original `run-as <pkg> sh -c 'test -d files/call-session-evidence'` failed due to Android shell argument forwarding. The former exporter therefore returned **false `USB_EXPORT=NO_DATA`** even though `GRANT_RECORD_EVIDENCE=true`, `LATEST_REPORT_PRIVATE_AUDIO_RECORDING=true` and session files existed.

Replaced fragile `sh -c` paths in `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh` with:
- `adb shell run-as <pkg> ls -d files/call-session-evidence` for the existing directory;
- `adb exec-out run-as <pkg> tar -czf - -C files call-session-evidence` for binary transfer.

Updated `scripts/tests/test_usb_private_export.py` to validate success when legacy `sh -c` fails on a real directory, along with non-overwrite/privacy and absent-folder behavior. Mac task `aicalls-20261103-f041-export-completed-wav-readonly-v2` **DONE/0**: `Ran 5 tests, OK`, bash syntax **PASS**, `EXACT_S22=true`, `EXPORT=PASS`, archive **5,679,205 B**, chmod **0600** in private owner directory (0700). **Four COMPLETE recorded sessions** were exported with valid TAR. Old first task `aicalls-20261103-s22-private-audio-rx-tx-silence-diagnostic-v1` reporting NO_DATA is historical and superseded. Caller state was active during the successful *read-only* archive; no changes made to it.

**F-041 status: VERIFIED.** User can review the locally saved archive; no new phone call or consent step was necessary to retrieve already authorized data.

## Latest completed real recording: technical findings

- `RECORDING_END_REASON=owned_call_ended`; **3 counterpart STT final results, 3 approved agent turns, 4 RX WAV windows and 3 written TX WAV windows**.
- The fourth RX window lasted **8.80 seconds**, with only **14.3%** of 250 ms windows below -35 dBFS; it contains line energy but no completed STT transcript or subsequent TX before the end marker. This does not independently identify who hung up or prove semantic content.
- Audio persisted as genuine 16 kHz mono PCM WAV. Technical WAV written TX evidence shows frames accepted by uplink writer, but **cannot independently prove remote audibility**.

| Turn | RX clip duration | RX low energy % | Longest RX low-energy window | TX clip duration | STT→approved | Approved→TX | STT→TX |
|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 4.30 s | 35.3% | 1.50 s | 7.16 s | 7,704 ms | 2,214 ms | 9,918 ms |
| 2 | 10.64 s | 53.5% | 3.50 s | 3.53 s | 7,253 ms | 1,252 ms | 8,505 ms |
| 3 | 2.42 s | 60.0% | 1.50 s | 1.42 s | 9,359 ms | 1,264 ms | 10,623 ms |
| 4 | 8.80 s | 14.3% | 0.75 s | no TX | no final STT | — | — |

The elapsed milliseconds come directly from private `timeline.jsonl` event timestamps:
- RX start markers at **1,502 / 23,204 / 46,186 / 60,954**.
- Finished STT at **5,812 / 33,855 / 48,615**.
- Agent approved text at **13,516 / 41,108 / 57,974**.
- TX start at **15,730 / 42,360 / 59,238**.
- Session end at **70,256**.

Previous saved technical report estimated EOS→first TX at **11,663 ms** (first) and maximum **11,935 ms**. These times reconcile with ~8.5–10.6 s STT-final→TX. STT→approved includes actual Gemma/action policy/scheduling and is **not** yet measured separately as pure model inference.

**Interpretation:** There is a demonstrable long quiet interval between operator turn recognition and audible-attempted agent output. The user's observation that the office operator hangs up due to that silence is consistent with measured timings. Do not present it as a carrier-certified disconnect reason: `owned_call_ended` itself encodes loss of call ownership, not caller/hangup intent.

## F-042: newly identified real blocker, unresolved

Live dialogue responsiveness is still far too slow even after earlier F-034 model prewarming reduced *initial* cold start from 47.6 s to 10–12 s. New user-visible problem: no fast procedural acknowledgment while Gemma/action policy and local TTS take ~8–11 seconds after STT. 4th RX was captured without a completed next response.

Track as [F-042 in BLOCKERS.md](../../BLOCKERS.md) **OPEN**. Next engineering stage:
1. Instrument separately local Gemma inference and policy/skill resolution and TTS on real S22; do not assume all STT→approved is model time.
2. Optimize for immediate, approved, appropriate conversational acknowledgment and faster answer *without* bypassing application output approval, injecting unsupported statements, committing effects early or weakening STOP/grant/media ownership.
3. Compare resulting EOS→TX and natural conversation outcomes on an independently permitted actual call; preserve saved recordings locally for non-public acoustic diagnosis.

Do not repeat completed F-041 export/tests or mistake this diagnostic for proof of CLIR success. 
