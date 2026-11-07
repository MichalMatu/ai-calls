# AI Calls — UX polish sprint 2: real Samsung S22 acceptance (2026-11-07)

## Source and scope
**Exact tested source:** `main@b4370ace1151ef86edf4e6168d7368a15de5365f`. Existing `main` branch and clean Mac host checkout; no new branch, no GitHub Actions, no Codex or Work.

Main presentation-only changes:
- `UiDesign`: reusable action tone PRIMARY/SECONDARY/DANGER, cards, spacing constants, captions and 48dp touch controls.
- Skill Lab: START primary; STOP shown only during active test; rating shown only once results exist.
- Language Models: model cards, model readiness and selection state, installation-dependent download/import/selection actions, transfer-only cancellation, technical details under disclosure.
- Conversation Preparation: card-based context, skills and secure-vault references; one direction dropdown and clear action hierarchy.
- Audio Engines: separate cards, unsupported providers behind an expandable list, download cancel only while busy, compact explanation.
- Campaign: visually distinct START/STOP/revocation while keeping original exact authorization and STOP logic; compact idle live chat.
- Skill presets and legacy accordion controls reuse shared select and action styling.
- `SettingsHubActivity`: direct one-click language dropdown from earlier `9e8f6f3` already included in this tested source.
- Regression tests for presentation-only transitions and readonly screen navigation; anti-friction checks updated to accept the evidence-checkbox inside the new action card, without weakening evidence scope checks.

## Real quality receipts
- Local Agent `aicalls-20261107-ux-polish-final-host-v4`: DONE, exit 0. **304 Python unit tests PASS**, AndroidTest compilation, JVM tests, APK, lint, anti-friction PASS. `UX_POLISH_FINAL_HOST_GREEN=PASS`.
- Local Agent `aicalls-20261107-ux-polish-s22-install-48tests-screenshots-v4`: DONE, exit 0. Physical Samsung `SM-S906B`, serial `RFCT70L7E8J`, visible on USB. Verified twice IDLE, no active app-owned campaign, no queued retry, exact default-dialer role and unchanged APK signing certificate. Installed debug app + AndroidTest APK via `adb install -r -t` preserving application data.
- **27/27 off-call S22 UI tests PASS** (including 5 UI polish contract tests, existing navigation, settings, chat, archive synthetic-only delete/playback tests).
- **15/15 safe visual screen audit tests PASS** and **6/6 expanded UI state tests PASS**, total **48 passing S22 tests**.
- Saved **37 JPEG screenshots** (main screens at top/middle/bottom + expanded dropdowns/panels), private to the owner's Mac:
  `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-polish-sprint2`
- Private review ZIP:
  `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-polish-sprint2/AI-Calls-S22-UX-polish-sprint2-private.zip`
- The screenshots were **not** published to GitHub or sent as logs; privacy and non-mutating scope retained. No live phone calls, no call audio, no real conversations exported.

## Historical failures and resolution
- `aicalls-20261107-ux-polish-final-host-v2` failed on legacy campaign source tripwire after evidence checkbox moved to the new card; Kotlin test changed to preserve checkbox wiring check.
- `aicalls-20261107-ux-polish-final-host-v3` failed on a second static friction tripwire; analyzer now checks retained checkbox in the new card and remains green.
- Earlier S22 transport preflight failed with phone disconnected; final retry succeeded only after exact physical USB verification.
- The first S22 task had unsupported time limits, corrected in the final task.
- These failures should **not** be re-reported as ongoing current failures.

## Remaining next step
- This verifies functionality, screenshot capture and layout-geometry workflow, **not subjective visual acceptance**. The owner can upload the private ZIP for a pixel-level comparison of all 37 screenshots (no automatic sharing).
- Continue targeted corrections for any visible overflow/crowding, typography, high font scale and older secondary screens. Preserve existing audio, telephony, consent and STOP guarantees.
- F-103 legacy PL/EN localization, F-107 owner visual review and F-080 end-to-end live call recording remain open.
- Before another code sprint, re-read current HEAD and this checkpoint; **do not rerun accepted unchanged-source hardware tests**.
