# AI Calls — MVP 0.2.2

On-device GSM assistant for Samsung S22+ / Android 16, with PL/EN profiles, local conversation history, encrypted notes and optional WAV recordings.

[Download AI Calls 0.2.2 (APK)](https://github.com/MichalMatu/ai-calls/releases/download/v0.2.2/ai-calls-v0.2.2.apk) · [SHA256](https://github.com/MichalMatu/ai-calls/releases/download/v0.2.2/SHA256SUMS.txt)

**MVP preview, not a production-qualified dialer.** Previously verified: Polish incoming GSM, notes and WAV. English synthetic speech passed; full English GSM and current outgoing GSM are not yet physically qualified. Use only for authorized calls.

## Install and set up

1. Install the APK on S22+. Configure permissions and, if intended, the default Phone app under **Settings → Permissions and default apps**.
2. In **Settings → GSM audio → Manage pairing**, enable Wireless debugging, pair by code and start the GSM audio host while the phone is idle.
3. In **Model and voice**, download Gemma 4 E2B/E4B, check compatibility, choose conversation language and preview offline voice. App language controls the interface.
4. Set SIM and mode for incoming calls or authorize an outgoing task. Test synthetic flows in the **Offline laboratory**.

[Documentation: MVP status, security and development](docs/README.md).
