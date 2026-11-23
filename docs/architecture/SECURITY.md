# Security

- Check the exact Call, SIM and generation. STOP and takeover revoke model and TX authority. Hang up only after TTS and authorization.
- The Binder host runs as shell UID 2000. Local ADB setup checks device identity, IDLE and user consent. Shizuku and `WRITE_SECURE_SETTINGS` are outside the MVP.
- Enter the one-time code through the private notification or the manual code field. Keep codes and keys out of logs. A reboot may require reconnecting.
- The model proposes actions. `CallCommitmentGate` and `FactDisclosurePolicy` enforce execution and disclosure limits. Notes are encrypted and subject to secret filtering.
- Keep personal data, WAV and tokens outside Git and logs. WAV recording depends on the user's choice. Keep screenshots available. No `FLAG_SECURE` in app windows.
