# AI Calls — NOWY CZAT: kompleksowy funkcjonalny odbiór ekran po ekranie

Wznów projekt `MichalMatu/ai-calls` w trybie **GitHub Connector + Mac Local Agent**. Pracuj autonomicznie i metodycznie, bez Codex, ChatGPT Work, GitHub Actions (również bez odczytu statusu), tworzenia nowych gałęzi i force-push. Repo `main`, małe commity `[skip ci]`. Mac Local Agent jest na `macbook-michal`, fizyczny testowy telefon to Samsung S22 `SM-S906B`. Sprawdź bieżące połączenie/stan narzędzi — nie zakładaj ich z poprzedniej rozmowy.

## Pierwszy obowiązkowy krok — preflight i rekonstrukcja
1. Pobierz **aktualny** `main` HEAD; nie ufaj SHA z handoffu. Zweryfikuj checkout Mac, czysty `git status`, stan Mac Local Agent i dokładną wersję APK na telefonie, obecność modeli oraz gotowość S22. Sprawdź `IDLE`, aktywne Call/usługi/role/uprawnienia zanim użyjesz ADB.
2. Przeczytaj `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/checkpoints/2026-11-08-pre-functional-full-checkpoint.md`, `docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/TEST_LAB_SCOPE.md` i `BLOCKERS.md`.
3. Zrekonstruuj już zaliczone dowody: UX-11 **356/356 Mac**, **28/28 Android UI**, **2/2 floating synthetic**, 18 screenshotów; model lokalny **2 tury + STOP PASS**; polski TTS→Parakeet STT **PASS**. Nie powtarzaj ich bez nowej przyczyny. Żaden z tych wyników nie dowodzi realnego Call.

## Zadanie — przejdź ekran po ekranie, funkcja po funkcji
Prowadź **jedną żywą macierz** `docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md`, zapisując dla każdego ID rzeczywisty wynik, a nie tylko obecność UI.

Przejdź po kolei:
1. **Nawigacja / powłoka** — 6 zakładek, przejścia, powrót, język, dostępność, stan po wznowieniu.
2. **Start / Kampanie / sterowanie** — stan faktyczny, autoryzacja, rola, uprawnienia, START/STOP, błędy, powtórzenia, floating chat i pochodzenie statusu. W fazie początkowej bez realnego połączenia.
3. **Historia** — sesje, szczegóły, archiwum, odtwarzanie/STOP audio, filtr, usuwanie **tylko sztucznych fixture**, korelacja rezultatu.
4. **Agent AI** — Skills (presety i wersje), scenariusze (fabryczne i własne), Profile OUTGOING/INCOMING, 7 pól standardowych Sejfu, pola własne, szyfrowanie, walidacja, uprawnienia do ujawnienia, Narzędzia i trwałość ustawień. Bez prawdziwych prywatnych wartości.
5. **Laboratorium Skills** — quick/full, faktyczne działanie modelu, kolejne tury, przebieg na żywo, przewijanie, STOP/restart, metryki, błędy i powtarzalność.
6. **Modele / silniki** — katalog, status modelu, wybór, pobranie i VERIFY tylko jeśli potrzebne i świadomie uzasadnione, STT, model Direct/skrypt, TTS i głos, testy opóźnień oraz jakości; nie pobieraj dużych modeli bez potrzeby.
7. **Ustawienia** — ogólne, język, sekretarka INCOMING/manual/AI/takeover, diagnostyka i USB.
8. **Integracja** — realna współpraca STT→model/Skills→TTS, STOP, błędy i recovery; **potem** telefonia i wynik w rzeczywistym środowisku, jeśli istnieje konkretne zaakceptowane upoważnienie/cel.

Dla każdego testu: kryterium przed START, wejście, obserwacja/UI, stan magazynu lub efekt, STOP/recovery, wynik, dowód na konkretnym HEAD i status `PASS_UI_SYNTHETIC / PASS_OFFCALL_FUNCTIONAL / PASS_DEVICE_FUNCTIONAL / PASS_REAL_CALL / PARTIAL / FAIL / BLOCKED / NOT_RUN`. Nie traktuj screenshotu jako dowodu działania, testu offline jako prawdziwego połączenia ani deklaracji modelu jako zewnętrznego skutku. Zapisuj dokładne receipts i prywatne artefakty bez wysyłania ich do GitHub.

## Naprawy i zamknięcia
Jeśli znajdziesz błąd: zidentyfikuj przyczynę, dodaj wpis do `BLOCKERS.md` z nowym numerem, popraw minimalnym zakresem, odpal adekwatny test red/green, zweryfikuj na Mac/S22 i zaktualizuj status danego ID. Nie rozbudowuj kosmetycznie UX i nie wdrażaj „sztucznych blokerów”. Zachowaj STOP, autoryzację, kontrolę własnego Call, prywatność i Samsung PCM/PFD freeze. **F-080, F-122, F-123, F-124** pozostają otwarte, dopóki nie ma odrębnego dowodu naprawy.

**Tryb pracy:** rozpocznij natychmiast od preflight i `NAV-01`, następnie przechodź do kolejnych przypadków autonomicznie. Zadawaj pytanie tylko o materialne informacje niedostępne narzędziami — w szczególności dokładny cel/upoważnienie wymagane do rzeczywistego połączenia, kiedy testy do niego dojdą. Nie przerzucaj na użytkownika ręcznego odczytu ADB/logów. Na koniec każdego zamkniętego etapu: aktualizuj macierz, checkpoint, handoff, clean Git i krótko raportuj co PASS/FAIL/BLOCKED oraz co jest następną funkcją.
