# AI Calls — HANDOFF IMPLEMENTACYJNY: wybór wejścia STT / modelu dialogu / wyjścia TTS

**Data:** 2026-11-04. **Cel nowego czatu: kodować moduł od pierwszego kroku, nie powtarzać analizy.** Dokument jest wiążącą instrukcją implementacji przy zachowaniu `AGENTS.md` i bezpieczeństwa. Status przed otwarciem nowego czatu: **wyłącznie dokumentacja i projekt rozwiązania; nie napisano nowych providerów ani menu**. Nie ogłaszaj nowego APK, dopóki nie istnieje receipt z Mac QA i fizycznego S22.

## 0. Niepodlegająca reinterpretacji decyzja właściciela

1. **USUNĄĆ zadanie F-049.** Właściciel wyjaśnił, że dwuczęściowe powitanie jest cechą infolinii, **nie błędem AI Calls**. Zostało wycofane z aktywnych blokerów i tylko historycznie odnotowane jako fałszywy alarm. Nie naprawiaj „za szybkiego wejścia Gemmy”, nie zmieniaj `PcmEndOfUtteranceDetector.trailingSilenceMs` (1500 ms), nie dodawaj logiki `IVR_CONTINUING`, opóźnień TX, nowego VAD lub testów poprawiania powitania. Zmiany ASR mogą jedynie zachować dotychczasowe zachowanie i przetestować brak regresji.
2. **Główny nowy sprint to rzeczywisty wybór wejścia / modelu dialogu / wyjścia**, jak w Edge Gallery: Android STT, Parakeet PL, eksperymentalne audio bezpośrednio do Gemmy; Gemma 4 E2B/E4B; Android TTS PL, Piper PL i zgodne językowo inne TTS. Menu ma być zrozumiałe, wielowarstwowe, niewymieszane z prompt-skills lub grantami.
3. **Zachować działający tor S22**, bez przepisywania Samsung/Shizuku, Android Telecom, `AuthorizedPcmUplinkWriter`, `CampaignAuthorizationPolicy`, modelowego RAW i `STOP`. Rozwijaj za wymiennymi interfejsami.
4. **Kolejne niezależne zadania**: F-048 autoryzowany dostęp do faktów z IdentityVault, F-047 przerwany końcowy STT oraz F-045 kontrola semantyczna RAW. **Nie** uzależniać pojawienia się menu od ich rozwiązania i nie rozszerzać grantu przez wybór silnika.
5. W nowym czacie **przystąpić do implementacji kodu** i odnotować realne PASS/FAIL; nie kończyć na kolejnej propozycji UI.

## 1. Baseline do ochrony

- Repo `MichalMatu/ai-calls`, docelowy `main`; **zawsze odczytać świeży HEAD**, nie zakładać tego zapisanego tu. Gałęzie z ostatniej weryfikacji: `main` i historyczny `agent-control`; bez nowych branchy i force-push. Commity na `main` z `[skip ci]`.
- Pierwszy zweryfikowany RAW bez action JSON parsera: `0d44b7d5a7e01daa118da10c2e04c458086dced9`. Ostatni fizycznie potwierdzony APK zawierający dynamiczny CLIR TaskBrief, 4 prompt-skills i dolny chat: **source `268be1f8f13502c7202822438b501706e0fcb0e1`**. To punkt porównawczy, nie aktualny HEAD.
- Host QA `aicalls-20261104-live-chat-dock-host-qa-v1` DONE/0 (full Gradle build/tests/lint, 248 Python, anti-friction). Exact S22 `aicalls-20261104-live-chat-dock-s22-offcall-install-v1` DONE/0 (APK + AndroidTest, 4 native synthetic Gemma turns, `IDLE`, brak zmiany prefów/grantu). Dwie realne rozmowy RAW z 2026-11-04: 06:30 **8/8** oraz 06:35 **7/7** STT/Gemma/`WRITE_COMPLETE`, zero `DIRECT_REJECTED`; użytkownik potwierdził prawidłowy chat. Nie oznacza to, że CLIR włączono lub że daleki odbiorca słyszał wszystko.
- Bieżący STT: `app/src/main/kotlin/pl/michalmatu/aicallbridge/localspeech/OnDeviceSpeechInput.kt`, Android `SpeechRecognizer.createOnDeviceSpeechRecognizer` i `RecognizerIntent.EXTRA_AUDIO_SOURCE`, `pl-PL`, 16 kHz mono PCM16.
- Bieżący TTS: `app/src/main/kotlin/pl/michalmatu/aicallbridge/localspeech/LocalTtsSpeechOutput.kt`, Android `TextToSpeech` wyłącznie lokalny głos `pl-PL`, wynik przeliczony na 16k mono PCM16.
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/localspeech/LocalSpeechTextPipeline.kt` **konkretnie tworzy** `OnDeviceSpeechInput`, `LocalTtsSpeechOutput` i `TextCallTurnController` oraz ma `start / writeInputPcm / finishInput / cancel / close`. To pierwsza, naturalna granica wstrzyknięcia providerów.
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt` tworzy pipeline, obsługuje `PcmEndOfUtteranceDetector`, `ChatRelayDownlinkPump`, `LiveCallChatFeed`, `CallTextAgentOutputApprovalPolicy`, ACK/TTS/TX; **nie przenosić polityki do modelu**.
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/campaign/AppOwnedCampaignService.kt`, `CampaignGemmaDirectPreflight.kt`, `GemmaDirectTaskBriefBuilder.kt`, `AppOwnedClirCampaignExecutor.kt` — kontekst grantu i prewarm; profil providerów należy ustalić na starcie tej samej autoryzowanej kampanii i porównać na granicy przed wybieraniem numeru.
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt` — `com.google.ai.edge.litertlm:litertlm-android:0.17.1` w `app/build.gradle.kts`, `Gemma4Runtime.generate(userText)`, `EngineConfig.audioBackend = null`; obecny Gemma **nie przyjmuje audio**. `Gemma4ModelAcquisitionCatalog`, `AndroidGemma4ModelDownloader`, `Gemma4ModelInstaller` już tworzą precedens na SHA/rozmiar/licencję/atomową aktywację modelu.
- Istniejący chat: `LiveCallChatFeed.kt`, `LiveCallChatPanel.kt`, `CampaignToolActivity.kt`, `AndroidCallControlsActivity.kt`. Należy dodać etykiety źródeł oraz metryki wejścia/wyjścia, ale **nie logować prywatnego tekstu poza procesem**.

## 2. Źródła Edge Gallery i ograniczenia

Właściciel przekazał 5 screenshotów Edge Gallery (2026-11-04 06:26–06:28); wcześniejszy rekonesans w [projekcie wyboru silników](2026-11-04-edge-gallery-audio-engine-selection-design.md) zidentyfikował:
- ML Kit ASR (Basic), Parakeet TDT 0.6B v3 (~615 MB), Kokoro 82M (~295 MB), PocketTTS 100M (~760 MB), Gemma 4 E2B (~2.6 GB) i E4B (~3.7 GB).
- **Parakeet v3 obsługuje język polski**, Kokoro/PocketTTS w omawianych paczkach **nie**. Piper `pl_PL` jest kandydatem dla polskiego TX, ale jego Android runtime, wybrany głos i licencja wymagają osobnego sprawdzenia. Dane te są stanem badań, nie gwarancją zgodności na S22.
- Gemma 4 E2B/E4B potrafi rozumieć wejście audio, ale **nie dostarcza obecnie udowodnionego w AI Calls bezpośredniego strumienia mowy wyjściowej**; wymaga zewnętrznego TTS. Modelem wejścia mogą być *odcinki* PCM, a nie gwarantowany stale otwarty duplex. Limit ~30 s/segment weryfikować w konkretnej wersji SDK/modelu.
- Sprawdzić aktualne biblioteki i sposób przekazywania PCM *przed* dołożeniem zależności: https://developers.google.com/edge/mediapipe/solutions/audio/audio_transcriber/android, https://developers.google.com/edge/mediapipe/solutions/audio/text_to_speech/index, https://gemma.ai/models/gemma-4/model-card/ i https://github.com/google-ai-edge/gallery. Bez pobierania modeli z niezweryfikowanych URL lub przenoszenia loginów/kluczy do repo.

## 3. Docelowy UX — osobny ekran „Ustawienia silników audio AI”

Odnośnik z `CAMPAIGN TOOLS`, docelowo także z głównego `AI Calls`. **Jeden spójny ekran** z trzema, klarownie rozdzielonymi sekcjami (nie mieszać z prompt-skills, faktami, autoryzacją):
1. **WEJŚCIE (RX / rozpoznawanie):** `Android STT — pl-PL, gotowy, domyślny`; `Parakeet TDT 0.6B v3 — pl-PL, do pobrania / testów`; `Gemma 4 Audio Direct — eksperymentalne, audio→tekst odpowiedzi`. Wyświetla `Język`, `Streaming / segmenty`, `Dokładność` (tylko po pomiarze), `Czas`, `Zajęta pamięć`.
2. **MODEL ROZMOWY:** `Gemma 4 E2B — gotowy`, `Gemma 4 E4B — do pobrania/kompatybilności`; wybrany `TaskBrief` i skills nadal mają własne, osobne sterowanie. Przy wejściu `Gemma Audio Direct` kontrola kompatybilności musi jawnie ustalić, czy wybrany model rzeczywiście może przyjąć audio.
3. **WYJŚCIE (TX / synteza):** `Android TTS offline pl-PL — gotowy`, `Piper pl-PL — do pobrania/implementacji`, `Kokoro 82M — brak pl-PL`, `PocketTTS 100M — brak pl-PL`, `Gemma Audio output — niedostępne dla obecnego modelu`. Dla kompatybilnych głosów: `Głos`, `Podgląd odsłuchu (wyłącznie poza połączeniem)`, `Język`.
4. Dół ekranu: **Aktywna konfiguracja** w jednej linii, `Zapisz konfigurację`, `Przywróć stabilny wariant` i niezależne `Sprawdź offline` bez wykonywania rozmowy. Status `Gotowy` wyłącznie po realnym probe; `Pobierz` tylko z zaimplementowaną, bezpieczną pobierarką; `Eksperymentalny/Brak PL/Niewspierany` z jasnym powodem. Nie pokazywać nieaktywnych opcji jako już działających.
5. **Nie przełączać silników w trakcie rozmowy.** Próba zmiany ma dać informację „zostanie zastosowane przy następnej autoryzowanej rozmowie”, nie zmieniać live sesji. Bez automatycznych, zbędnych dialogów potwierdzających lub cichej zmiany wbrew wyborowi użytkownika. Nie wolno stawiać wyboru modelu jako dodatkowego wymagania do uruchomienia starego stabilnego flow.

## 4. Precyzyjny kontrakt kodu (projekt, nie twierdzenie o istniejącym API)

Proponowane typy Kotlin w nowym logicznym pakiecie np. `audioengines/`; ostateczne nazwy wybrać według konwencji repo, unikać duplikatów:
```kotlin
enum class SpeechInputId { ANDROID_STT, PARAKEET_PL, GEMMA_AUDIO_DIRECT }
enum class DialogueModelId { GEMMA_4_E2B, GEMMA_4_E4B }
enum class SpeechOutputId { ANDROID_TTS_PL, PIPER_PL, KOKORO, POCKET_TTS }
data class AudioEngineProfile(
    val input: SpeechInputId = SpeechInputId.ANDROID_STT,
    val dialogue: DialogueModelId = DialogueModelId.GEMMA_4_E2B,
    val output: SpeechOutputId = SpeechOutputId.ANDROID_TTS_PL,
    val languageTag: String = "pl-PL",
    val voiceId: String? = null,
)
data class ModelCapability(
    val available: Boolean, // actual runtime / installed model readiness, never UI-only
    val languages: Set<String>,
    val supportsAudioInput: Boolean,
    val producesText: Boolean,
    val producesPcm: Boolean,
    val supportsStreaming: Boolean,
    val reasonCode: String? = null,
)
```
- `AudioEngineProfileStore` — tylko preferencja, **nie** grant; nie zawiera telefonu, tokenów, danych konta. Nowa konfiguracja nie powinna nadpisywać osobno zapisanych prompt-skills. Migracja/brak zapisanej wartości = **ANDROID_STT + GEMMA_4_E2B + ANDROID_TTS_PL, pl-PL**.
- `AudioEngineRegistry` + `AudioEngineReadiness` — faktyczna instalacja/version/hash, języki, model→backend, kompatybilność kombinacji i pamięć S22. Waliduj **kompozycję** (input + model + output), nie każdy element izolowanie. Jeżeli model niedostępny, nie potwierdzaj zapisu niemożliwego profilu jako „aktywnego”.
- `SpeechInputProvider` powinien posiadać semantykę obecnego `OnDeviceSpeechInput` (start/ready, `writePcm16Mono16k`, `finishInput`, `cancel/close`, `onFinalTranscript`) i ewentualne opcjonalne `onPartialTranscript` + znaczniki czasu. Aktualny Android STT musi być opakowany adapterem **bez zmiany działania**.
- `SpeechOutputProvider` powinien mieć `synthesize(text, onPcm16Mono16k)`, `cancel/close` oraz jawny `locale/voice`. Aktualny Android TTS ma przejść adapter bez zmiany wynikowego PCM.
- `DialogEngineProvider` dla tekstowego `TextCallAgentBackend` powinien reużywać dotychczasową rozmowę i prewarm. `GEMMA_AUDIO_DIRECT` jest **alternatywną strategią wejścia do multimodalnego dialogu** (audio segment → tekst odpowiedzi) i nie może przypadkiem używać `TextCallAgentBackend.generate(String)` jakby dostawał audio. Zaproponuj osobny interfejs wejścia audio na etapie M4 i sprawdź API LiteRT.
- Rozwiązanie `LocalSpeechTextPipeline`: wstrzyknij fabryki dwóch providerów lub skonstruowane porty, **zachowując zgodny konstruktor z domyślnymi obecnymi silnikami**. `TextCallTurnController`, application-owned output approval oraz PCM uplink pozostają po tej samej stronie bariery. Nie przepisuj `ChatRelayLiveCallProbe` w celu dodania UI.
- `CampaignGemmaDirectPreflight` musi wiązać `AudioEngineProfile` + *dokładny* `TaskBrief` + model artifact; jeżeli w trakcie prewarm wyboru dokonano ponownie, nie przenoś starego modelu do nowej sesji. W `AppOwnedCampaignService` utrwal jedną konfigurację dla danego rozpoczęcia, przetestuj STOP w każdej fazie.
- Wszystkie silniki potrzebują numerów generacji/cancellation i po zakończeniu own-call natychmiast kończą pracę; zakaz wysyłki PCM po stop. Dla modeli 24-kHz float32 na wejściu `AuthorizedPcmUplinkWriter` wymagane jest testowane przeliczenie do 16-kHz PCM16.
- `LiveCallChatFeed`: dodaj neutralne nazwy providera, partial/final i czasy pipeline, **bez zapisywania dodatkowego prywatnego tekstu na stałe**.

## 5. Zaplanowane zmiany krok po kroku — nie zatrzymać się na samym katalogu

**M1 (pierwszy rzeczywisty sprint, do zamknięcia w nowym czacie):**
A. Preflight: aktualny HEAD + repo policy + status exact Mac Local Agent i telefonu (jeśli dostępne), brak aktywnego Call/retry przed ewentualną instalacją, kod dzisiejszych providerów i QA. NIE powtarzać starych testów tylko po to, by zgromadzić te same dowody.
B. Testy RED: default profile/migracja, bez uprawnień, nieznane ID, E4B niedostępne, język `pl-PL` z Kokoro/Pocket, profil audio-direct przy niekompatybilnym backendzie, ustawienia zmienione podczas active Call, brak cichego fallbacku, prewarm mismatch, STOP podczas STT/LLM/TTS.
C. Dodać typy, rejestr, profile store i engine readiness; dostarczyć **realnie działające menu**, a niedostępne provider wybory jawnie wyłączyć z poprawnym statusem, nie zgłaszać fikcyjnego PASS. Do `AppOwnedCampaignService` dodać walidację/snapshot profilu przed prewarm/call.
D. Wstrzyknąć istniejące Android STT i Android TTS do `LocalSpeechTextPipeline` przez provider ports; domyślnie zachowanie **bajtowo/behawioralnie zgodne** (tam gdzie deterministyczne). Zachować `SCRIPT_GEMMA` i `GEMMA_DIRECT` jako warianty dialogowe niezależnie od wyboru ASR/TTS.
E. Testy GREEN unit, `scripts/verify_host.sh` + `python3 scripts/aicall_tools/diagnostics/audit_product_friction.py` przez Mac Local Agent, build/lint. Następnie, jeżeli telefon faktycznie IDLE, brak kampanii/retry, zrobienie off-call testu 4 natywnych tur i bezpiecznej instalacji. Żadnego nowego połączenia w ramach testów M1.
F. Zaktualizuj README/ARCHITECTURE/ROADMAP/BLOCKERS/ten handoff z dokładnymi commitami i terminalnymi wynikami, usuń szum z ukończonych etapów; przejdź do M2, jeżeli warunki są spełnione.

**M2:** prawdziwa integracja Parakeet PL `AudioTranscriber` — model downloader/importer, pinned revision + SHA/size + license; realny `SpeechInputProvider` + timestamps i final, bounded streaming PCM bez blokady RX; WER/RTF/memory comparison na tej samej prywatnej próbce lokalnie; szczególnie utrzymanie sposobu segmentowania istniejących pauz; nie modyfikować `PcmEndOfUtteranceDetector` tylko z powodu F-049.

**M3:** prawdziwa integracja Piper PL i wybór głosu na podstawie aktywnego modelu. Zapewnić poprawne `pl-PL`, audio 16k PCM16, odsłuch lokalny i testy `TTS→TX` bez połączenia. Kokoro/PocketTTS nadal wyłączone dla PL; jeśli rozszerzysz na inne języki, najpierw udowodnij support i legalność paczki.

**M4:** eksploracja on-device Gemma Audio Direct na S22 przez zgodne multimodalne API (segmenty 16k PCM, maksymalny rozmiar według SDK). Udowodnij, że model rozpoznaje/rozumie wejście audio z multi-turn context **bez** osobnego ASR oraz daje tekst gotowy do zatwierdzenia i wybranego TTS. Dopiero potem aktywuj wybór Audio Direct; nie obiecuj speech-to-speech.

Każdy etap ma mieć osobny checkpoint i status *implemented vs benchmarked vs shipped*, nie jedną deklarację „gotowe”.

## 6. Kryteria akceptacji

- **Nie utracić baseline:** Android STT → Gemma 4 E2B RAW → Android TTS musi zachować tekst, tempo, kontekst, grant, VAD i 4-turn off-call oraz działający prywatny live chat; także tryb SCRIPT_GEMMA.
- **UI prawdziwe:** wybór aktywnego modelu i wybór gotowych silników STT/TTS widoczny i utrwalany, dostępność/licencja/język/size/readiness z rzeczywistych źródeł; niedostępne opcje nie uruchamiają Call i są opatrzone powodem.
- **Model lifecycle:** instalacja atomowa i weryfikowana, brak cichych aktualizacji/modeli przy telefonowaniu; prewarm exact profile, brak reuse modelu z innego grantu/promptu/profilu; RAM i współbieżny GPU zmierzone na S22 przed deklaracją „gotowe”.
- **Bezpieczeństwo:** zapisywany profil nie zmienia `CampaignAuthorizationGrant` ani `IdentityVault`; brak nieautoryzowanego disclosure, brak PCM po STOP, zero zdalnych połączeń od Local Agenta i zero prywatnych WAV/tekstów w GitHub.
- **Mierzalność:** raport per wariant `STT final / model output / first PCM / TX completion / errors / allocations` i użyteczny live chat; wynik z instrumentacji **nie** jest dowodem zewnętrznego CLIR ani słyszalności operatora.
- **Brak niepożądanego długu:** F-049 pozostaje wycofane, F-048/F-047/F-045 nadal niezależne, bez nowych sztucznych limitów, ręcznych kliknięć lub niepotrzebnych gałęzi.

## 7. Narzędzia — bez zmiany reguł

Dozwolone: **GitHub Connector + Mac Local Agent**. Zakazane: **Codex, ChatGPT Work, GitHub Actions, zaglądanie do ich statusu, nowe branche, force-push, instalacja w aktywnej rozmowie i samowolne połączenia**. Aktualizować `main` commitami `[skip ci]`. Czytać `AGENTS.md`, `README.md`, `BLOCKERS.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/SECURITY_PRIVACY.md` i `docs/GENERIC_PHONE_TASK_AUTHORITY.md` przed kodowaniem.

Mac host: `target_host_id=macbook-michal`, `target_installation_id=cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Queue repo: `MichalMatu/local-agent`, branch `agent-control`, prefix `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/<unique>.json`, wynik `task-results/<same>.json`; host `cwd=/Users/michal`. **Najpierw zweryfikować** aktualny `MichalMatu/local-agent/AGENTS.md` i dokumenty hosta oraz faktyczne `DONE` — samo utworzenie pliku task nie dowodzi wykonania.

Fizyczny target: **Samsung S22+ SM-S906B, serial ADB RFCT70L7E8J**; przed ADB install 2× `mCallState=0`, brak aktywnej kampanii/retry, dialer role zachowana, fingerprint prefów przed/po. Mac może mieć tylko prywatny eksport `~/Documents/AI-Calls/private-exports/` i nie powinien przekazywać jego treści do GitHub.

### Tekst startowy do nowego czatu (kopiuj)

Kontynuuj `MichalMatu/ai-calls` z aktualnego `main`. Najpierw przeczytaj `AGENTS.md`, `docs/HANDOFF_NEXT_CHAT.md` oraz **`docs/checkpoints/2026-11-04-edge-gallery-audio-implementation-handoff.md`**. Wyłącznie GitHub Connector + Mac Local Agent, bez Codex/Work/GitHub Actions, nowych branchy/force-push; commity `main [skip ci]`. **Nie wracaj do F-049**: właściciel uznał dwuczęściowe powitanie biura za prawidłowe i wycofał zadanie poprawy końca tury. Zacznij kodować M1: **osobne menu Wejście STT / Model rozmowy / Wyjście TTS, typowany profil i readiness, adaptery Android STT/Gemma E2B/Android TTS z zachowaniem działającego RAW/STOP/PCM**. Pokazuj Parakeet PL, Gemma Audio Direct, E4B, Piper PL, Kokoro/Pocket w katalogu, ale **nie udawaj że działają** — zaimplementuj i przetestuj je etapami M2–M4, jeśli obecny sprint na to pozwoli. Najpierw RED→GREEN, pełne Mac QA, potem exact S22 off-call; żadnych samowolnych połączeń. Poświęć uwagę stabilnemu UX, nie powtarzaj już zamkniętych testów ani ogólnych audytów. F-048/F-047/F-045 pozostają osobnymi otwartymi zadaniami.
