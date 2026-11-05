# Runtime, skills/dane i historia — 2026-11-05

Kod `e72bb15995a5637f53cfc258917488ab91b7732a`, baza `67c2382`; bezpośredni lokalny Codex/Git/Gradle/USB, bez Local Agent zadań/Work/Actions. [Architektura i zakres](../CONVERSATION_RUNTIME.md).

## Zmiana

Przeniesiono jedynego wykonawcę do AndroidConversationRuntime. CLIR i tryb nadzorowany mają oddzielne scenariusze, wspólny runtime nie zawiera Orange/CLIR/permitów ani odczytu tożsamości. Wyodrębniono kierunek i kontekst przygotowania, szyfrowany magazyn, wspólny kompilator instrukcji, rejestr własności sesji oraz live/archiwalny czat. Bieżące callery i kontrakty/audyty zmigrowane, usunięto stare klasy i UI grantu skills, bez aliasów.

Profile i notatki nie upoważniają do połączeń/skutków/ujawnień. Wartości osobowe pozostają w IdentityVault. Trwała historia odczytuje istniejący prywatny timeline przy dotychczasowej opcji nagrań/transkryptu, bez drugiej bazy i bez nowego domyślnego zapisu. Zdarzenia kierunku i statusu TX odróżniają kontekst, tekst zatwierdzony i wysłanie lokalne.

Automatyczny odbiór nie został dodany; INCOMING ma miejsce w przygotowaniu, instrukcjach, metadanych i kontrakcie scenariusza, a przyszły adapter ma związać właściwy Call z tym samym silnikiem. Samsung/Shizuku/PCM/PFD/VAD/quick ACK bez zmian parametrów i sterowania.

## QA

- RED kontraktów nowych modułów: `/tmp/ai-calls-conversation-modules-red.log`, brak typów przed implementacją.
- Pierwszy build migracji FAIL: `/tmp/ai-calls-conversation-modules-green.log`, import panelu i dwa odwołania do przeniesionej polityki (F-077); brak wdrożenia.
- Focused modułów i snapshotu GREEN: `/tmp/ai-calls-conversation-modules-green-fixed.log`, `/tmp/ai-calls-conversation-snapshot-green.log`.
- JVM całej aplikacji PASS: `/tmp/ai-calls-conversation-modules-jvm.log`.
- Pełny host: `/tmp/ai-calls-conversation-modules-host.log`, `host_quality_gate_green=true`; 785 JVM (724 app, 51 realtime-client, 10 privileged-helper), 266 Python, lint wszystkich modułów, oba APK i audyty PASS. Po końcowym przeglądzie guard transportu tożsamości obejmuje także wydzielony scenariusz CLIR; końcowy ponowny gate w `/tmp/ai-calls-conversation-modules-host-reviewed.log`.
- Native/instalacja: **8/8 PASS**. Zgodność podpisów i instalacja bez czyszczenia danych; 5 istniejących kontraktów scope/STOP/UI w `/tmp/ai-calls-conversation-modules-native-baseline.log`. Następnie 3 nowe kontrakty: szyfrowany kontekst/reopen/oddzielenie kierunków bez zadania, jednorazowa migracja preferencji oraz ekrany przygotowania/historii bez zmiany task authority/audio. `android-conversation-modules-native.txt` i `native-conversation-summary.json` w prywatnym katalogu. Dawną preferencję skills migruje się raz; zachowana semantyka, nie wymaga zachowania jej starego pliku/hashu.
- APK app SHA-256 `abb10cb318ea432437ee20494c44ca6c3aea78560ec1ab72c06b61b8ef121046`; AndroidTest `0798a941dd75a0af41793fb6fd87c4994511f6391d997a3b63fd2e79404e0a8c`.
- Nowa rzeczywista regresja Orange: **7 pełnych tur STT→Direct→TX**, źródło GEMMA_DIRECT_RAW=7, techniczne `REAL_PIPELINE_COUNTERS_PRESENT`, RX 3 162 880 B / TX 1 399 606 B. Siedem zatwierdzonych odpowiedzi, sześć różnych. Cel testu: wspólny runtime, metadane kontekstu, kontrolowany STOP i zamknięcie archiwum — nie akceptacja jakości całego flow lub CLIR.
- Zakończenie fizycznego testu aplikacyjnym **STOP własnej sesji**, reason `operator_stopped`, probe_complete=true. Końcowo 2× IDLE, kampania nieaktywna, retry false. Nie przedstawiać nazwy pola after_autonomous_idle z runnera jako autonomicznego zakończenia tej próby.
- Prywatny timeline: direction OUTGOING, profile outgoing-clir, response_kind MODEL_DIRECT; 7 COUNTERPART_STT, 7 AGENT_APPROVED_TEXT i 7 AGENT_FULL_TX/WRITE_COMPLETE, END. Archiwum USB PASS; na urządzeniu 33 sesje (przed próbą 32), bez danych w Git/logach zwykłych.
- Pierwsze EOS→TX 19 756 ms, mediana 6406 ms; pojedyncza naturalna rozmowa nie stanowi porównania wydajności przyczynowego. F-042 i ocena jakości/postępu pozostają osobno. **INCOMING auto-answer i zewnętrzny skutek CLIR nie są przetestowane ani deklarowane.**

F-075: konfiguracja bez grantu; F-076: wspólny model i dostęp do historii; F-077: naprawiona migracja; F-078: brak I/O konfiguracji przed STOP; F-079: rejestr i lifecycle właściciela. Aktualne statusy tylko w BLOCKERS, nie zamykać fizycznego zakresu testami hosta.

## Receipts i granice

Testy urządzenia wykonano na roboczym drzewie opartym na `67c2382`, następnie zapisanym w powyższym commicie. Oryginalne receipts zachowują ten stan źródła; verified-source.json wiąże je z commitem i hashami APK. Po QA usunięto jedynie nadmiarową pustą linię na końcu ConversationMessageView.kt; bez zmiany zachowania.

Prywatnie `~/Documents/AI-Calls/local-codex/2026-11-05-conversation-modules/`: task-intake-final-apk-sha256.json, task-intake-native-summary.json, native-conversation-summary.json, outgoing-regression-report.json, outgoing-regression-summary.json, outgoing-regression-private-timeline.jsonl i pełny eksport w private-exports. Folder 0700, pliki 0600. Raw WAV/transkrypty pozostają wyłącznie na S22 i lokalnym Macu. Ostatni profil: ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL, voice auto.

F-075/F-076 zweryfikowane w opisanym zakresie host/native/OUTGOING; F-077 poprawia migrację źródeł i QA, F-078 kolejność STOP bez store I/O, F-079 atomowe przyjęcie i release sesji. Brak fault-injection uszkodzenia Keystore podczas realnego połączenia; nie oznaczać tego osobnego wariantu jako PASS. Poprzednie F-044/F-045/F-048/F-061 pozostają otwarte w kanonicznym rejestrze.
