# AI Calls — kompletny checkpoint kodu, dokumentacji, jakości i testów

**Data:** 2026-11-06. **Referencyjny HEAD kodu przed tym dokumentacyjnym commitem:** `1d260d8424e152b8e3d072842e7bf92405b80e6e` (`main=origin/main`, clean). Po publikacji tego pliku HEAD się zmienia; przy wznowieniu pobrać świeży. **Wynik:** HOST PASS / S22 OFFCALL PASS / live-call QA_PENDING.

## 1. Kod i architektura

Sześć modułów: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `privileged-helper`, `realtime-client`. Śledzone pliki Kotlin (nie przypadki testowe) na referencyjnym SHA:

| Moduł | Produkcyjne `src/main` | JVM `src/test` | `src/androidTest` |
|---|---:|---:|---:|
| app | 199 | 159 | 38 |
| conversation-core | 4 | 4 | 0 |
| conversation-tools | 1 | 1 | 0 |
| audio-bridge | 3 | 0 | 0 |
| privileged-helper | 0 | 0 | 0 |
| realtime-client | 17 | 15 | 0 |
| **Razem** | **224** | **179** | **38** |

Wartość 0 dla helpera nie oznacza braku kodu Java/AIDL. Modularizacja: etapy 0–1 DONE, etap 2 NO_CHANGE/PASS, etap 3 krok 1 DONE (`ConversationSessionRegistry` w `conversation-core`). Produkcyjne źródła: `7a121657` — kontrakt semantyczny propozycji `end_call`; `e8db8db765a75990f7c60da3145ddbcf4d6e17b5` — wersjonowane skillsy, 3 fabryczne presety, własne kopie, edycja, zaszyfrowany store, snapshot i wieloturowe Laboratorium off-call. Commit `1d260d8` dodaje instrumentacyjne testy trzech presetów i dokumentację — **bez zmiany produkcyjnego APK**. Nie ruszano zamrożonego Samsung/Shizuku RX/TX PCM/PFD, Android Call ownership, STOP ani autoryzacji skutków.

## 2. Quality gates na referencyjnym HEAD

Mac Local Agent: `macbook-michal` / instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Dokładny receipt: `aicalls-20261106-complete-checkpoint-preflight-fullgate-v1`, **DONE / exit 0**, clean worktree i `main=origin/main`.

| Gate | Wynik |
|---|---|
| `:app:compileDebugAndroidTestKotlin` | **PASS**, Gradle BUILD SUCCESSFUL, 70 tasków |
| JVM testy wszystkich modułów w `scripts/verify_host.sh` | **PASS** |
| Lint Android app / realtime / helper / audio | **PASS** |
| Build `app:assembleDebug` i `app:assembleDebugAndroidTest` | **PASS**, końcowe BUILD SUCCESSFUL, 127 tasków |
| Python unittest discovery | **266 testów, OK** |
| Audyt audio | **AUDIO_ENGINE_QUALITY_GATE=PASS** |
| `audit_product_friction.py` | **ANTI_FRICTION=PASS** (statyczny) |
| `git diff --check`, Git czysty i zgodny HEAD remote | **PASS** |
| 90 odnośników z 7 aktywnych dokumentów | **90 sprawdzonych, 0 broken** |

Logi zawierały `call_state=UNKNOWN` i `explicit_real_call_opt_in_required` w testach negatywnych; pełna bramka zakończyła się exit 0. **Host gate nie uruchamiał ADB ani połączeń.** Ponowne testy nie są potrzebne bez nowej przesłanki; ten commit jest tylko dokumentacją.

## 3. Fizyczny S22 i wyniki Gemmy

Samsung `SM-S906B`, Android API 36. Zainstalowane produkcyjne APK odpowiadające `e8db8db`, SHA-256 `e8422d5124328520d0f03be81f34937abc07808593708b966d9cf54196f63a4f`. Zachowano dane aplikacji; telefon był `IDLE`, bez kampanii i retry.

Receipts:
- `aicalls-20261106-gemma-skills-s22-physical-preflight-v1`: DONE/0, S22 IDLE, brak kampanii.
- `aicalls-20261106-gemma-skills-s22-matching-apk-native-offcall-v2`: DONE/0, APK aplikacji i testów zainstalowane, natywny F-091 off-call PASS, po teście IDLE.
- `aicalls-20261106-skills-matrix-test-source-v1`: DONE/0, kompilacja AndroidTest PASS.
- `aicalls-20261106-skills-matrix-s22-run-v1`: Android `OK (3 tests)`. Pierwszy host wrapper oznaczył FAIL, myląc `INSTRUMENTATION_CODE: -1` (`RESULT_OK`) z błędem.
- `aicalls-20261106-skills-matrix-recover-checkpoint-publish-v1`: odczytano pomiary z prywatnego cache bez ponowienia modelu; sam wrapper zatrzymał się na białej linii EOF testu.
- `aicalls-20261106-skills-matrix-whitespace-publish-v1`: DONE/0, korekta whitespace i commit `1d260d8`.

**Model:** `GEMMA_4_E2B`; identyczne 4 syntetyczne historie i 12 tur na preset, razem 36 tur. Każda historia ze świeżym kontekstem, kolejne tury ze wspólną historią.

| Preset | Poprawne END_CALL | Przedwczesne | Pominięte | Pytania (`?`) | Średnia generacja | Init |
|---|---:|---:|---:|---:|---:|---:|
| Naturalny | 12/12 | 0 | 0 | 3 | 7 029 ms | 8 ms |
| Formalny | 12/12 | 0 | 0 | 3 | 11 012 ms | 8 ms |
| Zbieranie wiadomości | 12/12 | 0 | 0 | 3 | 12 361 ms | 18 ms |

**Native 3/3 PASS:** izolowany szyfrowany store, zapis i ponowne otwarcie, wersja 2, wyłączenie i rollback skilla; uruchomienie ekranów Skills i Laboratorium bez zmiany aktywnego presetu; ten sam model dla trzech presetów. **Brak** pełnych interakcji dotykowych edytora i ręcznej oceny naturalności. Nie było połączeń, STT/TTS, wykonania narzędzia `END_CALL`, ani przenoszenia transkryptów/PII do repo. [Dokładny checkpoint matrycy](2026-11-06-gemma-skills-three-presets-s22-offcall.md).

## 4. Dokumentacja / rozbieżności do synchronizacji

Aktualne źródła prawdy: [AGENTS](../../AGENTS.md), [BLOCKERS](../../BLOCKERS.md), [architektura](../ARCHITECTURE.md), [Roadmapa](../ROADMAP.md), [Handoff](../HANDOFF_NEXT_CHAT.md), [kontrakt Skills](../SKILLS_AND_LAB.md). Starsze fragmenty `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/NEXT_CHAT_PROMPT.md` wciąż opisują stan sprzed instalacji `e8db8db` i część wcześniejszych TODO jako bieżące. Należy ujednolicić je z tym checkpointem, nie usuwać historycznych dowodów. `BLOCKERS.md` jest jedynym kanonicznym rejestrem defektów. Stan source PASS nie jest automatycznie real-call PASS.

## 5. Otwarte / QA_PENDING

- Ręczna ocena naturalności (1–5) na wspólnym korpusie i powtarzalność (jedna próbka nie wystarcza do wyboru jakościowego).
- Pełna fizyczna obsługa edytora: wybór, kopia, zapis, przywrócenie oraz zachowanie po restarcie.
- F-091: rzeczywiste pożegnanie -> pełny zaakceptowany TX -> rozłączenie wyłącznie własnego Call i poprawny cleanup.
- F-092: rzeczywisty polski język sekretarki bez wtórnego powitania.
- Manual/TAKE OVER/powrót AI/STOP, timeout 30+15 s, głos w grace.
- F-080 ciągły zapis całej sesji i player; F-044/F-045/F-048 zadania/skutki/fakty; F-047 końcowy RX; dalsze stany według `BLOCKERS.md`.

## 6. Bezpieczne przekazanie dalej

1. Sprawdzić świeży `main`, clean Git, host claims i nowe receipts.
2. Zsynchronizować historycznie nieaktualne odcinki dokumentacji w małym **docs-only** commicie; link/diff/friction gate, bez ponawiania modelu.
3. W osobnej sesji przeprowadzić ręczne QA UI i naturalności na S22 bez połączeń.
4. Osobny, konkretnie autoryzowany scenariusz rzeczywistego połączenia tylko po preflight `IDLE`, bez dublowania wykonanych prób.

**Tryb:** GitHub Connector + Mac Local Agent; bez Codex, Work, GitHub Actions, nowych gałęzi, force-push i nieuzasadnionych zmian telefonu.
