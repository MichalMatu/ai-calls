# AI Calls — HANDOFF: Sprint 6 CLOSED, UX-01/02/03 SOURCE_PASS (2026-11-07)

**Status końcowy:** `UX_ITERATION_CLOSED / SOURCE_PASS / S22_PASS / REAL_GEMMA_PASS`. Właściciel uznał UX za wystarczająco dobry i kończy tę iterację. **Nie rozpoczynać kolejnych mikro-poprawek wizualnych ani nie powtarzać zaliczonych testów.**

**Źródło prawdy:** [Finalny checkpoint Sprintu 6](checkpoints/2026-11-07-ux-sprint6-final-handoff.md) · [roadmapa](ROADMAP.md) · [rejestr BLOCKERS](../BLOCKERS.md). Historyczny [checkpoint sprzed odbioru](checkpoints/2026-11-07-ux-sprint6-source-green-s22-adb-pending-doc-freeze.md) zachowany jako dowód przebiegu, nie jako bieżący stan.

## Dokładny stan repo i środowiska

- Repo `MichalMatu/ai-calls`, gałąź `main`, **Sprint 6 funkcjonalny `1b359146d89260e7cffd6d4c851d4c475cee20ac`**, osobny **UX-01 tested HEAD `82d86e3faa256ee5b6d5ec8ab812ea104733bd29`**, **UX-02 tested HEAD `109c446779257fdfb7806716c6f05e97eeff48e2`**, **UX-03 tested HEAD `b53cb820cb4d1f8eb36c38bca67e77cdc33f1e7a`**. Commity dokumentacji mogą podnieść HEAD; zawsze odczytaj aktualny commit i clean worktree. Bez nowych gałęzi, force-push, GitHub Actions, Codex ani Work; commity `[skip ci]`.
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

**Następny osobny zakres:** **UX-04 — modele i ustawienia audio** (jedna granica odczytu/prezentacji, istniejące store/providers/transfer service); później UX-05 F-103. Nie łączyć sprintów ani nie wznawiać kosmetyki Sprintu 6.

**Inne niezależne, nienaruszane sprawy:** F-080 pełny ciągły recorder/player sesji; fizyczne autoryzowane call flows, przejęcie i odstawienie AI, rzetelna ocena semantyki i naturalności Gemmy. `END_CALL 2/2` to wynik decyzji końca, nie merytorycznej jakości.

## Stałe granice bezpieczeństwa

Nie ruszać zamrożonego Samsung/Shizuku/PCM/PFD RX/TX, ownership jedynego Android Call, grant/revoke, STOP, polityk ujawniania faktów i sekwencji `zatwierdzenie → pełny TX → end_call`. Bez realnych połączeń ani ingerencji w prawdziwe archiwa bez wyraźnej autoryzacji. Testować nową odpowiedzialność w izolacji, `scripts/verify_host.sh`, friction audit, AndroidTest kiedy odpowiedni, checkpoint ze źródłowym i fizycznym QA osobno.

**Synchronizacja Mac:** testowany UX-03 `b53cb820` był czysty i zgodny z `origin/main` w `aicalls-ux03-runner-final-green-host-20261107-v2` DONE/0. Dokumentacyjne commity po źródłowym QA wymagają oddzielnego `git fetch --no-tags origin main`, `git merge --ff-only origin/main`, `git status --porcelain`; dokumentacyjne SHA nie są instalacją na S22.
