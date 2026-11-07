# AI Calls — exact S22 UX acceptance, 21/21 off-call instrumented tests (2026-11-07)

## Source and host quality
- Android source, app and instrumentation: `main@035af45045934453fe9801d29e2f6f932c5dc848` (only test source changes after `b33bdaa`; UI/controller implementation identical).
- Selected host: Mac `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`.
- Exact `aicalls-20261107-s22-locale-matrix-fix-host-v1`: **DONE, exit 0**; 297 Python, AndroidTest compile, JVM, lint/APK, anti-friction PASS, clean repo and source head. Earlier `aicalls-20261107-tools-ui-controller-locales-gate-v1`: 297 Python PASS.
- Security preflight `aicalls-20261107-s22-ux-preinstall-safety-v1`: DONE/0; exact hardware SM-S906B / expected previously paired serial, 2× IDLE, no campaign or retry, installed/new app signer identical; app and test APK present. Prior ADB loss was transient, read-only reconnect `aicalls-20261107-s22-detect-after-reconnect-v1`: DONE/0, 1 USB device, 0 offline, exact model/serial verified.

## Physical acceptance on actual Samsung S22

The successful **21 test outcomes** were collected on the same phone and same app/controller revision, after `adb install -r -t` updated the app and instrumentation APK with no app-data clear, permission grant or phone calls.

| Test suite | Tests | Outcome |
| --- | ---: | --- |
| `AndroidAppNavigationContractTest` | 4 | PASS |
| `AndroidAppBottomNavigationFlowTest` | 2 | PASS |
| `AndroidMenuVisualContractTest` | 3 | PASS |
| `AndroidUiAccordionContractTest` | 2 | PASS |
| `AndroidSettingsNavigationMatrixTest` | 3 | PASS |
| `AndroidLiveChatDockContractTest` | 2 | PASS |
| `AndroidScreenshotAllowedTest` | 1 | PASS — `OK (1 test)`, `INSTRUMENTATION_CODE: -1` |
| `AndroidHistoryNavigationDeletionTest` | 1 | PASS — synthetic archive, Back and safe confirmation |
| `AndroidArchivedChatAudioPlaybackTest` | 1 | PASS — synthetic silent WAV only |
| `AndroidIncomingAssistantContractTest#settingsDoNotEnableAiOrChangeTaskAndEnginesOnOpen` | 1 | PASS |
| `AndroidConversationToolsSettingsTest#openingToolScreenDoesNotAcceptTaskOrAlterConfiguration` | 1 | PASS |
| **Total** | **21** | **PASS** |

Evidence split and failed historical attempts:
- `aicalls-20261107-s22-ux-install-21tests-v1`: **FAILED after successful data-preserving app/test installation and 11 native PASS**, because a legacy settings test asserted a Polish title on the now localized UI. Not a complete acceptance.
- `aicalls-20261107-s22-ux-21tests-locale-corrected-v2`: **FAILED operator wrapper after 16 real native PASS**. In particular screenshot runner expected the ungrammatical `OK (1 tests)`.
- `aicalls-20261107-s22-screenshot-failure-diagnosis-v1`: **DONE/0**, exact raw screenshot test log explicitly `OK (1 test)` and `INSTRUMENTATION_CODE: -1`. Screenshot was captured in memory and bitmap recycled, **not saved or exported**.
- `aicalls-20261107-s22-ux-final-four-acceptance-v1`: **DONE/0**; 4/4 remaining tests PASS. Phone checked again IDLE and no campaign/retry after tests. Thus **16 + 1 + 4 = 21/21 distinct verified outcomes**; old failed wrappers remain immutable and must not be relabeled green.

## Scope
Verified: no-screen-capture-block source contract and real private-history screenshot, Calls child/system Back, completed-only synthetic transcript/WAV delete with confirmation, archive playback synthetic WAV, four-tab navigation, menu/UI contrast/contracts, opening settings without changing selected preferences, incoming assistant and AI tools controllers' read-only screen behavior. Existing user data and settings retained, zero real calls or unwanted state changes. No uploaded screenshots.

Not yet verified: full polish/aesthetics on every screen, large-font/landscape/overflow and dynamically added button gaps, all legacy screens translated PL/EN, every preference edit toggle in exhaustive manual matrix, and full end-to-end real call/media behavior. F-103 remains OPEN; F-107 physical visual owner review remains pending. F-080 full continuous-call recorder remains separate.

## Next development slice
Keep this exact source and 21/21 physical acceptance as checkpoint. Next: translate remaining user-facing screens, with Main / Campaign / Audio Engines / Preparation / Skills / Lab as priority; separate corresponding view logic from store/engine/authority adapters in small behavior-preserving commits; fresh host gate and physical S22 test after actual source changes. Do not rerun accepted tests on unchanged source.
