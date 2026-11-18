# AI Calls — checkpoint MILESTONE S22, 15.11.2026

**MILESTONE: stabilny, fizycznie przetestowany rdzeń audio i dialogu
off-call.** Repo MichalMatu/ai-calls, gałąź main.
Nie jest to jeszcze kwalifikacja Telecom / prawdziwej długiej rozmowy.
Źródła: AGENTS.md, docs/PROJECT.md oraz AUDIO_ROUTE_QUALITY.md.

## Potwierdzone na Samsungu S22

- S2: stały korpus 5 × 2, Piper/Gosia → Parakeet, 10/10 PASS,
  WER 5,77%, źródłowy SHA 7c08d554.
- S3: sześć kombinacji STT i TTS, 60/60 syntetycznych prób PASS,
  osobne WER i opóźnienia, SHA 5990b3b1.
- S4 + S6: sześć pełnych kombinacji z Gemma E2B, 18/18
  syntetycznych tur PASS; wiadomość, korekta dnia,
  pamięć i PCM16; SHA c9d0eda, 6c2f74c, bed4397.
- S5: STOP natywne off-call 5/5 PASS (STT, TTS, Gemma,
  spóźnione callbacki, UI), SHA eb60b7e.
- S7: dwa przebiegi świeży Engine / prewarm / hot,
  sześć generacji na przebieg, ten sam prompt i 69 znaków.
  Pierwszy przebieg wygenerował wszystkie odpowiedzi, ale
  miał błędną asercję REUSED; drugi: PASS 6/6, SHA e0baaf1.
  Cold-first średnio 15,74 s, po prewarm 8,12 s,
  prewarm gotowy po 5,96 s, hot median 1,50 s.
  REPAIRS=1 w pierwszej turze, REPAIRS=0 w kolejnych.
  Cold oznacza świeży Engine, nie zimny start Androida.
- S22 IDLE po testach; Test Lab nieaktywny, zapisany
  profil audio i granty niezmienione. Wybrany profil:
  Parakeet PL → Gemma 4 E2B → Piper Android TTS,
  głos pl_PL-gosia-medium. Bez połączeń i uninstall.
  Sprawdzony główny APK SHA-256:
  cf3cdc7e9752656c2b746e5e0734bf1dd39356de431a203eb2925d906440c1a7.

## Interpretacja i decyzja

Zainicjalizowany model po pierwszej inferencji odpowiadał
na krótki stały prompt w ~1,5 s. Sam prewarm obniżył
pierwszy czas o 48%, ale odpowiedź po samym załadowaniu
nadal trwała ~8 s (w obu próbach naprawa JSON).
Nie mieszać tych wyników z całym STT → LLM → TTS:
starsze 16–26 s obejmowało inne etapy, różne wypowiedzi
i bywało zawyżone startem.
Opcji stałego rezydenta w RAM nie wdrażano.
Nie zmieniano wybranego LLM ani silnika STT/TTS.

## Nadal otwarte: REAL CALL / HUMAN ACCEPTANCE

1. 4–6 wymian na prawdziwym przychodzącym połączeniu:
   powitanie podczas prewarm i faktyczny end-of-speech → TX.
2. Barge-in, STOP w czasie RX/model/TTS/TX oraz
   brak starego dźwięku po przerwaniu.
3. END_CALL po „do widzenia”, rzeczywiste rozłączenie
   bez kolejnego pytania, stabilny dialer i evidence.
4. Odsłuch, echo, hałas, długie rozmowy i daty/godziny.
5. Osobno porównać nowe LLM (Qwen) po tym checkpoint:
   fresh/prewarm/hot, RAM, jakość JSON i pamięć konwersacji.

## Dowody / wznowienie

Mac Local Agent: repo MichalMatu/local-agent, agent-control;
receipts S2–S7 wskazane w AUDIO_ROUTE_QUALITY.md.
Prywatne liczniki S7 na Macu w katalogu
~/Documents/AI-Calls/private-exports/audio-matrix/
(1c093662ed21-gemma-cold-warm.json,
e0baaf1eaa45-gemma-cold-warm-r2.json).
Nie kopiować transkryptów, WAV i numerów do Git.
Nowe prace: najpierw aktualny HEAD/main, potem wyłącznie
celowane testy i udokumentowany wynik. Bez schedulerów,
nieautoryzowanych połączeń, zmian uprawnień i masowego dial.
