# AI Calls — aktualny handoff (2026-11-07, Sprint 6)

> **Jedyny bieżący handoff.** Po otwarciu nowego czatu sprawdź świeży `main` i wyniki Local Agenta. Historyczne handoffy i testy są w Git oraz [checkpoints](checkpoints/); nie stanowią nowych zadań.

## Punkt startowy

- Repo: `MichalMatu/ai-calls`, `main`, bez nowych gałęzi, force-push, Codex, Work ani GitHub Actions. Środowisko: GitHub Connector + **Mac Local Agent**, `macbook-michal`, installation `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; checkout `/Users/michal/agent-workspace/repos/ai-calls/work`. Commity `[skip ci]`.
- **HEAD funkcjonalny Sprint 6:** `0dface1e86e6bf9bab65090c3af293ff12f9dc7c`. Nowsze commity dokumentacyjne są możliwe; przed pracą sprawdź dokładny HEAD i clean worktree.
- **Checkpoint nadrzędny:** [Sprint 6 — kod, QA, S22 i dalsze granice UX](checkpoints/2026-11-07-ux-sprint6-source-green-s22-adb-pending-doc-freeze.md).
- **Bieżący rezultat:** `SOURCE_GREEN`. Mac `aicalls-20261107-ux-sprint6-source-host-qa-v3` DONE / exit 0: **325 host PASS**, 33 celowane Python, AndroidTest compile, JVM/APK i friction PASS. Kod nowych ekranów jest w `main`.
- **Sprzęt Sprint 6:** `S22_QA_PENDING_ADB`. `...s22-install-28ui-24visual-42screens-v2` FAILED preflight: `adb devices -l` puste, exact S22 niedostępny. Nie instalowano tej rewizji, nie otrzymano ZIP. `...s22-real-gemma-follow-stop-v1` FAILED prerequisites: 602 s oczekiwania na ZIP, model nie ruszył. Żadna z tych porażek nie dowodzi błędu Gemmy.
- **Ostatni fizyczny baseline:** Sprint 5 `0bd17da` — 27/27 natywne UX, 23/23 wizualne, 43 prywatne zdjęcia, realne offline 2 tury + STOP PASS. Nie przypisuj tego wyniku Sprintowi 6. [Dowód](checkpoints/2026-11-07-ux-sprint5-live-lab-s22-acceptance.md).

## Co jest gotowe w Sprint 6

1. Historia rozmów: **„Usuń wszystkie zakończone rozmowy”** na dole listy z potwierdzeniem. Usuwane są tylko sprawdzone COMPLETE/END prywatne archiwa i ich audio, **nie** sesje aktywne/nieukończone; testy hosta na izolowanych danych. Na S22 test kasowania ma **jedynie anulować** dialog, nie zatwierdzać usuwania prawdziwych rozmów.
2. Chat historyczny: dotknięcie dymku z WAV odtwarza, ponowne zatrzymuje, bez przycisku pod dymkiem; niepowiązane klipy pozostają w zwijanej sekcji. Player nie działa w czasie rozmowy.
3. Lab: poprawione przewijanie do ostatniej tury, wyraźny STOP, rozdzielone wyniki END_CALL od semantyki i ostrożna heurystyka pytań bez `?`.
4. Kampanie/Skills/Silniki: czytelny historyczny status, krótsze CTA, zapis i gotowość we właściwej hierarchii; bez zmian autorizacji i telekomunikacji.

## Co wykonać tylko raz, po spełnieniu warunków

1. Odzyskaj i sprawdź **dokładny** S22 `SM-S906B / RFCT70L7E8J` w ADB jako `device`, stan IDLE, brak aktywnej kampanii/retry, rola dialera i zgodność certyfikatu.
2. Ponów **tylko** niezakończoną bramkę Sprint 6: 28 natywne UI, 24 wizualne, min. 42 prywatne zrzuty, `adb install -r -t` bez utraty danych i zero realnych rozmów. Zapisz nowy receipt i ZIP na lokalnym Macu; niczego prywatnego nie commituj.
3. Dopiero po 42 screenshotach: realna lokalna Gemma **2 tury + widoczny ostatni dymek + STOP**, docelowo 46 screenshotów. Jeżeli ADB nadal nie ma, zaznacz `QA_PENDING_ADB`; nie wykonuj pętli niekończących się retry.
4. Właściciel przekazuje prywatny ZIP do ręcznej oceny. Dopiero wtedy merytoryczne mikro-UX i decyzja o nowym sprintcie.

## Następna praca architektoniczna — oddzielona od sprzętu

**Rekomendacja:** stopniowe rozdzielenie UX od logiki, nie masowe przeniesienie klas. Najpierw read-only `CampaignExecutionStatusPresenter` (samo mapowanie kodów wykonania), potem `ConversationHistoryController`/stan, później `SkillLabRunner`/stan. Działające `GeneralSettingsController`, `IncomingAssistantSettingsController` i `ConversationToolsSettingsController` pozostają. Każdy krok: mapa wywołań → testy → jedna zmiana → właściwy host gate; nie dotykać Samsung/Shizuku/Telecom.

**Otwarte:** F-103 (pełne PL/EN), F-080 (ciągłe nagranie całej rozmowy), osobne live-call QA i naturalność Gemmy. Statusy wyłącznie w [BLOCKERS](../BLOCKERS.md), decyzje architektoniczne w [SAFE_MODULARIZATION_PLAN](SAFE_MODULARIZATION_PLAN.md) i [ARCHITECTURE](ARCHITECTURE.md).

## Niezmienniki

- Ważny, odwoływalny grant konkretny cel/SIM/efekt/fakty, ownership jednego Call, brak efektu po samym modelu; STOP i revoke mają pierwszeństwo.
- `END_CALL` dopiero po zatwierdzeniu i pełnym TX; INCOMING cisza 30 + 15 s; nie zmieniać zamrożonego PCM/PFD, Shizuku, RX/TX, recorder i takeover w sprincie UX.
- S22 testy tylko na `IDLE` i bez prawdziwych połączeń bez nowej wyraźnej autoryzacji. Żadnych prywatnych WAV, numerów czy rozmów w GitHub.
- Weryfikacja kodu `scripts/verify_host.sh`, testy właściwego zakresu, audit_product_friction, clean `main`; bez niepotrzebnego powtarzania PASS.
