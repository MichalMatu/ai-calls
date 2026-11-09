# AI Calls — S22 NAV-01 and USB Lab acceptance, 2026-11-09

## Verified

- S22 `SM-S906B`, serial `RFCT70L7E8J`, authorized Mac Local Agent `macbook-michal`; current development on `main`, no new branch, no live Call.
- **NAV-01 scoped 10/10 physical PASS**, installation of app + AndroidTest without wipe: `aicalls-nav01-f151-s22-reacceptance-20261109-v1` DONE/0: `AndroidAppNavigationContractTest` 5/5, `AndroidAppBottomNavigationFlowTest` 2/2, `AndroidSettingsNavigationMatrixTest` 3/3; Call IDLE. F-151 VERIFIED (stale test expected PhoneEnrollment instead of IdentityVault); full Mac gate `aicalls-nav01-f151-targeted-mac-build-20261109-v1` DONE/0.
- **USB receiver Android contract 3/3 PASS**, `aicalls-usb-device-readonly-contract-20261109-v1`: DUMP/debug receiver, safe off-call status and negative intervention, invalid configuration has no mutation. The *same* first attempt found an independent real Mac CLI write failure.
- **F-152 VERIFIED**: raw adb `sh -c` lost a complete command; shell quoting fix `main@39984125`, regression `main@b87456e`, full Mac gate `aicalls-usb-f152-mac-host-qa-20261109-v1` DONE/0 and real S22 CLI `aicalls-usb-f152-s22-cli-reacceptance-20261109-v1` DONE/0: `status`, `config-get`, `ram` 3/3 PASS; phone IDLE, no data export.
- **F-153 Mac HOST_PASS, physical OPEN**: private USB Lab `lab-start` for E2B 4096 succeeded on the S22, but `lab-send` had no stdin because inherited ADB commands consumed it. `aicalls-usb-cli-stdin-consumption-diagnosis-20261109-v1` verified `adb shell getprop` consuming 20/20 synthetic bytes. Fix `main@a2728127` makes read-only ADB invocations `stdin=DEVNULL`; test `main@00078e12`; `aicalls-usb-f153-mac-host-qa-20261109-v1` DONE/0 (6+5 focused tests and full host gate PASS).
- Physical F-153 reacceptance `aicalls-usb-lab-two-turn-native-s22-20261109-v2` **REFUSED at foreground gate before START**. Read-only `aicalls-usb-f153-s22-foreground-check-20261109-v1` DONE/0 confirmed PocketHost `pl.pockethost.companion` foreground/focus, screen awake. No app takeover. First `v1` remains FAILED with no generated turns; its private Lab STOP succeeded, Call IDLE.

## Open and limits

- **NAV-01 PARTIAL**: repeat tab/back-stack/duplicate Activity behavior and restart session preservation not fully proven. Retain 10/10 scoped PASS; do not restage those tests without an independent reason.
- **USB Lab PARTIAL**: native `START` and `STOP` observed, but fixed `SEND` and two generated turns not yet accepted. F-153 DEVICE_QA_BLOCKED_FOREGROUND. Independent shared-backend GUI/USB race remains F-150 DEVICE_OPEN.
- **Call interventions NOT_RUN**: no positive takeover/resume/exact owned STOP, no real incoming/outgoing call, no authorization bypass. F-148 semantic review and 8192 trial also remain OPEN/NOT_RUN.
- No phone call, disclosure of owner identity, private vault changes, new grant, model download, or private WAV/transcript publication occurred in this session. Diagnostic script did not produce a successful lab-save from the failed test.

## Next safe step

With AI Calls or Android Launcher unlocked and focused, verify S22 IDLE/no campaign, retry **only** `lab-start` → two neutral `lab-send` messages through the **fixed** CLI → per-turn `lab-status` → private `lab-save` → exact session STOP. Avoid interference with PocketHost; only then mark F-153 VERIFIED / USB Lab two-turn PASS. Real Call tests are separately authorized scope.
