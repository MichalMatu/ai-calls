# AI Calls — UX-12: ostatni tail UX i przekazanie do odbioru funkcjonalnego

**2026-11-08.** Start `main@28795a63c813253c23a2ebb13e3c6dfd28d9ab04`; aktualny kod `47bdba6d403611ea0611a3fc4f50dc7e3843c278` (`ac6516b` + poprawka `47bdba6`). Wyłącznie GitHub Connector + Mac Local Agent, `main`, bez Codex/Work/Actions/force push.

## Zmiany
1. **F-131 / Historia:** `ConversationHistoryActivity.showSession` pokazuje kierunek i lokalną datę/czas nad długą osią czatu, nie pokazując danych personalnych. Kampanie już wcześniej rozdzielały wynik, czas i zwijane szczegóły; nie zmieniano callbacków, autoryzacji ani danych historii.
2. **F-136 / ujęcia UX:** `AndroidVisualScreenAuditTest` opcjonalnie przyjmuje odizolowany identyfikator `-e uiAuditRunId <token>`. `scripts/aicall_tools/diagnostics/ui_screenshot_inventory.py` niezależnie od stdout weryfikuje 17 nazw ekranów, indeksy 0–2, obecność 1–3 poprawnych JPEG/ekran, pozwala na bezpieczne, prywatne archiwum ZIP (bez nadpisania). Odrębny rezultat AndroidTest i rezultatu screenshotów — brak fałszywego uznania testów za FAIL przy braku komunikatów `UI_AUDIT`. Dla starszych wywołań test Android utrzymuje oryginalną lokalizację.
3. **Testy:** nowe `test_ui_screenshot_inventory.py` (7 przypadków: 1/2/3, brak, dziura, błędny JPEG, obcy JPEG, run-id, pusty katalog), `test_ux12_source_contract.py` (2). Zachowane 5 kontraktów UX09.

## Receipts i status
- **Mac źródło:** `aicalls-ux12-source-preflight-20261108-v1` — czysty `main@28795a6`, `origin/main` zgodny.
- **Mac targeted:** `aicalls-ux12-targeted-build-20261108-v1`: **7/7 + 2/2 + 5/5 Python PASS**, `ANTI_FRICTION=PASS`. Po synchro kodu czysty checkout `47bdba6`; pierwsza bezpośrednia Gradle kompilacja nie miała ustawionego SDK (błąd środowiska). **Pełny host gate:** `aicalls-ux12-full-host-gate-20261108-v1` — **PENDING do odczytu końcowego wyniku**, nie PASS.
- **Samsung S22:** `aicalls-ux12-s22-gate-readonly-20261108-v1` DONE/0: ADB `RFCT70L7E8J`, telefon `mCallState=0`; aktywny `pl.pockethost.companion/.MainActivity`. Nie uruchamiano instrumentacji i nie instalowano nowej APK, aby nie zakłócić PocketHost. UX12 fizycznie **NOT_RUN**, wcześniejsze 28/28+2/2 synthetic zachowane jako historia dla starego kodu.
- **Zewnętrzne skutki:** żadnego Call, operacji na sejfie, modeli/transferów, dzwonienia lub modyfikacji danych właściciela. F-080/F-122/F-123/F-124 nadal OPEN.
- **Zobowiązanie QA:** po udostępnieniu S22 przeprowadzić kontrolę F-131 w PL/EN, `AndroidUx09VisualSemanticsTest` i świeże 17 ekranów w izolowanym katalogu; prywatne screenshoty nie opuszczają Mac/telefonu.

## Następny sprint: odbiór funkcjonalny

Wznowić `docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md`. `NAV-01 PARTIAL` pozostaje bez zmiany, dalej NAV-02 i Start/Kampanie bez prawdziwych połączeń, Historia, Agent AI/Sejf, Lab, Modele, Ustawienia. Statusy tylko z rzeczywistym dowodem; Call tylko w konkretnym autoryzowanym zakresie.
