# AI Calls — sprint pętli diagnostycznej i usuwania sztucznego tarcia
Data: 2026-11-03

## Źródło prawdy i granice
- Normatywny zakaz dokładania sztucznych blokad: pierwsze linie `AGENTS.md`.
- Jedyny aktualny rejestr i status: [BLOCKERS.md](../../BLOCKERS.md).
- Nie zmieniać w ciemno praw rozmówców, zgody nagrywania, bezpieczeństwa STOP, granic własnego Android Call ani odmów narzędzi. Nie usuwać walidacji identyfikatorów akcji / parserów jako pozornego „odblokowania”.
- GitHub Connector + Mac Local Agent; bez Codex/Work/GitHub Actions. Przed każdą kolejną zmianą odczytać aktualny HEAD.

## Zrealizowane źródła
1. **F-002/F-012/F-013/F-014/F-015**: wcześniejsze uproszczenie jednorazowego nagrywania i podwójnych zgód, testów limitorów i ochrony przed auto-start. Mac QA v3 zakończył się PASS na `338ce662`.
2. **F-010**: `CampaignContinuationStore` zapisuje rozpoczęte, dokładnie powiązane z grantem i kartą SIM zadanie, następne podejście, historię niedokończonych prób i STOP. `AppOwnedCampaignService` planuje następną próbę bez nowego kliknięcia, z malejącą częstotliwością bez maksymalnej kwoty prób; próbuje wznowić zaakceptowany bieg usługi Android START_STICKY, lecz nigdy nie ożywia odwołanego grantu.
3. **F-018**: spóźnione wyniki z wcześniejszego uruchomienia nie powinny nadpisywać STOP ani planować dodatkowego połączenia; zablokowane przez `continuationGeneration` oraz kontrolę grantu.
4. **F-011 częściowo**: `LocalTtsTextChunker` + `LocalTtsSpeechOutput` porcjują długie zatwierdzone wypowiedzi zgodnie z limitem pojedynczego wywołania systemowego TTS, zachowując kolejność i pełną treść. `DialogueSkillPolicy` nie odrzuca zatwierdzonej odpowiedzi po 600 znakach, `BackendOpenAiTextBackend` nie odrzuca 2000 znaków z już ograniczonej odpowiedzi JSON.
5. **F-003 częściowo**: `CampaignEvidenceArchive` tworzy ZIP z zakończonego folderu (timeline, WAV RX/TX, COMPLETE), nie pobiera plików obcych. Jest przetestowany jednostkowo, ale **nie jest połączony z UI**. Próba integracji wyboru celu z Android `CampaignToolActivity` została odrzucona przez narzędzie; F-017 OPEN. Nie obchodzić odmowy inną drogą.
6. **Testy i rejestr**: `NoArtificialCampaignBlockersContractTest`, `CampaignContinuationPolicyTest`, `CampaignContinuationStoreContractTest` (kompilacja AndroidTest), `CampaignEvidenceArchiveTest`, `LocalTtsTextChunkerTest`, długościowe testy odpowiedzi i aktualny Python tripwire wymagają także numerów F-016..F-019.

## Dowody QA
- `aicalls-20261103-friction-two-layer-source-qa-v3`, SHA `338ce662`: ANTI_FRICTION/Python/Kotlin build-unit-APK-lint PASS, bez telefonu.
- `aicalls-20261103-continuation-sprint-source-qa-v4`, SHA `a2b5ef8`: PASS source.
- `aicalls-20261103-full-sprint-final-source-qa-v5`, SHA `e057cf8`: Python 222 PASS, Kotlin 633/1 FAIL przez niepoprawne oczekiwanie trailing whitespace; poprawione w `fde032c`, zapis F-019.
- `aicalls-20261103-sprint-verified-source-qa-v6`, SHA `32ff901b20481c5670ce157e856cf42fc217308b`: **PASS** — Python 222/222, skaner, Kotlin units, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`. `BUILD SUCCESSFUL in 1m 35s`. To źródłowe QA, nie test live-call.
- `aicalls-20261103-s22-readonly-preflight-v7`: `AUTHORIZED_DEVICE_COUNT=0`; na Macu nie było podłączonego S22. Nie uruchamiano ADB install, zmiany roli dialera, połączeń ani nagrań.

## Gotowe APK na Macu (bez ingerencji w telefon)
Mac Local Agent `aicalls-20261103-sprint-export-debug-apk-v8` DONE/0, source SHA `32ff901b`:
- `/Users/michal/Downloads/ai-calls-sprint-32ff901b-debug.apk` (SHA256 `0cde4749fc86e1ee2b69977f279a10c6de8de1e3c79c3549fa1f2fe8f3226c67`).
- `/Users/michal/Downloads/ai-calls-sprint-32ff901b-androidTest.apk` (SHA256 `12450f21cedecfdc477a2a2f0df09b358d07157183b12f637d22087690d2edd7`).
- Nie było instalacji, zmiany roli ani połączenia; aplikacja gotowa do instalacji po podłączeniu S22.

## Niedomknięte — nie wolno ogłaszać pełnego PASS
- F-003/F-017: wygodny, prywatny eksport przez UI nie działa; moduł archiwum samodzielnie nie zapewnia UX.
- F-011: limit 4000 znaków wejścia brokera i fizyczne długie dialogi; klasyfikator JSON/ID ma własne zasadne limity protokołu. Nie podnosić ich bez kontraktu backendu.
- F-016: nieaktualne historyczne zdania w aktywnych dokumentach; próba usunięcia odrzucona przez narzędzie, nadrzędna jest początkowa zasada AGENTS oraz BLOCKERS.
- S22: brak aktualnego instalacyjnego, instrumentacyjnego i długiego telefonicznego PASS; testy AndroidTest tylko zbudowane.

## Bezpieczny dalszy krok
Gdy dokładnie jeden fizyczny S22 jest widoczny w ADB i w stanie IDLE, zainstalować APK przygotowany z SHA `32ff901b`, uruchomić wyłącznie bezpołączeniowe testy `CampaignContinuationStoreContractTest` oraz `AndroidCampaignAuthorizationStoreContractTest`, potem prowadzić świadomie zainicjowane testy kampanii w już zatwierdzonym zakresie. Nie wykonywać automatycznych połączeń w zadaniu buildowym ani publikować prywatnych WAV/transkryptów w GitHub/task-results.

## Kontynuacja sprintu 06:43 — źródła nowsze niż poprzedni APK

- Punktem wejścia był main `a5c85b8` z poprzednim QA v6 PASS, urządzenie wcześniej niedostępne.
- Mac `aicalls-20261103-s22-check-0643-readonly-v9` DONE/0: SM-S906B, jeden autoryzowany S22 pod ADB, `mCallState=0`.
- Mac `aicalls-20261103-s22-install-readiness-v11` DONE/0: `INSTALL_READY=YES_READONLY`, wersja zainstalowanej aplikacji 2, nieaktywny `AppOwnedCampaignService`, bez ingerencji w telefon.
- F-020: wykryto, że pilotaż audio w czasie oczekiwania na retry mógł przejąć serwis i wyczyścić kontynuację. `AppOwnedCampaignService.startSupervisedAudio` nie przechodzi do pilota, gdy zatwierdzona kampania już czeka; nie dodano dialogu ani limitu. Test kontraktowy z `NoArtificialCampaignBlockersContractTest`.
- F-011: `scripts/aicall_tools/text/openai_text_broker.py` oraz `BackendOpenAiTextBackend` nie odrzucają już tekstu po 4000 znakach wejścia i 2000 znakach wyjścia. Broker utrzymuje ograniczenie rozmiaru HTTP dla ochrony pamięci i tokenów modelu, a nie licznik produktu. Test Unicode STT >4000, długi wynik >2000, bounded HTTP i nieukończony upstream. Bez użycia klucza API i bez zewnętrznych rozmów.
- Mac QA v10 SHA `3f5b345`: Python 225 PASS, Kotlin 637 testów/1 FAIL w teście kontraktowym F-020. Powodem był fałszywy match komentarza `finish()/STOP` do `.contains("finish(")`, **nie błąd aplikacji**. Rejestr F-021, poprawka testu do sprawdzania linii wykonywalnego wywołania.
- Mac pełne QA `aicalls-20261103-broker-pilot-sprint-qa-v12`, pinned `f42ce03e14d9be60ef4036d6850971fc27e99832`: **QA_PENDING** do publikacji potwierdzonego terminalnego wyniku. Nie instalować nowego APK ani nie oznaczać PASS tylko na podstawie kolejki.
- Po v12 PASS można zainstalować nowe APK wyłącznie na dokładnie jednym S22 w stanie IDLE i z nieaktywną usługą kampanii; uruchomić jedynie instrumentację SharedPreferences (bez wykonywania połączeń). Do realnego dialera/rozmowy konieczne są granice systemowej roli i konkretnej zgody aplikacyjnej.
- F-003/F-017 eksport z UI i F-016 historyczna dokumentacja pozostają OPEN z odmową narzędzia; nie próbować obchodzić odmowy.
