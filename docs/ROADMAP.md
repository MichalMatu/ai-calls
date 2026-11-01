# Roadmap

## Status legend

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — canonical host verification passed.
- `PROVEN_S22` — physically executed on the target phone.
- `FROZEN` — change only with a concrete root cause.

## Najnowszy checkpoint MVP E2E — 2026-11-01

Szczegóły i dokładne receipty: `docs/checkpoints/2026-11-01-ai-calls-mvp-e2e-precontinuation.md`. S22 Gemma inference **PASS**, host `7aa1d952` **PASS**, lecz `shizuku_binder_unavailable` **FAIL**. Dwa późniejsze testy zakończyły się przed kompilacją i instalacją z powodu nieaktualnego literalnego SHA. Zanim zacznie się rozmowa: wynik QA najnowszego kodu, przywrócenie Shizuku Binder, testy bez rozmowy STT/Gemma/TTS na nowym APK. Połączenie tylko ręcznie zainicjowane i świadomie nadzorowane; autonomiczny exact-call controller nadal zablokowany.

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

**2026-11-01 Telecom capability feasibility milestone:** added `scripts/bin/aicalls call-capabilities` with fail-closed read-only inspection and regression tests. Physical Mac/S22 probe `aicalls-exact-capabilities-s22-20261101-v1` confirmed Android 16/Shizuku/IDLE; default dialer role query returned UNKNOWN, no verified exact-call controller. Android standard InCallService requires becoming a complete default dialer; preserving Samsung dialer remains a constraint. Await separate host QA and role fallback receipts before closing the diagnostics gate. See `docs/EXACT_CALL_CAPABILITY_SPRINT_2026-11-01.md`.

**2026-11-01 FINAL exact-call feasibility sprint PASS:** source `31bc42f`, Mac task `aicalls-exact-capabilities-closeout-host-20261101-v2` `done/0`: **199 Python tests, Gradle unit/lint/assembleDebug/AndroidTest APK PASS**. Device task `aicalls-exact-capabilities-final-s22-20261101-v1` `done/0`: Android 16, Wireless ADB, Shizuku Binder and microphone ready, `IDLE`, default dialer **com.google.android.dialer** confirmed by two independent shell queries. Diagnostic milestone closed; **autonomous dial/call-specific controller remains BLOCKED**. See `docs/EXACT_CALL_CAPABILITY_SPRINT_2026-11-01.md`.


**2026-11-01 supervised audio pilot installed:** `docs/SUPERVISED_FIRST_AUDIO_PILOT_2026-11-01.md` records Android UI/service and a deterministic, effect-free 2-turn speech backend for explicitly user-controlled existing calls. `HOST_GREEN` (199 Python + Android unit/lint/APKs), S22 Wireless ADB/Shizuku/IDLE `PROVEN_S22` for **no-call readiness**, and the reviewed APK is **installed on S22**. No real dialogue test has taken place, no call was dialed/hung up, and `CampaignCallControllerRegistry` still lacks an exact-call backend. Preserving Google Phone as default prevents using the standard InCallService call handle without an additional opt-in complete dialer path.

**MVP supervised call — 2026-11-01:** funkcjonalność manualnego dołączenia do rozmowy i lokalnych skryptowych 3 tur (bez zmian usług, bez danych osobowych i bez hangup) jest w kodzie `5cb13fc`; Mac host gate PASS (199 Python, Android unit/lint/assemble). **Nie jest PROVEN_S22:** próba `adb install -r` przerwana przez utratę Wireless ADB; kolejna diagnostyka: device offline, brak mDNS. Pierwszy krok po odzyskaniu Wireless Debugging: deploy + UI refusal smoke, następnie ręcznie zainicjowana uzgodniona rozmowa testowa. Raport `docs/SUPERVISED_FIRST_CALL_MVP_2026-11-01.md`.


**2026-11-01 latest MVP S22 deployment acceptance:** `aicalls-mvp-install-retry-20261101-v2` done/0 `adb install -r` Success (nondestructive), `aicalls-mvp-postinstall-ui-smoke-20261101-v1` done/0 launched `MainActivity`, visible supervised-first-call button, Google Phone remains default. Verified current S22 via Wireless ADB port 44441, identity and IDLE. Only install/launcher UI gate closed; in-pilot negative acceptance and carrier audio RX/STT/TTS/TX remain untested. Exact-call autonomous controller remains blocked. See `docs/SUPERVISED_FIRST_CALL_MVP_2026-11-01.md`.


**2026-11-01 supervised Gemma speech hardening:** the free-form denylist was replaced by app-owned dialogue-skill speech; the local model classifies only, and the exact allowlist rejects raw prose regardless of synonyms. Script mode is the default on devices without the reviewed Gemma file. Regression tests added. Outstanding delivery gates: exact-HEAD host test/lint/APK, safe S22 deployment and no-call refusal UI, model provision/READY, and a manually initiated consenting real-call STT/Gemma/TTS/TX observation. No automated CLIR dial or global hangup.


## 2026-11-01 — supervised MVP E2E physical-check sprint (newest)

Read `docs/MVP_E2E_SPRINT_2026-11-01.md`. Exact S22 no-call Gemma readiness + real LiteRT skill inference **PASS** (`aicalls-mvp-phone-gemma-inference-20261101-v1`, 30.317s). Hardening adds 20ms consent-checked PCM uplink and synchronous STOP authorization revocation, binds call-start confirmation to Gemma/script mode, and adds STT/TTS/supervised-Gemma Android instrumentation tests. Final host QA and Binder-readiness tasks are queued; do not infer their PASS until results. No real service call or phone audio acceptance occurred, no automatic dial/hangup or CLIR mutation. Real end-to-end supervised MVP still needs fresh APK/device tests plus consenting human-initiated call.
