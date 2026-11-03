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
- Full source QA submitted to Mac Local Agent as `aicalls-20261103-ui-accordion-source-full-qa-v1` in `MichalMatu/local-agent:agent-control`; pinned source `64ce6fbae5201c8246b2b80dd8f35cf0373505ad`. Runs anti-friction audit, complete `scripts/verify_host.sh` (all app module test/lint/assemble, Python), and a diff allowlist against prior `165e5deb`. **Read actual result** before declaring PASS. No APK installation or instrumentation run in this task.
- Existing `F-035` in-call STT recovery, remote audible TX and external CLIR completion are **still OPEN**. Do not consider UI QA a substitute for them.

## Next safe step

1. Read `task-results/aicalls-20261103-ui-accordion-source-full-qa-v1.json` from Mac host installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Fix compilation/QA regressions if any without changing phone logic.
2. If source gate passes, consider separately checking the UI instrumentation on the exact S22 in `IDLE` state only, without starting any call or enabling capture. This requires a verified installed test APK and the existing tool permissions. Do not evade tool denial by alternate execution paths.
3. Do not prematurely claim UI field acceptance before it is observed.
