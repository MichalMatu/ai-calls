# AI Calls — handoff, 2026-11-01: corrective safety hold

Do not assume HEAD; check fresh `origin/main` at every session. Durable changes belong on `main`; `agent-control` is transport only. Read `AGENTS.md`, `README.md`, `docs/CORRECTION_CAMPAIGN_2026-11-01.md`, `docs/REAUDIT_2026-11-01.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`.

## Current verified status

2026-11-01 corrective source changes on `main`: negation-safe provisional CLIR carrier evidence, removal of supervisor-based success token, atomic in-process grant reservation/revocation, live authorization watchdog and pre-disclosure/commit/PCM checks, conservative single-default voice phone account binding, and removal of unsafe broad `TelecomManager.endCall()`.

**Safety block:** current app-owned CLIR dial is intentionally disabled before any call, because no mechanism to identify and selectively terminate **only** an app-owned call exists. Do not remove the guard or use generic `isInCall()`/global `endCall()` as ownership proof. There is no verified autonomous physical CLIR feature at this checkpoint.

**Validation:** standalone `kotlinc`/JVM classifier harness passed 14/14 positive+negative cases after the Polish letter `ł` fix. Host Gradle suite, Android build, Android instrumentation concurrency tests, actual S22 media/readiness, SIM selection, real call and independent `*#31#` have **not** run or been accepted.

## Next steps

1. Obtain a fresh current-chat Local Agent binding, confirm daemon and task state, clean checkout, latest main and physical S22 condition. Do not infer present daemon health from old `.agent/status`.
2. Run `./gradlew testDebugUnitTest`, Android build and safe instrumentation with the new grant race test. Repair observed compile/regression failures; no GitHub Actions, Codex or Work.
3. Implement/prove a real Android exact-call owner with selective hangup; preserve the normal default dialer and unrelated calls. Only then remove the fail-closed dial block on evidence.
4. Verify Shizuku version-specific binder/startup path and phone `IDLE` using current device facts.
5. With current exact application authorization and confirmed call ownership, physically test one Orange `*100` interaction; accept only carrier factual evidence **plus independent `*#31#`**, safe own-call cleanup to `IDLE`, and sanitized evidence.
6. Update ROADMAP, evidence and handoff after physical tests. No unverified success claims.

## Historical context (not refreshed today)

At the 2026-10-08 stop the S22 had Shizuku Manager 13.6.0 but readiness failed `shizuku_binder_unavailable`; historical `start.sh` was missing. The independently checked CLIR state was not restricted. No new physical state can be inferred from the 2026-11-01 source-only campaign. PHONE vault and permissions were present during an earlier preflight; do not leak plaintext to Git, logs, model or relay.

Do not revive the unaccepted debug one-shot PR #21. Frozen media, privilege and identity architecture stay closed absent a concrete failure.

## Logiczny reaudyt (2026-11-01, późniejszy checkpoint)

Zobacz `docs/LOGICAL_ARCHITECTURE_AUDIT_2026-11-01.md`. Sam szkielet autoryzacji i odpowiedzialności jest sensowny, ale aktualny kod **zawsze odrzuca dial** przez stałe `CampaignCallOwnershipProof(false,false)`; to nie jest dynamiczny test własności. Nie usuwać blokady mechanicznie bez konkretnego kontrolera/cleanup własnego połączenia. Pozostałe odkrycia: nadmiar `accounts.size==1`, pominięcie deterministycznego `PhraseMatrix` w kampanii, `generation=0L` zamiast sesji, pozytywny matcher dopuszcza pytania, brak niezależnej weryfikacji/terminalnego sukcesu i wymaganie uprawnień do połączenia przy samym tworzeniu grantu. Ten dokument jest **wynikiem audytu**, nie listą napraw już wdrożonych. Przed pracą sprawdzić świeży HEAD; bez połączeń bez ważnej autoryzacji.

## Sprint after logical re-audit (2026-11-01)

**Read first:** `docs/SPRINT_LOGIC_CORRECTIONS_2026-11-01.md`. The artificial constant ownership proof has been replaced by a typed exact-call lifecycle port, not a working Android adapter. The dial remains unavailable until a genuine call-specific controller is installed and verified. The sprint fixed PL question handling for CLIR, introduced neutral PhraseMatrix-first turns, bound PHONE/commit/TX to real media generation, improved route recognition, selected exact default SIM without excluding multiple accounts, deferred microphone/call permissions from grant creation and enabled provisional carrier evidence without another TTS turn. **27/27** standalone Kotlin/JVM carrier+route cases passed; app Gradle/Android/physical tests are **not run**. Do not misreport this as a live feature. Next: Local Agent build/tests -> genuine call controller -> physical S22/Orange/CLIR acceptance.


## Sprint Wireless ADB + telephony safety (2026-11-01)

**Read `docs/SPRINT_WIRELESS_ADB_CALL_SAFETY_2026-11-01.md`** before S22 work. Last source changes correct `device_readiness.sh` wireless-vs-USB detection, add verified mDNS discovery, remove developer-runner `KEYCODE_ENDCALL` / `cmd telecom end-call`, disable automatic old `clir_iteration.sh`, make `relay-live` and Phase1 runner attach-only, and stop forcibly disabling Bluetooth on externally owned calls. 12 standalone discovery smoke and 12 static source checks passed; full repo host/Android/physical checks are still **not run**. Android call controller **still has no real adapter**, so no autonomous real call should be claimed. Resume from fresh main, then host tests + read-only phone preflight, not legacy dial.
