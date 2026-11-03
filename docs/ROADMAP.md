# AI Calls — current roadmap\n\n**2026-11-03 test receipt:** pierwsze QA nowego F-002 (`556bf67`) FAIL 2/624 z powodu dwóch nieaktualnych testów przywracających oczekiwania F-002/F-009. Testy zaktualizowano (ostatni `2c84513`), weryfikacja nowego HEAD PENDING. Nowy wpis `F-015` w [BLOCKERS.md](../BLOCKERS.md).

## 2026-11-03 — NOWY STAN: druga warstwa ochrony przed blokerami (źródła, QA niepotwierdzone)

Aktualna lista wszystkich naruszeń, statusów i klasyfikacja ważnych limitów: [BLOCKERS.md](../BLOCKERS.md). Nadrzędny zakaz: pierwsze linie [AGENTS.md](../AGENTS.md). `docs/FRICTION_BLOCKER_LEDGER.md` przechowuje **historię**, nie konkurencyjne statusy.

- Audyt Mac `aicalls-20261103-policy-code-deep-audit-v1`: `done/0`, 293 pliki źródłowe/skrypty, 1197 dopasowań kandydujących, nie 1197 potwierdzonych błędów.
- `F-002` poprawiony **w źródłach**: usunięte 10-minutowe uzbrojenie jednej rozmowy. W jednym jawnie zatwierdzanym zakresie kampanii użytkownik może opcjonalnie zaznaczyć lokalny zapis RX/TX/transkryptu (domyślnie OFF), a potem wyłączyć zapis kolejnych rozmów. Rozmówcy i ich prawa nadal wymagają właściwej obsługi.
- `F-012`: usunięty redundantny dialog w pilocie audio. `F-014`: naprawione niezamierzone auto-START przy powrocie z dialera. `F-013`: naprawiony stary test limitu pięciu prób.
- Mechaniczna druga warstwa: `scripts/aicall_tools/diagnostics/audit_product_friction.py`, testy Python i rozszerzone testy Kotlin/Android. **Nie uznawać ich za PASS bez terminalnego wyniku Mac**.
- Pozostałe `OPEN`: `F-003` eksport bez technicznego ręcznego przenoszenia, `F-010` pełne autonomiczne wznowienia, `F-011` realny wpływ limitów modelu/transportu. S22 dla tej nowej wersji nie został potwierdzony.
- Wpisy poniżej opisujące nagrywanie jako nadal wymagające osobnego uzbrojenia są **archiwalnym stanem poprzedniego APK**, nie aktualną regułą ani statusem nowego kodu.

## 2026-11-03 — usuwanie sztucznych limitów: QA PASS na S22, fizyczne długie sesje OPEN

- `F-004`: kampania nie ma arbitralnego stopu po dziesiątej turze; rozłączenie/STOP/autoryzacja i rzeczowe zakończenie pozostają.
- `F-005`: usunięto nieuzasadnione ograniczenia długości nagrania, limit 24 MB, limit trzech plików sesji i cięcie tekstu; odzyskiwanie miejsca wyłącznie pod presją rzeczywistej pojemności Androida.
- `F-008`: podgląd transkryptu pokazuje pełne wypowiedzi z pliku, a nie ostatnie 100 linii/24 tys. znaków.
- `F-002`: zgoda na nagrywanie pozostaje wymogiem obecnego kodu; **nie twierdzić, że została usunięta**. Wymaga zgodnego z prawem rozwiązania, nie obejścia odmowy narzędzia.
- `F-007`, `F-009`, `F-010`: niezamknięte, jawnie sklasyfikowane jako dług.
- Mac `aicalls-20261103-blocker-cleanup-final-qa-s22-v2` DONE/0: compile, unit, lint, S22 IDLE install i instrumentacja PASS. Długie połączenia rzeczywiste: nieprzetestowane.

Źródło prawdy: `BLOCKERS.md` + `docs/FRICTION_BLOCKER_LEDGER.md`. Zobowiązanie normatywne: pierwsze linie `AGENTS.md`.

## 2026-11-03 — zakaz reintrodukcji samowolnych bramek (najnowsze)

Stare instrukcje AGENTS nakazujące osobny 10-minutowy opt-in zapisu były sprzeczne z autonomicznym, bezproblemowym produktem. Zmieniono je na historyczny opis stanu kodu, nie regułę projektu. F-002 pozostaje jawną luką UX; nie maskować statusu. Jedyna norma: **nie tworzyć samowolnych dodatkowych kliknięć, liczników, whitelist ani ręcznego relaya w ramach już zatwierdzonego zadania**. Każdy taki przypadek wpisz natychmiast do `docs/FRICTION_BLOCKER_LEDGER.md` i usuń z testem; nie usuwaj rzeczywistych uprawnień i praw rozmówców.

## 2026-11-03 — five-call attempt ceiling removed

Owner decision: eliminate the arbitrary maximum number of phone calls as a redundant blocker to repeated authorized testing. `maxAttempts` and `ATTEMPT_LIMIT_REACHED` removed from code and UI. Existing scoped grants migrate without renewal; old `max_attempts` preference is ignored, and `attemptsUsed` remains only an internal reservation marker (never a call limit).

Still enforced: exact `*100` / `SET_SERVICE(CLIR=true)` scope, default SIM, disclosure policy, expiry/revocation, Android role, device readiness, one active owned call, targeted STOP and effect commitment checks. One START initiates one call, not an automatic call loop.

Physical evidence: 2026-11-03 BT-off app-owned `*100` call and exact owned hangup PASS; Samsung media runtime blocked because Telecom used VoWiFi (`mUseIWLAN=true`, `MODE_IN_COMMUNICATION`). Owner has now disabled Wi-Fi Calling, but no new cellular-audio PASS has been confirmed yet. Source `fbcb53d` for early VoWiFi refusal built/unit/lint PASS and installed on S22 (Mac task `aicalls-20261103-wificalling-failfast-build-s22-v1`). Check current HEAD, IDLE, role, grant and VoWiFi on the next one-call acceptance.

# AI Calls — current roadmap

## 2026-11-03 — historical dialing regression and real Telecom controller source

Git history proves a dial regression: before `6e732af`, `AppOwnedClirCampaignExecutor` used `ACTION_CALL` and generic `TelecomManager.endCall()`. Commit `6e732af` added a constant-false ownership check; `c48685b` introduced `CampaignCallControllerRegistry` without a provider; `a019795` separately removed ADB-script automatic dial/hangup. These were real removed paths, but the old hangup did **not** select one specific Call.

New code `0226d61` → `294cc2d` → `33e27cc` → `3ee183c` implements Android default-dialer-role-gated `InCallService`, `TelecomManager.placeCall`, exact matched `Call.disconnect()`, minimal human call controls and one-tap START continuation after real OS role/notification consent. This is **candidate production code**, not a live-call physical PASS. It does not mutate the Google Phone role without OS user selection.

Mac task `aicalls-20261103-controller-build-cache-adb-recover-v3`: **DONE/0**, `EXACT_CONTROLLER_BUILD_LINT_UNIT=PASS` and `APK_CACHE_READY=true` for `3ee183c`; device disappeared from ADB, so **not installed**. Separate `aicalls-20261103-cached-apk-last-s22-usb-diagnose-v2`: DONE/0, cached APK verified, `S22_TRANSPORT_NOT_AVAILABLE=true`, `NO_INSTALL=true`. Real role grant, live dial, RX/STT/Gemma/TTS/TX, selective live teardown, and `MODE_IN_COMMUNICATION` root-cause acceptance remain OPEN. Detailed checkpoint: `docs/checkpoints/2026-11-03-historical-dial-regression-controller-recovery.md`.


## 2026-11-03 newest engineering gate

Focused source `4c47c308`: STOP now revokes app audio and targets only an exact owned-call handle; local Gemma pilot enabled; redundant UI controller pre-check removed later in `30d2634`. Source `4c47c308` passed Mac JVM-focused/assemble/lint and exact S22 IDLE installation (`aicalls-20261103-stop-gemma-build-s22-install-v1`: DONE/0). Newer source `4b79a302` carries cancellation-race fixes and Android STOP-idempotence test, now **S22 OFF-CALL PASS** (`aicalls-20261103-stop-final-regression-s22-v1` DONE/0: focused unit/build/lint, both APKs installed, two-press STOP instrument PASS, post-test IDLE PASS). This is not an in-call acoustic STOP or autonomous dial PASS. Platform-exact autonomous call controller and live RX/TX remain OPEN. Historic manual pilot instructions below do not supersede P0. See [2026-11-03 sprint](checkpoints/2026-11-03-stop-friction-sprint.md).

This is the **current** execution plan. The complete 2026-11-01 engineering/audit chronology stays in Git history and the named sprint/checkpoint documents; historical `latest` labels are not current acceptance evidence.

## Product objective and state

**P0:** execute a user-delegated, lawful telephone task with local AI from authorization to completed call: approved destination → dial → IVR/Polish STT → local Gemma/task logic → TTS/phone TX → factual outcome → teardown → result. No permanent manual-dial or manual-hangup requirement should be built into the product design. Support ordinary user-approved numbers and varied task types, not just Orange CLIR.

The owner delegates autonomous engineering, integration, device work and repeat testing within previously approved scope; do not ask for unnecessary confirmations. This authorization does not expand third-party data access, target selection or external account effects beyond a concrete application task/grant.

- **PROVEN_S22 / OFF-CALL:** STT/TTS, Gemma 4, Shizuku and one-button no-call readiness, including actual APK installation and Android instrumentation.
- **HOST_GREEN:** removal of the redundant supervised-audio confirmation dialog (`aicalls-campaign-attach-one-tap-qa-20261102-v1`), build+lint passed; physical installation of that particular change not yet confirmed.
- **INCOMPLETE:** autonomous call control/cleanup, real carrier RX/TX and far-end audibility. A user screenshot observed `MODE_IN_COMMUNICATION`; helper-side audio compatibility has not been established.
- **LAB EXPERIMENT:** the explicit ADB `hangup --global` option passed focused host tests, not a physical in-call check. It is not a product call-identity mechanism.

**Priority:** finish the missing pieces and measure the actual outcome rather than multiplying manual supervised tests. Technical constraints and external platform controls are implementation inputs, not excuses to change the product goal.

## 2026-11-02 — newest no-click S22 diagnostic acceptance

**DONE:** source `8f976a1` approved by new focused tests, Android app build+lint and DUMP-permission no-media/no-dial contract. Mac Local Agent installed the new APK on the exact idle S22 and ran the app-process readiness probe without on-screen interaction: `microphone_permission=true`, `phone_state_permission=true`, `shizuku_ready=true`, `telecom_in_call=false`, `telecom_managed_call=false`, `audio_mode=MODE_NORMAL`, `failure_reason=no_active_cellular_call`, `report_complete=true`. **This is an off-call PASS**, not an in-call routing PASS. The actual screenshot showed `MODE_IN_COMMUNICATION` during a real user call and still needs contemporaneous read-only triage. Fully automated Orange dialing remains **BLOCKED** until the app has verified exact-call identity and selective disconnect; do not substitute global hangup. See [latest S22 no-click checkpoint](checkpoints/2026-11-02-s22-no-touch-call-readiness-installed.md). Do not rerun completed acceptance suites.

## 2026-11-02 S22 reconnected and app deployed (earlier hardware step)

**APP INSTALLED / READINESS PASS:** Mac Local Agent has verified exact S22+ hardware `SM-S906B / RFCT70L7E8J`, idle carrier state, built the audited Android APK and installed it using `adb install -r` successfully. Post-install Shizuku, microphone permission and live-call readiness are true. Both Google speech support packages are not disabled under Android user 0. **Do not repeat completed PASS tests.** Next and only physical gate is one manual human-consented supervised call proving in-call audio mode, RX/STT, reviewed local dialogue, TTS/TX audibility and STOP while retaining Google Phone control. [S22 checkpoint](checkpoints/2026-11-02-s22-reconnected-audited-apk-installed.md).

## 2026-11-02 offline diagnostics gate (newer than the original pre-call quality receipt)

The first on-call attempt reported `AUDIO_ROUTE_NOT_READY`. The current diagnostic module refactor (code `cce1fe99`) removes inconsistent double-sampling, uses synchronized diagnostics and atomic private receipts, and avoids model argument/local error content in evidence. **New diagnostic source HOST_GREEN**: Mac task `aicalls-diagnostics-modular-audit-host-20261102-v2` is terminal **DONE/0** on pinned production code `cce1fe99`; new focused tests, app assemble/lint, static safety and clean-worktree checks all **PASS**. The initial v1 compile failure is retained as a failed attempt, not relabeled. Current phone is disconnected, so **physical deployment, real-call RX/TX and acoustic STOP remain OPEN**. Install only after exact S22 identity + IDLE preflight. Do **not** rerun earlier PASS full suites. See [offline diagnostics audit](checkpoints/2026-11-02-offline-supervised-diagnostics-architecture-audit.md) and [handoff](HANDOFF_NEXT_CHAT.md).

## Immediate engineering sequence — autonomous task execution

1. Check current `main`, the exact Local Agent receipt and S22 identity/IDLE. Reuse all completed hardware and unit tests.
2. Implement Android call-session control for ordinary approved destinations and reliable teardown of the intended session; validate the necessary Telecom capabilities and any system role requirements.
3. Diagnose the real `MODE_IN_COMMUNICATION` vs `MODE_IN_CALL` discrepancy across app and Samsung helper. Do not claim RX/TX readiness from Telecom's call indicator alone.
4. Integrate the existing STT → local task/dialogue engine → authorized data/effects → approved TTS into the in-call session. Keep a visible STOP and record sanitized evidence.
5. Run a concretely authorized real-call acceptance scenario with counterparty awareness, verify the result and `IDLE`. Persist PASS/FAIL; repair the observed defect instead of repeating unrelated suites.
6. Generalize user-approved targets, task grants, retries and resumes. No redundant confirmation when the grant remains valid; scope changes and device permissions still require their appropriate checks.

## Proven, do not repeat

| Boundary | Device evidence |
|---|---|
| Polish Google STT and local TTS PCM | TTS → PCM → exact Polish transcription **PASS** (2026-11-02) |
| Complete-text deterministic backend + speech output | STT → approved text → TTS output **PASS** |
| Real local Gemma 4 skill + exact app-approved speech | Physical Android instrumentation **PASS** |
| Shizuku Binder, microphone permission, model READY | Physical read-only probe **PASS** |
| Off-call frozen cellular media backend | Correct rejection and teardown to `IDLE` **PASS** |
| Pilot UI under `IDLE` | Attach refuses; STOP inactive **PASS** |
| Real user call RX/STT/dialogue/TTS/TX and acoustic STOP | **OPEN** — requires consenting partner |
| Independent exact-call ownership and targeted hangup | **BLOCKED** — separate engineering track |

Sources: [STT recovery](checkpoints/2026-11-02-ai-calls-s22-polish-stt-recovery.md), [no-call pilot](checkpoints/2026-11-02-ai-calls-autonomous-nocall-supervised-acceptance.md).

## Continued generic product expansion

Once the real-call controller and bidirectional audio are verified, complete appointment, inquiry, reservation, account update and carrier service workflows using the same task/grant/effect model. The user may choose an ordinary lawful destination; never reinterpret developer authorization as permission for arbitrary unscoped external changes. Preserve external-success evidence and recovery after interruption.

Relevant enduring contracts: [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md), [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md), [ARCHITECTURE](ARCHITECTURE.md), [CORRECTION_CAMPAIGN](CORRECTION_CAMPAIGN_2026-11-01.md) and [G5_CLIR_ROUTE_DISCOVERY](G5_CLIR_ROUTE_DISCOVERY.md).
