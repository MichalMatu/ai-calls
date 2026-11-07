# AI Calls — handoff po general repository audit (2026-11-07)

Wznów `MichalMatu/ai-calls` na świeżym `main`; **najpierw** odczytaj `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `BLOCKERS.md`, `docs/ARCHITECTURE.md`, `docs/CONVERSATION_RUNTIME.md`, `docs/SECURITY_PRIVACY.md` oraz [checkpoint audytu](checkpoints/2026-11-07-general-repository-audit-sprint.md). Obowiązuje GitHub Connector + **Mac Local Agent** (`macbook-michal`, `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`), Linux Sandbox dla izolowanych testów; bez Codex, ChatGPT Work, GitHub Actions nawet do statusu, bez nowej gałęzi i force-push. Każdy commit `[skip ci]`.

## Nie powtarzaj zamkniętych prac
- Sprint 6 UX i UX-01–05: historyczne zielone receipts i S22 odbiór pozostają aktualne **wyłącznie dla wskazanych SHA i zakresów**.
- UX-06 source `25a41a0`: Mac 341 Python + JVM/lint/APK PASS, S22 10/10 syntetycznych UI na `SM-S906B` PASS; brak prawdziwych połączeń.
- General audit source `bbb2ceb9`: zbędne rewizje nazwy/celu osobistego scenariusza naprawiono normalizacją; nowy test RED `aicalls-general-audit-scenario-revision-red-20261107-v1` potwierdzony. Odczytaj dokładny aktualny wynik `aicalls-general-audit-scenario-green-full-host-20261107-v1` i skanu `aicalls-general-audit-source-matrix-20261107-v1`, **nie domniemaj** ich PASS. Checkpoint aktualizuj tylko z receipts.

## Obowiązujący sposób działania
Zweryfikuj GitHub `main` HEAD, `git status` Mac, dokładny task-result w `MichalMatu/local-agent:agent-control`, obsługę kontroli hosta, SDK i stan S22 przed jakąkolwiek operacją sprzętową. Oddzielaj `SOURCE_PASS`, `HOST_PASS`, `S22_SYNTHETIC_PASS`, `REAL_CALL_NOT_RUN` i `QA_PENDING`.

Najbliższe nierozwiązane F-122 (aktywne nagrywanie), F-123 (Call/attemptId/rezultat), F-124 (wybór archiwum), F-080 (ciągły recorder/player) oraz integracja nowych scenariuszy **nie są wykonane**. Prowadź każdy jako osobny, zreprodukowany i przetestowany zakres po konkretnym wskazaniu użytkownika albo następnym jawnym poleceniu sprintu. Nie rozwijaj automatycznie zaległych elementów tylko dlatego, że widnieją na roadmapie.

Bezwzględne granice: dokładny i odwoływalny grant, kontrola wyłącznie własnego Android `Call`, STOP, ujawnianie prywatnych faktów i brak rzeczywistych połączeń bez osobnego upoważnionego celu. Nie zmieniaj zamrożonego Samsung/Shizuku PCM/PFD bez uzasadnionego osobnego testu. Żadnych prywatnych WAV, screenshotów, numerów i transkryptów w GitHub.

**Przed końcem:** małe komity, minimalna poprawka + RED/GREEN, odpowiedni `bash scripts/verify_host.sh`, `audit_product_friction.py`, aktualizacja `BLOCKERS.md` tylko dla nowych naruszeń, checkpoint z exact HEAD/receipts i clean Mac. Syntetyczne testy nie są dowodem faktycznej rozmowy ani skutku rezerwacji.
