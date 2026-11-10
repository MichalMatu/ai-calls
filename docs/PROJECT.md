# AI Calls — MVP FIRST

**Priorytet:** uruchomienie pełnego asystenta telefonicznego na Samsung S22+, nie G6.5 hardening. Istniejące moduły, bezpieczeństwo Call i autoryzacja są zamrożonymi fundamentami. Błędy nieblokujące MVP odłóż.

## Zamknięcie QA G6.4
- Mac host full: Gradle/JVM/lint, 409 Python, audio quality i preflight PASS dla 04a72f2; receipt: aicalls-g64-freeze-fullqa-20261110-v1.
- S22+ exact device, Android IDLE, rola dialera, APK aplikacji i testów PASS. Na **wcześniej zainstalowanym** APK 4/4 instrumented off-call PASS: takeover i stale PCM, RX archive, syntetyczny WAV playback, STOP; receipt: aicalls-g64-s22-installed-instrumentation-physical-offcall-20261110-v1.
- Bieżącej wersji APK nie porównano z nowym HEAD. Nie przeprowadzono realnych rozmów, barge-in, live resume, pełnego TX/RX na połączeniu ani rzeczywistej rezerwacji. **PHYSICAL_OPEN**. Nie nazywaj tego real-call DEVICE_VERIFIED.

## Otwarte — MVP FIRST (G6.1 i pozostałe P0/P1)
| Funkcja | Status | Zweryfikowane / blokada |
| --- | --- | --- |
| Automatyczne INCOMING zgodnie z polityką | HOST_TESTED | Exact Call, SIM, AI/MANUAL, takeover w koordynatorze; żywa rozmowa na S22 **BLOCKED** do zgody |
| Gemma + STT/TTS podczas rozmowy | HOST_TESTED | Native off-call i produkcyjny runtime są; słyszalne RX/TX i barge-in na żywo **BLOCKED** |
| Outgoing po autoryzacji | HOST_TESTED | BOOK_APPOINTMENT: exact E.164, wybrana SIM, scoped grant, prewarm Gemma i istniejący owner Android Telecom; realny dial S22 **PHYSICAL_OPEN** |
| Wybór danych IdentityVault | HOST_TESTED | UI pozwala wybrać FIRST_NAME/LAST_NAME/PHONE/EMAIL, scoped reader sprawdza task/target/generation i zgodę; mówione wartości na S22 **PHYSICAL_OPEN** |
| Wizyta, warunki i wynik do oceny | HOST_TESTED | CallTask, parser terminu, scena Gemma/STT/TTS, UI zakresu/warunków i REVIEW_REQUIRED; syntetyczne E2E PASS; brak realnego providera i próby live **PHYSICAL_OPEN** |
| Natychmiastowy STOP / TAKE OVER i resume | HOST_TESTED / DEVICE_VERIFIED off-call | Symulowany STOP i TAKE OVER PASS; na wcześniejszym APK fizyczne off-call STOP/stale PCM PASS; przejęcie rozmowy live **PHYSICAL_OPEN** |

Status DEVICE_VERIFIED w ostatnim wierszu dotyczy wyłącznie wymienionego testu off-call. Żadna cała funkcja nie ma jeszcze ukończonego odbioru fizycznego. BOOKED u dostawcy syntetycznego nigdy nie stanowi dowodu prawdziwej rezerwacji.

## Zamrożony host MVP
- main@044427b01150ef542852f2ec42a19463f89e1feb: pełny test-profile.sh full **PASS** (Gradle/JVM, lint, 409 Python, audio/secret gates), :app:assembleDebug **PASS**, czysty Git.
- APK wyłącznie na Macu: ~/agent-workspace/artifacts/ai-calls/ai-calls-mvp-first-044427b01150.apk, 234 MB, SHA-256 437e35f0a445dfe1cf4f48eaf6a3f0cf5a663e9d4998db03be7275edd0c0d232; **nie zainstalowano**. Kompilacja to nie odbiór urządzenia.

## Następny sprint — MVP FIRST (kod, nie audyt)
1. Fizyczny odbiór z osobną zgodą na exact APK, instalację, testowe numery/SIM i zakres połączeń. Wszystkie live-call funkcje pozostają **PHYSICAL_OPEN**.
2. Po próbach poprawić wyłącznie usterki blokujące MVP. Nie uruchamiać G6.5/G7, niepotrzebnych refaktorów ani audytu.

## Próby S22+ — dokładny zakres PHYSICAL_OPEN
Dopiero po osobnej, konkretnej zgodzie na zakres, numer testowy, SIM, uczestników i instalację nowego APK; zweryfikuj exact S22, Shizuku, rolę dialera, uprawnienia i IDLE:
1. **INCOMING:** kontrolowany numer dzwoni, polityka AI odbiera automatycznie, MANUAL nie odbiera i obce Call pozostają nietknięte.
2. **Audio:** rozmówca słyszy lokalne TTS, STT rozumie jego odpowiedź, Gemma odpowiada, przerwanie podczas TX ucina stare audio.
3. **Human:** STOP i TAKE OVER w tym samym Call bez globalnego hangup; jawny resume, brak spóźnionych PCM; poprawna historia i legalny playback po zgodzie.
4. **OUTGOING:** tylko dokładny zatwierdzony numer i SIM; brak niezamierzonego redial; faktyczny wynik odróżniony od UNKNOWN.
5. **BOOK_APPOINTMENT:** uzgodnione fikcyjne dane i pola, pytania o termin/warunki, prezentacja propozycji do zatwierdzenia. Bez rzeczywistego potwierdzenia rezerwacji bez dodatkowego grantu i niezależnego dowodu providera.

## Odbiór MVP
MVP można ogłosić gotowym dopiero po udanych, kontrolowanych próbach fizycznych INCOMING, OUTGOING, dwukierunkowego audio, STOP/TAKE OVER i scenariusza wizyty. Do tego czasu: **MVP BLOCKED / PHYSICAL_OPEN**. Zasady: [Local Agent](operations/LOCAL_AGENT.md), [Device](operations/DEVICE.md), [Security](architecture/SECURITY.md), [Development](operations/DEVELOPMENT.md).
