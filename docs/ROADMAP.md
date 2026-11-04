# AI Calls — roadmap: konfigurowalne wejście / model / wyjście (2026-11-04)

**Końcowy host-only gate (2026-11-04):** `aicalls-20261104-hostonly-audio-qualitygate-final-postfix-v4` pinned `2c2dac7` **DONE/0 PASS** (focused Kotlin PCM, pełny Gradle/lint/build, 10/10 mutacje, 258 Python, anti-friction). F-054/F-055 VERIFIED Mac; realna wersja APK S22 `0d0fdfe` niezmieniona. Następny fizyczny etap: polski syntetyczny ASR, Piper instalacja/głos PL, porównanie RAM/thermal, bez niesprawdzonego live. [Checkpoint](checkpoints/2026-11-04-audio-models-hostonly-quality-gates-checkpoint.md).

**HISTORYCZNY RED poprzedzający finalny PASS:** v3 miał 703 Kotlin JVM testów i 1 FAIL wyłącznie nowego fixture `0x7f7f`/expected `0x7fff` (F-055), bez błędu produkcyjnego. Fix `2c2dac7`; ostateczny Mac `aicalls-20261104-hostonly-audio-qualitygate-final-postfix-v4` **QA_PENDING** do terminalnego receipt. Wcześniejszy v2 pełny Mac PASS nie obejmował nowego testu. Bez telefonu.

**Sprint bez telefonu / bramka jakości:** 10 testów mutacyjnych, nowa obowiązkowa host QA audio i dodatkowe Kotlin PCM chunk/WAV/macierze profili. Mac v2 PASS, v3 final po nowych testach QA_PENDING; żadnych zmian produkcyjnych względem APK S22 `0d0fdfe`. [Host-only checkpoint](checkpoints/2026-11-04-audio-models-hostonly-quality-gates-checkpoint.md).

**Stan najnowszy (S22, 2026-11-04):** Fizyczne M2 Parakeet (natywny model + syntetyczny PCM) i M4 Gemma Audio Direct (natywne AudioBytes syntetyczne) **PASS off-call**; poprawiony APK `0d0fdfe` STORE 6/6, MENU i cztery syntetyczne tury Gemma **PASS**. M3 Piper system package na S22 **ABSENT**, więc native TTS OPEN. WER polskiej mowy, równoczesny Parakeet+E2B, thermal/perf i prawdziwa rozmowa OPEN. [Checkpoint](checkpoints/2026-11-04-audio-engines-m2-m4-sprint-checkpoint.md).

**Priorytet właściciela:** M1 zamknięty Mac/S22 off-call; M2 Parakeet i M3 Piper mają implementację oraz Mac QA PASS, M4 eksperymentalny source czeka na finalny Mac QA i natywny S22. Żadna nowa konfiguracja M2–M4 nie ma dowodu rozmowy. Początkowo należało **kodować** menu i wymienne silniki audio z planu [Edge Gallery](checkpoints/2026-11-04-edge-gallery-audio-engine-selection-design.md), nie wykonywać kolejnego ogólnego audytu. Szczegółowa, wiążąca lista kroków znajduje się w [IMPLEMENTATION_HANDOFF](checkpoints/2026-11-04-edge-gallery-audio-implementation-handoff.md), a [HANDOFF_NEXT_CHAT](HANDOFF_NEXT_CHAT.md) wskazuje punkt startu.

## Potwierdzony baseline — nie rozmontowywać

S22+ (SM-S906B), `Gemma 4 E2B RAW` z dynamicznym `TaskBrief` i 4 prompt-skills, Android STT + offline Android TTS, prywatny chat STT/Gemma/TX na 3 ekranach. Dwa realne cykle: **8/8** i **7/7** STT/model/`WRITE_COMPLETE`, bez `DIRECT_REJECTED`. Ostatni fizycznie zainstalowany APK z kodu `268be1f8f13502c7202822438b501706e0fcb0e1`; źródła dla pierwotnego RAW `0d44b7d5a7e01daa118da10c2e04c458086dced9`. `WRITE_COMPLETE` to lokalne TX, nie niezależne potwierdzenie działania CLIR. Zawsze najpierw odczytaj aktualny HEAD.

**Korekta F-049 — zamknięte, NIE jest defektem AI Calls.** Dwuczęściowe powitanie było zachowaniem infolinii. **Nie dodawać** nowego wykrywania końca powitania, zmian VAD, dodatkowych pauz, opóźnienia ACK/TX ani osobnych sprintów naprawczych z tego powodu. Historyczny wpis zostaje w `BLOCKERS` jako wycofany.

## Sprint M1 — PASS (Mac QA + S22 off-call), live acceptance OPEN

Commitowane zmiany `413676be` → `bc019ce`: menu `AudioEngineSettingsActivity` z osobnym wejściem / modelem / wyjściem, profil i prywatny store, katalog z wyłączonymi niezintegrowanymi modelami, test offline, wybór głosu, Android STT/TTS ports i dokładny snapshot profilu prewarm/call. Bez zmian Samsung/Shizuku, STOP, VAD, ACK, prompt-skills i autoryzacji. Pierwszy Mac RED test zakończył się oczekiwaną porażką; po przywróceniu kodu Gradle JVM unit PASS, full gate FAIL na lint `MissingPermission` w lokalnym podglądzie TTS. F-050 naprawione w źródłach i pełny Mac QA v2/v3 **PASS**. Dodano wymienny `DialogueEngineProvider` (E2B, bez cichego fallbacku na E4B) w `990a8dd8`; jego pierwszy compile QA v4 **FAIL** (F-051, utracony typ interfejsu resetującego kontekst), poprawka w `a616795e`. Końcowe Mac v5 **DONE/0 PASS** i S22 off-call v2 **DONE/0 PASS** dla `a616795e`: oba APK zainstalowane, testy profilu i ekranu PASS oraz 4 syntetyczne tury Gemma RAW PASS; S22 IDLE, grant i skills niezmienione. Poprzedni S22 v1 przerwał bez instalacji, gdy QA v4 był FAIL. Test rzeczywistego połączenia w nowym wydaniu nadal OPEN.

### Zakres M1 i kryteria końcowe

- Dodać odrębne, przejrzyste **Ustawienia silników audio AI** (Wejście / Model dialogu / Wyjście) z bieżącą konfiguracją, kompatybilnością `pl-PL`, stanem instalacji modelu, lokalizacją/wersją, rozmiarem, testem offline, zdolnością urządzenia i pojedynczym `Zapisz`. Nie mieszać z menu prompt-skills i grantem.
- **Wejście:** Android STT (aktywny), Parakeet 0.6B v3 PL (pobierz/eksperymentalny), Gemma 4 Audio Direct (eksperymentalny). **Model:** Gemma 4 E2B (aktywny), E4B (po sprawdzeniu wersji/zasobów), w przyszłości inne zgodne modele. **Wyjście:** Android TTS `pl-PL` (aktywny), Piper `pl_PL` (do zaimplementowania); Kokoro 82M i PocketTTS 100M mogą być pokazane w katalogu, ale oznaczone `Brak PL` i **nieaktywne** dla obecnej kampanii. Gemma E2B nie ma udowodnionej generacji PCM jako TTS.
- Dodać typowany `AudioEngineProfile` + rejestr `capabilities/availability/languages/backends`, oddzielne `SpeechInputProvider`, `SpeechOutputProvider`, `DialogEngineProvider` (oraz eksperymentalny `AudioDirectAdapter`). Nie dodawać fałszywie działających elementów UI. Profile nie przyznają dodatkowych uprawnień i nie zmieniają przyznanego celu/odbiorcy.
- First-pass musi być **funkcjonalnie zgodny z istniejącym Android STT → Gemma RAW → Android TTS**: identyczny call authority, STOP, prewarm, VAD, PCM RX/TX i live chat. Konfiguracja zastygła przed prewarm/Call, nie przełączać modeli w środku rozmowy.
- RED→GREEN focused unit, pełny Mac QA, exact S22 off-call, bez samowolnego połączenia. Oczekiwany wynik: działające menu i rzeczywiście wybieralne gotowe domyślne silniki oraz jasno opisane niegotowe opcje.

## Sprint M2 — Parakeet PL ASR (SOURCE + MAC + S22 syntetyczne native PASS; polski WER OPEN)

- Zintegrować oficjalne MediaPipe AudioTranscriber + `parakeet-tdt-0.6b-v3`; licencja, deklaracja rozmiaru i wymagania wersji w rejestrze.
- PCM 16 kHz z istniejącego RX, partial/final z timestamps, przycięcie pamięci i kolejka nieblokująca RX, prawdziwa diagnostyka offline i porównanie na tych samych prywatnych WAV. Utrzymać istniejące zamykanie tury i UX.
- Pomiar kosztu RAM/GPU, nagrzewania i latencji na S22 z Gemma 4 E2B, bez założenia że można jednocześnie odpalić oba GPU modele.

## Sprint M3 — wybieralne polskie TTS (SOURCE + MAC PASS; Piper APK S22 ABSENT)

- `Android TTS pl-PL` nadal bezregresyjny.
- Piper `pl_PL` dopiero po weryfikacji Android runtime/modelu/licencji/głosu; normalizacja PCM do **16 kHz mono signed PCM16**, nie dotykać zamrożonego `AuthorizedPcmUplinkWriter`.
- Kokoro/PocketTTS tylko zgodne języki (w obecnej wersji **bez polskiego**), niepodmieniane milcząco. Test liczb, CLIR, skrótów, szybkości pierwszego dźwięku i jakości.

## Sprint M4 — Gemma 4 Audio Direct (eksperymentalne SOURCE + MAC + S22 syntetyczne native PASS; dialog OPEN)

- Osobny dowód kompatybilności LiteRT-LM/modelu (obecnie `audioBackend = null` i tylko `generate(userText)`), wejście PCM 16 kHz do multimodalnego `Conversation`, zwrot tekstu dla TTS. Segment 30s jako granica możliwości modelu do weryfikacji w SDK; **nie deklarować pełnego strumieniowego speech-to-speech**.
- Osobny perf/thermal test na S22, fallback wyłącznie jawny, bez utraty dziennika rozmowy, bez cichego wyłączenia STT w panelu.

## Niezależne pozostałe zadania

- **F-048**: typowane dozwolone fakty `FactRequest → FactDisclosurePolicy → IdentityVault`, brak plaintext w prompcie; to odrębny moduł rozwijany po/obok wyboru modeli, nie blokuje budowy samego menu.
- **F-047**: po rozłączeniu możliwy `RX_INTERRUPTED` bez końcowego STT; marker istnieje, opcjonalne odzyskanie tylko do prywatnej diagnostyki, bez TX.
- **F-045**: pełna semantyczna walidacja swobodnej mowy RAW i niedozwolonych commitments nadal otwarta.
- **Zewnętrzny efekt CLIR:** wymaga osobnego dowodu; lokalny poprawny TX nie wystarczy.

**Wykonanie:** GitHub Connector + Mac Local Agent, `main`, commity `[skip ci]`, bez Codex, Work, GitHub Actions, nowych gałęzi, force-push i nieautoryzowanych połączeń. `BLOCKERS.md` to jedyny rejestr statusów; checkpointy to dowody i dokumenty wdrożeniowe.
