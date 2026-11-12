# AI Calls — MVP FIRST

**Priorytet:** działający asystent telefoniczny na S22+. Bezpieczeństwo Call, granty i STOP są zamrożonym fundamentem; odłóż nieblokujący hardening.

## Interfejs użytkownika
Cztery zakładki: **Start / Rozmowy / Agent AI / Ustawienia**. Start otwiera formularz zapytania o wizytę i pokazuje stan zadania wychodzącego, STOP oraz prywatny podgląd. Agent: zachowanie sekretarki, scenariusze, kontekst rozmów, sejf, notatki i narzędzia. Ustawienia: model i głos, odbieranie, uprawnienia Androida, narzędzia programistyczne i język aplikacji.

Kontrakt panelu: [UI](modules/UI.md). **IMPROVE** istniejącego `UiDesign`, bez nowych bibliotek. Walidacja: 1200 testów JVM, 437 Python, lint; 29 testów Androida, 20 ekranów i 9 przy czcionce 150%. Granty, Call, STOP i prywatność pozostają fundamentem.

## Otwarte — MVP FIRST (G6.1 i pozostałe P0/P1)
| Funkcja | Status | Zweryfikowane / blokada |
| --- | --- | --- |
| Panel UX na S22 | PHYSICAL_OPEN · app/ui | Menu i podstrony zweryfikowane na hoście i emulatorze; odbiór właściciela na S22 pozostaje otwarty. W tej zmianie bez instalacji na S22 |
| Audio Gemmy | HOST_TESTED / PHYSICAL_OPEN · app/audioengines | STT zachowuje kontekst; pusty finalny transkrypt wraca do nasłuchu (Mac PASS `6b93564`). Model, granty i STOP bez zmian. Jakość, native PL i RAM S22 otwarte |
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
- **Rola Telefonu:** jawne granty w Ustawienia → Uprawnienia aplikacji; na Start tylko brak roli. Otwarcie ekranów nie żąda uprawnień. WAV opt-in; transkrypty/diagnostyka niezależne. Szczegóły: [UI](modules/UI.md).

- **QA UX:** prywatne zrzuty emulatora potwierdzają układ, nawigację i większą czcionkę; nie potwierdzają zachowania podczas rzeczywistego Call. Odbiór S22 **PHYSICAL_OPEN**.
