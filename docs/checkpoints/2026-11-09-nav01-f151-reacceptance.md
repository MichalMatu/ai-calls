# AI Calls — NAV-01 and F-151 physical reacceptance (2026-11-09)

## Baseline and confirmed execution

- Source before repair: `main@6f34c5c39f1afba6be913425577e8c555ed66825`. Exact device RED: `aicalls-nav01-contract-triage-20261109-v1` DONE/0, 5 AndroidAppNavigationContractTest cases, **1 failed** (`agentOwnsEditorsAndSettingsHasNoDuplicateDestinations`, `IllegalArgumentException: Required value was null`). S22 `SM-S906B`, ADB `RFCT70L7E8J`, Call IDLE.
- Root cause: obsolete test asserted `agent-destination:PhoneEnrollmentActivity` while active `AgentAiHubActivity` exposes `IdentityVaultManagerActivity` since UX-10/F-143. Actual app UI was consistent with newer design. No production UI change required.
- Source fix: `main@b501768566cad446d0866a013538fd8b612e6258` updates expected destination to `IdentityVaultManagerActivity` and asserts no obsolete Agent hub link. F-151 registered `main@bbfb4171323c28efcf8b43dcc0398e867d96b3b3`.
- Mac full gate: `aicalls-nav01-f151-targeted-mac-build-20261109-v1` **DONE/0**, `bash scripts/verify_host.sh` PASS (Gradle/test/lint/audio), Android APK + AndroidTest APK built; clean checkout; APK SHA-256 e3e542405e65bef43527832acbec8a9cef501aa0ff5b93dac4af52ee430e0da8 and AndroidTest 7c7d58c15540ce72737aa8568b02be2aa514280dd9bf0efd512cfdb126e0da06.
- Physical S22: `aicalls-nav01-f151-s22-reacceptance-20261109-v1` **DONE/0**. Preflight model/ADB, Call IDLE, campaign idle, awake Launcher PASS. `adb install -r -t` for both APKs succeeded **without data wipe**. Read-only instrumentation results:
  - `AndroidAppNavigationContractTest`: **5/5 PASS**.
  - `AndroidAppBottomNavigationFlowTest`: **2/2 PASS**.
  - `AndroidSettingsNavigationMatrixTest`: **3/3 PASS**, opening settings/configuration pages without changing profile/skills/incoming preferences.
  - Call IDLE after instrumentation. Mac checkout clean.

## Decision and scope

**F-151 VERIFIED** (stale test fixed and physical GREEN). **NAV-01 PARTIAL**: all 10 scoped tests passed, but full repeated tab/back-stack duplicate prevention and persistence after Activity/process restart remain outside this receipt. Do not mark complete based on synthetic or single screen transitions.

The app APK was refreshed in this sprint. The install script searched `dumpsys package` for `UsbLabControlReceiver` and returned `False`; this text search is **not a conclusive component-discovery or USB runtime test**. New USB Lab / actual receiver operations remain **DEVICE_QA_NOT_RUN** until separately verified; call takeover/resume/exact STOP **NOT_RUN**. No outgoing or incoming Call, TTS, model inference, download, grant alteration, user vault edit or private transcript export in this sprint.

## Next scoped work

1. Finish NAV-01 repeated transitions and Activity Back/recreate with read-only AndroidTest; keep phone unlocked, Call IDLE and current app/Launcher foreground. Alternatively retain precise PARTIAL while moving to HOME-01/02 and return only when needed.
2. Independently check USB manifest component presence and `DUMP` receiver response; distinguish APK registration from functional device acceptance.
3. Respect F-148 and F-150 as separate OPEN/DEVICE_QA_OPEN and do not run live Call without concrete authorized scope.
