# AI Calls — UI cleanup, first accordion pass (2026-11-03)

## Scope and recoverability

- **Base:** `main` at `165e5deb0d63688a14b9bfc6e1d06d9d96346c35` (prior organizational freeze complete). Recoverable safety checkpoint remains `checkpoint/2026-11-03-f035-s22-baseline` pinned at `6d3db9159b24d91aa28f2db6162d3881a0a428f0`.
- **UI-only source change:** `64ce6fbae5201c8246b2b80dd8f35cf0373505ad` — `MainActivity.kt`, `CampaignToolActivity.kt`, new `UiAccordionSection.kt` and `AndroidUiAccordionContractTest.kt`; no changes to call engine, STT, Gemma, TTS, transport, grants, persistent runtime preferences or service.
- Main-first repository policy applies. No Codex, Work or GitHub Actions. Mac Local Agent is used for build/test, not as part of application runtime.
- **No live phone calls** and no grant/recording/phone-number changes in this UI cleanup.

## Product decisions

**Main screen (always visible, in existing order of user functions):**
- Project heading and current `runtimeSelectionSummary()` status (configuration/model summary, not a claim of live carrier outcome).
- Navigation to **Campaign tools**, existing supervised one-tap AI entry, and real **STOP AI** action. Their existing listeners/authorization/media checks are unchanged.

**Collapsed, non-destructive screen sections:**
- **Tryb rozmowy i model:** audio mode spinner and conditional text LLM provider spinner, preserving existing preference storage.
- **Model Gemma 4:** existing import, download, progress/cancel and model-source information, preserving asynchronous operation semantics.
- **Uprawnienia i konfiguracja:** microphone permission and phone enrollment controls, unchanged.
- **Diagnostyka developerska:** all existing Shizuku/capability/transport-probe buttons; no fake probes are promoted as proven capabilities.

**Campaign tools activity (always visible):**
- Authorization, START authorized CLIR, real **STOP AI** and **revoke campaign grant**; also the existing status.
- Collapsible dialer-role setup, supervised audio mode and optional private evidence/playback/transcript controls. No control is removed, moved to another task owner or turned into a stub.
- STOP and revocation remain accessible without expanding any section. Android itself must still grant the dialer role. Private RX/TX/transcript rules are unchanged.

**Reusable implementation:** `UiAccordionSection` uses a simple native Android Button and LinearLayout with discoverable tags for UI tests, a descriptive expand/collapse accessibility label, and no runtime state persistence or external dependencies. Expansion is an appearance-only action. Each screen retains the same old control views and their original click listeners.

**Not part of this sprint:** profiles (fast/standard/experimental), comparing different LLM/audio backends, a native speech-to-speech implementation, AI/skills refactor, new campaign authority or any new limits. Those remain deliberate follow-up design ideas; avoid implying they exist.

## Regression and QA

- New instrumentation source `AndroidUiAccordionContractTest` checks that real STOP, primary access and revocation stay visible, setup/model/diagnostic controls still exist, the sections toggle, and essential actions remain visible after toggling. It clicks **only** accordion toggles.
- Mac Local Agent `aicalls-20261103-ui-accordion-source-full-qa-v1` **DONE/0**, pinned source `64ce6fbae5201c8246b2b80dd8f35cf0373505ad`: anti-friction PASS, complete `scripts/verify_host.sh` **host_quality_gate_green=true**, 3 successful Gradle build phases, **244 Python tests PASS**, and exact `UI_ONLY_SOURCE_DELTA=PASS` (**four UI/test files only** compared to `165e5deb`). No S22 interaction in this task.
- Mac Local Agent `aicalls-20261103-ui-accordion-s22-nocall-instrumentation-v1` **DONE/0 as a safely deferred deployment**, not a physical UI PASS: application and AndroidTest APK both built PASS, but `EXACT_S22=false` for expected `RFCT70L7E8J`/`SM-S906B`; `DEPLOY=DEFERRED_UNAVAILABLE_S22`. **No install and no UI instrumentation tests executed.** No claim about app behavior on handset or call grant changes.
- Existing `F-035` in-call STT recovery, remote audible TX and external CLIR completion are **still OPEN**. Do not consider UI QA a substitute for them.

## Physical Samsung S22 UI acceptance — PASS (after reconnect)

- Read-only Mac Local Agent `aicalls-20261103-s22-post-reconnect-preflight-v1` **DONE/0**: the exact `RFCT70L7E8J` / `SM-S906B` reappeared, phone **IDLE**, AI Calls the default dialer, no active app-owned campaign service.
- `aicalls-20261103-s22-ui-install-and-offcall-flow-v2`: pinned UI app `64ce6fbae5201c8246b2b80dd8f35cf0373505ad`, `APK_AND_UI_TEST_BUILD=PASS`, **`APP_INSTALL=PASS`** and **`TEST_INSTALL=PASS`** with `adb install -r` (no `pm clear` / uninstall). An unnecessarily fragile diagnostic attempted to hash private grant/continuation files through incorrectly passed `run-as sh -c` quoting; this failed to *read the fingerprints*, not evidence of missing or modified grants. The task safely deferred actual instrumentation, returning rc=75. It did **not** revoke, clear or replace grant files.
- `aicalls-20261103-s22-grant-readonly-hash-diagnostics-v1` **DONE/0**: direct `run-as <package> sha256sum <known path>` succeeded for both existing app-owned preferences and correctly detected S22 IDLE, without publishing hash values, grants or personal data. Use this validated read-only command pattern for future checks.
- Final **`aicalls-20261103-s22-ui-four-flows-instrumentation-v3` DONE/0** on the installed APK and test APK:
  - `UI_ACCORDIONS=PASS` — **2** native Android UI instrumentation tests; confirms visible real STOP and preserved authorization/revocation actions, hidden settings retain controls and expand/collapse correctly.
  - `DIALER_READINESS=PASS` — **1** test: real Android dialer role and exact-call-controller prerequisite without dialing.
  - `GEMMA_READINESS=PASS` — **1** test: locally stored Gemma 4 model recognized as READY, no inference or microphone.
  - `ONE_TAP_OFFCALL=PASS` — **1** test: protected one-tap flow safely reports readiness when no call exists, without placing/attaching a call.
  - **Total 5 physical device instrumentation tests PASS.** `AUTHORIZATION_PRESERVED=true`, `CONTINUATION_PRESERVED=true`, `AFTER_IDLE=true`, `AFTER_ROLE_READY=true`; no audio/media export and no live call.
- **Acceptance split:** `UI_SOURCE_FULL_QA_PASS`, `S22_APP_INSTALLED_PASS`, `S22_OFFCALL_UI_FLOW_PASS`. **NOT** equivalent to `IN_CALL_STT_RECOVERY_PASS`, `FAR_END_TX_AUDIBLE_PASS` or `CARRIER_CLIR_EFFECT_PASS`; these remain OPEN.

## Next safe step

1. Preserve this physical UI acceptance; **do not repeat** installation or the same five tests unless UI code/device state changes.
2. Optional UI polish should be verified against unchanged STOP, grant/revocation, callback and model preference behavior. The already-safe current UI is installed and user-visible.
3. Model voice, audio/AI engine performance, in-call F-035 recovery, independently audible TX and actual CLIR effect require **separate** evidence and a separately permitted/authorized external call. Do not infer them from off-call UI tests or bypass previous tool refusals.
