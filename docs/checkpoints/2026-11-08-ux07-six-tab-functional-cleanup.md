# AI Calls — UX-07 sześć zakładek i data-first, checkpoint źródła (2026-11-08)

**Decyzja użytkownika:** 6 pozycji dolnego menu: Start / Rozmowy / Agent AI / Lab / Modele / Opcje. Agent AI: charakter, Skills, skrypty, profile, sejf, uprawnienia narzędzi. Modele: STT/LLM/TTS, download/import/wybór; ustawienia ogólne: telefon/system. Zero zduplikowanych edytorów i magazynów, żadnego wipe.

**Zmiany:** `AppNavigation` i `AgentAiHubActivity` tworzą jedną ścieżkę do edytorów w Agent AI. `LanguageModelActivity` jest tab-root Modele; pokazuje stany i wielkości modeli, ma skróty do STT/TTS i trybu rozmowy. `AudioEngineSettingsActivity` zachowuje zapis input/output i wyświetla model z obecnego store, ale nie ma niezależnego selektora modelu. `SettingsHubActivity` nie powiela wejść Agent/Models. Dla kart `UiChromeActivity` zapewnia 16 dp odstępu. `ParakeetModelTransferService` przenosi opcjonalny 615 MB download do foreground; kontrola SHA i zapis pozostały oryginalne. Wymagane uprawnienia foreground są już w manifeście. PL/EN stringi dodane.

**Dowody:** bazowy `main@5ecf44a` Mac PASS 341/341 Python. Nowy `c51333d` Mac `aicalls-ux07-six-tab-source-host-20261108-v1`: **Kotlin/AndroidTest compile PASS; full verify_host RED (8 starych oczekiwań na poprzednie cztery zakładki / ekran Settings)**, bez dowodu błędu runtime. Aktualizacje testów i dalszy pakiet kodu wymagają **kolejnego pełnego Mac QA**. Nie ogłaszać PASS przed nowym terminalnym wynikiem.

**Granice:** zero CALL, STOP, rzeczywistego nagrywania lub 615 MB pobierania w czasie audytu; S22 nieinstalowany i nietestowany dla nowych commitów; dane w sejfie i ich zgody pozostają lokalne. Bez dotykania Shizuku PCM/PFD, Telecom ani autoryzacji. Wznowienie: sprawdzić świeży main, latest Mac receipt, następnie bezpiecznie zainstalować APP+TEST na S22 bez wipe i wykonać 6-tab navigation + karty + screenshot matrix (tylko jeśli telefon IDLE), potem wizualny odbiór; test download wymaga osobnej świadomej akcji. Otwarte: pełna data-first prezentacja surowej diagnostyki (F-131) i ręczny TalkBack.


## Data-first UX refinement (2026-11-08; not yet hardware accepted)

- USB: stany parsowane do 7 czytelnych pól (telefon, kampania, STT, AI, TTS, recording, incoming); raw JSON dostępny wyłącznie w rozwijanej sekcji. Bez migracji stanu czy logowania danych.
- Podglądy Agent AI: scenariusz jako lista etykiet i wartości, przygotowanie jako liczby Skills i pól + pełny skrypt w przewijalnym obszarze, Skills zwinięte do trzech wierszy i otwierane dotykiem, bez utraty pełnej treści ani rewizji.
- Nagłówki podstron: własny tytuł (np. Kampanie, Skills, STT/TTS) zamiast nagłówka zakładki.
- Mac gate `aicalls-ux07-six-tabs-parakeet-final-host-20261108-v1` dla `60aa75d`: Kotlin/AndroidTest compile **PASS**, Python 341: **3 stare kontrakty strukturalne RED**, bo szukały Parakeet download w dawnej Activity; zmieniony audyt sprawdza właściwą usługę foreground, wywołanie z zakładki Modele i cancel guard. Final QA na nowym HEAD wymagane. Na S22 nie uruchomiono żadnego połączenia ani pobrania modelu.

## Redukcja duplikatów nagłówków

Na podstronach z aktywnym wspólnym paskiem tytułu usunięto drugi identyczny tytuł z przewijanej treści (Kampanie, Skills, Silniki, numer telefonu, narzędzia, sekretarka, podstawowe, developer). Pierwotne akcje, opisy, dostępność i statusy pozostają. Etykiety sześciu zakładek 11sp zamiast 10sp. Nie usuwano żadnego edytora ani stanu.

**Gate:** Source-only commit po `1bf90c0`; Mac `aicalls-ux07-datafirst-final-host-20261108-v1` zamówiony dla `1bf90c0`; nie przenosić jego ewentualnego PASS na nowszy SHA. Dla ostatniego commitu niezbędny osobny gate. S22 nadal niezweryfikowany dla UX-07.
