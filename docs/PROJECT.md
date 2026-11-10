# AI Calls — aktualny stan i kolejka rozwoju

**Stan źródłowy:** G6.4 freeze przed G6.5; aktualny HEAD zawsze z origin/main. Real-call S22 PHYSICAL_OPEN.

## Cel i fakty
- Jeden lokalny agent i jeden wspólny runtime INCOMING/OUTGOING. Bieżący produkt jest **modułowym laboratorium**, finalnie jeden pakiet instalacyjny. Obecny wybór: Gemma 4 E2B/GPU, CONTEXTUAL Skills, Android STT/TTS i Samsung/Shizuku.
- **G3/G4:** wybór E2B po 96/96 native tur; dynamic Skills S22 PASS w zakresie off-call. **G5.3:** 18/18 native off-call tur i 36 prywatnych WAV, rzeczywisty STOP STT/model/TTS sprawdzony. Indywidualna ocena człowieka: **0/6, UNASSESSED** (nie wpisuj human PASS).
- **G6 source/host:** Exact Call/consent, STOP/TAKE OVER, END_CALL po RX drain, ENDING rezerwuje zasoby; TX statusy i historia WAV z ochroną symlinków; downlink ma zero-read backoff. Jednorazowy commitment i syntetyczny provider hold/TTL/cancel/confirm/read-back są testowane, lecz nie są prawdziwą rezerwacją. Mac full QA dla 04a72f2: Gradle/JVM/lint, 409 Python, audio i preflight **PASS**. S22 exact/IDLE/dialer: PASS; na **wcześniej zainstalowanej** aplikacji 4/4 off-call instrumentation PASS (takeover/stale PCM, RX archive, syntetyczny WAV playback, STOP). Zgodność wersji z 04a72f2 NIEZWERYFIKOWANA; bez nowego APK, realnego Call i zewnętrznego efektu. G6 live INCOMING/OUTGOING, S22 barge-in, manual resume i pełne manual audio nadal **PHYSICAL_OPEN**.
- Historyczne A8→S22 potwierdziły tylko fragment RX i własne rozłączenie, nie całe G6. Nie powtarzaj G3/G5.3 off-call.

## Otwarte — tylko rzeczywista następna praca
| Priorytet | Moduł | Brakujący warunek odbioru | Najmniejsza potrzebna weryfikacja |
| --- | --- | --- | --- |
| P0 | G6.1 / CONVERSATION | Lokalny Gemma/STT/TTS: wykrycie RX podczas TX, anulowanie starego TTS i bezpieczne wznowienie na realnym Call | Test generacji+stale PCM, następnie **autoryzowany** S22 barge-in |
| P0 | G6.1 / INCOMING | Human TAKE OVER/resume tej samej sesji bez rozłączenia; dokładna polityka stop/error/recovery | Regresja jednostkowa i jedna kontrolowana fizyczna próba |
| P0 | AUDIO_HISTORY | Pełny legalny zapis RX/TX/manual i oś czasu. Obecnie są prywatne fragmenty WAV, bez pełnego manual audio; retencja i symlink guard istnieją | No-leak/retencja i S22 playback |
| P1 | G6.2 / OUTGOING | Autoryzowany IVR/DTMF dla exact Call; gate i Telecom port mają testy, ale brak realnego IVR | Test portu, potem S22 po grancie/IDLE |
| P1 | BOOKING_EFFECTS | Syntetyczny hold/TTL/cancel/confirm/read-back istnieje; realny adapter i zewnętrzny dowód nadal OPEN | Idempotencja i autoryzowany dowód usługodawcy |
| P1 | SECURITY | Scoped IdentityVault: policy + podwójny live check; CLIR korzysta ze scoped reader. Inne narzędzia faktów F-048 OPEN. BOOK_APPOINTMENT ma app-owned exact proposal/constraints/consent gate; inne efekty F-045 OPEN | Denied/approved scoped tests bez realnych danych |
| P2 | CONVERSATION | WAV przerwanego RX jest domknięty przed END; końcowy STT po rozłączeniu przed ciszą VAD (F-047) i jakość długich wypowiedzi nadal OPEN | Syntetyczny końcowy fragment, później fizyczna próba |
| P2 | G7 / UI | Uproszczenie UX i finalny product packaging, dopiero po G6 | Celowane UI/functional acceptance |

**Nie otwierać ponownie naprawionych problemów tylko dlatego, że kiedyś miały numer.** Nowa luka trafia w odpowiedni istniejący wiersz lub zastępuje zamkniętą; brak nowej historii/chronologii. Testów nie nazywaj PASS bez faktycznego wykonania.

## Następny sprint
1. G6.4 host + zainstalowany S22 off-call są zielone; przed G6.5 nadal wymagany konkretny grant na SIM/target/dane i realne INCOMING/OUTGOING. Bez tego nie dzwoń ani nie instaluj; PHYSICAL_OPEN pozostaje.
2. **Existing Code First** ([reguła](operations/EXTERNAL_REUSE.md)): własny baseline → do 3 kandydatów → KEEP/IMPROVE/ADAPT/REPLACE z testem. Bez rewrite; chroń PCM/Call/grant i stosuj celowane focus lub auto <base-SHA>.
3. Full QA G6.4 odebrany (aicalls-g64-freeze-fullqa-20261110-v1), urządzenie off-call (aicalls-g64-s22-installed-instrumentation-physical-offcall-20261110-v1). Kolejne testy tylko dla zmienionego kontraktu, bez powtarzania zamrożonego QA.
4. Fizyczne S22 / APK wyłącznie z konkretnym upoważnieniem, zweryfikowanym IDLE i kontrolowanym zakresem; nie deklaruj zamknięcia G6 wcześniej.

## Warunek zamknięcia G6
Oddzielnie udokumentowane **autoryzowane** INCOMING i OUTGOING na S22: własność Call, audio RX/TX i słyszalność, przerwanie/STOP/manual resume, rozłączenie, wiarygodny outcome, odpowiednio pełny zapis i playback; skutki zewnętrzne potwierdzone niezależnie. Host QA nie zastępuje tych wyników.
