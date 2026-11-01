# AI Calls — handoff, 2026-11-01: corrective safety hold

Do not assume HEAD; check fresh `origin/main` at every session. Durable changes belong on `main`; `agent-control` is transport only. Read `AGENTS.md`, `README.md`, `docs/CORRECTION_CAMPAIGN_2026-11-01.md`, `docs/REAUDIT_2026-11-01.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`.

## 2026-11-01 canonical Mac Host Control migration (LATEST)

**Start here:** `docs/LOCAL_AGENT_HOST_FLOW_2026-11-01.md` and the current installed `MichalMatu/local-agent` `docs/HOST_CONTROL_PLANE.md`. `ai-calls:agent-control/.agent/status/daemon.json` is **not** the Mac host-status source. New Mac host tasks belong to `MichalMatu/local-agent:agent-control/.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/tasks/`; corresponding `task-results/` and `status.json` prove execution. The old preflight queued to `ai-calls:.agent/tasks/` was sent to the legacy **project** route and does not prove current Mac connectivity. Fetch live host evidence, then run safe Wireless ADB and Android host tests. No calls until exact-call owner adapter + fresh grant.

## Exact-call Telecom feasibility sprint — 2026-11-01

Read `docs/EXACT_CALL_CAPABILITY_SPRINT_2026-11-01.md` after Host Flow. A new no-call `call-capabilities` CLI and negative test suite inspect default-dialer role, SDK, installed package, cellular IDLE and Shizuku process on verified S22. Actual Mac task `aicalls-exact-capabilities-s22-20261101-v1` returned `done/0`: Android 16, app installed, Shizuku visible, phone IDLE, role query unsupported/UNKNOWN, **no proven exact-call owner**. Final Mac Host Control receipt `aicalls-exact-capabilities-closeout-host-20261101-v2` is **PASS** at source `31bc42f` (199 Python tests, Gradle unit/lint/assembleDebug/AndroidTest APK). Final exact S22 no-call receipt `aicalls-exact-capabilities-final-s22-20261101-v1` is **PASS**: Android 16, `com.google.android.dialer` independently confirmed by RoleManager and Telecom, Shizuku permission/Binder and microphone ready, cellular `IDLE`. The command mismatch in the earlier diagnostic was corrected to `get-role-holders`. This closes only the **diagnostic** sprint. Real exact-call backend is still absent; never auto-change default dialer, call `*100`, or use global hangup.

## Current verified status

2026-11-01 corrective source changes on `main`: negation-safe provisional CLIR carrier evidence, removal of supervisor-based success token, atomic in-process grant reservation/revocation, live authorization watchdog and pre-disclosure/commit/PCM checks, conservative single-default voice phone account binding, and removal of unsafe broad `TelecomManager.endCall()`.

**Safety block:** current app-owned CLIR dial is intentionally disabled before any call, because no mechanism to identify and selectively terminate **only** an app-owned call exists. Do not remove the guard or use generic `isInCall()`/global `endCall()` as ownership proof. There is no verified autonomous physical CLIR feature at this checkpoint.

**Validation:** standalone `kotlinc`/JVM classifier harness passed 14/14 positive+negative cases after the Polish letter `ł` fix. Host Gradle suite, Android build, Android instrumentation concurrency tests, actual S22 media/readiness, SIM selection, real call and independent `*#31#` have **not** run or been accepted.

## Next steps

1. Select the exact installed Mac Host Control identity, read its current status and task results on `MichalMatu/local-agent:agent-control`, confirm clean checkout, latest main and read-only physical S22 condition. Do not infer present daemon health from old `.agent/status`.
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

## 2026-11-01 GitHub Agent transport / call cleanup checkpoint

Read `docs/SPRINT_AGENT_CONTROL_CALL_LIFECYCLE_2026-11-01.md`. New `agent-control` task `aicalls-sprint-host-phone-preflight-20261101-v1` (rev 2) queues Python+Gradle+safe ADB readiness, but **no run/result** was yet observed and daemon heartbeat remains 2026-10-19. Latest `main` fixes mDNS target service scoping, app call-session cleanup on exceptional paths, and pre-dial grant recheck. Standalone Python mock smoke passed 12/12; do not claim host/device GREEN. No real calls made.

## Live Mac Wireless ADB acceptance — 2026-11-01

See `docs/HOST_S22_MAC_ACCEPTANCE_2026-11-01.md`. New Mac host task `aicalls-mac-wireless-preflight-20261101-v1` ended `done/0` and proved live S22 through mDNS at `192.168.0.100:41703`, hardware model/serial and cellular `IDLE`. Two follow-up Mac host jobs (`aicalls-mac-host-quality-20261101-v1`, `aicalls-mac-s22-app-readiness-20261101-v1`) were queued but lacked terminal results at checkpoint time; do not claim Gradle/Shizuku green until independently read. App call owner remains missing.

## 2026-11-01 Shizuku restored / physical readiness PASS

Fresh actual Mac Host Control result `aicalls-shizuku-user-start-readiness-20261101-v1` is `done/0`: exact S22, wireless ADB, `shizuku_server`, granted microphone/Shizuku, `live_call_readiness=true`, `IDLE`. Clean Mac full host gate `aicalls-mac-host-quality-20261101-v2` is `done/0` (185 Python tests plus Gradle unit/lint/debug APKs) on `0ce0d658`. See `docs/HOST_S22_MAC_ACCEPTANCE_2026-11-01.md`. Only the **no-call gate** is accepted; exact-call Android adapter still absent. Do not dial or infer CLIR activation from these results.


## Supervised first-audio pilot — 2026-11-01 (latest source milestone)

Read `docs/SUPERVISED_FIRST_AUDIO_PILOT_2026-11-01.md`. `main@a503add6` adds a user-attached, two-turn deterministic read-only test for an existing ordinary phone call (start/stop the app's audio only, never hang up). **Host GREEN** on Mac task `aicalls-supervised-audio-host-20261101-v3` (199 Python + Gradle unit/lint/APKs PASS), S22 no-call Shizuku/IDLE preflight **PASS** on `aicalls-supervised-audio-s22-nocall-20261101-v2`, and exact APK install **PASS** on `aicalls-supervised-audio-safe-deploy-s22-20261101-v2`. Google Phone remains the default dialer. No real conversation has been run. Next is a human-controlled, consenting test call to validate the actual audio loop, followed separately by an exact-call controller engineering decision; **autonomous CLIR dialing is still blocked**. Do not interpret the supervised pilot as call ownership or authorization to dial.

## 2026-11-01 — pierwszy nadzorowany MVP (najnowsze)

Przeczytaj `docs/SUPERVISED_FIRST_CALL_MVP_2026-11-01.md`. Na `main@5cb13fc` dodano UI + neutralną lokalną 3-turową ścieżkę skryptową dla rozmowy rozpoczętej **ręcznie** w Google Phone, STOP wyłączający jedynie audio AI, bez dial/hangup oraz testy bramek. Mac Host Control `aicalls-supervised-mvp-build-20261101-v1` **PASS** (Android unit/lint/debug APK, 199 Python). **APK nie jest jeszcze zainstalowane na S22:** Wireless ADB straciło połączenie w trakcie `adb install`, wcześniejszy port nie odpowiada, mDNS pusty. Wymagane ponowne włączenie Wireless Debugging na odblokowanym S22, następnie bezpieczne `adb install -r` i UI smoke. Żadnej rozmowy nie wykonywano; autonomiczny exact-call controller nadal nie istnieje.


## Pierwszy nadzorowany MVP — instalacja S22 już POTWIERDZONA (2026-11-01, najnowsze)

Przeczytaj `docs/SUPERVISED_FIRST_CALL_MVP_2026-11-01.md`. Historyczny bloker Wireless ADB został rozwiązany. Na S22 przez Mac Host Control `aicalls-mvp-install-retry-20261101-v2` = `done/0`, APK `adb install -r` = `Success`. `aicalls-mvp-postinstall-ui-smoke-20261101-v1` = `done/0`, `MainActivity` = `Status: ok`, `VISIBLE_CONTROLS` zawiera `PIERWSZE POŁĄCZENIE — TEST NADZOROWANY`, Google Phone nadal default. Widoczne `MVP_BUTTON_PRESENT=false` jest fałszywą negatywną asercją testu (niedopasowana wielkość liter), **nie** brakiem przycisku. Host unit/lint/APK i 199 Python PASS na `5cb13fc`. Następnie test bezrozmownej odmowy pilota i świadomie autoryzowana ręczna rozmowa testowa; brak dowodu RX/STT/TTS/TX i brak autonomicznego dial/hangup.

## 2026-11-01 — supervised Gemma-first MVP deployed, no-call UI PASS (LATEST)

Read `docs/SUPERVISED_GEMMA_FIRST_CALL_MVP_2026-11-01.md`. Implemented a bounded local Gemma 4 E2B IT dialogue backend with a deterministic TTS release filter, fixed missing post-`ACTION_DIAL` operator-return flow (explicit attach confirmation), model-readiness UI, safe script fallback, and test coverage. Latest product source code commit `9017a12f1f85e2e2697ebcb923f462679cf6e5cb`. Mac exact-head gate `aicalls-gemma-supervised-host-20261101-v2` **done/0**: Android unit, Lint and debug/APK builds PASS, 199 Python PASS. Exact S22 model inspection **MISSING**, cellular `IDLE`. Device task `aicalls-gemma-supervised-deploy-s22-20261101-v1` successfully ran `adb install -r` and launched MainActivity, but its subsequent UI-smoke script failed due a literal `ANDROID_HOME` string; this is not an install failure. Separate corrected UI task `aicalls-gemma-supervised-ui-s22-20261101-v2` **done/0**: Gemma toggle, missing-model label, STOP AI visible; S22 remains `IDLE`. **Not yet proven:** actual Gemma on S22, user-controlled live STT/Gemma/TTS/TX; do not claim a completed conversation. Next is reviewed in-app 2.59 GB model install on Wi-Fi, then a user-consented manually initiated call and report/PCM verification. Google Phone remains default and autonomous exact-call ownership remains BLOCKED.
