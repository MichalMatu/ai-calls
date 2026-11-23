# MVP architecture

Telecom provides the exact Call. `AndroidIncomingAssistant` starts `AndroidConversationRuntime`, which connects STT, Gemma and TTS to `ToolCallingConversationSession`. The app can save an encrypted note after approved TTS and authorize END_CALL.

`app` integrates Android UI, presentation, domain and data adapters. `conversation-core` and `conversation-tools` are JVM modules. `audio-bridge` handles PCM, `privileged-helper` handles system integration, and `realtime-client` handles transport. `module_boundary_gate.py` checks imports.

The Android Views screens keep some UI coordination. Native TTS probes and terminal summaries have separate owners. The [Activity map](UI_SCREEN_MAP.md) records the baseline for future Compose work.

The Binder host runs outside the app process as shell UID 2000. Start it through local ADB while the phone is idle. Reboot can require reconnecting and starting the host again.
