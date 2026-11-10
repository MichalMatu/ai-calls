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
| Outgoing po autoryzacji | HOST_TESTED | Existing grant + Telecom na ścieżce CLIR; generic BOOK_APPOINTMENT target nie jest podłączony do startu kampanii, realny dial **BLOCKED** |
| Wybór danych IdentityVault | HOST_TESTED | Scoped reader, podwójne sprawdzenie live authority, PHONE w CLIR; wizyty i inne pola bez app-owned live binding **BLOCKED** |
| Wizyta, warunki i wynik do oceny | HOST_TESTED | CallPlan/TaskGraph + synthetic booking; REVIEW_ONLY bez hold/commit i odczyt pending proposal z LocalTextCallSession PASS. Nadal brak live kampanii, UI oceny i prawdziwego providera: **BLOCKED** |
| Natychmiastowy STOP / TAKE OVER i resume | DEVICE_VERIFIED | Tylko off-call STOP/stale PCM na zainstalowanym APK; przejęcie/resume bez rozłączenia realnego Call **BLOCKED** |

Status DEVICE_VERIFIED w ostatnim wierszu dotyczy wyłącznie wymienionego testu off-call. Żadna cała funkcja nie ma jeszcze ukończonego odbioru fizycznego. BOOKED u dostawcy syntetycznego nigdy nie stanowi dowodu prawdziwej rezerwacji.

## Następny sprint — MVP FIRST (kod, nie audyt)
1. **Existing Code First:** zintegruj generic użytkownikowy BOOK_APPOINTMENT: wybrany cel/SIM, exact grant, prewarm Gemma/STT/TTS, owned Telecom, CallTask/CallPlan/LocalTextCallSession, IdentityVault i typed outcome. Nie zastępuj sprawdzonego CLIR ani transportu Samsung/Shizuku.
2. W aplikacji pokaż pending appointment/warunki do weryfikacji. Nigdy nie wykonuj realnego confirm na podstawie samego transkryptu lub synthetic receipt.
3. Syntetyczne E2E: świadome przyjęcie zadania, zgodność target/SIM, mock ACTIVE Call, odpowiedź, oferta, walidacja, ograniczone fakty, REVIEW_REQUIRED, STOP/TAKE OVER i odmowy przy niezgodności. Po kodzie tylko testy celowane i zmieniony zakres; pełny host QA po funkcjonalnym freeze.
4. Nie rozpoczynaj G6.5, G7 ani dalszych optymalizacji przed działającym MVP.

## Próby S22+ — dokładny zakres PHYSICAL_OPEN
Dopiero po osobnej, konkretnej zgodzie na zakres, numer testowy, SIM, uczestników i instalację nowego APK; zweryfikuj exact S22, Shizuku, rolę dialera, uprawnienia i IDLE:
1. **INCOMING:** kontrolowany numer dzwoni, polityka AI odbiera automatycznie, MANUAL nie odbiera i obce Call pozostają nietknięte.
2. **Audio:** rozmówca słyszy lokalne TTS, STT rozumie jego odpowiedź, Gemma odpowiada, przerwanie podczas TX ucina stare audio.
3. **Human:** STOP i TAKE OVER w tym samym Call bez globalnego hangup; jawny resume, brak spóźnionych PCM; poprawna historia i legalny playback po zgodzie.
4. **OUTGOING:** tylko dokładny zatwierdzony numer i SIM; brak niezamierzonego redial; faktyczny wynik odróżniony od UNKNOWN.
5. **BOOK_APPOINTMENT:** uzgodnione fikcyjne dane i pola, pytania o termin/warunki, prezentacja propozycji do zatwierdzenia. Bez rzeczywistego potwierdzenia rezerwacji bez dodatkowego grantu i niezależnego dowodu providera.

## Odbiór MVP
MVP można ogłosić gotowym dopiero po udanych, kontrolowanych próbach fizycznych INCOMING, OUTGOING, dwukierunkowego audio, STOP/TAKE OVER i scenariusza wizyty. Do tego czasu: **MVP BLOCKED / PHYSICAL_OPEN**. Zasady: [Local Agent](operations/LOCAL_AGENT.md), [Device](operations/DEVICE.md), [Security](architecture/SECURITY.md), [Development](operations/DEVELOPMENT.md).
