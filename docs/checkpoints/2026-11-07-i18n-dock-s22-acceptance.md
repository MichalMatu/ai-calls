# AI Calls — S22 acceptance: localized light chat dock

Date: 2026-11-07 Europe/Warsaw. Source: `main@d4ca11a52c2e772ae356111bc77b1e62114f9c70`. Mac Local Agent, exact Samsung S22 `SM-S906B`.

## Receipts

- `aicalls-20261107-i18n-dock-s22-reconnected-preflight-v2`: **DONE/0**, exact S22 identity, two consecutive IDLE checks, inactive campaign, `pending_retry=false`, installed app confirmed.
- `aicalls-20261107-i18n-dock-s22-reconnected-acceptance-v2`: FAILED before device mutations; stale wrapper wrongly expected dirty files already committed to `main`. No app defect.
- `aicalls-20261107-i18n-dock-s22-reconnected-acceptance-v3`: **DONE/0**. Verified same Android signing cert, `adb install -r -t` preserved user data, tested without calls or changing models:
  - `AndroidLiveChatDockContractTest`: **2/2 PASS** (light dock, centered 48dp toggle, PL/EN resource lookup).
  - `AndroidMenuVisualContractTest`: **3/3 PASS**.
  - `AndroidUiAccordionContractTest`: **2/2 PASS**.
  - `AndroidSettingsNavigationMatrixTest`: **3/3 PASS**.
  - **Total: 10/10 PASS**. Posttest S22 IDLE, no campaign, no pending retry, zero phone calls.
- Mac source gate `aicalls-20261106-i18n-dock-foundation-gates-v1`: DONE/0, 275 Python, AndroidTest compile, APK, lint and anti-friction PASS. Tests need not be repeated for documentation-only update.

## Remaining

F-103 **OPEN**. The resource catalogs and localized dock passed, but Auto/Polski/English selection and migration of 19 subcreens are not yet finished. UI locale must not change STT, TTS, conversation language, grants or phone behavior. No complete bilingual UI claim. Manual final UX approval of all screens is still pending.
