# AI Calls — fallback bezczynności INCOMING: 30 s + 15 s (2026-11-06)

## Decyzja i implementacja

Właściciel potwierdził dwa fizyczne testy poprawnego samodzielnego end_call na S22 i poprosił o niezależny 30-sekundowy timeout. Preflight repo main e993b78cd509428d44f99baf6c1297d5b86fa020, czysty checkout, S22 SM-S906B / RFCT70L7E8J IDLE, kampania nieaktywna, retry false, polityka AI, 39 sesji. Ostatnie dwie mają pełne sekwencje END_CALL REQUESTED → VALIDATED → DISPATCHED → CONFIRMED, status owned_call_ended oraz COMPLETE. Nie ujawniono prywatnych treści w zleceniach ani Git.

Jedyna polityka IncomingSecretaryScenario: po 30 000 ms aktywnego nasłuchu bez wykrytej mowy wygenerować deterministyczne ostrzeżenie przez istniejące zatwierdzanie tekstu i TTS/TX. Po jego zakończeniu zacząć nowe 15 000 ms aktywnego nasłuchu; odpowiedź rozmówcy cofa zagrożenie rozłączeniem i wraca do dialogu Gemmy. Dalsza cisza uruchamia deterministyczne pożegnanie, a po pełnym TX dopiero istniejące session-owned end_call. Nie liczyć czasu generacji, syntezy, TX ani odstępów retry STT; głos zeruje zegar, samo ponawianie rozpoznawania bez mowy go nie zeruje. Połączenia OUTGOING, manual, STOP, przejęcie oraz zamrożony RX/TX/Shizuku bez zmian.

## Testy i dowody

- Nowy kontrakt JVM oczekiwany RED przed implementacją; GREEN dla 30 s, 15 s, czasu TTS, resetu po głosie, braku resetu przy STT retry, końcowego bezpiecznego end_call dopiero po PLAYBACK_COMPLETE i odmowy po cofnięciu.
- Pełny skrypt scripts/verify_host.sh: host_quality_gate_green=true; 266 testów Python, Gradle test/lint/APK i audyty PASS. scripts/aicall_tools/diagnostics/audit_product_friction.py PASS.
- Native S22: AndroidIncomingIdleTimeoutTest z prawdziwym Android TTS pokazuje zatwierdzony komunikat ostrzegawczy i pożegnanie oraz dokładnie jedno wywołanie narzędzia po granicy odtwarzania; PASS (1). AndroidConversationToolProtocolTest wybranej Gemmy/kontraktu narzędzi: PASS (1). To testy bez telefonii i bez fizycznego upływu 45 s.
- Nowy app-debug.apk SHA-256 c85c4a4b10e7d35cd3c8d8521b4bc63223f7a2c22e76a73aa1bc6d4fb6d65e88; androidTest APK SHA-256 7c25427793351cab0aa3dc822733c9506ee464f80b3ae0240d6c0979d517ab9b. Podpis zgodny z istniejącą aplikacją, aktualizacja -r z zachowaniem danych. S22 przed i po IDLE, kampania nieaktywna, retry false, profil Android STT/Gemma E2B/Android TTS i upoważnienia niezmienione.
- Prywatne receipts na Macu: ~/Documents/AI-Calls/local-agent/2026-11-06-incoming-idle30, katalog 0700, testowe logi 0600. Nie publikowano WAV, numerów ani transkryptów.

## Następne kryterium fizyczne — QA_PENDING

A8 dzwoni na S22: po powitaniu milczeć bez rozłączania; po około 30 s aktywnego RX powinno być ostrzeżenie; po dalszych około 15 s aktywnego RX powinno nastąpić pożegnanie, następnie automatyczne rozłączenie bez kolejnego RX. Drugi wariant: odpowiedź po ostrzeżeniu musi zachować Call i wznowić zwykły dialog. Weryfikować timeline, etap CONFIRMED, pełny TX, END/COMPLETE, IDLE i brak zmiany polityki. Nie oznaczać fizycznego timeoutu jako PROVEN_S22 bez dowodu. F-099 pozostaje FIXED_IN_SOURCE; F-091/F-098 potwierdzono wcześniej dwiema osobnymi fizycznymi rozmowami.
