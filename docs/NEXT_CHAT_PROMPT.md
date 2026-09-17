# Next chat prompt — Phase 2 / Milestone D continuation

Paste the block below into a fresh ChatGPT window.

```text
Kontynuuj projekt `MichalMatu/android-ai-call-bridge` na branchu `work/phase1-live-call-probes`.

Najpierw przeczytaj i potraktuj jako źródło prawdy:
- `docs/HANDOFF_NEXT_CHAT.md`
- `AGENTS.md`
- `docs/ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`

Nie zaczynaj od Realtime AI i nie rób ponownie audytu od zera.

Aktualny stan:
- Phase 2B local RX+TX: PROVEN_S22 / frozen na `milestone/phase2b-proven-s22-20260916`.
- Phase 2C Shizuku UserService parity: PROVEN_S22 / frozen na `milestone/phase2c-shizuku-live-proven-20260916`, commit `9c136fc05c5b33f383d72b0b7080ad5b9a754bb4`.
- M1/M2/M3/M4 z deep audit są zakończone. M3 commit: `60cbe81af2e02ba2e4100691c8afb76332735548`.
- Po M3 przeszły: full host validation, off-call parity, silent live parity oraz 30 s bidirectional endurance.
- Fizycznie udowodniono też śmierć UserService/helpera podczas aktywnej sesji: helper znika, klient dostaje EPIPE, normalna aplikacja i Shizuku server przeżywają.
- Milestone D jest aktywny.

Najbliższy nierozstrzygnięty gate to normal-app-death podczas aktywnego media. Poprzedni test nie jest dowodem błędu produktu: po `am force-stop` host ADB wszedł w `waiting for device`, przez co hostowy pomiar ~105 s jest nieważny. Po odzyskaniu transportu zarówno app, jak i UserService były już martwe.

Do powtórki app-death użyj nowego harnessu:
- `scripts/s22_app_death_gate.py`
- `scripts/test_s22_app_death_gate.py`

Harness wykonuje timing-krytyczny observer po stronie telefonu, używa `/proc/uptime`, obserwuje app/helper process death i `USAGE_CALL_ASSISTANT state:stopped`, zapisuje wynik atomowo i nie zależy od ciągłego hostowego ADB. Nie używa `nohup`, bo ten S22+ nie ma `toybox nohup` (`exit 125`). Host validation harnessu: 11/11 focused tests, 30/30 wszystkich Python tests, full Gradle build/test GREEN.

Po app-death gate pozostałe Milestone D gates:
1. zakończyć cellular call podczas aktywnego bridge i udowodnić pełny cleanup;
2. zamknąć jeden transferred RX/TX PFD i udowodnić sibling abort / whole-generation stop;
3. 10–20 start/abort cycles z porównaniem FD/thread/process/resource counts;
4. finalny 10-min bidirectional endurance z telemetry/resource counts;
5. finalny audit/regression;
6. freeze Milestone D;
7. dopiero potem Realtime AI.

Nie zmieniaj bez fizycznego powodu proven Samsung invariants: direct-shell RX prepare-before-context ordering, system RX attribution, `com.android.shell` TX attribution, `USAGE_CALL_ASSISTANT`, mono PCM16LE -> stereo dopiero na Samsung TX boundary, PFD ownership, jeden wspólny RX+TX fail-safe lifetime, brak per-frame Binder.

Dla każdego live cellular testu telefon ma pozostać lokalnie bezgłośny: `STREAM_VOICE_CALL Muted:true`, `streamVolume:0`, `Devices: earpiece(1)`, speakerphone off; sprawdź mute przed dial i ponownie po ACTIVE. Preferuj bezpośredni USB-C <-> USB-C do MacBooka.

Local Agent binding dla tego repo:
- repository: `MichalMatu/android-ai-call-bridge`
- repo id: `android-ai-call-bridge`
- agent binding: `c25f88c0-4682-414c-8062-c47fa4034cb0`

Każdy Local Agent task musi mieć dokładnie ten `agent_binding` i jawne `resources`. ChatGPT planuje; Local Agent wykonuje deterministyczne Mac/Gradle/ADB/device commands. Nie uruchamiaj lokalnego Codex przez Local Agent.

Wycofano eksperymentalny event-driven Local Chat Bridge flow. Nie używaj `LAB:WAIT_TASK` ani `task_result_ready` jako mechanizmu pracy. Terminalny `.agent/results/<task-id>.json` jest autorytatywny. Dla zdrowych długich tasków nie polluj co 30 s; sprawdzaj ręcznie w rozsądnym odstępie lub gdy użytkownik poprosi.

Zacznij od potwierdzenia HEAD i stanu Local Agenta. Jeśli telefon nie jest podłączony, wykonuj tylko host-only pracę i nie deklaruj device gate jako PASS.
```
