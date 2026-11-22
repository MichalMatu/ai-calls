# Bezpieczeństwo

- Exact Call/SIM/generacja; STOP/takeover odwołują model/TX. END_CALL po TTS i autoryzacji.
- Binder UID 2000 i Kadb: weryfikacja tożsamości, lokalne ADB, wymóg IDLE i zgody. Bez Shizuku/`WRITE_SECURE_SETTINGS`.
- Parowanie kodem jednorazowym przez prywatne powiadomienie lub ręczne pole; bez logowania kluczy/kodów. Reboot może wymagać zgody.
- Model proponuje, nie autoryzuje; `CallCommitmentGate` i `FactDisclosurePolicy` ograniczają działania i ujawnienia. Notatki szyfrowane, z możliwością wyłączenia i filtrowaniem sekretów.
- WAV, PII i tokeny poza Git/logami; nagrania zależą od wyboru użytkownika. Bez `FLAG_SECURE`, automatów dzwoniących, schedulerów i Actions.
