# AI Calls — G5.1 fizyczny off-call STT→E2B+Skills→TTS (2026-11-09)

## Wynik i kod

**`G5.1=S22_OFFCALL_SINGLE_TURN_AUDIO_PASS`** — jeden prawdziwy przepływ audio na fizycznym Samsungu S22 (`SM-S906B`, `RFCT70L7E8J`), w syntetycznym scenariuszu bez aktywnego połączenia. G4.1–G4.3 pozostają ukończone (Lab-only), G3 `KEEP_E2B` niezmieniony. Source po dodaniu testu `main@0b3b4bb9329cee45da53a2d696d4f57fe777cf41`. Nie modyfikowano produkcyjnej ścieżki Samsung RX/TX, grantu ani modelu użytkownika.

**Nowy test `app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/localspeech/AndroidG51SpeechSkillsOffcallE2ETest.kt`** używa wyłącznie istniejących portów: `LocalTtsSpeechOutput` generuje kontrolowane polskie, syntetyczne 16k mono PCM, `SpeechInputProvider` z zapisanej konfiguracji dostarcza końcowy transkrypt, `AndroidSkillLabSessionFactory` tworzy odizolowaną Gemmę E2B z `SkillLoadingMode.CONTEXTUAL`, `ConversationReplyBackend` dekoduje typowaną odpowiedź, `LabToolProposalPolicy` tylko klasyfikuje propozycję, `LocalSpeechTextPipeline` przekazuje tekst przez `TextOutputApprovalPolicy` i uruchamia `SpeechOutputProvider`. Wynik TTS przechwytywany jest jako PCM, nie odtwarzany przez głośnik ani nie wysyłany do Telecom/TX.

Zabezpieczenia: `Call IDLE` sprawdzone na Mac przez ADB przed instalacją, przed instrumentacją i po teście; oba debug APK `adb install -r -t` bez wipe. Test nie nagrywa mikrofonu, nie pobiera danych właściciela/IdentityVault ani nie zapisuje WAV/transkryptu do repo lub logcat. `LabToolProposalPolicy.executed=false`, `pipeline.cancel()` i `close()` w `finally`, stan profilu po próbie taki sam. Wybrany STT/output są zgodne z aktualnie zapisanym profilem, lecz log finalny **nie podaje konkretnych ID STT/TTS**; nie zgadywać, czy był to Android STT czy Parakeet, Android TTS czy Piper. Wymagany pełny UI/realny audio output to dalsze bramki.

## Dwa dokładne task receipts

| Bramka | Task/stan | Odbiór |
| --- | --- | --- |
| Mac source/test/full | `aicalls-g5p1-stt-e2b-skills-tts-mac-qa-20261109-v1` **DONE/0**, 4/4 komend exit 0 | Checkout `main@0b3b4bb9329cee45da53a2d696d4f57fe777cf41` clean, `:app:assembleDebugAndroidTest` i `:app:assembleDebug` build, `bash scripts/verify_host.sh` green (371 Python, JVM/lint/audio), anti-friction, końcowe clean |
| Fizyczny S22 | `aicalls-g5p1-speech-skills-androidstt-e2b-tts-s22-20261109-v1` **DONE/0**, 4/4 komend exit 0 | `G51_DEVICE_CALL_IDLE_GATE=PASS`, instalacja dwóch APK bez wipe, `am instrument` **1/1 PASS**, urządzenie `Call IDLE` na końcu, profil niezmieniony, clean Git |

**S22 metryki z jednej próby** (monotonicznie od początku testowego uruchomienia potoku, nie z osobnego telephony EOS):

| Punkt | Czas narastający |
| --- | ---: |
| Otrzymano końcowy transkrypt STT | **4 622 ms** |
| Zatwierdzono wypowiedź z Gemmy | **21 680 ms** |
| Wygenerowano kompletny TTS PCM | **23 574 ms** |

Dodatkowo: `skills_turns=1`, `stt=true`, `approved=true`, `tts_pcm_bytes=236416`, `proposal_present=false`, `tool_executed=false`, `stop=true`, `offcall=true`. PCM16 16k mono, długość niezerowa i poprawne wyrównanie próbek.

**Granice dowodu:** jeden turn/cold pipeline, syntetyczne wypowiedziane zdanie, brak testu pamięci audio między turami, brak przerywania podczas STT/model/TTS, brak rzeczywistego ludzkiego rozmówcy, brak testu multi-accent, brak statystyki N≥3/p95 i brak telefonicznego TX/słyszalności. `STOP` oznacza końcowe anulowanie i zamknięcie po turze, a **nie** fizyczny dowód przerwania podczas syntezy.

## Pierwszy kolejny zakres G5.2

1. Zre-użyć `LocalSpeechTextPipeline` i `AndroidSkillLabSessionFactory` do **2+ tur rzeczywistego audio**, skorygowania informacji oraz zmiany intencji/Skills z zachowaną pamięcią (PIN tylko fikcyjny), bez nowego runnera.
2. Oddzielnie przetestować STOP i late callbacks w każdej fazie (STT pending, native model pending, TTS PCM pending); kontrolować, że nie zostanie wyemitowane zatwierdzone audio po STOP. Użyć istniejących `LocalSpeechGenerationGate`, `ConversationReplyBackend` i typowanych portów.
3. Raportować czasy STT, generacji i TTS osobno, cold/warm; zapewnić brak surowego dialogu i audio w Git. Testować wybrany backend i lokalny głos bez niejawnego fallbacku; błędy urządzenia zachować jako RED.
4. Potem G5.3 human semantic/naturalness review jakości pełnej rozmowy i G5.4 ewentualne audio EOS→TX dopiero z konkretnym autoryzowanym Call w osobnej G6 — nie mieszać telefonii do Lab.
5. G6 realny Call INCOMING/OUTGOING wymaga dokładnego grantu i osobnego fizycznego dowodu. G7 UX pozostaje później.

Reguły: GitHub Connector + wskazany Mac Local Agent, ChatGPT Sandbox, bez Codex, Work i GitHub Actions (nawet statusu), `main`, `[skip ci]`, bez force-push, nowych gałęzi, ujawniania danych i nieautoryzowanych połączeń.
