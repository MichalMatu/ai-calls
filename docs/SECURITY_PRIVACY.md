# Security and Privacy

This project processes live telephone audio and may eventually send that audio to a remote realtime model. Privacy and fail-safe behavior are part of the architecture, not polish to add later.

## Minimum rules

1. No long-lived OpenAI API key in the APK, source tree, logs, screenshots, or SharedPreferences.
2. No call recording by default.
3. Diagnostic recordings must be opt-in, short-lived and visibly indicated.
4. `Take over` must stop AI audio injection immediately even if the network/backend is unhealthy.
5. App shutdown/crash must fail toward normal human call behavior, not persistent injection.
6. Logs must not contain full transcripts or raw audio unless an explicit development mode is enabled.
7. Any backend requiring ADB, Shizuku, root or system privileges must state that requirement prominently in the UI and documentation.

## Realtime credentials

Preferred model:

```text
Android app -> project backend -> short-lived/session-scoped realtime credential
                                      |
                                      v
                                OpenAI Realtime
```

The backend stores the long-lived server credential. The mobile client receives only what is needed for the active session.

## Consent / disclosure

Call recording and AI participation rules vary by jurisdiction and context. The product UX should make it easy to disclose AI participation and should not silently persist audio.

For development tests, use your own second phone or a participant who knows the test is happening.

## Threat model to revisit before production

- stolen/repackaged APK;
- leaked short-lived credentials;
- malicious caller prompt injection;
- unintended tool execution from caller speech;
- background microphone/call access;
- transcript/audio retention;
- abuse of automatic outbound calling;
- privilege escalation through helper components;
- denial of service causing AI audio to get stuck in the uplink.

The first PoC should have **no external actions/tools** available to the model. It should only converse.
