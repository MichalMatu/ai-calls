# AI Calls — aktualny stan i kolejka rozwoju

**Stan źródłowy:** G6.1 source freeze po Mac QA 2026-11-10; aktualny commit ustalaj z origin/main.

## Cel i fakty
- Jeden lokalny agent i jeden wspólny runtime INCOMING/OUTGOING. Bieżący produkt jest **modułowym laboratorium**, finalnie jeden pakiet instalacyjny. Obecny wybór: Gemma 4 E2B/GPU, CONTEXTUAL Skills, Android STT/TTS i Samsung/Shizuku.
- **G3/G4:** wybór E2B po 96/96 native tur; dynamic Skills S22 PASS w zakresie off-call. **G5.3:** 18/18 native off-call tur i 36 prywatnych WAV, rzeczywisty STOP STT/model/TTS sprawdzony. Indywidualna ocena człowieka: **0/6, UNASSESSED** (nie wpisuj human PASS).
- **G6 source/host:** INCOMING reentrant TAKE OVER/STOP, pełna polityka i exact AudioSlot przed end_call. Realtime barge-in/flush; lokalny RX-during-TX anuluje TTS i wznawia STT z pre-roll. RX/STT/TX mają ogrodzenia tury, epoki, generacji i próby RX; downlink EOF fail-closed. TX COMPLETE wymaga flush i live grant; częściowy lub niepewny zapis nie jest sukcesem. Historia zachowuje prywatne fragmenty WAV, domyka interrupted RX przed END; to nie pełne nagranie. Echo/double-talk i S22 pozostają PHYSICAL_OPEN. QA aicalls-g61-tx-ivr-incoming-freeze-full-mac-20261110-v1: Gradle/lint PASS; 409 Python PASS po korekcie budżetu dokumentacji. Bez APK i realnych Call.
- Historyczne dwa INCOMING A8→S22 dowiodły fragmentów odbioru i zakończenia własnego połączenia, **nie** całości G6. Nie powtarzaj G3 96 tur, G5.3 18 tur ani fizycznego STOP bez zmiany kontraktu.

## Otwarte — tylko rzeczywista następna praca
| Priorytet | Moduł | Brakujący warunek odbioru | Najmniejsza potrzebna weryfikacja |
| --- | --- | --- | --- |
| P0 | G6.1 / CONVERSATION | Lokalny Gemma/STT/TTS: wykrycie RX podczas TX, anulowanie starego TTS i bezpieczne wznowienie na realnym Call | Test generacji+stale PCM, następnie **autoryzowany** S22 barge-in |
| P0 | G6.1 / INCOMING | Human TAKE OVER/resume tej samej sesji bez rozłączenia; dokładna polityka stop/error/recovery | Regresja jednostkowa i jedna kontrolowana fizyczna próba |
| P0 | AUDIO_HISTORY | Pełne nagranie zgodne z uprawnieniami/zgodą, w tym dopuszczalna część manualna oraz płynna oś czasu. Powtarzane okna RX zachowują osobne WAV; historia i odzyskiwanie miejsca weryfikują prywatne timeline/COMPLETE, chronią aktywny katalog i pomijają symlink escape; playlist odtwarza **fragmenty**, nie cały Call | Pokrycie RX/TX/manual, retencja, no-leak, kontrolowany S22 playback |
| P1 | G6.2 / OUTGOING | Dokładnie autoryzowany outbound, integracja IVR/DTMF z app-owned Telecom; gate JVM + Android Telecom tone adapter mają osobne testy i seam; zajęty ton daje bezpieczne NOT_READY/retry zamiast trwałego UNKNOWN, ale brak autoryzowanego wywołania IVR i testu fizycznego | Focused port test, prawdziwy IVR wyłącznie po grancie/IDLE |
| P1 | BOOKING_EFFECTS | Typowany wynik i niezależny dowód rezerwacji/CLIR; syntetyczny availability/hold (TTL, wyłączność rzeczywistego terminu mimo zmiany ceny/strefy, izolacja sesji, cancel)/confirm + provider read-back; realny adapter nadal OPEN | Syntetyczny adapter, idempotencja, potem osobny autoryzowany dowód usługodawcy |
| P1 | SECURITY | Scoped IdentityVault: policy + podwójny live check; CLIR korzysta ze scoped reader. Inne narzędzia faktów F-048 OPEN. BOOK_APPOINTMENT ma app-owned exact proposal/constraints/consent gate; inne efekty F-045 OPEN | Denied/approved scoped tests bez realnych danych |
| P2 | CONVERSATION | WAV przerwanego RX jest domknięty przed END; końcowy STT po rozłączeniu przed ciszą VAD (F-047) i jakość długich wypowiedzi nadal OPEN | Syntetyczny końcowy fragment, później fizyczna próba |
| P2 | G7 / UI | Uproszczenie UX i finalny product packaging, dopiero po G6 | Celowane UI/functional acceptance |

**Nie otwierać ponownie naprawionych problemów tylko dlatego, że kiedyś miały numer.** Nowa luka trafia w odpowiedni istniejący wiersz lub zastępuje zamkniętą; brak nowej historii/chronologii. Testów nie nazywaj PASS bez faktycznego wykonania.

## Następny sprint
1. Pobierz aktualny `main`, sprawdź realny stan Mac Local Agent i konflikty. Wybierz pionowy zakres G6.1 (lokalne przerwanie i takeover) z tabeli, nie kolejne audyty.
2. **Existing Code First** ([reguła](operations/EXTERNAL_REUSE.md)): własny baseline → do 3 kandydatów → KEEP/IMPROVE/ADAPT/REPLACE z testem. Bez rewrite; chroń PCM/Call/grant i stosuj celowane focus lub auto <base-SHA>.
3. Jeden `full` na Macu po freeze, aktualizuj tylko `PROJECT.md` i dotknięty moduł, krótkie `[skip ci]` commity na `main`.
4. Fizyczne S22 / APK wyłącznie z konkretnym upoważnieniem, zweryfikowanym IDLE i kontrolowanym zakresem; nie deklaruj zamknięcia G6 wcześniej.

## Warunek zamknięcia G6
Oddzielnie udokumentowane **autoryzowane** INCOMING i OUTGOING na S22: własność Call, audio RX/TX i słyszalność, przerwanie/STOP/manual resume, rozłączenie, wiarygodny outcome, odpowiednio pełny zapis i playback; skutki zewnętrzne potwierdzone niezależnie. Host QA nie zastępuje tych wyników.
