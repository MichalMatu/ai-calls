# Privileged Helper Experiments

This directory is intentionally isolated from the normal Android application.

Use it only for experiments that cannot be implemented with public third-party Android permissions, for example testing whether a shell/system-privileged process can access call uplink/downlink audio on the target Samsung firmware.

Every experiment added here must document:

- exact Android/Samsung build tested;
- exact privilege mechanism: ADB shell, Shizuku, root, system app, etc.;
- permissions/capabilities used;
- setup and teardown steps;
- whether it survives reboot;
- whether it changes device security state;
- physical two-phone test result;
- failure behavior.

Do not silently make the main application depend on this directory. A privileged path becomes a production candidate only after it is reproducible and its tradeoffs are explicitly accepted.
