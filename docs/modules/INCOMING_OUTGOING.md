# Telefonia — INCOMING, OUTGOING, IVR/DTMF

**Exact Call ownership:** `AndroidIncomingAssistant`, `IncomingAssistantCoordinator`, `AndroidOwnedInCallService`, `AndroidTelecomOwnedCallController`, `CampaignOwnedCallLifecycle`. Inne trwające połączenie unieważnia wyłączność AI; spóźnione callbacki nie mogą wybrać/odebrać/rozłączyć obcego Call.

## INCOMING
RINGING → odpowiedź tylko w AI policy i przy gotowości/account match → ACTIVE → wspólny runtime. Manual answer lub TAKE OVER unieważnia generację AI, nie rozłącza Call. Resume wymaga tej samej aktywnej sesji i sprawdzenia polityki; ENDED nie przechodzi w MANUAL przez późny callback. Readiness po reentrant callbacku sprawdza SIM, exclusivity i drugi AI Call. Zmiana pełnej polityki (ID, trybu, SIM lub zgody na zapis) unieważnia live AI także po pasywnym observe bez callbacku ustawień; Android wiąże AudioSlot z polityką, end_call wymaga identycznej polityki, a wyjątek podczas cleanup audio nie wstrzymuje human takeover. Fizyczne takeover/resume OPEN.

## OUTGOING
Task/grant+SIM+target → exact app-owned dial, nadzorowany lifecycle z STOP → wspólny runtime → typowany outcome. Żadnego uniwersalnego ograniczenia do Orange; rzeczywista próba wymaga konkretnego upoważnienia i potwierdzenia IDLE. Zewnętrzny efekt nie jest dowodem sukcesu tylko dlatego, że rozmowa się zakończyła.

## IVR / DTMF
`OutgoingIvrDtmfGate` w `conversation-core`: poprawny digit, generation, turn, step, dwa sprawdzenia exact Call+grant, brak ponowienia niepewnego dispatch, limit rejestru 256 kroków. Mac focused 11 testów PASS. **Własny adapter Telecom z parą play/stop i exact Call istnieje jako grant-bound seam, bez podłączonej akcji AI/IVR; nie wysłano żadnego tonu**. `A-D` są odrzucane (API Android akceptuje tylko `0-9*#`); aktywny ton kończy się po 150 ms lub przy STOP/takeover/removal. `DISPATCHED` oznacza tylko rozpoczęcie tonu i zaplanowane stop, nie potwierdzenie przez IVR. Przy integracji dodaj typed hold/transfer/menu/outcome, ale nie nowego runnera ani obejścia STOP.

## Fizyczny zakres G6
Oddzielny kontrolowany live INCOMING (human takeover/resume, speech cut, historia) i OUTGOING (autoryzowany IVR i wynik). Historia wcześniejszych A8→S22 nie zamyka tych testów. [Current](../PROJECT.md), [security](../architecture/SECURITY.md).
