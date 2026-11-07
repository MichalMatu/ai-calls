# AI Calls — UX/controller stage, localization, and device QA handoff (2026-11-07)

## Current user goal
Finish coherent, professional UX, permit screenshots on every screen permanently, fix Calls Back and individual conversation deletion, separate storage/phone/media behavior from views, localize all PL/EN pages with an Auto/PL/EN picker, and physically verify the next app on exact Samsung S22. Use GitHub Connector + selected Mac Local Agent, main only. No Codex/Work/GitHub Actions.

## Accepted source and host receipts
- Clean Mac preflight `aicalls-20261107-screenshots-history-preflight-v1` DONE/0 started `f764fec`.
- `a94ae8e`: all eight FLAG_SECURE removal points, global source no-screenshot blocker test and permanent owner no-blocker policy in AGENTS.md. Exact `aicalls-20261107-screenshot-no-block-host-gate-v1`: DONE/0, 286 Python, APK/lint/AndroidTest compile, anti-friction PASS.
- `8baf386`: Calls back-to-list, individual confirmed deletion for private completed archives, 10dp action spacing, Auto/PL/EN UI picker. The initial combined gate FAILED Android 16 GestureBackNavigation lint; historical result unchanged.
- `340f70e`, `5ff1563`, `5957121`: AndroidX lifecycle-aware Back dispatcher, ComponentActivity compatibility and unchanged permission callback signature semantics. Historical failed compile receipts retained.
- `aa46fea`: update stale child-toolbar source test. Exact `aicalls-20261107-ui-back-contract-repair-host-v1`: DONE/0, 292 Python, AndroidTest compile/APK/lint, friction PASS.
- `43dd95b`, `05b3bd6`, `bd5802f`: separate `ArchivedConversationPlayer` from Calls views; typed off-call permission/Telecom/WAV check and MediaPlayer lifecycle. Translate all Calls details/playback states PL/EN, make existing audio instrumentation locale-neutral. Exact `aicalls-20261107-calls-ui-player-locale-full-gate-v2`: DONE/0, 293 Python, focused JVM, AndroidTest compile/APK/lint, friction PASS.
- `8b4e16c`: separate `IncomingAssistantSettingsController` from screen policy/Telecom/service writes, translate whole incoming assistant settings screen PL/EN; exact `aicalls-20261107-incoming-controller-ux-host-v1`: DONE/0, 295 Python, AndroidTest compile/APK/lint, friction PASS.
- `e4e60c2` + `b33bdaa`: separate `ConversationToolsSettingsController` from screen, localize the tools settings and typed tool-status messages without changing ConversationToolExecutor or its authority. Exact task `aicalls-20261107-tools-ui-controller-locales-gate-v1` **PENDING until receipt read**. Do not claim acceptance before that.

## S22 attempt and blocker
Exact `aicalls-20261107-ux-player-s22-acceptance-v1` FAILED **before install**: source gate green, but exact S22 ADB discovery returned `exact_s22_adb_transport_unavailable`. Debug stdout then reported wrong model because empty serial; the discovery was the real blocker, not an alternate phone. No APK installed and no phone call/delete performed. Exact follow-up `aicalls-20261107-s22-adb-transport-diagnosis-v1` DONE/0: 0 connected, 0 offline, exact S22 absent over USB, no S22 mDNS advertisement. S22 physical QA for this source is **QA_PENDING / BLOCKED_BY_ADB**. Prior UI 15/15 belongs to older `f764fec` and cannot qualify these changes.

Only when exact SM-S906B hardware serial RFCT70L7E8J is visible again: confirm 2× IDLE/no campaign/retry, same APK signer, `adb install -r -t` app and test APK preserving private data, and run safe instrumentation classes. Include screenshot test (memory-only), Calls Back and deletion with synthetic archive only, synthetic silence WAV playback, navigation/theme/skills/config no-mutation tests. Never start a real call or delete any real conversation. On a failed safety check, stop without install.

## What is separated
- `AppNavigation`, `UiChromeActivity`, `AppBaseActivity`, `AppLanguage`: UI chrome and UI-only locale, separate from STT/TTS/Call.
- `GeneralSettingsController`: CallRuntimePreferences writes, old format preserved.
- `RecordedConversationHistory`: private timeline read/delete with COMPLETE+END and symlink/path protection.
- `ArchivedConversationPlayer`: media/telephony playback gate; history view renders typed statuses only.
- `IncomingAssistantSettingsController`: existing policy store, Android phone account selection and policy-changed notification; screen purely selects/render feedback.
- `ConversationToolsSettingsController`: existing enabled capabilities and typed diagnostics; screen renders choices. Existing effect grant, STOP, tool validation remain elsewhere and unchanged.

## Remaining work, in order
1. Physically connect the exact S22, rerun guarded install and 19+ UI tests on the **fresh green source**. Do not downgrade security/device identification to get a passing report.
2. Full F-103: migrate legacy hardcoded strings in all user-facing Activities and test Auto/PL/EN on S22. A static inventory found particularly heavy user-facing text in Main (381 lines / ~19 obvious literals), CampaignTool (474/~30), AudioEngineSettings (348/~24), ConversationSkillLab (177/~13), ConversationPreparation (91/~11), ConversationSkills (188/~7), and standalone diagnostics/probes. Counts are an incomplete regex lower bound, not a full translation audit.
3. Further presentation/controller extraction for preparation/skills, engines and UI status, then carefully for CampaignToolActivity without altering app-owned permission, grant, Call, RX/TX and effects.
4. Visual S22 owner acceptance: check dynamic button gaps (some buttons are inserted after setContentView), 48dp touch targets, text overflow/long font sizes, scroll/system insets, nested Back and screenshot access. An automated test matrix is not proof of aesthetics.
5. F-080 continuous full-call recording remains out of scope. Existing turn WAVs must never be misrepresented as full recording.

## Evidence integrity
No GitHub Actions, Codex, Work, new branches, force-push, credential leaks, private audio/text export, real call or blanket permission changes. Failed historical task receipts remain immutable; no source gate equals hardware QA.
