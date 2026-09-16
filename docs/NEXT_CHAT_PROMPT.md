# Next chat prompt — Phase 2 continuation

Paste the block below into a fresh ChatGPT window.

```text
Kontynuuj projekt `MichalMatu/android-ai-call-bridge` na branchu `work/phase1-live-call-probes`.

Najpierw przeczytaj w całości i potraktuj jako źródło prawdy:
- `docs/HANDOFF_NEXT_CHAT.md`
- `AGENTS.md`
- `docs/PHASE2_DEEP_AUDIT_2026-09-16.md`
- `docs/superpowers/plans/2026-09-16-phase2-deep-audit-fixes.md`

Potem przeczytaj potrzebne dokumenty architektury/evidence wskazane w handoffie. Nie zaczynaj od Realtime AI i nie rób ponownie całego audytu od zera.

Aktualny etap:
- M1 FIXED/GREEN — Shizuku probe bodies działają poza main thread.
- M4 FIXED/GREEN — lokalne RX/TX probe media mają deterministycznego ownera cleanup.
- M2 FIXED/GREEN — privileged automation usunięta z exported MainActivity; `DiagnosticProbeActivity` jest chroniony `android.permission.DUMP`; fizyczny off-call shell path na S22 przeszedł `offcall_parity_ok=true`.
- M3 OPEN — to jest następny task.

Następny krok wykonaj minimalnie: popraw `PrivilegedCallContexts.create()` tak, aby nie robił `Looper.prepare()` ani drugiego `ActivityThread.systemMain()` na Binder thread, tylko użył istniejącego `ActivityThread.currentActivityThread()` utworzonego przez Shizuku. Zachowaj resztę konstrukcji system/shell Context bez zmian. Jeśli `currentActivityThread()` jest null, fail fast z czytelnym `IllegalStateException`.

Po M3:
1. full host tests/build,
2. off-call Shizuku prepare/abort przez protected `DiagnosticProbeActivity`,
3. kilka powtórzeń off-call jeśli tanie,
4. jeden wąski silent live Shizuku parity regression na S22,
5. dopiero potem 30 s endurance i pozostałe Milestone D gates z handoffu.

Nie zmieniaj bez fizycznego powodu proven Samsung invariants: RX ordering, system/shell attribution, `USAGE_CALL_ASSISTANT`, mono PCM16LE -> stereo dopiero na Samsung TX boundary, PFD ownership, jeden RX+TX fail-safe lifetime, brak per-frame Binder.

Dla każdego live cellular testu telefon ma pozostać lokalnie bezgłośny: voice-call muted, streamVolume=0, earpiece, speakerphone off; mute ponownie po ACTIVE. Używaj bezpośredniego USB-C <-> USB-C do MacBooka.

LOCAL CHAT BRIDGE / LOCAL AGENT — testujemy teraz nowy EVENT-DRIVEN flow.
Oczekiwane hard binding:
- repository: `MichalMatu/android-ai-call-bridge`
- repo id: `android-ai-call-bridge`
- agent binding: `c25f88c0-4682-414c-8062-c47fa4034cb0`

Jeżeli bridge injectuje inny repo/binding, użyj PAUSE zamiast zgadywać lub przełączać repo. Każdy Local Agent task JSON dla tego projektu ma mieć dokładnie:
`"agent_binding": "c25f88c0-4682-414c-8062-c47fa4034cb0"`.

Przed edycją brancha sprawdź aktywne Local Agent taski. ChatGPT planuje i podejmuje decyzje o kodzie; Local Agent wykonuje wyłącznie deterministyczne komendy Mac/build/ADB/device. Nigdy nie uruchamiaj lokalnego Codex przez Local Agent.

Najważniejsze dla nowego flow: po zakolejkowaniu jednego konkretnego taska NIE polluj go co 30 s. Użyj dokładnie:
`[LAB:WAIT_TASK=<task-id>]`
To ma być event-driven wake + alarm fallback. `task_result_ready` jest tylko sygnałem pobudki — po nim przeczytaj dokładny terminalny result JSON i dopiero wtedy uznaj GREEN/FAIL. `NEXT` używaj tylko do realnych kontroli czasowych/zewnętrznych, nie jako polling taska. Jeśli twarde live evidence pokazuje, że aktywny task nie może już osiągnąć celu, anuluj ten dokładny task zamiast czekać na timeout.

Chcę od razu przetestować ten event-driven workflow w nowym oknie, więc przy pierwszym zadaniu wymagającym Local Agenta użyj tego mechanizmu.

Zacznij od potwierdzenia aktualnego HEAD i przeczytania handoffu, a potem przejdź bez zbędnego rozwlekania do M3.
```
