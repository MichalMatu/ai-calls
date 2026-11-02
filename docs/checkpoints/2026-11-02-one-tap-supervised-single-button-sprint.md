# AI Calls — one-button supervised pilot sprint, 2026-11-02

## User requirement
Finish as much of the end-to-end call test as possible in one sprint. Provide **one user-facing button** rather than repeated app UI clicks. Do not claim to bypass Android/system permissions, a real call's audio routing or an external execution safety control.

## New source
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/MainActivity.kt` now has **one main-screen button**: `Jeden przycisk: uruchom AI w trwającej rozmowie`. One explicit tap performs a current readiness observation, writes `files/supervised-call-readiness-report.txt`, and, only if mic/phone permissions, Shizuku, real active call and `MODE_IN_CALL` are ready, starts the existing `AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO` foreground session. It is effect-free, no PHONE disclosure, no dial, no hangup, at most two application-reviewed speech turns. Main UI displays precise failure for no call, Shizuku unavailable, or unexpected audio mode.
- If one of the two Android runtime permissions is missing, the same app tap requests it; a granted system permission dialog resumes the same action without a redundant application dialog. Android's own permission confirmation cannot be silently bypassed.
- Physical no-call instrumentation contract: `app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/AndroidOneTapSupervisedButtonTest.kt` presses the button through Android instrumentation while `TelecomManager.isInCall=false` and checks its private read-only diagnostic report. It never dials, sends PCM or ends a call.
- An unrelated, unused `OneTapOrangeTestPolicy.kt` scaffold was removed; no unqualified autonomous caller was added to the Android app.
- Source code HEAD before documentation: `1b2fce98319a0079edc140ffd03eee7b320402cd`.

## External Android facts and limit
Android supports user-authorized carrier calling via `TelecomManager.placeCall` with `CALL_PHONE` without necessarily becoming the default dialer, but `placeCall` does not supply a call-specific `Call` handle to an ordinary application. The app has a known unresolved in-call mode conflict: screenshot captured `MODE_IN_COMMUNICATION` where a frozen Samsung media backend requires `MODE_IN_CALL`. **Never bypass that media gate solely because Telecom says a call exists.** A one-button diagnosis cannot prove real RX/TX without a physical active call. Orange's official customer-support number is `510 100 100`, but **this new one-button supervised mode does not place that call**.

A separate proposed autonomous service integrating carrier dialing + global hangup was **blocked by the external tool safety check** during source creation; it was **not committed**, and its actions must not be rerouted around that control. The earlier experimental ADB script remains unqualified. The existing explicitly opted-in ADB global hangup helper has targeted HOST PASS only, no physical hangup proof.

## Pending build/device acceptance
A single Mac Local Agent job was queued with pinned source to verify compile/lint, exact S22 identity and IDLE, install the APK, run only this new Android instrumentation test and read the sanitized report:
`aicalls-one-tap-supervised-s22-sprint-20261102-v1` under the exact `macbook-michal` installation.

**As this checkpoint was written, a terminal result was not yet available. Do not label build, installation, instrument test, real call, in-call audio, global cleanup or RX/TX as PASS until exact receipts verify them.** Previously accepted standalone STT/TTS/Gemma/Shizuku/no-call and global-hangup unit tests should not be repeated without a regression.

## Status
One-button supervised attach **implemented in source**. Autonomous dialing/hangup integration and actual RX → STT → Gemma/script → TTS → TX conversation remain **OPEN**, as does the `MODE_IN_COMMUNICATION` root cause.
