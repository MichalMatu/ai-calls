# AI Calls — jedyny aktywny plan

**Priorytet:** GSM na S22+; SIP dopiero po działającym GSM. Gałąź `feature/s8-no-shizuku`; `main` i stabilna aplikacja bez zmian. Stare checkpointy to archiwum, nie backlog.

## Stan projektu

Użytkownik potwierdza działanie aplikacji z GSM. Istnieje Samsung/Shizuku oraz STT → Gemma E2B → TTS; testy syntetyczne 2/4/6 tur PASS. Nie oznacza to jeszcze pełnej kwalifikacji GSM. PJSIP/JNI uruchomiono offline na S22+ z rollbackiem; SIP na żywo nieprzetestowane.

## Otwarte

**P0 — sekretarka GSM:** sprawdzić faktyczne RX rozmówcy → STT → Gemma → TTS → TX rozmówcy na istniejącej ścieżce Samsung/Shizuku; nie przepisywać działających modułów. Potwierdzić 4–6 wymian, powitanie, polskie godziny/daty, barge-in/STOP, zakończenie po „do widzenia”, nagrywanie i odsłuch/echo. Naprawiać błędy z runtime.

**P1 — proste lokalne SIP:** dopiero po P0, odrębnie od GSM.

## Następny sprint

1. **GSM:** sprawdzić obecną konfigurację, granty, tor Shizuku RX/TX i beztreściowe logi. Testy celowane; kontrolowane połączenie tylko za zgodą i przy IDLE. Naprawić najkrótszą drogą brakujące ogniwo.
2. **SIP:** uruchomić prostą lokalną centralę testową.
3. Odebrać połączenie przez PJSIP.
4. Połączyć PCM z istniejącym STT → Gemma → TTS.
5. Odesłać audio rozmówcy i poprawnie się rozłączyć.
6. Dopiero po MVP dopracować TLS/SRTP, obsługę błędów i bezpieczeństwo internetu.

**Zasady:** Existing Code First → mała zmiana → test celowany → runtime. Bez schedulerów, Actions, Codex/Work, nieautoryzowanych połączeń/instalacji, dodatkowych audytów. Nieszyfrowany RTP tylko w izolowanym lokalnym laboratorium, nigdy publicznie. Zachować rollback i prywatność.
