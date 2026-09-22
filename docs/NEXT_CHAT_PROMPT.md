# Next-chat prompt — Gate D S22 proof + bounded BOOK_APPOINTMENT integration

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego checkpointu Gate D.

Pracuj na branchu `gate-d-taskgraph-core`, PR #5. Najpierw pobierz świeży stan repo/PR i nie zakładaj, że SHA zapisane tutaj jest nadal HEAD.

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

Nie powtarzaj zakończonych audytów/spike’ów ani slice’ów: `CustomTaskGraphCore`, finalized-turn shadow lifecycle, `TaskGraphApplyBridge`, generic `AppointmentInterpreter`, sequence eval corpus, `DialogueFitHysteresis`, host `PersistentIdentityVault`, Android IdentityVault adapter ani reviewed product shadow/apply seam.

Historyczne host-green checkpointy do weryfikacji przez świeży HEAD:

```text
3b79d42012d80c7cc6956bac77f590bfae20dc72
Android IdentityVault adapter
Android CI #494: success
```

```text
a355f604484a78d7a99d7594455f3344ca081e04
reviewed Gate D product integration after constructor-only compile fix
Android CI #497: success
```

Android vault ma `noBackupFilesDir + AtomicFile`, Android Keystore AES-256/GCM, non-exportable key contract, create/reuse, AAD/algorithm identity i fail-closed missing/invalid-key semantics. Instrumentation tests są kompilowane/pakowane przez CI, ale nie zostały jeszcze fizycznie wykonane na S22 — to nadal `HOST_GREEN`, nie `PROVEN_S22`.

Reviewed product integration ma jawny internal binding:

```text
finalized turn
 -> deterministic interpretation first
 -> optional bounded shadow proposal
 -> SupervisorProposalValidator
 -> application-owned current-state / slot-authorization re-check
 -> TaskGraphApplyBridge
 -> effects as inert data
 -> existing workflow / proposal / confirmation / commitment / output owners
```

Deterministic rejection nie może fallbackować do shadow. Publiczne `LocalTextCallSession.create(...)` nadal nie aktywuje automatycznie shadow ani product apply bindingu. Nie twórz generic effect executora.

## Pierwszy konkretny cel

Po świeżym bindingu Local Chat Bridge / Local Agent wykonaj **S22 proof Android IdentityVault bez połączenia telefonicznego**.

Najpierw sprawdź fresh daemon/current-task state i exact bound repo. Następnie uruchom istniejący Android instrumentation contract na Samsung S22+ i zbierz terminal evidence dla:

1. app-private `noBackupFilesDir` ciphertext storage;
2. atomic replacement;
3. Android Keystore AES key creation + reuse;
4. non-exportability;
5. AES/GCM + AAD/algorithm identity;
6. fail-closed corruption / unsupported record / missing-or-invalid key;
7. `DEVICE_BOUND_NO_BACKUP` semantics;
8. braku plaintext secret w ordinary diagnostics/durable bytes.

Nie zmieniaj kodu tylko po to, żeby test przeszedł, jeśli failure jest środowiskowy. Najpierw root cause.

## Następny etap

Po udanym vault proof wykonaj Android/S22 integration proof reviewed product bindingu — nadal bez cellular call — dla deterministic-first order, generation/current snapshot progression, optional shadow, stale/cancel behavior, apply-time authorization re-check i braku automatycznego public wiring.

Dopiero potem przejdź do minimalnego `BOOK_APPOINTMENT` owner wiring. Mapuj konkretne effects do istniejących workflow/proposal/confirmation/commitment/output ownerów pojedynczo i reviewowalnie. Storage/model/parser/shadow/reducer nie mogą przejąć authority nad dialem, target widening, plaintext disclosure, speech/TTS release, proposal approval, user confirmation, commitment ani completion.

Plaintext IdentityVault rozwiązuj możliwie późno, wyłącznie po `AuthorizedFactSnapshot -> FactDisclosurePolicy -> aktualny task/target/state/generation -> ewentualne user approval`.

Frozen boundaries: nie ruszaj `privileged-helper/`, Samsung media path ani `CallMediaSessionCoordinator` bez osobnego root-cause i scope. Jeśli wróci `CallRealtimeMediaSessionTest.pumpFailure...`, najpierw test ordering/synchronization/test pollution.

Orange pozostaje checkpointed ServicePack i nie jest teraz głównym celem.

Telefon może być użyty do Android/Keystore/ADB proof po świeżym bindingu, ale samo podłączenie telefonu nie jest zgodą na wykonanie połączenia. Każdy live-call wymaga świeżej jawnej autoryzacji targetu i zadania w bieżącej sesji.

Pracuj autonomicznie: fresh state -> device seam check -> physical RED/GREEN evidence lub root cause -> canonical regressions jeśli kod się zmienia -> Android/session proof -> dopiero bounded owner wiring -> aktualizacja authoritative docs/handoffu.
