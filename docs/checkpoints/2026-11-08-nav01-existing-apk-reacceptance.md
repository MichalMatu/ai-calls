# AI Calls — NAV-01 resumed S22 acceptance (2026-11-08)

## Source and device
- Repository source at inspection: `main@fea65de7382fd75b2a77e5b1615aa74d470849d3`. Mac working checkout fast-forward/clean verified by `aicalls-nav01-safe-device-run-20261108-v2`.
- Real Samsung S22 (`SM-S906B`) reconnected via USB/ADB, call state IDLE, campaign inactive. AI Calls `0.2.0` installed. The newer USB Lab receiver was **not** present in installed package; this session did **not** install APK/test APK or change configurations.
- Existing installed AndroidTest used against existing installed app. Exact device APK-to-source build identity was **not** independently verified; do not claim this proves current source `fea65de` on device.

## New results
1. `aicalls-nav01-safe-device-run-20261108-v2` **DONE/0**: safe foreground AI Calls Models; `AndroidAppBottomNavigationFlowTest` **2/2 PASS**. It clicks the five non-Home bottom tabs from Home and checks destination presence, then opens General Settings from Settings hub and performs child-toolbar Back. Call IDLE and no active campaign in postcheck. This establishes a *scoped* device navigation result, not all of NAV-01.
2. `aicalls-nav01-secondary-readonly-matrix-20261108-v1` **FAILED**: preflight passed with Launcher foreground; `AndroidAppNavigationContractTest` ran **5 tests / 1 failure**. The task printed only the tail of the instrumentation failure, not the failing test identifier or assertion, so the cause is **UNDIAGNOSED**. `AndroidSettingsNavigationMatrixTest` was not started after this failure. Do not label this a source-code defect or PASS without isolated reproduction.
3. `aicalls-nav01-contract-failure-detail-20261108-v1` **REFUSED** before instrumentation: foreground was not eligible. Subsequent read-only diagnostic `aicalls-nav01-after-contract-fail-transport-20261108-v1` showed ADB connected, window focus `NotificationShade`, underlying focused app AI Calls and device `Dozing`. `aicalls-nav01-gated-wakeup-diagnostics-20261108-v1` safely woke the screen when Call was IDLE and underlying focused app AI Calls; `NotificationShade` remained focused. A single guarded Back via `aicalls-nav01-dismiss-notification-only-20261108-v1` did not remove it. **No further UI testing** or forced unlocking.

## Decision
- **NAV-01 remains PARTIAL**, with a new targeted 2/2 device navigation PASS and a separate **5-run/1-failure** contract result requiring triage.
- Unverified: full selected-state, back stack, duplicate Activity behavior, persistence and restart; `AndroidSettingsNavigationMatrixTest` remains NOT_RUN in this resumed run.
- No real outgoing/incoming calls, model inference, downloads, wipe, preference changes, or private-data export. USB Lab new receiver / takeover and LIVE Call remain NOT_RUN.
- This is QA evidence, **not** a verified product defect. If a specific app violation is reproduced, register it in `BLOCKERS.md` before fixing.

## Next safe step
1. Keep Samsung screen awake/unlocked with AI Calls or Launcher on top; do not displace PocketHost or bypass the lock screen.
2. Check device APK and AndroidTest build identity against source, then run a *scoped diagnostic* of the failed assertion, capturing only test name/exception (no private content). Run `AndroidSettingsNavigationMatrixTest` separately if preflight remains eligible.
3. Complete NAV-01 back stack / restart in the accepted device scope; record result by exact test and update functional matrix. Only then progress to HOME or USB Lab physical acceptance.
