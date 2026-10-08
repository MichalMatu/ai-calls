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
