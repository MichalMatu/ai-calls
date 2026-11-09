# AI Calls — G4.1/G4.2 dynamic Skills: prawdziwe E2B off-call E2E, PASS (2026-11-09)

## Rozstrzygnięcie i źródła

**G4.1 CONTRACT_MAC_PASS** i **G4.2 S22_OFFCALL_E2E_PASS (scoped)**, po fizycznej naprawie regresji pamięci **F-157 VERIFIED_SCOPED**. Nie oznacza to pełnego G4, jakości wszystkich Skills ani rzeczywistego Call. G3 wybór modelu `KEEP_E2B` jest zakończony i niezmieniony; Qwen3.5 nadal wyłącznie Lab-only. Źródło przed dokumentacyjnym checkpointem: `main@91bcd0bc7cb8e45a528f3822a8fed51cf944dbea`. Po dokumentacji odczytaj nowy HEAD; nie zakładaj starego.

**Dokładny finalny S22 receipt:** `MichalMatu/local-agent:agent-control`, `aicalls-f157-g4p2-e2b-memory-s22-e2e-retest-20261109-v4` **DONE/0**, wszystkie 5 komend exit=0. Wskazany host `macbook-michal/cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, urządzenie `SM-S906B` serial `RFCT70L7E8J`.

## Implementacja

1. `ConversationSkillTurnRouter` (G4.1, `main@39212f6570173275a1bff44949e0ab96992f39c7`) wybiera z zamrożonego snapshotu wersji Skills przy każdej wypowiedzi użytkownika. Wykrywa wiadomość, sprostowanie i jawny pivot tematu; zachowuje aktywne moduły przez follow-up; wyłączona wersja nigdy nie wraca; nieznany custom Skill pozostaje przypięty. `SkillLoadingMode.ALL_ACTIVE` zachowuje dawne zachowanie; `CONTEXTUAL` to eksperyment Lab.
2. `NativeConversationInstructionBackend` (G4.2, `main@ad42c7b501ac719a40f82893130a0de31cee5fe8`) umożliwia dobrowolne odświeżenie instrukcji między turami. `LiteRtGemma4Runtime.replaceSystemInstruction` odmawia operacji podczas generowania i zamyka stary `Conversation`, pozostawiając kosztowny `Engine` zaalokowany. `Gemma4LiteRtTextBackend` zachowuje generacyjny fencing; normalny Call nie wywołuje nowej operacji.
3. `DynamicSkillLabBackend` (tylko `CONTEXTUAL` w `AndroidSkillLabSessionFactory`) zachowuje w pamięci ukończone pary wypowiedź/odpowiedź. Przy zmianie Skills odbudowuje *system instruction* ze zwykłym `ConversationInstructionBuilder` i zacytowaną, kodowaną jako JSON, rolami oznaczoną historią jako **danymi, nigdy uprawnieniami**. Nie dodaje kompetencji do nieufnego wejścia STT, nie wykonuje narzędzi, nie czyta IdentityVault ani nie zapisuje nowego transkryptu do repo. `SkillLabRunner` pozostaje właścicielem STOP i sesji.
4. Ostateczne źródło i testy `main@91bcd0bc7cb8e45a528f3822a8fed51cf944dbea`. Domyślny profil telefonu E2B, budżet Call 2048, RX/TX Samsung i zakres uprawnień są niezmienione. Lab w tym teście używa 4096, dynamiczna selekcja tylko po wybraniu `CONTEXTUAL`.

## Odbiór i RED → GREEN

| Bramka | Wynik | Dowód |
| --- | --- | --- |
| G4.1 per-turn contract + 6 JVM | PASS | `aicalls-g4p1-perturn-skill-contract-mac-qa-20261109-v2` DONE/0; targeted JVM, full `verify_host.sh`, anti-friction, clean |
| G4.2 pierwsza implementacja + 4 JVM | PASS Mac, S22 jeszcze nie wtedy | `aicalls-g4p2-native-skill-refresh-mac-qa-20261109-v1` DONE/0, AndroidTest compile, 371 host Python, lint/audio/Gradle |
| G4.2 pierwszy fizyczny RED | FAIL | `aicalls-g4p2-e2b-native-runner-s22-e2e-20261109-v1`: natywny test przerwany przez `g4_memory_before_topic_change`; S22 IDLE przed/po, APK bez wipe; F-157 OPEN |
| Diagnoza natywnego lifecycle | PASS odczytowy | `aicalls-f157-g4p2-native-memory-telemetry-readonly-20261109-v1`: 4 kolejne GPU `conversation_reused=false`, Engine init tylko przy pierwszej; więc reset/przełączenie faktycznie następowały |
| Pierwsze poprawki kontrolne | RED przed urządzeniem | Retest v2/v3 miał przestarzałą asercję pełnego zdania po zmianie łamania linii; naprawiono test, bez udawania GREEN |
| **F-157 finalny E2E** | **PASS fizyczny** | `aicalls-f157-g4p2-e2b-memory-s22-e2e-retest-20261109-v4`, Mac pełna bramka + 4/4 natywne tury + STOP, wszystko exit=0 |

**W finalnym v4 na fizycznym S22:** `G4P2_S22_CALL_IDLE_GATE=PASS`, oba debug APK zainstalowane bez wipe; `AndroidG4DynamicSkillsNativeE2ETest#runnerGemmaDynamicSkillsPreservesMemoryAndStops` **1/1 PASS**; `turns_completed=4`, `skills_refresh=3`, `memory_before_pivot=true`, `memory_after_pivot=true`, `native_timing_count=4`, `stop=true`, `profile_preserved=true`, `G4P2_POST_DEVICE_CALL_IDLE=True`. Źródło końcowe clean. Test używa produkcyjnego `SkillLabRunner` → `AndroidSkillLabSessionFactory` → `DynamicSkillLabBackend` → native `LiteRtGemma4Runtime`; nie jest tylko testem mocka.

**Finalny Mac gate v4:** celowane JVM + APK + AndroidTest build PASS, `bash scripts/verify_host.sh` pełny PASS (371 Python, Android Gradle JVM/lint/APK/AndroidTest/audio), anti-friction PASS, czysty HEAD `91bcd0bc7cb8e45a528f3822a8fed51cf944dbea`.

## Granice i dalsza kolejność

- PASS jest **scoped**: jeden czteroturowy, wyłącznie syntetyczny dialog tekstowy, jeden E2B/GPU na S22, włączony tryb `CONTEXTUAL`, bez realnego STT/TTS/Call, bez autoryzowanych efektów zewnętrznych. To nie gwarancja nieskończonej pamięci ani ocena naturalności w pełnych rozmowach. 4096 kontekstu przy długim dialogu nadal może wymagać polityki rollover — F-148 i human review pozostają osobne.
- UI pokazuje aktualnie selekcję na początku sesji, nie pełny dziennik każdej zmiany między turami; to osobne G4.3 UX/telemetria, nie deklarować pełnej widoczności.
- **Następny krok G4.3:** uczyń dynamiczne metadane `name/description/capability` czytelnymi w wersjonowanym Skill i laboratorium; dodaj testy odświeżenia wersji, raport przejść między turami i kontrolę *typed tool proposal → app validation*, bez wykonywania skutków. Dopiero po G4 prowadź G5 STT→E2B+Skills→TTS i human semantic review; G6 INCOMING/OUTGOING prawdziwy Call wymaga dokładnego grantu. Bez powtórzeń G3 i G4.2 bez nowej przesłanki.
- Tryb: GitHub Connector + wybrany Mac Local Agent + ChatGPT Sandbox; żadnych Codex, Work ani GitHub Actions (w tym statusu), force-push, nowych branchy, nieautoryzowanych Call i danych w Git. Każdy commit `[skip ci]`.
