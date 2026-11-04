# Polish audio quality and Orange acceptance — 2026-11-05

Local Codex executed Git/Gradle/ADB directly on the designated Mac and S22+.
No Local Agent tasks, Actions, new branches or global hangup were used.
Source/install: `da09ea23618ccfb29704f0f52088b1472a28b30c`.

## Development commits recovered

| Module | Original commits | Implemented scope |
|---|---|---|
| M1 | `413676b`, `84c2708`, `de0538d`, `990a8dd` | Typed profile, separate input/dialogue/output providers and settings |
| M2 | `a0d0000`, `bf79f71`, `cb2afd1`, `c06de45` | Parakeet streaming PCM input, pinned model and acquisition |
| M3 | `a201d18`, `1a15f1b`, `8037eaa` | Explicit external Piper Android TTS provider |
| M4 | `fc91c17`, `cce18b1` | Experimental utterance-buffered PCM→WAV→Gemma AudioBytes ASR |

The dialogue model remains Gemma E2B. E4B is declared but not integrated.
M4 is not a completed audio-conversation backend; its selectable profile remains
unqualified. No qualification check was removed to manufacture a result.

## Synthetic speech on physical S22

The new shared test uses three fixed Polish phrases, Android offline TTS PCM16,
real native input providers and word/character edit distance. No real recording,
account data, grant creation or phone call is used. The existing USB console adds
`probe gemma-audio-pl`, `probe parakeet-quality`, `probe piper-pl` and exports
only typed numeric counters. A regression test excludes transcript/unknown fields.

| Provider | Result | Timing/resources |
|---|---|---|
| Parakeet | PASS: 0/16 word edits, 0/127 character edits, one final callback per sample | Init 1.14–2.18 s, inference 0.45–0.84 s; observed max PSS 459,062 kB, battery 32.9°C |
| Gemma Audio before fix | RED: sample 3 repeated the text instruction after the correct transcript, 6/4 word edits | Failure reproduced twice; observed max PSS 2,056,500 kB, battery 32.2°C |
| Gemma Audio after F-056 | PASS: 0/16 word edits, 0/127 character edits, one final callback per sample | Init 4.73–6.96 s, inference 3.13–4.04 s |
| Piper → Parakeet | FAIL before PCM: `tts_set_voice_failed` | Init SUCCESS, advertised Gosia voice present, default voice null |

PSS/temperature maxima are sampled observations, not exact peaks or joint-model
qualification. These error rates describe three synthetic samples, not a natural
Polish corpus. F-056 changes only message ordering: instruction before audio;
the test threshold was not weakened and no output suffix was stripped.

## Piper installation and observed external defect

[Official release](https://github.com/piper-tts-apps/piper-app-android/releases/tag/latest):
`piper-android-1.1-0295dce.apk`, 126,796,105 B, verified published SHA-256
`5e99e1fe3a4e36983f924e68ef05396bdddb31777efb817ea70f00a0d186518e`.
Installed package `dev.ihorshevchuk.piper`, cert SHA-256
`da35a3b17c8b8ed4e9952c1f7e709ba8e9f49d229eddefe91a398073f261ccd1`.
Gosia was downloaded and activated through the Piper UI. Preview did not resolve
the public API failure. F-057 records the source/API diagnosis: custom voice
names are advertised without overriding Android's language-tag-based
`onIsValidVoiceName`. AI Calls retains explicit engine/voice selection and reports
the real error; Piper PCM and live acceptance are not claimed.

## Two real Orange calls

Both calls used the existing exact app-owned grant and the app's READY preflight,
targeted ownership, local dialogue and teardown. No new grant was issued.

| Profile | Installed product source | Duration | Completed STT/model/full-TX turns | First EOS→TX |
|---|---|---|---|---|
| Parakeet → E2B → Android TTS | `418c84e2` baseline | 77.1 s | 1 / 1 / 1 | 35,046 ms |
| Android STT → E2B → Android TTS | F-056 build, committed as `da09ea2` | 165.4 s | 5 / 5 / 5 | 22,447 ms |

The local runner recorded the then-current Git HEAD `63f3d2f`; the fix was in
the working tree during execution and committed afterward. Use the installed
APK hashes below to identify the final deployment. The two calls are observed
integration tests, not a controlled comparison of recognition speed.

The second call included `DISCLOSE_AUTHORIZED_FACT` in the existing SCRIPT path
and an interrupted sixth RX at remote end. This does not close F-048 RAW or
F-047 transcript recovery. F-042 latency remains open. Successful TX writes do
not prove far-end audibility. No factual CLIR effect or independent network-state
verification was obtained, so no external task success is declared.

After each call reached IDLE, the app-owned STOP cancelled pending retries.
Private USB exports passed. Final state: twice IDLE, no campaign/retry, stable
Android STT/E2B/Android TTS profile restored, audio/skills fingerprints unchanged.
The grant-store fingerprint changed during actual attempts (`attempts_used=11`);
do not claim byte-for-byte grant preservation for this live session.

## Source QA and private receipts

705 app JVM tests and 263 Python tests PASS. Debug/AndroidTest APK build,
lintDebug, audio quality audit, anti-friction and diff checks PASS. Piper's
separate physical FAIL remains explicit. Samsung/Shizuku, VAD/ACK, authority and
disclosure implementations were not changed.

- Debug APK: `6ae1b572aba5388bc6cc739c71c78d458c9ddfc1dbee80717d7583759e24b7b5`.
- AndroidTest APK: `30d64e7ade088d46b9a1d7dff760bb2ba38a3e8f5ce9a09de63366c51ce44814`.
- Private receipts: `~/Documents/AI-Calls/local-codex/2026-11-05-audio-engines/`.
- Last private archive (UTC filename): `~/Documents/AI-Calls/private-exports/call-evidence-20261104T233412Z-bzaezEB0.tar.gz`.

Real transcripts/WAV and identifiers remain on the device and private Mac.
The canonical open-status registry remains [BLOCKERS](../../BLOCKERS.md).
