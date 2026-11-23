# AI Calls

**An on-device AI phone assistant for Android.** AI Calls can help answer incoming calls, prepare authorized outgoing call tasks, run local speech and language models, and keep a private record of conversations.

**MVP preview · Samsung Galaxy S22+ (SM-S906B) · Android 16**

[Download AI Calls 0.2.3 (APK)](https://github.com/MichalMatu/ai-calls/releases/download/v0.2.3/ai-calls-v0.2.3.apk) · [SHA-256 checksum](https://github.com/MichalMatu/ai-calls/releases/download/v0.2.3/SHA256SUMS.txt) · [Release notes](https://github.com/MichalMatu/ai-calls/releases/tag/v0.2.3)

> **Preview, not a production-qualified dialer.** On-device GSM transport has only been developed for the specified S22+ / Android 16 configuration. Polish incoming GSM, encrypted notes and WAV have earlier physical evidence; live English GSM and outgoing flows still require acceptance on this build. Only use calls you own or are authorized to handle.

## What can it do?

- **Incoming calls:** choose AI or manual answering, assign a SIM and take over an active AI-managed call using STOP/takeover.
- **Outgoing tasks:** prepare a purpose, recipients, SIM and time window, then explicitly approve the call (experimental; no automatic dialing by installing the app).
- **Private conversation history:** locally saved history, encrypted notes and optional WAV recordings, with controls to disable recordings.
- **On-device AI:** downloaded Gemma models, configurable conversation instructions/skills, local speech recognition and voice output, language choices for Polish and English.
- **Settings and diagnostics:** model compatibility checks, microphone/phone permissions, GSM host readiness and an isolated laboratory for supported testing flows.

The Android interface currently uses Views; the code is split into UI, presentation/domain logic, data adapters and JVM modules. [Architecture](docs/architecture/SYSTEM.md) · [MVP status](docs/PROJECT.md)

## Install on Samsung S22+ (Android 16)

1. Download the **APK** linked above on the phone. Open it and accept Android's *Install unknown apps* prompt if shown; grant it only to the installer you trust.
2. Open **AI Calls → Settings → Permissions and default apps** (*Uprawnienia i aplikacje domyślne*). Grant the permissions needed for your chosen features: phone, microphone and **notifications** (essential for the pairing reply). If you want AI Calls to handle GSM calls, choose it as the **default Phone app** when Android prompts you. You can restore the previous dialer later.
3. Keep the phone connected to Wi-Fi. Enable **Developer options** if hidden: **Settings → About phone → Software information → Build number** (tap seven times), then return to **Settings → Developer options → Wireless debugging** (*Debugowanie bezprzewodowe*) and enable it.

## Wireless ADB pairing — enter the code in the top notification shade

**Important: the six-digit code is displayed by Android; it is entered into an AI Calls notification, not into the Android dialog.** No computer, IP address or manual port entry is required for the supported on-device pairing flow.

1. In **AI Calls → Settings → GSM audio / Audio GSM → Manage pairing / Parowanie ADB**, tap **Start pairing in Android / Rozpocznij parowanie w Androidzie**. Allow notifications if prompted. AI Calls opens Android Developer options and posts a pairing notification.
2. Open **Wireless debugging / Debugowanie bezprzewodowe** and tap **Pair device with pairing code / Sparuj urządzenie kodem parowania**. **Leave the Android popup with its six-digit code visible.**
3. **Swipe down from the very top of the screen** to open the notification shade (*górna szuflada powiadomień*). Expand **“AI Calls — parowanie GSM”** if needed, tap **“Wpisz kod / Enter code”**, type the **six digits shown in the Android popup**, and submit the notification reply.
4. Wait for the **“Sparowano / Paired”** notification. Go back to AI Calls → **GSM audio**. While the phone is **not in a call**, tap **“Uruchom host audio GSM / Start GSM audio host”**.
5. Tap **Refresh status / Odśwież stan**. Expect **“ADB: połączono i autoryzowano”** and **“Audio GSM: gotowe”**. Only then configure incoming calls or an outgoing task.

If you do not see the reply notification, check **AI Calls notification permission** and that notifications are not blocked. You can alternatively use the six-digit field inside AI Calls in split-screen mode, keeping Android's code popup open. If pairing fails, reopen the Android pairing-code dialog to get a **new** code; do not reuse an expired code. After a reboot, you may need to re-enable Wireless debugging and **start the GSM host again**.

## Finish setup and use

Open **Model and voice / Model i głos**, download a supported Gemma model (E2B/E4B), run **Check compatibility / Sprawdź zgodność**, select conversation language and preview your voice. Then configure **Answering calls / Odbieranie połączeń** for the desired SIM and AI/MANUAL mode, or create an outgoing task that you explicitly approve. Adjust **Conversation instructions** before using real conversations.

**Security:** Wireless ADB grants powerful local debugging access. Use only on your own phone, disable it when not needed, and never share pairing codes, tokens, call audio or private transcripts. The app uses a package-bound shell host and checks an exact device/UID, but this MVP is not a general security audit.

## Contributions welcome

Issues, reproducible bug reports, documentation improvements, tests, accessibility work and focused pull requests are welcome. For a larger change, open an issue first; preserve the existing UI, user data, exact Call/SIM ownership and STOP/takeover safety gates. Never include personal call data or secrets in PRs. See [developer documentation](docs/README.md).

**License:** No reuse/distribution license is declared in this repository at present; please discuss licensing with the maintainer before reusing the code outside this project.
