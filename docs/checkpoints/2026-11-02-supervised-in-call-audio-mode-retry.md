# AI Calls — supervised call reported active, audio route not ready (2026-11-02)

## User-observed symptom

During a manually started Orange customer-service call, the supervised UI displayed:
`Tor rozmowy nie jest aktywny. Spróbuj podczas trwającego połączenia.`
The UI reaches this branch when `TelecomManager.isInCall == true` but the ordinary app's `AudioManager.mode != MODE_IN_CALL`. **The actual in-call mode was not captured in that earlier attempt**, so this is a narrowed code-path diagnosis, not proof of a specific Android mode or root cause.

The optional read-only S22 diagnostic receipt `aicalls-s22-safe-mode-diagnostic-20261102-v2` was `done/0`, but it observed `mCallState=0`, `MODE_NORMAL` **after** the call. It cannot establish what mode was used during the call. First diagnostic v1 failed admission because the command's requested timeout did not fit its task deadline; no commands ran.

## Scoped application change

Current code commit: `5396cce7e906cc7db256b8bc13d7f910ec916933` on `main`.

- The UI **only** retries an observed `AUDIO_ROUTE_NOT_READY` for at most 3 seconds while its foreground screen is present; if Android transitions into `MODE_IN_CALL`, it opens the ordinary explicit consent dialog, never starts AI silently.
- If Android remains in another mode, the app displays that exact mode and writes `files/supervised-call-readiness-report.txt` in app-private storage. It contains only `telecom_in_call`, `telecom_managed_call`, `audio_mode`, failure reason and completion marker, with **no phone number, audio or transcript**.
- `READ_PHONE_STATE` is explicitly checked before querying either Telecom status API.
- Existing Shizuku/audio helper, explicit approval, `MODE_IN_CALL` fail-closed gate, STOP-only-audio and default Google Phone dialer are unchanged. No autonomous call or global hangup was added.

The bounded retry fixes only a potentially transient audio-mode transition. A persistent mode mismatch requires physical evidence from another manually started, consenting live call before altering frozen media routing.

## Verification / immutable Mac Host Control evidence

- `aicalls-s22-route-retry-regression-deploy-20261102-v1`: terminal **failed** after `TARGETED_TEST=PASS`, APK assembled, and app lint found two new `MissingPermission` diagnostics in the report writer. Those were corrected in source; the initial lint run must **not** be relabeled PASS.
- `aicalls-s22-route-retry-lint-fix-and-install-20261102-v2`: terminal **done/0**, pinned clean source `5396cce`, `APP_BUILD_LINT=PASS`, `APK=READY`. Deployment **DEFERRED_USB_UNAVAILABLE**, so the fixed APK was **not installed** by that task. Prior targeted policy test remained accepted and was not repeated.
- `aicalls-s22-deploy-discover-wireless-20261102-v3`: terminal **done/0**, pinned clean source `5396cce`; exact S22 USB/previously paired Wireless ADB discovery found no transport: `S22_DEPLOY=DEFERRED_EXACT_ADB_TRANSPORT_UNAVAILABLE`. No build/install without the device.
- Full unrelated STT, TTS, Gemma, Shizuku and original host quality suites were **not rerun**.

## Exact remaining gate

1. Reconnect the physical S22+ `SM-S906B` / `RFCT70L7E8J` to Mac USB ADB (or restore an already paired Wireless ADB endpoint). Only after verifying exact hardware identity and `mCallState=0`, build the current pinned source and use `adb install -r`, preserving app data.
2. User starts and ends any real call manually; no automatic dialing of `*100`, `510 100 100` or other targets. For a supervised speech acceptance use a consenting counterparty; human-perceived remote TX still requires independent human confirmation.
3. With user-approved AI attach, use Mac Local Agent to read only sanitized `supervised-call-readiness-report.txt` and `chat-relay-live-call-report.txt` counters. If `MODE_IN_CALL` appears, check RX/STT/Gemma-or-script/TTS/TX and STOP; if not, correlate the actual in-call mode and carrier state, identify the precise device routing root cause before changing frozen media code.

**Status: SOURCE REGRESSION PASS, PHYSICAL DEPLOY OPEN, LIVE RX/TX/STOP OPEN.**
