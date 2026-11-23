# Conversation and offline lab

GSM audio flows from PCM to STT, Gemma with dialogue instructions, approval, then TTS and TX. STOP and takeover revoke TX. After approved TTS, the app can save key details as one encrypted note per conversation before END_CALL. Notes are enabled by default, can be switched off and filter secrets. TASK, CALENDAR and MESSAGE entries are proposals.

The offline lab is available through `scripts/bin/aicalls` with `lab-start`, `lab-send`, `lab-status`, `lab-stop`, `lab-save`, `--instructions-stdin` and `lab-regression`.

The app's language selection sets the speech profile and built-in instructions for new calls. User-authored instructions retain their text. E4B requires a compatibility check.
