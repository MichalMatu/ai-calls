# AI Calls — checkpoint przed sprintem faktów i synchronizacji tur (2026-11-04)

## Co zamrażamy

**Stan źródeł przed checkpointem:** `main` HEAD `960c2c66d112b46b8db21366e666feae13fc5c03` (ostatnia zmiana dokumentacyjna). **Ostatnia potwierdzona wersja APK na fizycznym S22:** `268be1f8f13502c7202822438b501706e0fcb0e1`, zawiera dynamiczny `GemmaDirectTaskBriefBuilder`, cztery wybieralne prompt-skills i prywatny dock `LiveCallChatPanel`. Mac QA `aicalls-20261104-live-chat-dock-host-qa-v1` DONE/0, full Android unit/lint/build + 248 Python + anti-friction PASS. S22 `aicalls-20261104-live-chat-dock-s22-offcall-install-v1` DONE/0: APK + AndroidTest zainstalowane; cztery syntetyczne natywne tury `PASS`, telefon `IDLE`, dialer/grant/continuation zachowane. **Baseline pierwszego działającego RAW bez parsera:** `0d44b7d5a7e01daa118da10c2e04c458086dced9`. Te SHAs są historycznymi punktami reprodukcji, nie przyszłym HEAD.

Działający tor **własny Call → Samsung/Shizuku RX → STT → per-call Gemma 4 E2B RAW (wieloturowa pamięć) → lokalne TTS → autoryzowane TX** i jego aplikacyjny grant, STOP, release oraz izolację prywatności zostawić nienaruszone w następnym sprincie. Starszy `SCRIPT_GEMMA` pozostaje trybem domyślnym; właściciel wybiera `CAMPAIGN TOOLS → Start authorized CLIR enable — Gemma Direct` do live RAW. Główny „Rozpocznij autoryzowane zadanie AI” uruchamia `SCRIPT_GEMMA`, a nie RAW. Brak samodzielnie wykonywalnego tool-calling w obecnym RAW.

## Fizyczny dialog: dowody najnowsze (bez treści prywatnych)

Ostatni odczyt S22 przez Mac Local Agent `aicalls-20261104-closeout-latest-session-metadata-v1` DONE/0. Dwie najnowsze sesje mają prywatne, kompletne `timeline.jsonl` oraz pliki WAV:

| Sesja (Europe/Warsaw) | STT biura | Odpowiedzi Gemma RAW | Pełne lokalne TX | RX przerwane | Zakończenie |
| --- | ---: | ---: | ---: | ---: | --- |
| 2026-11-04 06:30:59 | 8 | 8 | 8/8 `WRITE_COMPLETE` | 1 (kolejne RX po ósmej odpowiedzi) | `owned_call_ended` |
| 2026-11-04 06:35:27 | 7 | 7 | 7/7 `WRITE_COMPLETE` | 0 | `operator_stopped` |

Obie sesje mają **0 `DIRECT_REJECTED`, 0 `DIRECT_MODEL_ERROR`, 0 `DIRECT_RAW_UNUSABLE`**. Właściciel potwierdził, że dialog i prywatny dock czatu działają zaskakująco dobrze; ta opinia to *odbiór użytkownika*, nie dowód zakończenia CLIR, odsłuchu dalekiego końca ani semantycznego bezpieczeństwa tekstu. Ostatni preflight S22 (zadanie `aicalls-20261104-closeout-readonly-confirm-stop-v1`) wykazał `OFFHOOK → IDLE → IDLE` i `CAMPAIGN_RETRY_PENDING=false`. Eksport prywatnych danych wykonano **dopiero przy IDLE**.

## Prywatny transkrypt i szczegóły pierwszej tury

Mac Local Agent `aicalls-20261104-transcript-first-turn-overlap-private-audit-v1` DONE/0, `PRIVATE_EXPORT=PASS`; kompletna kopia na **Macu, bez kopiowania PII do GitHub**:

- Archiwum `~/Documents/AI-Calls/private-exports/call-evidence-20261104T054132Z-1b65RKoF.tar.gz` — 0600.
- Czytelny HTML z transkryptem i odsłuchem WAV `~/Documents/AI-Calls/private-exports/review-20261104T054132Z-1b65RKoF/index.html` — katalog 0700.

**Sesja 06:35, tura 1:** RX okno od +1,876 s, długość 3,620 s; STT final +5,518 s, **tylko 3 słowa powitania IVR**, bez pytania o usługę. Po rozpoczęciu mowy w oknie wystąpiło ok. 1,65 s sygnału poniżej progu RMS=600, więc obecny VAD z `trailingSilenceMs=1500` uznał pauzę za koniec tury. Gotowa neutralna zapowiedź `AGENT_ACK_TX` rozpoczęła się już +5,800 s (ok. **304 ms po końcu okna RX**), trwała 1,512 s; model zatwierdził pierwszą 21-wyrazową odpowiedź dotyczącą CLIR w +9,553 s (inferencja 4030 ms), a jej rzeczywisty plik PCM wysłano od +10,184 s przez ok. 10,230 s (`AGENT_FULL_TX` zapisany po wykonaniu). Druga tura biura była rozpoznana po oknie RX od +20,728 s do +31,148 s i doprowadziła do drugiej odpowiedzi Gemmy; cały dialog trwał siedem tur.

**Sesja 06:30, tura 1:** RX 3,080 s, STT 3 słowa powitania, ACK rozpoczyna się +5,674 s, Gemma PCM +10,748 s. Schemat wstępnego przerwania powitania powtarza się.

**Wniosek F-049 (niedomknięty):** użytkownik podczas rzeczywistej rozmowy usłyszał, że pierwsza odpowiedź Gemmy weszła przed końcem wypowiedzi infolinii, która musiała ponowić informację. Pomiary potwierdzają **bardzo wczesny endpoint po samym powitaniu** i osobną zapowiedź jeszcze przed wygenerowaną odpowiedzią. WAV RX obejmuje *tylko okna nasłuchu*, więc nie pozwala bezpośrednio dowieść akustycznego nakładania po stronie operatora po oknie RX; obserwację właściciela odnotować jako sygnał empiryczny, nie mylić z pełnym dowodem duplex. Nie leczyć tego arbitralnym globalnym opóźnieniem. Następny sprint: rozsądny model zakończenia wypowiedzi / wykrycie niekompletnego powitania IVR, test przyspieszenia i wstrzymania ACK/TX przy kontynuowanym głosie, bez regresji responsywności, bez nowej sztucznej bramki ani zmian Samsung media bez uzasadnienia.

## Problem F-048: brak dozwolonych faktów

Właściciel zgłosił, że prowadzenie rozmowy działa, ale model nie ma danych potrzebnych do odpowiedzi na pytania biura. W prywatnej sesji 06:35 tura 5 zawierała sygnał prośby o weryfikację tożsamości; nie kopiujemy wypowiedzi ani danych abonenta do repo. W aktualnym `GemmaDirectTaskBriefBuilder` model zna **cel CLIR, rolę dzwoniącego, ograniczenia i prompty skills**, ale nie zna plaintext `IdentityVault`; RAW nie ma działającego typowanego `RequestFact`. Prompt-skills są instrukcjami, **nie modułami pobierającymi dane**.

Następny etap architektoniczny: typed `FactRequest` i deklaratywny katalog identyfikatorów faktów potrzebnych do autoryzowanego zadania; `FactDisclosurePolicy` + dokładny grant/odbiorca/typ/zakres danych ocenia każde ujawnienie; `IdentityVault` odczytuje plaintext tylko po tej walidacji; brak faktu → kontrolowane pytanie do właściciela bez zgadywania. **Nie** preloadować wrażliwych danych do globalnego promptu, nie pozwalać Gemmie wywoływać dowolnych narzędzi, nie poszerzać autoryzacji CLIR i nie przyjmować instrukcji z IVR/STT jako nowego grantu. Osobna kontrola semantycznej treści RAW (`F-045`) pozostaje wymagana przed szerokim wdrożeniem.

## Otwarta lista / priorytet

- **F-046:** techniczna przyczyna `DIRECT_REJECTED` usunięta przez RAW, wieloturowy real-call **VERIFIED** w zakresie 8/8 + 7/7. Nie oznacza to udanego CLIR.
- **F-044:** dynamiczny CLIR TaskBrief + cztery prompt-skills **VERIFIED dla CLIR** na Macu, S22 off-call i realnym wieloturowym dialogu. Rozszerzenie na inne typy zadań otwarte.
- **F-047:** `RX_INTERRUPTED` działa (wystąpiło w sesji 06:30); przechwycenie transkryptu po rozłączeniu nadal OPEN. Nie dopuszczać nowego TX po END.
- **F-048:** bezpieczne dostarczanie dozwolonych faktów z IdentityVault — OPEN, kolejny sprint.
- **F-049:** niedojrzałe turn-taking/powitanie IVR, wczesna zapowiedź — OPEN, konieczny test pomiarowy.
- **F-045:** niezależna semantyczna walidacja naturalnych wypowiedzi RAW — OPEN.

Żadnego nowego telefonu, instalacji, mutacji grantu, czyszczenia prywatnych danych ani zmiany aplikacji w sprincie dokumentacyjnego zamknięcia. Również historii checkpointów i dawnego `agent-control` nie usuwać: są audytowalnymi dowodami, a nie „szumem” aktualnego handoffu.
