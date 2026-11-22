# AI Calls — separacja UI / logiki przed Compose (stan częściowy)

Bazowy kod: `main` od `b1179fa`. Sprinty: `6dbeaa3`, `3a7290d`,
`3efaf1e`, `53ce9eb`, `aec2f02`, `06c6460`. To **nie** jest zakończona migracja ani kwalifikacja Android.

## Stan przed zmianami (wybrane ekrany)

```text
AgentAiHubActivity -> AndroidConversationPreparationStore / IdentityVault / ToolsSettings
                   -> mapowanie wyników na zasoby i bezpośrednie aktualizacje widoków
MainActivity       -> PhoneTaskWork / NotebookStore / IncomingSettings / GSM readiness
                   -> odczyty w workerze -> TextView
ModelVoiceSettings -> picker + reguły wyboru języka / walidacja generowania
                   -> AudioEngineProfileStore + probe/TTS lifecycle + widoki
```

## Granice wprowadzone w sprintach (tylko wskazane operacje)

```text
UI / Activity
  AgentAiHubActivity ──> AndroidAgentHubOverviewReader (data/adapters)
           │                         │
           │                         └─> istniejące magazyny i vault
           └─> AgentHubSummaryFormatter (presentation/agent)
                           ↑
                    AgentHubOverview (domain/models)

  MainActivity ─────────> AndroidHomeOverviewReader (data/adapters)
           │                         │
           │                         └─> istniejące magazyny, polityka SIM,
           │                              readiness GSM (read-only)
           └─> HomeOverviewSnapshot (domain/models)
                + IncomingHomeSummary (stary renderer zasobów)

  ModelVoiceSettings ──> ModelVoiceSelectionPolicy (audioengines, JVM)
           └──────────> DialogueGenerationInputParser (audioengines, JVM)
           ├──────────> ModelVoiceProfileController (presentation/modelvoice)
           │             └─> AudioEngineProfileStore via load/save callbacks
           ├──────────> ModelVoiceEnginePickerPresenter (presentation/modelvoice)
           └──────────> Android-owned TTS/probe lifecycle (bez zmian)
```

Kierunek zależności: UI korzysta z czytników danych i czystych mapperów;
adaptery nie importują `Activity`, `TextView`, `UiDesign`. Nie dodano
sztucznych modułów Gradle ani zależności Compose. Dotychczasowe moduły
`conversation-core`, `conversation-tools`, `audio-bridge`,
`realtime-client`, `privileged-helper` pozostają bez zmian.

## Kontrakty zachowania

- Agent: kolejność i niezależność odczytów pięciu źródeł, `Result` per źródło,
  identyczne identyfikatory zasobów i argumenty formatowania; odczyt na
  pojedynczym workerze, render na wątku UI, kontrola `generation`.
- Home: nadal sprawdza zadania co 5 s (harmonogram ekranu co 1 s), wykonuje
  `recover` przed `phoneTasks`, odczyt notatników oraz readiness SIM/host
  w tym samym executorze; te same fallbacki i opisy dostępności.
- Model i głos: wybór `en-US` wymusza Android STT/output, usuwa wybrany głos
  i czyści tylko silnik Piper; `pl-PL` zachowuje engine/input/output i usuwa
  voiceId. Bez zmiany zachowania `save` i resetów sesji TTS/probe.
- Generowanie: puste budżety = null/auto, wcześniejsze granice zakresów,
  przecinek dziesiętny, identyczna obsługa błędnych pól i wartości NaN.
- Zapis profilu: identyczna kolejność odczyt → porównanie → walidacja →
  zapis → powiadomienie/odświeżenie; nieudany zapis ani niezgodny profil
  nie zmieniają lokalnego draftu. Odczyt zmiany z zewnątrz jest odrębny:
  ekran zamyka probe **przed** przyjęciem nowego draftu.
- Wybór silnika TTS: kolejność i zip list bez zmian, ukrywanie Piper dla
  `en-US`, nieznany zapisany silnik pozostaje widoczny jako brakujący,
  bez automatycznej podmiany.

## QA — aktualny stan

- Nowe unit testy: `AgentHubSummaryFormatterTest` (3),
  `ModelVoiceSelectionPolicyTest` (3),
  `DialogueGenerationInputParserTest` (4),
  `ModelVoiceProfileControllerTest` (6),
  `ModelVoiceEnginePickerPresenterTest` (4).
- Lokalna izolowana próba z kompilatorem Kotlin: 17 asercji logicznych
  mapperów/parsowania, **ze stubami zależności**.
- Bieżący sprint: izolowany `kotlinc` 1.9.0 i uruchomienie 6 testów
  kontrolera oraz 4 testów prezentera z lokalnymi atrapami profilu i JUnit;
  dodatkowo 19 + 13 asercji bezpośrednich (PASS). Nie są to testy
  JUnit Gradle ani build Androida, więc nie kwalifikują integracji.
- Mac Local Agent: zadanie QA na `agent-control`; brak świeżego heartbeat/ACK.
  `preflight`, `app:testDebugUnitTest`, `assembleDebug` i emulator
  **niepotwierdzone** dla nowych commitów. S8 Lab/S22+: bez instalacji i
  połączeń. Żadnych PASS dla GSM, plików użytkownika lub produkcji.

## Pozostałe granice do wydzielenia

1. Z `MainActivity`: polling/status i STOP -> presenter + autoryzowany adapter;
   osobno testy cyklu życia i zatrzymania.
2. Z `AgentAiHubActivity`: oddzielić politykę świeżości wyników od Activity.
3. Z `ModelVoiceSettings`: teraz zapis/stan profilu i opcje silnika
   są wydzielone; pozostają cache/inventory TTS, probe, async/cancel,
   mapowanie stanu ekranu. UI nadal konstruuje Android store; tę
   kompozycję należy przenieść poza screen przed migracją Compose.
4. `LanguageModelActivity`: transfer/download i import pliku modeli;
   zachować identyfikatory, postęp i odbiór rezultatów.
5. `ConversationHistoryActivity`: m.in. uprawnienia delete/idle,
   odczyt i playback; oddzielić repozytorium, stan i mapowanie.
6. GSM/Telecom/SIM: w pierwszej kolejności testy autoryzacji, exact Call,
   takeover/STOP/END_CALL i roli dialera; bez refaktoryzowania krytycznego
   runtime przed potwierdzeniem fizycznych bramek.

## Plan Compose + Material 3

- Nie zastępować UI zanim czytniki i komendy ekranów mają niezależne kontrakty.
- Dla każdego ekranu: `UiState` + `Action` + presenter/ViewModel;
  adapter/system za portem; UI tylko wyświetla i przekazuje zdarzenia.
- Dopiero potem ekrany Compose nawigowane w istniejącej Activity,
  bez równoległych zapisów do prefs i bez drugiego właściciela GSM.
- Porównanie starego i nowego UI na tych samych snapshotach,
  testy dostępności, rotacji, cancel/lifecycle, test S8 Lab; przełączenie
  osobnym, odwracalnym etapem.
