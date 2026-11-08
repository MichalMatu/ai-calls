# AI Calls — kompletny checkpoint przed funkcjonalnym odbiorem (2026-11-08)

**Repo:** `MichalMatu/ai-calls`; **baseline kodu:** `main@edf63c1ea9dc425c91d2a1571a90038631852ffd` (HEAD zweryfikowany 2026-11-08, po UX-11; ewentualny kolejny commit porządkuje wyłącznie dokumentację). **Gałąź:** `main`. **Wykonanie:** GitHub Connector + Mac Local Agent, Mac `macbook-michal`, urządzenie Samsung Galaxy S22 `SM-S906B`. Nie używać Codex, Work ani GitHub Actions, również do odczytu statusu. Bez nowej gałęzi i force-push.

## Produkt i stan architektury
Modułowe laboratorium AI Calls z sześcioma zakładkami: **Start, Historia, Agent AI, Laboratorium, Modele, Ustawienia**. Komponenty: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. `AndroidConversationRuntime` jest wspólnym punktem dla INCOMING/OUTGOING; STT, model/strategia, Skills, TTS, konfiguracja próby i warstwa UI są rozdzielane. Wymagane w przyszłości: ogólne zadania telefoniczne, wspólny bezpieczny runtime, manual/AI takeover, pełna sesja audio/transkrypt, właściwy wynik wykonanej operacji. **Nie wszystkie funkcje są dziś gotowe produkcyjnie.**

## Zamknięte prace
- **UX-01–11** — finalny etap wizualny zamknięty; nie otwierać następnego kosmetycznego sprintu bez funkcjonalnego błędu P0/P1.
- **UX-10:** pływająca ikona bez tekstu pojawia się tylko przy aktywnym feedzie z wiadomością; panel około 84% obszaru; wspólny dla Start i Kampanii. Sejf: 7 typowanych pól oraz oddzielne pola custom lokalnie szyfrowane; Lab: uproszczone sterowanie i stale dostępny STOP.
- **UX-11:** Lab z nagłówkiem „Przebieg rozmowy” i placeholderem, zwarte wiersze Sejfu z dodaniem własnego pola u góry, syntetyczny test rozwijania i zamykania floating chat.
- **Zamrożone granice:** runtime/Telecom/PCM/Shizuku i autoryzacja zadania nie były modyfikowane w UX-10/11; istniejące dane v1 sejfu nie są migrowane ani automatycznie ujawniane. Zapisanie pola custom nie daje AI prawa ujawnienia.

## Mac i S22 — wyłącznie exact receipts

| Obszar | Receipt / HEAD | Rezultat | Nie dowodzi |
|---|---|---|---|
| UX-10 Mac | `aicalls-ux10-floating-vault-lab-mac-20261108-v5` / `73d9691` | 353/353 Python, pełny host gate PASS | nowego UX11 |
| UX-10 S22 | `aicalls-ux10-s22-guarded-visuals-20261108-v3` | 26/26 native UI, 17 screenshotów, APK `-r` | real-call |
| UX-11 Mac | `aicalls-ux11-final-mac-gate-20261108-v2` / `949a854` | 356/356 Python, AndroidTest/APK/JVM/friction PASS | testu rozmów z człowiekiem |
| UX-11 AndroidTest fix | `aicalls-ux11-synthetic-layout-mac-20261108-v1` / `3029034` | targeted AndroidTest compile PASS | pełnego retestu 356 na dokumentacyjnym HEAD |
| UX-11 S22 widoki | `aicalls-ux11-final-s22-guarded-synthetic-20261108-v2` | 28/28 UI PASS; *pierwsze* 2 synthetic FAIL w pomiarze geometrii przed layout — nie ukrywać RED | właściwego layout po aktualizacji testu |
| UX-11 S22 floating | `aicalls-ux11-s22-synthetic-layout-and-private-archive-20261108-v1` / `3029034` | 2/2 PASS po poprawce pomiaru; 18/18 screenshotów, S22 IDLE przed i po | fizycznego Call |
| Funkcja: model offline i STOP | `aicalls-functional-real-offline-two-turn-stop-s22-20261108-v1` | 1 instrumentacja PASS: lokalne 2 tury, STOP/restart; 4 prywatne screenshoty | prawdziwego RX/TX |
| Funkcja: polski audio offline | `aicalls-functional-polish-tts-parakeet-offcall-s22-20261108-v1` | 1 instrumentacja PASS: TTS→PCM→Parakeet STT, leksykalny overlap | pełnej semantyki/latencji/telefonii |

**Ostatnie znane środowisko:** finalne zadania Mac/S22 wykonane z kodem `main@3029034`; urządzenie sprawdzone IDLE przed i po, brak niekontrolowanych połączeń, zachowana prywatna instalacja danych. Bieżący stan Mac/urządzenia w nowym czacie **zawsze ustalić na świeżo**, bez zakładania, że nadal jest IDLE.

**Prywatne artefakty wyłącznie na Macu:**
- `~/Library/Application Support/AI-Calls/UI-audit/2026-11-08-ux11/AI-Calls-S22-UX11-top17-plus-chat-private.zip` — 17 górnych ujęć ekranów + 1 aktywny syntetyczny czat.
- `~/Library/Application Support/AI-Calls/functional/2026-11-08-lab-offline/AI-Calls-S22-Functional-Offline-Lab-private.zip` — 4 ujęcia dwóch prawdziwych tur offline i STOP.
Nie zamieszczać tych plików ani numerów, PESEL, realnych transkryptów i WAV w repo.

## Znane ograniczenia, które NIE są PASS
- **Rzeczywisty Call:** niezweryfikowane na obecnym HEAD: fizyczne RX/TX, TTS słyszalny po obu stronach, pełny dialog, DTMF/IVR, poprawny end/transfer, stan Call przed/po i rzeczywisty efekt (np. CLIR). Lokalna dwuturowa inferencja i test syntetyczny nie dowodzą tego toru.
- **Sejf:** ekran i szyfrowana implementacja zostały zweryfikowane źródłowo/widokowo, ale pełna macierz funkcjonalna standard/custom CRUD, restarty, wymiana kluczy, brak wycieku i scope disclosure **NOT_RUN**. Nie testować na danych właściciela.
- **Narzędzia/scenariusze:** wiele presetów ma status szkicu `DRAFT_ONLY`; nie uruchamiać ich jako realnych operacji na kontach.
- **Historia/recorder:** `F-080` pełna ciągła sesja/player, `F-122` stop aktywnego rejestratora, `F-123` korelacja Call/attemptId/wynik, `F-124` selektor audio — pozostają OPEN w `BLOCKERS.md`.
- **Jakość mowy:** test TTS→STT z przykładową polską frazą nie jest oceną WER/CER, p95/median, jakości na głośniku, pogłosu ani rzeczywistej linii telefonicznej.
- **UI:** 18 screenshotów to głównie początkowe części ekranów, a nie ręczna akceptacja wszystkich zwojów, TalkBack, pól formularza i błędnych ścieżek.

## Dokładny następny zakres
**Nie pisać kolejnego sprintu kosmetycznego.** Przeprowadzać funkcjonalną akceptację **ekran po ekranie, funkcja po funkcji** według [macierzy odbioru](../FUNCTIONAL_ACCEPTANCE_MATRIX.md), aktualizując status pojedynczej funkcji (nie całego ekranu po jednym UI PASS). Start: preflight → nawigacja i Start w trybie bezpiecznym → Historia → Agent AI (Skills, scenariusze, Profile, Sejf, Narzędzia) → Lab → Modele/silniki → Ustawienia/Incoming/USB → integracja → autoryzowany real CALL. Każdy test: opis wejścia, wynik, dokładny artefakt/receipt, izolowane dane fixture i ewentualny defekt z `BLOCKERS.md`. Reużyć istniejące testy i wcześniejsze PASS, zamiast powtarzać kosztowne badania bez nowej przesłanki. Przed wykonaniem real Call ustalić konkretny dozwolony cel/numer i aktualną autoryzację, a następnie wykonać preflight rzeczywistego własnego Call; nic nie uruchamiać z samych przykładowych szablonów.

## Źródła prawdy i następny czat
- Sterowanie pracą: [AGENTS](../../AGENTS.md), [SECURITY_PRIVACY](../SECURITY_PRIVACY.md), [AUTONOMOUS_OPERATION_MODE](../AUTONOMOUS_OPERATION_MODE.md).
- Produkt: [ARCHITECTURE](../ARCHITECTURE.md), [TEST_LAB_SCOPE](../TEST_LAB_SCOPE.md), [PRODUCT_ASSEMBLY_PLAN](../PRODUCT_ASSEMBLY_PLAN.md).
- Stan/priorytety: [HANDOFF_NEXT_CHAT](../HANDOFF_NEXT_CHAT.md), [ROADMAP](../ROADMAP.md), [BLOCKERS](../../BLOCKERS.md).
- Dalsze polecenie: [NEXT_CHAT_PROMPT](../NEXT_CHAT_PROMPT.md).
- Poprzednie dowody: [UX11](2026-11-08-ux11-final-polish-functional-handoff.md), [funkcjonalne offline](2026-11-08-functional-real-offline-acceptance.md).
