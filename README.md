# AI Calls

Androidowe, modułowe laboratorium STT → model/Skills → TTS oraz kontrolowanych scenariuszy telefonicznych. Obecnie służy do weryfikowania komponentów i ich integracji; nie jest jeszcze dowiedzionym, kompletnym wykonawcą dowolnych zadań ani zamkniętym archiwum audio.

## Aktualny stan — 2026-11-08

**Baseline kodu:** `main@edf63c1ea9dc425c91d2a1571a90038631852ffd` (UX-11 CLOSED; kolejne commity porządkują wyłącznie dokumentację; przy wznowieniu sprawdzić świeży HEAD).

- **UX-12 (tail):** historia szczegółów z kontekstem, nowe prywatne i deterministyczne zbieranie screenshotów; targeted QA 14/14 PASS; pełny host gate 365/365 PASS, archiwalne zdjęcia S22 17/17 walidacji PASS; świeże screenshoty kodu UX12 nadal NOT_RUN. [Checkpoint](docs/checkpoints/2026-11-08-ux12-final-source-closeout.md).
- **UX:** sześć zakładek Start / Historia / Agent AI / Laboratorium / Modele / Ustawienia; UX-11 zakończony i zamrożony poza błędami funkcjonalnymi P0/P1. Czat icon-only tylko podczas aktywnego feedu; Sejf standardowy + własne pola, czytelny Lab.
- **Mac:** ostatni pełny gate na kodzie `949a854`: **356/356 testów Python, Gradle/JVM/AndroidTest/APK/friction PASS**. Zmiana test-only `3029034`: AndroidTest compile PASS.
- **S22:** **28/28 UI** + **2/2 syntetyczny aktywny chat PASS**, **18 prywatnych screenshotów**; instalacja bez kasowania danych i potwierdzone IDLE przed/po.
- **Funkcje offline:** prawdziwy lokalny model zakończył **2 tury i STOP (PASS)**; polski TTS → PCM → Parakeet STT **PASS** na testowym tekście.
- **Nieudowodnione:** rzeczywisty Call/RX/TX, wynik zewnętrzny zadania, pełne audio/manual takeover, kompletna ścieżka CRUD i ujawniania danych sejfu. F-080, F-122, F-123, F-124 pozostają OPEN.

## Co dalej

**Nowy etap to testy funkcjonalne ekran po ekranie i funkcja po funkcji — nie kolejny sprint kosmetyczny.** Zanim cokolwiek oznaczysz PASS, sprawdź działanie, efekt/stan, STOP, restart i prywatność w faktycznym dopuszczonym zakresie. Aktywna macierz to [FUNCTIONAL_ACCEPTANCE_MATRIX](docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md).

[Kompletny checkpoint](docs/checkpoints/2026-11-08-pre-functional-full-checkpoint.md) · [Bieżący handoff](docs/HANDOFF_NEXT_CHAT.md) · [Prompt do nowego czatu](docs/NEXT_CHAT_PROMPT.md) · [Roadmapa](docs/ROADMAP.md) · [Rejestr blokad](BLOCKERS.md)

## Architektura i tryb pracy

Moduły: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. Jeden `AndroidConversationRuntime` współdzieli porty STT/model/TTS między INCOMING i OUTGOING, przy zachowaniu rzeczywistej kontroli własnego Android Call, STOP, zgód oraz zasad udostępniania danych.

Pracować **GitHub Connector + Mac Local Agent**; dla izolowanych testów używać sandboxu. Bez Codex, ChatGPT Work, GitHub Actions (również odczytu statusów), tworzenia zbędnych gałęzi i force-push. Commity `[skip ci]`. Prywatne zdjęcia, WAV, numery i transkrypty zostają na urządzeniu/Macu. Źródła normatywne: [AGENTS](AGENTS.md), [Security](docs/SECURITY_PRIVACY.md), [Laboratorium](docs/TEST_LAB_SCOPE.md).
