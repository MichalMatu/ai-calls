# AI Calls — audyt czytelności menu po zrzutach S22

**Data:** 2026-11-06. **Repo:** `MichalMatu/ai-calls`, `main`. **Bazowy HEAD:** `402199cea3575cf09d86bf7e7f15a66a2130ca60`. **Zielone commity:** `2f58adf48b49e0389b0d55304bffea8ae2340022`, `312db7ab4b974225099cf8a3a73076e263d4cf9f`. Oddzielne historyczne błędy quality gate nie zostały przemilczane.

## Źródło problemu (F-102)

Dwa zrzuty fizycznego S22 pokazały nieczytelne nagłówki (biały na jasnoszarym), czarne wartości pól Spinner na ciemnych sekcjach, nakładanie paska Androida, długą kolumnę jednakowo ciężkich przycisków i zbyt duży dolny panel czatu. Wcześniejszy fizyczny test 7/7 dowodził działania ekranów i syntetycznego odsłuchu, nie czytelności wizualnej.

## Zmiana etapowa i bramki

1. **Kontrast i safe areas** — `2f58adf`: wymuszone jasne powierzchnie kategorii, ciemny tekst, kontrastowe pola wyboru i kontrolki, składane kategorie i domyślnie złożony czat na home, wcięcia system bars obsługujące API 29 i nowsze. Mac receipt `aicalls-20261106-menu-contrast-insets-api29-gates-v1` **DONE/0**, kompilacja AndroidTest, full host 270 Python, lint, APK i anti-friction PASS. Poprzednie FAILED: `...menu-contrast-insets-source-v1` (zbyt ścisły anchor w zadaniu), `...menu-contrast-insets-source-v2` (lint API 29). Publikacja `aicalls-20261106-menu-contrast-publish-v1` DONE/0.
2. **Hierarchia i kontrakty wizualne** — `312db7a`: jeden wyraźny START, w drugim rzędzie Kampanie/STOP, oddzielny rząd Rozmowy/Laboratorium, sekcje ustawień poniżej, ciemny tekst na jasnych powierzchniach, status modelu w osobnym panelu, polskie etykiety pierwszego ekranu. Chat główny jest kompaktowy, z zachowanym rozwijaniem i prywatną historią. Mac receipt `aicalls-20261106-menu-hierarchy-contract-rebuild-v4` **DONE/0**, kompilacja AndroidTest, full host **272 Python**, lint/APK i anti-friction PASS. FAILED `...menu-hierarchy-source-gates-v1`, `...menu-hierarchy-repair-gates-v2`, `...menu-hierarchy-test-typo-gates-v3`: błędy budowy kontraktu testowego, nie produkcyjnej logiki. Publikacja `aicalls-20261106-menu-hierarchy-publish-verified-v1` DONE/0.
3. **Fizyczny S22** — `aicalls-20261106-menu-physical-s22-ux-matrix-v1` **DONE/0**: S22 SM-S906B, 2× IDLE, brak kampanii i retry, zgodny podpis instalowanego APK, `install -r` bez kasowania danych. Instrumentacja: `AndroidUiAccordionContractTest` **2/2**, `AndroidMenuVisualContractTest` **3/3** (kontrast WCAG 4.5:1, wybór STT/LLM, zwijanie czatu, insets), `AndroidSettingsNavigationMatrixTest` **3/3** (8 ekranów, zachowana konfiguracja). **Łącznie 8/8 PASS**, po teście S22 IDLE, brak aktywnej kampanii i pending retry, **zero połączeń**.

## Zakres i otwarte punkty

Ten sprint naprawia **ekran główny** i wspólny komponent sekcji; nie przebudowuje wszystkich ekranów podrzędnych ani nie wprowadza ostatecznego paska zakładek. Ręczna ocena estetyki/wygody przez właściciela na nowym APK jeszcze nie nastąpiła, więc nie należy oznaczać całej aplikacji jako finalnej UX. Następne etapy: spójny system nawigacji podstron i pełna matryca edycji każdej opcji. `F-080` nadal **OPEN** — pełny ciągły zapis audio sesji, manual takeover i player całości są osobnym wymaganiem. Nie modyfikowano Android Call, upoważnień, Samsung RX/TX, Shizuku, modelu, magazynów danych ani nagrań.
