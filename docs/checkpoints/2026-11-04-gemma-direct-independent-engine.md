# Gemma Direct — niezależny silnik dialogowy (2026-11-04)

## Wynik sprintu

**DONE: source + pełne host QA + fizyczny test syntetyczny S22 bez połączenia.**
**OPEN: realna rozmowa Gemma Direct z konsultantem, słyszalność jej pełnego TTS, efekt CLIR.**

Wybór na ekranie `Kampanie i zadania AI Calls`:
- `Start authorized CLIR enable` — niezmieniony dotychczasowy Script/PhraseMatrix + Gemma action router; domyślny przy wejściu przez istniejący START;
- `Start authorized CLIR enable — Gemma Direct` — drugi, jawnie wybierany backend generujący własną wypowiedź, bez `ClirDeterministicDialogueBackend` i `GateCHybridDialogueBackendFactory` na ścieżce rozmowy.

**Wspólne, bez zmian uprawnień:** dokładny aplikacyjny grant, SIM/target/CLIR, Telecom Call ownership, STT, TTS, Samsung RX/TX, zatwierdzanie finalnej wypowiedzi, `CallExternalEffectValidator`/`CallCommitmentGate`, `FactDisclosurePolicy`, STOP i odwołanie grantu. Pojawienie się modelu nie jest zgodą na nowy skutek. Żadnych automatycznych połączeń wykonanych w sprincie.

## Kontrakt niezależnego silnika

- `CampaignDialogueMode` i `CampaignDialogueModeStore` — skrypt domyślny; tryb Direct wymaga jawnego wyboru, jest związany z tym samym `grantId` dla kontynuacji, nie poszerza zakresu. Unknown mode = fail-closed, przy braku/złym zapisie bezpieczny script.
- `GemmaDirectDialogueBackend` — nowy modelowo generowany tekst w polskim dialogu. Kontekst zadania jest jawnie ograniczony do bieżącego CLIR; żadnych prywatnych wartości IdentityVault w prompt. Trwałe natywne LiteRT-LM `Conversation` zachowuje historię jednej rozmowy; testowy prewarm ma odrębny owner i czyści sztuczną turę.
- Model proponuje JSON z `SPEAK` (własny tekst), `ASK_CLARIFY`, `DISCLOSE_PHONE` (wyłącznie poprzez app-owned `AuthorizedPhoneFactBackend`) albo `CONFIRM_EFFECT` (wyłącznie przez istniejący `controlAwareBackend` i jednorazowy app-owned permit). Nie wykonuje efektów ani nie czyta danych z własnej inicjatywy. Mylne JSON, obce instrukcje, nieautoryzowane liczby/wypowiedzi/obietnice przechodzą do neutralnego dopytania zamiast do TX.
- Diagnostyka: tryb rozmowy i dla każdej tury kod akcji, źródło oraz czas inferencji w prywatnym `timeline.jsonl`/raporcie bez raw promptów w logach. Dawny tryb i jego metryki zachowane.
- Limit modelu `MAX_CONTEXT_TOKENS=2048` pozostaje realną granicą modelu; brak gwarancji nieskończonego dialogu w jednym natywnym kontekście. Nie dodano limitu liczby tur produktu.

## Weryfikacja źródeł

- Funkcjonalne commity na `main`: `252cdaa9` (mode store), `d59e4999`/`e07ae9c4`/`27030d51`/`65171eba` (direct model backend, polityka, testowalny zegar), `8c5bd08f` (JSON schema), `a1ed4bbf` (niezależny prewarm), `2b293053`/`ad14d153`/`3fc0f612`/`bb4dab49` (wiring), `24482cc4`/`56011259` (JVM regressions), `a68f3add` (default mode), `24241183` (instrumentacja).
- Mac focused v1 pokazał prawdziwe RED: 5 JVM test failures z powodu bezpośredniego używania `SystemClock`. Naprawa przez iniekcję monotonicznego zegara.
- Mac Local Agent `aicalls-20261104-gemma-direct-focus-qa-v2`: DONE/0, źródło `56011259`, `:app:testDebugUnitTest` + APK PASS, 676 testów, anti-friction PASS.
- Pełny Mac Local Agent `aicalls-20261104-gemma-direct-full-host-qa-v1`: DONE/0, źródło `2424118318caaca03d6330129bddf4190715677c`, trzy fazy Gradle test/lint/build/debugAndroidTest PASS, 248 Python PASS, `host_quality_gate_green=true`, anti-friction PASS. ZERO działań na telefonie w pełnym host QA.

## S22, fizyczny natywny model, bez połączenia

Mac task `aicalls-20261104-gemma-direct-s22-offcall-two-turn-v1` DONE/0: exact `SM-S906B`, dwa `IDLE`, rola dialera zachowana, brak uruchomionej kampanii i zaplanowanej kontynuacji. Zbudowano i zainstalowano dwa APK z `24241183`. Instrumentacja `AndroidGemmaDirectOnDeviceTest#twoSyntheticGemmaDirectTurnsReturnStructuredSafeProposals`: **OK (1 test)**, dwie odpowiedzi z poprawnymi, zatwierdzonymi typowanymi propozycjami JSON w kodzie testu; nie drukowano tekstu odpowiedzi. `INSTRUMENT_RUNNER_EXIT=0`; po testach `IDLE=true`, grant i continuation fingerprints unchanged, rola dialera preserved. Żaden telefon, mikrofon, TTS, media/call ani skutek zewnętrzny nie został użyty.

Niezależny read-only `AiCallBridgePerf`, Mac `aicalls-20261104-gemma-direct-s22-perf-metadata-v1` DONE/0:
- Tura 1: GPU, `engine_ready_ms=6198`, `conversation_create_ms=3097`, `generate_ms=6525`, `conversation_reused=false`, `conversation_turn=1`, 145 znaków odpowiedzi JSON, status `complete`;
- Tura 2: GPU, `engine_ready_ms=0`, `conversation_create_ms=0`, `generate_ms=1598`, `conversation_reused=true`, `conversation_turn=2`, 70 znaków, status `complete`.

**Nie twierdzić, że 1,6 s to rzeczywisty EOS→TX ani dowód jakości konwersacji z konsultantem.** W teście nie było RX/STT/TTS, a full/direct CLIR pozostają real-call acoustic/effect PENDING. Zbiór prywatnych nagrań wcześniejszych rozmów nie został ruszony.
