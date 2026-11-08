# AI Calls — UX-09 / Pięć problemów ze screenshotów UX-08

Data: 2026-11-08. Baseline `main@8ec31adda869dc4ac00a76bcac44e863b3fd21f0`.

1. **Skills:** wyłączona instrukcja jest ukryta do rozwinięcia, ale można ją nadal obejrzeć, włączyć i edytować. Nazwa nie wiąże Skills z Gemmą.
2. **USB:** jeden tytuł, jedna ramka sekcji JSON, wygodniejsze marginesy, komplet dawnych danych.
3. **Kampanie:** ostatni status ma osobny stan, znacznik czasu i rozwijane objaśnienie; źródłowy zapis techniczny pozostaje. Zakończenie Call nie znaczy sukcesu zadania.
4. **Start:** status upoważnienia jest najważniejszy; szczegóły modelu są rozwijane; pusty panel rozmowy znika, ale pozostaje osiągalny ręcznie, a przy aktywnej/zapamiętanej rozmowie wraca automatycznie.
5. **Agent AI:** widoczne nazwy aktywnych Skills, kategorie pól obu profili i obecne kategorie danych w IdentityVault bez odczytu wartości. Konfiguracja nie daje grantu na ujawnienie danych.

**Weryfikacja:** `AndroidUx09VisualSemanticsTest`, `test_ux09_source_contract.py`, pełny Mac `verify_host.sh`, friction, następnie bezpieczny odbiór S22 IDLE/screenshoty, jeśli najnowszy HEAD przejdzie bramkę.

**Stan bazowy:** SOURCE_ONLY / QA_PENDING do dokładnego receipt. Nie wykonano połączeń, pobierania modeli, resetu sejfu lub zmiany uprawnień. Nie ruszono Shizuku PCM/Telecom/runtime. F-122, F-123, F-124, F-080 i F-136 pozostają niezależne.
