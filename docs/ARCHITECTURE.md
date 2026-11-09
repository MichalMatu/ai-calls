# AI Calls — architektura operacyjna

## G3 — wybór modelu i ograniczenia (2026-11-09)

`KEEP_E2B` **DECIDED**: w istniejącym `DialogueModelId` E2B pozostaje modelem głównym Call, Qwen3.5 2B wyłącznie Lab/CPU (bez promocji, bez zmian profilu). Mac+S22 96 tur technicznych, raport 48 par, Mac clean, 96 syntetycznych odpowiedzi poddanych lokalnej regułowej ocenie 150/150 E2B vs 141/150 Qwen. Różne GPU/CPU i brak ludzkiej oceny naturalności ograniczają wnioski. G4 przygotowuje per-turn Skills, lecz zmiana system instructions w żywym LiteRT wymaga osobnego potwierdzenia bezpieczeństwa/lifecycle przed włączeniem. [Finalny checkpoint](checkpoints/2026-11-09-g3-keep-e2b-final-decision.md).

## G3 — modelowy A/B na S22, granice izolacji (2026-11-09)

`DialogueModelId.QWEN_3_5_2B` jest przypiętym katalogowo **kandydatem laboratoryjnym**, a nie gotowym profilem Call. Zwykły `AudioEngineRegistry.incompatibility` go blokuje; przycisk zwykłego wyboru również. `LiteRtGemma4Runtime` wykorzystuje tę samą fabrykę i output approval, ale polityka Qwen3.5 **CPU_ONLY** (Mali/Exynos ryzyko RAM) i nowy Engine po resecie konwersacji ograniczają mieszanie rekurencyjnego stanu. Starsze modele zachowują własne dotychczasowe ścieżki. E2B pozostaje domyślnym modelem telefonu, G3 nie daje nowej władzy nad Call ani danymi.

`AndroidGemmaE2BQwen35DialogueABNativeTest` wykonuje identyczne 8 scenariuszy ×3 powtórzenia ×2 modele ×2 tury przy `2048` i statycznym snapshotcie Skills. Prywatne syntetyczne dialogi zapisywane są w `no_backup/g3-private` i mogą być odebrane lokalnie poprzez debugowy `adb run-as`, bez wysyłania tekstu do logcat/GitHub. Zwykłe logi emitują tylko metryki. **G3 fizyczne 96/96 tur na S22 i kompilacja najnowszego journalu PASS; 48 prywatnych par wyeksportowane na Mac.** Formalnie host task `failed` tylko przez `task_budget_exhausted` ostatniego niewykonanego clean Git; semantic review OPEN. CPU-vs-GPU nie jest kontrolowanym porównaniem tego samego akceleratora. [Dokładny checkpoint/handoff](checkpoints/2026-11-09-g3-e2b-qwen35-pending-host-receipt-handoff.md).

## Skills: prompt injection vs wywołania agentowe — zweryfikowany zakres 2026-11-09

`ConversationSkillLibrary` zarządza wersjonowanymi snapshotami, a `ConversationInstructionBuilder` scala aktywne instrukcje w statycznym promptcie. **F-156 VERIFIED_SCOPED** po przełożeniu wersjonowanych instrukcji *za* wiążące zasady bezpieczeństwa i *przed* formatem odpowiedzi; fizyczna E2B na S22 (`active=true`, `inactive=false`) i izolowany UI edit/rollback PASS. Wspólny backend LiteRT-LM pozostaje niezmieniony.

Oficjalne `Agent Skills` AI Edge Gallery to osobna **warstwa aplikacji**: `SKILL.md` z metadanymi `name/description`, selektywne włączenie pełnej instrukcji w kontekst, wykonawcze narzędzia JS lub natywne akcje. Nie jest to specjalny przełącznik w Gemmie. W AI Calls narzędzia telefoniczne nadal będą wyłącznie *propozycjami*, a granty, `CallCommitmentGate`, `FactDisclosurePolicy` i STOP pozostają nadrzędnymi warstwami autoryzacji i dowodu skutku. [Analiza i plan eksperymentu](AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md).

Porównanie S22 E2B/E4B na tych samych testach 2048: **18/18 tur syntetycznych**; oba GPU i podobna poprawność punktowa, E4B około 3× wolniejsza. To wspiera pozostawienie E2B w telefonicznej ścieżce domyślnej, bez modyfikacji profilu, oraz dalszy test osobnego selektora Skills. Nie nadawać N=1 próbce statusu benchmarku p95/realnych rozmów. [Checkpoint](checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md).


## Laboratorium: konfiguracja kontekstu i natywna diagnostyka — F-155 (2026-11-09)

`ConversationSkillLabActivity` ma lokalną preferencję dodatniego budżetu `context_tokens` (domyślnie 4096), snapshotowaną przez `AndroidSkillLabSessionFactory.open` na **nową sesję**. Nie zmienia domyślnego `DEFAULT_CONTEXT_TOKENS=2048` w backendzie Call. Wspólny `DialogueEngineProvider` przepuszcza opcjonalny, tylko-laboratoryjny observer `NativeModelTiming` do `LiteRtGemma4Runtime`, który mierzy oddzielnie tworzenie silnika `Engine`, konwersacji i `sendMessage`. Listener nie zmienia autoryzacji, STOP, runtime ani odpowiedzi. Bez opóźnionej rejestracji do zewnętrznego API: per-turn metryki są obsługiwane w UI, a pełny log `AiCallBridgePerf` zawiera tylko CPU/GPU, czasy, reuse, kontekst, status i liczbę znaków (nie użytkownika/tekst/numery). `Debug.MemoryInfo` pokazuje PSS procesu, `ActivityManager.MemoryInfo` wolną RAM na moment odczytu. Tokens/s nie jest liczone bez tokenizera. F-155 fizycznie **VERIFIED** (S22 4/4, full Mac PASS). [Checkpoint](checkpoints/2026-11-09-f155-lab-context-latency-diagnostics.md).


## Laboratorium/telefonia przez prywatny USB/ADB — architektura 2026-11-08

`UsbLabControlReceiver` jest odbiornikiem `BroadcastReceiver` chronionym `android.permission.DUMP` i flagą `FLAG_DEBUGGABLE`. Mac `ai_calls_usb_lab.py` zapisuje żądanie w katalogu aplikacji przez `adb shell run-as` i wysyła jedynie nonce przez `am broadcast`. Odpowiedź i opcjonalny transkrypt trafiają do prywatnego magazynu. Brak serwera HTTP/TCP, ogólnego RCE i publicznego eksportu danych.

`UsbLabControl` deleguje sesje tekstowe do **istniejącego** `SkillLabRunner`/`AndroidSkillLabSessionFactory`; GUI i USB współdzielą procesową dzierżawę natywnego modelu (F-150). `AudioEngineProfileStore` i `IncomingAssistantSettingsController` pozostają właścicielami konfiguracji. Takeover/resume/exact-owned-stop przekazują działanie do istniejących, uprawnionych kontrolerów Call — bez globalnego rozłączania i dowolnego `place_call`. 4096 to domyślny Lab, 2048 to niezmieniony Call, 8192 wyłącznie jawny eksperyment.

**Status:** pełny Mac gate 370 Python, Gradle/JVM, lint, app APK i AndroidTest build PASS; **nowy receiver/GUI-USB concurrency i interwencje Call na fizycznym S22 NOT_RUN**. [Pełny checkpoint](checkpoints/2026-11-08-usb-lab-telephony-full-checkpoint.md) · [instrukcja USB](USB_LAB_TELEPHONY_CHANNEL.md).

## Docelowe składanie produktu

[PRODUCT_ASSEMBLY_PLAN](PRODUCT_ASSEMBLY_PLAN.md) definiuje dalszy kierunek: wspólne moduły dla agenta zadań i sekretarki, osobne dane profilu/zadania, skillsy/instrukcje i archiwum, manual/AI/przejęcie oraz ciągły zapis i odtwarzanie całej sesji z odnośnikami czatu. Wydzielone obecnie pakiety nie są jeszcze kompletem niezależnych bibliotek Gradle. Recorder pełnej sesji ma być niezależny od okien VAD i cyklu inferencji; obecne archiwum tur nie spełnia jeszcze tego wymagania (F-080). Plan nie zmienia zamrożonego transportu ani statusu fizycznego QA.

## UX-05 — warstwa lokalizacji i dostępności (2026-11-07)

UX-05 tylko prezentacja: 553 parzyste zasoby PL/EN, widoczne opisy katalogów i factory skill labels mapowane przy renderowaniu, bez zmiany zapisanych promptów, nazw technicznych i model selection. `UiDesign.heading` oznacza nagłówki dostępności, statusy Home/Kampanii/Pilota są politem live region; istniejący przycisk, nawigacja, zasady screenshotów i wykonawcze ścieżki zachowane. [Źródło/host/S22 2/2 i 4/4 PL/EN](checkpoints/2026-11-07-ux05-pl-en-accessibility-closeout.md). Nie przedstawiać automatycznej instrumentacji jako manualnego TalkBack.

## Odczytowe granice UX (2026-11-07)

Po UX-01–04 bieżąca aplikacja rozdziela presenter statusu Kampanii, kontroler historii, `SkillLabRunner` oraz typowane prezentery stanów modeli i zapisanego profilu audio. Nie są to nowe serwisy Androida ani drugi store: `AudioEngineProfileStore`, `AudioEngineReadinessProbe`, `AndroidGemma4ModelReadiness`, `LanguageModelTransferService` i `TextToSpeech` zachowują właścicielstwo danych i efektów. [UX-04 SOURCE_PASS](checkpoints/2026-11-07-ux04-model-and-audio-presentation-closeout.md) obejmuje 13 JVM, 332 host Python i kompilację AndroidTest/APK, nie fizyczny S22 nowego SHA.

## Bieżący podział runtime

[CONVERSATION_RUNTIME](CONVERSATION_RUNTIME.md) opisuje wdrożony podział: AndroidConversationRuntime + ConversationScenario, oddzielne przygotowanie skills/danych i live/history chat. CLIR jest adapterem aplikacyjnego scenariusza, bez reguł Orange w silniku mediów. Profile INCOMING/OUTGOING i kompilator ról są wspólne; [Adapter odbierania](INCOMING_ASSISTANT.md) jest zaimplementowany wraz z niezależnym rdzeniem JVM conversation-core i manual/przejęciem; fizyczne end_call i podstawowy INCOMING zweryfikowane, manual/przejęcie/STOP jeszcze QA_PENDING. [Nowe QA](checkpoints/2026-11-05-conversation-modules.md).

## Granice modułowe — aktualizacja 2026-11-06

Sześć modułów Gradle: app, audio-bridge, privileged-helper, realtime-client, conversation-core (czysty JVM) i conversation-tools (czysty JVM). Po kroku 1 etapu 3 z 220 produkcyjnych plików Kotlin 195 jest w app; IncomingIdleTimeout i ConversationSessionRegistry są w JVM conversation-core. Rejestr chroni jedną sesję bez zależności Androida. AndroidConversationRuntime ma 815 linii i 7 użyć produkcyjnych; CallRealtimeSessionOrchestrator 758 linii i 4 użycia. To aktywne komponenty, nie potwierdzony zbędny kod. Przenosić neutralne reguły i polityki do istniejących bibliotek krok po kroku, z migracją konsumentów i regresją, bez zmian zamrożonego toru Samsung/Shizuku.

[Zamknięcie etapów 0–1](checkpoints/2026-11-06-modularization-handoff-final.md) i [etap 2 NO_CHANGE](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md) i [etap 3, krok 1 PASS](checkpoints/2026-11-06-mod-stage3-session-registry-core.md) oraz [metryki sprzed etapu 1](checkpoints/2026-11-06-quality-modularity-reaudit.md). Incoming 30 s ostrzeżenia + 15 s czasu reakcji przechodzi przez ten sam approval/TTS, a finalny end_call przez istniejący executor po pełnym TX.

## Granice i źródła prawdy

Bieżący AI Calls jest modułowym laboratorium testowym; docelowy autonomiczny flow powstanie ze sprawdzonych komponentów. [Zakres laboratorium i przegląd granic](TEST_LAB_SCOPE.md). Rzeczywiste zadania wykonuje wspólny aplikacyjny tor przez własny Android Telecom Call. Modele, ServicePack, PhraseMatrix, TaskGraph, supervisor, STT i TTS **nie są źródłami zgody**. Bieżący stan QA → [intake i A/B](checkpoints/2026-11-05-task-intake-and-dialogue-ab.md); aktualne OPEN/VERIFIED → [BLOCKERS](../BLOCKERS.md); reguły → [SECURITY_PRIVACY](SECURITY_PRIVACY.md) i [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md).

```text
Użytkownik → CallTask + CallResolvedTarget + ważny odwoływalny grant (cel/SIM/fakty/efekty)
  → stan Android, dokładnie własny Call
  → zamrożony Samsung/Shizuku RX (PFD/PCM16 16k)
  → SpeechInputProvider (Android STT / Parakeet PL; eksperymentalne AudioBytes)
  → per-call DialogueEngineProvider (Gemma E2B SCRIPT / Direct RAW)
  → aplikacyjne walidatory / FactDisclosurePolicy / zatwierdzony tekst
  → SpeechOutputProvider (Android TTS, opcjonalny Piper gdy realnie dostępny)
  → AuthorizedPcmUplinkWriter → Samsung cellular TX
  → aplikacyjny STOP/teardown own-Call → oddzielny dowód zewnętrznego efektu → raport
```

**Nie utożsamiać:** grant ≠ walidacja propozycji ≠ permit z `CallCommitmentGate` ≠ zużycie permitu ≠ efekt po stronie operatora ≠ zakończenie zadania. `CallExternalEffectValidator`, `CallExternalEffectCompletionTracker` i `FactDisclosurePolicy` zachowują odrębną odpowiedzialność. Wykonalny typ `SetService(CLIR)` lub `BookAppointment` nie dowodzi gotowości całego scenariusza.

## Dialogue i pamięć

- Stabilne `ClirDeterministicDialogueBackend` / `GateCHybridDialogueBackendFactory` oraz jawny `GemmaDirectDialogueBackend` RAW. RAW wysyła swobodny tekst przez aplikacyjny release gate, bez parsera akcji JSON. Nadal brakuje pełnej walidacji znaczenia swobodnego tekstu **F-045 OPEN** — brak automatycznego prawa do ujawniania lub obietnic modelu.
- `CampaignGemmaPreflight` / `CampaignGemmaDirectPreflight`: prewarm przed wybieraniem, reset syntetycznego kontekstu; dla RAW dokładnie ten sam autoryzowany `TaskBrief` i prompt-skills co live. F-043: utrzymuj zdrową jedną natywną `Conversation` przez kolejne tury **jednego** `Call`, zamykaj przy STOP/błędzie/teardown. Model nie może zmienić profilu ani celu w trakcie Call.
- Lokalny `ConversationChatFeed` przechowuje nietrwały prywatny STT/tekst zatwierdzony/TX dla ekranów, a RecordedConversationHistory czyta istniejące archiwa timeline; nie publikuje transkryptów do GitHub/logcat. Prawdziwe audio/transkrypty diagnostyczne tylko na urządzeniu i prywatnym Macu.
- **F-048 OPEN:** typowany `FactRequest` → `FactDisclosurePolicy` → `IdentityVault` → kontrolowany release wypowiedzi. Nie wklejać danych abonenta do promptów ani wyników agenta. **F-047 OPEN:** niepełna końcowa tura RX przy hangup; diagnostyka bez dodatkowego TX. **F-049 WYCOFANY:** dwuczęściowe powitanie infolinii nie jest defektem VAD/ACK.

## Profil audio M1–M4

`AudioEngineProfile` / `AudioEngineProfileStore` to osobna **preferencja**, nie grant ani prompt-skill. `AudioEngineRegistry` rozdziela *zintegrowany wybór* od *rzeczywistej dostępności urządzenia*; `AudioEngineReadinessProbe` sprawdza offline STT, lokalne modele i głosy. `AudioEngineSettingsActivity`: osobne **Wejście / Model / Wyjście**. Profil jest snapshotowany przed prewarm i obowiązuje przez jeden owned Call, bez cichej zamiany.

- **M1:** `AndroidSttInputProvider` → `DialogueEngineProvider` E2B → `AndroidTtsOutputProvider` pozostają funkcjonalnie zgodne ze starym `OnDeviceSpeechInput` / `LocalTtsSpeechOutput`; `LocalSpeechTextPipeline` i `AuthorizedPcmUplinkWriter` są wspólną ścieżką sterowania.
- **M2:** `ParakeetSpeechInputProvider` używa MediaPipe AudioTranscriber, modelu stateful 614 588 752 B pinned SHA, PCM16→float, bounded nonblocking queue, onReady/final/error/cancel. S22: 3 polskie próbki syntetyczne bez błędów słów/znaków i jedna pełna rzeczywista tura Orange. Naturalny korpus, final segments i wspólne zasoby pozostają OPEN.
- **M3:** `PiperAndroidTtsOutputProvider` deleguje przez jawny `TextToSpeech` engine `dev.ihorshevchuk.piper` i wymaga offline głosu z `piperVoice`; brak fallbacku. APK 1.1 i Gosia są na S22, lecz publiczne API silnika odrzuca wybór głosu (F-057); brak PCM/live PASS.
- **M4:** `GemmaAudioDirectInputProvider` buforuje PCM wypowiedzi → WAV → LiteRT `Content.AudioBytes`, model GPU i audio backend CPU. Instrukcja musi poprzedzać audio (F-056: usunięte echo promptu). S22: 3 polskie próbki syntetyczne bez błędów słów/znaków. To **nie** zintegrowany dialog audio/audio; `GEMMA_AUDIO_DIRECT` remains disabled. Kokoro/PocketTTS nie są zintegrowane dla polskiego. E4B jest tekstowym modelem w aktualnym katalogu i ma native PL/JSON PASS; nie jest dodatkowym backendem wejścia audio. [Najnowszy pomiar](checkpoints/2026-11-05-audio-quality-orange-acceptance.md).

## Nienaruszalna granica fizyczna

Samsung/Shizuku privileged helper `CallMediaSessionCoordinator`: przesyłanie PCM przez PFD (nie Binder per frame), po utracie endpointu fail-safe, STOP/TAKE OVER, tylko własny Call. **Nie zmieniać bez osobnego powodu i akceptacji**. Normatywny fizyczny freeze: [PHASE2D_FREEZE_2026-09-18](PHASE2D_FREEZE_2026-09-18.md). Żaden licznik `WRITE_COMPLETE`, off-call test, pozytywny host QA ani samo UI nie dowodzą słyszalności u drugiej strony czy wykonania CLIR.

Historia wdrożeń/fizycznych QA: [RAW live](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md), [M2–M4](checkpoints/2026-11-04-audio-engines-m2-m4-sprint-checkpoint.md), [host quality](checkpoints/2026-11-04-audio-models-hostonly-quality-gates-checkpoint.md). Te checkpointy są archiwum dowodów, nie bieżącą instrukcją.


## Katalog modeli LiteRT-LM (2026-11-05)

`LanguageModelCatalog` wiąże `DialogueModelId` z przypiętym plikiem, SHA-256, rozmiarem i źródłem. `LiteRtDialogueEngineProvider` przekazuje wybrany identyfikator do wspólnego istniejącego runtime i aplikacyjnej walidacji wyjścia. `AudioEngineProfileStore` pozostaje jedynym miejscem wyboru modelu; STT/TTS/głos są niezależne, aktywna rozmowa zachowuje snapshot. Readiness dotyczy wybranego pliku, bez fallbacku do E2B. Gemma Audio wskazuje jawnie E2B, niezależnie od modelu tekstowego.

Nieeksportowany `LanguageModelTransferService` serializuje import i pobranie przez istniejący installer: rozmiar + SHA-256, staging, atomic move. Serwis nie wybiera modelu i nie posiada żadnej władzy nad połączeniami. Katalog UI wywołuje ten sam serwis z ekranu głównego i ustawień audio; poprzedni równoległy downloader E2B w MainActivity został usunięty. [Dowody i ograniczenia](checkpoints/2026-11-05-litert-model-catalog.md).


## Przyjmowanie polecenia właściciela (F-062/F-065)

Jawne polecenie Codexa przez istniejącą DUMP-chronioną DiagnosticProbeActivity i START w UI trafiają do tego samego `CampaignAgentControl.run`. Aplikacja przyjmuje/reużywa konkretny zakres zadania i uruchamia owned-call service. Nie ma oddzielnego grantu, dialogu ani czasowego TTL; podgląd niczego nie przyjmuje. Trwały pojedynczy zapis zakresu i mechanizmy rezygnacji pozostają w CampaignAuthorizationStore. Sama kontynuacja nie tworzy nowej zgody. Zakres nie przechodzi na inny cel/SIM/efekt/fakt.

Dwa backendy dialogu mają oddzielne znaczenie: SCRIPT_GEMMA = PhraseMatrix → klasyfikacja Gemma → zatwierdzone teksty akcji/recovery; GEMMA_DIRECT = tekst modelu → wspólny aplikacyjny release/TTS. Parametr trybu jest przesyłany jawnie przez runner, a źródła/czasy obu backendów pozostają w metrykach. RAW nadal nie posiada brakującego adaptera FactRequest (F-048).

## Modułowe laboratorium — bieżący kierunek

`SpeechInputProvider`, `DialogueEngineProvider` i `SpeechOutputProvider` są istniejącymi granicami wymiany silników. `AudioEngineProfile` przechowuje STT/model/TTS/głos; strategia dialogu zachowuje swój magazyn, a skills/notatki/referencje danych są wspólnym przygotowaniem rozmowy. Istniejące wybory składa teraz `ConversationRunConfiguration`: ScriptAssisted albo ModelDirect z wymaganą instrukcją. Ten sam obiekt trafia do prewarm, owned-call executora i potoku. Nie dodano drugiego magazynu preferencji. Instrukcje są zamrożone na próbę; edycja nie przerywa prewarm, a zakres/cofnięcie/STOP pozostają sprawdzane na żywo.

Start w `CampaignAgentControl`, service i executor nadal korzystają z `ClirCampaignSpec`. Wspólny runtime otrzymuje już odrębny scenariusz CLIR lub nadzorowany. Ogólny intake innych zadań nadal wymaga integracji. Adapter odbierania już działa i korzysta z tego samego potoku i wspólnych polityk. Tekst, plik audio i mikrofon mają pozwalać ocenić komponenty bez zmiennego rozmówcy Orange; kontrolowana telefonia osobno weryfikuje RX/TX. To plan integracji, nie deklaracja gotowego uniwersalnego runnera. Szczegóły i ocena instalacji: [TEST_LAB_SCOPE](TEST_LAB_SCOPE.md).

`GemmaDirectDialogueBackendFactory` jest teraz adapterem wymagającym jawnego backendu modelu, bez własnego Context, domyślnego E2B i domyślnego promptu. Snapshot Direct kieruje cache i tworzenie do tej samej pary profil/instrukcja. SCRIPT korzysta ze wspólnego `CampaignGemmaPreflight.createClassifier` dla prewarm i braku cache, również z wybranym profilem. Brak gotowej instancji nie podmienia wybranego modelu. [Receipts i granice QA](checkpoints/2026-11-05-lab-run-configuration.md).

## UX / application boundaries — plan po Sprincie 6 (2026-11-07)

**Stan:** Sprint 6 UX **CLOSED / HOST + S22 + REAL_GEMMA PASS**, kod `1b359146`. [Finalny checkpoint](checkpoints/2026-11-07-ux-sprint6-final-handoff.md). Poniższa separacja jest **planem przyszłej iteracji**, nie wdrożonym refaktorem.

```text
Android Activity (widok, nawigacja, potwierdzenie, lifecycle)
     ↓ zdarzenia użytkownika                ↑ immutable ScreenState / zdarzenia UI
Presenter / Controller / Runner (jedna odpowiedzialność, testy stanów)
     ↓ istniejące repo/provider                ↑ typowany wynik
RecordedConversationHistory / AudioEngineProfileStore / SkillLabScoring / RuntimeStatusStore
     ↓ wyłącznie istniejące autoryzowane efekty w aplikacji
CampaignAgentControl → polityka/grant → własny Telecom Call → STT / Dialog / TTS / TX
```

- **Odczyt i prezentacja nie są upoważnieniem:** stan `targeted_disconnect_requested` to żądanie, nie potwierdzone zakończenie. Presenter Kampanii nie może wykonywać STOP, grantów, call effects ani czytać prywatnych danych.
- **Historia:** `ConversationHistoryActivity` obecnie posiada UI i wywołuje asynchronicznie `RecordedConversationHistory`. Docelowo Controller przejmie kolejkę IO, serializację kasowania, typowane wyniki i ochronę przed stale callbacks. `ArchivedConversationPlayer` zostaje kontrolowaną off-call warstwą Android. Nie tworzyć kopii archiwum.
- **Laboratorium:** `ConversationSkillLabActivity` obecnie łączy renderowanie, `TextCallAgentBackend`, `Thread`, `cancel`, `120s` timeout i obserwacje. Docelowo `SkillLabRunner` emituje stan niezmienny; `SkillLabScoring` pozostaje czystym obliczeniem; UI nie wykonuje `END_CALL`.
- **Istniejące rozdzielenie:** nie duplikować `IncomingAssistantSettingsController`, `GeneralSettingsController`, `ConversationToolsSettingsController` ani transferu modeli.
- **Kierunek:** małe, niezależne kroki UX-01 prezentacja kampanii → UX-02 historia → UX-03 Laboratorium → ustawienia/i18n; [plan bramek](SAFE_MODULARIZATION_PLAN.md). Inwarianty Telecom/STOP/PCM/PFD/TX, oszczędne testy i granice bezpieczeństwa pozostają bez zmian.
