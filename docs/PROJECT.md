# AI Calls 0.2.3 preview

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

## Preview release work

Repeat language selection and an authorized GSM call on the release APK. The public 0.2.2 APK and the local 0.2.3 candidate use the same Android Debug certificate. Choose a release signer and verify the upgrade or fresh-install path before distribution. See the [release checklist](operations/DEVELOPMENT.md).

The public 0.2.2 APK predates the main-package Wireless ADB setup.

[Telephony development priorities](modules/INCOMING_OUTGOING.md#development-priorities)

**Outside the MVP:** Shizuku, GitHub Actions, schedulers and `WRITE_SECURE_SETTINGS`.
