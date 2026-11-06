# AI Calls — etap 1: IncomingIdleTimeout w conversation-core (2026-11-06)

## Zakres, który nie zmienia działania

Baza: main 5a63f8b27e16078dc337d59ed795fbbe2c6f76ec, clean i zgodny z origin. Przeniesiono jedną niezależną od Androida klasę IncomingIdleTimeout z app do istniejącego JVM conversation-core. Jedynymi zmianami w tej klasie są package i widoczność publiczna wymagana przez moduł app; odtworzenie starych dwóch nagłówków daje identyczny SHA-256 d08ff767f36a395c2a79d48f767748929a6509348b81e74864dc5649973746c2. Czasy 30/15 s, restart po głosie, STT no-match, opóźnienie między RX i TTS i algorytm WARN/CLOSE bez zmian.

AndroidConversationRuntime oraz AndroidIncomingIdleTimeoutTest otrzymały wyłącznie import nowej klasy. Dwa czyste testy timeoutu przeniesiono do core, a dwa integracyjne testy approved playback/tool pozostały w app jako IncomingIdlePlaybackTest. Dwa dodatkowe testy core weryfikują granice zegara i reset po głosie. Nie zmieniono Handler/SystemClock, pipeline, modelu, TTS, Call, grantu, STOP, przejęcia, PCM, PFD ani Samsung/Shizuku.

## Dowody techniczne

- Etap 0: [baseline i mapa zależności](2026-11-06-mod-stage0-baseline.md).
- Pierwszy nowy test clock miał niepoprawne założenie: dodatkowe bezpośrednie expire() w czasie odtwarzania symulowało nowy nasłuch wcześniej niż runtime; RED pochodził wyłącznie od testu. Poprawiono scenariusz, bez modyfikowania algorytmu produkcyjnego. Oryginalny FAIL zachowano w prywatnym logu focused-build.txt.
- Weryfikacja po poprawie: :conversation-core:test, celowany :app:testDebugUnitTest oraz :app:compileDebugAndroidTestKotlin PASS. Pełne bash scripts/verify_host.sh: host_quality_gate_green=true, Gradle BUILD SUCCESSFUL, JVM/lint/APK/audyty PASS, 266 Python tests PASS. audit_product_friction.py PASS. Ścieżka logów Mac: ~/Documents/AI-Calls/local-agent/2026-11-06-modular-stage1 (0700; logi 0600). Git diff --check PASS.
- Żadnego zainstalowanego APK ani nowej rozmowy na S22. Obecnie działająca na telefonie wersja pozostaje niezmieniona. Host PASS nie zastępuje dodatkowego fizycznego testu; brak podstaw, by powtarzać wcześniej zatwierdzony fizyczny dialog tylko z powodu przeniesienia klasy.

## Następny kontrolowany krok

Etap 2: osobny przegląd liczby konsumentów typowanych ConversationOutcome/ConversationOutcomeBoundary oraz wybór najwyżej jednego czystego kontraktu do wydzielenia (lub odroczenie gdy importy rosną). Bez zmiany działającej semantyki i bez przebudowy całego AndroidConversationRuntime.
