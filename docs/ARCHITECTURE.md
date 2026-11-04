# AI Calls — architektura

## Cel i granice

Produkt wykonuje **konkretnie autoryzowane zadanie telefoniczne na urządzeniu**: interpretacja celu → wybór zatwierdzonego numeru/SIM → telefon → dialog STT/Gemma/TTS → deterministyczny skutek i dowód → selektywne zakończenie własnego połączenia → wynik użytkownikowi. CLIR jest aktualnym przypadkiem testowym; nie ogranicza silnika do operatora ani nie dowodzi implementacji ogólnych wizyt/rezerwacji.

## Przepływ i właściciele decyzji

```text
użytkownik → aplikacyjny grant (cel / target / SIM / fakty / dozwolone skutki)
  → sprawdzenie Android / IDLE / uprawnień
  → Telecom + dokładnie własny Call
  → Samsung/Shizuku cellular RX
  → lokalny polski STT
  → workflow/PhraseMatrix + Gemma (propozycja typowanej akcji/odpowiedzi)
  → walidator celu/skutku + FactDisclosurePolicy + output approval
  → lokalny TTS → autoryzowany cellular TX
  → wykonanie w granicach grantu + niezależny dowód zewnętrznego efektu
  → selektywny STOP/teardown → IDLE → raport
```

Gemma, ServicePacks, TaskGraph, PhraseMatrix, parser i supervisor **nie są magazynami uprawnień**. Model interpretuje kontekst i proponuje; aplikacja ogranicza i zatwierdza. `CallTask`, `CallResolvedTarget`, `CallExternalEffectValidator`, `CallCommitmentGate`, `CallExternalEffectCompletionTracker`, `FactDisclosurePolicy` i właściciel workflow zachowują odrębne role. `BookAppointment` i `SetService(CLIR)` to typowane efekty w kodzie, **nie dowód gotowości ich wszystkich dialogowych wykonawców w realnym połączeniu**.

```text
autoryzacja ≠ walidacja propozycji ≠ permit ≠ zużycie permitu
           ≠ zewnętrzny sukces ≠ ukończenie zadania
```

## Warianty silnika dialogowego (Gemma Direct, 2026-11-04)

- **Script + Gemma (domyślny):** istniejący `ClirDeterministicDialogueBackend` → `GateCHybridDialogueBackendFactory`, deterministyczne akcje i odpowiedzi. Obecna ścieżka zachowana.
- **Gemma Direct RAW (jawny wybór):** `GemmaDirectDialogueBackend` pomija skrypty i parser akcji JSON; lokalny model tworzy swobodną wypowiedź bez modelowo wywoływanych narzędzi, przez wspólną bramkę stanu aplikacji → TTS/TX. Nie ma w nim obecnie wykonywalnego `DISCLOSE_PHONE` ani `CONFIRM_EFFECT` — zapobiega to niekontrolowanemu wykonywaniu skutków, ale **nie jest pełną semantyczną ochroną treści wypowiedzi** (F-045 OPEN). Na fizycznym S22 potwierdzono wieloturowe 8/8 i 7/7 STT → Gemma RAW → lokalne WRITE_COMPLETE bez DIRECT_REJECTED. Jedna kolejna tura RX z pierwszej sesji miała RX_INTERRUPTED po hangup (F-047). Przebieg to nie niezależny dowód CLIR, słyszalności każdej wypowiedzi ani pełnej semantycznej zgodności treści.
- **Dynamiczny TaskBrief F-044 (kod po frozen RAW v1):** `GemmaDirectTaskBriefBuilder` składa instrukcję systemową z dokładnie uprawnionego `CampaignExecutionRequest` + `CampaignAuthorizationGrant`; stała rola to DZWONIĄCY asystent użytkownika, nie konsultant. `GemmaDirectPromptSkill` (cel/IVR/procedura/wyjaśnienie) to wybieralne moduły tekstu, nie wykonywalne tool-calle ani dodatkowe uprawnienia. `CampaignToolActivity` daje menu i podgląd, wybór zapisywany per grant. Prewarm i właściwa natywna `Conversation` dostają dokładnie ten sam prompt; brak sekretów, grantId i wartości konta w prompcie. Na dziś zweryfikowany live wykonawca jest ograniczony do `SET_SERVICE(CLIR=true)`; nowe typy zadań wymagają osobnych adapterów i grantów. Brak jest typowanego pobrania autoryzowanego faktu (F-048); dowody i plan w [checkpoint live](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md).
- Tryb jest własnością aplikacji, zapisany per-grant dla autoryzowanej kontynuacji i nie poszerza uprawnień. Brak/nieznany tryb nie uruchamia niekontrolowanego modelu, a stary START nadal wybiera script.
- **Dwa poziomy weryfikacji:** `PROVEN_S22_OFFCALL` oznacza cztery kolejne natywne syntetyczne tury z dynamicznym TaskBrief na GPU i zachowanymi uprawnieniami. **Fizyczny dialog RAW** 8/8 i 7/7 tur został zarejestrowany prywatnie na S22; `WRITE_COMPLETE` oznacza lokalną transmisję, nie dowód odsłuchu czy zakończenia CLIR. [Checkpoint odbioru](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md).

## Pamięć dialogu i przyspieszenie

- `CampaignGemmaPreflight` rozgrzewa silnik Gemmy przed wybieraniem numeru. Syntetyczny kontekst jest czyszczony przed prawdziwym dialogiem, ale wagi/Engine pozostają gotowe.
- F-043 utrzymuje jeden zdrowy natywny LiteRT-LM `Conversation` dla kolejnych wypowiedzi w tym samym połączeniu. Zakończona tura nie uruchamia `cancelProcess()`. Aktywne anulowanie, STOP i błędy odrzucają niebezpieczną sesję; backend po zakończeniu rozmowy zamyka zasoby. Warianty bez pamięci natywnej mogą nadal korzystać z aplikacyjnego kontekstu tur.
- F-042 przygotowuje neutralną zapowiedź w osobnym lokalnym TTS; po wykryciu końca mowy może ją wysłać wyłącznie w aplikacyjnie autoryzowanej kampanii. Zapowiedź i odpowiedź główna korzystają z jednego serializowanego TX; inferencja Gemmy biegnie niezależnie. **Korekta właściciela (2026-11-04):** dawny F-049 dotyczył dwuczęściowego powitania infolinii, a nie usterki AI Calls; został wycofany. Nie modyfikować z tego powodu VAD, schedulerów ACK/TX ani czasu reakcji.
- `LocalTtsSpeechOutput` obecnie tworzy instancję silnika TTS przy wywołaniu `synthesize`; ewentualny pooling/reuse wymaga pomiaru wpływu na czas, testów zamykania i braku regresji STOP.

## Kontrolowane fakty i obserwowalność (stan wdrożenia)

- **`FactRequest` / `IdentityVault` (F-048, OPEN):** docelowy model: identyfikator żądanego pola → `FactDisclosurePolicy` z dokładnym grantem, odbiorcą i celem → tylko dozwolona wartość → autoryzowany release wypowiedzi. Obecny Gemma RAW zna cel, ale nie ma wykonawczego adaptera do danych abonenta. Sam prompt ani prompt-skill nie jest źródłem zgody i nie zawiera plaintext danych.
- **Prywatny dock czatu:** `ChatRelayLiveCallProbe` publikuje wyłącznie w pamięci procesu `LiveCallChatFeed` (z tokenem sesji) → `LiveCallChatPanel` pod `MainActivity`, `CampaignToolActivity` i `AndroidCallControlsActivity`; pokazuje finalne STT rozmówcy, zatwierdzone odpowiedzi Gemmy i stan lokalnego zapisu TX. `Wyczyść widok` nie ingeruje w Call, grant ani prywatny osobny zapis evidence. Dane nie trafiają na GitHub/logcat.
- **Archiwum diagnostyczne:** zatwierdzony zapis na S22 i prywatny eksport USB/ADB tylko do Mac `private-exports`; pełne WAV i transkrypcje nie są przedmiotem publicznych commitów lub wyników zadań.

## Moduł wyboru silników audio — M1 PASS off-call, M2/M3 Mac PASS, M4 eksperymentalne

`audioengines/AudioEngineProfile`, `AudioEngineRegistry`, `AudioEngineProfileStore` i `AudioEngineReadinessProbe` określają lokalną preferencję, nigdy uprawnienie. Oddzielny ekran `AudioEngineSettingsActivity` prezentuje wejście / dialog / wyjście, język i faktycznie niedostępne modele, wybiera lokalny głos `pl-PL` i sprawdza offline obecny Android STT, plik E2B oraz systemowe głosy bez wykonywania połączeń.

`SpeechInputProvider` i `SpeechOutputProvider` opakowują aktualne `OnDeviceSpeechInput` i `LocalTtsSpeechOutput`; `LocalSpeechTextPipeline` nadal wykonuje istniejące output approval, generation gate i PCM16/16k do `AuthorizedPcmUplinkWriter`. `AppOwnedCampaignService` utrwala jeden profil na kampanię, a oba prewarm przekazują/odbierają engine tylko z identycznym profilem (dla RAW także dokładny `TaskBrief`), więc nie ma cichej zamiany podczas tury. Nie modyfikowano `CampaignAuthorizationGrant`, prompt-skills, VAD, ACK ani Shizuku.

**Zaimplementowany tylko Android STT → Gemma 4 E2B → Android TTS.** Parakeet PL, Gemma Audio Direct, E4B, Piper PL i warianty bez `pl-PL` są widoczne jako nieaktywne; samo menu nie dowodzi instalacji/model readiness. Pierwszy Mac QA wykrył F-050 (permission lint na preview TTS), naprawę zacommitowano; końcowe host QA i exact S22 off-call **PASS** dla M1. Dodatkowy `DialogueEngineProvider` z `DialogueEngineProviders.resolve` owija istniejącą fabrykę `Gemma4LiteRtTextBackendFactory` dla SCRIPT i RAW bez zmiany silnika. Odrzuca E4B bez natywnego adaptera. `CampaignGemmaPreflight` i `CampaignGemmaDirectPreflight` wciąż kasują syntetyczny kontekst natywny. Pierwsze QA adaptera v4 wykryło F-051 w typowaniu resetu; kod po poprawce `a616795e` ma Mac v5 i S22 off-call v2 PASS. Nowy E4B provider dopiero po potwierdzeniu fizycznej gotowości.

[Szczegółowy handoff implementacyjny](checkpoints/2026-11-04-edge-gallery-audio-implementation-handoff.md) i [plan modeli](checkpoints/2026-11-04-edge-gallery-audio-engine-selection-design.md).

## Zamrożona granica mediów

`CallMediaSessionCoordinator` i Samsung/Shizuku pod `privileged-helper/` stanowią wcześniej zweryfikowaną granicę RX/TX. Przed zmianami przeczytaj [PHASE2D_FREEZE](PHASE2D_FREEZE_2026-09-18.md). Nie utożsamiaj poprawności plików PCM/liczników z niezależnym odsłuchem z drugiej strony połączenia.

## Bezpieczeństwo

Jeden własny aktywny Call; realne uprawnienia i stan telefonu; konkretny, trwały, odwoływalny zakres grantu; ujawnienie faktów dopiero po `FactDisclosurePolicy`; bez dowolnych skutków modelu. STOP wygrywa z inferencją/TX/retry. Nie rozłączaj połączeń obcych. Nie zapisuj tożsamości i nagrań w GitHub ani zwykłych wynikach narzędzi. Wymagania: [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md), [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md).

## Stan i dowody

Architektura opisuje **kontrakt**, nie samą akceptację wdrożenia. Aktualny zakres PASS/OPEN jest w [HANDOFF](HANDOFF_NEXT_CHAT.md) i jedynym rejestrze [BLOCKERS](../BLOCKERS.md), a najnowsze fizyczne wyniki w [checkpoincie RAW live](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md). Historyczne checkpointy F-042/F-043 pozostają w archiwum; fakt wielu tur Gemmy i dobrego UX **nie** potwierdza zewnętrznego efektu CLIR.

## Adaptery M2–M4 — implementacja 2026-11-04

- M2 `ParakeetSpeechInputProvider`: oficjalne MediaPipe AudioTranscriber, 16-kHz PCM16→float, nieblokująca kolejka RX, natywne prewarm/segmenty/finish w osobnym wątku. Gotowość dla konkretnego urządzenia wymaga modelu i natywnego testu; Mac QA PASS, S22 OPEN.
- M3 `PiperAndroidTtsOutputProvider`: tylko jawny pakiet usługi systemowej Piper i offline głos z cechą `piperVoice`; pełna konwersja WAV→PCM16/16k pozostaje w `LocalTtsSpeechOutput`. Sam zewnętrzny silnik i jego głosy nie są dystrybuowane w AI Calls; Mac QA PASS, S22 OPEN.
- M4 `GemmaAudioDirectInputProvider`: odrębny eksperymentalny LiteRT `audioBackend=CPU` z danymi `Content.AudioBytes` i trasą PCM→transkrypcja; kod nie może zostać wybrany jako aktywny profil (`audio_direct_s22_unqualified`). Przed produkcją wymagany backend jednej rozmowy bez duplikacji pamięci modelu oraz realny natywny pomiar audio/tur/terminacji na S22. Test Mac/S22 QA_PENDING.
- Żaden adapter nie daje grantu, nie zmienia VAD/ACK, routingu Shizuku, STOP ani prawa do skutków. F-045/F-047/F-048 nadal OPEN.
