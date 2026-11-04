# Gemma Direct — pierwsze rzeczywiste połączenia i analiza prywatnych RX/STT/TTS/TX (2026-11-04)

**Stan:** realna linia telefoniczna / wybrany `GEMMA_DIRECT` / `OWNED_ACTIVE` na S22 zostały fizycznie potwierdzone (3 próbki OFFHOOK). W końcowym preflight `IDLE` 2/2 i po eksporcie IDLE; prywatny eksport i analizator PASS. **Głos Gemmy wyszedł na lokalny TX; wieloturowa rozmowa nie osiągnęła stabilności — F-046 OPEN.** Nie ma niezależnego potwierdzenia akustycznego odsłuchu u konsultanta ani sukcesu CLIR. Nie wykonywano połączeń, STOP ani instalacji z poziomu agenta; użytkownik wykonał start/STOP w aplikacji.

## Materiał dowodowy i poufność

- Kod aplikacji uruchomiony na dokładnym S22 `SM-S906B`: commit `2424118318caaca03d6330129bddf4190715677c` (źródło pełnego host QA i off-call instrumentacji). Domyślny Script + Gemma pozostawał oddzielny.
- Zadanie `aicalls-20261104-gemma-direct-live-3sample-readonly-v1`: 3 × `OFFHOOK`, 3 × `OWNED_ACTIVE`, 3 × `GEMMA_DIRECT`.
- Zadanie `aicalls-20261104-gemma-direct-postcall-completed-review-v2`: `DONE/0`, exact S22, `PHONE_CHECK_1/2=IDLE`, `USB_EXPORT=PASS`, 22 sesje ogółem, analizator lokalnego HTML `VIEWER_VALID=true`; bez odszyfrowanych treści/numerów opublikowanych w GH.
- Prywatne archiwum na Macu: `~/Documents/AI-Calls/private-exports/call-evidence-20261104T021532Z-W7WSvgrR.tar.gz` (0600, 29 541 877 bajtów).
- Prywatny odtwarzacz z osią czasu: `~/Documents/AI-Calls/private-exports/review-20261104T021532Z-W7WSvgrR/index.html` (otwarty na Macu przez zadanie `aicalls-20261104-gemma-direct-postcall-utterance-origin-v1` DONE/0). Tekst rozmowy, nazwę sesji, RX/ACK/TX WAV obejrzeć i odsłuchać wyłącznie lokalnie. Nie wypisywać transkryptów, danych i ścieżek sesji z identyfikatorami do GitHub.
- `QuickAcknowledgementPolicy.TEXT` = „Proszę chwilę zaczekać.”, z osobnym `AGENT_ACK_TX` — to **gotowa zapowiedź aplikacji**, a nie treść wygenerowana przez Gemmę. Analiza prywatnego JSONL użytkownikowych wskazówek `WAIT` wykryła wyłącznie `AGENT_ACK_TX` w 4 najnowszych sesjach; zgłaszanej frazy „nie udało się potwierdzić danych” nie rozpoznano w ich tekstach STT/AI. Nie oznacza to, że dźwięk RX na pewno jej nie zawierał. Prawdziwy odsłuch WAV pozostaje niezależnym dowodem.
- RX pliki obejmują tylko okna nasłuchu STT; TX zapisuje bajty przyjęte przez lokalny uplink, a nie gwarancję odsłuchu po stronie konsultanta.

## Wyniki czterech najnowszych sesji Gemma Direct

| Start lokalny | STT rozmówcy | AI zatwierdzone | AI ACK | AI full TX | Decyzje modelu | WAV | Koniec |
| --- | ---: | ---: | ---: | ---: | --- | --- | --- |
| 02:56:09 | 4 | 4 | 4 | 4 | T1 `SPEAK/GEMMA_DIRECT` 25 069 ms, T2–T4 `ASK_CLARIFY/DIRECT_REJECTED` 3 361 / 3 433 / 2 610 ms | 13/13 poprawnych (5 RX/4 ACK/4 TX) | `owned_call_ended` |
| 02:59:20 | 4 | 4 | 4 | 4 | T1 `SPEAK/GEMMA_DIRECT` 13 602 ms, T2–T4 `ASK_CLARIFY/DIRECT_REJECTED` 3 703 / 2 452 / 2 392 ms | 13/13 poprawnych | `owned_call_ended` |
| 03:02:51 | 4 | 4 | 4 | 4 | T1 `SPEAK/GEMMA_DIRECT` 9 203 ms, T2–T4 `ASK_CLARIFY/DIRECT_REJECTED` 2 992 / 2 875 / 2 674 ms | 13/13 poprawnych | `owned_call_ended` |
| 03:07:10 | 1 | 1 | 1 | 1 | T1 `SPEAK/GEMMA_DIRECT` 9 462 ms, początek następnego okna RX bez STT | 4/4 poprawne (2 RX/1 ACK/1 TX) | `operator_stopped` |

Łącznie dla tych czterech sesji: **13 zatwierdzonych wypowiedzi AI przyjętych przez lokalny TX**, w tym **4 modelowo ułożone `SPEAK` (pierwsza tura każdej próby)** i **9 awaryjnych dopytań `DIRECT_REJECTED` (tury 2–4 trzech dłuższych prób)**. **43/43 WAV strukturalnie poprawne**: 17 RX, 13 ACK, 13 TX. Niektóre sesje miały piąte okno nasłuchu RX bez STT, które skończyło się wraz z rozmową.

Ostatni techniczny raport: `dialogue_mode=gemma_direct`, `probe_complete=true`, `turns_completed=1`, `failure_reason=operator_stopped`, `telephony_rx_pcm_bytes=98560`, `telephony_tx_pcm_bytes=288364`, `clir_external_success=false`; pierwsza tura `Gemma 9462 ms`, `EOS→ACK TX 1804 ms`, `EOS→full TX 13552 ms`, `stt_chars=16`, `approved_chars=116`, `quick_ack_tx_bytes=48380`. Te czasy nie obejmują zewnętrznego odsłuchu.

## Interpretacja i kolejność dalszych prac

1. **F-046 OPEN — najbliższa naprawa wynikająca z prawdziwego testu**, a nie rozszerzenie TaskBrief: `GemmaDirectProposalParser.parse` zwrócił odrzucenie w 9/9 późniejszych turach. Z obecnych metryk nie wiadomo, czy powodem był JSON/schema, token limit, niezgodna `action`, niedozwolony tekst czy kontekst. Nie spekulować, nie oznaczać naprawy jako zamkniętej. Najpierw dodać bezpieczny typowany kod przyczyny, syntetyczne testy 4+ tur i poprawić potwierdzoną przyczynę, nie ruszać `SCRIPT_GEMMA`.
2. **Ponowny eksperyment live** po Mac QA / syntetycznym S22, start przez użytkownika w aplikacji w dokładnym, nadal ważnym grancie; zachować nagrywanie za zgodą i twarde uprawnienia. Porównać tę samą oś STT→Gemma→TTS→TX, a faktyczne słyszenie i CLIR osobno. Nie wykonywać zdalnego dial ani nie obchodzić wcześniejszej odmowy narzędzia.
3. **Dopiero po stabilnym realnym dialogu — F-044**, dynamiczny `TaskBrief` z typowanym zadaniem, faktami i dozwolonymi skillsami.
4. **Na końcu zgodnie z decyzją użytkownika — F-045**, dodatkowe semantyczne hardening wolnej mowy. **Istniejące** zabezpieczenia i twarde reguły grantu, ujawniania i wykonania skutków obowiązują już teraz; eksperyment nie jest certyfikacją produkcyjną.

## Rozróżnienie PASS vs OPEN

**PASS:** wybór `GEMMA_DIRECT`, realny owned-call, wielokrotne STT, własne `SPEAK` Gemmy na TTS/TX, zapis i poprawny odczyt RX/TX/ACK WAV, stan końcowy S22 IDLE i lokalne archiwum.

**OPEN:** 9/9 późniejszych tur jest odrzucane i zastępowane fallbackiem, przyczyna odrzucenia nieustalona; brak dowodu pełnej słyszalności i zewnętrznego CLIR; F-044/F-045 niezaimplementowane.
