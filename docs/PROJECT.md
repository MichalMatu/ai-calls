# AI Calls — MVP FIRST

**Priorytet:** uruchomienie pełnego asystenta telefonicznego na Samsung S22+, nie G6.5 hardening. Istniejące moduły, bezpieczeństwo Call i autoryzacja są zamrożonymi fundamentami. Błędy nieblokujące MVP odłóż.

## Zamknięcie QA G6.4
- Mac host full: Gradle/JVM/lint, 409 Python, audio quality i preflight PASS dla 04a72f2; receipt: aicalls-g64-freeze-fullqa-20261110-v1.
- S22+ exact device, Android IDLE, rola dialera, APK aplikacji i testów PASS. Na **wcześniej zainstalowanym** APK 4/4 instrumented off-call PASS: takeover i stale PCM, RX archive, syntetyczny WAV playback, STOP; receipt: aicalls-g64-s22-installed-instrumentation-physical-offcall-20261110-v1.
- G6.4 off-call PASS. Jeden autoryzowany realny OUTGOING S22 uruchomiony; bez propozycji wizyty. Live barge-in, takeover i rezerwacja nadal **PHYSICAL_OPEN**.

## Otwarte — MVP FIRST (G6.1 i pozostałe P0/P1)
| Funkcja | Status | Zweryfikowane / blokada |
| --- | --- | --- |
| Automatyczne INCOMING zgodnie z polityką | HOST_TESTED | Exact Call, SIM, AI/MANUAL, takeover w koordynatorze; żywa rozmowa na S22 **BLOCKED** do zgody |
| Gemma + STT/TTS podczas rozmowy | DEVICE_OBSERVED: częściowo | W pierwszym OUTGOING: STT 43 znaki, 1 tura, RX 151040 B / TX 202622 B; jakość u rozmówcy i barge-in nadal **PHYSICAL_OPEN** |
| Outgoing po autoryzacji | DEVICE_VERIFIED: real dial | Na S22+ po jednym autoryzowanym START Telecom potwierdził aktywne połączenie; odbiór i jakość audio nadal **PHYSICAL_OPEN** |
| Wybór danych IdentityVault | HOST_TESTED | UI pozwala wybrać FIRST_NAME/LAST_NAME/PHONE/EMAIL, scoped reader sprawdza task/target/generation i zgodę; mówione wartości na S22 **PHYSICAL_OPEN** |
| Wizyta, warunki i wynik do oceny | 1 LIVE, REVIEW brak | Druga tura RX przerwana po 55280 ms (owned_call_ended) bez transkryptu; 20s RX segmentacja wdrożona, ponowna weryfikacja live **PHYSICAL_OPEN** |
| Natychmiastowy STOP / TAKE OVER i resume | HOST_TESTED / DEVICE_VERIFIED off-call | Symulowany STOP i TAKE OVER PASS; na wcześniejszym APK fizyczne off-call STOP/stale PCM PASS; przejęcie rozmowy live **PHYSICAL_OPEN** |

Pierwszy OUTGOING potwierdza częściowy RX/TX i jeden turn, nie potwierdza zrozumiałości u rozmówcy ani końcowej rezerwacji. Pełne live QA nadal otwarte.

Automatyczne, prywatne archiwum STT/zatwierdzonych odpowiedzi/diagnostyki na każdej autoryzowanej sesji, bez UI click i bez WAV: **HOST_TESTED** (`app` focused + changed QA, anti-friction i docs PASS; Mac `2e7d931a`). Audio WAV nadal wymaga osobnego opt-in. **S22 PHYSICAL_OPEN**: zmian jeszcze nie zainstalowano. Pierwszego transkryptu EMC ten fix wstecznie nie odtwarza; STT nieukończonej tury pozostaje otwartym ryzykiem.

## Zamrożony host MVP
- main@044427b01150ef542852f2ec42a19463f89e1feb: pełny test-profile.sh full **PASS** (Gradle/JVM, lint, 409 Python, audio/secret gates), :app:assembleDebug **PASS**, czysty Git.
- APK na Macu: ~/agent-workspace/artifacts/ai-calls/ai-calls-mvp-first-044427b01150.apk; SHA-256 437e35f0a445dfe1cf4f48eaf6a3f0cf5a663e9d4998db03be7275edd0c0d232. Exact bytes zainstalowane na S22+ przez adb install -r bez wipe; podpis, wersja, dialer i dane zgodne. Off-call STT/TTS/Gemma 3/3 PASS, status bridge PASS, hashe ustawień bez zmian.

- S22+ main@a2b4a32c: APK SHA-256 da06dcc; install-r bez wipe, sejf/ustawienia/podpis PASS; call-status pokazuje bezpieczne metryki zakończonej sesji.

## Następny sprint — MVP FIRST (kod, nie audyt)
1. Pierwszy OUTGOING: owned_call_ended, 1 tura STT/TTS, druga RX 55s bez końca; 20s limit segmentu wizyty wdrożony i przetestowany syntetycznie. Potrzebna nowa jawna zgoda na próbę live.
2. Po próbach poprawić wyłącznie usterki blokujące MVP. Nie uruchamiać G6.5/G7, niepotrzebnych refaktorów ani audytu.

## Próby S22+ — dokładny zakres PHYSICAL_OPEN
Przed live-call: osobna zgoda na numer, SIM, uczestnika i zakres; zweryfikuj ponownie S22, Shizuku, dialer, uprawnienia i IDLE:
1. **INCOMING:** kontrolowany numer dzwoni, polityka AI odbiera automatycznie, MANUAL nie odbiera i obce Call pozostają nietknięte.
2. **Audio:** rozmówca słyszy lokalne TTS, STT rozumie jego odpowiedź, Gemma odpowiada, przerwanie podczas TX ucina stare audio.
3. **Human:** STOP i TAKE OVER w tym samym Call bez globalnego hangup; jawny resume, brak spóźnionych PCM; poprawna historia i legalny playback po zgodzie.
4. **OUTGOING:** tylko dokładny zatwierdzony numer i SIM; brak niezamierzonego redial; faktyczny wynik odróżniony od UNKNOWN.
5. **BOOK_APPOINTMENT:** uzgodnione fikcyjne dane i pola, pytania o termin/warunki, prezentacja propozycji do zatwierdzenia. Bez rzeczywistego potwierdzenia rezerwacji bez dodatkowego grantu i niezależnego dowodu providera.

## Odbiór MVP
MVP można ogłosić gotowym dopiero po udanych, kontrolowanych próbach fizycznych INCOMING, OUTGOING, dwukierunkowego audio, STOP/TAKE OVER i scenariusza wizyty. Do tego czasu: **MVP BLOCKED / PHYSICAL_OPEN**. Zasady: [Local Agent](operations/LOCAL_AGENT.md), [Device](operations/DEVICE.md), [Security](architecture/SECURITY.md), [Development](operations/DEVELOPMENT.md).
