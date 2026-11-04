# F-046 — eksperymentalny Gemma Direct RAW (2026-11-04)

## Dlaczego

W czterech realnych sesjach CLIR na S22 (`2026-11-04 02:56–03:07` lokalnie), wszystkie cztery pierwsze odpowiedzi modelu przeszły `SPEAK/GEMMA_DIRECT`, ale 9/9 następnych propozycji zostało odrzuconych przez parser/speech gate (`DIRECT_REJECTED`). W zweryfikowanym, przesłanym przez właściciela ZIP lokalnego odtwarzacza są zapisane wszystkie RX/ACK/TX WAV i zatwierdzone teksty; **nie ma tam surowej, odrzuconej odpowiedzi modelu**, więc jednoznaczna przyczyna odrzucenia pozostaje nieznana. Zapowiedź `Proszę chwilę zaczekać.` pochodzi od aplikacyjnego `QuickAcknowledgementPolicy`, a nie modelu.

## Zmiana na badanie izolacyjne

Na wyraźne życzenie właściciela Gemma Direct od tej rewizji jest eksperymentalną ścieżką **STT → LiteRT Gemma (zwykły tekst) → ogólna bramka aplikacyjna → TTS → TX**:
- `Gemma4LiteRtTextBackendFactory.createDirectDialogue` nie wymusza `ResponseFormat.json`.
- `GemmaDirectTaskBrief.PROMPT` nadal wprost podaje zadanie **CLIR** i brak prywatnych danych, ale prosi o 1–2 zdania po polsku, bez JSON, znaczników ani kodów akcji.
- `GemmaDirectDialogueBackend` nie używa `GemmaDirectProposalParser.parse` ani `GemmaDirectSpeechPolicy.review` w ścieżce mowy; przekazuje zwykły tekst modelu bez przepisywania poza `trim()`. Puste, wewnętrzne kontrolne `[[...]]` lub nadmiernie długie odpowiedzi trafiają do jawnie oznaczonego fallbacku `DIRECT_RAW_UNUSABLE`, nie do zewnętrznych skilli.
- `ChatRelayLiveCallProbe` **nie** opakowuje Raw Direct przez `controlAwareBackend`. Model nie może składać aplikacyjnych poleceń `COMMIT_CLIR_ENABLE` ani żądać `DISCLOSE_PHONE` przez parser JSON. Działają wspólne: dokładny aktywny grant, własny call, `STOP`, kontrole stanu workflow, finalny output release, STT/TTS/TX i prywatna obserwowalność.
- Zmienione testy JVM: naturalne słowa 1:1, cztery różne wypowiedzi bez JSON, brak uruchamiania kontrolnych tokenów i ujawniania faktów, błąd/empty fallback. AndroidTest: 4 natywne syntetyczne tury, bez połączenia i danych osobowych, sprawdzenie, czy każda zwraca używalny tekst.
- Nie zmieniono trybu `SCRIPT_GEMMA`, autoryzacji kampanii, sterowania połączeniami, budowy `TaskBrief` lub `F-045`.

**Zagrożenie:** To badawczy tryb swobodnej mowy, bez semantycznego filtra `SPEAK`; model może wypowiedzieć nieprawdziwe/nieuprawnione sformułowanie. Nie jest bezpiecznym, gotowym produktem i nie powinien służyć do samodzielnego akceptowania opłat, zmian taryfy, umów ani innych skutków. Brak parsowanych akcji zapobiega wewnętrznemu wykonaniu skilla, ale **nie gwarantuje**, że operator nie zinterpretuje słów modelu jako zgody. Kolejne fizyczne badanie wymaga nadzoru właściciela i aktywnego STOP.

## Rzeczywisty stan wykonania

Commity: `b3eb513d` (LiteRT: bez schema JSON), `8df42862` (raw prompt/flow), `9731ce63` (bez interpretera efektów), `5b16d788` (JVM tests), `0d44b7d5` (4-turn AndroidTest). **Kod na `main`; na S22 jeszcze niezweryfikowany dla RAW i nie należy zakładać instalacji.**

Mac Local Agent: zadanie `aicalls-20261104-f046-gemma-raw-host-qa-v1`, stan **PENDING/UNVERIFIED**, ponieważ nie ma `task-results` w momencie pisania. Ostatni dostępny `status.json` Mac Local Agenta miał `updated_at=2026-11-04T02:20:33Z`, a teraz jest później; nie twierdzić, że host QA przeszło. Nie uruchamiać GitHub Actions, Codex lub Work. Bez autonomicznego połączenia.

## Kontynuacja

1. Zbadać dostępność Mac Local Agenta i odebrać terminalny wynik host QA, naprawić ewentualne RED. Nie oznaczać PASS bez receipt.
2. Tylko przy dokładnym S22, IDLE, braku aktywnej kampanii lub retry i zachowanym grancie: bezpieczny off-call test 4 natywnych tur (oraz APK install, jeśli warunki pozwalają), bez żądania nowego połączenia.
3. Jeśli 4/4 raw off-call działają, właściciel świadomie uruchamia **jeden** autoryzowany nadzorowany test Gemma Direct z przycisku w aplikacji; zapisać prywatnie STT, model source `GEMMA_DIRECT_RAW`, zatwierdzony tekst, ACK/full TX, WAV i powód STOP. Monitorować skutki słowne, bez odbierania twardych bramek.
4. F-046 zamknąć dopiero przy stabilnym wieloturowym RAW; następnie zgodnie z priorytetem użytkownika F-044 `TaskBrief`, F-045 semantyczne hardening jako ostatnie.

## Aktualizacja: pełne Mac QA GREEN

Receipt Mac Local Agent `aicalls-20261104-f046-gemma-raw-host-qa-v1`: **DONE/0** z dokładnego commit `0d44b7d5a7e01daa118da10c2e04c458086dced9`. `scripts/verify_host.sh`: 3 × `BUILD SUCCESSFUL`, `Ran 248 tests` / `OK`, `host_quality_gate_green=true`, `ANTI_FRICTION=PASS`. ZERO połączeń i zmian telefonu w host QA. Te wyniki nadpisują wyżej zapisany wcześniejszy status `PENDING/UNVERIFIED`. **Nie są** jeszcze dowodem fizycznej generacji RAW na S22.

Uruchomiono kolejną bezpieczną próbę `aicalls-20261104-f046-gemma-raw-s22-offcall-v1`: warunki `IDLE`, brak aktywnej kampanii/retry, role dialera i niezmienione fingerprinty prefów są sprawdzane **przed** instalacją, a 4 natywne tury realizowane bez połączenia; do chwili terminalnego receipt status **PENDING**. Żadnego owner-initiated live test nie wykonał agent.
