# AI Calls — historical dial regression, real exact Call controller (2026-11-03)

## Root cause, source-verified
1. Before commit `6e732afba2da0d7e7f08f1b02373546042259e04`, `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AppOwnedClirCampaignExecutor.kt` invoked `startActivity(Intent.ACTION_CALL)` (old line 111), then `TelecomManager.endCall()` (old line 242). This could place a call, but the hangup was device-global, not exact-call owned.
2. `6e732af`, 2026-11-01: inserted `CampaignCallOwnershipProof(false,false)` before dialing. Hence the app could no longer dial by that path. `76b086ba8542` moved that denial before permission prompts.
3. `c48685b79689`, 2026-11-01: introduced `CampaignCallControllerRegistry` but left `provider=null`, no production registration. `AppOwnedClirCampaignExecutor` therefore always rejected attempts without the previously working dial method.
4. `a01979586588`, 2026-11-01: removed auto-dial and global endcall from `autonomous_call_loop.py` and `chatgpt_relay_live_call.py`; previous developer ADB loop was reduced to attach-only. These are separate paths from the Android app.
5. No Git history was rewritten and no global hangup restored.

## Changed code (commits `0226d61`, `294cc2d`, `33e27cc`, `3ee183c`)
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AndroidTelecomOwnedCallController.kt` (new): role-gated Android `TelecomManager.placeCall`, target/SIM/direction/timing/token matcher, real `Call` reference and targeted disconnect including STOP-before-callback handling. Generic controller design accepts approved targets, though current campaign UI is CLIR-specific.
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AndroidOwnedInCallService.kt` (new): receives OS-selected `Call` objects; unrelated calls are not bound to autonomous session; posts a permission-checked notification.
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AndroidCallControlsActivity.kt` (new): minimal human dial/answer/reject/finish panel when user elects to replace Google Phone as default dialer.
- `CampaignCallControllerRegistry.kt`: returns real controller only if Android confirms the application actually holds `ROLE_DIALER` and is the default dialer.
- `AppOwnedClirCampaignExecutor.kt`: uses the new context-aware controller, preserving scoped grant and SIM checks.
- `CampaignToolActivity.kt`: one-tap START requests notification permission on Android 13+ and an Android OS dialer-role choice only if required, resumes START once granted, never auto-grants roles.
- `app/src/main/AndroidManifest.xml`: explicit `InCallService`, ACTION_DIAL activity, POST_NOTIFICATIONS.
- `app/src/test/kotlin/pl/michalmatu/aicallbridge/campaign/CampaignCallMatchPolicyTest.kt` (new): rejects wrong number/account/incoming/stale/mismatched token.
- `app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/campaign/AndroidExactCallRoleReadinessContractTest.kt` (new): no-call OS role/manifest contract.

## Exact test receipts
- `aicalls-20261103-history-rootcause-phone-preflight-v1`: DONE/0. Git history proof; old dial/endCall; S22 IDLE, Google Phone default at that observation.
- `aicalls-20261103-android-exact-call-compile-device-v1`: FAILED (lint `POST_NOTIFICATIONS` required); fixed without suppressing lint.
- `aicalls-20261103-android-call-controller-final-acceptance-v2`: FAILED solely because `RFCT70L7E8J` vanished from USB ADB after build; focused unit + APK + androidTest APK + lint **PASS** on `33e27cc`. No installation.
- `aicalls-20261103-controller-build-cache-adb-recover-v3`: DONE/0, `EXACT_CONTROLLER_BUILD_LINT_UNIT=PASS`, APK + AndroidTest cached at private Mac `~/Library/Caches/ai-calls/verified-builds/3ee183c085e7cef200ba0f34d28a106adf27f426/`. S22 unavailable after USB + paired WiFi discovery, no installation.
- `aicalls-20261103-cached-apk-last-s22-usb-diagnose-v1`: FAILED/not started due task budget misconfiguration (repaired).
- `aicalls-20261103-cached-apk-last-s22-usb-diagnose-v2`: DONE/0, APK cache PASS, exact S22 transport unavailable, no install/no call.

## Still open, do not misstate
- The new `3ee183c` code **has not been installed on S22** and no physical `AndroidExactCallRoleReadinessContractTest` completed. The old installed version is separate evidence.
- The OS requires user selection before AI Calls can hold the default dialer role. Do not silently change Google Phone or evade permission checks.
- No real autonomous call, real RX/TX, in-call STOP/selective disconnection or success verification passed.
- `MODE_IN_COMMUNICATION` seen earlier remains unproven at root cause; source scan found no ordinary application `AudioManager.setMode` changes. Diagnose synchronized system audio owner and actual call before altering frozen Samsung media safety.
- Default role-qualified controller fixes the previous **unimplemented backend** but does not by itself complete a generic natural-language task executor; current product campaign action is CLIR-specific.

## Next precise engineering action
On an available exact S22+ `SM-S906B / RFCT70L7E8J`, verify IDLE, install the already-cached `3ee183c` APK + AndroidTest, run role-readiness instrumentation, then obtain explicit OS role consent and a concretely authorized consenting call target before controlled real-call RX/TX/teardown acceptance. Build is already PASS; do not re-run unchanged host suites.
