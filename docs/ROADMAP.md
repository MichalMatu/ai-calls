# Roadmap

## Status legend

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — canonical host verification passed.
- `PROVEN_S22` — physically executed on the target phone.
- `FROZEN` — change only with a concrete root cause.

## Stable foundation

- Samsung cellular media + privileged helper — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- local phone LLM/Gemma dialogue infrastructure — physically probed on S22.
- Android Keystore-backed `IdentityVault` — `PROVEN_S22`.
- generic `CallExternalEffect` + shared one-shot `CallCommitmentGate` — `DONE / HOST_GREEN`.
- `SET_SERVICE(CLIR=true)` validation + separate factual-success tracking — `DONE / HOST_GREEN`.
- private app-owned PHONE enrollment/disclosure — `DONE / HOST_GREEN / PROVEN_S22`.
- durable app-owned CLIR-enable campaign grant, exact scope matcher, subscription binding, app-owned dial/runtime/cleanup — `DONE / HOST_GREEN`; physical end-to-end acceptance pending.

`docs/AUTONOMOUS_OPERATION_MODE.md` remains normative for physical acceptance.

## Active gate — Orange CLIR enable


**2026-11-01 SAFETY HOLD:** The corrective campaign fixed the source-level CLIR wording matcher, in-flight scope checks, shared grant locking, provisional carrier-result reporting and conservative SIM selection. The app-owned call now **refuses to dial** until exact-call ownership and targeted termination exist; do not bypass. Android build, tests and physical acceptance are not complete. Next: fresh Local Agent, host/Android tests, exact owned-call controller, then current Shizuku readiness and app-authorized physical proof. Details: `docs/CORRECTION_CAMPAIGN_2026-11-01.md`.

The product execution path is app-owned:

```text
durable exact-scope grant
 -> readiness + IDLE
 -> app-owned exact *100 dial
 -> local script/PhraseMatrix + local model
 -> late-bound PHONE policy
 -> one-shot commitment permit
 -> factual Orange result
 -> independent *#31# verification
 -> cleanup to IDLE
```

ChatGPT, Local Agent and ADB are development/deployment/diagnostic tooling only. They are not product authority and must not be required to sustain the call.

### Current physical blocker

At the 2026-10-08 handoff:

- PHONE vault: present in the later S22 preflight;
- required call permissions: granted;
- local phone-LLM runtime/model files: present;
- Shizuku Manager 13.6.0: installed;
- legacy Shizuku external `start.sh`: absent;
- live readiness: blocked by `shizuku_binder_unavailable`;
- CLIR remains not accepted/enabled.

Do not reopen frozen media, identity, or campaign-authority architecture without new physical evidence.

### Next execution order

1. Restore Shizuku server/binder readiness for the installed version.
2. Run live readiness and confirm phone `IDLE`.
3. Deploy the exact reviewed APK.
4. Run one real app-owned `*100` physical iteration.
5. Accept enable only after factual Orange success **and** independent `*#31#` confirms restriction active.
6. Then implement/run the separately scoped inverse `SET_SERVICE(CLIR=false)` acceptance.

## Acceptance invariant

```text
exact authorized task/target/effect
 -> deterministic validation
 -> one fresh shared CallCommitmentGate permit
 -> permit consumed exactly once
 -> factual external success
 -> independent state verification
 -> cleanup to IDLE
```

Authorization, model output, route entry, permit consumption and hangup are not success evidence by themselves.

After CLIR enable/disable acceptance, return to generic phone-task development rather than building an Orange-specific bot.

**Update — logical sprint 2026-11-01:** `docs/SPRINT_LOGIC_CORRECTIONS_2026-11-01.md` is the current source checkpoint. The fake fixed-`false` call ownership gate, false questions, `generation=0L`, single-account SIM veto and early microphone/dial permission prompts have been replaced. A proper exact-call lifecycle interface now defines the safe integration boundary; **no installed Android/S22 controller exists yet**, so physical call remains blocked on a *real missing capability*, not synthetic flags. Neutral PhraseMatrix routing precedes Gemma, but full CallPlan integration, independent network verification, Android test runs and S22 acceptance remain open.


**2026-11-01 Wireless/Call-Safety sprint checkpoint:** `docs/SPRINT_WIRELESS_ADB_CALL_SAFETY_2026-11-01.md`. Wireless S22 discovery and identity verification are in the host tooling. Unsafe developer global end-call and automatic dial were removed/disabled. **Android app-specific dial/hangup adapter remains unimplemented** and physical S22/CLIR validation is open. Next gates: Local Agent Python+Gradle host tests, no-call wireless ADB/IDLE/Shizuku readiness, then actual exact-call controller and device acceptance. Do not treat the attach-only probe as an autonomous calling solution.

**2026-11-01 Local Agent control sprint:** GitHub task transport now has a pending Mac host/phone preflight task; `agent-control` heartbeat is stale (2026-10-19) so its acceptance is **not verified**. Android call lifecycle exceptional cleanup and pre-dial authorization recheck hardened. Wireless mDNS reconnect is restricted to the S22 service identity. Full results and remaining controller gate: `docs/SPRINT_AGENT_CONTROL_CALL_LIFECYCLE_2026-11-01.md`.

**2026-11-01 Mac Host Control protocol migration:** see `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md`. The old `ai-calls` project heartbeat is not evidence of Mac being offline; host ADB/build tasks now use `MichalMatu/local-agent:agent-control`, explicit `macbook-michal`/installation routing and per-installation `task-results`. Any host/ADB acceptance status depends on fresh results, not on queue submission. CLIR exact-call controller remains the product gate.

**Real Mac Wireless ADB acceptance, 2026-11-01:** `aicalls-mac-wireless-preflight-20261101-v1` terminal `done/0` proves live S22 identity and `IDLE` through current mDNS port. **No Gradle/Shizuku proof yet**; see `docs/HOST_S22_MAC_ACCEPTANCE_2026-11-01.md`. This closes only transport reachability, not exact-call Android ownership or CLIR.

**2026-11-01 evidence update:** verified Mac **HOST_GREEN** on `0ce0d658` (`aicalls-mac-host-quality-20261101-v2`: Gradle unit/lint/debug and 185 Python tests). Verified S22 **no-call Shizuku/audio readiness PASS** (`aicalls-shizuku-user-start-readiness-20261101-v1`, `live_call_readiness=true`, Binder/permissions granted, `IDLE`, Wireless ADB). Real app-owned exact-call controller and CLIR physical acceptance remain **BLOCKED**. Refer to `docs/HOST_S22_MAC_ACCEPTANCE_2026-11-01.md`.
