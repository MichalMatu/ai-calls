# Telefonia — INCOMING, OUTGOING, IVR/DTMF

**Exact Call ownership:** `AndroidIncomingAssistant`, `IncomingAssistantCoordinator`, `AndroidOwnedInCallService`, `AndroidTelecomOwnedCallController`, `CampaignOwnedCallLifecycle`. Inne trwające połączenie unieważnia wyłączność AI; spóźnione callbacki nie mogą wybrać/odebrać/rozłączyć obcego Call.

## INCOMING
RINGING → odpowiedź tylko w AI policy i przy gotowości/account match → ACTIVE → wspólny runtime. Manual answer/TAKE OVER unieważnia AI bez rozłączania. Resume wymaga tej samej sesji i najnowszej polityki; ENDED nie wraca do MANUAL. Reentrant readiness sprawdza SIM, exclusivity i drugi AI Call. Zmiana ID, trybu, SIM lub zgody na zapis unieważnia live AI także po pasywnym observe. AudioSlot i end_call są związane z polityką, exact Call, generacją i ENDING również bezpośrednio przed disconnect(). Takeover zamyka pending recorder i działa mimo błędu cleanup; STOP w ENDING nadal zatrzymuje audio. Wynik END_CALL jest CONFIRMED wyłącznie po rozłączeniu tego samego Call nadal w ENDING; przejęcie/manualne rozłączenie daje UNKNOWN. Telecom outcome czeka na zamknięcie RX WAV; DISCONNECTING zachowuje właściciela do DISCONNECTED. Fizyczne takeover/resume OPEN.

## OUTGOING
Task/grant+SIM+target → exact app-owned dial, nadzorowany lifecycle z STOP → wspólny runtime → typowany outcome. Żadnego uniwersalnego ograniczenia do Orange; rzeczywista próba wymaga konkretnego upoważnienia i potwierdzenia IDLE. Zewnętrzny efekt nie jest dowodem sukcesu tylko dlatego, że rozmowa się zakończyła.

## Trwałe zadania i harmonogram
`PhoneTask` przechowuje niezmienny zatwierdzony cel, szablon, dokładne numery/SIM, strefę i okno dzwonienia; każdy odbiorca ma oddzielne próby oraz prywatny wynik. `PhoneTaskRepository` korzysta z istniejącego szyfrowanego Keystore/AtomicFile. Rezerwacja próby jest trwała przed dial; wynik nieznany po restarcie wstrzymuje zadanie. BUSY/REMOTE wymagają obserwowanego setup i braku ACTIVE/czasu połączenia exact Call; timeout daje UNKNOWN. Przerwa retry należy do zadania, bez kwoty prób.

`AlarmManager.setExactAndAllowWhileIdle` wybudza istniejący `AppOwnedCampaignService`; BOOT/package/time events jedynie odtwarzają alarm. Intent nie zawiera numeru ani grantu. Przed próbą aplikacja sprawdza gotowość, okno, IDLE i SIM, tworzy osobny exact-target grant i wywołuje dotychczasowy executor. Scenariusz ma świeży kontekst per odbiorca. Usługa kolejki używa typu phoneCall dla domyślnego dialera; PCM nadal należy do istniejącego helpera. Odmowa Androida daje stan wstrzymany i powiadomienie, nigdy pozorny sukces.

## IVR / DTMF
`OutgoingIvrDtmfGate` w `conversation-core`: poprawny digit, generation, turn, step, dwa sprawdzenia exact Call+grant, brak ponowienia niepewnego dispatch, limit rejestru 256 kroków. Mac focused 11 testów PASS. **Własny adapter Telecom z parą play/stop i exact Call istnieje jako grant-bound seam, bez podłączonej akcji AI/IVR; nie wysłano żadnego tonu**. `A-D` są odrzucane (API Android akceptuje tylko `0-9*#`); aktywny ton kończy się po 150 ms lub przy STOP/takeover/removal. `DISPATCHED` oznacza tylko rozpoczęcie tonu i zaplanowane stop, nie potwierdzenie przez IVR. NOT_READY oznacza odmowę przed play (poprzedni ton trwa albo brak aktualnego scope) i pozwala ponowić dokładnie ten sam krok; UNKNOWN po możliwym play pozostaje terminalne bez automatycznego replay. Błąd stopDtmfTone zamyka port dla nowych tonów; późniejsze close może jedynie ponowić stop dokładnie tego samego Call. Przy integracji dodaj typed hold/transfer/menu/outcome, ale nie nowego runnera ani obejścia STOP.

## Fizyczny zakres G6
Oddzielny kontrolowany live INCOMING (human takeover/resume, speech cut, historia) i OUTGOING (autoryzowany IVR i wynik). Historia wcześniejszych A8→S22 nie zamyka tych testów. [Current](../PROJECT.md), [security](../architecture/SECURITY.md).
