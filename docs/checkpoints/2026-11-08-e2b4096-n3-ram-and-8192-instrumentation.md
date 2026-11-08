# E2B context reliability — 4096 N=3 physical RAM / 8192 opt-in status (2026-11-08)

## Decision and scope

**4096 Lab = PASS_DEVICE_TECHNICAL in three 13-turn sessions** on real S22 (39 model turns). This is not a human semantic quality pass; two of the three sessions returned 11/13 diagnostic heuristics. **8192 = HOST_PASS, S22 NOT_RUN** due to PocketHost foreground, not a model failure. Other app call routes still use 2048.

### Verified 4096 S22 samples

| Session | Physical 13 turns | Mean per-turn time | Diagnostic checks | Peak app PSS | Lowest MemAvailable |
| --- | --- | ---: | --- | ---: | ---: |
| First (earlier) | 13/13 PASS | 6686 ms | 11/13 | not sampled | not sampled |
| Second (new) | 13/13 PASS | 5346 ms | 13/13 | 2,326,200 kB | 1,378,220 kB |
| Third (new) | 13/13 PASS | 7633 ms | 11/13 | 2,348,103 kB | 1,436,520 kB |

Last two sessions' PSS and MemAvailable are sampled Android `dumpsys meminfo` / `/proc/meminfo` at approx. 8-second cadence, **not exact instantaneous absolute peaks and not GPU VRAM accounting**. App PSS peak observed ~2.35 GB, low system MemAvailable observed ~1.38 GB. First session's RAM wasn't measured. All 13-turn runs ended with zero END_CALL proposals, no telephone call, phone IDLE. Mean answer ~6.56 s across these three runs. A physically free memory reserve was observed, warranting a *guarded* 8192 experiment, not an unconditional default switch.

Previous 2048 native setup encountered `LiteRtLmJniException` at turn 10 in two separate physical attempts. 4096 completing 39 turns across three sessions supports context exhaustion as a possible cause but does not prove the underlying LiteRT JNI root cause.

### Exact receipts

- `aicalls-e2b4096-n3-ram-preflight-20261108-v1`: DONE/0, Samsung SM-S906B, ADB, IDLE, no campaign, clean checkout; starting MemAvailable ~2,342,184 kB.
- `aicalls-e2b4096-n3-ram-native-20261108-v1`: DONE/0, 2/2 additional physical 13-turn runs PASS, memory samples above, postflight IDLE and clean. Together with `aicalls-semantic2-s22-context4096-20261108-v1` DONE/0, N=3 complete.
- `main@1c2bf2f1cfa310302cf7f642a2238a98c4d28166`: initial explicit `lab_context_tokens=8192` instrumentation option; Mac AndroidTest compile initially failed due to incorrect instrumentation argument accessor. Superseded by `main@faf6ee5be68ca5af95e3438b42ecc3af1ba5cb60`.
- `aicalls-e2b8192-experimental-mac-gate-20261108-v2`: DONE/0; full `scripts/verify_host.sh` PASS (AndroidTest, APK, JVM, lint, 365 Python, audio audit, clean).
- `aicalls-e2b8192-s22-first-ram-20261108-v1`: FAILED/71 at *preflight*, no APK installation, no 8192 inference; initial MemAvailable ~2,332,336 kB.
- `aicalls-e2b8192-foreground-readonly-20261108-v1`: DONE/0; foreground `pl.pockethost.companion/.MainActivity`, Call IDLE, campaign inactive. Do not interrupt or force-close PocketHost.

### What remains open

1. Reattempt one guarded 8192 13-turn test only when S22 foreground is AI Calls or Samsung Launcher and Call IDLE. Monitor app PSS and MemAvailable against observed 4096 values, test context `8192` specifically, and check JNI, elapsed time, and output dimensions. Keep ordinary Lab 4096 and other call paths 2048.
2. If 8192 is stable and memory permits, compare N=3 runs and evaluate whether more context produces meaningful quality gains instead of just RAM/latency overhead.
3. Explicitly review the raw answers behind the two repeatedly unconfirmed `missing_ticket_cautious` and `final_ticket_cautious` heuristic checks, with private evidence and human `UNASSESSED` until reviewed. Do not publish transcripts to GitHub or Local Agent results.
4. Then implement context memory in its own sprint following [memory architecture plan](../LAB_E2B_CONTEXT_MEMORY_PLAN.md). E4B and other models only after E2B memory handling is accepted.

**F-148 remains OPEN** for root cause and true natural semantic quality. The 4096 fix is *device-technically accepted at 13-turn scope only*.
