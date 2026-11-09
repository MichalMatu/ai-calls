# AI Calls

Androidowe, modułowe laboratorium STT → model/Skills → TTS oraz kontrolowanych scenariuszy telefonicznych. Obecnie służy do weryfikowania komponentów i ich integracji; nie jest jeszcze dowiedzionym, kompletnym wykonawcą dowolnych zadań ani zamkniętym archiwum audio.

## Aktualny stan — 2026-11-09

- **S22 nawigacja:** pełny Mac quality gate i fizyczna macierz **10/10 PASS** (kontrakt 5/5, tab flow 2/2, ekrany ustawień 3/3), F-151 VERIFIED. NAV-01 pozostaje PARTIAL dla długiego back-stack/restart.
- **USB Lab:** debug APK/AndroidTest na S22 bez wipe, `AndroidUsbLabControlContractTest` **3/3 PASS** oraz prawdziwe `status/config-get/ram` **3/3 PASS**, F-152 VERIFIED. Pierwszy `lab-start` i STOP fizycznie wykonane; `lab-send` wymagał poprawki stdin (F-153 HOST_PASS), retest urządzenia BLOCKED PocketHost foreground. Nie deklarować dwuturowego USB native PASS.
- **Bezpieczeństwo i granice:** Call IDLE; bez nowych rzeczywistych połączeń, brak pełnego live takeover/resume/exact STOP, osobne F-150/F-148 nadal otwarte. [Aktualny checkpoint](docs/checkpoints/2026-11-09-nav01-f151-usb-f152-reacceptance.md) · [Handoff](docs/HANDOFF_NEXT_CHAT.md).

## Stan historyczny — 2026-11-08

**Aktualny kod sprzed zbiorczego checkpointu:** `main@af571f06a7a34b05a14a8e0b54b8642f40a89e57`. Od stanu UX-11 dodano rzeczywisty Lab i nowy kanał USB; przy wznowieniu należy odczytać świeży HEAD.

- **UX-12 (tail):** historia szczegółów z kontekstem, nowe prywatne i deterministyczne zbieranie screenshotów; targeted QA 14/14 PASS; pełny host gate 365/365 PASS, archiwalne zdjęcia S22 17/17 walidacji PASS; świeże screenshoty kodu UX12 nadal NOT_RUN. [Checkpoint](docs/checkpoints/2026-11-08-ux12-final-source-closeout.md).
- **UX:** sześć zakładek Start / Historia / Agent AI / Laboratorium / Modele / Ustawienia; UX-11 zakończony i zamrożony poza błędami funkcjonalnymi P0/P1. Czat icon-only tylko podczas aktywnego feedu; Sejf standardowy + własne pola, czytelny Lab.
- **Mac:** najnowszy `aicalls-usb-lab-shared-native-lease-mac-qa-20261108-v4` **DONE/0**, 370/370 Python, Gradle/JVM, lint, APK/AndroidTest build, audyt audio PASS. Starsze testy UX zachowane w ich checkpointach.
- **S22:** **28/28 UI** + **2/2 syntetyczny aktywny chat PASS**, **18 prywatnych screenshotów**; instalacja bez kasowania danych i potwierdzone IDLE przed/po.
- **Funkcje offline:** prawdziwy lokalny model zakończył **2 tury i STOP (PASS)**; polski TTS → PCM → Parakeet STT **PASS** na testowym tekście.
- **Bramki otwarte:** nowy odbiornik USB na S22, dodatnie interwencje Call nowym kanałem, pełne audio/manual takeover, rezultat zewnętrzny, pełna ocena semantyczna i F-148/F-150. Historyczne dowody audio/Call nie są odbiorem nowego kanału.

## Gemma i prywatny kanał USB — 2026-11-08

- **Gemma E2B 4096:** trzy realne sesje po 13 tur, **39/39 technicznie PASS** na S22; heurystyki 11/13, 13/13, 11/13; human semantic UNASSESSED. 2048 dwukrotnie zatrzymało się w turze 10, 8192 na S22 NOT_RUN.
- **Kanał USB/ADB:** prywatne START/SEND/STOP, RAM/status, konfiguracja audio i incoming, kontrolowane przejęcie/wznowienie/STOP własnego Call. Kod i Mac QA PASS, nowa APK S22 NOT_RUN. [Kompletny checkpoint](docs/checkpoints/2026-11-08-usb-lab-telephony-full-checkpoint.md).

## Co dalej

**Nowy etap to testy funkcjonalne ekran po ekranie i funkcja po funkcji — nie kolejny sprint kosmetyczny.** Zanim cokolwiek oznaczysz PASS, sprawdź działanie, efekt/stan, STOP, restart i prywatność w faktycznym dopuszczonym zakresie. Aktywna macierz to [FUNCTIONAL_ACCEPTANCE_MATRIX](docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md).

[Kompletny checkpoint](docs/checkpoints/2026-11-08-pre-functional-full-checkpoint.md) · [Bieżący handoff](docs/HANDOFF_NEXT_CHAT.md) · [Prompt do nowego czatu](docs/NEXT_CHAT_PROMPT.md) · [Roadmapa](docs/ROADMAP.md) · [Rejestr blokad](BLOCKERS.md)

## Architektura i tryb pracy

Moduły: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. Jeden `AndroidConversationRuntime` współdzieli porty STT/model/TTS między INCOMING i OUTGOING, przy zachowaniu rzeczywistej kontroli własnego Android Call, STOP, zgód oraz zasad udostępniania danych.

Pracować **GitHub Connector + Mac Local Agent**; dla izolowanych testów używać sandboxu. Bez Codex, ChatGPT Work, GitHub Actions (również odczytu statusów), tworzenia zbędnych gałęzi i force-push. Commity `[skip ci]`. Prywatne zdjęcia, WAV, numery i transkrypty zostają na urządzeniu/Macu. Źródła normatywne: [AGENTS](AGENTS.md), [Security](docs/SECURITY_PRIVACY.md), [Laboratorium](docs/TEST_LAB_SCOPE.md).
