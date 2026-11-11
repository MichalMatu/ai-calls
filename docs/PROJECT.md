# AI Calls — MVP FIRST

**Priorytet:** uruchomienie pełnego asystenta telefonicznego na Samsung S22+, nie G6.5 hardening. Istniejące moduły, bezpieczeństwo Call i autoryzacja są zamrożonymi fundamentami. Błędy nieblokujące MVP odłóż.

## Zamknięcie QA G6.4
- Mac host full: Gradle/JVM/lint, 409 Python, audio quality i preflight PASS dla 04a72f2; receipt: aicalls-g64-freeze-fullqa-20261110-v1.
- S22+ exact device, Android IDLE, rola dialera, APK aplikacji i testów PASS. Na **wcześniej zainstalowanym** APK 4/4 instrumented off-call PASS: takeover i stale PCM, RX archive, syntetyczny WAV playback, STOP; receipt: aicalls-g64-s22-installed-instrumentation-physical-offcall-20261110-v1.
- G6.4 off-call PASS. Jeden autoryzowany realny OUTGOING S22 uruchomiony; bez propozycji wizyty. Live barge-in, takeover i rezerwacja nadal **PHYSICAL_OPEN**.

## Interfejs użytkownika — uproszczenie
- **Start / Rozmowy / Agent AI / Ustawienia** — cztery zakładki. Start zawiera bezpośredni dostęp do istniejącej rozmowy **w sprawie wizyty**, bieżący status, STOP i prywatny podgląd rozmowy; nie sugeruje ogólnego scenariusza, którego nie wdrożono.
- Usunięto dawny ekran Kampanie i martwe napisy zasobów; CLIR/start skryptu, nadzorowany test audio oraz diagnostyczne karty nie mają już konsumenckich przycisków. Modele i silniki są pod Ustawieniami, laboratorium wyłącznie w narzędziach developerskich. Wewnętrzne mechanizmy grantów, dokładnego Call, STOP, STT i archiwum zachowane.
- W formularzu wizyty uprawnienia Androida i wybór dialera mogą kontynuować **ten sam wyraźnie rozpoczęty przez użytkownika** zamiar, bez ponownego naciskania Start. Odmowa nie rozpoczyna połączenia.
- Host: anti-friction i fokus NoArtificialCampaignBlockersContractTest PASS, kompilacja compileDebugAndroidTestKotlin PASS. Brak instalacji nowego UI na S22+ i brak nowego połączenia; fizyczna akceptacja **PHYSICAL_OPEN**.

- **Sekretarka (UX):** dwa tony naturalny/formalny i własne instrukcje bez nazywania profilu. Ustawienia dotyczą tylko incoming; stare presety i historia wersji są usuwane podczas migracji szyfrowanego zapisu; aktywne własne instrukcje i ton sekretarki są zachowywane. Wymagana fizyczna kontrola UI S22+ po instalacji.

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

Automatyczne, prywatne archiwum STT/zatwierdzonych odpowiedzi/diagnostyki na każdej autoryzowanej sesji, bez UI click i bez WAV: **HOST_TESTED** (`app` focused + changed QA, anti-friction i docs PASS; Mac `2e7d931a`). Audio WAV nadal wymaga osobnego opt-in. **S22: APK z `f42deb9` zainstalowane bez kasowania danych, host QA i RX endpoint unit PASS; live STT PHYSICAL_OPEN**. Pierwszego transkryptu EMC ten fix wstecznie nie odtwarza; STT nieukończonej tury pozostaje otwartym ryzykiem.

## Następny sprint — MVP FIRST (kod, nie audyt)
1. Pierwszy OUTGOING: owned_call_ended, 1 tura STT/TTS, druga RX 55s bez końca; 20s limit segmentu wizyty wdrożony, przetestowany syntetycznie i zainstalowany na S22. Próba live w tej wersji nadal nie została wykonana.
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

## Agent — aktualny UX
Scenariusze: klikalne karty, szkice i prywatne kopie; 10 szablonów obsługuje pojedyncze, autoryzowane rozmowy informacyjne bez skutków zewnętrznych. Kontekst i dane AI: osobne profile incoming/outgoing, bez powielonych Skills i linków; podgląd jedynie kontekstu. Zakres danych jest górnym limitem, nie zgodą na ujawnienie. Wizyta wymaga przecięcia profilu z grantem, a scenariusze korzystają z notatek outgoing bez ujawniania danych. Niezapisane zmiany chronione przy nawigacji; fizyczny UX S22: **PHYSICAL_OPEN**.

- **Sejf danych UX:** akcja „Dodaj własne pole” na końcu; jeden klikalny wiersz zamiast dublowania edycji, liczba zapisanych pól, formularz nie zamyka się po błędnym wpisie. Szyfrowanie i brak grantu dla pól własnych zachowane.

- **Narzędzia AI MVP:** SAVE_NOTE szyfrowane per Call, migracja/deduplikacja, TASK/CALENDAR/MESSAGE tylko jako propozycje; END_CALL po TTS i kontroli Call. Notatnik: filtr, wyszukiwanie, strony, usuwanie po potwierdzeniu, AtomicFile .bak, IO poza UI. Migracja realnych danych i żywy Call pozostają bez fizycznej akceptacji.

- **Opcje / Settings (2026-11-11):** kod @dd74270: reset synchronizuje UI/store, spóźnione TTS odrzucane, mikrofon PL/EN, brak automatycznej zamiany SIM, błędny odczyt blokuje Save, poprawny komunikat MANUAL, Back w modelach, szkice po obrocie i oszczędniejszy refresh. Mac preflight/unit/Gradle **PASS**, emulator Android 36 **12/12 PASS @e533ad9**. S22+ **in-place PASS @e533ad9**, APK SHA-256 `36cb0b473d3fba647c8f380b7eecf62be5dcbfb712c695ecd5397a1925a16ada`; podpis, dane, dialer zachowane, Telecom IDLE, **0 realnych połączeń**. **P2 OPEN:** po zmianie/odłączeniu SIM przy otwartej stronie potrzebne ponowne sprawdzenie konta przy Save (controller + emulator, bez live Call). Niezaliczoną dodatkową zmianę wycofano, pozostawiając ostatni w pełni przetestowany kod. Wizualny odbiór właściciela i realne rozmowy **PHYSICAL_OPEN**. **Bez publicznego GO.**
