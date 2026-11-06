# AI Calls — etap 2: typowane wyniki, NO_CHANGE (2026-11-06)

## Preflight i mapa zależności

- Zweryfikowany main = origin/main = `06fdd75c7db8949aa64e5a5fb17f124853f7403b`, czysty Git; Mac `macbook-michal` / instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`.
- Read-only Mac task `aicalls-20261106-mod-stage2-preflight-v1`: DONE/exit 0. Wyszukiwanie wszystkich Kotlin/Java source + testów potwierdziło 9 referencji `ConversationOutcome`, 9 `ConversationOutcomeBoundary`, 3 `ConversationRunResult` i 7 `ConversationTermination`. Z tego produkcyjne `ConversationOutcome` i `ConversationOutcomeBoundary` po 5 plików, `ConversationRunResult` 3.
- `ConversationOutcome`: `ConversationScenario.kt`, `AndroidConversationRuntime.kt`, `ClirConversationScenario.kt`, `SupervisedConversationScenario.kt`, `ToolCallingConversationSession.kt`.
- `ConversationOutcomeBoundary`: te same pięć plików produkcyjnych; `TRANSCRIPT` i `PLAYBACK_COMPLETE` definiują miejsce odbioru wyniku narzędzia po zatwierdzonym odtworzeniu.
- `ConversationRunResult`: `ConversationScenario.kt`, `AndroidConversationRuntime.kt`, `AndroidIncomingAssistant.kt`; pola obejmują raport diagnostyczny, oczekiwanie na dowód narzędzia i token lokalnego czatu.
- `ConversationTermination`: typ `Completed/Stopped(reason)/Failed(code)` i powody STOP/CALL_ENDED/TAKEOVER/REVOKED/OWNERSHIP_CHANGED już są w JVM `conversation-core`. Konsumenci pozostają po stronie aplikacyjnego sterowania.

## Decyzja: NO_CHANGE

Nie przenoszono nowego kontraktu. `ConversationOutcome` jest czysty składniowo, ale jego wszyscy obecni konsumenci są w `app`; przeniesienie do `conversation-core` zmieniłoby tylko package/public API i importy bez wyeliminowania zależności ani przeniesienia odpowiedzialności. `ConversationOutcomeBoundary` jest częścią cyklu transcript/playback i bezpośrednio ochrania kolejność pełnego TX → end_call; bez przeniesienia implementacji runtime do core nie zyska nowego konsumenta. `ConversationRunResult` zawiera dane Android runtime / prywatnej diagnostyki / widoku live, więc nie jest neutralnym wynikiem domenowym. `ConversationTermination` już ma właściwą granicę JVM. Nie dokładano aliasów ani wrapperów.

**Zakres zmian produkcyjnych i testowych: zero**. Brak zmian algorytmu 30+15 s, INCOMING/OUTGOING, STOP, takeover, grantu/SIM, Samsung/Shizuku PCM/PFD, STT/TTS, pełnego TX lub end_call.

## Bramka jakości — nowy dokładny receipt

- Mac task `aicalls-20261106-mod-stage2-nochange-qa-v1`: **DONE / exit 0**, 152,426 s, na bazowym HEAD.
- `:conversation-core:test` **PASS** i `:app:compileDebugAndroidTestKotlin` **PASS**.
- `:app:testDebugUnitTest` celowane: `ConversationToolPlaybackTest`, `IncomingIdlePlaybackTest`, `ConversationSessionRegistryTest`, `ConversationRxInterruptionCodeTest` — **PASS**.
- `bash scripts/verify_host.sh`: **PASS**, `host_quality_gate_green=true`, trzy zakończone sukcesem fazy Gradle (55 s, 41 s, 11 s), **266 testów Python PASS**.
- `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py`: **ANTI_FRICTION=PASS**; `git diff --check` i clean worktree **PASS**. Nie wykryto nowego naruszenia do dopisania w `BLOCKERS.md`.
- Prywatne logi wyłącznie na Mac: `~/Documents/AI-Calls/local-agent/2026-11-06-modular-stage2`; brak WAV, transkryptów i danych abonentów w GitHub.

## Status i granice akceptacji

**Etap 2: PASS / NO_CHANGE / zamknięty po host QA.** Brak wdrażania APK na S22 lub fizycznych połączeń. Używane APK S22 nadal poprzedza etap 1, więc host PASS nie jest nowym dowodem PROVEN_S22.

**QA_PENDING pozostaje oddzielnie**: szczegółowe zdarzenia ostatniego timeoutu 30+15 s, manual/TAKE OVER/powrót AI/STOP, głos w okresie 15 s grace. Zachowano starsze dowody fizycznego end_call i obserwację właściciela bez powtórek.

**Następny jeden krok, w osobnym czacie:** dopiero po nowym preflight rozpocząć etap 3, analizując jedną czystą regułę `AndroidConversationRuntime` bez dotykania toru mediów i ownership.
