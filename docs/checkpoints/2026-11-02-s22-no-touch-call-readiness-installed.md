# AI Calls — no-click S22 call-readiness probe installed (2026-11-02)

## Purpose / exact starting evidence
The user observed, during a real manually started Orange call, that the supervised activity detected an ongoing call but refused AI audio because its app-context `AudioManager.mode` was `MODE_IN_COMMUNICATION`, not `MODE_IN_CALL`. The screenshot is an authentic **in-call app observation**; it is **not** yet independent proof that privileged Samsung `VOICE_DOWNLINK` and `CALL_ASSISTANT` routing accept that mode. Keep the frozen media gate and watchdog fail-closed until the live cross-layer mode discrepancy is measured.

The user requested no repeated UI clicking and autonomy during development. The new test entrypoint eliminates app UI interaction for **read-only diagnosis**, not Android's authority/role requirements for initiating and ending real calls.

## Source / immutable receipts
- GitHub `main@8f976a17c57022f23d655a0f83f6499276dd4d2a` contains:
  - `SupervisedCallReadinessProbeActivity.kt`: DUMP-permission-gated shell-accessible one-shot Android Activity, observes Telecom, permissions/Shizuku and AudioManager in the **app process**; emits sanitized app-private report then finishes. Does not dial, hang up, access audio PCM, attach AI, or change default dialer.
  - `AndroidManifest.xml`: `android.permission.DUMP` gate; exported only for privileged shell observer; `noHistory` and `excludeFromRecents`.
  - `scripts/aicall_tools/device/s22_supervised_readiness.py`: verifies exact S22 hardware, deletes previous report before capture to avoid stale evidence, launches probe through authorized ADB, and prints strictly whitelisted typed metadata.
  - `scripts/tests/device/test_s22_supervised_readiness.py`: tests valid audio-mode mismatch, malformed reports, privacy field rejection, identity targeting and no-call-effect command contract.
- `aicalls-no-touch-diagnostics-build-20261102-v1`: **DONE/0** on code `b0bfec0`, app build/lint PASS, DUMP + no-call-effect contract PASS.
- `aicalls-no-touch-diagnostics-complete-gate-20261102-v2`: **DONE/0** on full code `8f976a1`, targeted new Python test PASS, Android build/lint PASS, DUMP/no-media contract PASS, clean source PASS. No earlier unrelated tests repeated.
- `aicalls-s22-ongoing-call-mode-evidence-20261102-v1` was **FAILED** when USB/ADB reported `device not found`; did not capture the prior call. Do not relabel it PASS.
- `aicalls-s22-noclick-diagnostics-deploy-20261102-v1`: **DONE/0**, exact `SM-S906B / RFCT70L7E8J` verified, `mCallState=0` before build and again immediately before install, pinned `main@8f976a1`, APK built, `adb install -r` successful, app package verified, and the **new ADB-only probe physically executed** with sanitized output:
```text
probe=supervised_call_readiness
microphone_permission=true
phone_state_permission=true
shizuku_ready=true
telecom_in_call=false
telecom_managed_call=false
audio_mode=MODE_NORMAL
failure_reason=no_active_cellular_call
report_complete=true
NO_DIAL_NO_HANGUP_NO_PCM=true
NOCLICK_READINESS=PASS
```
This is **off-call** evidence, not proof of live audio routing. Because the collector always clears the previous report before taking a fresh snapshot, this test replaced the older saved in-call report; the user screenshot remains the authoritative observation of `MODE_IN_COMMUNICATION` at that earlier time.

## Safety/controller review
- `app/.../campaign/CampaignCallControllerRegistry.kt` defines `CampaignOwnedCallSession` with exact-call active state and selective disconnect, but provider is **not installed**.
- `AppOwnedClirCampaignExecutor.kt` therefore fails closed **before dialing**. The ADB call helper also refuses global hangup and unmanaged dialing. Do not remove guards merely to allow test repetition.
- Android documentation states `TelecomManager.endCall()` ends the **device foreground call**, not a proven app-owned call, and is deprecated. The supported `InCallService` call-handle path generally requires `RoleManager.ROLE_DIALER` with a **fully functional phone UI including incoming/ongoing calls**. It requires Android role selection; do not silently supplant Google Phone or install a partial default-dialer implementation.
- No real Orange call was placed by the tool, no service was changed, no account/identity audio was sent, and no call was ended.

## Next meaningful hardware test
1. Retain accepted code/host/device receipts; do **not** repeat completed STT/TTS/Gemma, Shizuku, or no-call diagnostics absent a regression.
2. During a human-consented, human-initiated real call (or once a verified exact-call controller exists), run `PYTHONPATH=scripts python3 -m aicall_tools.device.s22_supervised_readiness --serial RFCT70L7E8J` through Mac Local Agent. This takes a **no-click app-process** mode snapshot; it must not run while an AI audio session is already attached because Activity switching intentionally stops supervised audio.
3. Correlate with time-aligned sanitized system telephony/audio-mode metadata to identify why the app saw `MODE_IN_COMMUNICATION`. Check whether helper has a distinct view of audio mode before any change to frozen capture/transmit gates.
4. For fully autonomous real carrier calls, scope a separate product sprint for a genuinely call-specific controller with role/UI requirements, revocable target/SIM authorization and selective disconnect, verified first with zero external effects. Only then reconsider automated calls to service numbers.
5. After a safely established call, prove real RX → STT → reviewed Gemma/script → TTS → TX, remote audibility and prompt STOP. Counters alone do not prove what the other party heard.

**Status: NO-CLICK DIAGNOSTICS BUILD/LINT/TEST PASS, PHYSICALLY INSTALLED AND OFF-CALL PROBE PASS; LIVE AUDIO-MODE ROOT CAUSE OPEN; AUTOMATED REAL CALL BLOCKED ON EXACT OWNERSHIP.**
