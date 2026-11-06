# AI Calls — bieżący handoff po reaudicie 2026-11-06

## Stan i dowody

Punkt odniesienia audytu: main 5cf3a11c766c4a8e4179179dd6797491f20886f9, czysty i zgodny z origin przed commitem porządkującym dokumentację. Przy wznowieniu pobierz świeży HEAD. Historia wcześniejszych wersji tego handoffu jest w historii Git; starsze fizyczne dowody zachowano w checkpointach. Bieżący reaudyt: [jakość i modularność](checkpoints/2026-11-06-quality-modularity-reaudit.md).

AI Calls jest modułowym laboratorium Android do porównań STT/model/strategia/TTS, z zadaniami wychodzącymi oraz sekretarką INCOMING. Wspólny AndroidConversationRuntime jest aktywny dla obu kierunków; conversation-core i conversation-tools są osobnymi modułami JVM. Orange/CLIR to jeden scenariusz; model nie przejmuje aplikacyjnego grantu, własności Call ani uprawnień do skutków.

- **End_call sekretarki:** dwie rzeczywiste próby A8→S22: REQUESTED/VALIDATED/DISPATCHED/CONFIRMED, END/COMPLETE i zakończenie własnego Call — VERIFIED.
- **30 + 15 sekund:** tylko aktywny nasłuch bez głosu, ostrzeżenie TTS i pożegnanie, a po pełnym TX end_call. Host RED→GREEN/full PASS; S22 native rzeczywisty TTS i end-call PASS 2/2, APK zainstalowany bez utraty danych. Właściciel potwierdził fizyczne samoczynne rozłączenie. Ponowny odczyt: **40 sesji zamiast 39**, telefon IDLE, kampania nieaktywna, brak retry, niezmieniony profil Android STT / Gemma 4 E2B / Android TTS, zero błędów/ostrzeżeń/fatal. **Nie pobrano szczegółowego timeline ostatniego połączenia**; pełna sekwencja warning/grace/TOOL_EVENT/COMPLETE pozostaje QA_PENDING. F-099 jest OPERATOR_ACCEPTED w tym ograniczonym zakresie.
- **Manual/przejęcie/STOP, powrót do AI i odpowiedź po ostrzeżeniu:** osobna kwalifikacja fizyczna QA_PENDING. Nie ponawiać już ukończonych dwóch zwykłych rozmów.

## Reaudyt quality gate i granice

Na 5cf3a11: bash scripts/verify_host.sh PASS, host_quality_gate_green=true; Gradle build/JVM/lint/APK i audyty PASS, 266 testów Python PASS, audit_product_friction PASS, 90 plików dokumentacyjnych i 0 niedziałających lokalnych linków, Git clean. Jest 6 modułów Gradle; 220 produkcyjnych plików Kotlin, 197 w app. Największy AndroidConversationRuntime ma 815 linii, CallRealtimeSessionOrchestrator 758. Są aktywnie używane. Nie usuwać prób, wyników ani zamrożonego transportu tylko dlatego, że nazwy są historyczne.

**Modularizacja etapy 0–1:** bazowe testy i mapa PASS, IncomingIdleTimeout przeniesiony do conversation-core z identycznym algorytmem, app/native compile/full host PASS. [Nowy checkpoint](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md). Telefon S22 nadal z poprzednim działającym APK (brak nowej instalacji).\n\n**Bezpieczna modularizacja:** [plan krokowy z bramkami jakości](SAFE_MODULARIZATION_PLAN.md). Plan nie zmienia produkcyjnego flow i ma pierwszeństwo przy refaktoryzacji struktury, nie przy STOP/bezpieczeństwie.

## Kolejność dalszej pracy

1. Domknąć zachowanie INCOMING manual/TAKE OVER/powrót AI/STOP i brak przypadkowego rozłączenia po odpowiedzi na ostrzeżenie. Szczegółowa weryfikacja ostatniego timeline F-099 tylko przy dostępnym bezpiecznym odczycie.
2. Modularizować małymi krokami czyste polityki i stan rozmowy do istniejących conversation-core/conversation-tools (testy przed/przy migracji), nie kopiować runnera, nie ruszać zamrożonego PCM/PFD.
3. F-080: pełne ciągłe audio/transkrypt i player całej sesji z fragmentami czatu, także w manual. Obecne WAV per tura nie są pełnym nagraniem.
4. F-044/F-045/F-048: ogólny intake zadań, spójna polityka ujawnienia i dozwolonych skutków; osobna weryfikacja powodzenia poza aplikacją. Następnie presety i powtarzalne korpusy testowe.
5. Nie wracać do F-093–F-096 jako błędów otwartych; ich regresje pierwszych buildów pozostają udokumentowane. Aktualne statusy wyłącznie w [BLOCKERS](../BLOCKERS.md).

## Zasady wznowienia

Odczytaj [AGENTS](../AGENTS.md), [ROADMAP](ROADMAP.md), [ARCHITECTURE](ARCHITECTURE.md), [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [BLOCKERS](../BLOCKERS.md), [freeze mediów](PHASE2D_FREEZE_2026-09-18.md) i świeży HEAD. W tym trybie ChatGPT używa GitHub Connector + Mac Local Agent; bez Codex/Work/GitHub Actions, nowych branchy, force-push i operacji na innej instalacji. Sprawdź claims i S22 IDLE przed dotknięciem urządzenia. Nie publikuj nagrań, transkryptów ani numerów w GitHub. Zmiana zachowania wymaga RED→GREEN, host QA, odpowiednich testów S22 i osobnego dowodu skutku.
