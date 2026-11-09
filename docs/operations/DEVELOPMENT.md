# Development — FAST FLOW i repozytorium

## Zasada
Jeden aktualny plan [PROJECT](../PROJECT.md), jeden indeks [README](../README.md), trwałe kontrakty modułowe. Nie twórz `BLOCKERS.md`, `checkpoints/`, kolejnych `NEXT_CHAT`, kronik i równoległych roadmaps. Przy zamknięciu zadania aktualizuj jedynie odpowiedni moduł i obecne statusy; Git commits i Host Control receipts są źródłem chronologii. Nie dopisuj historycznych PASS do bieżącej instrukcji.

## Iteracja kodu
1. Pobierz faktyczny `main` HEAD i zachowaj `base-SHA` na start sprintu. Sprawdź czysty Git, brak równoległej pracy na Macu i ostatnie rzeczywiste receipts. Bez nowych gałęzi i force-push.
2. Zmapuj zależności, zmieniaj istniejące moduły, kilka spójnych porcji, małe commity `[skip ci]`. Nie uruchamiaj automatycznie GitHub Actions (ani nie sprawdzaj statusu); nie używaj Codex/Work.
3. `bash scripts/bin/test-profile.sh preflight` — szybka kontrola diff, anti-friction i lokalnych linków dokumentacji, bez Gradle.
4. `bash scripts/bin/test-profile.sh auto <base-SHA>` — tylko zmienione moduły i sparowane Python; `focus <module> '<test-class>'` lub `changed <module>` dla dokładnej regresji. Nie powtarzaj G3/G5.3 i testów sprzętu gdy kontrakt nie został zmieniony.
5. `bash scripts/bin/test-profile.sh full` — jeden większy host unit/lint/Python/audio/security gate na stabilnym zakresie, **bez APK**. `device-build` jedynie bezpośrednio przed rzeczywiście autoryzowaną instalacją na S22. Żaden profil nie dzwoni ani nie instaluje.
6. Zapisz w [PROJECT](../PROJECT.md) wynik, czego nie sprawdzono i co dalej, spójny `main`, żadnych dodatkowych handoffów.

## Cache i hooki
`gradle.properties`: build caching, configuration caching, parallel + trwały daemon. Nie używaj `--no-daemon` i nie czyść `.gradle`/`~/.gradle` bez zdiagnozowanej usterki. `scripts/bin/test-profile.sh` odkrywa istniejący lokalny Android SDK. Repo-local `scripts/bin/git-hooks/pre-commit` jest lekki/statyczny; `hooks-enable` aktywuje go na Macu. GitHub Connector pomija lokalny hook: jawny host QA pozostaje konieczny.

## Właściciel plików
Nowa implementacja należy do istniejącego katalogu: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`, `service-packs`, `scripts`. Nie dodawaj nowego rootu bez uzasadnienia i aktualizacji allowlisty w `scripts/verify_host.sh`; test tego kontraktu: `scripts/tests/test_verify_host_layout.py`. Python umieszczaj w `scripts/aicall_tools/<domena>/` z odpowiadającym `scripts/tests/...`; nie dokładaj płaskich skryptów w `scripts/`. Dane testowe tylko w `benchmarks/` i prywatnych repo lokalnych, nie przyklejaj surowych rozmów do dokumentacji.

## Diagnostyka
`scripts/bin/aicalls` — lokalny dispatcher; `scripts/aicall_tools/diagnostics/` — eksport USB i bramki audio, no-clobber; `scripts/verify_host.sh` — host full; `scripts/local_agent/*.sh` — wrappery dla Mac, nie drugi scheduler. `scripts/aicall_tools/diagnostics/docs_check.py` wymusza jeden indeks i poprawne lokalne linki; `audit_product_friction.py` nie wymaga już historycznego rejestru. Każdy test ocenia tylko zmieniony kontrakt.

## Dokumentacja — limit jakości

Jedno źródło aktualnych decyzji: [PROJECT](../PROJECT.md); dokumenty modułów opisują trwałe kontrakty, bez chronologii sprintów. Zmiany Markdown przechodzą w istniejącym [docs_check.py](../../scripts/aicall_tools/diagnostics/docs_check.py) uruchamianym przez `preflight` i lokalny pre-commit hook. Brak sieci, Gradle, Node, pobierania zależności i dodatkowych pipeline'ów.

- **Budżet całkowity:** ostrzeżenie po 42 KiB, twardy limit 50 KiB, łącznie z głównymi instrukcjami `README.md` i `AGENTS.md`.
- **Pojedynczy dokument:** ostrzeżenie po 6 KiB, błąd po 8 KiB; dla `AGENTS.md` twardy limit 6 KiB.
- **Jakość:** dokładnie jeden nagłówek H1, niepowtarzalne nagłówki sekcji, wykrywanie bliskich duplikatów długich akapitów, brak chronologicznych nagłówków i alternatywnych sekcji statusu.
- **Nawigacja:** dokument musi należeć do zatwierdzonego modułu i być obecny w indeksie; każdy względny link do Markdown **lub kodu** musi rozwiązywać się w repozytorium. Linki zewnętrzne nie są sprawdzane sieciowo.
- **Dopisywanie:** najpierw skracaj lub aktualizuj istniejący dokument. Jeśli nowy moduł jest faktycznie potrzebny, aktualizuj świadomie `DOCS` i indeks. Zmiana samych limitów wymaga uzasadnienia i testu; nie omijaj bramki przez kopiowanie dokumentów do innych folderów.

`bash scripts/bin/test-profile.sh preflight` sprawdza to bez kompilacji; `python3 -m unittest scripts.tests.test_docs_check` uruchamia testy mutacyjne. Rozmiary są wyrażane w bajtach UTF-8 (KiB = 1024 B).