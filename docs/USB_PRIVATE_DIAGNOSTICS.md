# AI Calls — prywatna diagnostyka przez USB/ADB (wybrana ścieżka właściciela)

**Decyzja 2026-11-03:** do rozwoju i fizycznych testów używać USB/ADB + Mac Local Agent do lokalnego pobierania RX, TX i transkryptów. **Nie wymagamy przycisku eksportu ZIP w UI**, jego implementacja nie jest warunkiem prowadzenia testów ani rozwoju. Nie dodawać okien, ręcznego kopiowania plików, sztucznych limitów ani dodatkowych zatwierdzeń tego samego zadania.

## Wykonanie

- Telefon: dokładnie `SM-S906B`, identyfikator ADB `RFCT70L7E8J`; Mac ma `/Users/michal/Library/Android/sdk/platform-tools/adb`.
- Źródło na urządzeniu: katalog prywatny debugowanej aplikacji `pl.michalmatu.aicallbridge`, `files/call-session-evidence/`. Wewnątrz są lokalne foldery sesji, `timeline.jsonl`, oddzielne `turn-*-rx.wav` / `turn-*-tx.wav` i `COMPLETE` po poprawnym zakończeniu zapisu.
- Istniejący, zatwierdzony mechanizm: `scripts/aicall_tools/diagnostics/export_private_call_evidence.sh`. Mac Local Agent uruchamia go z `adb` w `PATH`, po sprawdzeniu jednego właściwego urządzenia i połączenia `IDLE`.
- Wyjście: **wyłącznie Mac**, `~/Documents/AI-Calls/private-exports/call-evidence-YYYYMMDDTHHMMSSZ.tar.gz`, uprawnienia katalogu `0700`, archiwum `0600`. Pliki są dostępne w folderze projektu lokalnie do analiz. Nie przesyłać prywatnych danych przez GitHub, `agent-control`, historię komend ani standardowe wyniki Local Agenta.
- W razie potrzeby użytkownik może **sam zdecydować** o przesłaniu sprawdzonego archiwum tutaj na czat. Nie jest to warunek pracy nad kodem.
- Po kolejnej zaakceptowanej sesji nagranej zgodnie z zakresem autoryzacji ponowić eksport automatycznie; brak folderu oznacza **NO_DATA**, a nie błąd ADB ani potrzebę nowej sztucznej blokady.

## Potwierdzona diagnostyka USB

Mac Local Agent `aicalls-20261103-usb-private-evidence-export-v21`: terminalne `DONE/0`, `ADB_AUTHORIZED_DEVICE_COUNT=1`, `DEVICE_MODEL=SM-S906B`, `CALL_STATE=mCallState=0`, `EVIDENCE_DIRECTORY=NOT_PRESENT`, `USB_EXPORT=NOTHING_TO_COPY`. W tej chwili **brak faktycznych nagrań do kopiowania**. Nie deklarować testu prawdziwej archiwizacji ani audio RX/TX, dopóki pliki nie powstaną; po ich pojawieniu uruchomić wskazany skrypt.

## Zakres prywatności i testów

Nagrywanie pozostaje opcjonalne i domyślnie wyłączone. Jego włączenie jest częścią jednego rzeczywiście autoryzowanego zakresu kampanii i musi respektować prawa uczestników rozmowy. Eksport USB przenosi jedynie istniejące dane — sam nic nie nagrywa, nie wybiera numeru, nie zmienia dialera i nie ujawnia w odpowiedziach zapisanych rozmów. Do uruchomienia rzeczywistego połączenia wciąż potrzebne są odpowiedni stan Android Telecom i ważny grant konkretnego celu. Są to rzeczywiste warunki działania, **nie sztuczne blokery**.

## Priorytet dalszych prac

1. Po USB preflight/eksport i zapisaniu ścieżki projektu aktualizować źródło prawdy `BLOCKERS.md` oraz checkpoint; nie wymagać UI ZIP.
2. Następnie wrócić do **uruchomienia aplikacji** na S22 i badania pełnego runtime: faktyczny grant, rola dialera, gotowość lokalnego audio/STT/Gemma/TTS/TX, START, rzeczywisty rezultat, selektywne STOP i kontynuacja.
3. Dane prywatne zostają na Macu; przy analizie przez agenta logować tylko techniczne liczniki, kody stanu i redagowane przyczyny bez PII. Nie oznaczać pełnej rozmowy PASS na podstawie samej instalacji lub testów off-call.
