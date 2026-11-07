**Ważne: `d38408ba` naprawia mylące hasło START z Home:** przycisk nadal realnie uruchamia tylko autoryzowane CLIR w strategii skryptowej; zwykłe scenariusze to wyłącznie formularze. F-127 FIXED_IN_SOURCE / QA_PENDING.

# UX-06 aktualizacja — osobiste scenariusze, historyczne statusy (2026-11-07)

Bieżący etap wdrażania: [nowy checkpoint](checkpoints/2026-11-07-ux06-personal-scenarios-and-history-receipt.md). `main@3c7a8b5` kod: 10 factory presetów, szyfrowane szkice, kopie osobiste i rewizje, UI PL/EN, rzeczywista data historii kampanii. **Host v4 na b2a2fc5 DONE/0**: pełny build + 338 Python, JVM, AndroidTest compile, friction PASS. **Host v5 do 3c7a8b5 QUEUED, wynik QA_PENDING.** S22 w tym etapie nie instalowano ani nie testowano. Następny kierunek: typowany live-state i bezpieczna próba Android UI bez faktycznego CALL. Niewdrożone presety pozostają draft-only; nie ogłaszać gotowych rezerwacji ani sukcesu sieciowego.

---

# NOWY zakres UX-06 w toku — 2026-11-07

Po wyraźnym poleceniu właściciela „rozpocznij wdrażanie poprawek” rozpoczęto source-only UX-06 na aktualnym `main`. **Stan:** `SOURCE_PARTIAL / HOST_PASS_THROUGH_ec3a280 / SCENARIO_QA_PENDING / S22_NOT_RUN`. Wdrożono Start/Kampanie stany, widoczny dialer, uproszczenie accordion, odrębne anulowanie zadania OUTGOING oraz pierwszy katalog 10 scenariuszy z szyfrowanym formularzem. **Nie pomyl szkiców scenariuszy z gotowymi połączeniami i nie uruchamiaj nieautoryzowanego call**. Następne kroki i dokładne receipts: [UX-06 checkpoint](checkpoints/2026-11-07-ux06-campaign-and-scenario-source-checkpoint.md), [plan](UX06_USABILITY_AND_SCENARIO_PLAN.md), [roadmap](ROADMAP.md), [BLOCKERS](../BLOCKERS.md). Test `aicalls-ux06-host-regression-fix-20261107-v2` DONE/0 na `ec3a280`, v3 nowego kodu scenariuszy QA_PENDING, bez S22. Nie uruchamiać równoległych host tasków dla tego checkout podczas v3.

---

# AI Calls — HANDOFF (2026-11-07): Sprint 6 CLOSED, UX-01–04 SOURCE_PASS, UX-05 VERIFIED

**Bieżący stan:** `UX_ITERATION_CLOSED / UX05_SOURCE_HOST_PASS / UX05_S22_AUTOMATED_PASS / UX05_PL_EN_SCREENSHOT_PASS`. Poprzednie P0/UX-01–04 nie zostały ponownie otwarte. [Dokładny checkpoint UX-05](checkpoints/2026-11-07-ux05-pl-en-accessibility-closeout.md).

**Aktualny zakres ukończony:** 553 spójne zasoby PL/EN, tłumaczenia Home/Models/Engine/Skills/Campaign/Pilot/dialer/live-chat, nagłówki i statusy czytnika. `main@9da1468b4aa4336822a5ffd6390817848830a9f8` to ostatni testowany SHA przed dokumentacją; produkcyjny kod UX-05 skończył się na `1464c795`, ostatni full host source gate `c9d48b0`, późniejsze testy tylko w test files. `aicalls-ux05-final-host-gate-20261107-v1` DONE/0: **337 Python + 10 PL/EN + friction/JVM/AndroidTest/APK PASS**; corrected parity `aicalls-ux05-corrected-parity-gate-20261107-v1` DONE/0. S22 `aicalls-ux05-s22-targeted-ui-acceptance-20261107-v1` DONE/0: **2/2 native**, signer zgodny i dane zachowane; `aicalls-ux05-s22-private-screenshot-instrumentation-20261107-v2` DONE/0: **4/4 PNG PL/EN**, `pl` przywrócony, bez rozmów/archiwów. Prywatny ZIP wyłącznie Mac: `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-ux05/AI-Calls-UX05-S22-PL-EN-private.zip`. Manualnego TalkBack nie zaliczono/nie wykonywano.

**Kolejny niezależny krok:** F-080 — ciągłe nagranie całej sesji i odtwarzacz osi czasu. Autoryzowane call flows, przejęcie/oddanie AI oraz semantyka Gemmy to osobny zakres. Żadnego ponownego UX polishing, żadnych ponownych zamkniętych testów. Działać wyłącznie GitHub Connector + exact Mac Local Agent, `main`, bez Codex/Work/GitHub Actions/gałęzi/force push. Commity `[skip ci]`; weryfikować bieżący HEAD i clean Mac przed zmianą. Nie ruszać zamrożonego Samsung/Shizuku/PCM/PFD ani Call ownership/grants/STOP/full TX/archiwów bez odrębnego uzasadnienia i zgody na skutki.

---

## Historyczny handoff Sprint 6 / UX-01–04 (zachowany)


**Status końcowy:** `UX_ITERATION_CLOSED / SOURCE_PASS / S22_PASS / REAL_GEMMA_PASS`. Właściciel uznał UX za wystarczająco dobry i kończy tę iterację. **Nie rozpoczynać kolejnych mikro-poprawek wizualnych ani nie powtarzać zaliczonych testów.**

**Źródło prawdy:** [Finalny checkpoint Sprintu 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md) · [roadmapa](ROADMAP.md) · [rejestr BLOCKERS](../BLOCKERS.md). Historyczny [checkpoint sprzed odbioru](checkpoints/2026-11-07-ux-sprint6-source-green-s22-adb-pending-doc-freeze.md) zachowany jako dowód przebiegu, nie jako bieżący stan.

## Dokładny stan repo i środowiska

- Repo `MichalMatu/ai-calls`, gałąź `main`, **Sprint 6 funkcjonalny `1b359146d89260e7cffd6d4c851d4c475cee20ac`**, osobny **UX-01 tested HEAD `82d86e3faa256ee5b6d5ec8ab812ea104733bd29`**, **UX-02 tested HEAD `109c446779257fdfb7806716c6f05e97eeff48e2`**, **UX-03 tested HEAD `b53cb820cb4d1f8eb36c38bca67e77cdc33f1e7a`**, **UX-04 tested HEAD `7df2c33b282da868d06d5308c9e57d1dd0317435`**. Commity dokumentacji mogą podnieść HEAD; zawsze odczytaj aktualny commit i clean worktree. Bez nowych gałęzi, force-push, GitHub Actions, Codex ani Work; commity `[skip ci]`.
- GitHub Connector + **Mac Local Agent**: `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, repo na Macu `/Users/michal/agent-workspace/repos/ai-calls/work`. Testy hosta wykonywać tam, nie zgadywać stanów sprzętu.
- Fizyczny S22 `SM-S906B` / serial `RFCT70L7E8J`; instalacja `app-debug.apk` i AndroidTest APK przez `adb install -r -t`, signer zgodny, **dane zachowane**. `IDLE`, brak aktywnej kampanii i faktycznych połączeń podczas testów.

## Zakończone PASS — nie otwierać ponownie

| Obszar | Receipt | Wynik |
|---|---|---|
| Kod finalny, lokalne testy, APK i friction | `aicalls-20261107-ux-sprint6-visual-review-hotfix-source-qa-v1` | **DONE/0**, 325 host tests PASS, 33 celowane Python, AndroidTest/JVM/APK PASS; HEAD `1b359146` |
| Pełna instalacja Sprint 6, UI i screenshots | `aicalls-20261107-ux-sprint6-s22-install-28ui-24visual-42screens-v3` | **DONE/0**: **28/28 native UX, 24/24 visual**, 42 prywatne JPG |
| Hotfix heurystyki Android, auto-follow i screenshot dialogu | `aicalls-20261107-ux-sprint6-visual-review-hotfix-s22-targeted-v2` | **DONE/0**: 1/1 bezpieczny dialog, 3/3 syntetyczne stany Lab; 44 JPG; finalny hotfix APK wgrany |
| Realna Gemma po hotfixie | `aicalls-20261107-ux-sprint6-hotfix-real-gemma-final-v1` | **DONE/0**: **2 realne tury LiteRT + STOP**, stan IDLE po teście, **46 prywatnych JPEG** |
| Android regex bug F-118 | [BLOCKERS](../BLOCKERS.md) | **VERIFIED**, nie przywracać `(?iuU)` w Android Regex |

- **Funkcje UX:** bulk-delete tylko zakończonych rozmów z potwierdzeniem, dymek WAV tap→play/tap→stop, czytelny STOP i auto-follow w Lab, Skills/Engine/Campaign hierarchia, status wykonania historyczny a nie live.
- **Prywatny finalny ZIP:** `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-ux-sprint6/AI-Calls-S22-UX-sprint6-private.zip` (Mac). Nie commitować JPEG/WAV/transkryptów/numerów.
- Wcześniejsze błędy ADB oraz FAIL z `PatternSyntaxException` są **historycznymi RED z późniejszym GREEN** — nie usuwać ich dowodów, ale nie klasyfikować jako dzisiejsze blockers.

## Nowy zakres UX-01 — zamknięty źródłowo, niezależny od Sprintu 6

**UX-01 SOURCE_PASS**: read-only `CampaignExecutionStatusPresenter` z typowanym znaczeniem/tonem i PL/EN; jedynie render historycznego stanu w `CampaignToolActivity`, surowa diagnostyka bez zmian. [Checkpoint i dokładne receipts](checkpoints/2026-11-07-ux01-campaign-status-presenter-closeout.md): RED rzeczywiste przy skonfigurowanym SDK, następnie JVM 6/6, 325 host Python, AndroidTest compile, APK i friction PASS. **S22 UX-01 nie instalowano ani nie testowano**, brak realnego Call. Nie mylić SOURCE_PASS z fizycznym odbiorem nowego APK.

**UX-02 SOURCE_PASS (zamknięty źródłowo):** [checkpoint](checkpoints/2026-11-07-ux02-conversation-history-controller-closeout.md). Kontroler z immutable stanem historii, serializowanym IO, ochroną stale callbacks i wyłącznym kasowaniem przez istniejący `RecordedConversationHistory`. Kod przetestowany na `main@109c4467`: RED → 7 JVM GREEN, 325 Python, friction, APK i AndroidTest compile PASS, clean Mac. **S22 UX-02 NOT_RUN**, żadne prywatne archiwa nie były dotykane. Nie otwierać ponownie zaliczonego zakresu.

**UX-03 SOURCE_PASS / HOST_PASS (zamknięty źródłowo):** [checkpoint i receipts](checkpoints/2026-11-07-ux03-skill-lab-runner-closeout.md). `SkillLabRunner` przejął model, worker, STOP, 120 s i callbacki; immutable `SkillLabUiState`; Activity renderuje tylko stan. RED→GREEN 9 JVM, 328 Python, friction, `scripts/verify_host.sh`, AndroidTest Kotlin i dwa APK PASS, clean Mac na `b53cb820`. **S22 nowego UX-03 NOT_RUN**; bez rzeczywistych połączeń i ingerencji w prywatne dane. Historyczne RED i 4 nieaktualne źródłowe asercje testów poprawione, dowody zachowane.

**UX-04 SOURCE_PASS / HOST_PASS (zamknięty źródłowo):** [checkpoint i receipts](checkpoints/2026-11-07-ux04-model-and-audio-presentation-closeout.md). Dwa osobne read-only presentery: `LanguageModelStatusPresenter` (installed/ready/active, busy, postęp, feedback) i `AudioEngineActiveProfilePresenter` (prawdziwy zapisany profil → podsumowanie). RED→GREEN **13 JVM**, **332 Python**, friction, `verify_host.sh`, AndroidTest Kotlin i oba APK PASS, Mac clean na `7df2c33`. **S22 nowego UX-04 NOT_RUN**. Transfer, wybór modelu, zapis konfiguracji, readiness i Android TTS zachowały dotychczasowych właścicieli.

**Następny osobny zakres:** **UX-05 / F-103 — pełne PL/EN i dostępność UI**, nie refaktor serwisów modeli. Nie łączyć sprintów ani nie wznawiać kosmetyki Sprintu 6.

**Inne niezależne, nienaruszane sprawy:** F-080 pełny ciągły recorder/player sesji; fizyczne autoryzowane call flows, przejęcie i odstawienie AI, rzetelna ocena semantyki i naturalności Gemmy. `END_CALL 2/2` to wynik decyzji końca, nie merytorycznej jakości.

## Stałe granice bezpieczeństwa

Nie ruszać zamrożonego Samsung/Shizuku/PCM/PFD RX/TX, ownership jedynego Android Call, grant/revoke, STOP, polityk ujawniania faktów i sekwencji `zatwierdzenie → pełny TX → end_call`. Bez realnych połączeń ani ingerencji w prawdziwe archiwa bez wyraźnej autoryzacji. Testować nową odpowiedzialność w izolacji, `scripts/verify_host.sh`, friction audit, AndroidTest kiedy odpowiedni, checkpoint ze źródłowym i fizycznym QA osobno.

**Synchronizacja Mac:** źródłowy UX-04 `7df2c33` był czysty i zgodny z `origin/main` w `aicalls-ux04-final-source-green-host-20261107-v1` DONE/0. Po commitach wyłącznie dokumentacyjnych zrób `git fetch --no-tags origin main`, `git merge --ff-only origin/main` i `git status --porcelain`; dokumentacyjne SHA nie są instalacją na S22.
