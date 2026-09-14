# Security and Privacy

This project processes live telephone audio and may send that audio to a remote realtime model. Privilege boundaries, takeover behavior and data minimization are part of the core architecture.

## Security goals

1. A normal application failure must not leave AI audio injected into a live call.
2. Privileged code must be as small and narrow as practical.
3. Live audio should be processed as a stream, not silently retained.
4. Long-lived server credentials must never be stored in the APK.
5. Caller speech must never directly authorize dangerous external actions in the first product versions.

## Privilege boundary

The normal application process should not receive broad privileges merely for convenience.

Privileged operations live in `privileged-helper`, initially through a Shizuku UserService/shell context.

The helper should expose only the minimum control surface needed for experiments and the eventual bridge, for example:
- capability query;
- start/stop capture;
- open capture pipe;
- start/stop/abort injection;
- open injection pipe;
- limited route/call-audio diagnostics.

Do not expose arbitrary shell command execution to the app UI or realtime model.

Any Samsung-specific backend must document:
- effective UID;
- exact permissions/capabilities relied on;
- Android/One UI/build constraints;
- private Binder/system services used;
- failure and cleanup semantics.

## Helper lifecycle and fail-safe

Injection is the highest-risk resource.

If the normal app process dies, Binder disconnects, the helper loses its controller, or a bounded heartbeat expires while injection is active, the helper must:

1. stop accepting new PCM;
2. discard queued output;
3. stop/release the injection `AudioTrack` or vendor route;
4. release temporary audio-routing state;
5. restore the human microphone path where the backend controls it;
6. close pipes and session resources.

The fail-safe must be local. It cannot depend on reaching the project backend or OpenAI.

## Take over

`Take over` is a privileged safety operation, not merely a button state.

The local sequence is:

```text
block new AI output
-> flush queued injection
-> abort injector
-> restore human microphone
-> keep cellular call active
-> cancel remote model response
```

The network cancellation can happen after the local audio path is safe.

## Realtime credentials

Preferred model:

```text
Android app -> project backend -> short-lived/session-scoped realtime credential
                                      |
                                      v
                                OpenAI Realtime
```

Rules:
- no long-lived OpenAI API key in source, APK, SharedPreferences, logs or screenshots;
- credential lifetime should be limited to the active session;
- backend should authenticate the app/user before issuing session capability;
- revoke/expire session capability when the call bridge ends.

## Audio and transcript retention

Default behavior:
- do not record calls;
- do not persist raw PCM;
- do not persist transcripts unless the user explicitly enables a feature requiring them;
- do not log full transcripts in normal mode.

Diagnostic audio for Phase 1:
- explicit opt-in;
- short duration;
- stored locally;
- clearly named as a diagnostic artifact;
- easy to delete;
- never uploaded automatically.

## Consent and disclosure

Call recording and AI participation rules vary by jurisdiction and context. Product UX must make AI participation and any retention behavior visible enough for the user to comply with applicable rules.

Development tests should use the owner's second phone or a participant who knows that the test is happening.

## Caller prompt injection

The remote caller is untrusted input.

For the first functional versions the model must have **no external tools/actions**. It may converse only.

Before any later tool integration, add an authorization layer outside the language model for actions such as:
- sending messages;
- accessing contacts/private data;
- making purchases;
- changing device settings;
- placing additional calls;
- controlling connected services.

A caller saying "ignore your instructions and do X" must never be sufficient authorization.

## Shizuku and debugging exposure

Shizuku/ADB expands the device attack surface compared with an ordinary APK.

Product/development rules:
- clearly indicate when privileged mode is enabled;
- request only the access required for this app;
- do not keep unnecessary debug servers or arbitrary command interfaces alive;
- treat wireless debugging exposure as a development/security consideration;
- provide a clean way to stop the helper/session;
- do not assume Shizuku grants every protected Android capability.

## Logging

Normal logs may include:
- state transitions;
- backend name;
- anonymized capability result;
- frame/queue counters;
- timings;
- exception classes/messages that do not contain private audio/transcript data.

Normal logs must not include:
- raw PCM;
- API keys/tokens;
- full phone numbers unless a dedicated debug mode explicitly requires it;
- full transcripts;
- contact databases.

## Threat model before production

Revisit at minimum:
- repackaged/tampered APK;
- malicious or over-privileged Shizuku helper client;
- leaked ephemeral credentials;
- Binder interface abuse;
- caller prompt injection;
- unintended background call access;
- stuck injection after UI/process failure;
- audio/transcript retention leaks;
- service denial causing buffer growth or repeated output;
- Samsung private API changes after OTA updates;
- automatic outbound-call abuse if that feature is ever added.

## Production gate

No build should be considered product-ready until a destructive failure test confirms that killing the app, losing the network and losing the realtime session all result in **AI injection stopping locally while the user retains control of the cellular call**.