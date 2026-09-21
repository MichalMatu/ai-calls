# Night autonomous run — 2026-09-21

Purpose: continuation contract for a fresh ChatGPT conversation using Local Agent Chat Bridge against **only** `MichalMatu/android-ai-call-bridge`.

## Bridge setup

Chat Bridge is transport/scheduling only; ChatGPT remains planner and Local Agent remains deterministic executor.

Recommended operator sequence in the new chat:

1. enable the Local Agent Chat Bridge extension / Master switch;
2. bind the new conversation to repository id `android-ai-call-bridge` using the Bridge popup;
3. if using operator markers instead of the popup, do it only after the content script is active and send a **separate new user message**: `[LAB:OP:ADD=android-ai-call-bridge]`, then enable it as needed;
4. paste the continuation prompt below as the active goal;
5. the assistant should inspect fresh binding/status evidence and, while work remains, arm a reasonable one-shot wake such as `[LAB:NEXT=2m]` after queueing a short task or `[LAB:NEXT=5m]`/`10m` for healthy longer work.

Never infer or change repository binding from conversation prose. Trust the Bridge envelope and fresh `.agent/status/daemon.json`.

## Autonomous operating contract

- Work autonomously through the night without asking for routine confirmations.
- Ask/pause only for user action, unavailable hardware/credentials, an irreversible external action, or a materially new product decision.
- One active goal/task chain at a time. Inspect terminal result before creating the successor.
- GitHub direct edits are fine for exact reviewable source/docs changes; Local Agent is required for Gradle, host execution, ADB and S22 evidence.
- Every Local Agent task must use the exact current bridge/daemon `agent_binding`, include `resources: []`, be bounded and have a unique immutable id.
- Do not poll healthy tasks aggressively: first recheck about 2 minutes when useful, then 5-10 minutes for healthy longer work.
- Never launch Codex from Local Agent.
- Keep `main` durable and clean; `.agent/tasks`, `.agent/runs`, `.agent/results` stay on `agent-control`.
- Preserve terminal `.agent/results` as evidence. Old task/helper-script files may be pruned after their terminal results exist.

## Technical goal

Make the Edge Gallery / Gemma 4 E2B + official Agent Skills phone-navigation path fast enough for bounded Orange IVR while preserving the application authority boundary.

Do this in this order:

1. read fresh `AGENTS.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md` and this file;
2. inspect fresh `main`, daemon status and the terminal result `chatgpt-orange-partial-stt-v12d-20260921`;
3. establish a clean baseline; no product change should come from diagnostic v12* patches automatically;
4. design the narrowest `prepare -> decide` split for the headless official Agent Skills runtime;
5. benchmark preparation/reset cost separately from final constrained tool decision on the S22;
6. use stable Android partial STT to prepare or run cancelable speculative inference while Max speaks only if it improves measured latency;
7. invalidate speculation on material transcript growth/resumed speech and never release it before final endpoint;
8. final endpoint must bind the decision to the final transcript + explicit information-only goal, then pass the existing application-owned output/action policy;
9. keep current tools to `say`, `listenMore`, `takeOver`; no DTMF yet;
10. once offline/device tests show repeatable safe low latency, perform one controlled Orange call to allowlisted `510100100`, capture the next branch response, hang up and restore state;
11. no PESEL/account secrets/passwords/PIN/OTP/payment data; no purchase, activation, tariff/plan change, contract acceptance or commitment;
12. if the approach cannot meet a practical latency/safety bar cleanly, document/freeze it and move to Gate C rather than piling logic into probes.

## Architecture constraints

- `privileged-helper/` is frozen and must not change.
- Reuse `TextCallAgentBackend`, `TextCallTurnController`, `LocalSpeechTextPipeline` and existing application approval.
- Agent Skills are proposal generation, never authority.
- `LocalPhoneLlmLiveCallProbe` remains an evidence driver, not the product multi-turn session.
- A long capture watchdog is safety only; do not reintroduce a short arbitrary silence timeout as product endpoint semantics.
- Partial STT is a latency/preparation signal, not permission to speak early.

## Completion condition for the night

A useful overnight result is one of:

1. **success:** repeatable low-latency final tool decision + controlled Orange multi-turn evidence capturing the next IVR response, with cleanup and all safety boundaries intact; or
2. **clean negative result:** strong measurements proving the approach is not viable enough on current S22/Gemma, documented with the path frozen and a clean Gate C continuation point.

Before stopping:

- run `bash scripts/verify_host.sh` for durable product changes;
- run only necessary S22 gates;
- verify `privileged-helper/` unchanged;
- update authoritative docs with measured results;
- leave `main` clean;
- use `[LAB:STOP]` only when the active goal is actually complete, otherwise schedule the next evidence-based wake or `[LAB:PAUSE]` if user action is required.

## Continuation prompt to paste into the new chat

```text
Kontynuuj autonomicznie przez noc projekt MichalMatu/android-ai-call-bridge w trybie Local Agent + Local Agent Chat Bridge.

Najpierw zaufaj WYŁĄCZNIE aktualnemu envelope Bridge i świeżemu agent-control:.agent/status/daemon.json dla tożsamości repo/bindingu. Nie przepisuj bindingu z historii. Przeczytaj świeże AGENTS.md, docs/HANDOFF_NEXT_CHAT.md, docs/NIGHT_AUTONOMOUS_RUN_2026-09-21.md, README.md i docs/ROADMAP.md; ARCHITECTURE/SECURITY przy zmianach granic.

Aktywny cel: domknąć eksperymentalny checkpoint EDGE_GALLERY / Gemma-4-E2B-it + oficjalny Agent Skills dla bezpiecznej nawigacji Orange IVR. Frozen Samsung media i privileged-helper są nietykalne. Agent Skills są tylko proposal layer; CallBridge zachowuje target/authority/approval/TAKE OVER. Dozwolone narzędzia skilla na tym etapie: say, listenMore, takeOver. Bez DTMF, logowania, danych wrażliwych, zakupów, aktywacji, zmian taryfy/umów i zobowiązań.

Punkt startowy: v12d udowodnił bogaty partial STT całego powitania Maxa, ale odpowiedź wyszła około 38 s po recognizer end i Orange rozłączył. Warm Agent Skills potrafił wcześniej dać poprawny SAY około 3.5-4.4 s, natomiast reset sesji per-turn dawał około 10-11.5 s. Następny kierunek to PREPARE podczas mowy -> szybki DECIDE po final endpoint, ewentualnie cancelable speculative inference z partial STT. Nigdy nie wypuszczaj spekulacyjnego wyniku do TTS/TX przed finalnym endpointem, dopasowaniem do final transcript/goal i application-owned approval. Wznowienie/istotna zmiana transkryptu unieważnia spekulację.

Pracuj sekwencyjnie i autonomicznie. Najpierw offline/device benchmark bez telefonu, potem dopiero kontrolowany Orange call do istniejącego allowlist 510100100. Celem calla jest wyłącznie informacyjne dojście dalej w gałęzi prepaid/SIM i zapis następnej odpowiedzi Orange. Nie rozszerzaj allowlisty. Po każdym tasku czytaj terminalny result; nie traktuj queue/ACK jako sukcesu. GitHub direct do małych reviewable zmian, Local Agent do Gradle/ADB/S22. Każdy task: świeży exact agent_binding, resources: [], unikalny id, bounded timeout. Nie uruchamiaj Codex z Local Agent.

Nie pytaj mnie o rutynowe decyzje. Sam wybieraj najwęższy bezpieczny następny eksperyment na podstawie dowodów. Zatrzymaj/pauzuj tylko gdy potrzebna jest moja akcja, nowa zgoda albo materialnie nowa decyzja produktowa. Jeśli Edge/Skills nie da się czysto doprowadzić do praktycznej latencji, udokumentuj negatywny wynik, zamroź tę ścieżkę i przejdź do przygotowania Gate C zgodnie z ROADMAP, bez hacków w probe.

Po utworzeniu taska ustaw sensowny wake Bridge (zwykle [LAB:NEXT=2m] na wczesny check, potem 5-10m dla zdrowego dłuższego taska). Przed końcem nocy: verify_host dla durable changes, tylko konieczne physical gates, docs zaktualizowane, main clean, privileged-helper bez zmian.
```
