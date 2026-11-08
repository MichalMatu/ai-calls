# AI Calls — prywatna diagnostyka przez USB/ADB (wybrana ścieżka właściciela)

**Nowość 2026-11-08:** osobny odbiornik DUMP dla **Laboratorium i metadanych telefonii**, bez publicznego portu sieciowego, z istniejącymi kontrolerami interwencji i konfiguracji. Host full gate PASS, fizyczny S22 nowego kanału NOT_RUN. [Dokładna instrukcja](USB_LAB_TELEPHONY_CHANNEL.md). Dotychczasowy eksport prywatnych WAV oraz `ai_calls_usb_debug.py` pozostają bez zmian.

**AKTUALNY WYNIK — F-041 VERIFIED (2026-11-03):** [checkpoint z dowodami](checkpoints/2026-11-03-f041-private-rx-tx-silence-f042-latency.md). Rzeczywisty S22 zawiera prywatne nagrania (`GRANT_RECORD_EVIDENCE=true`, `private_audio_recording=true`); stary eksport błędnie raportował `NO_DATA` przez wadliwe `run-as sh -c`. Eksporter i 5/5 syntetycznych testów poprawione. Mac Local Agent `aicalls-20261103-f041-export-completed-wav-readonly-v2` **DONE/0**, `USB_EXPORT=PASS`: archiwum 5 679 205 B, chmod 0600, **4 kompletne sesje**, z ostatniej 4 RX + 3 TX WAV i prywatną osią czasu. Całe audio pozostaje wyłącznie na Macu, nie publikowano do GitHuba. Potwierdzony poważny defekt jakości **F-042 OPEN**: ok. 8,5–10,6 s między wynikiem STT a TX (11,7–11,9 s EOS→TX). **Wszystkie historyczne `USB_EXPORT=NO_DATA` niżej dotyczą wcześniejszych przebiegów i są już nieaktualne.**


**Decyzja 2026-11-03:** do rozwoju i fizycznych testów używać USB/ADB + Mac Local Agent do lokalnego pobierania RX, TX i transkryptów. **Nie wymagamy przycisku eksportu ZIP w UI**, jego implementacja nie jest warunkiem prowadzenia testów ani rozwoju. Nie dodawać okien, ręcznego kopiowania plików, sztucznych limitów ani dodatkowych zatwierdzeń tego samego zadania.

## Wykonanie

- Telefon: dokładnie `SM-S906B`, identyfikator ADB `RFCT70L7E8J`; Mac ma `/Users/michal/Library/Android/sdk/platform-tools/adb`.
- Źródło na urządzeniu: katalog prywatny debugowanej aplikacji `pl.michalmatu.aicallbridge`, `files/call-session-evidence/`. Wewnątrz są lokalne foldery sesji, `timeline.jsonl`, oddzielne `turn-*-rx.wav` / `turn-*-tx.wav` i `COMPLETE` po poprawnym zakończeniu zapisu.
- Istniejący, zatwierdzony mechanizm: `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh`. Mac Local Agent uruchamia go z `adb` w `PATH`, po sprawdzeniu jednego właściwego urządzenia i połączenia `IDLE`.
- Wyjście: **wyłącznie Mac**, `~/Documents/AI-Calls/private-exports/call-evidence-YYYYMMDDTHHMMSSZ-<losowy_sufiks>.tar.gz`, uprawnienia katalogu `0700`, archiwum `0600`. Pliki są dostępne w folderze projektu lokalnie do analiz. Nie przesyłać prywatnych danych przez GitHub, `agent-control`, historię komend ani standardowe wyniki Local Agenta.
- W razie potrzeby użytkownik może **sam zdecydować** o przesłaniu sprawdzonego archiwum tutaj na czat. Nie jest to warunek pracy nad kodem.
- Po kolejnej zaakceptowanej sesji nagranej zgodnie z zakresem autoryzacji ponowić eksport automatycznie; brak folderu oznacza **NO_DATA**, a nie błąd ADB ani potrzebę nowej sztucznej blokady.

## Potwierdzona diagnostyka USB

Mac Local Agent `aicalls-20261103-usb-private-evidence-export-v21`: terminalne `DONE/0`, `ADB_AUTHORIZED_DEVICE_COUNT=1`, `DEVICE_MODEL=SM-S906B`, `CALL_STATE=mCallState=0`, `EVIDENCE_DIRECTORY=NOT_PRESENT`, `USB_EXPORT=NOTHING_TO_COPY`. W tej chwili **brak faktycznych nagrań do kopiowania**. Nie deklarować testu prawdziwej archiwizacji ani audio RX/TX, dopóki pliki nie powstaną; po ich pojawieniu uruchomić wskazany skrypt.

## Końcowe QA ścieżki USB i folder projektu

- Mac `aicalls-20261103-usb-run-as-and-grant-readonly-v23` **DONE/0**: `RUN_AS=PASS`, katalog `~/Documents/AI-Calls/private-exports` faktycznie utworzony z uprawnieniami `0700`, prawidłowy telefon przez USB/ADB, brak `files/call-session-evidence/` i brak istniejącego grantu.
- Mac `aicalls-20261103-usb-export-script-synthetic-and-real-nodata-qa-v24` wykrył **F-027**: próba dwa razy w jednej sekundzie wcześniej nadpisywała archiwum. Zmieniono szablon `mktemp` na zgodny z macOS, a końcowy plik publikowany jest atomowo przez `ln` bez możliwości nadpisania starego archiwum.
- Mac `aicalls-20261103-usb-noclobber-final-qa-v25` **DONE/0**, kod `7a9dae192b9295980a38120823a7eea843cb1579`: `bash -n` PASS, 4/4 testy syntetycznego ADB (dwa różne archiwa, uprawnienia 0600, `NO_DATA`, obcy model oraz brak `run-as`) PASS, skaner anti-friction PASS i 3/3 jego testy. Rzeczywisty S22 przez USB: `USB_EXPORT=NO_DATA`, ponieważ brak sesji, folder na Macu istnieje. Nie kopiowano cudzych danych ani nie inicjowano połączeń.
- **Kryterium sukcesu przy prawdziwym nagraniu:** skrypt ma zwrócić `USB_EXPORT=PASS`, archiwum `.tar.gz` przejść kontrolę integralności, pozostać wyłącznie na Macu i dać się otworzyć w prywatnym folderze. Do czasu sesji nie deklarować rzeczywistej kopii nagrania.

## Zakres prywatności i testów

Nagrywanie pozostaje opcjonalne i domyślnie wyłączone. Jego włączenie jest częścią jednego rzeczywiście autoryzowanego zakresu kampanii i musi respektować prawa uczestników rozmowy. Eksport USB przenosi jedynie istniejące dane — sam nic nie nagrywa, nie wybiera numeru, nie zmienia dialera i nie ujawnia w odpowiedziach zapisanych rozmów. Do uruchomienia rzeczywistego połączenia wciąż potrzebne są odpowiedni stan Android Telecom i ważny grant konkretnego celu. Są to rzeczywiste warunki działania, **nie sztuczne blokery**.

## Priorytet dalszych prac

1. Po USB preflight/eksport i zapisaniu ścieżki projektu aktualizować źródło prawdy `BLOCKERS.md` oraz checkpoint; nie wymagać UI ZIP.
2. Następnie wrócić do **uruchomienia aplikacji** na S22 i badania pełnego runtime: faktyczny grant, rola dialera, gotowość lokalnego audio/STT/Gemma/TTS/TX, START, rzeczywisty rezultat, selektywne STOP i kontynuacja.
3. Dane prywatne zostają na Macu; przy analizie przez agenta logować tylko techniczne liczniki, kody stanu i redagowane przyczyny bez PII. Nie oznaczać pełnej rozmowy PASS na podstawie samej instalacji lub testów off-call.
