# AI Calls — handoff do nowego czatu: PIERWSZA ROZMOWA NADZOROWANA

> **NAJNOWSZY STAN — 2026-11-02 po ponownym podłączeniu S22:** aktualny audytowany kod został **zbudowany i zainstalowany na S22**, przy `mCallState=0`, odbiór `APK_INSTALL=PASS`; źródło instalacji `8ed01fa`, bez zmian kodowych względem wcześniej zaliczonego testami `cce1fe99`. Odczyt po instalacji: `record_audio_granted=true`, Shizuku binder/support/permission `true`, `live_call_readiness=true`, pakiety Google STT `com.google.android.as` i `com.google.android.as.oss` aktywne dla user 0. Dokładny, zweryfikowany checkpoint: [S22 APK i gotowość](checkpoints/2026-11-02-s22-reconnected-audited-apk-installed.md), odpowiedzi Mac Host Control `aicalls-s22-reconnect-readonly-20261102-v1`, `aicalls-s22-audited-apk-install-20261102-v1`, `aicalls-s22-post-install-readiness-20261102-v1` i `aicalls-s22-google-speech-package-user0-20261102-v1`. **Wszystkie te bramki PASS; nie powtarzać.** Jedyny otwarty etap: *prawdziwa nadzorowana rozmowa* z osobą, która wyrazi zgodę, i dowód RX/STT/Gemma/TTS/TX/słyszalność/STOP. Rozmowę i rozłączenie wykonuje wyłącznie operator ręcznie. **Nie ma jeszcze dowodu, że wcześniejsze `AUDIO_ROUTE_NOT_READY` zostało rozwiązane w aktywnej rozmowie.**


> **Offline code audit, 2026-11-02 — NEWEST:** Physical S22 is deliberately disconnected. A scoped diagnostic extraction was committed on product-code `cce1fe99c7110f5c43a5f5406cdcae7b8238ed3e` (new immutable readiness snapshot, atomic private reports, thread-safe telephony evidence, less content-bearing data, new unit cases). **New code HOST_GREEN:** Mac task `aicalls-diagnostics-modular-audit-host-20261102-v1` FAILED on a compile syntax issue, corrected in that code commit; corrected `aicalls-diagnostics-modular-audit-host-20261102-v2` finished **DONE / exit 0**, `NEW_DIAGNOSTICS_TESTS=PASS`, `APP_COMPILE_LINT=PASS`, `SAFETY_AND_PRIVACY_STATIC=PASS`, clean exact source `cce1fe99`. Subsequent main changes were confirmed **docs-only**. Do not repeat these PASS tests absent a regression. **No new APK installed; no phone tests performed.** Full findings/edge matrix/next steps: [offline audit checkpoint](checkpoints/2026-11-02-offline-supervised-diagnostics-architecture-audit.md). **Priority: after S22 reconnect verify exact device + IDLE, install the accepted APK, then one manually initiated consenting hardware call; only diagnose actual live RX/TX/STOP findings.** Preserve all prior PASS without rerunning.


> **Aktualizacja 2026-11-02 po błędzie UI:** operator zgłosił `AUDIO_ROUTE_NOT_READY` mimo trwającej rozmowy. Bounded 3-sekundowy retry + prywatny sanityzowany raport wdrożono **w źródłach** na `main@5396cce`; targeted unit **PASS**, naprawione build+lint **PASS**. **Nowego APK nie zainstalowano na S22**: Mac USB niedostępny, sparowane Wireless ADB również niedostępne (`aicalls-s22-deploy-discover-wireless-20261102-v3`). Dotychczasowe testy PASS pozostają zamknięte. Następny krok: przywrócić ADB, sprawdzić exact S22 + `IDLE`, tylko zbudować i zainstalować nowe APK, potem jedna ręcznie rozpoczęta rozmowa ze świadomą zgodą i odczyt prywatnego `supervised-call-readiness-report.txt` oraz sanityzowanych liczników `chat-relay-live-call-report.txt`. **Nie omijać `MODE_IN_CALL`; tryb z błędnej chwili nie został jeszcze zmierzony.** Szczegóły: [checkpoint trybu audio](checkpoints/2026-11-02-supervised-in-call-audio-mode-retry.md).


**Stan zamknięcia:** 2026-11-02. Szczegóły i wyniki pełnego quality gate: [checkpoint przed pierwszym połączeniem](checkpoints/2026-11-02-before-first-call-checkpoint.md). **Nie używaj starych wpisów „latest” z dokumentów 2026-11-01 jako aktualnego stanu.**

## Cel następnego czatu

Wykonać **jedną** bezpieczną, świadomie nadzorowaną prawdziwą rozmowę testową na Samsung Galaxy S22+ i sprawdzić realne: RX PCM → polskie STT → ograniczona Gemma 4 lub skrypt → zatwierdzona wypowiedź → lokalne TTS → TX PCM, słyszalność po drugiej stronie oraz STOP.

**Tylko użytkownik inicjuje i kończy zwykłe połączenie w Google Phone** z wyrażającą zgodę osobą. AI Calls może jedynie dołączyć do istniejącej rozmowy po wyraźnym zatwierdzeniu w aplikacji. Przy teście bez osoby lub ważnej zgody skończ na bramce gotowości, bez wybierania numeru.

## Weryfikacje już zakończone — nie powtarzaj bez regresji

- STT `pl-PL`, TTS → PCM → STT, pełny deterministyczny przepływ STT → tekst → TTS: **PASS na fizycznym S22**.
- Rzeczywista Gemma 4 i polityka zatwierdzonych wypowiedzi: **PASS na fizycznym S22**, model READY.
- Shizuku Binder, RECORD_AUDIO i dostępność telefonu: **PASS**.
- Media poza rozmową odmawiają startu i sprzątają zasoby: **PASS**.
- UI podczas `IDLE` odmawia dołączenia AI, STOP jest wyłączony: **PASS**.
- Google STT naprawione po włączeniu `com.google.android.as` i `com.google.android.as.oss`; **nie wyłączaj ich ponownie**.
- Poprawka listenera, błędów i timeoutu STT: scalona do `main`, zainstalowana na S22.

Fizyczne dowody: [STT recovery](checkpoints/2026-11-02-ai-calls-s22-polish-stt-recovery.md), [supervised no-call acceptance](checkpoints/2026-11-02-ai-calls-autonomous-nocall-supervised-acceptance.md). Pełny quality gate i aktualne ograniczenia są w **checkpointcie przed pierwszą rozmową**.

## Tryb wykonania

- **GitHub Connector + Mac Local Agent**, bez Codex, Work i GitHub Actions. `MichalMatu/ai-calls` na aktualnym `main`; zweryfikuj HEAD, nie zakładaj SHA z dokumentu.
- Do host/ADB użyj wyłącznie `MichalMatu/local-agent:agent-control`, `target_host_id=macbook-michal`, `target_installation_id=cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Reguły i ścieżki w [Local Agent Host Flow](LOCAL_AGENT_HOST_FLOW_2026-11-01.md).
- Dokładny telefon: Galaxy S22+ `SM-S906B` / serial `RFCT70L7E8J`, ADB USB, Android 16 / One UI 8; dialer `com.google.android.dialer`. Zweryfikuj tożsamość i `mCallState=0` przed testem.
- Minimalizuj udział użytkownika: bez ręcznych komend, kopiowania logów i ponownego przepytywania o potwierdzone fakty. Wszelkie dopuszczalne preflighty i analizę liczników wykonaj sam.
- **Nie wykonuj samodzielnego dial/hangup**, `*100`, zmian CLIR/SIM/dialera ani globalnego zakończenia połączenia. Osobna funkcja autonomicznego dzwonienia nadal wymaga exact-call owner z selektywnym hangup. Przestrzegaj [AGENTS.md](../AGENTS.md) i [SECURITY_PRIVACY](SECURITY_PRIVACY.md).

## Jedyny nierozstrzygnięty test

1. Bez rozmowy odczytaj aktualny ADB, Shizuku, model, uprawnienia, `IDLE` i gotowość; bez powtarzania zakończonych testów STT/TTS/Gemma.
2. Użytkownik wybiera osobę zgadzającą się na test i **sam dzwoni**.
3. W AI Calls → **Pierwsze połączenie — test nadzorowany** → świadome potwierdzenie dołączenia. Preferowany Gemma 4 (READY), skrypt jako bezpieczny fallback; do trzech wypowiedzi.
4. Mac Local Agent monitoruje wyłącznie **sanityzowane** `chat-relay-live-call-report.txt`: `telephony_rx_pcm_bytes`, `turn_*_stt_chars`, `supervised_gemma_replies` / `supervised_script_fallback_replies`, `telephony_tx_pcm_bytes`, `failure_reason` i wynik. Nie publikuj numerów, surowego PCM ani transkryptów.
5. Druga strona potwierdza, czy słyszy AI. Użytkownik naciska **STOP AI**, weryfikujesz cofnięcie TX i pozostawienie rozmowy pod kontrolą Google Phone; użytkownik sam rozłącza. Zapisz dowód i koniec w `IDLE`.
6. Jeśli wystąpi błąd: napraw minimalny konkretny problem i tylko niezbędny test regresji. Nie rozkręcaj ponownie zakończonego debugowania.

## Gotowy pierwszy komunikat w nowym czacie

> Wznów AI Calls według `docs/HANDOFF_NEXT_CHAT.md` i `docs/checkpoints/2026-11-02-before-first-call-checkpoint.md`. Pracuj przez GitHub Connector + Mac Local Agent, bez Codex/Work/GitHub Actions. Najpierw zweryfikuj aktualny `main`, wynik quality gate i gotowość S22; nie powtarzaj testów PASS. Samodzielnie przygotuj pierwszy nadzorowany test rzeczywistej rozmowy i ogranicz mój udział do ręcznego połączenia z osobą, która wyraziła zgodę, zatwierdzenia AI i oceny słyszalności/STOP. Bez automatycznego wybierania numeru lub rozłączania. Zgłoś wyłącznie rzeczywiste blokery.
