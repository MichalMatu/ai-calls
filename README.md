# Android AI Call Bridge

Experimental Android project for testing whether a single Android phone can bridge a normal cellular call to a realtime AI voice session without external hardware.

## Goal

The target UX is simple:

1. Place or receive a normal phone call.
2. Let the app obtain the remote caller audio digitally.
3. Stream that audio to a realtime AI session.
4. Receive AI audio back with low latency.
5. Inject the AI audio into the call uplink.
6. Allow the user to take over instantly at any time.

The project deliberately separates **what Android publicly supports** from **device-specific or privileged experiments**. Nothing in this repository assumes that cellular downlink capture or uplink injection is available to a normal third-party app until it has been proven on the target device.

## Target device

Initial hardware target: Samsung Galaxy S22+.

## Development rule

**Do not build the AI layer first.** The first milestone is an audio-only proof of concept:

- prove remote-call audio capture;
- prove deterministic audio injection into the call uplink;
- measure latency, stability and routing behavior;
- only then connect a realtime model.

## Repository layout

- `app/` — Android application shell and user controls.
- `audio-bridge/` — interfaces and experimental audio backends.
- `realtime-client/` — realtime AI transport abstraction; kept independent from Android telephony experiments.
- `privileged-helper/` — placeholder for experiments requiring shell/system privileges; not part of the normal app path.
- `docs/` — architecture, research notes, implementation plan and device test protocol.

## Current status

Milestone 0: repository bootstrap and technical validation plan.

The key unknown is Android cellular-call audio access. Public Android APIs reserve `VOICE_UPLINK` and `VOICE_DOWNLINK` capture behind `CAPTURE_AUDIO_OUTPUT`, which is not available to ordinary third-party applications. MediaProjection playback capture also does not provide a general way to capture voice-communication audio. Therefore the project treats Samsung/system-specific or privileged access as an experiment, not an assumption.

## Safety and privacy

This project handles live call audio. Any implementation must make recording/AI participation explicit to the user, minimize retained data, protect API credentials, and comply with applicable call-recording and consent laws.

## License

No license has been selected yet.
