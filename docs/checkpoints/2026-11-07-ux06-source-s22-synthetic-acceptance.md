# AI Calls UX-06 — statusy i scenariusze: kwalifikacja źródłowa oraz syntetyczne S22

Data: 2026-11-07. **Testowany kod**: `main@25a41a081be032cf717c6a0f7e78f6a704488611`. **Stan:** `SOURCE_PASS / S22_SYNTHETIC_10_OF_10_PASS / NO_REAL_CALL / PRODUCT_SCOPE_PARTIAL`.

## Zmiany od UX-05

- Start: rozdzielone status konfiguracji / autoryzacji / feedback oraz aktualna faza serwisu. Przycisk jawnie opisany „Rozpocznij zadanie CLIR (skrypt + AI)”, ponieważ start z Home działa dziś wyłącznie dla tego zadania.
- Kampanie: czytelny opis Android dialer role i stan, Skills i test AI dostępne bez jednoelementowych rozwinięć, bogata sekcja nagrań pozostaje rozwijana, techniczne szczegóły historii bez rozwijania; oddzielny feedback przycisków.
- `CampaignRuntimeStatusStore`: czas historycznego zdarzenia zapisywany atomowo; starsze rekordy bez czasu pozostają czytelne. Typowane `CampaignLivePhase` obserwuje **wyłącznie bieżący proces aplikacji**, nie wyciąga live z historycznej wiadomości i nie potwierdza własności połączenia ani sukcesu zewnętrznego.
- „Anuluj zadanie” dotyczy tylko OUTGOING i nie usuwa odrębnej polityki sekretarki INCOMING, w przeciwieństwie do globalnego STOP AI.
- Scenariusze: 10 factory presetów (CLIR, mechanik, przychodnia, dentysta, fryzjer, restauracja, urząd, reklamacja, dostawca usług, wycena). Własne kopie presetów z nazwą/celem, wersjonowanie, restore i delete, formularze, podgląd, zapis szkiców w istniejącym szyfrowanym Keystore/AtomicFile. **Tylko CLIR ma wykonawczy adapter**; reszta to DRAFT_ONLY.
- Nie naruszono zamrożonego Samsung/Shizuku RX/TX, polityk ujawniania faktów, Call ownership, STT/TTS ani wywołań numerycznych.

## Dokładne dowody testowe

- `aicalls-ux06-personal-scenario-encrypted-status-host-20261107-v5`: **DONE/0**, `3c7a8b5`, 340/340 Python, JVM, AndroidTest compile/APK, friction PASS.
- `aicalls-ux06-live-state-source-qa-20261107-v6`: **FAILED przez wywołanie** Gradle bez `ANDROID_HOME` przed uruchomieniem testów; nie był to test FAIL kodu.
- `aicalls-ux06-live-state-host-sdk-fixed-20261107-v7`: **DONE/0**, `25a41a0`; `CampaignLiveStateResolverTest` PASS, 3/3 focused Python, **341/341 Python**, `verify_host.sh` full Gradle JVM/lint/assembleDebug/AndroidTest compile PASS, friction PASS, clean Mac checkout.
- `aicalls-ux06-s22-idle-preflight-20261107-v1`: **DONE/0**, USB `RFCT70L7E8J` / `SM-S906B`, `mCallState=0`, AppOwnedCampaignService absent, installed app present.
- `aicalls-ux06-s22-safe-synthetic-ui-acceptance-20261107-v1`: oba APK instalacja `adb install -r -t` **SUCCESS**, zachowanie aplikacji i danych bez odinstalowania; narzędzie `am instrument` pokazało **OK (10 tests)**. Wrapper zgłosił **FAILED** z powodu zbędnego wymogu `INSTRUMENTATION_CODE: -1`, którego ta odpowiedź ADB nie zawierała; postcheck w tej próbie pominięto. Dowodu tego błędnego wrappera nie usuwać.
- `aicalls-ux06-s22-safe-synthetic-ui-acceptance-20261107-v2`: **DONE/0** po korekcie wrappera; 10/10 testów Android **PASS**, bez zbędnej asercji kodu raportowania, preflight i postcheck `SM-S906B`, `mCallState=0`, AppOwnedCampaignService absent, Mac checkout clean. Testy: `AndroidUiAccordionContractTest`, `AndroidUiPolishContractTest`, `AndroidScenarioLibraryContractTest`, `AndroidPersonalScenarioStoreContractTest`, `AndroidCampaignRuntimeStatusRecordTest`. Bez rozmów, dial, STOP AI ani kasowania realnych archiwów.

## Otwarte i nieudowodnione

- **F-123 nadal OPEN (częściowo zaadresowany):** live phase jest read-only i bieżąca dla procesu, ale nie jest trwałym, korelowanym z `attemptId` stanem Telecom/Call ani niezależnym efektem zewnętrznym. Dalsze typed runtime/call ownership i aktualizacja UI pozostają do wdrożenia.
- **F-122 OPEN:** wyłączenie zapisu przyszłych prób nie zamyka rejestratora aktywnej rozmowy; UI uczciwie to opisuje.
- **F-124 i F-080 OPEN:** odsłuch ostatniego katalogu zamiast wybranej sesji oraz brak ciągłego nagrania całej rozmowy.
- Edytor warunków przejścia/pytanie-odpowiedź, kvalifikowane wykonanie innych scenariuszy, pełny Lab scenario runner, snapshot Skills/scenario/strategii przy START, pełne testy real-call pozostają OPEN.
- Nie wykonywano ręcznego TalkBack, ręcznego visual review nowych screenshotów ani autoryzowanych realnych połączeń. Syntetyczne 10/10 nie dowodzi jakości rozmowy, słyszalności po stronie rozmówcy, CLIR ani potwierdzenia rezerwacji.

**Wskazówka do kolejnego sprintu:** najpierw napraw F-122/F-123/F-124 bez naruszania fizycznie zamrożonego toru mediów, potem scenariusze z realnym task authority. Zachowaj zarówno v1 FAILED wrapper, jak i v2 PASS, aby nie mieszać wyników.
