# Galaxy S22+ Phase 1C remote uplink proof — 2026-09-16

## Classification

`PROVEN_S22`

The stock Galaxy S22+ `SM-S906B` / Android 16 / One UI 8 build can inject digital PCM into the cellular uplink from the direct shell UID 2000 privilege boundary using Samsung's CALL_ASSISTANT path.

Proven route:

```text
USAGE_CALL_ASSISTANT (system usage 17)
  -> AUDIO_STREAM_CALL_ASSISTANT
  -> incall_music_uplink
  -> AUDIO_DEVICE_OUT_TELEPHONY_TX
```

Generic `USAGE_MEDIA` and `USAGE_VOICE_COMMUNICATION` telephony-output constructors remain `FAILED_S22` for the tested live-call conditions.

## Local route evidence

During a real Orange carrier call the PCM leg reported:

```text
uid=2000
context_package=com.android.shell
context_op_package=com.android.shell
context_attribution_package=com.android.shell
permission_MODIFY_AUDIO_ROUTING=granted
permission_MODIFY_PHONE_STATE=granted
audio_mode=2
attributes_system_usage=17
track_state=1
play_state=3
routed_device=id:11,type:18,product:SM-S906B
route_guard=telephony_confirmed
sample_rate=16000
channels=stereo
```

Audio policy/logcat simultaneously selected:

```text
AUDIO_STREAM_CALL_ASSISTANT
AUDIO_DEVICE_OUT_TELEPHONY_TX
output 45
selectedDeviceId 11
incall_music_uplink
```

The deterministic DTMF-1 fixture was `697 Hz + 1209 Hz`, 300 ms, amplitude `0.10`. The complete write succeeded and the playback head reached the expected frame count.

## Remote behavioral proof

The final proof used three otherwise equivalent calls to the same Orange IVR. Before every leg, local `STREAM_VOICE_CALL` and `STREAM_DTMF` were explicitly muted (`Muted: true`), eliminating the phone speaker as an acoustic proof path.

After a fixed IVR timing point, `VOICE_DOWNLINK` captured the remote response for three conditions:

```text
no input:
  rms=1276.03
  peak=19833

native dialer DTMF 1:
  rms=3.71
  peak=26

CALL_ASSISTANT PCM DTMF 1:
  rms=17.03
  peak=319
```

Pairwise mean absolute difference:

```text
no-input vs native-DTMF = 317.72
no-input vs PCM-DTMF    = 320.48
native-DTMF vs PCM-DTMF =   5.58
```

The native DTMF and injected PCM legs therefore drove the remote IVR into the same downstream state, while the no-input control remained in a clearly different state. This closes the remote audibility gate without relying on local route state alone.

Speech transcription was unavailable because macOS Speech authorization was still `not_determined`; it is not needed for this verdict because the native-DTMF leg is the positive remote reference and the no-input leg is the negative control.

## Related classifications

- `VOICE_DOWNLINK`: `PROVEN_S22` for remote cellular downlink PCM on this build.
- generic telephony TX constructors: `FAILED_S22` for tested candidates.
- CALL_ASSISTANT / `incall_music_uplink`: `PROVEN_S22` for remote cellular uplink injection.
- direct shell UID 2000 privilege boundary: physically proven.
- Shizuku UserService parity: **not yet proven** and must be tested separately.

## Phase transition

Phase 1 cellular media gates are complete on the raw shell path. The next work is Phase 2: move the proven capture and injection mechanisms behind `privileged-helper`, stream PCM through file-descriptor pipes, implement `abortNow()`/watchdog cleanup, and prove a stable local bridge before enabling realtime AI networking.
