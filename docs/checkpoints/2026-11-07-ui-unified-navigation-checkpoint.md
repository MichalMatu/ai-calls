# AI Calls — unified navigation & UI architecture checkpoint (2026-11-07)

## User goal
One coherent native Android interface for AI Calls; remove single-button accordions; preserve tested call/AI logic, stored configuration, privacy, and device state. Work mode: GitHub Connector + Mac Local Agent, main only, [skip ci], no GitHub Actions/Codex/Work/force-push.

## Baseline
Started from verified `main@92229e5a524da6c955a67af3c18b88854a77e1e5` after existing physical S22 10/10 dock/i18n matrix. Mac read-only preflight `aicalls-20261107-ui-shell-preflight-main-v1` DONE/0: main=origin/main, 0 tracked/staged/untracked changes, SDK present. No active call or phone installation by that preflight.

## Implemented
- `8aae5e5` four-tab bottom navigation (Home, History, Lab, Settings) and direct SettingsHub with 10 destinations; vector icons, strings PL/EN and instrumentation contracts.
- `9f4e75d` removal of five fake home accordions; original modes and LLM choices moved to GeneralSettingsActivity, probes moved to DeveloperToolsActivity. Primary authorized START, STOP AI, campaign and supervised test remain visible.
- `b679717` migrate the historic F-039 contract for the new Home hierarchy; preserve failed predecessor `aicalls-20261107-ui-home-direct-settings-gate-v1`.
- `80f0cca` UiChromeActivity wraps 11 existing screens in shared navigation; saved preferences, API services and rendering logic are not rewritten.
- `16c58cd` one Android platform Theme.AiCalls and Widget.AiCalls.Button with common type, contrast, 48dp minimum, ripple and backgrounds; no new Compose/AppCompat dependency.
- `78bc2d6` shared 48dp Back control on child screens, density-aware spacing replacing 17 raw-pixel padding calls in 11 screens, safe Android instrument navigation flow.
- `dbf3361` Basic and Developer Settings also use UiChromeActivity (no duplicate nested shells).
- `5d427c6` GeneralSettingsController owns all writes to pre-existing CallRuntimePreferences; Activity is only presenter. No new preference keys or migrations.
- `d9a23db` and `da32f37` extend settings no-mutation matrix to both new screens, use active locale for expected titles.
- `0afea79` adjust static navigation contract to verify that Controller, not View, owns CallRuntimePreferences writes.

## Verified gates vs open work
- `aicalls-20261107-ui-nav-hub-host-gate-v1` DONE/0 — AndroidTest compile, full host 275 Python, lint/APK, anti-friction PASS.
- `aicalls-20261107-ui-home-direct-settings-gate-v1` FAILED — obsolete F-039 test expected the removed accordion. Followup `aicalls-20261107-ui-home-f039-contract-repair-gate-v1` DONE/0 — focused 6, 276 Python, APK and anti-friction PASS.
- `aicalls-20261107-ui-shared-chrome-host-gate-v1` DONE/0 — 278 Python, AndroidTest compile/APK/lint, anti-friction PASS.
- `aicalls-20261107-ui-theme-host-gate-v1` DONE/0 — 280 Python, AndroidTest compile/APK/lint, anti-friction PASS.
- `aicalls-20261107-ui-polish-child-chrome-host-gate-v1` DONE/0 — 282 Python, AndroidTest compile/APK/lint, anti-friction PASS.
- `aicalls-20261107-ui-chrome-controller-host-gate-v1` FAILED — obsolete source test still expected direct preferences in Activity after intentional controller extraction; AndroidTest compile/APK PASS. Fixed in `0afea79`; exact `aicalls-20261107-ui-controller-contract-repair-final-gate-v1` DONE/0 — **284 Python**, focused tests, AndroidTest compile/APK/lint and anti-friction PASS.
- **Physical S22 new navigation/theme: PASS 15/15**, exact task `aicalls-20261107-ui-full-navigation-s22-acceptance-v1` DONE/0. S22 SM-S906B two consecutive IDLE probes, no campaign/pending retry, same signer, install -r of app + test APK without clearing data; six Android test classes 3+2+3+2+3+2 PASS, phone still IDLE after, no real calls. Previous 10/10 applies to the older UI revision only.

## Architecture
Navigation, theme and presentation chrome belong to `app/ui`; UI navigation only starts existing screen Activities, never dials, grants, stops or changes a live call. General Settings presenter delegates write operations to a thin existing-store Controller; the phone runtime, Samsung/Shizuku PCM, speech and model engines, identity vault, audio clips and profile storage remain untouched.

**Remaining UI/business coupling is not fully removed:** AudioEngineSettingsActivity still owns TTS readiness/downloader+widgets; ConversationHistoryActivity owns playback+widgets; CampaignToolActivity and IncomingAssistantSettingsActivity own platform operations as well as rendering. These require their own behavior-preserving controller extraction and tests, not an unverified broad rewrite.

## Physical acceptance outcome
The exact Samsung S22 passed the preflight and all **15/15** instrumented UI tests. Test classes: AndroidAppNavigationContractTest (3), AndroidAppBottomNavigationFlowTest (2), AndroidMenuVisualContractTest (3), AndroidUiAccordionContractTest (2), AndroidSettingsNavigationMatrixTest (3), AndroidLiveChatDockContractTest (2). This covers new tab routing, back header, 10 settings screens, secure history, preserved preferences/skills, contrast, live chat and no hidden START/STOP. Same APK signing lineage, installation with -r, no app data wipe, no calls. **Owner's subjective visual/ergonomic review remains OPEN**; test PASS is not aesthetic sign-off.

## Not included in this UI sprint
F-103 (complete PL/EN screen localization and Auto/PL/EN choice) stays OPEN, despite foundation resources and localized chrome. F-080 (continuous complete recording/player) remains OPEN. Full MVVM/presenter extraction for all existing screens not claimed. No physical call or model inference performed.

## Next
Next: manual visual and ergonomic inspection of the installed S22 build, full Auto/PL/EN UI localization (F-103), and dedicated presentation/controller extractions for remaining mixed Activities. Preserve all accepted receipts; no need to repeat 15/15 without a new code change or issue.
