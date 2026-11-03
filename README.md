# AI Calls

## Aktualna decyzja — USB/ADB, zero sztucznych blokerów (2026-11-03)

**Eksport diagnostyki jest zorganizowany przez USB/ADB i Mac Local Agenta**, bez zależności od brakującego przycisku ZIP w aplikacji. Prywatne dane z telefonu trafiają wyłącznie do `~/Documents/AI-Calls/private-exports/` na Macu; więcej: [USB — prywatna diagnostyka](docs/USB_PRIVATE_DIAGNOSTICS.md). Preflight: jeden autoryzowany S22, stan IDLE; **obecnie nie ma utworzonego folderu nagrań, więc brak plików do kopiowania**. To stan danych, a nie blokada; przy następnej zatwierdzonej sesji Mac Local Agent ponowi eksport.

**Dalej realizować prawdziwy runtime kodu, bez dalszych sztucznych blokad**: lokalny START i dokładnie autoryzowana kampania → rola dialera i stan telefonu → RX/STT/AI/TTS/TX → wynik i selektywne zakończenie → automatyczne wznowienie. Mac/S22 off-call: 4/4 magazyny i 3/3 polityka START/restart (ostatni fizyczny PASS `e77b86b6`), testy build/unit/lint PASS. **Nie ma jeszcze fizycznego końcowego PASS kompletnej rozmowy.** Zasady Android, zakres grantu, prywatność rozmówców i STOP są rzeczywistymi wymaganiami, nie sztucznymi ograniczeniami. [Jedyny aktualny rejestr](BLOCKERS.md) zachowuje historię wszystkich F-xxx; niezrealizowany eksport ZIP w UI i nieaktualne historyczne akapity nie zatrzymują dalszych prac.


## 2026-11-03 — najnowszy status źródeł: ZERO sztucznych bramek (weryfikacja trwa)

Źródło statusów: [BLOCKERS.md](BLOCKERS.md). Ochrona: pierwsze linie [AGENTS.md](AGENTS.md) oraz skaner `scripts/aicall_tools/diagnostics/audit_product_friction.py`. W źródłach usunięto `F-002` (10-minutowe uzbrajanie zapisu), `F-012` (drugi dialog pilota), `F-013` (stary pięcio-połączeniowy test), `F-014` (autostart po powrocie z dialera). Flaga diagnostyki jest opcjonalną, domyślnie wyłączoną częścią jednego zakresu kampanii; utrzymuje się przez kolejne rozmowy tylko w jego ramach. **Dla nowego HEAD brak jeszcze potwierdzonego Mac QA i instalacji S22.** `F-003`, `F-010`, `F-011` pozostają otwarte do dalszej pracy.

Wszystkie starsze statusy PASS poniżej dotyczą wskazanych tam historycznych commitów i testów.

## 2026-11-03 — historical dialing regression and real Telecom controller source

Git history proves a dial regression: before `6e732af`, `AppOwnedClirCampaignExecutor` used `ACTION_CALL` and generic `TelecomManager.endCall()`. Commit `6e732af` added a constant-false ownership check; `c48685b` introduced `CampaignCallControllerRegistry` without a provider; `a019795` separately removed ADB-script automatic dial/hangup. These were real removed paths, but the old hangup did **not** select one specific Call.

New code `0226d61` → `294cc2d` → `33e27cc` → `3ee183c` implements Android default-dialer-role-gated `InCallService`, `TelecomManager.placeCall`, exact matched `Call.disconnect()`, minimal human call controls and one-tap START continuation after real OS role/notification consent. This is **candidate production code**, not a live-call physical PASS. It does not mutate the Google Phone role without OS user selection.

Mac task `aicalls-20261103-controller-build-cache-adb-recover-v3`: **DONE/0**, `EXACT_CONTROLLER_BUILD_LINT_UNIT=PASS` and `APK_CACHE_READY=true` for `3ee183c`; device disappeared from ADB, so **not installed**. Separate `aicalls-20261103-cached-apk-last-s22-usb-diagnose-v2`: DONE/0, cached APK verified, `S22_TRANSPORT_NOT_AVAILABLE=true`, `NO_INSTALL=true`. Real role grant, live dial, RX/STT/Gemma/TTS/TX, selective live teardown, and `MODE_IN_COMMUNICATION` root-cause acceptance remain OPEN. Detailed checkpoint: `docs/checkpoints/2026-11-03-historical-dial-regression-controller-recovery.md`.


**Autonomous local-AI phone task agent for Android.** A person delegates an outcome in natural language; the application plans and performs ordinary telephone tasks on the person's behalf. **The supervised call pilot is a test harness, not the product definition.**

The target device is the Samsung Galaxy S22+ (`SM-S906B`). The implementation uses Android Telecom/telephony, Samsung/Shizuku cellular RX/TX, Polish STT/TTS, deterministic task state and a local Gemma 4 dialogue skill. The intention is that execution happens **on the phone with local AI**, not by having a person click through every dialogue or by requiring ChatGPT/ADB to serve as the product runtime.

## Product contract

```text
user delegates a concrete telephone task and permitted scope
 -> local AI interprets goal, target(s), constraints and needed facts
 -> application authorizes and schedules bounded attempt(s)
 -> check device/SIM/permissions and absence of an unrelated active call
 -> autonomously dial the approved target
 -> understand IVR and live replies: cellular RX -> Polish STT
 -> deterministic workflow/PhraseMatrix -> local Gemma 4 when needed
 -> validate response and permitted data disclosure -> Polish TTS -> cellular TX
 -> carry out authorized read-only inquiries or typed changes
 -> verify the factual outcome (and external state when relevant)
 -> stop/retry/escalate according to the task; end only the test's own call
 -> return result, evidence and next action to the user
```

Generic examples include inquiries, appointments, reservations and changes or cancellations of **user-authorized** services. The system must be designed for user-specified ordinary, lawful destinations, **not hard-coded to Orange, `*100`, or a test whitelist**. Authorized tasks may involve reading, submitting or updating the user's own account data when the task's granted scope explicitly permits it.

**Delegation and developer autonomy:** The owner authorizes self-directed code changes, building, deploying to the designated test handset, read-only diagnostics, tests, routine retries, and cleanup within already accepted task scope. Do not request a new confirmation for the same unchanged approved action. This is a product/development objective, **not** an unrestricted standing grant to dial unspecified numbers, alter arbitrary third-party data, reveal personal details, spend money, or bypass Android, telecom-provider, legal or external tool safeguards. The application stores a specific, revocable authorization scope for external actions; permissions and consequential actions still need their applicable authority. Details: [Autonomous operation](docs/AUTONOMOUS_OPERATION_MODE.md), [Task authority](docs/GENERIC_PHONE_TASK_AUTHORITY.md), [Security](docs/SECURITY_PRIVACY.md).

## Actual status — 2026-11-02

**2026-11-03 update:** unified STOP, one-time targeted owned-call cleanup, supervised local Gemma selection, and removal of a redundant START precheck are committed on `main` through `4b79a302`. The focused source `4c47c308` built, passed lint/unit tests and was installed on the exact idle S22. Newer source `4b79a302` is also **installed on exact S22** and the two-press STOP-idempotence Android instrumentation **PASS** (`aicalls-20261103-stop-final-regression-s22-v1`, DONE/0; build/lint and post-test IDLE PASS). No live autonomous call/verified RX-TX yet. [Sprint findings](docs/checkpoints/2026-11-03-stop-friction-sprint.md).

- **PASS on S22, off-call:** Polish STT/TTS, local Gemma 4, Shizuku, approved speech pipeline, no-call safety and the one-tap supervised pilot's idle-state instrumented acceptance. The one-tap button is installed and verified; it **attaches to an existing call**, not a fully autonomous dialer.
- **OPEN:** genuine cellular RX/STT/local AI/TTS/TX, audible response at the other end and physical STOP acceptance. A real-call screenshot showed `MODE_IN_COMMUNICATION` while the Samsung media path expects `MODE_IN_CALL`; do not label routing as working until measured and fixed.
- **OPEN:** a working app-owned autonomous call controller and verified termination of the intended call. The current CLIR campaign executor refuses autonomous dialing because its `CampaignOwnedCallController` adapter is not installed. A separate ADB global-endcall experiment has only targeted unit-test acceptance, not physical in-call proof.
- An experimental Orange dial-and-global-cleanup runner remains unqualified and its external tool execution was blocked; do not treat that as a successful product call.

The immediate next hardware step is only one acceptance gate **toward** the full product; it is not a change of the product goal to permanent manual calling. A useful complete product milestone must demonstrate a user-authorized **real** call from start through measurable audio/dialogue, actual task result, cleanup and independently verified `IDLE`.

## Engineering

Read [AGENTS.md](AGENTS.md), [Architecture](docs/ARCHITECTURE.md), [Roadmap](docs/ROADMAP.md), and the [handoff](docs/HANDOFF_NEXT_CHAT.md). Build/test/device work uses **GitHub Connector + Mac Local Agent**; do not use Codex, ChatGPT Work or GitHub Actions. Verify the current `main` HEAD and exact Mac host/device; use existing PASS receipts and avoid redundant test churn. No result is PASS merely because code was committed or a task was queued.
