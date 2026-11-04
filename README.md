# AI Calls

**Host-only bramki jakości M1–M4:** [checkpoint 2026-11-04](docs/checkpoints/2026-11-04-audio-models-hostonly-quality-gates-checkpoint.md). Produkcyjny APK S22 bez zmian; testy mutacyjne, PCM, WAV i profilów na Mac bez telefonu. Nie jest to dowód live ani dostępności Piper.

## Sprint S22 po ponownym podłączeniu — potwierdzony 2026-11-04

- Źródło zainstalowanego nowego APK: `0d0fdfe22e9b002c8a9dc557ad5d95a882aa5976`. Mac full QA **DONE/0 PASS**, fizyczny S22 **DONE/0 PASS** dla profilu STORE 6/6, UI i czterech syntetycznych tur Gemma RAW. S22 `SM-S906B`, 2× IDLE, grant, rola, retry i odciski prefów bez zmian; żadnego połączenia.
- **M2 Parakeet:** 614 588 752 B, exact SHA-256 zweryfikowany na S22; MediaPipe native prewarm + przetwarzanie syntetycznego 16k PCM **PASS** (`aicalls-20261104-audio-parakeet-s22-native-acceptance-v1`). Dokładność transkrypcji polskich wypowiedzi i współdzielenie pamięci z Gemma w aktywnym Call **OPEN**.
- **M3 Piper:** adapter, Mac QA PASS; zewnętrzny pakiet `dev.ihorshevchuk.piper` **nieobecny** na S22, więc głos i rzeczywisty TTS Piper **NIEZWERYFIKOWANE** (brak cichego fallbacku).
- **M4 Gemma Audio Direct:** natywne AudioBytes PCM synthetic **PASS** (`aicalls-20261104-audio-gemma-direct-s22-native-v1`); pełny polski dialog i pamięć przy jednoczesnym E2B nadal niepotwierdzone. Tryb pozostaje wyłączony dla aktywnego profilu.
- F-053 został naprawiony i **VERIFIED** Mac + S22 off-call; F-045/047/048 pozostają OPEN, F-049 wycofany; brak nowego CLIR/live-call proof.

[Dokładne receipts](docs/checkpoints/2026-11-04-audio-engines-m2-m4-sprint-checkpoint.md).

Lokalny agent telefoniczny na Androida: użytkownik autoryzuje konkretny cel, target, konto/SIM i dozwolone skutki, a aplikacja prowadzi własne połączenie przez **STT → Gemma 4 E2B → TTS/TX** z kontrolą grantu i selektywnym STOP. CLIR jest pierwszym testowanym zadaniem; rezerwacje i inne skutki nie są jeszcze ogólnie dostępne.

## Potwierdzony stan — 4 listopada 2026

- **Realny S22+ (`SM-S906B`):** Gemma Direct RAW prowadzi wieloturowy dialog. Zakończone, prywatnie zarejestrowane sesje o 06:30 i 06:35 (Europe/Warsaw): odpowiednio **8/8 i 7/7** rozpoznanych wypowiedzi biura, własnych odpowiedzi Gemmy i pełnych lokalnych TX; 0 `DIRECT_REJECTED`. To **techniczny sukces toru rozmowy**, nie dowód wykonania CLIR lub słyszalności każdej wypowiedzi u operatora.
- **Zamrożony punkt odniesienia RAW:** `0d44b7d5a7e01daa118da10c2e04c458086dced9`. Kod źródłowy z dynamicznym, autoryzowanym CLIR `TaskBrief`, czterema tekstowymi prompt-skills i prywatnym czatem STT/Gemma/TX ma ostatni potwierdzony APK S22 z `268be1f8f13502c7202822438b501706e0fcb0e1`. Mac host QA, instalacja exact S22 i 4 syntetyczne natywne tury PASS. Bieżący `main` HEAD należy zawsze odczytać ponownie (dokumentacja przesuwa historię).
- **Obserwowalność:** dolny panel rozmowy w aplikacji prezentuje biuro/STT, zatwierdzony tekst Gemmy, TTS/TX i godziny. Właściciel potwierdził, że UI i dialog działają dobrze. Wewnętrzny czat jest nietrwały i nie jest archiwum danych.
- **Ostatni odczyt po rozmowie:** S22 `IDLE`, brak oczekującego ponowienia. Transkrypt, WAV i chronologiczny raport ostatnich sesji pobrano prywatnie na Mac, nie do repozytorium.

## Co pozostało

1. **M1 PASS (Mac + S22 off-call, live acceptance OPEN):** osobne menu trzech silników, typowany `AudioEngineProfile`, niezależny zapis, rejestr i readiness, wybór lokalnego głosu Android TTS, wymienne adaptery STT/TTS, profil przypinany do prewarm i całego owned-call. Kandydaci Parakeet PL, E4B, Piper PL, Gemma Audio Direct, Kokoro/PocketTTS pozostają jawnie nieaktywni bez zweryfikowanego runtime. Pierwszy Mac QA znalazł `MissingPermission` w nowym odsłuchu i był FAIL; poprawka F-050 jest poprawione i Mac QA v2/v3 PASS. Wymienny adapter modelu E2B dodano następnie w `990a8dd8`; Mac QA v4 wykrył F-051 (typ resetu kontekstu). Poprawiono w `a616795e`; Mac QA v5 i S22 off-call v2 **DONE/0 PASS**. APK i AndroidTest zainstalowane, profil/UI i 4 syntetyczne tury Gemma RAW PASS, S22 IDLE, grant/skills bez zmian. To nie jest dowód nowego żywego dialogu lub efektu CLIR. [Checkpoint M1](docs/checkpoints/2026-11-04-audio-engines-m1-source-checkpoint.md).
2. **F-048 — bezpieczne fakty:** Gemma zna cel CLIR, ale nie ma autoryzowanego `RequestFact` do kontrolowanego dostarczania informacji z `IdentityVault`. Nie wklejać danych osobowych do promptu; każdą decyzję o ujawnieniu podejmuje aplikacja.
3. **F-047 / F-045:** odzyskiwanie transkryptu RX przerwanego przez koniec Call (diagnostyka już działa) oraz semantyczna kontrola swobodnego tekstu RAW. Nie potwierdzono zewnętrznego skutku CLIR.

**F-049 wycofane przez właściciela:** dwuczęściowe powitanie infolinii jest prawidłowym zachowaniem po jej stronie. Nie planować zmian VAD, ACK ani opóźnienia Gemmy z tego powodu.

**Pełny bieżący checkpoint:** [2026-11-04 — RAW live / fakty / historia IVR](docs/checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md). **Jedyny rejestr statusów:** [BLOCKERS.md](BLOCKERS.md). **Następny sprint:** [ROADMAP](docs/ROADMAP.md) i [HANDOFF](docs/HANDOFF_NEXT_CHAT.md).

## Granice

Pracuj w trybie GitHub Connector + Mac Local Agent; bez Codex, Work, GitHub Actions, nowych samowolnych połączeń i force-push. Dokładny scope grantu, Android Telecom, prywatność rozmówców, STOP i zamrożony tor Samsung/Shizuku nadal obowiązują. Dokumentacja i wyniki testów nie nadają uprawnień. Historyczne dowody są w `docs/checkpoints/`, źródła zasad w [AGENTS](AGENTS.md), [architekturze](docs/ARCHITECTURE.md) i [bezpieczeństwie](docs/SECURITY_PRIVACY.md).

## Nowe etapy M2–M4 (2026-11-04)

- **M2 Parakeet PL — SOURCE + MAC PASS** (`a0d0000`): rzeczywisty MediaPipe AudioTranscriber 1.2.0, worker z nieblokującą kolejką 16 kHz, finalne segmenty, bez cichego fallbacku; pełny Mac QA `aicalls-20261104-audio-m2-parakeet-host-qa-v1` DONE/0. Model 615 MB wymaga instalacji i pomiaru na S22; test fizyczny **OPEN**. Model 614 588 752 B pobrany na Mac i zweryfikowany SHA-256. W aplikacji dodano na żądanie pobieranie Parakeet z pinem rewizji, kontrolą SHA-256 i anulowaniem; nowy kod oczekuje na odrębny Mac QA.
- **M3 Piper PL — SOURCE + MAC PASS** (`8037eaa`): odrębna Android TextToSpeechService `dev.ihorshevchuk.piper`, filtrowanie polskiego głosu offline z `piperVoice`, brak milczącego fallbacku do Android TTS. Pierwszy Mac QA M3 v1 wykrył F-052 (**FAIL/1**), po poprawce Mac `...m3-piper-postfix-host-qa-v2` **DONE/0 PASS**. Fizyczna obecność Piper i polskiego głosu na S22 nadal **OPEN**. Oddzielny silnik jest publikowany przez zewnętrzną aplikację Piper, nie dołączany do AI Calls.
- **M4 Gemma Audio Direct — EXPERIMENTAL SOURCE + Mac PASS, native S22 OPEN** (`fc91c17`, `cce18b1`): osobny natywny PCM16→WAV→LiteRT `Content.AudioBytes` z `audioBackend=CPU`, ograniczenie 30 s i izolowany worker; przewód do input port istnieje, ale menu celowo blokuje profil do czasu natywnego GPU/RAM i prawdziwego S22 QA. Ta wersja adaptera wykonuje rozpoznawanie audio do tekstu; nie jest jeszcze pełnym multimodalnym modelem konwersacyjnym ani bezpiecznym speech-to-speech.
- Ostatnia próba readonly S22 `aicalls-20261104-audio-m3-m4-s22-readonly-runtime-inventory-v1`: **FAILED/1, ADB device not found**; żaden nowy APK M2–M4 nie został w ramach tych tasków zainstalowany. Nie wykonywano połączenia. Historyczny M1 off-call PASS pozostaje punktem odniesienia.

[Checkpoint sprintu](docs/checkpoints/2026-11-04-audio-engines-m2-m4-sprint-checkpoint.md).
