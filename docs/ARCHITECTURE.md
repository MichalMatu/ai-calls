# AI Calls — architektura operacyjna

## Granice i źródła prawdy

AI Calls jest aplikacyjnym, lokalnym agentem wykonującym **konkretnie autoryzowane zadania przez własny Android Telecom Call**. Modele, ServicePack, PhraseMatrix, TaskGraph, supervisor, STT i TTS **nie są źródłami zgody**. Bieżący stan QA → [phone-ready checkpoint](checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md); aktualne OPEN/VERIFIED → [BLOCKERS](../BLOCKERS.md); reguły → [SECURITY_PRIVACY](SECURITY_PRIVACY.md) i [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md).

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
- **M4:** `GemmaAudioDirectInputProvider` buforuje PCM wypowiedzi → WAV → LiteRT `Content.AudioBytes`, model GPU i audio backend CPU. Instrukcja musi poprzedzać audio (F-056: usunięte echo promptu). S22: 3 polskie próbki syntetyczne bez błędów słów/znaków. To **nie** zintegrowany dialog audio/audio; `GEMMA_AUDIO_DIRECT` remains disabled. E4B/Kokoro/PocketTTS bez kwalifikacji są wyłączone. [Najnowszy pomiar](checkpoints/2026-11-05-audio-quality-orange-acceptance.md).

## Nienaruszalna granica fizyczna

Samsung/Shizuku privileged helper `CallMediaSessionCoordinator`: przesyłanie PCM przez PFD (nie Binder per frame), po utracie endpointu fail-safe, STOP/TAKE OVER, tylko własny Call. **Nie zmieniać bez osobnego powodu i akceptacji**. Normatywny fizyczny freeze: [PHASE2D_FREEZE_2026-09-18](PHASE2D_FREEZE_2026-09-18.md). Żaden licznik `WRITE_COMPLETE`, off-call test, pozytywny host QA ani samo UI nie dowodzą słyszalności u drugiej strony czy wykonania CLIR.

Historia wdrożeń/fizycznych QA: [RAW live](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md), [M2–M4](checkpoints/2026-11-04-audio-engines-m2-m4-sprint-checkpoint.md), [host quality](checkpoints/2026-11-04-audio-models-hostonly-quality-gates-checkpoint.md). Te checkpointy są archiwum dowodów, nie bieżącą instrukcją.


## Katalog modeli LiteRT-LM (2026-11-05)

`LanguageModelCatalog` wiąże `DialogueModelId` z przypiętym plikiem, SHA-256, rozmiarem i źródłem. `LiteRtDialogueEngineProvider` przekazuje wybrany identyfikator do wspólnego istniejącego runtime i aplikacyjnej walidacji wyjścia. `AudioEngineProfileStore` pozostaje jedynym miejscem wyboru modelu; STT/TTS/głos są niezależne, aktywna rozmowa zachowuje snapshot. Readiness dotyczy wybranego pliku, bez fallbacku do E2B. Gemma Audio wskazuje jawnie E2B, niezależnie od modelu tekstowego.

Nieeksportowany `LanguageModelTransferService` serializuje import i pobranie przez istniejący installer: rozmiar + SHA-256, staging, atomic move. Serwis nie wybiera modelu i nie posiada żadnej władzy nad połączeniami. Katalog UI wywołuje ten sam serwis z ekranu głównego i ustawień audio; poprzedni równoległy downloader E2B w MainActivity został usunięty. [Dowody i ograniczenia](checkpoints/2026-11-05-litert-model-catalog.md).
