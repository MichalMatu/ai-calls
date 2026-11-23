# AI Calls 0.2.3 preview

The supported setup is GSM on Samsung S22+ (SM-S906B) with Android 16. The main package provides Wireless ADB and GSM audio. The separate `.s8lab` package is for developer checks.

## Verified builds

- `1a4bec72` on S22+ completed a fresh APK installation, Phone app role approval, pairing by notification and host startup while idle. Gemma 4 E2B passed the compatibility check. The UI confirmed English (US), the selected SIM and AI answering.
- Polish incoming GSM, encrypted notes and WAV segments were verified on 0.2.1.
- PRs #78–#86 passed host tests and nine safe S22+ UI tests. That run covered navigation and settings.
- The [screen map](architecture/UI_SCREEN_MAP.md) describes the Activity inventory.

## Preview release work

Repeat language selection and an authorized GSM call on the release APK. Establish the release signing key and installation path from 0.2.2. See the [release checklist](operations/DEVELOPMENT.md).

The public 0.2.2 APK predates the main-package Wireless ADB setup.

**Outside the MVP:** Shizuku, GitHub Actions, schedulers and `WRITE_SECURE_SETTINGS`.
