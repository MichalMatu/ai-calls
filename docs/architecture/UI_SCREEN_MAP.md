# UI screen and settings contract — Compose migration baseline

Source: `app/src/main/AndroidManifest.xml` and the established MVP Activity inventory. This is a **source inventory**, not proof that physical UI QA passed. No redesign or behavior change is authorized by this map.

## Global navigation and appearance

- Four bottom tabs in `ui/AppNavigation.kt`: **Home**, **History**, **Agent**, **Settings**. Tags: `app-bottom-navigation`, `app-tab:home`, `app-tab:history`, `app-tab:agent`, `app-tab:settings`.
- Child screens use `UiChromeActivity` with `app-child-toolbar` and `app-child-back`. Preserve current Android back behavior, top/status bars, insets, focus/accessibility and active-tab semantics.
- UI is currently programmatic Android Views (`UiDesign`, `UiSelect`, Activity/dialogs), not Compose. Existing strings, Polish/English resources, touch states and instrumentation tags are the visual/behavioral baseline.
- Future Compose boundary: Activity/Compose host + UI state/events → `presentation` presenters/controllers → `domain` contracts/use cases → `data`/Android services/telecom/audio/LLM. Screens must not own persistence or native engine lifetime; do **not** move privileges into UI.

## All manifest-registered Activities (34)

This table lists each entrypoint, its visible controls/sections or diagnostic purpose, and the backend/state boundary to preserve. Paths are relative to `pl.michalmatu.aicallbridge`; the leading dot is the package suffix.

| Android entrypoint | Screen / role | Current structure, controls and navigation | Backend or state boundary |
|---|---|---|---|
| `.MainActivity` | Home | Start task, task list, notebook, incoming settings, STOP, live overlay | AndroidHomeOverviewReader; campaign service; conversation feed |
| `.ui.SettingsHubActivity` | Settings | Model and voice, GSM audio, incoming, prompt, permissions, developer/lab; app and conversation language English/Polish | AppLanguage; media backend mode; navigation only |
| `.ui.AgentAiHubActivity` | Agent | Skills, scenarios, preparation, identity vault, conversation tools | AndroidAgentHubOverviewReader → AgentHubSummaryFormatter |
| `.ui.GeneralSettingsActivity` | Settings / Permissions | Dialer role, incoming mode, default apps, app settings; microphone, phone-state, answer, call and notification grants | Android permission/role APIs; IncomingAssistantPolicyStore; GSM readiness |
| `.ui.DeveloperToolsActivity` | Diagnostics | Developer probes and Lab; supervised pilot where applicable | MainActivityDeveloperProbes; CallMediaBackendMode |
| `.ui.CapabilityDiagnosticsActivity` | Diagnostics | Capability status, routes, probe, detail expansion | Capability diagnostics/report parser |
| `.incoming.IncomingAssistantSettingsActivity` | Settings / Incoming | AI/manual mode, SIM, recording, save and discard changes | IncomingAssistantPolicyStore; telephony/SIM readiness |
| `.session.LocalAdbGsmSetupActivity` | Settings / GSM audio | Refresh readiness, manage pairing, open Android settings, enter pairing code, start host | Gsm media/ADB readiness and host control |
| `.tools.ConversationToolsActivity` | Agent / Tools | Notebook search, filters, expand, playback/details, delete; tool toggles and save | Notebook stores; tool settings controller |
| `.campaign.AndroidCallControlsActivity` | System dial intent | Manual call number/status and explicit call controls | Telecom/campaign service; restricted by call safety gate |
| `.identity.IdentityVaultManagerActivity` | Agent / Vault | Add/edit/remove encrypted identity entries; input visibility and confirmations | Identity vault persistence/encryption |
| `.identity.PhoneEnrollmentActivity` | Enrollment | Phone details save/cancel | Enrollment store; phone validation |
| `.developerrelay.SupervisedCallPilotActivity` | Shell-only diagnostics | Operator-supervised call pilot controls | Privileged/device diagnostics; no default user navigation |
| `.developerrelay.SupervisedCallReadinessProbeActivity` | Shell-only diagnostics | Read-only supervised call readiness | Exact-call/device readiness |
| `.developerrelay.S8LocalSpeechSmokeActivity` | Shell-only diagnostics | Synthetic STT/TTS test | Offline speech test adapters |
| `.developerrelay.S8LocalModelFourTurnSmokeActivity` | Shell-only diagnostics | Four-turn local model test | LiteRT inference adapters |
| `.developerrelay.S8NativeSpeechSmokeActivity` | Shell-only diagnostics | Synthetic PCM → STT → model → TTS | Audio/model test adapters |
| `.campaign.AppointmentMvpActivity` | Task / Appointment | Appointment inputs, SIM, disclosure, authorize one call, STOP, takeover, results | Campaign service; scheduling/authorization |
| `.campaign.ScenarioOutgoingCallActivity` | Task / Legacy compatibility | Legacy outgoing scenario entrypoint | Existing campaign flow adapter |
| `.textagent.LanguageModelActivity` | Settings / Models | Model and voice settings or file-management mode | ModelLibraryScreenActivity UI → ModelVoice presenters + model/transfer stores |
| `.campaign.PhoneTaskWizardActivity` | Home / New task | Scenario, recipients, SIM, execution window, speech language, recording, review, next/back/submit | PhoneTask draft, authorization, scheduler/campaign service |
| `.campaign.PhoneTasksActivity` | Home / Tasks | Task list, options, details, recipient status, skip/restore, retry, conversation, delete | PhoneTask store/work/scheduler; campaign service |
| `.audioengines.AudioEngineSettingsActivity` | Legacy compatibility | Old model/voice settings entrypoint | Subclass delegates to LanguageModelActivity |
| `.debug.UsbDebugPanelActivity` | Diagnostics | USB status, redacted details and read errors | UsbDebugBridge |
| `.DiagnosticProbeActivity` | Shell-only diagnostics | Probe output | Device diagnostics |
| `.GemmaActionRouterProbeActivity` | Shell-only diagnostics | Action routing probe | Model/action backend |
| `.LiveCallReadinessProbeActivity` | Shell-only diagnostics | Call readiness probe | Telecom/media readiness |
| `.developerrelay.ChatRelayProbeActivity` | Shell-only diagnostics | Chat relay probe | Developer relay transport |
| `.conversation.ScenarioLibraryActivity` | Agent / Scenarios | Preset/personal catalog, create, edit, save, delete, prepare outgoing | Scenario persistence and preparation rules |
| `.conversation.ConversationSkillsActivity` | Agent / Skills | Tone choice, personal instructions editor, save/cancel | Skills configuration store |
| `.conversation.ConversationSkillLabActivity` | Lab | Preset, model, case editor, options, start/adaptive, stop, manual send, timeline, reports, diagnostics | Lab runner and model/voice adapters; never real GSM by default |
| `.conversation.ConversationPreparationActivity` | Agent / Preparation | Direction, context notes, vault fields, save/discard | Preparation profiles and vault data |
| `.conversation.ConversationPromptSettingsActivity` | Settings / Prompt | Prompt sections, reference, editor, save/reset | Prompt preference/configuration store |
| `.conversation.ConversationHistoryActivity` | History | Session list/detail, audio playback, extra clips, delete one/all with confirmations | Encrypted history/recordings and playback controllers |

## Nested screens, settings and dialogs (same Activity, no separate manifest entry)

| Owner | Nested surface / controls | Contract for future Compose |
|---|---|---|
| `ui.modelvoice.ModelLibraryScreenActivity` | Normal mode: model, generation, speech input, speech language, voice cards, manage files; file mode: built-in model cards, download/import/remove/select/details, cancel transfer, Parakeet verify/download/cancel/remove, imported/legacy file lists and storage summary | `ModelVoiceProfileController`, `ModelVoiceReadinessPresenter`, `LanguageModelStatusPresenter` are presentation; model catalog, probes, transfer services, integrity and file stores remain infrastructure. `textagent.LanguageModelActivity` is an Intent/manifest compatibility subclass |
| `ui.modelvoice.ModelVoiceSettings` | Model chooser; generation dialog (budget/temperature); STT picker; language picker (PL/EN); TTS voice chooser, engine picker, preview/verification, retry | Keep `ModelVoice*Presenter` pure and use explicit async TTS lifecycle adapter before migrating views. No change to immediate-save, unavailable-voice and cancellation behavior |
| `conversation.ConversationSkillLabActivity` | Setup, advanced options, case management/dialog, active conversation timeline/manual composer, diagnostics and reports | Lab session domain and native model lifecycle must outlive rendered screen safely; maintain emulator/offcall separation |
| `campaign.PhoneTaskWizardActivity` | Multi-step draft/review, time window and SIM, recipient/speech options | Validation/authorization/scheduling live outside composables; no implicit dialing |
| `campaign.PhoneTasksActivity` | Grouped list, details, options dialogs, recipient detail, skip/restore/retry/delete confirmations | Task controller and service own mutations; confirmations remain explicit |
| `conversation.ConversationHistoryActivity` | List/detail, audio playback controls, extra clips, one/all deletion confirmation | Keep recording access, crypto, retention/deletion and audio player outside UI |
| `conversation.ScenarioLibraryActivity` | Preset/personal catalog, editor, save/discard and delete dialogs | Scenario validation and storage belong to domain/data |
| `identity.IdentityVaultManagerActivity` | Entry editor, visibility toggle, remove confirmation | Crypto and secret retrieval never migrate into UI state snapshots/logs |
| `tools.ConversationToolsActivity` | Notebook filter/search/list/details/delete and tool preferences, discard/save | Storage, settings validation and notebook actions remain in adapters/controllers |
| `incoming.IncomingAssistantSettingsActivity` | AI/MANUAL, SIM selection, record toggle, permission, save/discard | Exact-Call and SIM policy remain backend-owned, gated by safety |
| `ui.GeneralSettingsActivity` | Default dialer/permission action and Android settings deep links | Role/permission prompts must remain explicit user-driven Android interactions |

## Current package/module boundaries

`app` is the Android composition root (UI, presentation, Android adapters). Pure JVM domain modules: `conversation-core`, `conversation-tools`. Other dedicated modules: `audio-bridge` (PCM), `privileged-helper` (system), `realtime-client` (transport). Avoid artificial Gradle splits until an actual cycle or test/build isolation need is proven. `module_boundary_gate.py` enforces Gradle edges, Android-free JVM and the backend/presentation reverse-import checks; it does not prove all lifecycle logic has been extracted.

## Migration acceptance checklist

1. Preserve all manifest entrypoints, exports/permissions and legacy compatibility shims.
2. Preserve four tab destinations, screen/button tags, strings, accessibility and back navigation.
3. Characterize every persistent setting and dialog save/cancel/rollback path before swapping view layer.
4. Keep exact-Call/SIM safety, user authorization, STOP/takeover and recording privacy in backend.
5. Test presentation logic without a View/Activity. Gradle JVM and app tests, instrumentation and S22-safe smoke must be pinned to the **new exact SHA**; do not infer results from `main`.
6. A manifest change must update this document: `scripts/tests/test_ui_screen_map_contract.py` checks entrypoint coverage. Tags and button behaviors still require regression/UI tests.

**MVP scope:** Android Views remain the UI baseline. Some Activity and runtime orchestration remains; presentation/domain/data extraction covers selected flows only. No redesign or full Compose migration is claimed.
