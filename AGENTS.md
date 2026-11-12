# AI Calls — instrukcje pracy

## Źródło prawdy
- Bieżący cel i **wyłącznie otwarte** problemy: [PROJECT](docs/PROJECT.md). Kontrakt czytaj w odpowiednim module [indeksu](docs/README.md), nie we wszystkich plikach.
- **Dokumentacja jest indeksem decyzji, nie dziennikiem pracy.** Nie zapisuj przebiegu sprintów, liczby testów, historii commitów, katalogów kandydatów ani opisów zachowania widocznego w kodzie. Dowody: Git i niezmienne wyniki Local Agenta. Nie twórz handoffów, checkpointów, BLOCKERS ani równoległych roadmap.
- Aktualizuj dokumentację **tylko** przy zmianie istotnego kontraktu, decyzji lub stanu blokującego MVP; zamknięty problem usuń. Przed dopisaniem skróć istniejący wpis. Cel: minimalny kontekst dla kolejnego agenta, nie pełne archiwum.

## Wykonanie
- Aktualny `main`, czysty checkout i brak kolizji z zadaniami hosta. Kod/Git: GitHub Connector; preflight i testy: Mac Local Agent według [LOCAL_AGENT](docs/operations/LOCAL_AGENT.md). Bez Codex, Work, GitHub Actions (także ich odczytu), nowych gałęzi i force-push.
- Existing Code First: KEEP/IMPROVE/ADAPT/REPLACE na podstawie konkretnej luki, testu i licencji. Nie przepisuj bez dowodu sprawdzonego Call runtime, Samsung PCM, Shizuku, STOP, Gemma, Skills ani recordera.
- `bash scripts/bin/test-profile.sh preflight`; dla zmienionego kodu celowane `auto <base-SHA>` lub `focus`; `full` raz po dużym freeze. Nie kompiluj APK bez planowanej autoryzowanej instalacji. Bez niepotrzebnego ponawiania testów.
- Nie wprowadzaj sztucznych limitów, potwierdzeń ani tarcia; zachowaj scoped grant, exact Call, idempotencję, STOP i prywatność.

## Bezpieczeństwo
- Zmiany kodu i testy off-call nie upoważniają do prawdziwego połączenia, rezerwacji, ujawniania danych ani instalacji. Realny Call wymaga dokładnego numeru, SIM, zakresu zgody, zweryfikowanego S22, uprawnień i IDLE. Wynik UNKNOWN nie jest sukcesem. [SECURITY](docs/architecture/SECURITY.md).
- STOP/TAKE OVER najpierw unieważniają AI, potem sprzątają; nie rozłączaj obcego Call. Nie zapisuj numerów, WAV, transkryptów ani sekretów w Git, logach i receipts.
- Nigdy `connectedDebugAndroidTest` ani `adb uninstall` na prywatnym S22; instrumentacyjne tylko na emulatorze. Nie włączaj `FLAG_SECURE`.
- Brak próby na urządzeniu oznaczaj **PHYSICAL_OPEN**, nie PASS. Dokumentacyjny gate i limity: [DEVELOPMENT](docs/operations/DEVELOPMENT.md).
