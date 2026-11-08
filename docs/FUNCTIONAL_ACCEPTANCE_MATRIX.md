# AI Calls — macierz funkcjonalna ekran po ekranie

**Wersja bazowa:** `main@edf63c1ea9dc425c91d2a1571a90038631852ffd` (stan po UX-11, przed porządkowym commitem dokumentacji), 2026-11-08. **Cel:** rzeczywisty odbiór każdej funkcji widocznej w aplikacji na Samsung S22, nie ponowny przegląd estetyki. Źródło nawigacji: `AppNavigation.AppTab` — sześć zakładek: Start, Historia, Agent AI, Laboratorium, Modele, Ustawienia. Ekrany potomne wskazano poniżej.

## Protokół jednego przypadku
1. Nadaj ID, HEAD, wersję APK z urządzenia, urządzenie/SIM/kanał i izolowane dane testowe (bez sekretów).
2. Określ **przed próbą** oczekiwane działanie: wejście → reakcja UI → trwały stan/rezultat → STOP/rollback → ponowne otwarcie. Zapisz źródło prawdy (UI + store/log/dowód, jeżeli potrzebny).
3. Sprawdź normalną ścieżkę, przypadek negatywny, ponowienie, odwracalność, bezpieczeństwo danych i odporność na powrót/rotację/restart, gdy ma zastosowanie. Weryfikuj aktywne uprawnienia i stan urządzenia.
4. Używaj istniejących testów, fixture i ADB; nie traktuj renderowania kontrolki jako potwierdzenia wykonania. Nie zmieniaj ani nie kasuj danych właściciela: przygotuj odrębne, fikcyjne wpisy i usuń **tylko** je. Zrzuty, WAV, transkrypty i dane osobowe pozostają prywatnie na telefonie/Macu.
5. Po każdym przypadku aktualizuj poniższy status i krótką referencję do **konkretnego** dowodu. Defekt → `BLOCKERS.md` z nowym numerem i testem; naprawa minimalna, regresja tylko zmienionego obszaru. Nie kończ na stwierdzeniu „przycisk istnieje”.

**Statusy:** `NOT_RUN`, `PASS_UI_SYNTHETIC`, `PASS_OFFCALL_FUNCTIONAL`, `PASS_DEVICE_FUNCTIONAL`, `PASS_REAL_CALL`, `PARTIAL`, `FAIL`, `BLOCKED`. Statusy nie są zamienne. Dla przypadków wielofunkcyjnych status obejmuje **wszystkie** wymienione podpunkty, inaczej `PARTIAL`. **Dziedziczone dowody** to podpowiedź do testów, nie zaliczenie całego ekranu.

## Początek właściwego F1 — 2026-11-08, Skills / Sejf / scenariusze / Gemma Audio

- **Decyzja właściciela:** nie blokować testów funkcjonalnych zaległymi screenshotami UX12 ani niedokończonym NAV-01. Priorytet: edytowanie Skills, CRUD Sejfu, własne scenariusze, działający model i wejście audio.
- **Preflight:** `main@fa21f88` = czysty Mac checkout, S22 ADB `RFCT70L7E8J` obecnie **niepodłączony** (`aicalls-f1-corepreflight-20261108-v1`, failed/1 wyłącznie na `adb get-state`). **Nie** przypisywać PASS urządzenia.
- **Test Sejfu:** nowy `AndroidCustomVaultFunctionalAcceptanceTest` izoluje `noBackupFilesDir`, nie dotyka rekordu właściciela; obejmuje cztery typy, zakres wrażliwości, rename, walidację/duplikaty, brak plaintext w ciphertext, delete/reopen. Test AndroidTest Kotlin **COMPILE PASS** `aicalls-f1-custom-vault-test-compile-20261108-v1`; uruchomienie na S22 **NOT_RUN**.
- **Gotowe do uruchomienia obok:** istniejące izolowane `AndroidSkillPresetMatrixTest.aEncryptedEditVersionSwitchAndRollbackInIsolatedStore` oraz `AndroidPersonalScenarioStoreContractTest.personalScenariosUseExistingEncryptedStoreAndPreserveTemplate`; te testy nie zastępują ręcznego UI CRUD. Native `AndroidGemmaAudioNativeOffcallTest` jest osobną, ryzykowniejszą próbą modelu, przed którą sprawdzić dostępność RAM i bezpieczeństwo hosta. Audio Direct **nie jest włączone w registry**.
- [Checkpoint](checkpoints/2026-11-08-f1-owner-priorities-device-unavailable.md). F-080/F-122/F-123/F-124 bez zmian.

## Sesja odbioru 2026-11-08 — fizyczny preflight i NAV-01

- **Źródło:** czysty Mac checkout `main@0e7dd43244edd6c22ce398490806c4a70508d487` = `origin/main`; różnice od `3029034` tylko w dokumentacji. **Urządzenie:** Samsung S22 `SM-S906B`, USB/ADB, APK `0.2.0` code 2, telefon `IDLE`.
- **Bezpieczna gotowość:** telefon/mikrofon/Shizuku API granted, AI Calls domyślnym dialerem, proces Shizuku widoczny, usługa kampanii nieaktywna. Cztery pliki modeli E2B/E4B/Qwen3 1.7B/0.6B mają oczekiwane rozmiary — **nie** oznacza to weryfikacji SHA/inferencji. Odczyt profilu `ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL`. Receipts: `aicalls-fa-preflight-s22-20261108-1018-v1`, `aicalls-fa-models-roles-services-readonly-20261108-v1`.
- **NAV-01 = PARTIAL:** przejścia i zaznaczenia pierwszych czterech zakładek fizycznie potwierdzone, reszta nierozstrzygnięta po wejściu PocketHost na pierwszy plan. Nie oznaczać całości PASS ani otwierać nowego F bez reprodukcji defektu samej aplikacji. Przy kolejnym bezpiecznym foreground AI Calls dokończyć brakujące przejścia i Back/stos Activity. Nie przerywać innych aplikacji.
- **Bez skutków:** brak dial, pobrań, instalacji, zmian profilu, edycji sejfu i danych właściciela. Screenshoty na Macu prywatnie. [Checkpoint](checkpoints/2026-11-08-functional-nav01-preflight-interrupted.md).

## 0. Powłoka, nawigacja i dostępność
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| NAV-01 | Każda z 6 zakładek, selekcja, powrót, bez znikających danych i dubli Activity | **PARTIAL** — rzeczywiste ADB taps na S22: Start → Historia → Agent AI → Laboratorium; zaznaczenia tych zakładek potwierdzone. Modele/Ustawienia, Back, duplikaty Activity, trwałość, restart **NOT_RUN**. Pierwsza próba Models niejednoznaczna; dalszą diagnostykę przerwał obcy foreground `pl.pockethost.companion` (nie potwierdza błędu AI Calls). Receipts: `aicalls-fa-nav01-device-six-tabs-20261108-1023-v1`, `aicalls-fa-nav01-foreground-lockscreen-check-20261108-v1`. Wcześniejsze UI synthetic PASS bez zmian. |
| NAV-02 | Dolna belka, toolbar potomnych ekranów, klawiatura, długie teksty/scroll, pion/poziom | NOT_RUN |
| NAV-03 | PL/EN/Auto, ponowne wejście, zmiana języka bez utraty konfiguracji | NOT_RUN |
| NAV-04 | TalkBack, content descriptions, fokus, 48dp dotyk, kontrast i dynamiczny font | NOT_RUN manualnie; poprzednie testy automatyczne UX-05 |
| NAV-05 | Screenshoty dozwolone, prywatne wartości ukryte, brak wycieku w logach | PARTIAL: source/UX synthetic; prywatność całej ścieżki NOT_RUN |

## 1. Start / zadanie / Kampanie / sterowanie Call
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| HOME-01 | Informacja o zadaniu, autoryzacji, fazie AI i ostatnim wyniku nie jest mylona z aktualnym Call | NOT_RUN |
| HOME-02 | Stan gotowości roli Dialer, SIM, zgody, wymagane uprawnienia i czytelne błędy odmowy | NOT_RUN |
| HOME-03 | START tylko dla dokładnie przyjętego celu; odmowa, anulowanie, restart i deduplikacja | NOT_RUN / real Call wymaga właściwego upoważnienia |
| HOME-04 | Pływający czat: brak przed pierwszą linią, icon-only, otwarcie, dopisywanie, zamknięcie, zniknięcie po końcu | PASS_UI_SYNTHETIC — `aicalls-ux11-s22-synthetic-layout-and-private-archive-20261108-v1` 2/2; real CALL NOT_RUN |
| HOME-05 | STOP AI przerwie obliczenia, TX i skutki; pozostanie dostępny bez czatu | NOT_RUN w Call; LAB-03 dowodzi tylko offline STOP |
| CAMP-01 | Wejście w Kampanie, stan wykonywania, diagnostyka, status i niezależny zapis próby | NOT_RUN |
| CAMP-02 | Dwa tryby: skrypt + model oraz model Direct; prawidłowa konfiguracja/wybrany model | NOT_RUN w Call |
| CAMP-03 | Zgoda na przyszły zapis vs zatrzymanie aktywnego rejestratora, wycofanie zgody | BLOCKED dla aktywnego rejestratora: F-122 |
| CAMP-04 | Ponowienie, odwołanie, zakończenie, zgodność attemptId / sesji z rzeczywistym Android Call | BLOCKED częściowo: F-123 |
| CALL-01 | Ekran sterowania własną rozmową, przyjęcie/rozłączenie/przejęcie/STOP bez obcego Call | NOT_RUN fizycznie |
| CALL-02 | Połączenia przychodzące: tryb manual/AI, powrót właściciela i polityka ciszy | NOT_RUN end-to-end; istnieją testy jednostkowe/Android |

## 2. Historia
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| HIST-01 | Lista sesji, status, czas, kierunek, filtry, pusty stan, odświeżanie | NOT_RUN |
| HIST-02 | Otwieranie pojedynczej sesji, kolejność tur, poprawne powiązanie faktycznego wyniku | NOT_RUN |
| HIST-03 | Odtwarzanie WAV z dymku, pauza/STOP, błędny lub brakujący plik, rewind/seek | NOT_RUN fizycznie |
| HIST-04 | Usunięcie jednej rozmowy i usuwanie zbiorcze na **prywatnych fixture**, bez zmiany cudzych danych | NOT_RUN funkcjonalnie |
| HIST-05 | Cała oś audio: RX/TX/manual, ciągłość i odsłuch całej sesji + fragmentów | BLOCKED: F-080 |
| HIST-06 | „Ostatni odsłuch” pokazuje właściwą sesję również dla INCOMING / długich tur | BLOCKED: F-124 |

## 3. Agent AI — Skills, scenariusze, przygotowanie, Sejf, Narzędzia
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| SKILL-01 | Presety: wybór, aktywacja, włączenie/wyłączenie Skills i wpływ na wygenerowaną wypowiedź | NOT_RUN funkcjonalnie |
| SKILL-02 | Instrukcja: edycja, walidacja, wersje/rewizje, zapisz/przywróć, brak wpływu nieaktywnej instrukcji | NOT_RUN |
| SKILL-03 | Własne umiejętności, warunki użycia i separacja stanów pomiędzy presetami | NOT_RUN |
| SCEN-01 | Fabryczne scenariusze: przegląd i poprawne oznaczenie DRAFT_ONLY vs wykonawcze | NOT_RUN |
| SCEN-02 | Własny scenariusz: kopia, nazwa, cel, zapis, rewizje, przywrócenie, usunięcie fixture | NOT_RUN funkcjonalnie; istnieją testy Android/store |
| PREP-01 | Profil OUTGOING/INCOMING: edycja, zapis, wznowienie, niewyciekający kontekst i zakres pól | NOT_RUN |
| VAULT-01 | 7 pól standardowych: utwórz/zmień/usuń **fikcyjny testowy** wpis, odczyt po restarcie, błędy walidacji | NOT_RUN; widok PASS_UI_SYNTHETIC w UX-11 |
| VAULT-02 | Własne: typ tekst/liczba/data/sekret, poziom wrażliwości, dodaj/zmień/usuń, szyfrowanie/odporność | NOT_RUN fizycznie; **nowy izolowany test** `AndroidCustomVaultFunctionalAcceptanceTest` skompilowany na Mac (`aicalls-f1-custom-vault-test-compile-20261108-v1`), wykonanie na S22 OPEN. **Custom nie jest automatycznie udostępniany AI.** |
| VAULT-03 | PESEL/data urodzenia: ukrycie w UI, zgody zakresowe, brak wypowiedzi/wysłania bez autoryzacji | NOT_RUN; nie używać prawdziwego PESEL |
| TOOL-01 | Lista/stan narzędzi, zmiana ustawień, trwałość i zachowanie wyłączonych akcji | NOT_RUN |
| TOOL-02 | Dry-run wyboru narzędzia i bezpieczne zakończenie bez prawdziwego efektu | NOT_RUN |

## 4. Laboratorium Skills
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| LAB-01 | Wybranie presetu, zakres quick/full, reset, właściwa liczba tur i widok w trakcie | NOT_RUN pełną macierzą; UX-11 UI PASS |
| LAB-02 | Prawdziwy lokalny model: 2 kolejne tury, odpowiedź i kryteria końca bez Telecom | PASS_OFFCALL_FUNCTIONAL — `aicalls-functional-real-offline-two-turn-stop-s22-20261108-v1` |
| LAB-03 | STOP w trakcie, stan STOPPED, brak dalszych tur, ponowny START | PASS_OFFCALL_FUNCTIONAL — ten sam receipt, tylko izolowane dwie tury i restart/STOP |
| LAB-04 | Timeline podczas długiego biegu, ręczny scroll w górę, auto-follow na dole, widoczne błędy | NOT_RUN pełną macierzą |
| LAB-05 | Metryki, zgodność celu, odpowiedzi błędne/puste, timeout, ocena naturalności | NOT_RUN |
| LAB-06 | Wszystkie presety i typy Skills, powtarzalność N=3, opóźnienia median/p95 | NOT_RUN |

## 5. Modele i silniki audio
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| MODEL-01 | Katalog, szczegóły, aktualny model, lokalny stan artefaktu i READY vs niedostępny | NOT_RUN per model |
| MODEL-02 | DOWNLOAD / Cancel / retry / VERIFY SHA-256 / niepełny plik / brak miejsca | NOT_RUN fizycznie; nie pobierać dużych plików automatycznie |
| MODEL-03 | Wybór modelu, trwałość, presety Direct i skrypt, brak podmiany w aktywnej próbie | NOT_RUN |
| MODEL-04 | Lokalna inferencja: gotowość, wyjście/odpowiedź, STOP i ponowienie | PARTIAL: LAB-02/03 PASS tylko dla testowanego wybranego modelu |
| AUDIO-01 | Profile STT/model/TTS/głos i ich trwałość, niezależny wybór oraz rzeczywiste użycie | NOT_RUN |
| AUDIO-02 | Polski TTS → syntetyczny PCM → Parakeet STT, niepusty transkrypt / zgodność leksykalna | PASS_OFFCALL_FUNCTIONAL — `aicalls-functional-polish-tts-parakeet-offcall-s22-20261108-v1` |
| AUDIO-03 | STT na kontrolowanym nagraniu i mikrofonie, WER/CER, cisza, szum, stop i recovery | NOT_RUN |
| AUDIO-04 | Eksperymentalne bezpośrednie PCM16 → Gemma `Content.AudioBytes` → tekst; inicjalizacja native, pamięć/latencja, STOP, brak cichego fallback | NOT_RUN fizycznie; `GemmaAudioDirectInputProvider` istnieje, lecz `AudioEngineRegistry` ma `integrated=false` i `audio_direct_s22_unqualified`. Wymaga osobnego testu `AndroidGemmaAudioNativeOffcallTest` na S22, przed włączeniem w ustawieniach. |
| AUDIO-04 | TTS (system/Piper), polska wymowa, szybkość, latencja, cancel oraz audio output | NOT_RUN w pełnej macierzy |
| AUDIO-05 | Integracja STT→model/Skills→TTS przez wspólny runtime, faktyczne RX/TX | NOT_RUN end-to-end |

## 6. Ustawienia, sekretarka i diagnostyka
| ID | Funkcja / kryterium | Stan i dowód |
|---|---|---|
| SET-01 | Ustawienia ogólne: każda kontrolka, zapis, restart, Reset tylko fixture | NOT_RUN |
| SET-02 | Sekretarka INCOMING: profil, enable, zgoda, zachowanie dla połączeń własnych i manual takeover | NOT_RUN fizycznie |
| SET-03 | Developer: każdy bezpieczny probe, wyjście, log oraz obsługa braku zasobu | NOT_RUN |
| SET-04 | USB Debug: stan ADB/Shizuku, diagnostyka, czytelne błędy i prywatny eksport | NOT_RUN |
| SET-05 | PL/EN/Auto: poprawność tłumaczeń i wartości, po wznowieniu i restarcie | NOT_RUN manualnie |

## 7. Integracja i bezpieczeństwo — bramki końcowe
| ID | Kryterium | Stan i dowód |
|---|---|---|
| INT-01 | Dowód jednej konkretnej sesji: Call ID, SIM, kierunek, rola dialer, RX→STT→model→TTS→TX ACK | NOT_RUN |
| INT-02 | Realna skuteczność zadania (np. CLIR) + niezależne potwierdzenie efektu, nie sam tekst modelu | NOT_RUN |
| INT-03 | Incoming/Outgoing, IVR, cisza, rozmowa nakładająca się na TTS, STOP/recovery, przejęcie | NOT_RUN |
| INT-04 | Bezpieczeństwo danych: ujawnienie tylko dozwolonych pól, revocation, poufność i retencja | NOT_RUN end-to-end |
| INT-05 | Recorder: wyłącz aktywny, korelacja, ciągłość i poprawny odsłuch archiwum | BLOCKED/OPEN: F-122, F-123, F-124, F-080 |
| INT-06 | Brak pozostałego aktywnego Call/usługi po STOP, IDLE, bez wycieku sesji/testowych fixture | NOT_RUN real-call |

## Kolejność wykonania w nowym czacie
**Krok 1:** pełny preflight stanu APK/urządzenia bez powtarzania zaliczonych testów. **Krok 2:** NAV + Start (bez telefonii), następnie Historia, Agent AI, Laboratorium, Modele/audio i Ustawienia. **Krok 3:** integracyjne próby offline i testy odporności. **Krok 4:** rzeczywisty Call tylko po ustaleniu i przyjęciu konkretnego celu/autoryzacji oraz po preflight IDLE; dokumentuj również odmowę/uprawnienia. **Krok 5:** aktualizacja macierzy, checkpoint, synchronizacja dokumentacji i czysty handoff.

**Zamknięcie ekranu:** każda jego funkcja ma osobny wynik albo jawne `BLOCKED/NOT_RUN` z przyczyną; UI i backend zgodne, błędy opisane, regresja na zmienionym obszarze; brak niezamierzonej mutacji danych właściciela.
