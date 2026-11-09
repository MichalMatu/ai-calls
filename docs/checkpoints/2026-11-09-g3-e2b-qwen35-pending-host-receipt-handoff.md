# AI Calls — G0–G3 pełny checkpoint i handoff (2026-11-09)

**Stan referencyjny przed tym commitem dokumentacyjnym:** \`main@29669674c694d722eb0fe47f482f0e06927f0848\`. Dokumentacja opisuje tylko zaobserwowane dowody; jej publikacja nie zmienia kodu Android, modelu, profilu ani urządzenia. Po commicie należy odczytać świeży HEAD.

**Jedyny plan:** [AI_CALLS_MASTER_EXECUTION_PLAN](../AI_CALLS_MASTER_EXECUTION_PLAN.md). **Pierwsza otwarta bramka: G3, 96-turn S22 A/B.** Nie uruchamiać równoległego wykonania i nie przechodzić do G4 przed decyzją G3.

## 1. Macierz stanu — nigdy nie zamieniać braku raportu na PASS

| Bramka | Stan i dowód | Czego wynik nie dowodzi |
| --- | --- | --- |
| G0 — F-158 Skills kontekstowe | **S22_PASS techniczny**, \`aicalls-g0-f158-s22-20261109-v1\` DONE/0; 24/24 tur E2B/GPU, 12 rekordów + 4 podsumowania, bez Call | Human semantic review i dobór Skill w każdej turze nieprzetestowane |
| G1 — źródło i sprzęt Qwen3.5 | **SOURCE_PASS**, \`aicalls-g1-qwen35-2b-metadata-hardware-20261109-v1\` DONE/0; pin/licencja/S22/ABI/RAM/flash | Same metadane nie są uruchomieniem modelu |
| G2 — Qwen3.5 tekst na S22 | **MAC_PASS + S22_PASS techniczny**, \`aicalls-g2-qwen35-lab-cpu-mac-qa-20261109-v2\` DONE/0 i \`aicalls-g2-qwen35-2b-cpu-native-s22-20261109-v1\` DONE/0; 3/3 non-Call tury na CPU | Brak wykazanej wyższej jakości i brak produkcyjnej telefonii |
| G3 — E2B vs Qwen3.5 | **MAC_PASS na \`bf31c48\`**, kompilacja macierzy + full host gate + friction; kod rozszerzający prywatny journal \`2966967\` oczekuje na nową kompilację. **S22: SUBMITTED / RECEIPT_PENDING**, bez potwierdzonego licznikowego postępu lub wyniku 96 tur | Żadna decyzja o wyborze głównego modelu, p95 ani jakości semantycznej |
| G4–G7 | **PLANNED**, bez awansu z G3 | Nie uznawać dynamicznych Skills / pełnych rozmów / Call / UX za wdrożone |

## 2. Implementacja i dowody G1/G2

- Backend: LiteRT-LM Android \`0.19.0\`. Kandydat \`QWEN_3_5_2B\` dodany do \`DialogueModelId\` / \`LanguageModelCatalog\`, tekst-only, **Lab-only**. Bez drugiego engine runnera. \`AudioEngineRegistry.incompatibility\` odmawia profilu telefonicznego Qwen3.5; modelu nie można aktywować zwykłym przyciskiem „Użyj”. Fabryczny \`GEMMA_4_E2B\` nadal domyślny.
- Dokładny artefakt: \`litert-community/Qwen3.5-2B\`, rewizja \`f24013ba6844bd39be2d1f68e8d2421a77eed1f2\`, \`Qwen3.5-2B_int8.litertlm\`, **2 119 843 840 B**, SHA-256 \`c2438cb9c050220e667366b4beeca4daac56e693b226420674f53fc40f007c49\`, źródłowa deklaracja Apache-2.0. Mac pobrał plik i sprawdził SHA, S22 zaimportował go istniejącą aplikacyjną ścieżką z weryfikacją SHA i czyszczeniem stagingu.
- Sprzęt: Samsung \`SM-S906B\`, Exynos \`s5e9925\`, Android ABI \`arm64-v8a\`; \`MemTotal=7 445 972 KiB\`, jednorazowe \`MemAvailable=2 853 956 KiB\` w G1. Na Exynos/Mali polityka kandydacka **CPU_ONLY**; nowa konwersacja dostaje niezależny Engine, by nie mieszać rekurencyjnego stanu Qwen3.5. GPU Qwen3.5 **NOT_RUN**.
- S22 G2 (\`aicalls-g2-qwen35-2b-cpu-native-s22-20261109-v1\`, DONE/0): APK/test APK bez wipe, Call IDLE przed i po, **test import 1/1 PASS**, test Qwen CPU 1/1 PASS: 3 tury/2 niezależne backendy, cold **23 983 ms**, warm **3 537 ms**, fresh **15 758 ms**, PSS procesu **2 897 MiB** i wolne RAM **2 837 MiB** w końcowym próbkowaniu, \`low_memory=false\`. To trzy obserwacje, nie benchmark statystyczny ani PSS peak.
- Host source G2 v1 miał 3 czerwone testy przy 818 JVM, ponieważ dotychczasowe kontrakty zakładały, że **każdy** katalogowy model jest gotowy do telefonu. Minimalna naprawa testów (zachowująca bezpieczny rozdział Lab/Call) \`4d695eb\` i dedykowany natywny test \`0f60782\`. G2 v2 **DONE/0**, celowane testy, pełny \`bash scripts/verify_host.sh\`, 371 Python, Kotlin/JVM, lint/APK/AndroidTest oraz \`audit_product_friction.py\` PASS. Nie powtarzać starych RED bez nowej przyczyny.

## 3. Co dokładnie zawiera G3

- Kod na \`main@bf31c485b776e34cf78fd3f1356d5424af9c1369\`: \`AndroidGemmaE2BQwen35DialogueABNativeTest\`, AndroidTest na jednej fizycznej S22, **8 scenariuszy PL × 3 niezależne powtórzenia × 2 modele × 2 tury = 48 sesji, 96 tur**.
- Scenariusze: \`intent_change\`, \`clarification\`, \`recall\`, \`correction\`, \`contradiction\`, \`privacy\`, \`message\`, \`goodbye\`. Kolejność startującego modelu naprzemienna; wspólny system prompt, fabryczny snapshot Skills, schemat \`reply/tool\`, kontekst \`2048\`, bez wykonawczych efektów. E2B standardowo GPU, Qwen3.5 CPU — **nie** należy interpretować różnicy latencji jako porównania architektur na jednakowym akceleratorze.
- Kod dodatkowy \`main@29669674c694d722eb0fe47f482f0e06927f0848\`: plik \`no_backup/g3-private/e2b-qwen35-ab-<timestamp>.jsonl\` z **wyłącznie syntetycznymi** parami tur, zapis po każdej zakończonej parze. Zadanie hosta ma wyeksportować go prywatnym \`adb exec-out run-as\` do \`~/Library/Application Support/ai-calls/qa/g3-private\` (tryb 0600). Transkrypty/treści i pliki prywatne **nigdy do GitHub, task result ani logcat**. Kod prywatnego journala **nie ma jeszcze osobnego potwierdzenia build/QA**; kompilacja jest pierwszą fazą oczekującego zadania.
- Logcat planowo emituje tylko \`AiCallModelG3AB\`: \`model,scene,repeat,cold_ms,warm_ms,backend,engine_ms,native_ok,json_ok,tools_ok,hint_applicable,content_hint,output_chars,context,summary,PSS,available RAM\`. Heurystyka treści (np. trafienie w kod) **nie jest automatyczną oceną sensu**. Prywatne reporty pozwolą na późniejszy review \`TOPIC, MEMORY, CORRECTIONS, FACTUALITY, NATURALNESS, QUESTIONS, PROMISES, ENDING\`.
- G3 Mac QA zadanie \`aicalls-g3-e2b-qwen35-ab-mac-qa-20261109-v1\` na SHA \`bf31c48\`: **DONE/0** — \`assembleDebug\`, \`assembleDebugAndroidTest\`, full \`verify_host.sh\` i anti-friction PASS. Nie kopiować tego PASS automatycznie na nowszy \`2966967\`.

## 4. Dokładnie jedno oczekujące zadanie sprzętowe

**Task ID:** \`aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1\`.

- Repo kontrolne: \`MichalMatu/local-agent\`, branch \`agent-control\`.
- Task: \`.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1.json\`.
- Wynik: ta sama ścieżka \`task-results/aicalls-g3-e2b-qwen35-96turns-s22-20261109-v1.json\`. GitHub REST \`GET /repos/MichalMatu/local-agent/contents/<ścieżka>?ref=agent-control\`.
- Publikacja: commit zadania \`503f47a76c555e4cb9ce0865e3e5b9a2d5d3a471\`, **2026-11-09 08:58:23 UTC = 09:58:23 PL**. Pin aplikacji \`29669674...\`. Wymagane \`repo:ai-calls\` i \`adb:rfct70l7e8j\`; \`target_host_id=macbook-michal\`, \`target_installation_id=cfea4cc3-b54c-4b6d-b9a6-758d12733e98\`, checkout \`/Users/michal/agent-workspace/repos/ai-calls/work\`.
- Etapy: (1) clean/pin Git; (2) kompilacja APK + AndroidTest z nowym prywatnym journalem; (3) fizyczny IDLE preflight, instalacja bez wipe, 96-tur \`am instrument\`, prywatny eksport, agregaty + post-IDLE; (4) clean Git. Timeout pojedynczego etapu 6400 s, całego zadania 7600 s.
- **Stan przy tym checkpointcie:** task istnieje. Dokładny \`task-results\` **404 / RECEIPT_PENDING**. Brak potwierdzenia START, PASS, FAIL, liczby wykonanych tur lub eksportu prywatnego pliku. \`status.json\` Local Agent raportuje \`state=idle\` lecz ma stare \`updated_at=2026-11-09T08:23:50.206119Z\`; NIE jest bieżącym dowodem bezczynności Maca.
- Przesłany przez użytkownika log Mac Local Agenta ma ostatni wpis \`2026-11-09T09:27:24Z\` (10:27:24 PL). Przeszukanie pełnych ~272 tys. linii **nie znalazło dokładnego G3 task ID ani \`AiCallModelG3AB\`**. Widać \`ExecutionLeaseBusy\` (\`host-control-checkout\`), timeouty \`git pull --rebase ... agent-control\`, kolizje publikacji kontrolnej i \`host remote task intake failed: RuntimeError: host task runner currently active\`, przy aktywnych innych repo. Jest to **wskazówka zatoru control-plane**, nie dowód konkretnej przyczyny ani dowód, że zadanie się nie wykona. Nie zlecać duplikatu.
- Nie wprowadzać samowolnych zmian w innych projektach (Growclip, PocketHost, ShellyLink) w celu naprawy kolejki AI Calls. Nie ingerować w działające taski/leases ani nie wymuszać usuwania locków.

## 5. Pierwsza czynność kolejnego czatu — działanie zależne od dowodu

1. Odczytaj świeży \`ai-calls/main\` HEAD, \`AGENTS.md\`, plan i ten checkpoint. Następnie **najpierw dokładny result ID G3**, a nie stary status hosta.
2. Jeżeli raport jest \`done\`: sprawdź **każdy** exit code, liczbę 48 sesji/96 tur, 2 summary, backend, błędy CPU/GPU, JSON/tools/heurystyki, IDLE, prywatny report 48 par. Jeśli \`failed\`: zdiagnozuj etap i wynik częściowy. Nie powtarzaj 96 tur, jeśli inferencja zakończyła się, a zawiódł tylko eksport lub finalizacja — odzyskaj konkretny artefakt.
3. Jeżeli nadal **404**: zbierz **świeższe niż 09:27Z** logi hosta i dowód claim/lease/process dla dokładnego ID; sprawdź control checkout/synchronizację kolejki, czy task jest przyjęty lub wykonywany, a także zajętość S22. **Bez drugiego testu, force-push, kasowania lease i restartu na ślepo.** Dopiero gdy potwierdzono nieuruchomienie i brak aktywnej rezerwacji, napraw pobór zadania i zleć świadome pojedyncze ponowienie.
4. Po kompletnym technicznym PASS nie wpisuj automatycznie \`PROMOTE_QWEN\`. Sprawdź prywatną semantykę wszystkich scenariuszy i ewentualnie wykonaj dodatkową **celowaną** próbę dla rzeczywistych błędów. Przy znacznej różnicy latencji CPU/GPU, rozróżnij jakość i koszty sprzętowe. Decyzja tylko \`KEEP_E2B\` / \`PROMOTE_QWEN\` / \`INSUFFICIENT_EVIDENCE\` na podstawie kryteriów [planu](../AI_CALLS_MASTER_EXECUTION_PLAN.md).
5. Dopiero po zapisanej decyzji G3 rozpocznij G4 dynamiczne Skills; osobno później G5 pełny głos, G6 właściwe autoryzowane połączenia i G7 UX. Nie powtarzaj G0/G1/G2, gdy mają DONE/0, bez nowej przesłanki.

## 6. Granice bezpieczeństwa i QA

- Tryb: **GitHub Connector + Mac Local Agent + izolowany ChatGPT Sandbox**, nie Codex, Work, GitHub Actions (również nie odczytywać statusu), nowe branch, force-push i transmisja prywatnego tekstu.
- S22: przed ADB weryfikuj \`SM-S906B\`, \`IDLE\`, brak własnej kampanii i brak konkurencyjnego tasku. Nie wykonuj rzeczywistych Call bez odrębnego, dokładnego upoważnienia w aplikacji. Własność Call, STOP, dane, IdentityVault i zamrożony Samsung RX/TX pozostają nienaruszone.
- **To checkpoint dokumentacyjny:** żadna nowa kompilacja, host QA, inferencja ani nowy test G3 nie zostały wykonane w trakcie jego zapisania. Stan \`RECEIPT_PENDING\` wymaga późniejszego odczytu, nie domysłu.
