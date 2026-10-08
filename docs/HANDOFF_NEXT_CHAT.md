# Handoff — Orange CLIR physical acceptance

Date: 2026-10-08 02:50 CEST

## Frozen checkpoint

Repository: `MichalMatu/ai-calls`.

Durable product code is on `main`. Local Agent transport/history remains on `agent-control`.

Stable main checkpoint at session stop:

```text
d4f73bef621a198d384d5ba9ff3e1a775c3aa57a
Decouple campaign execution from ChatGPT transport
```

Do not infer a newer product checkpoint from Local Agent task history.

## What is complete

- Samsung S22 cellular media path + privileged helper: `PROVEN_S22 / FROZEN`.
- Local Polish STT/TTS: proven.
- Local phone LLM/Gemma dialogue infrastructure: proven in earlier S22 probes.
- Generic external-effect authority + one shared `CallCommitmentGate`: host-green.
- Android Keystore-backed `IdentityVault`: `PROVEN_S22`.
- Private app-owned PHONE enrollment/disclosure path: `PROVEN_S22`.
- App-owned campaign architecture is merged on `main`:
  - durable scoped grant;
  - exact `*100` / `SET_SERVICE(CLIR=true)` matching;
  - current voice-subscription binding;
  - bounded attempt reservation;
  - app-owned readiness + dial + foreground execution + cleanup;
  - ChatGPT/Local Agent/ADB removed from the product call execution path.

## Physical state at stop

No accepted app-owned CLIR-enable call was completed in this session.

Latest independent network state remains:

```text
CLIR enabled = false
physical enable acceptance = incomplete
```

Physical S22 evidence gathered today:

- wireless ADB discovery and install path works when the local network is healthy;
- required call permissions were granted;
- PHONE vault was present in the later physical preflight;
- local phone-LLM runtime/model files are present;
- Shizuku Manager 13.6.0 is installed;
- the legacy external `start.sh` path is absent;
- live readiness still failed on `shizuku_binder_unavailable`.

The next physical blocker is therefore the Shizuku server/binder startup/readiness path, not campaign authority, PHONE storage, or Android call permissions.

## Unmerged experiment that was intentionally NOT accepted

PR #21, `Auto-start one-shot debug CLIR E2E`, was a debug-only experiment to remove manual Authorize/Start clicks during physical testing.

Last branch head before cleanup:

```text
work/debug-one-shot-clir-e2e-v1
a0221e906e694e2f2554e21c307f8fe603e218a1
```

Important evidence from that line:

- CI was green at `0bca996803c3b00930c95d7d041112d60eb1cbf2`;
- a low-memory local debug APK build at that exact SHA also completed successfully;
- the one-shot branch was not physically accepted;
- a later manifest-only PHONE-number-read permission change existed after that green build;
- do not merge or recreate this experiment blindly. Re-audit the diff first if the idea is reused.

## Session shutdown state

- no active physical call;
- Local Agent `ai-calls` worker is idle;
- zero pending task JSONs without run/result;
- no additional device work should auto-start after this handoff;
- work stopped because the operator ended the session, not because CLIR acceptance succeeded.

## Resume order

1. Start from fresh `origin/main`; read `AGENTS.md`, this file, `AUTONOMOUS_OPERATION_MODE.md`, `ROADMAP.md`, `CAMPAIGN_AUTHORIZATION_GRANT.md`, and `G5_CLIR_ROUTE_DISCOVERY.md`.
2. Confirm S22 is reachable and phone is `IDLE`.
3. Restore Shizuku server/binder readiness using the currently installed Shizuku version; do not assume the historical `start.sh` path exists.
4. Re-run live readiness only.
5. Deploy an exact reviewed APK.
6. Run one real app-owned `*100` iteration. Do not add unrelated audits, synthetic churn, or more authorization UI before this physical test.
7. Accept CLIR enable only if Orange gives factual success and independent `*#31#` confirms restriction active.
8. Only after enable acceptance, implement/run the separately scoped inverse `CLIR=false` acceptance.

If the first real call exposes a concrete runtime failure, patch only that observed failure and repeat the physical test.

## Security boundary

Never put the real PHONE value in Git, Local Agent task JSON, durable logs, relay transcripts, model/supervisor context, or handoff text.

The allowed disclosure path remains:

```text
IdentityVault
 -> AuthorizedFactSnapshot
 -> FactDisclosurePolicy
 -> late on-device read
 -> approved local speech
```

## Start prompt for the next chat

```text
Kontynuuj wyłącznie MichalMatu/ai-calls od świeżego origin/main. Przeczytaj AGENTS.md i docs/HANDOFF_NEXT_CHAT.md. Main checkpoint z handoffu to d4f73bef621a198d384d5ba9ff3e1a775c3aa57a, ale zawsze potwierdź świeży HEAD. Product call execution jest app-owned; ChatGPT/Local Agent/ADB służą tylko do repo/build/install/diagnostics. PHONE vault i call permissions były fizycznie gotowe. Ostatni konkretny blocker: Shizuku binder unavailable na S22 z Shizuku Manager 13.6.0; historyczny start.sh nie istnieje. Najpierw napraw readiness Shizuku, potem jeden realny app-owned *100 i niezależne *#31#. Nie reaktywuj debug one-shot PR #21 bez ponownego audytu.
```
