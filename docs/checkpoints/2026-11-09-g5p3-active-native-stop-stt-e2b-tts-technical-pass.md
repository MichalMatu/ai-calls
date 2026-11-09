# AI Calls — G5.3: aktywny natywny STOP STT / Gemma E2B / TTS — checkpoint 2026-11-09

## Decyzja
**G5.3 TECHNICAL_STOP_S22_PASS — VERIFIED_SCOPED**, lecz **całe G5.3 pozostaje OPEN** z powodu jakościowej oceny semantycznej i naturalności przez człowieka oraz niedomkniętych statystyk latencji. Źródło kodu przed checkpointem: `main@34adb1f183e612468354ad60088beb295b79c963`. Gemma 4 E2B pozostaje domyślna; Qwen laboratoryjnie. Brak rzeczywistych połączeń, zgód na skutki zewnętrzne, nowych uprawnień, wipe, zmian profilu i ingerencji w Samsung RX/TX. G6 to osobny etap.

## Dokładne wyniki
| Bramka | Local Agent task | Wynik |
| --- | --- | --- |
| Mac: natywny STOP Gemmy | `aicalls-g5p3-native-stop-mac-qa-20261109-v1` | DONE/0; kompilacja AndroidTest, pełny `verify_host.sh`, 371 Python, lint/audio i anti-friction PASS, clean |
| S22: rzeczywisty `sendMessage` / `cancelProcess` | `aicalls-g5p3-native-active-inference-stop-s22-20261109-v2` | DONE/0; AndroidTest 1/1 PASS, E2B GPU, native send active, cancel requested, brak stale callbacks, nowa konwersacja odzyskała działanie, Call IDLE |
| Mac: STOP Android STT/TTS | `aicalls-g5p3-native-stt-tts-stop-mac-qa-20261109-v1` | DONE/0; AndroidTest compile i pełny `verify_host.sh`, 371 Python, anti-friction, clean |
| S22: aktywne STT i TTS | `aicalls-g5p3-native-stt-tts-stop-s22-20261109-v1` | DONE/0; 2/2 AndroidTest, natywny recognizer czytał PCM przy anulowaniu, TTS anulowany po `onStart`; oba restart PASS, brak stale callbacks/PCM |

W logu wyłącznie metadane: `native_cancel_requested=true`, `cancel_return_ms=0`, `cancelled_status=error_LiteRtLmJniException` (natywny przerwany `sendMessage`, a nie udana odpowiedź), `fresh_conversation_recovered=true`, `late_callbacks=0`. Jest to dowód wysłania `cancelProcess` podczas rozpoczętego natywnego send, jego zakończenia i odzyskania sesji, a **nie** pomiar czasu zatrzymania każdego GPU kernela ani p95. Na STT `native_stt_restarted=true`, `stale_stt_callbacks=0`; na TTS `native_tts_restarted=true`, `stale_tts_pcm=0`, po restarcie `94070 B` PCM. S22 `SM-S906B`, dwie instalacje debug APK `-r -t` bez wipe podczas każdej bramki, stan Call IDLE przed/po, profil audio zachowany, Mac Git clean, brak narzędzi/Telecom.

## RED → GREEN, zachować historię
Pierwszy task S22 model `aicalls-g5p3-native-active-inference-stop-s22-20261109-v1` skończył `FAILED/81` **przed instalacją**, ponieważ nieprawidłowo podwójnie escapowany regex hostowego Python nie rozpoznawał `mCallState`. Odczytowe `aicalls-g5p3-idle-refusal-diagnostics-s22-20261109-v1` potwierdziło telefon i brak kampanii, ale powtórzyło błąd regex. Skorygowany task `...-v2` zachował identyczną bramkę IDLE i zaliczył wszystkie kroki; `F-161 VERIFIED_SCOPED`. Nie mylić błędu harnessu z aktywnym Call ani z błędem aplikacji. Istniejący G5.2 (3 tury + 5 adapterowych STOP) nie był ponawiany.

## Zakres implementacji
- `Gemma4LiteRtTextBackend.kt`: diagnostyczny `onNativeSendEntered`, `NativeModelTiming.nativeCancelRequested`, bez ujawniania promptów, treści rozmów czy rozszerzenia uprawnień.
- `AndroidG53NativeInferenceStopTest.kt`: realny E2B GPU, aktywny warm `sendMessage` → native cancel → odzyskanie modelu.
- `LocalTtsSpeechOutput.kt`: tylko diagnostyczny, opcjonalny observer `onSynthesisStarted` w istniejącym silniku TTS.
- `AndroidG53NativeSpeechStopTest.kt`: rzeczywista usługa on-device STT i Android TTS, aktywny STOP, brak stale events, restart. Jeden istniejący pipeline/porty, brak drugiego silnika i brak skutków.

## Pierwsza niezamknięta część G5.3
**HUMAN_SEMANTIC_AND_NATURALNESS_REVIEW = UNASSESSED**. Osiem istniejących wymiarów `LabDialogueReport`: TOPIC, MEMORY, CORRECTIONS, FACTUALITY, NATURALNESS, QUESTIONS, PROMISES, ENDING. Żaden nie otrzymuje PASS bez dowodu konkretnej tury od niezależnego recenzenta. Dotychczasowe G5.2 3 tury mierzyły transcript, approved text i PCM, lecz nie przygotowały prywatnego, odsłuchowego audytu jakości; nie porównuj automatycznej poprawności markera `wtor` z ludzką oceną naturalności. Następny jeden zakres: zebrać wyłącznie lokalnie (S22/Mac, bez GitHub/logcat) pełne próbki tekst+WAV i ocenić dialog tura po turze istniejącym `LabDialogueReport`; dopiero potem ewentualne cold/warm N≥3 i decyzja G5.3. G6 realne połączenia bez jawnego nowego grantu NOT_RUN.

Przepływ: GitHub Connector + Mac Local Agent + ChatGPT Linux Sandbox; bez Codex/Work/GitHub Actions, nowych branchy/force-push; komit `[skip ci]`.
