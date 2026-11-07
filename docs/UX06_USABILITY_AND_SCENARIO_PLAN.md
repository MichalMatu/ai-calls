# UX-06 — plan wdrożenia ergonomii, stanów i biblioteki scenariuszy

Data: 2026-11-07. Stan: wdrożenie etapowe, niezatwierdzony wynik hosta/S22.

## Zakres zatwierdzony przez właściciela

- Ekran Start: zrozumiałe podstawowe akcje, zachowanie STOP, rozdzielenie autoryzacji, gotowości, bieżącej próby i historii.
- Kampanie: uproszczenie sekcji pojedynczych przycisków, opis roli domyślnego dialera Android, czytelne stany i akcje, jedyna rozwijana bogata diagnostyka nagrań, szczegóły ostatniego wykonania bez rozwijania.
- Spójne tłumaczenia PL/EN i opisy faktycznego działania, w tym ograniczeń supervised attach i nagrań RX/TX.
- Biblioteka scenariuszy: CLIR, mechanik, przychodnia, stomatolog, fryzjer, restauracja, urząd, reklamacja, usługi i wycena. Zapis/edycja/klonowanie, wersjonowanie i formularze per scenariusz; strategia skryptowa lub swobodny model, snapshot przy START.
- Przykład Mechanik: cel diagnostyka, termin przyszły tydzień rano, budżet 300 zł, instrukcja bez zatwierdzania dodatkowych napraw.
- Rozszerzyć istniejące Skills, przygotowanie i sejf danych, nie tworzyć drugich magazynów ani alternatywnego runtime.
- Rozszerzyć istniejące Laboratorium o symulacje scenariusza, bez pozorowania rzeczywistego potwierdzenia terminu lub skutku.

## Etapy i zależności

1. **Wiarygodność stanów**: niezależne `TaskAuthorizationState`, `LiveAttemptState`, `OperationFeedback`, `LastExecutionOutcome`, rejestrowane zdarzenia z generation/attemptId, refresh/lifecycle bez powielonego START. F-119–F-123. STOP i revoke wygrywają z późnym callbackiem. Zero nieautoryzowanego wybierania.
2. **Ekrany**: Start i Kampanie jako widoki typowanych stanów, aktywność przycisków wynika z capabilities; role i SIM odczytywane przy resume. Bez pojedynczych pustych accordion. Dodatkowe komunikaty aria/TalkBack i PL/EN.
3. **Archiwum**: istniejący `RecordedConversationHistory` i `ArchivedConversationPlayer` jako wspólne źródła; właściwy session ID, brak plików, corruption i bezpieczeństwo off-call; ciągły recorder F-080 osobno.
4. **Scenariusze**: wspólny immutable `ScenarioDefinition` (cel, pola formularza, pytania, warunki i rezultat), `ScenarioInstance` z wartościami właściciela, versioned `ScenarioRepository` szyfrowany lokalnie i migracja z CLIR. Nie uruchamiać niewdrożonego preset telefonicznego; laboratoryjne presety można testować tekstowo bez prawdziwego Call.
5. **Wykonanie scenariuszy**: `CallTask` + dokładny numer + grant/SIM + wymagane fakty i skutki + wybrana strategia + AudioEngineProfile + skills snapshot. `BookAppointment` jest typem efektu, nie potwierdzeniem rezerwacji. Dodatkowe koszty/naprawy poza zakresem wymagają nowej zgody.
6. **Laboratorium i odbiór**: RED/GREEN testów stanów, asercje PL/EN, kontrakty zapisu, race/timeout/rotate/restart/SIM/role revoke/STOP/recording, `verify_host.sh`, friction audit, AndroidTest/APK i bezpieczne S22 (bez faktycznych połączeń do innych podmiotów).

## Regresje, które mają blokować przyjęcie

- STOP nie może odłączać obcego Call. Anulowanie OUTGOING nie zmienia sekretarki INCOMING, a globalny STOP działa globalnie.
- Odrzucona/zmieniona SIM albo rola nie może rozpocząć rozmowy. Oczekujący START po rotacji nie może być uruchomiony powtórnie.
- „Wysłano żądanie rozłączenia” nie może wyglądać jak „połączenie rozłączone”, a potwierdzone rozłączenie nie oznacza zrealizowanego zadania.
- Nagrywanie przyszłych prób musi być zsynchronizowane między ekranem głównym kampanii i diagnostyką. Osobne sterowanie aktywnym rejestratorem wymaga atomowego STOP capture.
- Nie przedstawiać fragmentów WAV/STT jako pełnego nagrania. Odsłuch tylko poza Call, nie wykorzystywać przypadkowego najnowszego katalogu.
- Każda wybrana kombinacja model/STT/TTS/skills/scenario musi mieć jawny wynik gotowości bez cichej zamiany.
- Bez `FLAG_SECURE`; screenshoty dozwolone, prywatne WAV/transkrypty/numer bez automatycznego eksportu.

## Potwierdzone wykonanie

- `3418a3e`: pierwsza zmiana widoków i stanów.
- `afbafbfa`: rozdzielone anulowanie zadania wychodzącego.
- `2afdbea`: semantyczny stan anulowania i test JVM.
- Host i S22: QA_PENDING, niezależny wynik wymagany.

Nie uznawać „biblioteka scenariuszy wdrożona” przed faktycznym dodaniem modeli, edytora, testów i integracji; ten dokument nie jest kodem.


## Implementacja etapowa po zatwierdzeniu planu

Aktualny source: `a03e98b`. Biblioteka 10 presetów ma neutralny katalog JVM, formularz podglądu/edycji, przykładowe wartości Mechanik, prywatny szyfrowany zapis szkicu i AndroidTest nawigacji. **Nie posiada jeszcze** własnych scenariuszy użytkownika, wersjonowania formularzy, potwierdzenia wyboru przed START ani kwalifikowanego silnika nowych branż. Do momentu integracji wyświetla jawne DRAFT_ONLY. Szczegóły: [checkpoint](checkpoints/2026-11-07-ux06-campaign-and-scenario-source-checkpoint.md).

## Etap 2 — wersjonowanie i historia (2026-11-07)

Wdrożono kopie osobiste gotowych presetów, własny tytuł i cel, wersje i powrót do poprzedniej wersji, usuwanie z potwierdzeniem, szyfrowany store Keystore/AtomicFile, ochronę niezatwierdzonych zmian; datę/czas zapisu historycznego zdarzenia Kampanii. Bez telefonii dla nowych szablonów, bez statusu live. QA: v4 DONE/0, v5 oczekuje. [Checkpoint](checkpoints/2026-11-07-ux06-personal-scenarios-and-history-receipt.md).
