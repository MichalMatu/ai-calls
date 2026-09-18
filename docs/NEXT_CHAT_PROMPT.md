# Prompt for the next ChatGPT conversation

Copy the block below into a new chat. If the new chat does not yet have a Local Agent binding, let `[LAB:ADD=android-ai-call-bridge]` bootstrap it first and use the fresh `LA_AGENT` returned there. Never reuse the old chat's binding if the new bootstrap returns another value.

---

[LAB:ADD=android-ai-call-bridge]

Kontynuujemy rozwój wyłącznie repozytorium:

https://github.com/MichalMatu/android-ai-call-bridge

Repository id Local Agenta: `android-ai-call-bridge`.

Główny branch roboczy:
`work/phase1-live-call-probes`

Control branch Local Agenta:
`agent-control`

Jeżeli bootstrap tego nowego chatu zwróci `LA_AGENT`, traktuj go jako immutable binding dla tego chatu. Każdy tworzony `.agent/tasks/*.json` musi zawierać dokładnie ten świeży `agent_binding`. Nigdy nie kopiuj bindingu ze starego chatu, jeśli nowy bootstrap zwróci inny.

Pracuj tylko w `MichalMatu/android-ai-call-bridge`. Nie zgaduj i nie używaj innych repozytoriów pod tym bindingiem.

## Najpierw przeczytaj

Przed jakąkolwiek zmianą przeczytaj w tej kolejności:

1. `AGENTS.md`
2. `docs/HANDOFF_NEXT_CHAT.md`
3. `docs/PHASE3_REALTIME_STATUS_2026-09-18.md`
4. `docs/PHASE2D_FREEZE_2026-09-18.md`
5. `docs/ROADMAP.md`
6. `docs/ARCHITECTURE.md`
7. `docs/SECURITY_PRIVACY.md`
8. w razie potrzeby `docs/superpowers/plans/2026-09-18-telephone-agent-v1.md`

Następnie sprawdź:

- aktualny HEAD `work/phase1-live-call-probes`;
- `.agent/status/daemon.json` na `agent-control`;
- czy nie ma aktywnego Local Agent taska piszącego/operującego na tym samym branchu.

Nie rób recapu niezmienionego stanu — od razu kontynuuj od dokładnych dowodów z repo.

## Stan, od którego kontynuujemy

Ostatni behavior HEAD przed dokumentacyjnym handoffem:

`94594aa8f6e321395d5648dea4dffb243db911fd` — `feat: require function response identity`

Po nim mogą być wyłącznie commity dokumentacyjne z handoffu. Zweryfikuj aktualny HEAD zamiast zakładać SHA.

Milestone D / lokalny Samsung media bridge jest zamrożony i PROVEN_S22. Nie powtarzaj pełnej macierzy Phase 2D bez konkretnej regresji.

Frozen checkpoint:
`milestone/phase2d-failsafe-proven-s22-20260918`
`59b0505537a53306acdab6a2a66ca6eed2b3f1c0`

Zachowaj invariants: RX ordering/attribution, TX `com.android.shell`, CALL_ASSISTANT/TELEPHONY_TX, mono PCM16 wewnątrz, stereo tylko na Samsung TX boundary, PFD AutoClose, whole-generation cleanup, lokalny TAKE OVER, heartbeat i `CallModeWatchdog`.

## Co jest już zrobione w Phase 3

Mamy już produkcyjne:

- `CallMediaSessionCoordinator` + `ShizukuCallMediaSessionBackend`;
- `CallRealtimeAgentRuntime`, controller, orchestrator i media session;
- Realtime WebSocket + OkHttp connector + generation safety;
- PCM 16 kHz telephony <-> 24 kHz Realtime;
- bounded audio pump + barge-in;
- `CallTask` / hard constraints / soft preferences / `authorizedFacts` / workflow / outcomes;
- deterministic `CallConfirmationPolicy` i `NEEDS_USER_DECISION`;
- typed Realtime function calling;
- `evaluate_proposal`;
- one-shot `CallCommitmentGate`;
- forced `commit_proposal` po zgodzie i `NoTools` po commicie;
- hostowy credential broker + Android provider/request factory;
- ADB-only off-call Realtime network smoke plumbing;
- secure smoke runner staging secretów przez stdin;
- typed output audio/transcript/response lifecycle;
- `CallRealtimeOutputResponseBuffer` i pełno-response buffering przed telephony TX.

Mechanika response-lifecycle speech gate była pełne GREEN na:
`7d7bd65738568ee5a29ff6d2674b157584264e54`.

Realny OpenAI S22 network smoke NIE został jeszcze wykonany.

## Dwa aktualne RED-y — od nich zacznij

### RED 1: production output approval policy

Istnieje `CallRealtimeAgentOutputApprovalPolicyTest`, ale production `CallRealtimeAgentOutputApprovalPolicy` oraz `CallRealtimeAgentSessionSpec.outputApprovalPolicy` nie są jeszcze zaimplementowane.

Najpierw zaimplementuj ten kontrakt TDD:

- RELEASE zwykłej mowy tylko w bezpiecznym `ACTIVE_NEGOTIATION`, gdy nie ma pending commitment permit;
- DROP gdy permit commitmentu jest pending;
- DROP w `NEEDS_USER_DECISION`;
- DROP poza aktywną negocjacją;
- użyj dokładnie tego samego `CallCommitmentGate`, który obsługuje `evaluate_proposal` / `commit_proposal`.

Następnie przepuść `outputApprovalPolicy` przez production session/media wiring aż do `CallRealtimeAudioPump`, aby realna sesja rzeczywiście tworzyła `CallRealtimeOutputResponseBuffer`. Nie kończ na samym skompilowaniu testu ze `SessionSpec` jeśli production pump nadal dostaje `null`.

Dowód RED: `.agent/results/realtime-agent-output-policy-red-20260918-2500.json`.

### RED 2: stale fixture po `response_id` hardening

Focused `RealtimeFunctionToolProtocolTest` przechodzi, ale pełny `:realtime-client:testDebugUnitTest` ma jeden failure:

`RealtimeWebSocketFunctionTransportTest.incomingFunctionCallReachesTypedListener`

Fixture emituje `response.output_item.done` bez `response_id`, podczas gdy aktualny parser celowo wymaga response identity dla function call.

Nie osłabiaj parsera tylko po to, żeby stary test przeszedł. Zaktualizuj fixture do aktualnego GA event shape z `response_id` i dodaj asercję, że `RealtimeFunctionCall.responseId` zachowuje tę wartość.

Uwaga: task `.agent/results/realtime-function-response-id-green-20260918-2525.json` ma mylącą nazwę `green`, ale faktycznie `status=failed` właśnie przez ten jeden stale fixture. Traktuj to jako aktualny RED pełnego modułu.

## Gate po naprawach

Po obu poprawkach uruchom Local Agent read-only gate na exact HEAD:

- focused output-policy/speech-gate/function-response-id tests;
- `:realtime-client:testDebugUnitTest`;
- `:app:testDebugUnitTest`;
- `:app:assembleDebug`;
- `python3 -m unittest discover -s scripts -p 'test_*.py'`;
- scan, że produkcyjny Android/Realtime kod nie zawiera `OPENAI_API_KEY`, `sk-...`, `apiKey`, `api_key`;
- `git diff --check`;
- clean working tree.

Nie wykonuj połączenia komórkowego tylko po to, żeby zamknąć te hostowe RED-y.

## Następny fizyczny etap po pełnym host GREEN

Pierwszy kolejny device gate to PRAWDZIWY OpenAI **off-call network smoke**, bez dialowania.

Host musi mieć:

- `OPENAI_API_KEY` wyłącznie w env brokera;
- działający authenticated HTTPS endpoint/tunnel do loopback brokera;
- osobny silny broker bearer dla Androida.

Użyj istniejących:

- `scripts/realtime_credential_broker.py`
- `scripts/realtime_network_smoke.py`
- protected `RealtimeNetworkOffCallSmokeProbe`.

Nie wkładaj standardowego OpenAI key do APK, Intenta, app-private smoke config ani telefonu.

PASS off-call network smoke powinien udowodnić credential fetch + Realtime connect/session i dojście do `STARTING_MEDIA`, po czym frozen media ma poprawnie odrzucić start poza rozmową. `ACTIVE` off-call byłby safety failure.

Jeśli host nadal nie ma `OPENAI_API_KEY` lub HTTPS tunnel, nie zgaduj i nie obchodź zabezpieczeń. Zostaw real-network gate jawnie zablokowany przez external prerequisite.

## Dopiero później: pierwszy realny call

Po realnym off-call OpenAI smoke:

1. pierwszy cellular Realtime call ma być kontrolowany i non-committing;
2. zweryfikuj realną jakość RX/TX, latency, barge-in, TAKE OVER, cleanup i faktyczne GA event ordering/response identity;
3. dopiero później rób realną rejestrację w przychodni z jawnie podanymi danymi i constraints;
4. proposal poza authority -> `NEEDS_USER_DECISION`, nigdy automatyczne rozszerzenie authority.

Safe regression number `510100100` jest autoryzowany tylko gdy fizyczny regression test naprawdę go wymaga.

Przy live call: direct USB-C, Bluetooth off podczas testu, mute voice-call przed dial i po ACTIVE, speakerphone off, restore Bluetooth po teście.

## Zasady Local Agent

- direct GitHub edits, gdy diff/docs/code evidence wystarcza;
- Local Agent tylko do lokalnych buildów/testów/ADB/device;
- `.agent` metadata tylko na `agent-control`;
- każdy task ma dokładnie świeży `agent_binding` tego nowego chatu;
- przed pisaniem na ten sam branch sprawdź active task;
- nie polluj zdrowych multi-minute tasków co 30 s; minimum 2 min, zwykle 5–10 min;
- nigdy nie uruchamiaj lokalnego Codex z taska Local Agenta;
- nie pracuj nad innym repozytorium pod tym bindingiem.

Kontynuuj autonomicznie od RED 1, potem RED 2, potem pełny host gate. Nie pytaj mnie o rzeczy już zapisane w handoffie.

---
