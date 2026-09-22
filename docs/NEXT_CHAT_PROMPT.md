# Next-chat prompt — Gate D TaskGraph v1

Kontynuuj rozwój repozytorium `MichalMatu/android-ai-call-bridge` zgodnie z aktualnym stanem zapisanym w repo. Pracuj autonomicznie w ramach udokumentowanego scope. Nie wracaj do szerokiego mappingu Orange jako głównego celu.

## Najpierw świeży stan

Przed zmianami przeczytaj świeżo:

- `AGENTS.md`;
- `README.md`;
- `docs/HANDOFF_NEXT_CHAT.md`;
- `docs/ROADMAP.md`;
- `docs/HANDOFF_PROTOCOL.md`;
- `docs/ARCHITECTURE.md`;
- `docs/SECURITY_PRIVACY.md`;
- `docs/PHASE2D_FREEZE_2026-09-18.md`.

Pobierz świeży `origin/main`. `docs/ROADMAP.md` jest autorytatywną kolejnością pracy, a `docs/HANDOFF_NEXT_CHAT.md` checkpointem. Nie ufaj historycznemu SHA bardziej niż świeżemu repo.

## Local Chat Bridge / Local Agent

Jeżeli ten czat działa przez Local Chat Bridge, użyj wyłącznie świeżego binding envelope dostarczonego w **tym nowym czacie**. Nie kopiuj `agent_binding` z poprzedniego czatu, dokumentacji, historii ani starych tasków.

Pracuj wyłącznie na dokładnie zbindowanym repozytorium. Przed taskiem sprawdź świeży daemon/current-task evidence. Direct GitHub edits stosuj do małych, dokładnie reviewowalnych zmian; Local Agent do lokalnych buildów/testów/Gradle/ADB/device. `.agent` zostaje na `agent-control`. Nie uruchamiaj lokalnego Codex z taska Local Agent i nie restartuj Local Agent jako obejścia problemu.

## Aktywny cel

Aktywny milestone:

```text
Gate D — hybrid multi-turn Task Engine
```

Pierwszy realny use case:

```text
BOOK_APPOINTMENT
```

Docelowa hybryda:

```text
User goal
 -> IdentityVault availability + per-task fact authorization
 -> CallTask + constraints/preferences/AuthorizedFactSnapshot
 -> TaskGraph
 -> deterministic PhraseMatrix / typed parsers first
 -> shadow LLM observer from the beginning when enabled
 -> explainable DialogueFit / escalation policy
 -> bounded LLM proposal only when needed
 -> existing transition ID + typed non-secret slots only
 -> deterministic validation
 -> FactDisclosurePolicy when identity data is requested
 -> CallWorkflow / CallPlan / output approval
 -> proposal / confirmation / CallCommitmentGate
 -> structured completion
```

LLM pozostaje ważny, ale jako bounded supervisor/classifier. Może od początku obserwować **finalized turns** w quarantined shadow mode, żeby zachować kontekst. Shadow output nie może zmieniać TaskGraph/workflow, wypowiadać tekstu ani ujawniać danych. Aktywny supervisor może proponować tylko istniejący transition ID + typed non-secret slot candidates + confidence/diagnostics.

## Dane osobowe

Nie traktuj imienia, telefonu, maila, adresu, daty urodzenia ani PESEL jako zwykłych globalnych slotów.

Docelowy podział:

```text
IdentityVault       = durable encrypted values
CallTask            = per-task authorized fact references/snapshot
DialogueState       = transient facts learned in this call
```

Potrzebny jest typed `IdentityFieldId` oraz application-owned `FactDisclosurePolicy` zwracający `ALLOW / ASK_USER / DENY`. Sam fakt, że wartość istnieje w vault, nie daje prawa do jej ujawnienia.

Plaintext identity values trzymaj poza LLM context domyślnie. Supervisor zwykle ma wiedzieć tylko, że dane pole jest dostępne/autoryzowane. PESEL i inne high-sensitivity fields mają wymagać jawnego per-task authority i ewentualnie user/device authentication przed disclosure.

Android persistence wdrażaj dopiero po host contract: app-private ciphertext + non-exportable Android Keystore key + authenticated encryption + versioned records + jawna polityka backup/restore. Nie buduj nowego vault na deprecated `EncryptedSharedPreferences` / `MasterKey`.

## Pierwsza praca w tym czacie

Zacznij od **preimplementation audit Gate D** bez szerokiego refactoru i bez zmian frozen media.

1. Zmapuj istniejące typy/odpowiedzialności: `CallTask`, `CallWorkflow`, CallPlan/CallPlanTurnCoordinator, proposal/confirmation/commitment, prepared/local text session.
2. Ustal najmniejszy `TaskGraph v1`, który komponuje istniejących authority owners zamiast ich duplikować.
3. Zidentyfikuj właściwego product session ownera; nie zamieniaj diagnostic probes/runners w orchestrator.
4. Najpierw napisz wspólne **RED contract tests** dla TaskGraph: typed states/events/transitions, pure guards, state compatibility, bounded recovery, proposal/confirmation/commitment, versioned replayable event log i side-effect separation.
5. Na tych samych testach zrób krótki host spike:

```text
minimal custom reducer
vs
KStateMachine
```

6. Wybierz **dokładnie jeden** core. KStateMachine przyjmij tylko jeśli realnie upraszcza implementację i nie przejmuje authority/evidence/persistence semantics. Nie utrzymuj dwóch silników.
7. Zdefiniuj host contracts `IdentityVault`, `IdentityFieldId`, `AuthorizedFactSnapshot`/references i `FactDisclosurePolicy`.
8. Dopiero potem minimal GREEN wybranego TaskGraph core.
9. Następny slice: host-only `BOOK_APPOINTMENT` + deterministic simulated receptionist.

Nie wykonuj prawdziwych połączeń w pierwszym slice.

## Slot/fact extraction

Wzorzec obowiązkowy:

```text
extract candidate
 -> validate type/state/constraints/provenance/authorization
 -> commit to authoritative TaskState only after validation
```

Parser/NLU/LLM/matcher nie zapisuje autorytatywnego slotu tylko dlatego, że ma wysoką pewność.

## Shadow supervisor + DialogueFit

Po użytecznym deterministic simulatorze dodaj shadow observera z zerowym execution authority.

`DialogueFit` jest application-owned i explainable. Nie implementuj go jako raw text similarity ani jednego opaque LLM confidence. Uwzględniaj m.in.:

- STT quality/confidence gdy dostępne;
- PhraseMatrix result/confidence;
- parser completeness;
- compatibility z expected transitions obecnego TaskGraph state;
- contradiction/negation;
- missing required slots;
- repeated unknown/recovery count;
- deterministic-vs-shadow disagreement.

Zacznij od kategorii:

```text
HIGH       -> deterministic path
UNCERTAIN  -> clarification / optional supervisor check
LOW        -> supervisor proposal required
BROKEN     -> recovery / TAKE_OVER / safe stop
```

Thresholdy/liczby kalibruj dopiero na simulator/eval evidence i dodaj hysteresis/debouncing przed live użyciem.

## External patterns — co warto przejąć

Nie importuj frameworków w ciemno; przejmij kontrakty:

- **Pipecat Flows** — graph/config owns legal transitions, handlers zwracają structured results;
- **XState/statecharts** — pure guards, explicit state/event/context, event replay, effects poza transition logic;
- **LiveKit Tasks/TaskGroups** — małe typed-result subtasks pod jednym session ownerem;
- **Rasa/form slot filling** — extraction candidate -> explicit validation -> dopiero state; dynamic required slots + unhappy paths;
- **KStateMachine** — sensowny Kotlin Multiplatform kandydat na statechart core, ale tylko po wspólnym spike/contract tests.

## Orange

Nie usuwaj Orange ani nie traktuj go jako martwego testu. `service-packs/orange/service_tree.v1.json` to pierwszy persistent evidence-backed IVR ServicePack pod przyszłą obsługę realnych zadań Orange, regresję IVR i rozwój standardu ServicePack.

Nie kontynuuj szerokiego mappingu Orange domyślnie. Wróć do niego tylko gdy wspiera konkretny user task lub nową generyczną funkcję ServicePack. Wtedy użyj `docs/ORANGE_MAPPING_RUNBOOK.md`.

## Frozen / safety

Nie ruszaj bez osobnego uzasadnienia:

- `privileged-helper/` i frozen Samsung media path;
- fizycznie sprawdzonego `CallMediaSessionCoordinator` dla stylistycznego refactoru;
- diagnostic runnerów jako product orchestratorów.

Zachowaj istniejących authority owners: `CallTask`, `CallResolvedTarget`, `CallWorkflow`, `CallConfirmationPolicy`, `CallCommitmentGate`, application-owned output approval. Fact disclosure ma być dodatkową application-owned decyzją, nie authority przejętym przez vault/LLM/Skill.

Dla przyszłych realnych rejestracji stosuj politykę z `docs/ROADMAP.md`/`docs/SECURITY_PRIVACY.md`: test-only disclosure+consent na początku, genuine task tylko z user authorization, domyślnie jeden meaningful call na organizację, drugi tylko po early technical failure lub zgodzie na powtórkę, żadnych emergency/urgent/crisis lines.

## Styl pracy

- audit/root cause przed implementacją;
- TDD: RED -> udowodnij właściwy failure -> minimal GREEN -> regressions;
- najpierw wspólny contract, potem wybór implementacji;
- małe cohesive zmiany zamiast szerokiej przebudowy;
- nie pytaj ponownie o rzeczy już jednoznacznie zapisane w repo;
- przy dłuższej pracy informuj krótko o postępie;
- po zmianach uruchom targeted tests i `bash scripts/verify_host.sh` przed `HOST_GREEN`;
- physical evidence tylko wtedy, gdy naprawdę wymagane przez zmieniony boundary;
- na końcu większej sesji zastosuj `docs/HANDOFF_PROTOCOL.md` i odśwież handoff + ten prompt.

Rozpocznij od audytu istniejącego domain/task/workflow code pod TaskGraph v1. Następnie przejdź autonomicznie do wspólnych RED contract tests i hostowego spike `custom reducer vs KStateMachine`, o ile audyt nie ujawni architektonicznego blokera.
