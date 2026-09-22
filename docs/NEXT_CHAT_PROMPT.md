# Next-chat prompt — Gate D Android IdentityVault + product integration

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego checkpointu Gate D.

Pracuj na branchu `gate-d-taskgraph-core`, PR #5. Najpierw pobierz świeży stan repo/PR i nie zakładaj, że SHA zapisane w tym promptcie lub starym czacie jest nadal HEAD.

Przed zmianami przeczytaj kolejno:

- `AGENTS.md`
- `README.md`
- `docs/HANDOFF_NEXT_CHAT.md`
- `docs/ROADMAP.md`
- `docs/ARCHITECTURE.md`
- `docs/GATE_D_TASKGRAPH_V1_AUDIT_2026-09-22.md`
- `docs/SECURITY_PRIVACY.md`
- `docs/HANDOFF_PROTOCOL.md`
- `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

Nie powtarzaj zakończonego preimplementation audytu ani spike `custom reducer vs KStateMachine`. Produkcyjnym core v1 pozostaje minimalny application-owned `CustomTaskGraphCore`.

Nie powtarzaj też zakończonych slice’ów: finalized-turn shadow lifecycle, `TaskGraphApplyBridge`, generic `AppointmentInterpreter`, sequence-level eval corpus ani categorical `DialogueFitHysteresis`.

Aktualny checkpoint ma już host-green:

- typed/replayable `CustomTaskGraphCore` + `BOOK_APPOINTMENT` simulator;
- `AuthorizedFactSnapshot` / `FactDisclosurePolicy`;
- generic appointment interpretation z `extract -> validate -> commit`;
- bounded shadow observation/hypothesis + fail-closed `SupervisorProposalValidator`;
- session-owned stale/cancel/close-safe shadow lifecycle;
- application-owned `TaskGraphApplyBridge` z pełnym re-check przed reducerem;
- categorical `DialogueFit` + evidence-backed hysteresis;
- deterministic sequence corpus dla recovery exhaustion, ambiguity, alternate offers, user rejection, unauthorized/high-sensitivity facts, cancel/takeover, stale supervisor i clean recovery;
- hostowy `PersistentIdentityVault` z versioned encrypted envelope/payload, AEAD portem, associated data, redacted secret wrapper, fail-closed decode i `DEVICE_BOUND_NO_BACKUP`.

Ostatni host-green checkpoint przed handoffem był `27457e3e103b89dac9f7e86a1b427f297128b7dc` (`Add encrypted IdentityVault persistence core`), Android CI #488 success. Traktuj to tylko jako historyczny checkpoint i zweryfikuj świeży HEAD.

## Pierwszy konkretny cel

Następny slice to **Android IdentityVault production adapter**.

Najpierw zrób wąski seam audit istniejącego `PersistentIdentityVault.kt` oraz wymagań w `docs/SECURITY_PRIVACY.md`. Bez broad refactoru. Następnie RED contracts / Android tests dla co najmniej:

1. app-private ciphertext storage z atomic write semantics;
2. non-exportable Android Keystore key;
3. authenticated encryption (AES/GCM, jeśli nie pojawi się konkretny platformowy powód inaczej);
4. key creation + reuse;
5. prawidłowego AAD/algorithm identity;
6. fail-closed corruption / unsupported record / missing-or-invalid key behavior;
7. explicit `DEVICE_BOUND_NO_BACKUP` semantics;
8. braku plaintext secrets w zwykłych diagnostics/logach.

Po udowodnionym RED zrób minimal GREEN, targeted regressions i pełny host/Android CI zgodnie z repo. Nie używaj nowych implementacji opartych o deprecated `EncryptedSharedPreferences` / `MasterKey`.

Storage nie przejmuje disclosure authority. Fakt zapisany w vault nadal musi przejść `AuthorizedFactSnapshot` + `FactDisclosurePolicy`; plaintext ma być rozwiązywany możliwie późno i nie może trafić domyślnie do supervisor/model context.

## Następny etap po vault

Dopiero po stabilnym Android IdentityVault przejdź do reviewed product shadow/apply integration w kolejności:

```text
finalized turn
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state/apply policy
 -> TaskGraphApplyBridge
 -> effects as data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

Nie twórz generic effect executora omijającego istniejących ownerów. Shadow/model/parser/reducer nie mogą przejąć authority nad dialem, target widening, plaintext disclosure, speech/TTS release, proposal approval ani commitment.

Frozen boundaries: nie ruszaj `privileged-helper/`, zamrożonego Samsung media path ani fizycznie sprawdzonego `CallMediaSessionCoordinator` bez osobnego root-cause i jawnej decyzji scope. Jeśli wróci `CallRealtimeMediaSessionTest.pumpFailure...`, najpierw sprawdź test ordering/synchronization; nie naprawiaj produkcyjnego media na ślepo.

Orange pozostaje persistent checkpointed ServicePack i nie jest teraz głównym celem.

Jeżeli używasz Local Chat Bridge / Local Agent, użyj wyłącznie świeżego binding envelope z bieżącego czatu, sprawdź fresh daemon/current-task state w dokładnie bound repo i nie kopiuj starego `agent_binding`. GitHub jest właściwy dla bounded reviewowalnych diffów, Local Agent dla lokalnych buildów/ADB/device evidence. Queue/ACK nie jest sukcesem — wymagaj terminal result.

Telefon może być użyty do testów Android/Keystore/ADB po świeżym bindingu, ale samo podłączenie telefonu nie jest zgodą na połączenie. Każdy fizyczny live-call wymaga świeżej, jawnej autoryzacji targetu i zadania w tej sesji.

Pracuj autonomicznie: fresh state -> seam audit -> RED -> potwierdzenie właściwego failure -> minimal GREEN -> regressions -> canonical verification -> aktualizacja authoritative docs/handoffu. Nie pytaj ponownie o decyzje już jednoznacznie zapisane w repo.
