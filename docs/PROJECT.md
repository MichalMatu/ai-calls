# AI Calls — MVP FIRST

**Priorytet:** uruchomienie pełnego asystenta telefonicznego na Samsung S22+, nie G6.5 hardening. Istniejące moduły, bezpieczeństwo Call i autoryzacja są zamrożonymi fundamentami. Błędy nieblokujące MVP odłóż.

## Interfejs użytkownika — uproszczenie
- **Start / Rozmowy / Agent AI / Ustawienia** — cztery zakładki. Start zawiera bezpośredni dostęp do istniejącej rozmowy **w sprawie wizyty**, bieżący status, STOP i prywatny podgląd rozmowy; nie sugeruje ogólnego scenariusza, którego nie wdrożono.
- Usunięto dawny ekran Kampanie i martwe napisy zasobów; CLIR/start skryptu, nadzorowany test audio oraz diagnostyczne karty nie mają już konsumenckich przycisków. Model i głos są pod Ustawieniami, laboratorium wyłącznie w narzędziach developerskich. Wewnętrzne mechanizmy grantów, dokładnego Call, STOP, STT i archiwum zachowane.
- W formularzu wizyty uprawnienia Androida i wybór dialera mogą kontynuować **ten sam wyraźnie rozpoczęty przez użytkownika** zamiar, bez ponownego naciskania Start. Odmowa nie rozpoczyna połączenia.

- **Sekretarka (UX):** dwa tony naturalny/formalny i własne instrukcje bez nazywania profilu. Ustawienia dotyczą tylko incoming; stare presety i historia wersji są usuwane podczas migracji szyfrowanego zapisu; aktywne własne instrukcje i ton sekretarki są zachowywane. Wymagana fizyczna kontrola UI S22+ po instalacji.

## Otwarte — MVP FIRST (G6.1 i pozostałe P0/P1)
| Funkcja | Status | Zweryfikowane / blokada |
| --- | --- | --- |
| Czytelność Model i głos | PHYSICAL_OPEN · app/ui | Trzy karty, modale i autosave; kompilacja/pakowanie i install-r PASS. Ocena właściciela pozostaje otwarta |
| Audio Gemmy | HOST_TESTED / PHYSICAL_OPEN · app/audioengines | Wspólny silnik, transkrypt przed kontrolą wizyty/danych, narzędzia i STOP; testy hosta PASS. Gemma pobrana według właściciela; native PL, jakość i RAM S22 otwarte |
| Pełny gate Python | OPEN · scripts/tests | Pełny zestaw pominięty w tej iteracji. Pozostaje dług testów dawnych ekranów; po odbiorze modali wymagane dostosowanie testów UI |
| Automatyczne INCOMING zgodnie z polityką | HOST_TESTED | Exact Call, SIM, AI/MANUAL, takeover w koordynatorze; żywa rozmowa na S22 **BLOCKED** do zgody |
| Gemma + STT/TTS podczas rozmowy | DEVICE_OBSERVED: częściowo | W pierwszym OUTGOING: STT 43 znaki, 1 tura, RX 151040 B / TX 202622 B; jakość u rozmówcy i barge-in nadal **PHYSICAL_OPEN** |
| Outgoing po autoryzacji | DEVICE_VERIFIED: real dial | Na S22+ po jednym autoryzowanym START Telecom potwierdził aktywne połączenie; odbiór i jakość audio nadal **PHYSICAL_OPEN** |
| Wybór danych IdentityVault | HOST_TESTED | UI pozwala wybrać FIRST_NAME/LAST_NAME/PHONE/EMAIL, scoped reader sprawdza task/target/generation i zgodę; mówione wartości na S22 **PHYSICAL_OPEN** |
| Wizyta, warunki i wynik do oceny | 1 LIVE, REVIEW brak | Druga tura RX przerwana po 55280 ms (owned_call_ended) bez transkryptu; 20s RX segmentacja wdrożona, ponowna weryfikacja live **PHYSICAL_OPEN** |
| Natychmiastowy STOP / TAKE OVER i resume | HOST_TESTED / DEVICE_VERIFIED off-call | Symulowany STOP i TAKE OVER PASS; na wcześniejszym APK fizyczne off-call STOP/stale PCM PASS; przejęcie rozmowy live **PHYSICAL_OPEN** |

Pierwszy OUTGOING potwierdza częściowy RX/TX i jeden turn, nie potwierdza zrozumiałości u rozmówcy ani końcowej rezerwacji. Pełne live QA nadal otwarte.

Automatyczne, prywatne archiwum STT/zatwierdzonych odpowiedzi/diagnostyki na każdej autoryzowanej sesji, bez UI click i bez WAV: **HOST_TESTED** (`app` focused + changed QA, anti-friction i docs PASS; Mac `2e7d931a`). Audio WAV nadal wymaga osobnego opt-in. **S22: bieżący APK `4d35a87c` zachowuje poprawkę RX i dane; live STT PHYSICAL_OPEN**. Pierwszego transkryptu EMC ten fix wstecznie nie odtwarza; STT nieukończonej tury pozostaje otwartym ryzykiem.

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

- **Ustawienia rozmów:** P2: walidacja SIM po zmianie karty i fizyczny odbiór pozostają otwarte.
- **Model i głos:** trzy karty, wybory w modalach zapisywane od razu; Parakeet w grupie Dodatkowe przy wyborze rozpoznawania, silnik przy głosach, pliki osobno. Bez górnych opisów. KEEP runtime, STOP, Skills i granty. Właściciel potwierdził pobranie Gemmy; odbiór nowego układu na S22 **PHYSICAL_OPEN**.
- **Rola Telefonu:** stan i jawny przycisk w Ustawieniach oraz na Start przy braku roli. Activity Result identyfikuje caller i odświeża status po wyniku; otwarcie aplikacji i anulowanie wyboru nie uruchamiają grantu ani Call. Gest Wstecz kontekstu obsługuje AndroidX dispatcher i chroni szkic.
- **QA UX:** kompilacja/pakowanie PASS; S22 APK `4d35a87c`, SHA-256 `690e8f62e1398e3fcc0323e5ee5e0f0a13490ef81d8fdfb92c925e75a76ba5bf`, install-r/odczyt APK PASS. 3 preferencje, pliki modeli, UID, uprawnienia i rola Telefonu zachowane; IDLE. Pełne zestawy i emulator pominięte na życzenie właściciela. Odbiór UI, native audio i live STT **PHYSICAL_OPEN**.
