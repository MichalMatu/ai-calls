# AI Calls — G4.3 finalny checkpoint: wersjonowane Skills, per-turn trace, typed tool policy (2026-11-09)

## Status i HEAD

**G4.3 = S22_OFFCALL_E2E_SCOPED_PASS**, a cała sekwencja **G4.1–G4.3 zakończona w zweryfikowanym zakresie Lab**. E2B pozostaje modelem głównym, Qwen 3.5 2B tylko w Lab. Bez zmiany telefonu, Call, grantu, domyślnego profilu czy RX/TX. Aktualny kod przed tym checkpointem: `main@9ff4d8ba88d79f3e33dbe4450b75b8392826cf62`. Następny czat ma zawsze ponownie odczytać aktualny HEAD po synchronizacji dokumentacji.

## Zmiany

- `ConversationSkillLibrary.kt`: `SkillMetadata(name, description, capability)`, enum `SkillCapability` (GENERAL, MESSAGE_CAPTURE, CLARIFICATION, CLOSURE), metadane zapisane razem z wersją instrukcji (`SkillRevision`). `metadataAt()` dostarcza domyślne wartości dla historycznych zaszyfrowanych rekordów schema=1 bez migracji uprawnień. `reviseMetadata()` i `reviseSkill(..., metadata)` tworzą nową wersję, nie zmieniając starej; `bindVersion` pozwala na rollback i wyłączenie.
- `ConversationSkillRouter` i `ConversationSkillTurnRouter` wybierają Skills według *przypiętej capability*, nie twardego ID. `GENERAL` i `CLOSURE` pozostają stabilne; MESSAGE_CAPTURE i CLARIFICATION reagują na zmianę intencji. Dwa różne Skills o tej samej capability mogą być aktywowane równocześnie; nie ma sztucznego limitu 1. Snapshot nie nadaje uprawnień.
- `ConversationSkillsActivity` pokazuje opis i capability oraz edytor nazwy, opisu, capability i instrukcji w **jednej wersjonowanej rewizji**. `ConversationSkillLabActivity` pokazuje tylko ID selekcji, zmiany w turach i czasy, bez transkryptu w Git lub standardowym logcat. `AndroidSkillLabSessionFactory` podaje odizolowany obserwator `onTurnRouting`; callback nie może zatrzymać generowania.
- `LabToolProposalPolicy` zwraca `NONE/PROPOSED_ONLY/REJECTED_UNAVAILABLE` dla typowanego, już zdekodowanego `ConversationReply`; `executed=false` zawsze, brak efektywnego portu. `SkillLabRunner` ocenia `END_CALL` jako propozycję i **nigdy** nie wywołuje rzeczywistego `ConversationToolExecutor` ani Telecom. Niewłaściwy JSON lub nieznany tool odrzuca wcześniejszy kontrakt dekodera.
- Lokalizacje PL i EN uzupełnione, stara nieprawdziwa wskazówka „wybór tylko na starcie” zastąpiona prawdziwą obserwowalnością per-turn.

## Dowody testów — zachować RED i GREEN

| Zadanie / commit | Wynik | Wnioski |
| --- | --- | --- |
| `aicalls-g4p3-skills-metadata-trace-tools-mac-qa-20261109-v1` | RED | `:app:compileDebugUnitTestKotlin` FAIL przez nowy callback jako końcowy parametr funkcyjny. F-158 |
| `main@9f4bb891d0ff8a01ae3225815e9269b2d4925e02` | FIX | `instructionFor` znów ostatnią lambdą konstruktora, kompatybilność wywołań przywrócona |
| `aicalls-g4p3-skills-metadata-trace-tools-mac-qa-20261109-v2` | RED | AndroidTest i Kotlin compile PASS, celowane JVM **30/31**; błędny test oczekiwał jednego aktywowanego Skill, gdy dwa mają MESSAGE_CAPTURE. F-159 |
| `main@10c28620bae7ffebcc4d187e088a4f70a9db1c84` | FIX | Asercja testowa uwzględnia prawidłowe obie aktywacje; bez zmian logiki produkcyjnej |
| `aicalls-g4p3-skills-metadata-trace-tools-mac-qa-20261109-v3` | **DONE/0 PASS** | Cztery kroki exit=0: pinned clean, celowane JVM i AndroidTest build, full `verify_host.sh` (371 Python, lint/Android/audio) oraz anti-friction, postflight clean |
| `aicalls-g4p3-metadata-ui-native-trace-s22-20261109-v1` | **DONE/0 PASS** | Cztery kroki exit=0: pinned `main@9ff4d8ba88d79f3e33dbe4450b75b8392826cf62`, build 2 APK, S22 2/2 UI + 1/1 native, postflight clean |

**Dokładny fizyczny wynik S22**, serial `RFCT70L7E8J`, model `SM-S906B`: `G43_S22_CALL_IDLE_GATE=PASS`, `G43_INSTALLED_NO_WIPE` oba APK; `AndroidG43SkillMetadataAcceptanceTest` **2/2** (zaszyfrowana rewizja metadanych, odczyt, rollback, GUI widoczne; stary preset użytkownika nietknięty). `AndroidG4DynamicSkillsNativeE2ETest#runnerGemmaDynamicSkillsPreservesMemoryAndStops` **1/1**: `turns=4`, `skills_refresh=3`, `g43_route_events=4`, `memory_before_pivot=true`, `memory_after_pivot=true`, `stop=true`, `profile_preserved=true`, brak realnego połączenia; `G43_POST_DEVICE_CALL_IDLE=True`. Cały task **DONE/0**. Rozmowy wyłącznie syntetyczne, bez treści tekstowych w publicznym wyniku.

**F-158 oraz F-159: VERIFIED_SCOPED** w zakresie Mac full gate + S22 powiązany UI/E2B. Wcześniejsze błędy zachowane w `BLOCKERS.md`. Weryfikacja poprzedniego G4.2 i F-157 pozostaje ważna, bez powtarzania dawnych G0–G3.

## Ograniczenia i następny etap

**Nie nazywać G5/G6 zaliczonymi.** G4.3 weryfikuje Lab-only, off-call tekst i ograniczoną sekwencję 4 tur. Nie ma pełnych nagrań RX/TX, rzeczywistych rozmów, STT→LLM+Skills→TTS E2E, pełnego human quality review ani dowolnie długiej pamięci. W G5 nie wolno zakładać prawdziwego EOS→TX z samego AndroidTest. Propozycje narzędzi nie stanowią grantu ani wykonania.

**Pierwszy następny zakres G5.1**: preflight i kontrakt pełnej ścieżki STT→Gemma E2B+per-turn Skills→TTS **off-call**, istniejące `ConversationRuntime` i Lab, źródła syntetycznego audio, poprawne EOS/interrupt/STOP oraz izolowana diagnostyka opóźnień. Zidentyfikować istniejące fizyczne dowody i brakujące porty, bez klonowania runnera i bez powtarzania PASS. Następnie targeted JVM, pełny Mac QA i fizyczne S22 dla rzeczywistego audio. G5 human semantic/naturalness review osobno. G6 pozostaje osobnym zadaniem wymagającym jawnego grantu realnego Call. G7 UX.

Obowiązuje `AGENTS.md`; GitHub Connector + Mac Local Agent + ChatGPT Sandbox, bez Codex/Work/GitHub Actions nawet statusu. `main` bez nowych branchy/force-push, `[skip ci]`, nie wysyłać prywatnych nagrań/transkryptów do GitHub.