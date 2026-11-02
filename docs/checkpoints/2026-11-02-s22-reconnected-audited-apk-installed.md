# AI Calls — S22 reconnected, audited APK installed, ready for manual call

Date: 2026-11-02. Mode: GitHub Connector + Mac Local Agent only; no Codex, Work or GitHub Actions.

## Verified source and hardware

- Current reviewed product code: `cce1fe99c7110f5c43a5f5406cdcae7b8238ed3e`. Deployment source `8ed01fa8a1feca1a867afe33db8410473fe6c1ce` differs from that code **only** in documentation, as confirmed by GitHub compare immediately before the deploy. The full offline diagnostics audit and focused new tests, app compile/lint and static safety checks were previously **PASS** on `cce1fe99`, receipt `aicalls-diagnostics-modular-audit-host-20261102-v2`. Do not rerun those without relevant new source changes.
- Exact device: Galaxy S22+ `SM-S906B`, serial `RFCT70L7E8J`. Physical ADB was available and reported `device`; no alternate/ambiguous device was selected.
- Mac Local Agent read-only identity preflight receipt `aicalls-s22-reconnect-readonly-20261102-v1`: **DONE/0**, `device`, model and serial matched, `mCallState=0`.
- Build/installation receipt `aicalls-s22-audited-apk-install-20261102-v1`: **DONE/0**, pinned source HEAD `8ed01fa`, `APK_BUILD=PASS`, immediately pre-install `mCallState=0`, Android `Performing Streamed Install / Success`, `APK_INSTALL=PASS`. Package name `pl.michalmatu.aicallbridge`. Preserving installed app data with `adb install -r`; no uninstall, reset, dial or hangup.
- Post-install readiness receipt `aicalls-s22-post-install-readiness-20261102-v1`: **DONE/0**, `mCallState=0`, installed package verified, `record_audio_granted=true`, `shizuku_binder_available=true`, `shizuku_supported=true`, `shizuku_permission_granted=true`, `live_call_readiness=true`. It ran the existing read-only, off-call readiness probe; no STT/TTS/Gemma full retest.
- The first broad `pm list packages -d` check emitted Android `SecurityException` for secondary user 151, so its apparent `NOT_DISABLED` findings **were not accepted**. A scoped `--user 0` check `aicalls-s22-google-speech-package-user0-20261102-v1` **DONE/0** confirmed `com.google.android.as=NOT_DISABLED_USER0` and `com.google.android.as.oss=NOT_DISABLED_USER0`. No package state was changed.

## Accepted and unaccepted

- **ACCEPTED / PASS:** source diagnostics and build/lint, exact device identity and IDLE, audited APK installed, permission/Shizuku readiness, Google speech support not disabled.
- **OPEN, NOT YET PASS:** actual active-call AudioManager mode, the previously observed `AUDIO_ROUTE_NOT_READY` during a carrier call, RX PCM, Polish STT, reviewed Gemma/script dialogue, TTS PCM, carrier TX audibility and acoustic STOP behavior with a consenting call partner.
- **BLOCKED by safety policy/architecture:** unattended dial/hangup, selecting `*100` or `510 100 100` via agent, changing CLIR/SIM/dialer, or bypassing `MODE_IN_CALL`. These remain separate from the supervised existing-call pilot.

## Next controlled action

Human starts a normal call themselves in Google Phone to a person **who expressly consents** to the AI audio test; no automated dial. While that call is in progress, return to AI Calls > **Pierwsze połączenie — nadzorowane**, tap **Uruchom AI w trwającej rozmowie** and approve the **Uruchom test** dialog (up to 3 effect-free turns; Gemma 4 if READY, else script). The app's 3 s mode transition retry records `files/supervised-call-readiness-report.txt` on a route problem. The audio runner writes `files/chat-relay-live-call-report.txt` on final session completion.

On failure or completion, human presses **STOP AI**; STOP stops only AI audio, never disconnects the carrier call. Human verifies far-end audibility, then ends the call manually. Mac Local Agent can read **only sanitized** report keys: readiness `telecom_in_call`, `telecom_managed_call`, `audio_mode`, `failure_reason`; runner `telephony_rx_pcm_bytes`, `turn_*_stt_chars`, `supervised_gemma_replies`, `supervised_script_fallback_replies`, `telephony_tx_pcm_bytes`, `turns_completed`, `chat_relay_live_call_success`, `failure_reason`. Never publish phone numbers, raw PCM, transcripts or arguments.

If Android mode still mismatches during a manual call, collect contemporaneous read-only call/audio status and app snapshot; do **not** replace the proven route gate until the discrepancy is established.

**Final state: APP INSTALLED + POST-INSTALL READINESS PASS. LIVE SUPERVISED CALL OPEN.**
