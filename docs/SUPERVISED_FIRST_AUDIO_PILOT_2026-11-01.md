# AI Calls — supervised first-audio pilot (2026-11-01)

## Implemented scope

This sprint creates a **user-attached, read-only** cellular audio pilot while retaining Google Phone as the default dialer. The human initiates and ends the normal carrier call; AI Calls only starts/stops its own speech/media loop. It cannot place or disconnect a cellular call, enable/disable CLIR, issue any external-effect commitment, disclose PHONE, or invoke a network purchase. This is **not** the autonomous exact-call controller.

Changes:

- `CampaignToolActivity`: explicit confirmation and two actions, **Attach read-only audio pilot to my existing call** and **Stop audio pilot (do not hang up)**. The attach action requests microphone and phone-state permissions only, with no `CALL_PHONE` permission or role change.
- `AppOwnedCampaignService`: isolated foreground supervised mode that requires `TelecomManager.isInCall`, `AudioManager.MODE_IN_CALL`, a Shizuku Binder and permission. Its every-turn authorization watchdog stops the app media when manually stopped or the call disappears. It never invokes any call-end primitive. The backend is capped at two turns.
- `ChatRelayLiveCallProbe`: `readOnlySupervised` explicitly requires `phoneDisclosureAuthorized=false` and `supervisorRelayEnabled=false`, uses a deterministic non-effect backend, and rejects service commits in the underlying control method.
- `SupervisedReadOnlyDialogueBackend`: safe, local fixed responses for basic speech-loop testing, with adversarial regression tests.

## Safety boundaries

The phone call is **unowned** by AI Calls in this mode. A global cellular in-call indicator is used *only* as a stop/attach check, never as proof of exact call identity or hangup authorization. The user must maintain control of the actual call in Google Phone. The speech pilot has no promise of attaching to a particular SIM or number and must not be used for the Orange CLIR mutation scenario.

The existing autonomous CLIR executor is unchanged and remains blocked at `CampaignCallControllerRegistry.available()==null`. There is still no verified Shizuku or Telecom exact-call Android backend. Android docs require a full default-dialer implementation (including incoming/ongoing call UI) to use `InCallService` with call-specific `Call.disconnect()`; this sprint does not request the dialer role.

## Verification status

Source commits on `main` began at `0799911`. `aicalls-supervised-audio-host-20261101-v1` was sent to the Mac Host Control task queue to run `scripts/local_agent/host_verify.sh` on exact source `e437f13`; **do not call this PASS without a terminal task result**. The pilot has not been deployed or manually tried on S22. No real calls were made by this sprint.

## Remaining work

1. Inspect the exact Mac task result and repair any compiler/lint/JVM regressions.
2. Verify pilot UI/readiness without a call, then arrange a distinct user-controlled call to test the actual RX/STT/script/TTS/TX audio loop.
3. For autonomous dialing, implement and physically prove a call-specific Telecom controller. Preserving the default Google Phone prevents relying on Android `InCallService` directly. Any change to that role requires explicit user selection and a complete dialer implementation. Neither Shizuku Binder nor a global `isInCall` is an acceptable shortcut.

## Verified closeout — 2026-11-01

- Final source: `a503add6e150ac66da2c555d7f2eacff9bc1ad1c`.
- Mac Host Control `aicalls-supervised-audio-host-20261101-v3`: **done / exit 0**; 199 Python tests, Gradle Android/JVM unit, Lint, debug APK and AndroidTest APK **PASS**. `SUPERVISED_READONLY_FINAL_HOST_GATE=PASS`.
- S22 preflight `aicalls-supervised-audio-s22-nocall-20261101-v2`: **done / exit 0**; Wireless ADB, Shizuku Binder and permission, microphone and cellular `IDLE` **PASS**.
- Safe deployment `aicalls-supervised-audio-safe-deploy-s22-20261101-v2`: **done / exit 0**, `adb install -r` reported `Success` and the app package was present. Default dialer remained `com.google.android.dialer`. The first deployment task failed only because it redundantly invoked Gradle after the host script without exporting the SDK path; v2 reused the verified APK.
- **No real call, dial, hangup, change to CLIR or switch of the default dialer was performed.** Pilot dialogue STT/TTS over a real user-controlled call is still awaiting an explicitly user-initiated physical test. Autonomous targeted call ownership is still blocked.
