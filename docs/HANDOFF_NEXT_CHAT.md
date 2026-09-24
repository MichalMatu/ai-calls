# Handoff — next chat: Orange CLIR physical loop

Date: 2026-09-25

## Repository

`MichalMatu/ai-calls`

Durable product/docs branch: `main`. Local Agent transport branch: `agent-control` only. Normal steady state is exactly those two branches. Always use the fresh bridge-provided Local Agent binding; never copy an old binding from history or docs.

Read `docs/AUTONOMOUS_OPERATION_MODE.md` immediately after this handoff. It is normative for active physical acceptance work.

## Product goal

AI Calls is a generic autonomous phone-task engine, not an Orange-specific bot. Models/supervisor help dialogue only; application-owned deterministic policy owns target/task/effect authority, disclosure, commitment and factual completion.

The target development mode is fully autonomous operationally: Local Agent drives local commands/device work, the live call progresses through deterministic script -> Gemma -> live supervisor fallback, physical findings drive minimal patches, and the operator is not used as a terminal/log relay when the system can perform the step directly.

## Frozen foundation

Keep closed unless a concrete root cause appears:

- Samsung cellular RX/TX + `CallMediaSessionCoordinator` — `PROVEN_S22 / FROZEN`;
- Shizuku / privileged media boundary — `PROVEN_S22 / FROZEN`;
- local Polish STT/TTS, IdentityVault disclosure, Gemma 4 LiteRT-LM lifecycle — proven;
- `BOOK_APPOINTMENT` Gate D — proven;
- generic `CallExternalEffect` + single shared `CallCommitmentGate` — host/no-call proven;
- `SET_SERVICE(CLIR=true)` exact validation + one-shot permit + separate external-success evidence — host/no-call proven;
- full synthetic/no-call product chain on S22 — proven.

Do not return to completed G1–G4/G6, redownload Gemma, rewrite frozen media or start broad cleanup without a concrete root cause.

## Physical findings on 2026-09-24

Initial G5b read-only discovery produced a generic Orange clarification/reprompt rather than a CLIR-specific route.

The later physical loop then established additional real behavior:

- Orange on-net service route uses `*100` for this campaign;
- the first CLIR navigation phrase is handled locally;
- a second local clarification phrase was added for Orange's generic "nie jestem pewien" response;
- no-speech windows are retried instead of immediately terminating the call;
- multi-turn fallback is `script/PhraseMatrix -> Gemma -> ChatGPT supervisor`;
- supervisor takeover occurred live while the cellular call was still `OFFHOOK`;
- Orange asked: `podaj dowolny numer twojej usługi lub wprowadź go na klawiaturze`;
- the current SIM/service number was supplied only through transient live handling and must not be copied into durable Git/log evidence;
- the call ended without factual CLIR success evidence;
- independent network interrogation with `*#31#` returned `Caller ID defaults to not restricted. Next call: Not restricted`;
- therefore CLIR remained disabled after that iteration.

Do not claim success from the call alone. Independent state evidence says the goal is not yet achieved.

## Current implementation state

The live probe now supports:

```text
scripted CLIR navigation
 -> scripted clarification when Orange is uncertain
 -> Gemma skills
 -> live supervisor relay fallback
 -> contextual CLIR commit prompt recognition after route context exists
 -> one shared CallCommitmentGate permit
 -> contextual success recognition after commitment
 -> factual completion path
```

Host cleanup previously raced the Android report write when Orange disconnected. `scripts/aicall_tools/calls/chatgpt_relay_live_call.py` was changed to preserve a sanitized live-probe report before cleanup so the next physical failure is diagnosable.

The host runner now also generates a relay session ID automatically when one is not supplied and defaults to the full bounded 10-turn budget. A new chat must not ask the operator for either value. The explicit CLIR-effect runner guard remains required internally and is supplied by the executor when the accepted authorization context covers the exact effect.

Relevant durable commits from this physical sequence include:

- `d08f20bed` — local Orange CLIR clarification handling;
- `417bf6240` — contextual CLIR commit/success handling;
- `1b2ef395e` — preserve live CLIR probe report before cleanup;
- `d0071fe0` — normative autonomous-operation documentation;
- `d1b3baa78` — allow application-owned scoped campaign grants as an authorization source;
- `42d37efa6` — make Local Agent the primary executor;
- `2ecb02451` — harden the autonomous loop against avoidable stalls.

Always fetch fresh `origin/main` rather than assuming these remain the tip.

## Required work mode

For the active CLIR campaign:

```text
fresh Local Agent state
 -> build/install only when code changed
 -> readiness immediately before dial
 -> require IDLE
 -> real Orange call
 -> script/PhraseMatrix first
 -> Gemma second
 -> supervisor takes over live if unresolved
 -> preserve sanitized report
 -> independent CLIR state check
 -> minimal patch from observed physical failure
 -> next real call
```

Do not substitute unit/synthetic suites for this physical loop unless the operator explicitly requests them. Build/compile/install is allowed and expected when needed.

Do not ask the operator to paste commands, copy terminal output or manually watch relay branches when Local Agent/ADB/GitHub can do it. Monitor relay requests while the call is still active.

Before asking any operator question, use the anti-stall ladder from `docs/AUTONOMOUS_OPERATION_MODE.md`. Recover facts from repo/device/logs first, choose safe reversible defaults for non-material choices, and repair/retry mechanical failures automatically. While a call is `OFFHOOK`, hot-call monitoring preempts all unrelated work.

Internal runner details such as relay session ID, bounded retry count, build/install decision after a code patch and required internal authorization guard flags are executor-owned; do not ask the operator to supply them.

Known remaining automation gap: Orange may request the service number. S22 shell probes (`dumpsys isub` and `content://telephony/siminfo`) do not expose the own MSISDN on this device. Until an app-owned `IdentityVault -> FactDisclosurePolicy -> local DTMF/speech` path is wired for this state, handle the authorized phone field only through transient live handling and never persist plaintext in Git, Local Agent JSON or durable logs. This gap must not trigger a generic operator question during the call if an already-authorized transient disclosure path is available.

If an external platform/tool blocks an action, do not bypass the platform control and do not fabricate execution. Continue all non-blocked work autonomously and report the exact blocker only when operator action is genuinely unavoidable.

## Authorization architecture direction

Repeated phone-task retries should ultimately be governed by the durable scoped campaign-grant design described in `docs/AUTONOMOUS_OPERATION_MODE.md`, not by treating chat prose or documentation as an authority store.

The grant must be application-owned, explicit, scoped and revocable. It should eliminate redundant product prompts inside an unchanged authorized scope while preserving fail-closed behavior for material target/task/effect/account widening.

Existing `CallCommitmentGate` and external-success evidence remain mandatory for real account-changing effects. Do not create `ClirCommitmentGate`.

## Relevant files

- `docs/AUTONOMOUS_OPERATION_MODE.md`
- `scripts/aicall_tools/calls/live_call_readiness.py`
- `scripts/aicall_tools/calls/chatgpt_relay_live_call.py`
- `app/src/main/kotlin/pl/michalmatu/aicallbridge/developerrelay/ChatRelayLiveCallProbe.kt`
- `docs/G5_CLIR_ROUTE_DISCOVERY.md`
- `docs/ORANGE_MAPPING_RUNBOOK.md`
- `docs/GENERIC_PHONE_TASK_AUTHORITY.md`
- `docs/SECURITY_PRIVACY.md`
- `service-packs/orange/service_tree.v1.json`

## Local Agent rules

- use only the fresh current-chat binding;
- inspect daemon/active-task evidence before queueing work;
- every task JSON declares `resources` explicitly;
- Local Agent is the default/first-choice executor for repository mutations, multi-step work, builds, Android/ADB/device state, local files/processes/logs and physical-call support;
- prefer one coherent Local Agent workflow over splitting a task across direct GitHub edits and manual/local follow-up;
- direct GitHub is mainly for lightweight read-only inspection, control-plane transport or a truly isolated tiny edit when Local Agent adds no practical value;
- do not make the operator a local-shell proxy;
- `.agent/*` stays on `agent-control`, never merge it into `main`;
- durable changes go to `main`.

## Completion condition

The CLIR enable task is complete only when application-owned completion logic has factual success evidence **and** the independent network-state check confirms caller-ID restriction is actually active.

After that, the next acceptance task is the inverse physical operation (`SET_SERVICE(CLIR=false)`) and another autonomous iteration, with the goal of removing supervisor intervention from recurrent turns.

## Suggested next-chat instruction

```text
Kontynuuj wyłącznie MichalMatu/ai-calls z aktualnego main. Najpierw przeczytaj AGENTS.md, docs/HANDOFF_NEXT_CHAT.md i docs/AUTONOMOUS_OPERATION_MODE.md oraz sprawdź świeży Local Agent. Local Agent ma pierwszeństwo i powinien prowadzić zadania end-to-end. Pracuj w trybie fizycznej pętli: Local Agent -> realny Orange call -> script/PhraseMatrix -> Gemma -> live supervisor takeover tylko gdy potrzebny -> factual evidence -> niezależny status CLIR -> minimalna poprawka -> kolejny call. Operator-question budget jest zero domyślnie: odzyskaj fakty z repo/telefonu/logów, sam wybieraj bezpieczne odwracalne defaulty i automatycznie naprawiaj/retry mechanical failures; pytaj tylko o unrecoverable material decision. Gdy call jest OFFHOOK, relay/call monitoring ma absolutny priorytet nad inną pracą. Nie rób syntetycznych/unit testów jako pętli akceptacyjnej i nie używaj operatora jako terminala/log relayu. Aktualny stan sieci po ostatniej iteracji: CLIR nadal wyłączony.
```