# AI Calls — minimal UI / Tabler / one-route navigation, S22 acceptance (2026-11-07)

## Goal and implementation
Owner's six approved UI corrections, implemented with behavior-preserving presentation changes:
1. Home no longer duplicates Calls, Lab or Settings, and the live chat no longer duplicates History. Four always-visible bottom tabs remain the only routes to those destinations. Home retains the unique authorized task, campaign, STOP and supervised off-call assistant actions.
2. Calls list renders each session as a compact horizontal row with an incoming/outgoing/unknown **Tabler** icon, short date and message count, a trailing chevron, plus a separate 52dp red trash icon for completed sessions. The existing safe `history.canDelete()` and `confirmDelete()` gates are unchanged. Details reuse the common toolbar/gesture Back, removing the redundant "All conversations" button. Tests delete only a unique synthetic completed archive.
3. Eleven local Tabler Icons outline vector drawables, all with their original MIT attribution in `app/src/main/assets/licenses/tabler-icons-MIT.txt`; no font and no runtime network.
4. Flat buttons: disabled default platform `stateListAnimator`, elevation and translationZ for shared Button theme, Home actions, chat dock and bottom navigation; shared gentle stroke and click ripple.
5. Settings' "Language and appearance" is a compact collapsible row with an Auto/PL/EN native dropdown, not three always-visible radio rows. No unsupported appearance option added.
6. `UiSelect` is the single platform-spinner adapter/treatment (padding, font, native popup item, surface and Tabler chevron) used in General, Audio Engine, Skills Lab and Language UI. Controllers and preferences remain separate and unchanged.

Source commits in order: `34965f9` (Home/chat/flat controls), `82b57b9` (Tabler icons/MIT license), `537f6ef` (Calls row, Back and synthetic tests), `9e37ed8` (UiSelect and language), `692a1d6` (migrated 5 outdated Python contracts, added static UI contract), `6a3aad0` (updated physical navigation matrix and comment). Final tested source **`main@6a3aad0b2a8d5671305634c8ed432ae663f27d34`**.

## Verified quality receipts
- Read-only original workspace/device preflight `aicalls-20261107-ux-minimal-preflight-v1` DONE/0: clean main, origin exact, S22 visible.
- Historical initial host `aicalls-20261107-minimal-ux-host-gate-v1` **FAILED** only in outdated contracts requiring the intentionally removed shortcuts; Android compilation succeeded. Preserve failure.
- Updated host `aicalls-20261107-minimal-ux-host-gate-v2`: **DONE/0**, 15 focused + **300 Python**, AndroidTest compilation/APK/lint and anti-friction PASS.
- Final host `aicalls-20261107-minimal-ux-final-host-v3`: **DONE/0**, full **300 Python PASS**, AndroidTest compile, JVM/Gradle APK, lint, audio audit and anti-friction PASS, exact clean source `6a3aad0`.
- Historical device `aicalls-20261107-minimal-ux-s22-install-22tests-v1`: **FAILED before install** because an operator diagnostic used unsupported `cmd role holders`. It did verify the exact S22 and twice IDLE, no active campaign or queued retry. No APK mutation.
- Diagnosis `aicalls-20261107-s22-dialer-role-diagnostics-v1`: **DONE/0**, Samsung supports `cmd role get-role-holders` and `cmd telecom get-default-dialer`. Role source corrected, no security bypass.
- **Final real device `aicalls-20261107-minimal-ux-s22-install-22tests-v2`: DONE/0**. Exact physical S22 SM-S906B and registered hardware serial verified; twice IDLE, no active campaign/retry, telecom dialer role/default consistency PASS, unchanged installed/new APK signing certificate PASS. App and test APK installed with `adb install -r -t`, preserving existing user data. **22/22 physical Android instrumentation tests PASS**, including Home 4 tabs and no duplicate links, main design, 3 Settings screen tests, language accordion/dropdown, chat chevron dock, screenshot capture in memory, safe synthetic Calls row/Back/trash confirmation, synthetic silent WAV player, read-only Incoming and Tools screens. Post-test phone safety PASS. Zero real calls, no private audio/screenshots exported.

## Open boundaries
- This is native UX/technical acceptance, not owner subjective visual acceptance. F-107 dynamic gaps/long-font/overflow and owner screenshot review remain OPEN.
- F-103 full PL/EN migration of legacy Campaign, Main, Engines, Skills/Preparation/Diagnostics remains OPEN; locale selector does not alter STT/TTS/AI language.
- More controller/view separation in Campaign/Engine/Preparation/Skills remains future work; no modifications to authorization, call ownership, effects, PCM/audio path, tool permissions or storage. F-080 full continuous-call recording remains separate and OPEN.

Do not re-run this accepted S22 matrix on unchanged source. Continue aesthetic review from owner's screenshots and concrete issues, then a single new UI source gate and hardware test for any corrections.
