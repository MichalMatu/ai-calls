# AI Calls — general repository audit & repair sprint (2026-11-07)

## Źródła i zakres
- Repo: `MichalMatu/ai-calls`; GitHub `main`, bez nowych gałęzi, bez GitHub Actions/Codex/Work.
- Preflight remote `main@1517bda419746cb1fd1c20278915019650962b31`; repo zawiera 908 tracked blobs / 1104 tree entries, 6 modułów Gradle, 195 app JVM tests, 57 app Android instrumentation files i 81 Python test files w `scripts/tests` (liczba plików, nie liczba scenariuszy).
- Mac Local Agent: `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; checkout `/Users/michal/agent-workspace/repos/ai-calls/work`. Preflight receipt `aicalls-general-audit-preflight-20261107-v1` DONE/0: clean `main@97b3293`, po fetch `origin/main@1517bda` (jeden commit tylko dokumentacyjny za remote), origin poprawny, JDK/Python/ADB dostępne.
- Historia z preflight (obliczona z `97b3293`): 982 commits / 7 dni, 982 / 30 dni i 2046 / 90 dni; zmiany skupiają się wokół Android UI, zasobów PL/EN, kontrolerów kampanii, scenariuszy i dokumentacji. Nie traktować samej liczby commitów jako dowodu poprawności.
- Wyłączone z tego zakresu: nowe funkcje produktu, refaktor zamrożonego PCM/PFD/Shizuku, ingerencja w aktywne połączenia, cofnięcie grantów, prywatne WAV/screenshoty/transkrypty, testy prawdziwych połączeń bez osobnego autoryzowanego celu.

## Potwierdzona usterka i minimalna naprawa
- `ScenarioPersonalLibrary.PersonalScenario.revised` porównywał surowy tytuł i cel z wcześniej zapisanymi, przyciętymi wartościami. Zmiana tylko białych znaków tworzyła zbędną rewizję.
- Reprodukcja: `ScenarioPersonalLibraryTest.unchangedMetadataWithSurroundingWhitespaceDoesNotCreateExtraRevision`, commity testów `18e8650` i `1bf2a38`; Mac Local Agent `aicalls-general-audit-scenario-revision-red-20261107-v1` FAILED oczekiwanie: 5 testów, 1 RED `ScenarioPersonalLibraryTest.kt:39`.
- Poprawka: `bbb2ceb9a9e267073b67242b5b30350e047a5437`: `trim()` przed walidacją i porównaniem no-op; dotyczy wyłącznie czystego modułu JVM `conversation-core`, bez zmian Android runtime, zgód, telefonii i mediów.
- ChatGPT Linux Sandbox: izolowany compile/run test Kotlin, 5/5 GREEN na odtworzonym małym kontrakcie; to **nie** pełny Android build ani odbiór S22.
- Mac QA GREEN: zadanie `aicalls-general-audit-scenario-green-full-host-20261107-v1` zlecone dla dokładnego `bbb2ceb`; **QA_PENDING** aż do odczytu `task-results/<id>.json`. Zakres: targeted JVM, pełne `bash scripts/verify_host.sh` (testy modułów/JVM, Android lint, APK + AndroidTest compile, Python/audio/secret checks), friction audit, clean worktree.
- Read-only audyt całego drzewa, historii 7/30/90, kandydatów `FLAG_SECURE`, wyjątków, sekretów, dokumentacji i toolingu: `aicalls-general-audit-source-matrix-20261107-v1`; **QA_PENDING** do rzeczywistego result. Sam statyczny skan nie zastępuje ręcznej walidacji każdej ścieżki Androida.

## Dokumentacja i jakość
- Stwierdzono nieaktualny stan UX w `README.md`, `docs/README.md` i `docs/NEXT_CHAT_PROMPT.md` (jeszcze UX-04/05 zamiast UX-06); odświeżenie obejmuje także bieżący handoff/roadmapę oraz ten checkpoint. Dawne checkpointy zachowane bez zmiany historycznych dowodów.
- `scripts/verify_host.sh` zawiera wielomodułowe testy JVM, Android lint, APK/test APK, Python, kontrolę audio i skan sekretów; anti-friction jest osobnym quality gate. Brak niezależnego potwierdzenia format-check i aktualnego audytu podatności zależności; nie deklarować ich PASS bez wykonania.
- Nie stwierdzono nowej sztucznej bramki użytkownika w powyższej naprawie; `BLOCKERS.md` pozostaje rejestrem statusów. Wszelkie nowe naruszenia polityki `AGENTS.md` wymagają osobnego wpisu.
- Poprzednia niezależna baza: `25a41a0` z Mac full PASS (341 Python + JVM/lint/APK/AndroidTest build/friction) i S22 10/10 **syntetycznych** testów `SM-S906B`, IDLE przed/po; szczegóły w `2026-11-07-ux06-source-s22-synthetic-acceptance.md`. Wyniki te **nie dowodzą** wdrożenia `bbb2ceb` na telefonie, rozmowy CLIR ani skutku zewnętrznego.

## OPEN i handoff
- F-122: zgoda na przyszłe nagrania nie zatrzymuje aktywnego recordera; F-123: procesowy live-state bez trwałego powiązania `attemptId` / Telecom / rezultatu; F-124: wybór archiwum i długość WAV; F-080: brak pełnego ciągłego nagrania i osi czasu. Nadal OPEN, nie modyfikowano w tym sprincie.
- Pozostałe presety poza CLIR są DRAFT_ONLY; wymagają oddzielnego adaptera, dokładnego grantu i dowodu skutku, zanim wolno je przedstawić jako wykonawcze. Bez manual TalkBack i bez nowych real-call testów.
- Każdy kolejny chat: świeży `main` HEAD, czysty checkout Mac i exact receipts; przeczytaj `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `BLOCKERS.md`, `docs/CONVERSATION_RUNTIME.md`, `docs/SECURITY_PRIVACY.md`, ten checkpoint. Nie powtarzaj zamkniętych UX-01–06 ani dawnych fizycznych PASS.
- Najbliższy sensowny osobny sprint po zakończeniu QA: najpierw poprawność aktywnego zapisu / prywatność (F-122) w warstwie izolowanej, później korelacja `attemptId` (F-123), archiwum (F-124) i ciągłe audio (F-080). Bez samowolnych rzeczywistych połączeń, bez podważania STOP i własności `Call`.

**Stan tego dokumentu:** kod `bbb2ceb` opublikowany; checkpoint dokumentacyjny. Dowody Mac source-matrix i pełnego GREEN wpisuj dopiero po rzeczywistych receipts. Czystość Mac po nowych commitach dokumentacji musi zostać ponownie potwierdzona.
