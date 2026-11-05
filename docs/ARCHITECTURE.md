# AI Calls — architektura operacyjna

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
- Lokalny `LiveCallChatFeed` przechowuje nietrwały prywatny STT/tekst zatwierdzony/TX dla trzech ekranów; nie publikuje transkryptów do GitHub/logcat. Prawdziwe audio/transkrypty diagnostyczne tylko na urządzeniu i prywatnym Macu.
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

`SpeechInputProvider`, `DialogueEngineProvider` i `SpeechOutputProvider` są istniejącymi granicami wymiany silników. `AudioEngineProfile` przechowuje STT/model/TTS/głos; strategia dialogu i prompt-skills mają osobne magazyny. Istniejące wybory składa teraz `ConversationRunConfiguration`: ScriptAssisted albo ModelDirect z wymaganą instrukcją. Ten sam obiekt trafia do prewarm, owned-call executora i potoku. Nie dodano drugiego magazynu preferencji. Instrukcje są zamrożone na próbę; edycja nie przerywa prewarm, a zakres/cofnięcie/STOP pozostają sprawdzane na żywo.

Start w `CampaignAgentControl`, service i executor nadal korzystają z `ClirCampaignSpec`. Wydzielanie scenariusza i kanału testu ma usunąć to sprzężenie, używając obecnego potoku i wspólnych polityk. Tekst, plik audio i mikrofon mają pozwalać ocenić komponenty bez zmiennego rozmówcy Orange; kontrolowana telefonia osobno weryfikuje RX/TX. To plan integracji, nie deklaracja gotowego uniwersalnego runnera. Szczegóły i ocena instalacji: [TEST_LAB_SCOPE](TEST_LAB_SCOPE.md).

`GemmaDirectDialogueBackendFactory` jest teraz adapterem wymagającym jawnego backendu modelu, bez własnego Context, domyślnego E2B i domyślnego promptu. Snapshot Direct kieruje cache i tworzenie do tej samej pary profil/instrukcja. SCRIPT korzysta ze wspólnego `CampaignGemmaPreflight.createClassifier` dla prewarm i braku cache, również z wybranym profilem. Brak gotowej instancji nie podmienia wybranego modelu. [Receipts i granice QA](checkpoints/2026-11-05-lab-run-configuration.md).
