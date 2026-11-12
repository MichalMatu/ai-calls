# AI Calls — MVP FIRST

**Priorytet:** działający asystent telefoniczny na S22+. Bezpieczeństwo Call, granty i STOP są zamrożonym fundamentem; odłóż nieblokujący hardening.

## Interfejs użytkownika
Cztery zakładki: **Start / Rozmowy / Agent AI / Ustawienia**. Start otwiera wspólny kreator zadania i pokazuje kolejkę, wyniki oraz globalny STOP. Agent: zachowanie sekretarki, scenariusze, kontekst rozmów, sejf, notatki i narzędzia. Ustawienia: model i głos, odbieranie, uprawnienia Androida, narzędzia programistyczne i język aplikacji.

Kontrakt panelu: [UI](modules/UI.md). **IMPROVE** istniejącego katalogu scenariuszy, Keystore/AtomicFile i serwisu Call; bez nowych bibliotek. AlarmManager uzupełnia brak trwałego harmonogramu, zamiast procesowego Handler lub niedokładnego WorkManager. Runtime, granty i transport pozostają wspólne.

## Otwarte — MVP FIRST (G6.1 i pozostałe P0/P1)
| Funkcja | Status | Zweryfikowane / blokada |
| --- | --- | --- |
| Zadania i kampanie | IN_PROGRESS · app/campaign | Brak wspólnego kreatora, trwałej kolejki i harmonogramu. Gate: odtworzenie, duplicate alarm, STOP, exact SIM/Call, PL/EN; start w tle i realna kampania S22 PHYSICAL_OPEN |
| Panel UX na S22 | INSTALLED / PHYSICAL_OPEN · app/ui | `015ae2d` zainstalowane z zachowaniem danych/roli; odbiór otwarty |
| Audio Gemmy | HOST_TESTED / PHYSICAL_OPEN · app/audioengines | STT zachowuje kontekst; pusty finalny transkrypt wraca do nasłuchu. Jakość, PL i RAM S22 otwarte |
| Automatyczne INCOMING zgodnie z polityką | HOST_TESTED | Exact Call, SIM, AI/MANUAL, takeover w koordynatorze; żywa rozmowa na S22 **BLOCKED** do zgody |
| Gemma + STT/TTS podczas rozmowy | DEVICE_OBSERVED: częściowo | W pierwszym OUTGOING: STT 43 znaki, 1 tura, RX 151040 B / TX 202622 B; jakość u rozmówcy i barge-in nadal **PHYSICAL_OPEN** |
| Outgoing po autoryzacji | DEVICE_VERIFIED: real dial | Na S22+ po jednym autoryzowanym START Telecom potwierdził aktywne połączenie; odbiór i jakość audio nadal **PHYSICAL_OPEN** |
| Wybór danych IdentityVault | HOST_TESTED | UI pozwala wybrać FIRST_NAME/LAST_NAME/PHONE/EMAIL, scoped reader sprawdza task/target/generation i zgodę; mówione wartości na S22 **PHYSICAL_OPEN** |
| Wizyta, warunki i wynik do oceny | HOST_TESTED / LIVE REVIEW brak | RX 20s i diagnostyka przerwanego segmentu; parser i strefa zachowane. Druga tura PHYSICAL_OPEN |
| Natychmiastowy STOP / TAKE OVER i resume | HOST_TESTED / DEVICE_VERIFIED off-call | Symulowany STOP i TAKE OVER PASS; na wcześniejszym APK fizyczne off-call STOP/stale PCM PASS; przejęcie rozmowy live **PHYSICAL_OPEN** |

OUTGOING potwierdził jedną turę RX/TX, nie jakość audio ani rezerwację. Archiwum tekstu/diagnostyki HOST_TESTED; pełna historia S22 PHYSICAL_OPEN, WAV wymaga opt-in.

## Następny sprint — zadania i kampanie
Kreator: scenariusz → odbiorcy → jedno okno dzwonienia → zatwierdzenie. Jeden numer i kampania mają wspólny model; połączenia kolejno, retry tylko potwierdzonego BUSY/NO_ANSWER, bez limitu liczby prób. Godzina uruchamia kolejkę, nie gwarantuje zestawienia połączenia w tej sekundzie. Aplikacja posiada harmonogram i autoryzację; Gemma prowadzi pojedynczą rozmowę. Wyniki to wypowiedzi rozmówców do oceny, bez automatycznych rezerwacji. Nieznany wynik po restarcie wymaga decyzji, nie redial.

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
Scenariusze: 10 szablonów i własne wersje; przygotowanie przechodzi do wspólnego kreatora. Kontekst, sejf, Skills i narzędzia pozostają oddzielne od wykonania. SAVE_NOTE jest prywatny; TASK/CALENDAR/MESSAGE są propozycjami, nie integracjami zewnętrznymi.

Lab, model i uprawnienia: kontrakty w [UI](modules/UI.md) i [Skills/Lab](modules/SKILLS_LAB.md).
