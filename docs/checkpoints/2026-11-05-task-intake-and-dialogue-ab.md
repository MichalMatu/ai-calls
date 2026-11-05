# Przyjmowanie zadania i obiektywne A/B dialogu — 2026-11-05

Wykonanie: lokalny Codex / Git / Gradle / USB, bez Local Agent zadań, Work lub GitHub Actions. Baza `919fd64` (zmiana CI po `1ee21fb`); Samsung/Shizuku i PCM/VAD/quick ACK bez zmian.

## Co było przyczyną dodatkowych zgód

- F-062: osobny przycisk/dialog nadania grantu oraz 24 h TTL blokowały dalsze próby tego samego zadania. Potwierdzony preflight `NO_VALID_EXACT_GRANT`, `grant_expired=true`.
- F-065: start z Codexa wymagał wcześniejszej akceptacji na telefonie. To usunięte z intake i readiness.
- Jawne polecenie właściciela przekazane przez chronioną DUMP ścieżkę i START w UI korzystają z jednego `CampaignAgentControl`. Zapis dokładnego zadania jest tworzony/reużywany w aplikacji bez osobnego potwierdzenia. Podgląd niczego nie przyjmuje. Retry korzysta z istniejącego zakresu i nie odtwarza cofnięcia. F-069: znaczniki czasu pozostają wyłącznie audytem; korekta zegara nie unieważnia zadania i nie blokuje STOP. Usunięto też zbędne parametry czasu z autoryzacji/rezerwacji/in-flight/briefa, bez aliasów starego API. TTL usunięty z modelu, store, factory, UI, diagnostyki i bieżących wywołań.
- Pozostał pojedynczy aplikacyjny zapis zakresu (`CampaignAuthorizationStore`), a nie druga procedura nadawania zgody. Świeży START może przyjąć nowe zadanie po anulowaniu; sam retry tego nie robi. Reuse wymaga dokładnie tego samego celu/akcji/usługi/efektu/SIM/pól.

## Audyt pozostałych warunków

Sprawdzono START UI/DUMP, preflight, kontynuacje, przyjęcie/cofnięcie, decyzje skutków, dialogi UI, limity prób/tur oraz runner. Usunięto manualny grant, jego odnowienie i deadline. Brak globalnego `MAX_CALLS/MAX_ATTEMPTS`; rosnący retry backoff nie jest limitem prób. Pozostawiono prawdziwe uprawnienia Androida/Shizuku, własny Call, STOP, odwołanie, typowane fakty/skutki oraz nowe materialne warunki poza zadaniem. Dialogi edytora instrukcji i podglądu prywatnego transkryptu nie przyznają uprawnień do dzwonienia. Limity buforów chronią zasoby; pełne dowody RX/TX i transkrypt nie są obcinane. Opcja zapisu nie warunkuje wykonania zadania.

## Runner i pomiar

F-066: wcześniej start bez parametru zawsze wybierał SCRIPT_GEMMA. `--dialogue-mode` jest teraz jawnie przekazywany do aplikacji. F-067: konwerter raportu wymagał klasyfikatora JSON i pomijał RAW; teraz zachowuje typowane źródła/czasy Direct i pełną listę EOS→TX. To dowód technicznej ścieżki, nigdy dowód sukcesu CLIR.

## Dotychczasowe rozmowy i ostrożność w przypisaniu przyczyny

| Próba | Kod dialogu | Pełne tury | Wynik |
|---|---|---:|---|
| Script A | `1ee21fb` | 23 | 1 STATE_TASK_SUBJECT + 22 ACKNOWLEDGE_NEUTRAL; EOS→TX pierwszej 6420 ms; wzajemna pętla bez postępu |
| Kandydat F-061 | robocza reguła postępu, wycofana | 15 | ACK 5, następny krok 5, fakt 1, takeover 1, clarify 1, temat 1; cel nadal nieosiągnięty, pierwsze EOS→TX 8744 ms |
| Direct B | ten sam E2B, Android STT/TTS; aplikacyjny intake po usunięciu zgód | 7 | 7 GEMMA_DIRECT_RAW, bez pustych „Rozumiem”; pierwsze EOS→TX 13 702 ms, mediana 5433 ms; brak dowodu CLIR |

W SCRIPT_GEMMA model wybiera akcję, a executor emituje stałą frazę. „Rozumiem” pochodziło z `GateCHybridDialogueBackendFactory.actionExecutor`, nie ze swobodnej generacji Gemmy. Te dane nie dowodzą pogorszenia wag E2B ani wyłącznej winy bota Orange. Druga próba zawierała lokalny recovery/błędy — nie jest pełną poprawą dialogu. Kandydat F-061 zachowany prywatnie i wycofany z bieżącego drzewa przed A/B. Nie zmieniano promptu/runtime dialogu podczas usuwania zgód.

Direct porównano na tym samym E2B, Android STT/TTS i zadaniu; faktyczne źródło GEMMA_DIRECT_RAW=7. Rozmówca wypowiedział 7 różnych tekstów, agent 5; 0 pustych neutralnych ACK. Mediana inferencji modelu 2581 ms. SCRIPT baseline miał medianę EOS→TX 3567 ms, wycofany kandydat 3620 ms. Direct był bardziej zróżnicowany, ale wolniejszy w tej parze pomiarów. Jedna para rozmów z dynamicznym botem Orange wspiera hipotezę problemu sterowania hybrydowego, nie dowodzi wyłącznej winy skryptu ani pogorszenia wag. Direct zakończył się owned_call_ended, potem aplikacyjny STOP anulował retry. Techniczny RX/TX PASS: 1 975 680 / 1 341 328 B; nie jest to niezależny dowód słyszalności. F-048 (brak FactRequest w RAW) i niezależny zewnętrzny dowód CLIR pozostają osobnymi otwartymi wymaganiami. Pełna macierz modeli nie jest jeszcze ukończona i nie ma wiarygodnego zwycięzcy.

## QA

RED→GREEN: kontynuacja po 24 h, readiness bez wcześniejszego mobilnego grantu, liczniki RAW i korekta zegara/STOP. Pełna bramka `bash scripts/verify_host.sh`: 768 JVM (707 app, 51 realtime-client, 10 privileged-helper), 266 Python, lint wszystkich modułów, oba APK oraz audyty PASS. F-063 kontekst/import i F-064 audyt starego UI naprawione; początkowe FAIL zachowane. Native S22 **5/5 PASS**: podgląd bez zapisu/dispatch, jawny START z pustego izolowanego store bez mobilnej akceptacji i z dokładnym trybem, migracja bez expiry + clock correction/STOP, brak resurrect po revoke, kontrakt UI. F-068 fixture rezerwuje próbę przed sprawdzeniem in-flight; rzeczywisty guard nie został osłabiony. Produkcyjne preferences w testach izolowanych bez zmian.

Rzeczywisty START Direct po instalacji: READY → DISPATCHED_TO_INTERNAL_SERVICE → OFFHOOK → 7 pełnych tur → owned_call_ended → IDLE. Nie pytano właściciela o dodatkową zgodę. Końcowy odczyt: 2× IDLE, kampania nieaktywna, pending_retry=false; profil ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL, voice auto. Opcja nagrywania pochodzi z wcześniejszego jawnego wyboru właściciela. Ostatni read-only raport Local Agent idle/null; Codex nie wysłał zadania. F-062–F-069 VERIFIED w opisanym zakresie; F-061 i pozostałe wymagania dialogu/efektu pozostają OPEN.

Prywatne receipts: `~/Documents/AI-Calls/local-codex/2026-11-05-live-matrix/`. Rzeczywiste WAV/transkrypty/identyfikatory pozostają tylko na S22 i Macu. Historyczny odrzucony syntetyczny probe 4-turn nie był uruchamiany ani obchodzony.

## Dokładne receipts końcowe

- Host po ostatnim przeglądzie: `/tmp/ai-calls-permission-cleanup-host-reviewed.log`, `host_quality_gate_green=true`; wcześniejszy pełny GREEN `/tmp/ai-calls-permission-cleanup-host-final.log` zachowany.
- Native po końcowej instalacji: `/tmp/ai-calls-permission-cleanup-native-reviewed.log`, 5/5 PASS, IDLE i produkcyjne preferences unchanged. `task-intake-native-summary.json` w prywatnym katalogu dowodów.
- Końcowy zainstalowany app APK SHA-256: `53df52b6d752fc4da1d3e6cc95aeebe9f0984b452ff64cff9269dabd6e2a1fe1`; AndroidTest APK: `02be7730c772014754add6b9f161a6a9229f6d38c66cd979899c5419d7f7e3d0`. Podpis zgodny z istniejącą aplikacją, bez czyszczenia danych.
- Rozmowa Direct B używała app APK `788aea3771b9377a962e875f8148e75fc0cf38cace6d98bf1c5b5f58090f0a69` z tej samej logiki intake. Późniejszy przegląd usunął nieaktualne komentarze o expiry i poprawił tekst komunikatu odmowy przed dial; bez zmiany logiki dialogu lub mediów. Po tej zmianie ponowiono pełny host i 5 testów native, bez zbędnego ponawiania rozmowy.
- `ab-sanitized-comparison.json`, `03-direct-sanitized-report.json`, `03-direct-resource-samples.json`, `03-direct-private-timeline.jsonl` oraz oryginalne pipeline/timeline Script A i kandydata pozostają prywatnie. Patche odrzuconego F-061 i wcześniejsze błędy/testy zachowane.
