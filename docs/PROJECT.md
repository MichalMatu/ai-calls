# AI Calls — aktualny stan i kolejka rozwoju

**Stan źródłowy:** G6.1 source freeze po Mac QA 2026-11-09; aktualny commit ustalaj z origin/main.

## Cel i fakty
- Jeden lokalny agent i jeden wspólny runtime INCOMING/OUTGOING. Bieżący produkt jest **modułowym laboratorium**, finalnie jeden pakiet instalacyjny. Obecny wybór: Gemma 4 E2B/GPU, CONTEXTUAL Skills, Android STT/TTS i Samsung/Shizuku.
- **G3/G4:** wybór E2B po 96/96 native tur; dynamic Skills S22 PASS w zakresie off-call. **G5.3:** 18/18 native off-call tur i 36 prywatnych WAV, rzeczywisty STOP STT/model/TTS sprawdzony. Indywidualna ocena człowieka: **0/6, UNASSESSED** (nie wpisuj human PASS).
- **G6 source/host:** INCOMING reentrant takeover/STOP, atomowe Realtime barge-in/flush, odtwarzanie *dostępnych* prywatnych WAV, zakończenie/interrupted RX w historii, lokalny G6.1 TX ma ogrodzenie epoką tury (stare callbacki/PCM 20 ms); heurystyczny RX-during-TX z anulowaniem TTS, buforowanym STT i fail-closed po downlink EOF jest zaimplementowany źródłowo; echo/double-talk oraz realny S22 pozostają PHYSICAL_OPEN. Pełny Mac host QA aicalls-g61-barge-source-freeze-full-mac-20261109-v1: PASS, 409 Python i Gradle/lint PASS. INCOMING: reentrant readiness (SIM/exclusive/drugi AI Call) oraz tożsamość pełnej polityki przy zmianie zgody, live audio i end_call osłonięte testami jednostkowymi. Bez APK i realnych Call w tym sprincie.
- Historyczne dwa INCOMING A8→S22 dowiodły fragmentów odbioru i zakończenia własnego połączenia, **nie** całości G6. Nie powtarzaj G3 96 tur, G5.3 18 tur ani fizycznego STOP bez zmiany kontraktu.

## Otwarte — tylko rzeczywista następna praca
| Priorytet | Moduł | Brakujący warunek odbioru | Najmniejsza potrzebna weryfikacja |
| --- | --- | --- | --- |
| P0 | G6.1 / CONVERSATION | Lokalny Gemma/STT/TTS: wykrycie RX podczas TX, anulowanie starego TTS i bezpieczne wznowienie na realnym Call | Test generacji+stale PCM, następnie **autoryzowany** S22 barge-in |
| P0 | G6.1 / INCOMING | Human TAKE OVER/resume tej samej sesji bez rozłączenia; dokładna polityka stop/error/recovery | Regresja jednostkowa i jedna kontrolowana fizyczna próba |
| P0 | AUDIO_HISTORY | Pełne nagranie zgodne z uprawnieniami/zgodą, w tym dopuszczalna część manualna oraz płynna oś czasu. Powtarzane okna RX zachowują osobne WAV; historia weryfikuje prywatne timeline/COMPLETE i pomija symlink escape; playlist odtwarza **fragmenty**, nie cały Call | Pokrycie RX/TX/manual, retencja, no-leak, kontrolowany S22 playback |
| P1 | G6.2 / OUTGOING | Dokładnie autoryzowany outbound, integracja IVR/DTMF z app-owned Telecom; gate JVM + Android Telecom tone adapter mają osobne testy i seam, ale brak autoryzowanego wywołania IVR i testu fizycznego | Focused port test, prawdziwy IVR wyłącznie po grancie/IDLE |
| P1 | BOOKING_EFFECTS | Typowany wynik i niezależny dowód rezerwacji/CLIR; syntetyczny availability/hold (TTL, wyłączność slotu, cancel)/confirm + provider read-back; realny adapter nadal OPEN | Syntetyczny adapter, idempotencja, potem osobny autoryzowany dowód usługodawcy |
| P1 | SECURITY | Typowany późny scoped-read z IdentityVault (policy + dwukrotne live authority) dostępny jako app port; CLIR AuthorizedPhoneFactBackend korzysta ze scoped reader i ponownej live kontroli po formacie/obserwatorze; ogólne narzędzie innych faktów nadal OPEN (F-048); BOOK_APPOINTMENT: app-owned walidator zgodności exact proposal/ograniczeń/potwierdzenia przed permit; inne semantyki skutków i fizyczny dowód nadal OPEN (F-045) | Denied/approved scoped tests bez danych rzeczywistych |
| P2 | CONVERSATION | Końcowe RX/STT po rozłączeniu przed ciszą VAD (dawna F-047); jakość długich wypowiedzi | Syntetyczny końcowy fragment, później fizyczna próba |
| P2 | G7 / UI | Uproszczenie UX i finalny product packaging, dopiero po G6 | Celowane UI/functional acceptance |

**Nie otwierać ponownie naprawionych problemów tylko dlatego, że kiedyś miały numer.** Nowa luka trafia w odpowiedni istniejący wiersz lub zastępuje zamkniętą; brak nowej historii/chronologii. Testów nie nazywaj PASS bez faktycznego wykonania.

## Następny sprint
1. Pobierz aktualny `main`, sprawdź realny stan Mac Local Agent i konflikty. Wybierz pionowy zakres G6.1 (lokalne przerwanie i takeover) z tabeli, nie kolejne audyty.
2. Dla potwierdzonej luki G6.1 wykonaj krótki **Existing Code First** według [EXTERNAL_REUSE](operations/EXTERNAL_REUSE.md): baseline własnego kodu → najwyżej 2–3 kandydatów → KEEP/IMPROVE/ADAPT/REPLACE z dowodem. Nie kopiuj ani nie przepisuj działającego kodu bez wykazanej przewagi; potem implementuj kilka powiązanych funkcji; po porcjach tylko `focus` lub `auto <base-SHA>`. Nie zmieniaj zamrożonego PCM/Call/grant bez dedykowanego testu.
3. Jeden `full` na Macu po freeze, aktualizuj tylko `PROJECT.md` i dotknięty moduł, krótkie `[skip ci]` commity na `main`.
4. Fizyczne S22 / APK wyłącznie z konkretnym upoważnieniem, zweryfikowanym IDLE i kontrolowanym zakresem; nie deklaruj zamknięcia G6 wcześniej.

## Warunek zamknięcia G6
Oddzielnie udokumentowane **autoryzowane** INCOMING i OUTGOING na S22: własność Call, audio RX/TX i słyszalność, przerwanie/STOP/manual resume, rozłączenie, wiarygodny outcome, odpowiednio pełny zapis i playback; skutki zewnętrzne potwierdzone niezależnie. Host QA nie zastępuje tych wyników.
