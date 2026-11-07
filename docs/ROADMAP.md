# AI Calls — aktualna roadmapa

**Bieżący cel:** bezpieczna, małokrokowa modularizacja laboratorium Android. Nie rozwijać równolegle nowych funkcji, jeżeli mogłoby to zasłonić regresję działającego flow INCOMING/OUTGOING. [Zasady i bramki](SAFE_MODULARIZATION_PLAN.md).

## Ukończone

- Etap 0: mapa zależności i kontrakt testowy bez zmian zachowania — PASS; [dowód](checkpoints/2026-11-06-mod-stage0-baseline.md).
- Etap 1: przeniesienie czystego IncomingIdleTimeout do conversation-core, rozdzielenie testów JVM i playback oraz dwa nowe kontrakty czasowe — PASS; [dowód](checkpoints/2026-11-06-mod-stage1-incoming-idle-core.md).
- Produkcyjna sekretarka, własny Call i dwie fizyczne sekwencje end_call — potwierdzone. Timeout 30+15 s potwierdzony przez właściciela; pełny timeline tej sesji jeszcze QA_PENDING.

## Etap 2 — ukończony: NO_CHANGE / PASS

Pełna mapa wyników rozmowy 5/5/3 i `ConversationTermination` wykazała brak uzasadnionego nowego wydzielenia. Kod pozostał nietknięty. Celowane JVM, AndroidTest compile, pełna bramka hosta (266 Python), product friction i clean Git PASS. [Dowód](checkpoints/2026-11-06-mod-stage2-outcomes-nochange.md).

## Etap 3 — pierwszy mały krok ukończony / PASS

`ConversationSessionRegistry` przeniesiono z Android app do czystego JVM `conversation-core` (body identyczne, 4 testy core). Pełna bramka hosta, native compile i friction PASS, 266 Python. [Dowód](checkpoints/2026-11-06-mod-stage3-session-registry-core.md). Następny pojedynczy krok: audyt neutralnego stanu tury, bez domyślnego refaktoru.

## Potem, w oddzielnych małych sprintach

- Etap 3: pojedyncze, czyste odpowiedzialności wydzielane z 815-liniowego AndroidConversationRuntime, bez rozdzielania mediów i cyklu własności Call.
- Etap 4: osobne audyty dużych modułów lokalnej inferencji i realtime, bez przepisywania działających silników.
- Etap 5: usuwanie wyłącznie kodu z potwierdzonym zerowym użyciem — po analizie manifestu, testów, reflection/JNI i diagnostyki. Stare probe i checkpointy nie są automatycznie balastem.

## Autonomiczny sprint — F-092 HOST PASS

Kontrakt powitania i języka INCOMING uzupełniono bez zmian telefonii. Regression JVM, AndroidTest compile, pełny host (266 Python) i friction PASS. F-092 pozostaje do **fizycznej oceny języka** po osobnej decyzji o APK/połączeniu; nie jest PROVEN_S22. [Dowód](checkpoints/2026-11-06-autosprint-f092-language-role.md).

## Read-only analiza naturalnego zakończenia

Ostatni prywatny zbiór F-091 zawiera 8 `SPEAK` i 0 `END_CALL` w czterech plikach strukturalnych: brak pewności typowanej propozycji końca, bez podstaw do ingerencji w executor lub automatycznego disconnectu po samym słowie. F-089: typowane przyczyny poprawione w source, fizyczne potwierdzenie bieżącej wersji pending. [Dowód](checkpoints/2026-11-06-autosprint-f091-readonly-diagnostics.md).

## F-091 — poprawa decyzji o zamknięciu (source/host PASS)

Przed zmianą native off-call rzeczywistego modelu na wcześniej zainstalowanym APK: PASS, 41 s. Nowy kontekstowy kontrakt end_call i negatywna historia „kolejna sprawa” przeszły RED→GREEN JVM, AndroidTest compile, full host i friction. Zmiana nie dotyka wykonawcy połączeń. **Historycznie przed instalacją nowego APK fizyczny pomiar był QA_PENDING; następnie na S22 zainstalowano `e8db8db` i uzyskano native off-call PASS. Live-call nadal QA_PENDING.** [Checkpoint](checkpoints/2026-11-06-f091-semantic-closure-source.md).

## Równoległy backlog funkcjonalny — nie mieszać z refaktorem

- Fizyczne QA manual/TAKE OVER/powrót AI/STOP i odpowiedź w 15-sekundowym oknie po ostrzeżeniu.
- F-080: ciągły zapis całego połączenia (także manual) i odtwarzacz całości/fragmentów.
- F-044/F-045/F-048: ogólne zadania, zakres faktów, walidacja wypowiedzi i niezależne dowody skutków; porównawcze korpusy STT/model/TTS i późniejsza integracja w jednym APK.

[Plan produktu](PRODUCT_ASSEMBLY_PLAN.md) · [Kwalifikacja laboratorium](TEST_LAB_SCOPE.md) · [Rejestr blockerów](../BLOCKERS.md) · [Handoff](HANDOFF_NEXT_CHAT.md). Historyczne pomiary modeli i Orange pozostają w checkpointach, nie w tym aktywnym harmonogramie.

## Gemma skills/presets/lab MVP — 2026-11-06

Zaimplementowano wersjonowane instrukcje, fabryczne i własne presety, edycję w APK oraz lokalny, wieloturowy test bez telefonu. Source/host PASS, fizyczny S22 native off-call 3/3 PASS; manualna naturalność i live-call nadal QA_PENDING. [Opis](SKILLS_AND_LAB.md).

## Skills Gemmy — matryca S22 off-call 2026-11-06

Trzy presety i izolowany zapis/rollback przetestowane native, bez realnego połączenia. [Metryki](checkpoints/2026-11-06-gemma-skills-three-presets-s22-offcall.md). Manualna naturalność i live-call osobno.

## Pełny checkpoint jakości — 2026-11-06

[Audyt kodu, dokumentacji i testów](checkpoints/2026-11-06-ai-calls-complete-code-doc-qa-handoff.md): bazowy HEAD `1d260d8`, host JVM/lint/APK/AndroidTest PASS, 266 Python, audyty PASS, 90/90 linków. Następny etap to ręczne QA edytora i naturalności bez połączeń.

## Sprint UX ustawień, historii i audio — 2026-11-06

Wdrożone i testowane etapowo: pięć kategorii ustawień (`b448ace`), skróty Rozmowy/Laboratorium (`2fca5f9`) oraz lokalny odsłuch istniejących segmentów RX/TX/ACK w archiwalnym czacie (`d59b459`). Pełny host i friction po każdym etapie PASS, końcowo 270 testów Python. [Mapa](SETTINGS_UX.md) · [Checkpoint](checkpoints/2026-11-06-settings-ux-autosprint.md).

Fizyczne S22 QA: **7/7 testów UI PASS**, w tym osiem ekranów i krótki syntetyczny WAV odtworzony z archiwum; dane, profile i presety zachowane. [Nowy checkpoint](checkpoints/2026-11-06-settings-ux-s22-physical-acceptance.md). Oczekuje: wizualnie spójne ekrany, osobna matryca edycji kontrolek, wspólne zakładki, pełny recorder/player F-080. Zero zmian w telefonii, modelu, bezpieczeństwie i uprawnieniach.

## Korekta menu po fizycznych zrzutach S22

`2f58adf` i `312db7a` naprawiają F-102 na ekranie głównym. Host 272 Python/lint/APK PASS, fizyczny S22 8/8 PASS (kontrast, insets, sekcje, czat, historia, niezmienione ustawienia). [Checkpoint](checkpoints/2026-11-06-menu-visual-s22-ux-acceptance.md). Nadal potrzebne: ręczna ocena wyglądu, spójne podstrony oraz docelowe zakładki; nie łączyć z F-080.

## i18n + spójne podstrony — rozpoczęty sprint

Fundament: `UiDesign`, PL/EN string resources, jasny panel rozmowy i testy kluczy/kontrastu. Host 275 Python PASS; fizyczny S22 QA_PENDING ze względu na ADB. Następne: przełącznik języka Auto/PL/EN, Android 10-12 fallback + Android 13 per-app locale, następnie migracja podstron partiami (bez zmiany runtime). [Receipt](checkpoints/2026-11-07-i18n-dock-foundation-host.md). F-103 OPEN.

## i18n physical acceptance — 2026-11-07

Light dock + PL/EN resource foundation **S22 10/10 PASS**, no calls/data loss. [Receipt](checkpoints/2026-11-07-i18n-dock-s22-acceptance.md). Still TODO: locale chooser Auto/PL/EN, all subcreen translations/theme migration, persistence/accessibility tests. F-103 OPEN.

## Unified UI shell (2026-11-07)

Four-tab navigation, direct SettingsHub, 11 shared child-screen wrappers, common native theme and 48dp touch controls, density-aware paddings, Back header and separated basic preferences controller are on main through `0afea79`. Historical host gates: 275/276/278/280/282 Python PASS; the final controller gate is **284 Python PASS**; **S22 new UI 15/15 PASS**, signer preserved, no calls. Manual aesthetic acceptance remains OPEN; preserve F-103 PL/EN completion as separate work. [Checkpoint](checkpoints/2026-11-07-ui-unified-navigation-checkpoint.md).

## Calls UX, screenshots and locales (2026-11-07)

Owner banned screenshot-blocking UI flags globally (F-104). Calls details now return to Calls list; per-session completed archive deletion was added with explicit confirmation and path-safe private cleanup (F-105/F-106). Button spacing (F-107) and Auto/PL/EN UI-only selector are in source; full translation of older Activities is still F-103 OPEN. [Source checkpoint](checkpoints/2026-11-07-calls-screenshots-ui-phase.md). This work requires fresh host + S22 acceptance before VERIFIED.

## UX finishing phase — current 2026-11-07

Screenshot unblocker F-104, Calls Back/Delete F-105/106, action spacing F-107, and first UI-only Auto/PL/EN locale selector are source-implemented. General, archived playback, incoming policy and AI Tools settings now have view-independent controllers. The initial S22 ADB loss (F-108) was later resolved by USB; the latest code has 21/21 physical off-call UI PASS (see 2026-11-07 acceptance below). Translate the remaining legacy screens (F-103) and separate engine/campaign UI from policy in small behavior-preserving gates. [Current checkpoint](checkpoints/2026-11-07-ux-controller-qa-adb-handoff.md).

## Physical acceptance of current UX/controller build — 2026-11-07

`035af45`: **Host 297 Python, AndroidTest/Gradle/lint/APK/friction PASS; exact S22 21/21 native UI PASS**. Same APK signer, `-r` preserving data; Calls Back/deletion tested only with synthetic archive; screenshot actually captured in memory without export; no live call. F-104/F-105/F-106/F-108 VERIFIED; F-107 manual visual audit and F-103 full PL/EN still OPEN. Controller boundaries for archived media, incoming answering and tools accepted, but Campaign/Engine/Preparation/Skills UI still coupled. [Receipt](checkpoints/2026-11-07-s22-ux-latest-21-21-acceptance.md).

## Minimalizm UI i Tabler — 2026-11-07

`6a3aad0`: usunięte powtórzenia w Home i live chat, historia Calls jako jeden wiersz z kierunkiem i bezpiecznym koszem, 11 spójnych wektorów Tabler/MIT, płaskie akcje, `UiSelect` + zwijany język/wygląd. **Mac full gate 300 Python / AndroidTest / lint / APK / anti-friction PASS, Samsung S22 22/22 instrumented PASS**, app/test APK zainstalowane `-r` i dane zachowane. Wszelkie realne rozmowy i AI effects poza zakresem. [Odbiór](checkpoints/2026-11-07-minimal-ui-tabler-s22-22of22.md). Dalsze UX: ręczny audyt wizualny F-107, komplet PL/EN F-103 i separacja pozostałych Activities.

## Sprint UI polish 2 — rzeczywisty odbiór Samsung S22 (2026-11-07)

Źródło `b4370ac`, Mac host `304 Python + JVM + AndroidTest + APK/lint/friction PASS`. Dokładny fizyczny Samsung S22: aplikacja + test APK wgrane bez czyszczenia danych; `27/27` natywne testy UX, `15/15` ekranów i `6/6` rozszerzonych stanów — razem `48/48` PASS, **37 prywatnych JPEG** na Macu w `~/Library/Application Support/AI-Calls/UI-audit/2026-11-07-polish-sprint2`. Nowe komponenty kart, akcji, dropdownów, poprawki Laboratorium, Modeli, Silników, Przygotowania, Kampanii i Skills. Brak rzeczywistej rozmowy, danych usuniętych i screenshotów wysłanych do repo. Ręczna analiza wizualna ZIP-a i F-103/F-107/F-080 pozostają otwarte. [Checkpoint](checkpoints/2026-11-07-s22-ux-polish-sprint2-48-pass-37-screenshots.md).

## Sprint 5 UX — zamknięty odbiorem S22 (2026-11-07)

Uporządkowano Skills (aktywacja bez no-op, opisy), Start (backend/model/readiness), Kampanie (jawny zapis statusu i START/STOP/Anuluj), Silniki (Zapis przed diagnostyką), Modele (hierarchia i metadane), Historię (kosz w szczegółach), Narzędzia i diagnostykę PL. Laboratorium oferuje szybki test 2 tur / pełny korpus i bieżący widok odpowiedzi. **319 host PASS, 27/27 native UI, 15/15 bazowych ekranów, 6/6 rozwiniętych, 2/2 syntetyczne UI PASS. Rzeczywisty dwuturowy test lokalnego modelu i STOP PASS** na S22. 43 prywatne screenshoty, dane zachowane, zero realnych połączeń. [Checkpoint](checkpoints/2026-11-07-ux-sprint5-live-lab-s22-acceptance.md). Następna praca: ręczny audyt 43 screenów, oddzielnie od głębokiej QA rozmów i otwartych F-103/F-080.
