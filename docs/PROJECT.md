# AI Calls — MVP FIRST

**Priorytet:** działający asystent telefoniczny na S22+. Bezpieczeństwo Call, granty i STOP są zamrożonym fundamentem; odłóż nieblokujący hardening.

## Interfejs użytkownika — uproszczenie
- **Start / Rozmowy / Agent AI / Ustawienia** — cztery zakładki. Start bez powtórzonego nagłówka aplikacji zawiera dostęp do rozmowy **w sprawie wizyty**, status, STOP i prywatny podgląd. Rozmowy bez zbędnego górnego «Wstecz» (gest i systemowy Wstecz zachowane), Agent AI bez stopki o uprawnieniach (granice grantów bez zmian), Opcje bez nagłówka/opisu i z rolą Telefon wyłącznie w Uprawnieniach aplikacji (na Start awaryjnie, gdy roli brak).
- Kampanie i diagnostyczne akcje usunięte z nawigacji konsumenckiej. Model/głos: Ustawienia; laboratorium: narzędzia developerskie. Granty, Call, STOP, STT i archiwum zachowane.
- W formularzu wizyty uprawnienia Androida i wybór dialera mogą kontynuować **ten sam wyraźnie rozpoczęty przez użytkownika** zamiar, bez ponownego naciskania Start. Odmowa nie rozpoczyna połączenia.

- **Sekretarka (UX):** dwa tony naturalny/formalny i własne instrukcje bez nazywania profilu. Ustawienia dotyczą tylko incoming; stare presety i historia wersji są usuwane podczas migracji szyfrowanego zapisu; aktywne własne instrukcje i ton sekretarki są zachowywane. Wymagana fizyczna kontrola UI S22+ po instalacji.

- **Diagnostyka UX:** jedno wejście przez Opcje → Narzędzia programistyczne → Stan aplikacji. Jedna karta z 14 niepowtórzonymi statusami w czterech grupach, bez sekcji rozwijanej, ręcznego odświeżania i kopiowania JSON do schowka; odczyt przy otwarciu ekranu. Protokół USB/ADB i sondy bez zmian; odbiór S22 **PHYSICAL_OPEN**. Osobny ekran Możliwości urządzenia pokazuje parsowane dane w jednej karcie (wynik inicjalizacji nie oznacza skutecznego przechwycenia rozmowy); sonda poza wątkiem UI, oryginalny prywatny raport nadal zapisywany.

## Otwarte — MVP FIRST (G6.1 i pozostałe P0/P1)
| Funkcja | Status | Zweryfikowane / blokada |
| --- | --- | --- |
| Czytelność Model i głos | PHYSICAL_OPEN · app/ui | Wynik próby trwały, przycisk nie znika, nazwy TTS skrócone. Kompilacja/install-r PASS; ocena właściciela otwarta |
| Audio Gemmy | HOST_TESTED / PHYSICAL_OPEN · app/audioengines | STT zachowuje kontekst; pusty finalny transkrypt wraca do nasłuchu (Mac PASS `6b93564`). Model, granty i STOP bez zmian. Jakość, native PL i RAM S22 otwarte |
| Pełny gate Python | OPEN · scripts/tests | Pełny zestaw pominięty w tej iteracji. Pozostaje dług testów dawnych ekranów; po odbiorze modali wymagane dostosowanie testów UI |
| Automatyczne INCOMING zgodnie z polityką | HOST_TESTED | Exact Call, SIM, AI/MANUAL, takeover w koordynatorze; żywa rozmowa na S22 **BLOCKED** do zgody |
| Gemma + STT/TTS podczas rozmowy | DEVICE_OBSERVED: częściowo | W pierwszym OUTGOING: STT 43 znaki, 1 tura, RX 151040 B / TX 202622 B; jakość u rozmówcy i barge-in nadal **PHYSICAL_OPEN** |
| Outgoing po autoryzacji | DEVICE_VERIFIED: real dial | Na S22+ po jednym autoryzowanym START Telecom potwierdził aktywne połączenie; odbiór i jakość audio nadal **PHYSICAL_OPEN** |
| Wybór danych IdentityVault | HOST_TESTED | UI pozwala wybrać FIRST_NAME/LAST_NAME/PHONE/EMAIL, scoped reader sprawdza task/target/generation i zgodę; mówione wartości na S22 **PHYSICAL_OPEN** |
| Wizyta, warunki i wynik do oceny | HOST_TESTED / LIVE REVIEW brak | RX 20s; diagnostyka zapisuje przerwany segment także przy wcześniejszym błędzie downlink (Mac PASS `ef8657a`). Parser i strefa zadania zachowane. Druga tura live **PHYSICAL_OPEN** |
| Natychmiastowy STOP / TAKE OVER i resume | HOST_TESTED / DEVICE_VERIFIED off-call | Symulowany STOP i TAKE OVER PASS; na wcześniejszym APK fizyczne off-call STOP/stale PCM PASS; przejęcie rozmowy live **PHYSICAL_OPEN** |

Pierwszy OUTGOING potwierdza RX/TX i jedną turę, nie potwierdza jakości dźwięku ani rezerwacji.

Prywatne archiwum STT, zatwierdzonych odpowiedzi i diagnostyki bez WAV: **HOST_TESTED**. WAV wymaga opt-in; nieukończony STT i historia pierwszego połączenia pozostają **PHYSICAL_OPEN**.

## Następny sprint — MVP FIRST (kod, nie audyt)
1. OUTGOING: RX 20s, zapis metadanych przerwanego RX (`ef8657a`) i retry pustego końcowego STT (`6b93564`) przeszły host QA. Kontrolowana druga tura S22 **PHYSICAL_OPEN**.
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
Scenariusze: karty, szkice i 10 szablonów rozmów bez skutków zewnętrznych. Osobne profile incoming/outgoing; dane to limit, nie zgoda. Wizyta wymaga grantu, szkice chronione przy nawigacji; UX S22 **PHYSICAL_OPEN**.

- **Sejf danych UX:** akcja „Dodaj własne pole” na końcu; jeden klikalny wiersz zamiast dublowania edycji, liczba zapisanych pól, formularz nie zamyka się po błędnym wpisie. Szyfrowanie i brak grantu dla pól własnych zachowane.

- **Narzędzia AI:** szyfrowane SAVE_NOTE per Call; TASK/CALENDAR/MESSAGE tylko propozycje; END_CALL po TTS i kontroli Call. Notatnik: wyszukiwanie, strony, AtomicFile i IO poza UI. Migracja/żywy Call **PHYSICAL_OPEN**.

- **Ustawienia rozmów:** P2: walidacja SIM po zmianie karty i fizyczny odbiór pozostają otwarte.
- **Model i głos:** trzy karty, modale i osobne pliki; Parakeet w Dodatkowych. Status zgodności tekst/audio bazuje na potwierdzeniu dla pliku/profilu, a ręczny test jest dostępny dla pobranego modelu (S22 PHYSICAL_OPEN). KEEP runtime, STOP, Skills i granty. Gemma pobrana według właściciela; UX S22 **PHYSICAL_OPEN**.
- **Rola Telefonu:** stan i jawny przycisk w Opcje → Uprawnienia aplikacji oraz na Start przy braku roli. Tam jedna karta statusów i jawnych próśb o grant: mikrofon, SIM, wykonywanie połączeń, powiadomienia na Androidzie 13+ oraz Shizuku. Po odmowie z blokadą zgody dostępne są ustawienia Androida; prośba o grant wyłącznie po dotknięciu. Opcje bez powtórzonego nagłówka Model i głos; język aplikacji na dole, niezależny od STT/TTS. Incoming ma zwięzłe opisy bez tekstowej stopki i nieprawdziwej informacji o aktywnej rozmowie. Checkbox dotyczy WAV, a transkrypty/diagnostyka są niezależne. WAV domyślnie wyłączony dla nowych instalacji; wcześniejsze wybory zachowane. Activity Result identyfikuje caller i odświeża status po wyniku; otwarcie aplikacji i anulowanie wyboru nie uruchamiają grantu ani Call. Gest Wstecz kontekstu obsługuje AndroidX dispatcher i chroni szkic.
- **QA UX:** wcześniejszy sweep UI 13/13 na emulatorze; S22 odświeżony do `313f871` bez realnych Call, zachowano preferencje i rolę dialera. Najnowszy kod STT jeszcze nieinstalowany. UI/audio S22 **PHYSICAL_OPEN**.
