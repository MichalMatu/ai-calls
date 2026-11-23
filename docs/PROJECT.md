# AI Calls 0.2.3 — first MVP release

The supported setup is GSM on Samsung S22+ (SM-S906B) with Android 16. The main package provides Wireless ADB and GSM audio. The separate `.s8lab` package is for developer checks.

## Changes in 0.2.3

- Pair Wireless ADB on the phone from the main APK. Reply with the pairing code in the notification shade and start GSM audio from the app.
- Follow ordered steps for the Phone app role, permissions, GSM audio and SIM selection.
- Switch the interface and new conversations between English and Polish. English is the default.
- Follow the illustrated setup guide through model download, compatibility checking and call answering.

[Commit range since 0.2.2](https://github.com/MichalMatu/ai-calls/compare/v0.2.2...main)

## Verified builds

- `f438d52a` passed the full host gate. On S22+, the matching APK switched between both languages, passed both Gemma 4 E2B compatibility checks and restarted the GSM host in IDLE.
- `1a4bec72` completed a fresh APK installation on S22+, Phone app role approval, pairing by notification, host startup and SIM selection.
- Polish incoming GSM, encrypted notes and WAV segments were verified on 0.2.1.
- PRs #78–#86 passed host tests and nine safe S22+ UI tests. That run covered navigation and settings.
- The [screen map](architecture/UI_SCREEN_MAP.md) describes the Activity inventory.

## First MVP release and QA scope

[Download AI Calls 0.2.3 APK](https://github.com/MichalMatu/ai-calls/releases/download/v0.2.3/ai-calls-v0.2.3.apk) · [SHA-256 checksums](https://github.com/MichalMatu/ai-calls/releases/download/v0.2.3/SHA256SUMS.txt) · [Release notes](https://github.com/MichalMatu/ai-calls/releases/tag/v0.2.3). This is the regular GitHub MVP release, not a prerelease. The supported target is Samsung S22+ / Android 16.

Host tests, release packaging and signing checks cover 0.2.3. Physical S22+ acceptance described above is tied to earlier tested commits, and Polish real-call evidence comes from v0.2.1. A full authorized GSM call on the exact published v0.2.3 APK has not yet been separately recorded. Do not claim blanket Android compatibility or production-wide acceptance. Record Call/SIM, incoming audio, STOP/takeover and END_CALL after TTS against the exact distributed APK when completing further phone QA.

The MVP keeps the prior Android Debug signing certificate for upgrade compatibility. This is not a dedicated production release key; move to a managed signing identity through a documented upgrade strategy. See the [release checklist](operations/DEVELOPMENT.md).

The defective v0.2.2 release was withdrawn; its historical git tag is retained for traceability.

[Telephony development priorities](modules/INCOMING_OUTGOING.md#development-priorities)

**Outside the MVP:** Shizuku, GitHub Actions, schedulers and `WRITE_SECURE_SETTINGS`.
