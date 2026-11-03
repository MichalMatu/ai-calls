# AI Calls — F-033/F-034: po przerwaniu, handoff i wyniki (2026-11-03)

## Aktualizacja po wznowieniu (najbardziej aktualna sekcja)

1. Fizyczny retest `aicalls-20261103-f033-f034-fast-guard-physical-call-retest-v2` — **DONE/0** na kodzie `eb2f505831ddca17a395f666c4e7f08529306c55`. Dokładny S22 z ważnym istniejącym grantem/dialerem; `OFFHOOK`, 5 tur, RX **2 161 920 B**, TX **642 090 B**; pierwsze `EOS→TX=10 567 ms`, max **12 175 ms**, maksymalny `STATE_TASK_SUBJECT` streak **1**. Prywatny raport końcowy `CURRENT_SESSION`; `CLEANUP_APP_OWNED_STOP_ACK=true`, `IDLE`; brak global hangup i prywatnych danych w task-results. Tym samym źródłowa poprawka F-033 jest fizycznie zweryfikowana w zakresie **redukcji zapętlania**, F-034 w zakresie **skrócenia czasu pierwszego TX z 47 567 ms**. Wynik usługi CLIR i niezależna słyszalność nadal **OPEN**.
2. Nowy fizyczny defekt **F-035** (jedno `SpeechRecognizer.ERROR_NO_MATCH=7` zrywało całą lokalną sesję, zamiast kontynuować słuchanie tej samej rozmowy). Wprowadzono `LocalSpeechRecoverableFailure.kt`, zmieniono `ChatRelayLiveCallProbe.Run`: STT błędy 6/7 ponawiają *ten sam aktywny turn* po 750 ms tylko przy aktualnym `Call`/grant; inne błędy nadal są zgłaszane; STOP nie jest ignorowany; brak arbitralnego limitu powtórzeń. Regresja `LocalSpeechRecoverableFailureTest.kt`.
3. Nowe źródło po tej naprawie: `49d50fbfb22ab0434254386adea0f2df7e84d527` (nie zakładaj, że to obecny HEAD; odczytaj go). Mac task `aicalls-20261103-f035-stt-nomatch-retry-build-install-v1` w `agent-control` otrzymał build/lint/unit/anti-friction oraz bezpieczną instalację tylko przy `IDLE`, zachowanej roli i braku aktywnej kampanii; **sprawdź jego wynik jako pierwszy przed kolejną próbą**.
4. `BLOCKERS.md` jest jedynym rejestrem: F-033/F-034 mają status `VERIFIED` w opisanym zakresie, F-035 `OPEN` do uzyskania wyników QA/akceptacji. Po każdej zmianie uruchom audyt anti-friction i testy. Żadnych Codex/Work/GitHub Actions, numerów, nagrań ani surowych transkryptów w Git.


## Najważniejsze
- Repo: `MichalMatu/ai-calls`; startuj od **aktualnego** HEAD `main`, nie od zapamiętanego SHA. HEAD przed handoffem: `eb2f505831ddca17a395f666c4e7f08529306c55`.
- Tryb: GitHub Connector + `MichalMatu/local-agent` branch `agent-control` + Mac Local Agent (`macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`) + ADB/USB Samsung S22 `RFCT70L7E8J` / `SM-S906B`. **Nie** Codex, Work, GitHub Actions (nawet status). Nie wymagaj manualnych kliknięć/komend/plików.
- Jedyna lista blokerów: `BLOCKERS.md`. Normy: `AGENTS.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/USB_PRIVATE_DIAGNOSTICS.md`.
- Nie publikuj prywatnych numerów, grantów, transkryptów, WAV ani całych plików runtime report w Git task-results. Prywatny eksport tylko na Macu `~/Documents/AI-Calls/private-exports/`. `record_call_evidence=false` oznacza prawidłowy `USB=NO_DATA`.

## Problem z poprzednich fizycznych sesji
- F-033: powtarzanie `STATE_TASK_SUBJECT` przez wiele tur zamiast odpowiedzi na pytania konsultanta (5 razy z rzędu). Stan modelu **nie oznacza** kompetentnej rozmowy.
- F-034: pierwszy `EOS→TX=47 567 ms` na fizycznym S22, mimo STT ~4–6 s; cold-start/inferencja lokalnej Gemmy dominowała czas oczekiwania. To może powodować brak odpowiedzi w oczach IVR.
- RX/STT/Gemma/TTS/TX mają rzeczywiste bajty i tury, ale dowód niezależnej słyszalności uplinku i zewnętrznej zmiany CLIR pozostaje **OPEN**.

## Zmiany źródłowe
1. `DialogueActionTurnMemory.kt`: nietrwała historia ostatnich tur (bez trwałej publikacji STT), informacje o wykonanej akcji. W wersji `eb2f5058`: `adjustRepetitiveSubject` koryguje ponowne `STATE_TASK_SUBJECT` w kolejnym pytaniu do neutralnego `ASK_NEXT_SERVICE_STEP`, jeśli rozmówca nie pyta wyraźnie ponownie o cel. Fakt/efekt pozostaje pod polityką autoryzacji.
2. `DialogueActionBackend.kt`, `GateCHybridDialogueBackend.kt`: 3 nowe proceduralne akcje `EXPLAIN_SERVICE_SCOPE`, `ASK_NEXT_SERVICE_STEP`, `REQUEST_SERVICE_TRANSFER`; oddzielne, zatwierdzone teksty, a nie słynna powtarzana odpowiedź.
3. `ClirDeterministicDialogueBackend.kt`: szybkie zweryfikowane odpowiedzi na częste sformułowania CLIR przed kosztowną inferencją Gemmy. Nie automatyzuje danych wrażliwych ani potwierdzenia zmiany.
4. `CampaignGemmaPreflight.kt`, `AppOwnedCampaignService.kt` i przepływ tworzenia backendu: prewarm **tego samego** lokalnego klasyfikatora przed `Telecom.placeCall`, następnie ponowne sprawdzenie bieżącego grantu/roli/SIM/IDLE oraz przekazanie backendu do aktywnego połączenia. STOP/grant revoke usuwa tylko nieużywany model.
5. `scripts/aicall_tools/diagnostics/s22_autoloop.py`: odczyt wyłącznie zanonimizowanych liczników tur, rozkładu akcji i `EOS→TX`; nigdy nie traktuj bajtów PCM jako dowodu słyszalności u zdalnego konsultanta.

## Zrealizowane dowody QA
- Mac `aicalls-20261103-f034-prewarm-source-qa-apk-cache-v1` (źródło `a93e10df`): pełne Kotlin unit/build/lint, Python unit i anti-friction **PASS**, APK przygotowany.
- Mac `aicalls-20261103-f033-f034-stop-old-deploy-context-probe-s22-v1`: stara kampania zatrzymana aplikacyjnym STOP przy `IDLE`, instalacja PASS, siedem rzeczywistych klasyfikacji Gemma off-call **7/7**, rola dialera/grant zachowane.
- Mac `aicalls-20261103-f034-prewarm-real-call-latency-s22-v1`: fizyczna rozmowa na `a93e10df`: OFFHOOK→IDLE bez wymuszonego STOP, `RX=1 985 280 B`, `TX=1 028 726 B`, `5` tur, pierwsza odpowiedź `EOS→TX=14 394 ms`, maks. jedno `STATE_TASK_SUBJECT` z rzędu. Zewnętrzny efekt CLIR nadal OPEN.
- Mac `aicalls-20261103-f034-owned-campaign-cleanup-latency-inspect-v3`: inna kolejna sesja po autoryzowanym retry miała `EOS→TX=11 620/13 640/11 966/13 723 ms`, Gemma decyzje `8–11 s`, TTS ~1–2 s; **powtórne trzy `STATE_TASK_SUBJECT`**. Prewarm fizycznie potwierdzony (`SUCCESS=true`, cold-start `11 192 ms`), ale sam prewarm nie naprawia interpretacji.
- Mac `aicalls-20261103-f033-f034-fast-dialogue-guard-build-s22-v1` (pinned `eb2f5058`): **DONE/0**, `ANTI_FRICTION=PASS`, `PYTHON_QA=PASS`, **FULL_KOTLIN_UNIT_BUILD_LINT=PASS**, dokładny S22 i `IDLE`, APK `INSTALL=PASS`, nowy contextual Gemma probe **7/7 PASS**, telefon `IDLE`. To weryfikuje *źródło i off-call*, nie rezultat rozmowy na zmienionej logice.

## Zadanie w toku w momencie utworzenia handoffu
`aicalls-20261103-f033-f034-fast-guard-physical-call-retest-v2` w `MichalMatu/local-agent`:
- Task JSON: `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/aicalls-20261103-f033-f034-fast-guard-physical-call-retest-v2.json`
- Result JSON: `.../task-results/aicalls-20261103-f033-f034-fast-guard-physical-call-retest-v2.json`
- Pinned `eb2f505831ddca17a395f666c4e7f08529306c55`; *reuse installed*, bez ponawiania zaliczonych buildów i off-call probes. Skrót raportu ma zawierać `OFFHOOK`, `IDLE`, `ACTION_*`, `METRIC_FIRST_EOS_TO_TX_MS`, `METRIC_MAX_SUBJECT_STREAK`, `DIALOGUE_FAILURE_CODE`; task powinien po zakończeniu **przy IDLE** zatrzymać tylko aplikacyjne ponowienia, nigdy nie wykonać globalnego hangup.
- **Pierwsze działanie kolejnego agenta:** odczytaj ten wynik; nie wyzwalaj drugiego równoległego połączenia. Przed kolejnymi zmianami sprawdź nowy HEAD `main`, stan Mac/ADB i grant/rolę; nie podawaj numeru w rezultatach Git.

## Kryterium dalszego domknięcia
- F-033: obserwacja prawdziwych tur: brak mechanicznego powtarzania tego samego tematu na kolejne różne pytania, właściwe proceduralne odpowiedzi, bez nieautoryzowanego ujawnienia/efektu. Dopiero wynik fizycznego testu może dać `VERIFIED` w zakresie dialogowym.
- F-034: pomiar wielu latencji `EOS→pierwszy TX`; `prewarm` sam daje poprawę, ale Gemma nadal może mieć ~10 s na turę. Odróżnij fast-path od klasyfikatora, popraw zalegającą inferencję, bez sztucznych limitów tur/prób/grantów.
- Sukces CLIR wymaga osobnego dowodu od operatora/systemu; nawet `RX>0` i `TX>0`, zdalny `OFFHOOK` i 7/7 Gemma nie są dowodem skutku zewnętrznego.
- Po zmianach: `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`, Python regression, Kotlin focused/full tests, build/lint w Mac/ChatGPT Linux Sandbox zgodnie z AGENTS, a potem S22 exact ADB, wpis źródło/test/commit do `BLOCKERS.md`. Zachowaj artefakty/handoff, nie twórz konkurencyjnej listy blokerów.
