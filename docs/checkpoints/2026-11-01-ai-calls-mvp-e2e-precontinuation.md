# AI Calls — checkpoint przed kontynuacją MVP E2E, 2026-11-01

> **Punkt wejścia dla następnego czatu/sprintu.** Stan podany poniżej pochodzi z bezpośredniego odczytu repozytorium i *terminalnych* receiptów Mac Local Agenta. Nie kontynuuj wykonywania rozmów, zmiany CLIR ani instalacji bez odświeżenia tego stanu. Sam GitHub commit kolejki nie dowodzi wykonania zadania.

## 1. Kod, repozytorium i transport

- Produkt: `MichalMatu/ai-calls`, praca bezpośrednio na `main`, bez nowej gałęzi. **Zweryfikowany HEAD produktu przed zapisaniem checkpointu:** `2a05facf5f24e70006ed571bfe7748e7492470ef`. Najnowszy commit *kodu aplikacji*: `aed472dececaef711e2198fd26a995e27c9ad6ba` (`SupervisedCallPilotActivity` — tryb potwierdzony przez człowieka). Późniejsze commity aż do `2a05fac` zmieniają dokumentację. Nowe commity samego checkpointu zmienią HEAD, ale nie kod aplikacji.
- Ostatni **potwierdzony pełny** host quality gate: `aicalls-mvp-full-quality-20261101-v1`, źródło `7aa1d952786bbc31eba9504481f3339a5657d208`, `done/0`, zakończony `2026-11-01T16:29:19Z`. Android unit, lint, APK i AndroidTest APK **BUILD SUCCESSFUL**, suite Python **199/199 OK**, `MVP_FULL_QUALITY=PASS`. Ten receipt obejmuje nowy pisarz PCM i testy STT/TTS, ale **nie** późniejszy instrumentowany test supervised Gemma i poprawkę potwierdzania trybu z `aed472`.
- Wyraźnie **nie dziedzicz PASS** z `7aa1` na całe najnowsze źródło bez osobnego terminalnego receiptu.
- Local Agent: prywatne `MichalMatu/local-agent`, branch `agent-control`, `macbook-michal`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, `daemon_version=4.20.16`. Kanoniczny prefix:
  `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/`.
  Zadania: `tasks/<id>.json`, immutable wyniki: `task-results/<id>.json`, bieżący stan: `status.json`. Nie używać starego statusu projektu `ai-calls:agent-control`.
- Ostatni odczyt `status.json`: `2026-11-01T17:13:57.307540Z`, `idle`, `current_task_id=null`, `unresolved_claims=0`. Późniejsze terminalne receipty dowodzą, że agent faktycznie wykonywał zadania. Nie nazywać go martwym tylko na podstawie dawnego opóźnionego statusu.
- Nowe source-only QA, bez telefonu: `aicalls-checkpoint-exacthead-qa-20261101-v1` (przypięte do `2a05fac`). **W trakcie sporządzania checkpointu: brak terminalnego wyniku; sprawdzić i dopisać rezultat przed uznaniem za PASS.** Nie ponawiać tego samego ID.

## 2. Co jest faktycznie potwierdzone na S22

- `aicalls-mvp-e2e-s22-readiness-20261101-v1`: **done/0**, źródło `a4486fc`, zweryfikowana tożsamość Galaxy S22+ `SM-S906B`, `mCallState=0` (IDLE), domyślny dialer `com.google.android.dialer`, APK `AndroidJUnitRunner` obecny. Lokalny plik modelu `gemma-4-E2B-it.litertlm` ma dokładnie `2 588 147 712` bajtów. To nie jest test STT/TTS ani dowód Shizuku Binder.
- `aicalls-gemma-downloaded-readiness-s22-20261101-v1`: **done/0**, `GEMMA_MODEL_STATE=READY_SIZE_MATCH`; nie pobierać modelu ponownie. Test gotowości sprawdza plik/rozmiar; nie zakładaj osobnego pełnego SHA-256 w tym receipcie.
- `aicalls-mvp-phone-gemma-inference-20261101-v1`: **done/0**, źródło `a4486fc`, fizyczny AndroidTest `AndroidGemma4ModelReadinessContractTest` **OK (1 test)** i prawdziwa inferencja LiteRT-LM `AndroidGemma4DialogueSkillContractTest` **OK (1 test)**, czas testu modelu **30,317 s**. Brak połączenia, pozostawiono `IDLE`. To jest dowód **inferencji modelu**, nie nadzorowanej pełnej ścieżki STT/Gemma/TTS ani aktualizacji obecnego APK.

## 3. Poprawki i nowe testy na main (nie mylić z fizycznym PASS)

- `SupervisedGemmaDialogueBackend`: Gemma wybiera tylko jeden z ograniczonych `DialogueSkillId` z progiem 0,75; tekst mowy pochodzi z czterech dokładnie zatwierdzonych fraz aplikacji; przy błędzie/niepewności bezpieczny skrypt. Nie istnieje już swobodny output Gemmy do mowy. Zmiana wcześniejszego sprintu i jej negatywne testy semantyczne otrzymały host PASS.
- `AuthorizedPcmUplinkWriter.kt`: TX PCM16 w porcjach odpowiadających 20 ms; przed każdą porcją i `flush` ponowna kontrola zgody i generacji. `ChatRelayLiveCallProbe.stopAudio()` synchronicznie unieważnia autoryzację zanim asynchronicznie wyczyści sesję. Already accepted PCM w urządzeniu nie można cofnąć; nie ogłaszać natychmiastowego akustycznego STOP bez testu rozmowy.
- `AuthorizedPcmUplinkWriterTest.kt`: cztery testy JUnit (pełny strumień, cofnięcie po pierwszej ramce, odmowa przed pierwszą ramką, nieprawidłowe wyrównanie). Obecne w źródle `7aa1` i objęte pełnym PASS hosta. Nie powoływać się na rzekomy *oddzielny*, nieweryfikowany ręczny harness.
- `SupervisedCallPilotActivity.kt`: start sesji trwale wiąże tryb Gemma/skrypt z tym, który użytkownik zaakceptował w dialogu; przy wejściu odświeża gotowość modelu.
- `AndroidLocalSpeechMvpContractTest.kt`: nowe bezrozmowne testy inicjalizacji lokalnego pl-PL STT oraz TTS -> PCM16 mono 16 kHz. Kompilacja AndroidTest APK w starszym receipcie przechodzi; **testy urządzeniowe jeszcze nie zostały wykonane na aktualnym APK**.
- `AndroidSupervisedGemmaEndToEndContractTest.kt`: nowy bezrozmowny instrumentowany test model -> skill -> exact allowlist; dodany po `7aa1`, **brak zweryfikowanego wyniku Gradle i S22 na tym ostatnim kodzie**.
- Maksymalnie **3 odpowiedzi** w nadzorowanym pilocie. Skrypt i cztery neutralne frazy nie dowodzą autonomicznego rozumienia drzewa IVR infolinii ani wykonania realnego zadania.

## 4. Dokładny audyt terminalnych zadań (najistotniejsze dla wznowienia)

| Zadanie Mac Local Agent | Status/exit | Rozstrzygnięcie |
|---|---|---|
| `aicalls-mvp-full-quality-20261101-v1` | **done/0** | Source `7aa1`, build/unit/lint/APKs i 199 Python PASS |
| `aicalls-mvp-final-source-qa-20261101-v1` | **failed/1** | Mechaniczny **SHA mismatch**: spodziewał się `aed472`, rzeczywisty `2a05fac`. Zakończył przed Gradle; **nie wykryto błędu kompilacji** |
| `aicalls-mvp-physical-nocall-stt-gemma-tts-20261101-v1` | **failed/1** | Mechaniczny **SHA mismatch**: spodziewał się `60838eb`, rzeczywisty `2a05fac`. Nie doszedł do budowy, `adb install -r` ani AndroidTest; **nowszy APK nie został tą próbą zainstalowany** |
| `aicalls-mvp-s22-shizuku-media-preflight-20261101-v1` | **failed/1** | **Rzeczywisty bloker** `live_call_readiness_failed: shizuku_binder_unavailable` przy źródle `2a05fac`, `2026-11-01T16:58:58Z` |
| `aicalls-mvp-phone-gemma-inference-20261101-v1` | **done/0** | Na S22 prawdziwe Gemma LiteRT, test rozpoznawania skilla PASS, bez telefonu |
| `aicalls-checkpoint-exacthead-qa-20261101-v1` | **terminal receipt do odczytu** | Bez urządzenia; source-only Gradle/JVM/lint/APKs/Python na `2a05fac`. Nie przypisuj PASS dopóki rezultat nie istnieje |

Wcześniejsze stwierdzenie, że Mac Local Agent „nie działa”, zostało wyjaśnione późniejszymi terminalnymi wynikami; opóźnienie pomiędzy zapisaniem żądań a ich realizacją było istotne.

## 5. Macierz pełnego przepływu MVP

| Etap | Stan potwierdzony |
|---|---|
| S22, model, bezpieczne rozpoznanie urządzenia | **PASS** we wcześniejszych testach |
| Uruchomienie Gemma 4 LiteRT na S22 | **PASS**, syntetyczny input, 30,317 s |
| Zgoda operatora / tryb / 3-turn pilot | Kod zaimplementowany; brak pełnego testu UI ostatniego APK |
| Rozmowa operatora w Google Phone | **Nie wykonywano** |
| Shizuku Binder / call-media readiness | **FAIL** w najnowszym preflight (do naprawy/ponownej kontroli) |
| RX rzeczywistego rozmówcy, PCM do STT | **Nieudowodnione** w tym MVP |
| Polskie offline STT na bieżącym APK | **Device test otwarty** |
| STT -> bounded Gemma -> zatwierdzona wypowiedź | Model i polityka testowane osobno; łączny nowy device test otwarty |
| Skryptowe fallbacki po niepewności Gemmy | Unit/host poprzedniego sprintu PASS; fizyczne audio otwarte |
| Lokalny TTS -> PCM16 mono 16 kHz | Nowy device test otwarty |
| PCM -> uplink rozmowy -> faktycznie słyszane audio | **Nieudowodnione** |
| STOP / cofnięcie zgody | Unit testy PCM/źródło PASS; fizyczna akceptacja STOP otwarta |
| Końcowy raport `chat-relay-live-call-report.txt` | Oczekuje rzeczywistej, świadomie rozpoczętej rozmowy |
| Autonomiczny dial infolinii / selektywne rozłączenie | **BLOKADA bezpieczeństwa**: brak `CampaignOwnedCallController` z uchwytem własnej rozmowy; niezależne od MVP attach-only |

## 6. Wymagane następne kroki, w tej kolejności

1. **Preflight tylko odczyt:** świeży `origin/main`, task-results `aicalls-checkpoint-exacthead-qa-20261101-v1`, ostatni Mac `status.json`, `git status` w rzeczywistym repo hosta, bez fałszywego przenoszenia PASS między SHA. Nie uruchamiaj wcześniej zakończonych testów od zera bez konkretnego powodu.
2. **Odblokuj Shizuku** na fizycznym S22: diagnoza, czy usługa menedżera działa, czy aplikacja ma zgodę i czy Binder się udostępnia. W razie potrzeby ręczne zatwierdzenie zgodne z Android/Shizuku, bez niejawnych zmian uprawnień. Powtórz `scripts/local_agent/device_readiness.sh` w stanie `IDLE`. **Bez działającego Binder nie uruchamiaj realnego testu połączenia**.
3. **Host QA najnowszego źródła:** wymagane Android unit, lint, debug APK, AndroidTest APK, Python suite i `git diff --check`, dokładny source code SHA na receipcie. Jeśli stare QA `7aa1` jest nadal jedynym PASS, wykonaj prawidłowy nowy receipt. Nie używaj nieaktualnego porównania literalnego SHA przeciw zmieniającemu się docs-only `main`; przypnij źródło stabilnie lub odczytaj rzeczywisty wynik.
4. **Bezrozmowna instalacja/testy S22** dopiero po gotowym APK i zgodnym modelu (nie pobieraj go ponownie): `adb install -r` bez kasowania danych, instrumentowane `AndroidLocalSpeechMvpContractTest`, `AndroidSupervisedGemmaEndToEndContractTest`, Gemma readiness/inference, negatywny UI `NO_ACTIVE_CALL`, STOP nieaktywny bez sesji. Sprawdź `IDLE` i Google Phone przed i po. Żadnej automatycznej inicjacji rozmów.
5. **Fizyczne MVP 3-turn:** dopiero przy Binder PASS człowiek rozpoczyna sam uzgodnioną/świadomie zaakceptowaną rozmowę z numerem testowym lub infolinią obsługującą taki test. Po powrocie do AI Calls świadomie akceptuje attach. Obserwacja `RX -> finalny STT -> model/skrypt -> zatwierdzona wypowiedź -> TTS -> TX` i odsłuch po stronie rozmówcy; STOP wyłącza AI, **nie rozłącza** cudzego połączenia. Raport bez transkryptów i danych osobowych.
6. **Ewentualny osobny projekt autonomicznej infolinii:** realny exact-call owner, związanie z celem/SIM, selektywne rozłączenie, tylko precyzyjny grant aplikacji, zewnętrzne efekty po walidacji i osobne potwierdzenie faktów. Samo `TelecomManager.isInCall` nie daje prawa do kończenia rozmów.

## 7. Zasady pracy i kryterium zamknięcia

- Wykonawca: **Local Agent + GitHub Connector**, Mac host i izolowany sandbox do testów. **Zakaz** Codex, Work, GitHub Actions (także sprawdzania ich statusu), zmian domyślnego dialera, `KEYCODE_ENDCALL`, `TelecomManager.endCall()`, `cmd telecom end-call`, autonomicznego `*100` i nieautoryzowanej zmiany CLIR.
- Źródła pierwszego czytania: `AGENTS.md`, `README.md`, **ten checkpoint**, `docs/HANDOFF_NEXT_CHAT.md`, `docs/MVP_E2E_SPRINT_2026-11-01.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/CORRECTION_CAMPAIGN_2026-11-01.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md`. Nie przejmuj starych „LATEST” z daty bez aktualnych receiptów.
- `HOST_GREEN` = kompilacja/testy host; `PROVEN_S22_NOCALL` = bezrozmowne próby Android; `LIVE_SUPERVISED_PASS` = potwierdzona prawdziwa rozmowa i słyszalność; `AUTONOMOUS_SERVICE_PASS` = osobna, późniejsza akceptacja exact-call ownership i efektów. **Na ten checkpoint nie osiągnięto dwóch ostatnich.**
- Zachować zanonimizowane dowody (identyfikator zadania, SHA, status, liczniki PCM, czasy). Bez numerów telefonów, transkryptów, nagrań, danych kont, kluczy w publicznym Git.
- Nie pozostawiono w tym checkpoincie gałęzi roboczej; `main` pozostaje źródłem prawdy. Stan niezweryfikowanych lokalnych checkoutów na Macu należy odczytać w preflight, nie zgadywać.


## 8. Domknięcie spójności checkpointu

- Po zapisaniu checkpointu odświeżono `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md` i `docs/MVP_E2E_SPRINT_2026-11-01.md`, usuwając m.in. nieaktualne stwierdzenie, że model Gemma jest brakujący na S22, oraz stwierdzenie o nieistniejącym niezależnym 4/4 harnessie. To **tylko dokumentacja**.
- Odczyt `compare_commits(aed472de, 40e26cdb)` wykazał wyłącznie zmiany `README.md` i `docs/**`: drzewo kodu aplikacji pozostaje bez zmian względem `aed472dececaef711e2198fd26a995e27c9ad6ba`.
- Aby uniknąć powtarzania mechanicznego mismatch SHA po zmianach dokumentacji, w prywatnym Mac Host Control zapisano **osobny stabilny quality gate** `aicalls-checkpoint-product-tree-qa-20261101-v1`, porównujący drzewa produktu do `aed472de` zamiast literalnego HEAD, a następnie wykonujący `scripts/local_agent/host_verify.sh`. **Wynik nie był jeszcze opublikowany przy zamykaniu checkpointu**. Sprawdź ten dokładny receipt przed jakimkolwiek ponownym zadaniem QA.
- Nie tworzono nowych gałęzi, nie wykonywano instalacji ani połączeń w ramach samego checkpointu. Ewentualne późniejsze wykonanie zaakceptowanego tasku jest osobnym wynikiem, którego nie wolno automatycznie zaliczać.
