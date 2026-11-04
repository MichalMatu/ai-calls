# AI Calls — prywatny podgląd dialogu na żywo (2026-11-04)

## Zakres

Na prośbę właściciela dodano dokowany **czat STT / Gemma / TTS-TX** na dole trzech ekranów: `MainActivity`, `CampaignToolActivity` i `AndroidCallControlsActivity` (panel dialera widoczny przez powiadomienie aktywnej rozmowy). Ustawienia i przyciski telefonu pozostają nad dokiem, w przewijanym panelu. Sam dok ma status, dymki obu stron, znaczniki czasu, przewijanie, zwijanie i **Wyczyść widok**.

- `LiveCallChatFeed` przechowuje wyłącznie w procesie aplikacji ostatnią rozmowę; nie używa `SharedPreferences`, dysku, zewnętrznego serwera, intents, broadcastów, notyfikacji ani logcat. Działa niezależnie od dotychczasowego, opcjonalnie autoryzowanego prywatnego zapisu WAV/JSONL. Brak tekstu w repozytorium i wynikach Host Ops.
- `ChatRelayLiveCallProbe.Run`: tylko po sprawdzeniu aktywnego uprawnienia zakłada sesję z tokenem; publikuje finalny tekst STT rozmówcy, zatwierdzoną odpowiedź AI, etapy nasłuchu i syntezy, a status TX po powrocie z rzeczywistego `AuthorizedPcmUplinkWriter.write`. `TX: wysłano lokalnie` oznacza zaakceptowane bajty w lokalnym uplinku, **nie dowód słyszalności u operatora**.
- Źródło modelu jest jawne: `GEMMA RAW` lub `AI SKRYPT + GEMMA`. Żadnego parsera JSON ani zmiany działającego backendu Gemma RAW, autoryzacji, słownika faktów, VAD, Samsung media, wykonywania efektów, dialera czy STOP.
- Callbacki mają identyfikator generacji; spóźniony callback po zakończeniu lub podczas kolejnej rozmowy nie może trafić do nowej sesji. Czat wyświetla *ostatnią zakończoną* rozmowę do kolejnego startu lub lokalnego wyczyszczenia, bez trwałego zapisu.
- `LiveCallChatPanel` sprawdza snapshot tylko podczas podłączenia widoku, aktualizuje UI co 450 ms i nie wysyła komend do kampanii. Auto-scroll działa tylko, gdy użytkownik nie przewija wstecz.
- Testy `LiveCallChatFeedTest`: kolejność STT/AI i pełny TX, izolacja spóźnionych aktualizacji oraz czyszczenie tylko widoku.

## Jakość i dalsza akceptacja

Kod funkcji w komicie `268be1f8f13502c7202822438b501706e0fcb0e1`. Zlecone pełne Mac Local Agent QA `aicalls-20261104-live-chat-dock-host-qa-v1` na przypiętym SHA. **Na czas checkpointu wynik PENDING / QA niepotwierdzone**, nie należy mówić, że APK jest na S22, dopóki fizyczny test odbiorczy nie zostanie potwierdzony.

Po zielonym QA: przy exact S22, IDLE, brak aktywnej kampanii i retry, rola dialera nadal przyznana, zrobić bezpieczną aktualizację APK, po czym sprawdzić, czy 3 panele pokazują placeholder i status bez uruchamiania połączenia. Pełna walidacja tekstu po obu stronach wymaga dopiero kolejnej **właścicielskiej** autoryzowanej rozmowy. Zmiana nie poszerza zakresu grantu i nie uruchamia żadnego połączenia.

## Mac QA — terminalny odbiór

`aicalls-20261104-live-chat-dock-host-qa-v1` **DONE/0**, exact source `268be1f8f13502c7202822438b501706e0fcb0e1`. `QA_EXIT=0`, 3 × `BUILD SUCCESSFUL` (1m33s / 2m28s / 43s), `Ran 248 tests` / `OK`, `host_quality_gate_green=true`, `ANTI_FRICTION=PASS`. Bez urządzenia, połączeń, prywatnych eksportów. Wcześniejsze `PENDING` jest stanem historycznym.

Zlecono `aicalls-20261104-live-chat-dock-s22-offcall-install-v1`: exact S22, 2 × IDLE, rola dialera, brak aktywnej kampanii i retry, pref fingerprint przed/po; tylko przy spełnieniu warunków bezpieczna instalacja APK + 4 syntetyczne tury Gemmy w AndroidTest, bez połączenia. **Wynik tego zadania jeszcze nieodebrany na czas aktualizacji.**
