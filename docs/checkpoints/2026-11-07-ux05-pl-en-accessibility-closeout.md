# AI Calls — UX-05 / F-103 PL/EN and UI accessibility closeout (2026-11-07)

**Status: SOURCE_PASS / HOST_PASS / PHYSICAL_S22_AUTOMATED_PASS / LOCALE_SCREENSHOT_PASS.** Separate legacy Sprint 6 and UX-01–04 results remain closed on their own tested SHAs.

## Exact source and scope

- Initial UX-05 source was developed from clean `main@b2e1957ca6e8446af3f500a81473c993f0ebe773`; core UX-05 production changes end at **`1464c7950f0a75d14f590eff6ca254ab9844090f`**, regression/AndroidTest work at `c9d48b0239b3a2cb092877f6a00b38286f636204`, corrected source contract at `340579311f61ce6b35d6998cef281797f48addbb` and screenshot-only AndroidTest refinement at **`9da1468b4aa4336822a5ffd6390817848830a9f8`**. Commits after `c9d48b0` did **not** change app production code.
- PL/EN: both `values/strings.xml` and `values-en/strings.xml` contain **553 matching keys**, including 160 newly added pairs across the manual dialer, Home, Kampania, audio engines, model metadata and transfer, Skills editing, supervised pilot and live chat. Existing UI language Auto/PL/EN remains independent of STT/TTS and conversation instructions. Historical data/skill prompts and actual TTS sample speech remain deliberately Polish source data, **not** untranslated UI labels.
- Accessibility: common headings have Android heading semantics, duplicated decorative navigation label is skipped, dynamic Home/Campaign/supervised status uses a polite accessibility live region, existing visible focusability, labels and screenshot permission retained. No global screenshot-blocking flags. **Automated checks do not replace a separate manual TalkBack audit**.
- Not changed: Android Telecom role/Call ownership, call authorization or revocation, STOP, external-effect policy, full-TX-before-end_call ordering, frozen Shizuku/PCM/PFD RX/TX, private archives, model download/selection ownership or Android TTS speech locale.

## Mac receipts — actual evidence

| Receipt | Result |
|---|---|
| `aicalls-ux05-manual-call-locale-host-green-20261107-v1` | **DONE/0**: earlier 334 Python, 2 focused tests, anti-friction, AndroidTest/APK PASS |
| `aicalls-ux05-multiscreen-source-gate-20261107-v1` | **DONE/0**: 7 targeted tests, Kotlin/AndroidTest/APK, anti-friction, clean Mac |
| `aicalls-ux05-final-host-gate-20261107-v1` | **DONE/0**, source SHA `c9d48b0`: 10 focused PL/EN tests, **337 Python**, `bash scripts/verify_host.sh` with `host_quality_gate_green=true`, friction, AndroidTest and both APK assemblies PASS |
| `aicalls-ux05-corrected-parity-gate-20261107-v1` | **DONE/0**, `3405793`: corrected placeholder/literal scanner, 10 focused tests PASS, friction PASS, verified no app production changes |
| `aicalls-ux05-screenshot-rendered-view-build-20261107-v1` | **DONE/0**, `9da1468`: new screenshot instrument compiled, app production byte-identical to `c9d48b0`, clean Mac |

The pre-existing Sprint 6, UX-01–04 full tests were not rerun without reason. No new product blocker was proven by UX-05.

## Physical Samsung S22 receipts — distinct from source

- `aicalls-ux05-s22-readonly-qualification-20261107-v1` **DONE/0**: exact `SM-S906B/RFCT70L7E8J`, 2× IDLE, no active campaign or pending retry, dialer role consistent.
- `aicalls-ux05-s22-targeted-ui-acceptance-20261107-v1` **DONE/0**: installed application and AndroidTest APK via `adb install -r -t` with exact preexisting certificate match and **owner data preserved**. New `AndroidUx05LocaleAccessibilityTest` **2/2 PASS on S22**. Exact model/serial and post-test IDLE/no campaign rechecked. No real Call, deletion, or external effect.
- Original app locale `pl` read without mutation and checked separately by `aicalls-ux05-s22-locale-original-readonly-20261107-v1` **DONE/0**.
- **Final screenshot evidence:** `aicalls-ux05-s22-private-screenshot-instrumentation-20261107-v2` **DONE/0**: Settings and Engines, PL and EN, **4/4 distinct valid private PNG PASS**; original **`pl` restored PASS**, post-test IDLE/no campaign PASS; no screenshots or private data published to GitHub. ZIP on Mac only at `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-ux05/AI-Calls-UX05-S22-PL-EN-private.zip`.
- Two earlier screenshot attempts were **FAILED historical diagnostic evidence, not accepted results**: direct `adb am start` was refused for a non-exported Activity; subsequent instrument relied on an arbitrary 10 KB PNG minimum. Both restored locale to `pl`. Final screenshots use an internal `ActivityScenario` rendering of actual Activity views with PNG signature/hash checks, after a successful recompilation.

**Definition of done for F-103 UI localization and automated accessibility:** verified. Manual TalkBack navigation at large system font settings and non-UX-05 real-call/semantic quality are **not claimed** by these receipts. Full continuous call recording and timeline player remains separate **F-080 OPEN**; authorized live Call/takeover/AI semantics remain separate.

## Handoff

Do not repeat the accepted UX-05 matrix or reopen Sprint 6 cosmetic work. Next independent product work is F-080 full continuous recorder/player (new scope with its own safety and evidence gates), or independently authorized real-Call semantic testing. Continue from the then-current `main`, verify Mac clean, never use old documentation-only SHA as device install evidence. Work only through GitHub Connector + exact Mac Local Agent, no Codex/Work/GitHub Actions/force-push/branches, and use `[skip ci]`.
