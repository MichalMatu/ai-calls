# AI Calls — modular USB/ADB diagnostic bridge

## One path, no duplicate state

The owner-facing screen is **Main → Diagnostyka developerska → USB / ADB — diagnostyka**.
The app's existing DiagnosticProbeActivity (shell-only android.permission.DUMP)
dispatches usb_debug_action to debug/UsbDebugBridge. The same sanitized snapshot
is shown by UsbDebugPanelActivity. There is **no second grant, audio, or evidence store**.

From the selected Mac, run the single entry point from an exact source checkout:

The current development executor is local Codex (owner decision, 2026-11-04).
Run this console directly from the local checkout. Codex must not dispatch
Local Agent tasks; that execution mode is reserved for ChatGPT sessions.
Check for competing Local Agent/device work before tests or mutations. Opening
and navigating the application through ADB does not require operator clicks.

    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py status
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py errors
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py evidence-status
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py profile-check --input PARAKEET_PL
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py profile-set --input PARAKEET_PL
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py profile-reset
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py grant-revoke
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py evidence-disable
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py evidence-export
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe android-stt
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe android-tts
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe gemma-e2b
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe gemma-audio
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe parakeet-pl
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe parakeet-quality
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe gemma-audio-pl
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe piper-pl
    python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py probe usb-panel

The CLI detects the actual ADB transport, checks **SM-S906B / RFCT70L7E8J**,
and never uses an old IP/port. The app's DUMP-protected bridge writes one
nonce-bound JSON response under its private files/usb-debug-bridge/response.json;
only adb shell run-as can read it. The private audit file stores action/result
codes, never transcripts, personal identifiers, grant contents, or WAV bytes.

The three quality probes share `AndroidPolishAudioQualityTest`: three fixed
synthetic Polish phrases, actual PCM input, timing, word/character edit counts
and final callback counts. Piper output uses the actual service and Parakeet
recognition. Only recognized numeric `audio_quality_metrics` reach the console;
transcript and unknown fields are excluded. WER/CER describe this synthetic set,
not a natural corpus. Synthetic-only details can be kept in private app cache;
these tests never use real call material.

## Modules and responsibilities

| Module | Reused source of truth | Behavior |
|---|---|---|
| status / evidence-status / errors | profile store, grant store, private evidence directory | Redacted health, call state, evidence count and log severity counts |
| profile-check | AudioEngineReadinessProbe | Offline TTS voice/model probe; no save |
| profile-set / profile-reset | AudioEngineProfileStore + existing readiness and registry | Validated, off-call profile choice for next call |
| grant-revoke | CampaignAuthorizationStore.revoke | Revoke existing grant, never issue/widen one |
| evidence-disable | CampaignAuthorizationStore.disableEvidenceRecording | Narrow recording authority |
| evidence-export | Existing export_private_call_evidence.sh | Local Mac-only private archive; no new ZIP/UI implementation |
| probe (Android STT/TTS, Gemma E2B/Audio, Parakeet, UI) | Existing Android instrumented tests | One synthetic off-call test after test APK and safety preflight |

Mutations, exports and instrumentation require exact hardware, twice IDLE,
dialer role, no AppOwnedCampaignService, no pending retry and working run-as.
Profile changes are rejected without actual offline model/voice readiness.
Before/after fingerprint comparisons verify no unexpected grant/skills/audio edits.

**No direct raw SharedPreferences replacement is supported.** In particular,
ADB cannot create or widen grants, edit authorized targets/effects, disable
STOP, bypass the app's output/fact policies, unlock disabled M4, or place calls.
That would turn a diagnostic channel into a bypass of application-owned
authorization. Owners can edit a real grant through the existing application
authorization flow; the USB console supports immediate revocation, not forgery.

## Privacy and execution boundaries

- The private exporter only writes to ~/Documents/AI-Calls/private-exports
  with restricted permissions. Mac Local Agent results may contain sanitized
  PASS/FAIL codes; **never publish the actual archive** to GitHub,
  task-results, logcat or an issue.
- PROVEN_HOST is not S22 off-call acceptance; neither is a live call result.
- The bridge is intended for the existing debuggable application with
  DUMP-only exported diagnostic activity and owner-controlled ADB; never
  add a general externally exported mutable provider or arbitrary shell API.
- For live call testing the exact, revocable, app-stored grant and Android
  Telecom ownership checks remain independently required.
- Pin the source SHA and verify APK signing lineage before phone installation.
  Preserve stable app data and private grant fingerprint.
