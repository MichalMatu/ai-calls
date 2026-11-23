# AI Calls — agent instructions

- MVP: GSM only, without Shizuku, on S22+ / Android 16. [Status](docs/PROJECT.md) · [QA](docs/operations/DEVELOPMENT.md).
- Modules: app (Android/UI), conversation-core (JVM state), conversation-tools (contracts), audio-bridge (PCM), privileged-helper (system), realtime-client (transport). Core/tools stay Android-free.
- Workflow: GitHub + Mac Local Agent. No Codex, Work, Actions or schedulers. Permanent branches: main (code) and agent-control (tasks/reports). [Agent](docs/operations/LOCAL_AGENT.md).
- Before work, check HEAD, worktree and the latest agent result. Preserve existing changes. QA: preflight → JVM/Lab → S22+ → SHA-pinned report.
- For GSM, keep exact Call/SIM ownership, IDLE before install/host start, STOP/takeover, END_CALL after TTS and rollback. Real calls require consent.
- Keep personal data, WAV and keys out of Git and logs. No `FLAG_SECURE` or unverified PASS.
