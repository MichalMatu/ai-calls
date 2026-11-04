# AI Calls — handoff po fizycznym Gemma RAW + doc freeze (2026-11-04)

## Zacznij tutaj — jeden aktualny stan

**Repo:** `MichalMatu/ai-calls`; docelowy `main`, osobny `agent-control` tylko dla historycznej skrzynki. Remote GitHub branches potwierdzone: **tylko `main` i `agent-control`**. Przed każdą pracą pobierz **aktualny `main` HEAD**, nie zgaduj SHA ze starego handoffu. Podczas tego domknięcia nie zmieniano kodu aplikacji ani APK, jedynie aktywne dokumenty i `BLOCKERS`.

**Frozen techniczne:**
- Pierwsza działająca wersja per-call `Gemma Direct RAW` (bez przymusowego parsera JSON): `0d44b7d5a7e01daa118da10c2e04c458086dced9`. Po zmianie `TaskBrief`/skills i UI ostatni **potwierdzony na S22 APK** jest z kodu `268be1f8f13502c7202822438b501706e0fcb0e1`. `SCRIPT_GEMMA` nadal domyślny, `Gemma Direct RAW` tylko po jawnym wyborze w `CAMPAIGN TOOLS`.
- Mac `aicalls-20261104-live-chat-dock-host-qa-v1` DONE/0: full Android unit/build/lint, 248 Python, anti-friction PASS. S22 `aicalls-20261104-live-chat-dock-s22-offcall-install-v1` DONE/0: bezpieczny APK install + 4 natywne syntetyczne tury PASS, bez dzwonienia, grant/dialer/continuation zachowane. Nie powtarzaj bez zmiany kodu.
- **Dwie realne zakończone sesje Gemma RAW na S22 (2026-11-04 Europe/Warsaw):** 06:30:59 → **8 STT / 8 Gemma / 8 `WRITE_COMPLETE`**, 1 końcowy `RX_INTERRUPTED`, `owned_call_ended`; 06:35:27 → **7/7/7**, `operator_stopped`, 0 `RX_INTERRUPTED`. Obie bez `DIRECT_REJECTED`/błędu modelu. User potwierdził działanie dolnego czatu i długości dialogu. **To nie jest potwierdzenie CLIR ani dalekiego odsłuchu.** F-046 parser loop uznać za rozwiązany w trybie RAW.
- Końcowe zadania odczytu: `aicalls-20261104-closeout-readonly-confirm-stop-v1` DONE/0, **OFFHOOK → IDLE → IDLE**, retry false; `aicalls-20261104-closeout-latest-session-metadata-v1` DONE/0, 7/7/7 oraz 8/8/8; `aicalls-20261104-transcript-first-turn-overlap-private-audit-v1` DONE/0, prywatny eksport PASS. To nie jest stały monitoring. Przed kolejnym kontaktem z urządzeniem stan sprawdzić od nowa.

**Prywatny transkrypt ostatnich rozmów, tylko Mac (NIE GitHub):**
- `~/Documents/AI-Calls/private-exports/call-evidence-20261104T054132Z-1b65RKoF.tar.gz`
- `~/Documents/AI-Calls/private-exports/review-20261104T054132Z-1b65RKoF/index.html` — pełny tekst obydwu stron i osobne RX/TX WAV. Użytkownik poprosił o pobranie; archiwum jest lokalne, nie przesyłać go przez GitHub, wynik Mac task ani logcat.

## Najbliższa praca (nie powtarzaj zamkniętych testów)

**F-049 — zbyt szybka odpowiedź na IVR:** użytkownik w live teście usłyszał, że pierwsza wypowiedź Gemmy weszła zanim automat dokończył komunikat i musiał powtarzać. W 06:35 tura 1 STT = **3 słowa powitania**, RX 1,876–5,496 s; VAD uznał ~1,65 s ciszy za EOS; neutralny ACK wystartował już 5,800 s, pierwsza Gemma TX PCM 10,184 s. Nie twierdź, że dokładna akustyczna kolizja została dowiedziona przez WAV: RX zapisuje tylko okna STT. Potrzebne zamknięte testy scenariuszy pauz IVR i kontrola realnego końca wypowiedzi, **nie** sztywna długa pauza globalna. Ochrona STOP i szybkość reakcji pozostają.

**F-048 — brak niezbędnych danych:** Gemma zna typowany cel CLIR, ale **nie ma dostępu do zatwierdzonego `FactRequest` i `IdentityVault`**. W 06:35 tura 5 była prośba o weryfikację; faktów nie wpisywać do system prompt. W przyszłym sprincie typowany `FactRequest` → `FactDisclosurePolicy` (dokładny grant, zakres, odbiorca) → pojedynczy wymagany plaintext z `IdentityVault` → aplikacyjny release wypowiedzi. Brak faktu → zapytanie do właściciela. `GemmaDirectPromptSkill` to tekstowe instrukcje, nie wykonywalne tools ani zgoda na ujawnianie.

**F-047:** przy końcu połączenia przed VAD endpoint część RX może nie mieć STT; diagnostyczny `RX_INTERRUPTED` zaimplementowany i widoczny w 06:30, ale brak odzyskiwania ostatniego tekstu. **F-045:** pełna semantyczna kontrola RAW/niedozwolonych zobowiązań nadal OPEN. Sprawdzaj statusy wyłącznie w [BLOCKERS](../BLOCKERS.md), nie przenoś z historycznych checkpointów.

**Rzeczywisty stan produktu:** potwierdzony wieloturowy dialog i czat na S22; otwarte dane, turn-taking i semantyczne bezpieczeństwo oraz niezależny dowód skutku CLIR. Bez dzwonienia podczas dokumentacyjnego closeout. Kiedy będą zmiany w kodzie, najpierw RED → GREEN na Mac, potem exact S22 off-call, następnie wyłącznie autoryzowany test live uruchomiony przez właściciela. Nie zmieniaj zamrożonego Samsung/Shizuku audio bez osobnej przesłanki.

## Ścieżka wykonawcza

- Przeczytaj `AGENTS.md`, `README.md`, `BLOCKERS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/SECURITY_PRIVACY.md`, `docs/GENERIC_PHONE_TASK_AUTHORITY.md`; [checkpoint 06:35](checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md). Archiwum `docs/checkpoints/` zachowuj do audytu.
- Narzędzia: GitHub Connector + **Mac Local Agent** tylko; **bez Codex, Work, GitHub Actions (nie sprawdzaj ich statusu)**. Target `macbook-michal` / `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`; kolejka w `MichalMatu/local-agent:agent-control` pod `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/`, wynik w `task-results/`. Zawsze upewnij się, że to aktualny host/stan (nie stare status.json). Nigdy nie przekazuj tam prywatnych tekstów, WAV, numerów, sekretów. Lokalny checkout Mac nie został w tym sprincie zidentyfikowany w trzech sprawdzonych standardowych lokalizacjach; **nie twierdzić, że jego `git status` jest czysty bez osobnego sprawdzenia**.
- UI: Home `Rozpocznij autoryzowane zadanie AI` startuje **`SCRIPT_GEMMA`**, nie Direct. `CAMPAIGN TOOLS` → `Start authorized CLIR enable — Gemma Direct` do jawnej rozmowy RAW, `Skills i zadanie Gemma Direct` do instrukcji i podglądu. W trzech ekranach (Home/Campaign/dialer) na dole znajduje się prywatny dock z tekstem STT/Gemma/TX.
- Aktualne gałęzie remote: `main` i `agent-control`. Nie usuwać danych historycznych, nie przenosić agent-control do main, bez force-push i bez zmian w grancie.

## Kopiuj do następnego czatu

Kontynuuj `MichalMatu/ai-calls` z aktualnego `main` HEAD, czytając `AGENTS.md`, `BLOCKERS.md`, `docs/HANDOFF_NEXT_CHAT.md` i checkpoint `docs/checkpoints/2026-11-04-raw-live-dialogue-facts-turn-taking-freeze.md`. Tryb GitHub Connector + Mac Local Agent, bez Codex/Work/GitHub Actions. Działająca Gemma RAW na S22 prowadzi 8/8 i 7/7 tur, ostatni APK kod `268be1f8`; nie ruszać zamrożonego toru. Najpierw zbadaj F-049 (za wczesna odpowiedź po niepełnym powitaniu IVR) i F-048 (typowany, autoryzowany dostęp do faktów IdentityVault), później domknij F-047/F-045. Prywatny HTML i WAV są na Macu; nigdy nie publikuj ich w GitHub. Najpierw preflight i RED/GREEN, potem off-call S22. Realne połączenie wyłącznie przez aplikację i dokładny grant użytkownika. Nie powtarzaj zakończonych testów bez nowej przesłanki.
