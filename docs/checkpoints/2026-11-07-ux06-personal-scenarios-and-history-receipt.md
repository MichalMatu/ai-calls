# AI Calls UX-06 — własne scenariusze i prawdziwy czas historii

Data: 2026-11-07. Repo `MichalMatu/ai-calls`, `main`. Stan: `SOURCE_IMPLEMENTED / HOST_V4_PASS / HOST_V5_QA_PENDING / S22_NOT_RUN`.

## Kod wdrożony w tym etapie

- `704e697ca2554679edfa2328129b3c3cd1c5a7a2`: poprawiono dopuszczanie zapisu niepełnych formularzy przed poznaniem terminu wizyty. Dane wciąż nie autoryzują rozmowy.
- `8508d2ed2a507840dddefe3601add567f0293ff4`: `ScenarioPersonalLibrary`: kopiowanie, edycja, wersje i przywracanie w niemutowalnym modelu, walidacja ID/template/fieldów, JVM tests.
- `cf422164f9f5bd7b9e293e12b830d7d5f7c50f1a`: `AndroidConversationPreparationStore.loadPersonalScenarios/savePersonalScenarios`; powtórne użycie tego samego Keystore AEAD i AtomicFile, brak drugiego plaintext magazynu.
- `b2a2fc57a42ff204d2a6ea1f49214fd92007fb17`: ekran „Moje scenariusze”, kopiowanie fabrycznych presetów, własny tytuł/cel, zapis wersji, przywrócenie i bezpieczne usuwanie własnych. Bez zmiany fabrycznych presetów ani telefonii.
- `a62e9da230dad7836cf4dfafd804ce1fe69a8019`: izolowany AndroidTest synthetic encrypted persistence + dwa source contract Python (wyłącznie laboratoryjne dane, nie dotyka rzeczywistego grantu ani historii).
- `b74284b4c163f413114f9e050f68d30e8e213109`: historyczny `CampaignRuntimeStatusStore` z atomowym czasem zdarzenia, bez mylenia z bieżącą sesją; zgodność starszych rekordów; izolowany AndroidTest.
- `3c7a8b526e6bec2b8ed27c0ca69a3e303a404fdb`: ochrona niezapisanych zmian tylko przy faktycznych różnicach dla wszystkich ścieżek Back, baseline po zapisie. Zero nowych potwierdzeń dla już zapisanych danych.

## Testy i dowody

- `aicalls-ux06-scenarios-source-host-20261107-v3`: **DONE/0**, `a03e98b`, ScenarioCatalogTest, 338 Python, pełny Gradle build/JVM/AndroidTest compile/APK i friction PASS, clean worktree, brak S22.
- `aicalls-ux06-personal-scenarios-host-20261107-v4`: **DONE/0**, `b2a2fc5`, ScenarioPersonalLibraryTest PASS, 338 Python, `verify_host.sh`/APK/AndroidTest compile + friction PASS, clean Mac.
- `aicalls-ux06-personal-scenario-encrypted-status-host-20261107-v5`: **QUEUED**, dla `3c7a8b5` i nowych testów, dopóki nie ma terminalnego wyniku **QA_PENDING**.
- Fizyczne AndroidTest na S22: **NOT_RUN**, osobny preflight `SM-S906B`, `IDLE`, aktualny signer, zachowanie danych i brak aktywnej kampanii. Żaden call nie został uruchomiony.

## Niezakończone zobowiązania

1. F-123: typowany i faktyczny live-state z korelacją zdarzeń, niezależny od historycznego tekstu. Znacznik czasu nie zastępuje tej funkcji.
2. F-122: wyłączenie aktualnego rejestratora audio podczas rozmowy, osobna operacja i race test, bez zmiany zamrożonego RX/TX bez uzasadnienia.
3. F-124/F-080: jednoznaczny wybór sesji i spójny odtwarzacz; ciągły recorder nadal osobne zadanie.
4. Formularze nie mają jeszcze produkcyjnego połączenia dla Mechanika/Przychodni. Wybór scenariusza przed START, strategia, skills snapshot, dodatkowe fakty/effect gates i Lab scenario test pozostają do wdrożenia.

## Granice i ochrona danych

Bez zmiany grant/SIM, CLIR ownership, STOP, media, bez rejestrowania na GitHub danych osobowych. Wpisy „Moje scenariusze” to wyłącznie drafty, nie zgoda na połączenie/rezerwację/płatność. Brak GitHub Actions/Codex/Work. Kolejny host task wyłącznie po weryfikacji v5.
