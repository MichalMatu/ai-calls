# AI Calls — SINGLE_TRACK_OWNER: jeden tor rozwoju, handoff do nowego czatu (2026-11-09)

## G6 FAST FLOW — źródło i host QA po dużym sprincie (2026-11-09)

**Pre-sprint main:** `851cbe01b23f7f312f19fe0646823b49bc6df975`. **Code freeze:** `2bd841130ec2ca7b48172b5e676d0e900ee943fc`. Wspólny INCOMING/OUTGOING nie został przepisany: naprawiono reentrantne takeOver/STOP/exclusive w koordynatorze, wyścig Realtime barge-in/final-TX, sekwencyjne odtwarzanie dostępnych prywatnych WAV RX/TX w porządku czasu oraz odczyt wyniku zakończenia i przerwanych RX. Dodano odseparowany, testowany kontrakt IVR/DTMF dla przyszłego **dokładnie autoryzowanego** OUTGOING; jeszcze nie podłączony do Telecom. Samsung/Shizuku, Gemma E2B, Skills, STT/TTS, grant, recorder i CallCommitmentGate bez zmian.

**Mac DONE/0:** `aicalls-g6-sprint2-fast-auto-mac-20261109-v1` = `auto 851cbe0` PASS; osobne focused testy barge-in, IVR (11 testów), archive outcome PASS. **Jeden finalny full host QA** `aicalls-g6-sprint6-once-final-host-qa-20261109-v1` DONE/0: unit/lint, 398 Python, audio/security/preflight, Gradle 147 actions = 11 wykonanych + 136 UP-TO-DATE, configuration cache reused, działający daemon, Git clean. Bez `device-build`, APK, ADB i fizycznych połączeń. Nie powtarzano G3/G5.3/hardware STOP. F-171–176 w `BLOCKERS.md`.

**Fizyczne G6 nadal OPEN:** lokalna E2B true barge-in przy TX, INCOMING manual takeover/resume i continuous audio (F-080), realny OUTGOING/IVR DTMF adapter i typowany wiarygodny outcome, niezależnie udowodnione rezerwacje. Wymaga oddzielnego autoryzowanego zadania i IDLE S22; nie instalowano APK. 0/6 indywidualnych human review nadal UNASSESSED. Źródła zewnętrzne wykorzystano jako wzorce, **bez kopiowania kodu**. [Checkpoint](checkpoints/2026-11-09-g6-fast-sprint-code-and-host-qa.md).



## Kontrola zamknięcia dwóch czatów i start jednego (2026-11-09)

Handoff na podstawie bieżącego drzewa i zakończonego doc-only Mac v3 DONE/0: [checkpoint](checkpoints/2026-11-09-ai-calls-single-track-final.md). To przekazanie **nie** odłącza automatycznie ani nie archiwizuje okien rozmów w interfejsie. Od następnego zadania tylko jeden nowy czat; poprzednie chaty nie mają rozpoczynać nowych prac. Nowy chat odczytuje aktualny HEAD i nierozliczone zadania przed dotknięciem kodu.

## Decyzja właściciela

Oba dotychczasowe czaty AI Calls zostają zamknięte organizacyjnie. **Tylko jeden NOWY czat jest właścicielem dalszego wykonania**: GitHub Connector + wskazany Mac Local Agent, main bez nowych gałęzi, bez force-push, bez Codex/Work/GitHub Actions. Ten zapis NIE zamyka okien rozmów automatycznie, ale przenosi cały stan do repozytorium. Nie przyjmować ani nie inicjować dwóch równoległych sprintów na tym samym kodzie.

**Baza w chwili spisania: main@31f016d5468cccdf4418ea306c3584d0805aa178.** Przy starcie nowego czatu ZAWSZE pobrać ponownie aktualny main HEAD oraz live Mac task-results, bo starszy czat mógł jeszcze wykonać czynność. Jeśli pojawił się nowszy commit lub aktywne zadanie, najpierw odczytać jego diff, receipt i status. Nie cofać go do bazy tego handoffu, nie nadpisywać niczego bez zbadania.

**Jeden główny plan:** docs/AI_CALLS_MASTER_EXECUTION_PLAN.md. Ten handoff jest instrukcją startową, docs/EXTERNAL_REUSE_AUDIT_2026-11-09.md to referencyjna mapa źródeł, checkpoints/BLOCKERS zawierają historię wyników — nie są osobnymi planami.

## Szybkie testy i Gradle cache — F-170 VERIFIED_SCOPED

Pierwsze zadanie nowego czatu: używaj `test-profile.sh auto <pre-sprint-SHA>` lub `focus` do sprawdzenia wyłącznie dotkniętej logiki. Tryb `full` tylko przy integracji (bez APK), `device-build` jedynie tuż przed S22 install. Cache Gradle działa: celowany `conversation-core:test` 9,73 s → 0,87 s z reused configuration cache; bez pakowania. Pre-commit na Macu aktywny, statyczny, GitHub Connector pomija local hooks. [Evidence](checkpoints/2026-11-09-f170-fast-dev-gradle-cache-mac.md).

## Co bezwzględnie zachować

- Działająca droga Android SIM/Telecom + Samsung/Shizuku, tylko własny Call, niezmieniona ścieżka RX/TX/PCM, STOP, przejęcie oraz bezpieczna polityka jednego aktywnego połączenia. Zero zmian w granicach uprawnień bez uzasadnionego regresyjnego dowodu.
- **Gemma 4 E2B/GPU** jako obecny wybór, obecne STT/TTS, dynamiczne Skills i tryb CONTEXTUAL jako domyślny. Tryb ALL_ACTIVE wyłącznie w Lab (kontrolowany A/B).
- Jeden AndroidConversationRuntime i jeden port scenariuszy INCOMING/OUTGOING; istniejące FactDisclosurePolicy, IdentityVault, CallCommitmentGate, BookAppointmentOutcomeMatcher, CallSessionEvidenceRecorder, RecordedConversationHistory. Nie tworzyć bliźniaczego runnera ani drugiego rejestru uprawnień.
- Repozytorium, wdrożone ustawienia S22, prywatne 36 WAV, formularze odsłuchu, historyczne checkpoints, BLOCKERS i wszystkie dokładne receipts. Prywatnych nagrań, numerów i transkryptów nie wysyłać do GitHub/tasków.
- Wymóg docelowy: jeden instalowalny APK, manual/AI i przejęcie, pełne audio i transkrypt w historii, opcjonalne transporty w osobnych adapterach; obecny produkt nadal jest laboratorium.

## Baseline zaakceptowane — nie testować drugi raz bez zmiany kontraktu

| Zakres | Potwierdzony wynik | Nie wyciągać pochopnych wniosków |
| --- | --- | --- |
| G3 modele | 96/96 natywnych tur E2B/Qwen, KEEP_E2B | Nie wracać do pełnego A/B; inne modele Lab only |
| G4 Skills | routing, metadata i native E2B/S22 PASS | Nie przebudowywać routera na inny framework |
| G5.1/G5.2/G5.3 | 18/18 trzech tur w 6 sesjach, 36 WAV, WER syntetyczne 0/150, native STOP STT/model/TTS PASS | Jest to OFF-CALL, nie G6 live |
| Host QA | ostatnie 386 Python PASS + build/audio/anti-friction; Git clean w dokładnych task receipts | Nowy kod wymaga celowanego QA, nie ponowienia niezmienionego G3/G5 na S22 |
| Human quality | właściciel napisał, że pozytywnie ocenia rozmowę | 0/6 indywidualnych JSON: formalne 48 wymiarów UNASSESSED; nie fabrykować osiem wymiarów/sesję |
| Historyczne INCOMING | wcześniejsze rzeczywiste A8→S22, słyszalność, end_call i częściowy prywatny recorder | G6 jako całość nadal NOT_RUN; manual takeover/resume i komplet archiwum nadal wymagają dowodu |
| F-166–168 | źródło i 6/6 prywatnych stron naprawione, Mac QA DONE/0 | Nie tworzyć kolejnego narzędzia recenzji jako zamiennika odsłuchu |

## Wybór najlepszych fragmentów z innych projektów

**Zasada:** skopiować/adaptować sprawdzone **konkretne moduły i testy** wyłącznie, gdy licencja na to pozwala, ich Kotlin/Android kontrakt jest zgodny i nie istnieje odpowiednik w AI Calls. Zamiast automatycznie łączyć całe Python/Go/TS frameworks, przenieść potrzebny algorytm/test do istniejącego modułu AI Calls, zaznaczając provenance, licencję i granice regresji.

1. **Pipecat — P1 po read-only porównaniu.** Kod: src/pipecat/turns/speculation_gate.py i user_turn_controller.py, user_idle_controller.py; szczególnie kolejka HOLD/RELEASE/DISCARD oraz anulowanie generacji przy zmianie stanu mowy. AI Calls już ma CallRealtimeAudioPump.onRemoteSpeechStarted; porównać przede wszystkim lokalny Gemma/STT/TTS. BSD-2-Clause; zachować wymagane notices.
2. **call-use — P1/P2.** Kod: call_use/models.py, evidence.py oraz tests/test_bdd_call_lifecycle.py, test_evidence.py. Przejąć dokładne luki w IVR/DTMF/hold/disposition, idempotentny cancel/takeover/resume, testy Given/When/Then; zmapować na istniejący CallTask/CallOutcome/grant. MIT; bez nowego Python workera.
3. **Clinic Voice Agent — P2.** Źródła do studiowania: scheduling_api/app/main.py i eval/test_cases.py (availability → hold → confirm, idempotency-key, structural tool-trace eval). **Brak licencji**: nie kopiować kodu, przenieść tylko ogólny przepływ i samodzielnie napisane testy/adapter zgodne z istniejącym BookAppointment i CallCommitmentGate.
4. **DeskRoute — G7.** ARCHITECTURE.md: rozdział danych per agentId, escalations/knowledge, historia/transkrypt, panel klienta. **AGPL-3.0**: tylko analiza wzorców i własna implementacja, nie kopiować kodu/panelu do zamkniętego APK bez osobnej decyzji licencyjnej.
5. **LiveKit — po G6 SIM, opcjonalne.** livekit/agents oraz livekit/sip: SIP/WebRTC, lifecycle jobs/rooms/dispatch. Apache-2.0; dodać jako adapter transportu z osobnym acceptance, NIE zastępować Android Telecom i NIE wprowadzać hostowania/chmury do podstawowej ścieżki.

## Jedna sekwencja i ownership

**Sprint A — G6.1 INCOMING.** Read-only porównanie obecnego AndroidIncomingAssistant, AndroidConversationRuntime, LocalSpeechGenerationGate, CallRealtimeAudioPump i prywatnego archiwum z wzorcami Pipecat + call-use. Wskazać *jeden* brak funkcjonalny, wdrożyć minimalną poprawkę i test, dopiero potem zamrozić kod i uruchomić host QA jeden raz. Na fizycznym S22 powtórzyć tylko **nowy/zmieniony** kontrakt: przejęcie/resume, przerwanie, albo pełne archiwum; wcześniejsze testy nie są resetowane. Sam realny Call wymaga dokładnie autoryzowanego zadania i kontrolowanego rozmówcy.

**Sprint B — G6.2 OUTGOING.** Przejąć z call-use tylko brakującą obsługę IVR/DTMF/hold i strukturalny outcome/evidence. Istniejąca własność Android-owned Call, polityki grantu, STOP i jeden CallCommitmentGate pozostają. Testy syntetyczne/host → wąska rzeczywista autoryzowana rozmowa → niezależny dowód wyniku.

**Sprint C — rezerwacje.** W istniejących GateD/BookAppointment dodać wyłącznie brakujący adapter terminów, bez dublowania commit/consumption policy: availability → hold → confirm, replay/idempotency, stale slot, double commit. Testy i dowód zewnętrzny osobno.

**Sprint D — archiwum/UX i dodatkowe kanały.** Dopiąć pełną oś audio/transkryptu i replay (jeśli nie skończono w A), potem proste widoki historii/eskalacji, dopiero opcjonalne LiveKit/SIP i WhatsApp Business jako odrębne adaptery. Multi-tenant po decyzji o komercjalizacji, izolacji i licencji.

## Antyduplikacyjny gate testów

Przed każdą zmianą czytać **aktualny HEAD + diff od ostatniego checkpointu + aktywne task-results Mac**, nie wierzyć domniemanemu stanowi innego czatu. Dla każdego PR-free commita na main: nazwać zmianę kontraktu i stare receipt, wskazać testy dotknięte; wykonać tylko nowy targeted RED→GREEN; właściwy pełny host gate raz po zamrożeniu zakresu; na S22 wyłącznie nową funkcję lub konieczną regresję w dotkniętej warstwie. Bez resetu 96/96 G3, 18/18 G5.3, natywnego STOP, 6/6 HTML ani historycznych A8→S22; bez podwójnego QA w dwóch czatach. Wszelkie nowe rzeczywiste połączenia według lokalnej autoryzacji dokładnego numeru, zakresu i SIM.

## Pierwsze zadanie nowego czatu

1. Odczytać świeży main i obowiązkowe źródła: AGENTS.md, docs/NEXT_CHAT_PROMPT.md, docs/AI_CALLS_MASTER_EXECUTION_PLAN.md, docs/EXTERNAL_REUSE_AUDIT_2026-11-09.md, docs/INCOMING_ASSISTANT.md, docs/CONVERSATION_RUNTIME.md, docs/PRODUCT_ASSEMBLY_PLAN.md, BLOCKERS.md i dokładny najnowszy Mac task receipt.
2. Ustalić, czy poprzedni czat pozostawił ostatni nierozliczony commit/zadanie. Jeśli tak, włączyć jego wynik bez ponawiania i nie nadpisywać. Przy obecnym main źródłowe G6 i Pipecat/call-use na razie tylko preflight/gap assessment.
3. Wziąć **pierwszą faktyczną lukę G6.1**, nie przepisywać zewnętrznych frameworków w całości. Zapisać scoped plan źródło→funkcja→Kotlin port→RED→GREEN→host QA→S22 proof. Następnie autonomicznie wdrażać bez tworzenia kolejnych planów.
4. Nie zaczynać G7 ani SIP/WhatsApp zanim działający G6 SIM nie ma wiarygodnego odbioru. Nie wyłączać działających komponentów na rzecz atrakcyjniejszego frameworku.

## Ścieżki i zasady narzędziowe

AI Calls: MichalMatu/ai-calls main. Mac: macbook-michal / cfea4cc3-b54c-4b6d-b9a6-758d12733e98, checkout /Users/michal/agent-workspace/repos/ai-calls/work. Mac Host Ops tylko przez MichalMatu/local-agent:agent-control i task-results. Bez Codex/Work/GitHub Actions, także ich statusu; bez nowych gałęzi, force-push, direct active Call bez poprawnego grantu, wrzucania prywatnych danych do Git lub ślepego instalowania na S22. Komunikaty [skip ci]. Przy konflikcie HEAD zatrzymać mutację, odczytać aktualny diff i dostosować zakres, nigdy nie cofać чужих commitów.
