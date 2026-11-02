# AI Calls — handoff do nowego czatu: PIERWSZA ROZMOWA NADZOROWANA

> **NAJNOWSZY SPRINT — PRZYCISK TESTU AI, 2026-11-02:** na `main` jest zmiana produktu `c84e796` w `MainActivity.kt`: jeden przycisk **„Jeden przycisk: uruchom AI w trwającej rozmowie”**, który bez dodatkowego dialogu aplikacji prosi o brakujące uprawnienia Androida, zapisuje `supervised-call-readiness-report.txt` i uruchamia istniejący `AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO` tylko przy aktywnej rozmowie i `MODE_IN_CALL`. Nowy test przycisku w czasie bez rozmowy `AndroidOneTapSupervisedButtonTest.kt` w commit `1b2fce9`. Nie wybiera numeru, nie rozłącza, nie usuwa bramki Samsung. Proponowany kod serwisu autonomicznego dzwonienia/rozłączania został **odrzucony przez external tool safety check** i NIE trafił do repo; nie próbować go przepchnąć innym kanałem. Mac Local Agent `aicalls-one-tap-supervised-s22-sprint-20261102-v1`: **DONE/exit 0** — Android build+lint PASS, główna aplikacja i APK testów zainstalowane na dokładnym S22 w IDLE, fizyczny test przycisku PASS i prywatny raport gotowości PASS (`MODE_NORMAL`, `telecom_in_call=false`). Nie powtarzać zamkniętej instrumentacji bez zmiany tego flow. [Checkpoint przycisku](checkpoints/2026-11-02-one-tap-supervised-single-button-sprint.md). Pełny test realnej rozmowy, naprawa `MODE_IN_COMMUNICATION` i selektywne kontrolowanie połączenia nadal OTWARTE.


> **NOWY CHECKPOINT GLOBALNEGO ROZŁĄCZANIA — 2026-11-02:** operator zaakceptował globalne rozłączenie na dedykowanym S22. `scripts/aicall_tools/device/s22_call_control.py` ma **jawne `hangup --global`**, jedno `KEYCODE_ENDCALL`, walidację numeru seryjnego/modelu, stanu `OFFHOOK` i potwierdzenie `IDLE`. Mac Local Agent `aicalls-s22-global-hangup-code-gate-20261102-v1`: **DONE/0**, ukierunkowane testy PASS. Telefon w preflight `aicalls-s22-global-endcall-readiness-20261102-v1`: dokładny S22, `CALL_STATE=0`. **Nie było fizycznego rozłączenia ani nowej rozmowy.** Eksperymentalny `s22_single_service_smoke.py` jest w źródłach, lecz **niezweryfikowany**: zadanie walidacji dzwonienia/cleanup **zablokowane przez zewnętrzny safety check**, bez zlecenia i bez receipt; **NIE uruchamiać go innym narzędziem w celu ominięcia blokady**. Produkcyjny `CampaignOwnedCallController` nadal nie istnieje. [Pełen checkpoint](checkpoints/2026-11-02-s22-explicit-global-endcall-lab-boundary.md). Wcześniejsze PASS pozostają zamknięte.


> **NAJNOWSZY SPRINT 2026-11-02 — DIAGNOSTYKA BEZ KLIKANIA NA S22 ZAINSTALOWANA:** produkcyjny kod `8f976a17c57022f23d655a0f83f6499276dd4d2a` ma zaliczone build+lint, nowe testy Pythona, test kontraktu `android.permission.DUMP` i brak działań na rozmowach (`aicalls-no-touch-diagnostics-complete-gate-20261102-v2`: **DONE/0**). Mac Local Agent **naprawdę zainstalował** nowe APK na dokładnym `SM-S906B / RFCT70L7E8J` przy `IDLE` (`aicalls-s22-noclick-diagnostics-deploy-20261102-v1`: **DONE/0**). Automatyczny pomiar app-context: `telecom_in_call=false`, `telecom_managed_call=false`, `audio_mode=MODE_NORMAL`, `failure_reason=no_active_cellular_call`, uprawnienia mikrofon/telefon i Shizuku **true**, `report_complete=true`; to tylko dowód **off-call**, nie naprawa widocznego na zrzucie ekranu `MODE_IN_COMMUNICATION` w prawdziwej rozmowie. Narzędzie: `PYTHONPATH=scripts python3 -m aicall_tools.device.s22_supervised_readiness --serial RFCT70L7E8J` w Mac Local Agencie. **NIE POWTARZAJ PASS.** Do realnego automatycznego Orange dial nadal brakuje selektywnego kontrolera `CampaignOwnedCallSession`; nie usuwaj fail-closed ani nie używaj globalnego `TelecomManager.endCall()`. Dokładny [checkpoint ostatniego sprintu](checkpoints/2026-11-02-s22-no-touch-call-readiness-installed.md).


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
