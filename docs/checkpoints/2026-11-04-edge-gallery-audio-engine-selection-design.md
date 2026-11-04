# AI Calls — wybieralne STT / bezpośrednie audio Gemma / TTS
Stan badań: 2026-11-04. **DESIGN ONLY — kod i APK niezmienione**. Wcześniejszy zamrożony wieloturowy RAW oraz grant/STOP pozostają wymaganym baseline. **Korekta właściciela:** F-049 było błędną kwalifikacją normalnego, dwuczęściowego powitania infolinii; nie rozwijać z tego osobnego zadania, nie zmieniać VAD ani neutralnego ACK z tego powodu.

## Podstawy źródłowe

Przejrzano pięć zrzutów `Screenshot_20261104_062649`, `062719`, `062731`, `062801`, `062808` właściciela z Google AI Edge Gallery. Widać `Try ASR & TTS today`, `XR Language Teacher`, `AI Chat`, `Agent Skills`, osobne wybory main/ASR/TTS: **ML Kit ASR (Basic)**, **Parakeet TDT 0.6B ASR (614.6 MB)**, **Kokoro 82M TTS (294.8 MB)**, **PocketTTS 100M (760.1 MB)**, a w modelach LLM m.in. **Gemma 4 E2B (2.6 GB), E4B (3.7 GB)**, modele Gemma 3n oraz inne. Zrzuty pokazują opcje i rozmiary UI; nie dowodzą jakości, zgodności językowej ani jednoczesnego działania trzech modeli na S22.

Wg potwierdzonych changelogów Android Edge Gallery **1.0.21 z 2026-10-28** dodało ASR/TTS, XR Language Teacher, Live Metrics HUD, lepsze zarządzanie kontekstem; **1.0.22 (również 2026-10-28)** włączyło Gemma 4 E2B w XR Language Teacher.
- https://github.com/google-ai-edge/gallery
- https://whatsnew.fyi/product/google-ai-edge-gallery

Dokumentacja komponentów:
- Parakeet 0.6B v3, MediaPipe `AudioTranscriber` — ~615 MB, 25 języków **z polskim**, opcjonalne częściowe wyniki, segmenty z timestampami i streaming PCM; `tasks-audio >=1.2.0`, przykład Android: https://developers.google.com/edge/mediapipe/solutions/audio/audio_transcriber/index oraz https://developers.google.com/edge/mediapipe/solutions/audio/audio_transcriber/android
- NVIDIA potwierdza `pl`: https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3 . Licencja modelu CC BY 4.0; atrybucja w aplikacji.
- Gemma 4 E2B/E4B rozumie wejście audio i może zwracać **tekst**; oficjalna karta ogranicza segment audio do **30 sekund**: https://gemma.ai/models/gemma-4/model-card/ ; istnieje przykład LiteRT-LM Android z 16-kHz PCM: https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert/qualcomm/gemma4/cpu_gpu . To **nie jest** automatyczny, potwierdzony ciągły duplex stream PCM→głos ani natywny TTS.
- MediaPipe `TextToSpeech`: `Kokoro-82M` (~295MB; GPU, Apache 2.0), `PocketTTS 100M` (~760MB; CPU, CC BY 4.0), API `AudioChunk` jako mono float32 24 kHz; **aktualne paczki Kokoro i PocketTTS nie obsługują `pl-PL`**, więc w polskiej kampanii nie mogą zastąpić obecnego TTS. https://developers.google.com/edge/mediapipe/solutions/audio/text_to_speech/index
- Potencjalny polski głos alternatywny: Piper `pl_PL` przez zgodny silnik Android/ONNX, z weryfikacją licencji konkretnej paczki i rzeczywistej jakości. Nie jest modelem pokazywanym na dostarczonych zrzutach.

## Obecny stan AI Calls (źródła 2026-11-04)

- `OnDeviceSpeechInput` używa Android `SpeechRecognizer.createOnDeviceSpeechRecognizer` z injected 16 kHz mono PCM16 przez `EXTRA_AUDIO_SOURCE`. Domyślny `pl-PL`.
- `LocalTtsSpeechOutput` używa platformowego Android `TextToSpeech`, wybierając głos offline w języku `pl-PL`; po syntezie zwraca 16-kHz mono PCM16.
- `Gemma4LiteRtTextBackend` `com.google.ai.edge.litertlm:litertlm-android:0.17.1` przyjmuje `generate(userText)`; `EngineConfig.audioBackend = null`. **Gemma obecnie NIE dostaje audio**, tylko tekst STT. Bezpośrednia ścieżka wymaga kompatybilnego multimodalnego runtime, audio encoder oraz próby na S22.
- `ChatRelayLiveCallProbe` obsługuje obecne STT final, VAD, quick ACK, Gemma, TTS i autoryzowany `AuthorizedPcmUplinkWriter`. Działający pipeline z 8/8 i 7/7 tur, live dock, grant i STOP **nie ruszać** przy wprowadzaniu alternatyw.

## Docelowa konfiguracja UI

Sekcja na górze `CAMPAIGN TOOLS` lub w odrębnych `Ustawieniach audio AI`, z grupami:

- **Wejście / rozpoznawanie rozmówcy (RX):**
  1. `Android STT (obecny, stabilny, PL)` — domyślnie.
  2. `Parakeet TDT 0.6B v3 (PL, ~615 MB)` — po instalacji modelu i realnym pass benchmarku.
  3. `Gemma 4 Audio Direct (eksperymentalny)` — audio PCM 16k → multimodalna Gemma → tekst odpowiedzi, z 30-s ograniczeniem segmentu, bez obietnicy natychmiastowego nieprzerwanego streamingu. Opcjonalna równoległa transkrypcja do UI, tylko jeżeli pamięć pozwala.
- **Model rozmowy:** `Gemma 4 E2B` (current pass) w pierwszym etapie; nowe E4B i inne modele osobno dopiero po sprawdzeniu pamięci/latencji i kompatybilności. Nie mieszać w jednej zmianie ASR, LLM i TTS.
- **Wyjście / mowa do rozmówcy (TX):**
  1. `Android TTS offline (pl-PL)` — domyślnie.
  2. `Piper pl-PL (research → opt-in po QA)` — polski, osobny runtime/ONNX i głos.
  3. `Kokoro 82M` i `PocketTTS 100M` — tylko dla zgodnych języków (np. EN); dla PL wyświetlić `brak pl-PL w aktualnym modelu` i nie oferować aktywacji.
  4. `Gemma output audio` — **nie wspierane** przez obecny E2B; wyjście modelu jest tekstem.
- Każda opcja ma `Pobierz model` / `Gotowy` / `Brak PL` / `Zbyt mało pamięci` / `Eksperymentalny` tylko na podstawie prawdziwej diagnostyki. Pokaż rozmiar, atrybucję, zgodność PL, głos, test offline i status aktywnej konfiguracji. Nie prezentować nierozwiniętych wyborów jako działających.

## Wymagane granice kodowe

```text
OWNED Telecom Call → frozen Samsung/Shizuku RX (PCM16, mono, 16k)
                    → SpeechInputProvider (Android | Parakeet)
                    → final transcript+timestamps → DialogBackend (Gemma)
                    └→ albo AudioDirectAdapter (experimental) → multimodal Gemma
         → OutputReleasePolicy + grant + STOP
         → SpeechOutputProvider (Android TTS | Piper pl_PL | other locale-gated)
         → PCM conversion (e.g. 24k float→16k PCM16, bounded) → frozen Authorized TX
```

- Preferencje per-grant/profile są nieautoryzujące; konfigurację **zamrozić przed prewarm i połączeniem**. Nie przełączać STT/TTS ani modeli w połowie aktywnego Call. Błędny wybór: jawny fail-closed lub bezpieczny, jawnie zakomunikowany fallback, bez cichej zamiany modelu i bez obejścia STOP.
- ASR/timestampy na wspólnym interfejsie `SpeechSegment(text, isFinal, startMs, endMs)`; istniejące mechanizmy VAD i zatwierdzania zakończonej tury zachować. **Nie** implementować postulowanego wcześniej nowego `InputTurnState.IVR_CONTINUING`, automatycznego oczekiwania na cały komunikat lub zmiany `trailingSilenceMs` na podstawie wycofanego F-049. Weryfikacja zachowania VAD to test braku regresji, a nie nowa funkcja.
- Nie prowadzić GPU Parakeet i GPU Gemma jednocześnie bez pomiaru: model Gemma E2B ≈2.6 GB + Parakeet ≈0.615 GB + OS/audio/KV/GPU. S22 musi przejść test pamięci, termiki i latencji; implementować ograniczone kolejki PCM i monitoring RTF, żadnego blokowania pump RX.
- Przechowywać model w app-specific storage, z hash/wersją i możliwością wycofania. Licencję każdej paczki sprawdzić osobno, zwłaszcza TTS.
- Podgląd czatu pokazuje osobno `źródło STT/AudioDirect`, finalne i częściowe STT, model, TTS/voice oraz czasy RX_end→ASR→LLM→TTS→TX. Nigdy nie zapisywać prywatnego audio/tekstu w GitHub.
- Prawa do faktów `F-048`, `FactDisclosurePolicy`, `F-045` output safety, grant, selective STOP i permission boundaries są **niezależne od wybranego modelu**.

## Sprinty / warunki wejścia

1. **Adaptery i neutralne menu**: refactor bez zmiany zachowania Android STT/TTS, unit + host full QA, exact S22 4-turn off-call, diff/weryfikacja zamrożonego audio. Żadnych nowych real-call testów od agenta.
2. **Parakeet PL RX**: download/provider, 16k PCM conversion, streaming/interim/final timestamps, porównanie WER i real-time factor na tych samych prywatnych lokalnych nagraniach (bez publikacji), regresja na zwykłych pauzach i dwuczęściowym powitaniu infolinii **bez zmiany dotychczasowej logiki tury**, test pamięci z Gemma i handoff do STT. Domyślna stara ścieżka pozostaje.
3. **Piper PL TX**: porównanie czasu do pierwszego PCM, dźwięku numerów/kodów/skrótów, łącznej latencji, normalizacja do frozen TX. Kokoro/Pocket tylko dla języków wspieranych.
4. **Gemma Audio Direct**: osobny eksperyment z istniejącym `.litertlm` i aktualnym LiteRT-LM, 16k PCM w pojedynczych segmentach do 30 s, przełączenie w menu wyłącznie po potwierdzeniu on-device na S22, zachowanie kontekstu wielu tur, analiza pamięci. Rozróżnić `audio understanding` od `ASR` i od audio-output.
5. **Porównawcza tabela QA**: tryb (STT/LLM/TTS), język, słowa błędnie rozpoznane, zgodność obecnego zachowania IVR (bez wymuszania poprawy F-049), EOS→pierwszy PCM, EOS→TX, czasy generacji, peak RSS/GPU, brak regresji STOP. Tylko po PASS użytkownik wybiera live test w swoim dokładnym grancie.

Status: **PLANNED, NOT IMPLEMENTED.** Brak nowego APK, testów i telefonów w ramach samego badania. Nie zmieniać checkpointu zamrożenia RAW z 06:35 i nie podawać opcji, jakby już działała.
