# Roadmap

## Status legend

- `DONE` — implementation complete for the stated scope.
- `HOST_GREEN` — canonical host verification passed.
- `PROVEN_S22` — physically executed on the target phone.
- `FROZEN` — change only with a concrete root cause.

## Stable foundation

- Samsung cellular media + Shizuku helper — `PROVEN_S22 / FROZEN`.
- local Polish STT/TTS — proven.
- Gemma 4 LiteRT-LM runtime + Gemma-first dialogue action router — `PROVEN_S22`; current real-model off-call probe is 8/8.
- Android Keystore-backed `IdentityVault` — `PROVEN_S22`.
- `BOOK_APPOINTMENT` Gate D — `DONE / HOST_GREEN / PROVEN_S22`.
- generic `CallExternalEffect` + shared one-shot `CallCommitmentGate` — `DONE / HOST_GREEN`.
- `SET_SERVICE(CLIR=true)` validation + separate external-success tracking — `DONE / HOST_GREEN`.
- local `PHONE` fact resolver through `IdentityVault -> AuthorizedFactSnapshot -> FactDisclosurePolicy` — `DONE / HOST_GREEN`.
- private app-owned `PHONE` enrollment/disclosure into `AndroidIdentityVault` — `DONE / HOST_GREEN / PROVEN_S22`.
- durable app-owned CLIR-enable campaign grant + exact scope matcher + local dial/runtime tooling — `DONE / HOST_GREEN`; `PROVEN_S22` pending.

`docs/AUTONOMOUS_OPERATION_MODE.md` remains normative for physical acceptance.

## Active gate — Orange CLIR enable

Physical execution is active and **not yet successful**.

Already established on the real S22/Orange path:

- on-net campaign route is `*100`;
- developer relay dialogue can use `script/PhraseMatrix -> Gemma -> live supervisor`; the app-owned campaign path is `script/PhraseMatrix -> Gemma -> fail closed` with no ChatGPT dependency;
- endpointing uses 1.5 s trailing silence / 60 s hard capture to tolerate IVR pauses;
- Gemma correctly classifies the service-number prompt as `DISCLOSE_AUTHORIZED_FACT(PHONE)`;
- unsupported fact requests fail closed;
- commitment and factual success remain application-owned;
- independent state interrogation is available;
- latest independent state: caller ID defaults to **not restricted**.

The identity blocker is closed and physically proven: private app-owned PHONE enrollment/disclosure is `PROVEN_S22`. The transport blocker is also closed in code: the app now owns the bounded campaign grant, scope validation, dial and local runtime; ChatGPT/Local Agent/ADB are not part of product call execution. The remaining blocker is physical proof of this app-owned campaign executor and factual CLIR success. Plaintext must not move through Git, Local Agent JSON, durable logs or supervisor/model context.

### Next execution order

1. Install the exact current-head APK on S22 and open `Campaign tools`.
2. Create the bounded local CLIR-enable grant; the app binds it to the current default voice subscription and requests any missing Android runtime permissions locally.
3. Start the campaign from the app. The app must validate the grant/readiness/IDLE state, reserve an attempt, dial exact `*100`, run local dialogue/PHONE disclosure/commitment and clean up its owned call without ChatGPT/Local Agent/ADB.
4. Accept enable only after factual Orange success **and** independent `*#31#` confirms restriction active.
5. Then add/authorize the separately scoped inverse `SET_SERVICE(CLIR=false)` campaign and run its acceptance loop.

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

Authorization, route discovery, model output, permit consumption and call termination are not success evidence by themselves.

After CLIR enable/disable acceptance, return to generic phone-task development rather than building an Orange-specific bot.
