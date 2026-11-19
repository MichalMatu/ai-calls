# Bezpieczeństwo

- Exact Call/SIM/generacja; STOP i takeover odwołują STT/model/TX. END_CALL tylko po TTS i `IncomingEndCallAuthority`.
- Binder host UID 2000: weryfikacja UID/sesji, watchdog. Kadb tylko `127.0.0.1`, stałe polecenia, model S22+, Android 16 i IDLE; bez Shizuku w GSM lab.
- ADB: klucz w prywatnym no-backup, bez PII/logów; zapisany klucz nie dowodzi aktualnego połączenia. Kod parowania: jednorazowe prywatne powiadomienie. Debugowanie po reboocie może wymagać ręcznej zgody.
- `CallCommitmentGate`, `FactDisclosurePolicy`, `IdentityVault`: model proponuje, nie autoryzuje. `UNKNOWN` ≠ potwierdzenie.
- WAV opt-in, żadnych kluczy/audio w Git/receipt. Bez FLAG_SECURE. Fizyczny Call/deploy tylko IDLE, za zgodą i z rollbackiem. Bez schedulerów/`WRITE_SECURE_SETTINGS` w MVP.
