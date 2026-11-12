# AI Calls — granice bezpieczeństwa

- **Exact app-owned Call:** każda operacja dial/answer/TX/DTMF/hangup wymaga aktualnego, dokładnego Call, numeru/SIM i scoped grantu; nigdy globalne rozłączanie ani wpływ na obcą rozmowę.
- **STOP/TAKE OVER:** odetnij STT/model/TTS i spóźnione callbacki przed cleanup; przejęcie nie rozłącza Call. Resume wyłącznie jawnie w tej samej autoryzowanej sesji.
- **Skutki:** `CallCommitmentGate`, `FactDisclosurePolicy`, `IdentityVault` i polityka grantu pozostają w aplikacji. Model, prompt, transkrypt i sam TX nie są autoryzacją ani potwierdzeniem rezerwacji. Rozróżniaj requested/dispatched/confirmed/UNKNOWN; efekt zewnętrzny wymaga niezależnego dowodu.
- **Prywatność:** numery, sekrety, prywatne WAV/STT i kontekst sejfu tylko w aplikacji. WAV na opt-in; tekstowa historia autoryzowanej rozmowy lokalna. Kontroluj zgodę/informowanie rozmówców. Żadnych prywatnych danych w Git, logcat, zadaniach hosta i telemetrycznych wynikach; eksport wyłącznie kontrolowany.
- **S22:** nie modyfikuj zamrożonego Samsung `VOICE_DOWNLINK`/`CALL_ASSISTANT`, Shizuku, generacji PCM ani selective cleanup bez dowodu na fizycznym S22. Bazowy kontrakt sprzętowy: `59b0505`.
- Prawdziwa operacja wymaga jawnego zakresu właściciela, zweryfikowanego urządzenia, permission i IDLE; niepewność blokuje wykonanie. Nie dodawaj sztucznego tarcia, ale nie usuwaj tych granic. Nie wymuszaj `FLAG_SECURE`.
