# AI Calls — reaudyt quality gate, modułowości i balastu (2026-11-06)

## Dowody

HEAD przed porządkowaniem: main 5cf3a11c766c4a8e4179179dd6797491f20886f9, zgodny z origin, czysty. Mac Local Agent: bash scripts/verify_host.sh PASS (host_quality_gate_green=true), Gradle build/JVM/lint/APK PASS, 266 testów Python PASS; audit_product_friction PASS. W repo 213 plików testowych Kotlin, 220 produkcyjnych Kotlin; liczba plików nie jest liczbą testów. 90 Markdown, zero uszkodzonych linków lokalnych, zero tracked files >400 kB. Repo pozostało czyste po bramce. Prywatne receipts: ~/Documents/AI-Calls/local-agent/2026-11-06-quality-reaudit. Nie instalowano nowego APK ani nie wykonywano nowych połączeń.

## Modularność i zbędny kod

Sześć modułów Gradle: app (197 plików produkcyjnych Kotlin), audio-bridge (3), privileged-helper (inne źródła), realtime-client (17), conversation-core (2, JVM), conversation-tools (1, JVM). app zależy od pięciu pozostałych, realtime-client od audio-bridge. Największe pliki: AndroidConversationRuntime 815 linii, CallRealtimeSessionOrchestrator 758, LocalTextCallGateDProductIntegration 718, Gemma4LiteRtTextBackend 625. Największe pakiety app: campaign=26, textagent=25, developerrelay=17, conversation=17, localcall=16, agent=14.

Statyczne referencje: AndroidConversationRuntime w 7 plikach produkcyjnych, CallRealtimeSessionOrchestrator w 4, LocalTextCallSession w 5. LocalPhoneLlmLiveCallProbe i GenericPhoneTaskAcceptanceProbe mają aktywne wejścia przez DiagnosticProbeActivity. Same nazwy Probe ani rozmiary nie dowodzą balastu; produkcyjnych klas, prób i archiwalnych dowodów nie usuwano. W następnym pionowym kroku przenosić neutralne polityki/stan do istniejących JVM conversation-core/conversation-tools, potem wydzielać Android-niezależne fragmenty dużego runtime. Przed usunięciem kodu wymagać mapy konsumentów i testów. Zamrożony Samsung RX/TX/Shizuku pozostaje nietknięty.

## Stan fizyczny i ograniczenia

Dwie wcześniejsze rozmowy A8→S22 mają end_call REQUESTED/VALIDATED/DISPATCHED/CONFIRMED i END/COMPLETE. Osobny timeout 30+15 s przeszedł host RED→GREEN oraz 2/2 S22 native. Właściciel potwierdził fizyczne rozłączenie po ciszy. Bezpieczny późniejszy odczyt S22: 40 zapisanych sesji wobec 39, IDLE, kampania i retry nieaktywne, 0 app error, 0 warnings, 0 fatal w ostatnich 2695 liniach logcat. Szczegółowe markery warning/grace/TOOL_EVENT/COMPLETE ostatniej sesji nie zostały odczytane. F-099 ma zatem potwierdzenie operatora i statusu, lecz nie kompletny event-level receipt.

## Dokumentacja i backlog

Bieżący handoff był obciążony historycznymi, sprzecznymi statusami; został skrócony bez kasowania dowodów: stary tekst zachowuje commit 5cf3a11, pozostałe checkpointy i historia Git. Roadmap, README, architektura, indeks i sekretarka zaktualizowane. Historyczne transient compile F-093–F-095 oznaczono VERIFIED (compile/host), a F-096 VERIFIED (JVM/host) z potwierdzonym testem fault-injection obserwatora. F-092 i F-089 nadal OPEN w odrębnych zakresach; F-100 śledzi poprawiony dryf dokumentacyjny. Rejestru blockerów ani prywatnych WAV/transkryptów nie usunięto.

**Następne kroki:** (1) fizyczne manual/przejęcie/powrót AI/STOP oraz odpowiedź po ostrzeżeniu; (2) mała modularizacja czystej polityki do JVM z testami; (3) F-080 pełny transkrypt/audio i player; (4) F-044/F-045/F-048 uniwersalne zadania, dane i skutki; (5) powtarzalne korpusy porównawcze STT/model/TTS. Nie ponawiać zakończonych fizycznych prób bez nowej przesłanki.
