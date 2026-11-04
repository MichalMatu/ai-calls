# AI Calls — USB debug S22 acceptance, 2026-11-04

## Source and installation

- Current source and installed application + AndroidTest APK: `418c84e2a082930b58673a383969f7293ccd0168`. `main`, commits `[skip ci]`; no branches, force push, Actions, Codex or Work.
- Host: exact Mac `macbook-michal` / installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, Local Agent 4.20.16. All receipts are immutable in the matching `MichalMatu/local-agent:agent-control` Host Ops task-results.
- Phone: exact Samsung S22+ `SM-S906B` / `RFCT70L7E8J`. Transport was rediscovered through live ADB. Two IDLE checks, default-dialer role, inactive app-owned campaign and no pending retry were enforced before install/instrumentation. No calls originated.
- Build and signing: `aicalls-20261104-usb-debug-bridge-host-qa-v2` DONE/0: Python diagnostic unit, Android unit/lint/debug APK/AndroidTest APK, anti-friction and static audio QA PASS; both APKs staged privately on Mac. `aicalls-20261104-usb-debug-apk-signature-readonly-v1` DONE/0: APK SHA-256 and cert match to installed app PASS.
- Guarded in-place installation: `aicalls-20261104-usb-debug-s22-install-smoke-v1` DONE/0: production/test APK update without clear data, AndroidUsbDebugPanelTest PASS, ADB nonce-bound JSON status PASS, grant/skills/audio fingerprints unchanged, IDLE after.

## Off-call USB debug functional acceptance

| Capability | Host Ops receipt | Result |
|---|---|---|
| Status, redacted error counters, evidence count and readiness for Android STT/E2B/TTS and Parakeet | `aicalls-20261104-usb-debug-s22-readiness-v1` | DONE/0 PASS |
| Reject unqualified Gemma Audio Direct profile; write/read back Parakeet profile; reset stable Android STT → Gemma E2B → Android TTS | `aicalls-20261104-usb-debug-s22-profile-mutability-v1` | DONE/0 PASS; grant and skills unchanged |
| Existing Mac-only private USB export of recordings and transcripts | `aicalls-20261104-usb-debug-s22-private-export-e2e-v1` | DONE/0 PASS; 419 archive entries, timeline and WAV, integrity and private permissions PASS; no content published |
| Native off-call Android STT and TTS instrumented tests | `aicalls-20261104-usb-debug-s22-android-speech-probes-v1` | DONE/0 PASS 2/2, private settings unchanged |
| First Polish TTS → PCM16 16 kHz → Parakeet lexical-overlap instrumented test | `aicalls-20261104-usb-debug-s22-parakeet-polish-v1` | DONE/0 PASS, no real call; **not** WER/CER or physical conversation |
| Piper engine package installed? and resource baseline | `aicalls-20261104-usb-piper-thermal-readonly-v1` | DONE/0; Piper APK **ABSENT**, MemAvailable ~2661 MiB, battery 29.9°C; thermal status not available |
| Main activity/background/crash triage | `aicalls-20261104-crash-readonly-triage-v1`, `aicalls-20261104-crash-launch-repro-v1`, `aicalls-20261104-crash-usb-probe-ui-interruption-v1`, `aicalls-20261104-crash-profile-check-ui-repro-v1` | DONE/0; process stays up, main screen foreground when opened, no FATAL exception reproduced |

These are `PROVEN_S22_OFFCALL` outcomes only. Four-turn Gemma direct had earlier S22 synthetic PASS on prior acceptance; the new isolated four-turn invocation was **not run** during this sprint because the controlling tool rejected the proposed task. Do not treat this as a new pass or a runtime failure. Gemma Audio Direct remains disabled. Piper still needs its legitimate external package and Polish offline voice; no unverified package was installed. Polish WER/CER, final segment ordering and Parakeet+Gemma joint RAM/thermal/latency are still OPEN.

## Boundaries and next small step

- Keep all real RX/TX/WAV and transcripts only on S22 and privately on the Mac; never in Git history, Host Ops results or normal logcat. The USB bridge has no grant creation/widening, permission bypass, STOP override or arbitrary preference-write operation. Owner-facing grant/revoke UI still owns authorization.
- Current in-app path: Main → **Diagnostyka developerska** → **USB / ADB — diagnostyka**. The panel's automated UI smoke test passed; user visual/manual check of this new screen is still pending.
- No reason to repeat completed Mac tests, STT/TTS/Parakeet synthetic QA, private exporter, or already validated profile write/reset. Next: have the owner open the USB debug panel and inspect the actual screen; observe ADB/PID/crash only if needed. If an external tool denies the isolated Gemma instrumentation, do not reroute around the refusal. The 4-turn rerun stays pending.
- Keep `BLOCKERS.md` the unique blocker status registry; existing F-045/F-047/F-048 and lack of independently proved CLIR effect remain OPEN; F-049 remains withdrawn. No new application crash was reproduced.
