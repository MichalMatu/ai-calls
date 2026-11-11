# AI Calls — zasady pracy agentów

**Jedno źródło prawdy:** [docs/README.md](docs/README.md) → [docs/PROJECT.md](docs/PROJECT.md). Modułowe instrukcje w `docs/architecture`, `docs/modules`, `docs/operations`. Nie twórz dodatkowych handoffów, chronologicznych checkpointów ani pliku BLOCKERS; zamknięte zadania znikają z bieżącego stanu, a historię zmian zapewnia Git.

## Wykonanie
- Najpierw aktualny `main` HEAD, czysty Git, stan Mac Local Agent i brak kolizji. GitHub Connector + Mac Local Agent + Linux Sandbox; bez Codex, Work, GitHub Actions, nowych gałęzi, force-push i bez odczytu GitHub Actions.
- **Existing Code First:** dla konkretnej luki porównaj własny baseline z maksymalnie 2–3 kandydatami; wybierz KEEP/IMPROVE/ADAPT/REPLACE tylko na podstawie przewagi, testów i licencji. Bez niepotrzebnego rewrite. [Reguła](docs/operations/EXTERNAL_REUSE.md).
- Modyfikuj istniejące moduły, nie przepisuj sprawdzonego runtime, STOP, Samsung/Shizuku, Gemma E2B, Skills, autoryzacji ani recordera. Szybki flow: `bash scripts/bin/test-profile.sh preflight`; `auto <pre-sprint-SHA>` albo `focus <module> '<test>'`; jedno `full` po większym freeze. APK wyłącznie `device-build` bezpośrednio przed faktycznie autoryzowaną instalacją. [Procedura](docs/operations/DEVELOPMENT.md).
- Nie dodawaj sztucznych ograniczeń, limitów tur/prób, redundantnych potwierdzeń ani czynności manualnych bez materialnej przyczyny. Utrzymuj test regresyjny anti-friction; **nie** usuwaj granic Androida, scoped grant, STOP, Call ownership, idempotencji i prywatności.
- Każda nowa luka: opisz w sekcji `Otwarte` jednego [PROJECT.md](docs/PROJECT.md) konkretny objaw, owner modułu, test/gate. Po naprawie i dowodzie usuń wpis, zaktualizuj aktualny status. Nie zachowuj duplikatów i dawnej chronologii.

## Opcjonalna analiza zależności kodu

- Przy większej zmianie na zweryfikowanym Mac Local Agent i czystym HEAD
  użyj `la-graphify status /ABS/REPO`, a przy świeżym grafie
  `la-graphify impact /ABS/REPO relative/source.kt`. Gdy nie znamy
  właściciela modułu, pomocniczo `la-openvisio skeleton /ABS/REPO "task"`.
- OpenVisio MCP nie jest podłączony: bez `init`, `mcp`, `--watch`,
  `transport` i skanowania katalogu domowego. Narzędzi nie uruchamiaj
  obowiązkowo ani automatycznie; wyniki grafów są hipotezami do
  sprawdzenia w kodzie. Nie zastępują testów, granic Call ani emulatora.

## Granice zewnętrznych skutków
- Zwykła praca inżynierska i testy bez skutków mogą postępować autonomicznie; to **nie jest** grant na nieokreślone realne połączenia, zakup, rezerwację, ujawnienie danych ani modyfikację konta.
- Fizyczny Call / instalacja / prawdziwa operacja: dokładny zakres upoważnienia, zweryfikowany S22 i stan IDLE, własność konkretnego `Call`, uprawnienia systemowe, prawdziwy wynik. Nigdy nie obchodź odmowy narzędzia. `TAKE OVER` i STOP najpierw unieważniają AI, potem porządkują zasoby. [Reguły](docs/architecture/SECURITY.md).
- `CallCommitmentGate`, `FactDisclosurePolicy`, `IdentityVault`, uprawnienia i wynik zewnętrzny pozostają po stronie aplikacji. Nie wymyślaj potwierdzeń skutków; nie ujawniaj prywatnych WAV/transkryptów/tokenów w Git, logach lub wynikach zadań.
- Na prywatnym S22 z danymi użytkownika nie uruchamiaj connectedDebugAndroidTest ani adb uninstall. Standardowy runner może odinstalować główny APK, skasować dane i zresetować rolę dialera. Testy instrumentacyjne wykonuj na odizolowanym emulatorze; na S22 tylko nieinwazyjne kontrole i aktualizacje adb install -r po sprawdzeniu urządzenia i stanu IDLE, bez testowych połączeń.
- Nie włączaj blokowania zrzutów ekranu (`FLAG_SECURE` i ekwiwalenty) — decyzja właściciela. Zrzuty nadal traktuj jako potencjalnie wrażliwe.

## Zakończenie pracy
Pozostaw aktualne `docs/PROJECT.md`, poprawny indeks/linki, **budżet i quality gate dokumentacji** (`scripts/aicall_tools/diagnostics/docs_check.py`), zielone odpowiednie testy i czysty `main`. Zamiast nowych archiwów, korzystaj z commitów Git i niezmiennych receipts Mac Local Agent. Jeśli coś nie zostało sprawdzone na urządzeniu, pisz `PHYSICAL_OPEN`, nie `PASS`.
