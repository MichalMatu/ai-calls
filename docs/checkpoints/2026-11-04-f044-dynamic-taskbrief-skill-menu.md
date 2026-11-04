# AI Calls — F-044 TaskBrief i menu prompt-skills (2026-11-04)

## Punkty odniesienia

**Zamrożony, działający RAW v1:** `0d44b7d5a7e01daa118da10c2e04c458086dced9`, [checkpoint i audyt ostatniego RX](2026-11-04-gemma-raw-live-v1-frozen.md). To punkt reprodukcji/porównania, nie obietnica pełnego sukcesu CLIR. STT→Gemma→TTS→TX działało w dwóch kolejnych realnych turach, sygnał lokalnego TX potwierdzony. Nie zmieniono silnika językowego RAW, jego parsera (jest ominięty), domyślnego `SCRIPT_GEMMA` ani zamrożonego toru Samsung/Shizuku.

**F-047 diagnoza:** trzecie RX 17 080 ms bez finalnego STT, ponieważ Call został zakończony zanim VAD wykrył 1500 ms końcowej ciszy; w nagraniu maksymalnie 1000 ms. `pipeline.finishInput()` nie nastąpiło, `pipeline.close()` anulowało ASR. Nie jest to błąd parsera. Dodano diagnostyczny `RX_INTERRUPTED` z turn/captured_ms/speechDetected/trailingSilence/reason, bez żadnego tekstu i bez generacji po rozłączeniu. Osobny syntetyczny test EOU odtwarza 17 080 ms i brak endpointu. **F-047 OPEN** do QA i rozważenia oddzielnego odzyskiwania transkryptu *wyłącznie po rozłączeniu* (bez TX), gdy jest potrzebne.

## Co nowego w F-044

- `GemmaDirectTaskBriefBuilder.kt`: kompiluje tekst **system instruction** z `CampaignExecutionRequest` i dokładnego `CampaignAuthorizationGrant`. Najpierw aplikacyjny `CampaignAuthorizationPolicy.authorize`; nieobsługiwane zadania/niezgodne granty są odrzucane. Na dziś jedynym wykonawcą jest **SET_SERVICE(CLIR=true)** — nie deklarować, że można uruchamiać dowolne numery/usługi.
- **Rola zawsze pierwsza:** „jesteś DZWONIĄCYM asystentem użytkownika, nie konsultantem”. Adresatem jest człowiek lub IVR. Zadanie: CLIR dla aktualnej usługi głosowej; nie deklaruj sukcesu przed niezależnym potwierdzeniem. Osobiste fakty i numer konta, SIM, grantId, telefon docelowy i sekrety **nie są wpisywane do promptu**.
- `GemmaDirectPromptSkill` to **4 deklaratywne moduły instrukcji**, bez wykonywalnego narzędzia i bez poszerzania uprawnień: EXPLAIN_GOAL, HANDLE_IVR, ASK_PROCEDURE, CLARIFY. Wbudowana `Gemma4LiteRtTextBackend` pozostaje w naturalnym RAW (bez JSON; brak `automaticToolCalling`).
- `GemmaDirectPromptSkillStore` zapisuje wybór tylko względem aktywnego grantId (domyślnie wszystkie cztery). Nie nadaje uprawnień, zmiana w trakcie rozmowy nie przepina modelu.
- `CampaignToolActivity`: rozwijana sekcja **Skills i zadanie Gemma Direct**, lista checkboxów, „Zapisz skills do następnego połączenia”, „Podgląd instrukcji startowej Gemmy”. Rola jest stała; zadanie tylko ze zweryfikowanego grantu. Nowy wybór nie dodaje osobnego obowiązkowego dialogu do START/STOP.
- `AppOwnedCampaignService` buduje identyczny `TaskBrief` przed prewarm, sprawdza ponownie jego zgodność przed wybieraniem numeru, wiąże cały model z promptem. `CampaignGemmaDirectPreflight` i `GemmaDirectDialogueBackendFactory` przyjmują identyczną instrukcję; prewarm nie może przenosić kontekstu między różnymi zadaniami/skills. `AppOwnedClirCampaignExecutor` i `ChatRelayLiveCallProbe` niosą tę samą wartość do jednej rozmowy. Starszy prompt pozostaje kompatybilnym fallbackiem wyłącznie dla istniejących testów/wejść bez dynamicznego briefu.
- Testy Kotlin: rola niekonsultanta, zmiana skills bez zmiany celu, brak wstrzykiwania sekretów, odrzucenie niedozwolonego celu/SIM/efektu, unieważnienie grantu, odtwarzalny brak endpointu przy 17 080 ms. AndroidTest: cztery syntetyczne natywne tury z nowym promptem z autoryzowanego syntetycznego CLIR.

## Znaczenie słowa skills

Google dokumentuje funkcje/tool-calling w Gemma 4, ale **model sam nie wykonuje funkcji** — aplikacja musi je zdefiniować, walidować i obsłużyć. Obecne menu to **prompt skills**, nie import zewnętrznych pluginów, instrukcji z internetu ani nowych uprawnień. Dodatkowe skille rzeczywistych działań w przyszłości wymagałyby odrębnych, typowanych adapterów i walidacji grantu. Rola należy do promptu bazowego, nie do opcjonalnego skilla.

## QA/status

- Kod zapisany na `main`, rewizja kodowa przed QA: `28c1456e753baab0be6997384c2110a3c87cd940`.
- Mac Local Agent task: `aicalls-20261104-f044-brief-skills-f047-host-qa-v1`, `scripts/verify_host.sh` + friction audit. **Wynik PENDING na czas tworzenia checkpointu; nie deklarować PASS.**
- **APK z nowym TaskBrief nie został instalowany na fizycznym S22 i nie wykonano nowego połączenia.** Następny krok po GREEN: fizyczny synthetic/off-call 4 tury z nowym promptem, dokładny telefon, IDLE, brak aktywnej kampanii i retry, kontrola grantu/role; dopiero osobne właścicielskie live testy w ramach istniejącej autoryzacji.
- Dodatkowy semantyczny validator wolnej mowy F-045 nadal pozostaje na sam koniec według decyzji właściciela; istniejące granice grant/STOP/output release pozostają aktywne.
