# AI Calls — screenshots, Calls navigation/deletion, spacing, language choice (2026-11-07)

## Owner requests and source baseline
Owner explicitly requires screenshots to work on **every screen** and bans reintroduction of their blocking flags. Calls detail -> Back must return to Calls list, not Home. Single completed conversation must be deletable from Calls with all local transcript/audio. Buttons need whitespace. Work on PL/EN and separation of UI/business logic continues, without changing Telecom/STT/TTS/AI. Start clean main `f764fec0f6288ba89be4e2e45a2e2e8ecc8e590e`; Mac preflight `aicalls-20261107-screenshots-history-preflight-v1` DONE/0 exact HEAD, clean branch, main matches origin.

## Changes
- `a94ae8e`: removed `FLAG_SECURE` from eight Activities; source-wide screenshot regression test; owner prohibition in AGENTS.md and F-104 blocker. Nothing about IdentityVault encryption, automatic export or call authorization changes.
- `851ac575`: RecordedConversationHistory.canDelete/deleteCompleted for *completed* private archives only, canonical path boundary and no-follow-links recursive deletion; Calls list adds per-session Delete with confirmation. History detail Back now returns to Calls list; proper API33+ gesture callback and legacy compatibility; common chrome delegates child Back. New history strings in PL/EN. Test private synthetic sessions, never actual conversations.
- `6104a53f`: UiChromeActivity adds 10dp between imperative action buttons without touching listeners; Home action gap 12dp and independent supervised entry; UiDesign min 48dp.
- `3736e98f`: blockers F-105 (Back), F-106 (Delete), F-107 (spacing), synthetic on-device test with unique completed fake session only and JVM checks of incomplete sessions and traversal/symlink.
- `cb991825`: AppLanguage selector Auto/PL/EN in SettingsHub; on API33+ platform LocaleManager, pre-33 lightweight local locale wrapper for main Chrome/SettingsHub, locale_config. This only changes UI language, not STT/TTS/AI settings; existing hardcoded texts on old Activities are still part of F-103 OPEN.
- `348e2896`: Android test checks screenshot in private memory only, no image export; add locale-neutral history assertions.

## Safety
Explicit deletion is a one-session irreversible action after a confirmation; allowed only when `END` event and `COMPLETE` marker exist. Do not remove active/incomplete audio evidence. The private filesystem never leaves the device; Java NIO walkFileTree does not follow symlinks. UI screenshot ability belongs to the device owner; never automatically export captures or private transcripts. Back never dials, modifies grant, or ends another call.

## Verification / gate ledger
- Earlier UI shell `f764fec`: host 284 Python, AndroidTest compile, APK, friction PASS; S22 15/15 PASS *before this sprint*.
- This sprint's screenshot gate `aicalls-20261107-screenshot-no-block-host-gate-v1`: exact result **PENDING** until immutable receipt is read.
- Combined source host gate **PENDING**; must compile AndroidTest, run full `bash scripts/verify_host.sh`, focused `RecordedConversationHistoryTest`, Python screenshot/nav/spacing/i18n contracts and anti-friction.
- Device S22 **QA_PENDING**: exact idle twice, no campaign/retry, signer match, install -r preserving data. Run `AndroidScreenshotAllowedTest`, `AndroidHistoryNavigationDeletionTest`, `AndroidAppNavigationContractTest`, `AndroidSettingsNavigationMatrixTest`, prior live chat and navigation tests; never start calls or erase real saved sessions. Manual screenshot and layout review required.
- F-104/105/106/107 are FIXED_IN_SOURCE only pending tests, not VERIFIED. F-103 full localization remains OPEN. F-080 full continuous audio remains OPEN.

## Remaining work
Full PL/EN extraction for existing legacy Activity text and language-aware error messages; deeper separation of CampaignToolActivity/ConversationHistoryActivity/AudioEngineSettingsActivity UI from controllers; visual evaluation of all screens on S22. Before any next sprint read current main, actual Host Ops receipts and current BLOCKERS.md. Do not re-enable secure flags.
