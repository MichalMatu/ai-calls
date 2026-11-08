# AI Calls — pierwsze funkcjonalne testy offline na S22 (2026-11-08)

**Źródło:** `main@30290340942d4cb4dc115c7fe69a589c5857a874`, repo `MichalMatu/ai-calls`, fizyczny S22 `SM-S906B` (identyfikator urządzenia i uprawnienia pozostają w prywatnym środowisku). Host: `macbook-michal`, GitHub Connector + Mac Local Agent; bez Codex, Work, Actions i rzeczywistych połączeń.

## 1. Prawdziwy lokalny dialog i STOP (nie telefon)
- **Receipt:** `aicalls-functional-real-offline-two-turn-stop-s22-20261108-v1`, `done`, wszystkie komendy exit=0; preflight i postflight `IDLE`.
- Uruchomiono istniejący `AndroidSkillLabRealOfflineTest.realTwoTurnModelAndStopStayOffline`: wybrany model lokalny miał stan READY, wygenerował **dwie tury syntetycznej rozmowy** i przeszedł asercje postępu, przewinięcia ostatniej tury, ponownego START, przerwania STOP, statusu i banneru. Test ma `OK (1 test)` i `FUNCTIONAL_OFFLINE_TWO_TURN_STOP=PASS`.
- Cztery screenshoty prywatne na Mac: `~/Library/Application Support/AI-Calls/functional/2026-11-08-lab-offline/AI-Calls-S22-Functional-Offline-Lab-private.zip`; bez przesyłania surowych tekstów modelu do GitHub.
- Nie sprawdzono przez tę próbę prawdziwego rozmówcy, mikrofonu, odebranego audio/TX, własności Android Call ani skutku zewnętrznego.

## 2. Polski offline TTS → PCM → Parakeet STT
- **Receipt:** `aicalls-functional-polish-tts-parakeet-offcall-s22-20261108-v1`, `done`, wszystkie komendy exit=0, `FUNCTIONAL_POLISH_TTS_PARAKEET=PASS`, S22 IDLE przed i po.
- `AndroidParakeetPolishRoundTripTest.recognizesSynthesizedPolishSpeechWithLexicalOverlap`: offline TTS generuje testową polską frazę, Parakeet otrzymuje PCM16 i zwraca niepusty transkrypt ze zgodnością co najmniej dwóch elementów leksykalnych. Nie włączono mikrofonu, Telecom ani pobierania modelu, brak danych użytkownika.
- To **nie** jest jeszcze pomiar jakości na fizycznym RX/TX; nie podawać WER ani czasu, jeżeli receipt nie zawiera wartości liczbowych.

## Decyzja
**FUNKCJONALNY OFFCALL: PASS dla tych dwóch scenariuszy.** UX-11 CLOSED. Następna faza to osobno testowa macierz STT na głosie/testowych nagraniach, TTS/głośność i latencja, run-config/Skills/STOP/recovery, a następnie po konkretnym upoważnieniu **realny CALL** i niezależny dowód skutku. Wciąż otwarte F-080/F-122/F-123/F-124 oraz zgodność pełnego archiwum audio. Nie powtarzać zielonych testów tylko dla większej liczby.
