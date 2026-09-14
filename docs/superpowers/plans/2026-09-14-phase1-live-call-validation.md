# Phase 1 Live Cellular Audio Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove or reject digital cellular downlink capture and digital cellular uplink injection on the exact Samsung Galaxy S22+ build using a dedicated SIM and a second test phone, without adding AI or a custom dialer.

**Architecture:** Reuse the already-proven shell privilege boundary (UID 2000) and existing diagnostic probe code. Extend only the bounded physical-test path needed to read `VOICE_DOWNLINK` PCM during an active call and to inject a deterministic low-amplitude PCM fixture through `TYPE_TELEPHONY`. Keep capture and injection independent, measure each direction, and promote a backend only after the remote/local physical observation passes.

**Tech Stack:** Android 16 / API 36, Kotlin/Java, `AudioRecord`, `AudioTrack`, `AudioManager`, ADB shell / `app_process`, Local Agent, Gradle, PCM16LE.

**Spec:** `docs/POC_AUDIO_TEST_PLAN.md`, `docs/DEVICE_CAPABILITY_PROBE.md`, `docs/S22_BASELINE_2026-09-14.md`, `docs/ROADMAP.md`

## Global Constraints

- Target device is the exact `SM-S906B` build recorded in `docs/S22_BASELINE_2026-09-14.md`; after a firmware change, rerun the baseline first.
- No OpenAI/Realtime integration in this plan.
- No automatic dialing, contact access, call-log access, or custom default dialer.
- No root/system-image modification for the primary experiment.
- Every audio operation is bounded in time and releases resources on failure.
- No call audio is committed to Git.
- `PROVEN_S22` for a media direction requires the physical two-phone criteria in `docs/POC_AUDIO_TEST_PLAN.md`.
- Prefer `VOICE_DOWNLINK` for RX.
- First TX candidate is `USAGE_MEDIA -> TYPE_TELEPHONY`; compare `USAGE_VOICE_COMMUNICATION` only if needed.
- Do not copy AGPL/GPL implementation code from research references.

---

### Task 1: Freeze the pre-call baseline and active-call metadata contract

**Files:**
- Modify: `app/src/main/java/pl/michalmatu/aicallbridge/ShellAudioProbe.java`
- Create: `app/src/test/java/pl/michalmatu/aicallbridge/ProbeArgumentsTest.kt`
- Modify: `docs/S22_BASELINE_2026-09-14.md` only after the live test if firmware metadata differs

**Interfaces:**
- Consumes: existing shell probe entry point `pl.michalmatu.aicallbridge.ShellAudioProbe`
- Produces: deterministic command modes `inventory`, `capture-downlink`, and `inject-tone`, plus explicit argument validation

- [ ] **Step 1: Write the failing argument-parsing tests**

Create tests that prove invalid duration/sample-rate/mode values are rejected and that the default `inventory` mode remains non-streaming.

Expected behaviors:

```text
no args                         -> inventory
capture-downlink 1000           -> accepted
capture-downlink 0              -> rejected
capture-downlink 30000          -> rejected by bounded-duration policy
inject-tone 400 1000 0.05       -> accepted
unknown-mode                    -> rejected
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ProbeArgumentsTest*'
```

Expected: failure because the parser/modes do not exist yet.

- [ ] **Step 3: Implement the smallest parser/mode dispatch**

Keep parsing pure/testable. Do not start any audio object while parsing.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the same Gradle command and require PASS.

- [ ] **Step 5: Run the normal debug build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

Commit only the mode/argument contract before adding media behavior.

---

### Task 2: Add bounded `VOICE_DOWNLINK` capture with objective metrics

**Files:**
- Modify: `app/src/main/java/pl/michalmatu/aicallbridge/ShellAudioProbe.java`
- Create: `app/src/main/java/pl/michalmatu/aicallbridge/PcmMetrics.java`
- Create: `app/src/test/java/pl/michalmatu/aicallbridge/PcmMetricsTest.kt`

**Interfaces:**
- Consumes: `capture-downlink <durationMs>` mode from Task 1
- Produces: bounded PCM16LE byte stream/file plus metrics: requested duration, bytes read, non-zero sample count, RMS, peak, read errors, actual routed device when available

- [ ] **Step 1: Write failing PCM metric tests**

Use fixed PCM fixtures with known silence/non-silence, peak, and RMS behavior. Test only pure metric code.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*PcmMetricsTest*'
```

Expected: fail because `PcmMetrics` does not exist.

- [ ] **Step 3: Implement `PcmMetrics` minimally**

Requirements:

```text
encoding: signed PCM16 little-endian
channels: mono
sample rate: 16000 Hz for first physical test
bounded duration: <= 5 s
```

- [ ] **Step 4: Verify GREEN**

Run the focused test, then the full app unit-test suite.

- [ ] **Step 5: Implement bounded capture**

For `VOICE_DOWNLINK`:

1. create `AudioRecord`;
2. require `STATE_INITIALIZED`;
3. call `startRecording()`;
4. read only until the requested bounded duration/sample count;
5. emit/report actual read count and metrics;
6. stop and release in `finally`;
7. fail closed on partial setup.

The probe must not silently reinterpret an exception as zero audio.

- [ ] **Step 6: Build and run an off-call negative/control check**

Run through Local Agent with no active call. Record constructor/start/read behavior but do **not** classify the media direction from this result.

- [ ] **Step 7: Commit**

Commit the bounded capture probe and metrics before the physical call test.

---

### Task 3: Physically prove Phase 1A during a live call

**Files:**
- No production-code changes unless the probe itself exposes a defect
- Update: `docs/S22_BASELINE_2026-09-14.md`
- Update: `docs/RESEARCH_NOTES.md`
- Update: `docs/ROADMAP.md`
- Comment: GitHub Issue #1

**Interfaces:**
- Consumes: bounded downlink probe from Task 2
- Produces: `PROVEN_S22`, `AVAILABLE_UNPROVEN`, or `FAILED_S22` evidence for downlink

- [ ] **Step 1: Establish the test call**

Use the S22+ dedicated SIM and a second consenting test phone. Start with earpiece route. Record call type if visible (cellular/VoLTE/Wi-Fi Calling).

- [ ] **Step 2: Run the deterministic remote phrase test**

Second phone repeatedly says:

```text
REMOTE ALPHA 1 2 3
```

S22+ user remains silent.

- [ ] **Step 3: Capture 1–3 seconds of `VOICE_DOWNLINK`**

Use Local Agent/ADB to invoke the bounded probe. Keep any raw artifact outside Git and redact sensitive metadata.

- [ ] **Step 4: Verify objective signal first**

Require:

- expected byte/sample count or clearly documented partial-read result;
- non-zero RMS/peak above silence floor;
- no capture exception;
- call remains active.

- [ ] **Step 5: Verify intelligibility/source direction**

Confirm `REMOTE ALPHA 1 2 3` is present. Then repeat while the S22+ user says `LOCAL BRAVO 4 5 6` and classify remote-only/local-only/mixed.

- [ ] **Step 6: Update durable evidence immediately**

Do not defer documentation. Record exact git SHA, build fingerprint, route, PCM format, result, and limitations.

- [ ] **Step 7: Commit evidence classification**

If the test is inconclusive, mark it inconclusive; never promote from constructor success.

---

### Task 4: Add deterministic low-amplitude telephony TX tone generation

**Files:**
- Modify: `app/src/main/java/pl/michalmatu/aicallbridge/ShellAudioProbe.java`
- Create: `app/src/main/java/pl/michalmatu/aicallbridge/ToneGenerator.java`
- Create: `app/src/test/java/pl/michalmatu/aicallbridge/ToneGeneratorTest.kt`

**Interfaces:**
- Consumes: `inject-tone <durationMs> <frequencyHz> <amplitude>` mode from Task 1
- Produces: deterministic PCM16 fixture and bounded `AudioTrack` attempt routed to the telephony sink

- [ ] **Step 1: Write failing tone tests**

For a 1 kHz, 400 ms, 16 kHz mono fixture at 5% full-scale, test:

- exact sample count: 6400;
- maximum absolute sample does not exceed configured amplitude;
- signal is non-zero;
- invalid amplitude/frequency/duration is rejected.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*ToneGeneratorTest*'
```

- [ ] **Step 3: Implement minimal tone generation**

No file decoding, codecs, or TTS. Generate PCM mathematically.

- [ ] **Step 4: Verify GREEN**

Run focused and full unit tests.

- [ ] **Step 5: Implement the live-call TX probe**

Primary path:

```text
AudioAttributes.USAGE_MEDIA
PCM16 / 16 kHz / mono
AudioTrack MODE_STREAM
setPreferredDevice(TYPE_TELEPHONY sink)
play()
write complete bounded fixture
verify playback head advances
release in finally
```

Report separately:

- track construction;
- `STATE_INITIALIZED`;
- preferred-device result;
- actual routed device if available;
- write count;
- playback head;
- exception.

Do not play the tone through the ordinary speaker as a fallback.

- [ ] **Step 6: Add comparison mode only if primary construction/routing fails**

Comparison attributes may use `USAGE_VOICE_COMMUNICATION`, but preserve the primary result. Do not hide one route behind automatic fallback.

- [ ] **Step 7: Commit**

Commit the deterministic TX probe before the physical remote-party test.

---

### Task 5: Physically prove or reject Phase 1B

**Files:**
- Update: `docs/S22_BASELINE_2026-09-14.md`
- Update: `docs/RESEARCH_NOTES.md`
- Update: `docs/ROADMAP.md`
- Update: `docs/IMPLEMENTATION_PLAN.md`
- Comment: GitHub Issue #2

**Interfaces:**
- Consumes: bounded TX probe from Task 4
- Produces: physical verdict for generic stock-Samsung injection

- [ ] **Step 1: Start the live cellular call**

Keep the second phone physically separate enough to rule out acoustic leakage.

- [ ] **Step 2: Run the `USAGE_MEDIA -> TYPE_TELEPHONY` tone**

Use 1 kHz / 400 ms / 5% amplitude unless the phone route requires a lower safe level.

- [ ] **Step 3: Require remote observation**

The second-phone operator explicitly reports whether the beep is heard through the call.

- [ ] **Step 4: Verify no acoustic source**

Confirm the S22+ external speaker did not audibly play the fixture as the mechanism.

- [ ] **Step 5: Stop injection and speak normally**

Verify normal human speech resumes without redialing.

- [ ] **Step 6: Repeat at least three start/stop cycles**

A one-off success is not yet stable enough for Phase 2.

- [ ] **Step 7: Classify**

Only remote audible digital delivery can be `PROVEN_S22`. If `AudioTrack` still cannot be created while in-call, preserve the exact exception and move to the Phase 1C decision instead of guessing.

- [ ] **Step 8: Commit evidence**

Update docs/issues in the same work session while the conditions are known.

---

### Task 6: Gate the next architecture decision

**Files:**
- Modify only documentation/status files unless both directions passed

**Interfaces:**
- Consumes: physical Phase 1A and 1B evidence
- Produces: one explicit next state

- [ ] **Step 1: If both RX and TX pass**

Write the next Superpowers plan for Phase 2 local full-duplex bridge. Do not start OpenAI yet.

- [ ] **Step 2: If RX passes and generic TX fails**

Write a separate Phase 1C Samsung-specific research design/plan. Keep vendor research isolated from generic audio code.

- [ ] **Step 3: If RX itself fails**

Use systematic debugging to separate permission, start, read, route, call-type and firmware causes before changing architecture.

- [ ] **Step 4: Run final repository verification**

At minimum:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Then inspect the Local Agent result and Git diff before declaring the phase complete.

- [ ] **Step 5: Finish/merge the implementation branch**

Merge only after tests and physical evidence match the documentation. Preserve `agent-control` separately.
