# Bezpieczeństwo

- Exact `Call`/SIM/generacja; **STOP** i takeover odwołują STT, model i TX. `IncomingEndCallAuthority` potwierdza uprawnienie; `END_CALL` dopiero po TTS.
- Binder host UID 2000: weryfikacja UID, sesji, watchdog i rozłączenia; bez fallbacku Shizuku w S8 Lab. Reboot bez Maca = brak hosta.
- `CallCommitmentGate`, `FactDisclosurePolicy`, `IdentityVault`: model proponuje, nie autoryzuje. `UNKNOWN` i TX nie potwierdzają rezerwacji.
- WAV/evidence prywatne, opt-in; bez PII/audio w Git, receipts i logcat. Bez `FLAG_SECURE`. Fizyczny Call/deploy wyłącznie za zgodą, przy IDLE i z rollbackiem.
