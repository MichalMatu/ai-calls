# Bezpieczeństwo

- Tylko exact app-owned Call i grant z numerem/SIM/scope; bez globalnego hangup.
- STOP/TAKE OVER odcina stare STT/model/TTS/TX przed cleanup; resume jawnie.
- `CallCommitmentGate`, `FactDisclosurePolicy`, `IdentityVault`: model nie autoryzuje. UNKNOWN i TX nie potwierdzają rezerwacji.
- WAV na opt-in, STT/historia prywatne. Bez PII/audio w Git/receipts/logcat.
- Zamrożony Samsung PCM/Shizuku wymaga fizycznego QA po zmianie. Realny Call: zgoda, IDLE, sprawdzony S22. Bez `FLAG_SECURE`.
