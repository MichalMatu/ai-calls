# AI Calls — etap 0: zabezpieczenie flow i mapa zależności (2026-11-06)

Punkt odniesienia: `main f87bea5ae6a358862334216d48bcef7e7b2e1bc3`, równy `origin/main`, czysty. Przed migracją wykonano bez modyfikacji kodu: celowane JVM `:conversation-core:test`, `:conversation-tools:test`, `:app:testDebugUnitTest --tests *IncomingIdleTimeoutTest*` — PASS; `bash scripts/verify_host.sh` PASS z `host_quality_gate_green=true`, Gradle build/lint/APK PASS, 266 testów Python PASS; `audit_product_friction.py` PASS; worktree clean. Bez instalacji APK i nowego połączenia.

## Mapa obecnej logiki

- Moduły Gradle: `app=197`, `audio-bridge=3`, `conversation-core=2`, `conversation-tools=1`, `realtime-client=17` produkcyjnych plików Kotlin; privileged-helper ma inne źródła.
- `IncomingIdleTimeout` występuje w produkcji wyłącznie w `app/.../AndroidConversationRuntime.kt` oraz własnym pliku. Pozostałe dwa odwołania: test JVM `app/.../IncomingIdleTimeoutTest.kt` i native `app/.../AndroidIncomingIdleTimeoutTest.kt`. Sama klasa to 1337 bajtów, SHA-256 `d08ff767f36a395c2a79d48f767748929a6509348b81e74864dc5649973746c2`.
- JVM app: dwa testy czystego timeoutu (30 s, 15 s, reset głosem i brak resetu retry) oraz dwa testy wykonawcy narzędzia po playback i cofnięcia prawa.
- Native S22: `AndroidIncomingIdleTimeoutTest.selectedAndroidTtsWarnsThenPlaysFarewellBeforeToolWithoutTelecom` — istniejący wynik 1/1 PASS, tego dnia niepowtarzany.
- `conversation-core`: dwanaście przypadków `IncomingAssistantCoordinatorTest` i siedem `IncomingConversationCompletionTest`, w tym takeover, stare generacje, STOP/hold, revocation, wyłączność Call i pojedynczy disconnect.
- App regression: `ConversationToolPlaybackTest` ma cztery przypadki, w tym blokowanie end_call przed playback i takeover. `ConversationRxInterruptionCodeTest` pokrywa typowane powody zakończenia.

## Wniosek i brakujące dowody

Klasa `IncomingIdleTimeout` nie korzysta z Androida i nadaje się do istniejącego `conversation-core`. Przenieść ją bez zmiany ciała metod i zachowania; czyste testy do core, integracyjne playback w app, jeden import w Android runtime i native. Nie przenosić `Handler`, `SystemClock`, TTS, PCM, Telecom ani własności Call. Zmiana pakietu wymaga publicznej widoczności klasy na granicy Gradle (nie jest to nowa aplikacyjna funkcja ani zmiana interfejsu użytkownika).

**Wciąż QA_PENDING:** szczegółowy timeline fizycznego timeoutu 30+15 s, odmowa zakończenia po odpowiedzi rozmówcy na ostrzeżenie, manual/TAKE OVER/AI resume/STOP w fizycznym Call. Właściciel potwierdził funkcjonujące samorozłączenie; nie twierdzić, że sam host dowodzi wszystkich stanów.

**Następny punkt:** etap 1 — jedna klasa do JVM, GREEN/testy i pojedynczy commit z możliwością revertu.
