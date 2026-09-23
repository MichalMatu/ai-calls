# Next-chat prompt — Gemma model readiness semantics

Kontynuuj repozytorium `MichalMatu/android-ai-call-bridge` z aktualnego `main`.

Najpierw pobierz świeży `origin/main` i użyj wyłącznie świeżego/current Local Chat Bridge bindingu z nowego okna. Nie kopiuj starego `agent_binding` z dokumentów ani historii tasków.

Przeczytaj kolejno:

1. `AGENTS.md`
2. `README.md`
3. `docs/HANDOFF_NEXT_CHAT.md`
4. `docs/ROADMAP.md`
5. `docs/ARCHITECTURE.md`
6. `docs/SECURITY_PRIVACY.md`
7. `docs/HANDOFF_PROTOCOL.md`
8. `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

Gate D `BOOK_APPOINTMENT` jest zakończony i `PROVEN_S22`; nie implementuj go ponownie. Samsung media/STT/TTS i `privileged-helper/` pozostają `PROVEN_S22 / FROZEN`.

Aktywna architektura dialogu pozostaje:

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM => deterministic existing owner path
 -> unresolved/ambiguous/cold => lokalna Gemma 4 jako bounded skill-classifier
 -> app-owned exact reviewed response
 -> Gemma failure / low confidence / TAKE_OVER => injected-response / ChatRelay fallback
 -> normal output approval
 -> TTS
```

Gemma zwraca wyłącznie `skill + confidence + reason`; nie ma authority do dialowania, disclosure, confirmation, commitment, completion ani direct speech release. ChatRelay jest developer/injected-response fallback, nie product runtime transport.

## Gemma 4 — stan proven

Jedyny target/provider na tym etapie:

```text
provider=LOCAL_GEMMA_4
model=Gemma 4 E2B IT
file=gemma-4-E2B-it.litertlm
runtime=LiteRT-LM
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Nie porównuj Qwena. Stary HTTP `127.0.0.1:8080` backend został usunięty. `EDGE_GALLERY` jest tylko legacy stored value migrowanym do `LOCAL_GEMMA_4`.

Direct LiteRT no-call inference oraz nowy app-owned SAF model lifecycle są **PROVEN_S22**.

Importer:

```text
SAF source URI
 -> AndroidGemma4ModelImporter
 -> Gemma4ModelInstaller
 -> app-owned .importing sibling
 -> pinned SHA-256
 -> flush + fsync
 -> atomic replacement
 -> active app-owned model
```

Fizycznie udowodniono na S22: import przez UI/DocumentsUI, dokładny hash, atomic replacement, brak staging po sukcesie, poprawny app-owned SELinux/ownership oraz post-import inference:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

## Pierwszy task — readiness semantics

**Nie wykonuj live calla.**

1. Sprawdź świeży HEAD, daemon i bieżący binding.
2. Uruchom baseline targeted tests + `bash scripts/verify_host.sh`.
3. Zrób preimplementation audit wszystkich miejsc, które mogą wybrać lub uruchomić `LOCAL_GEMMA_4` — szczególnie `MainActivity`, `CallRuntimePreferences`, Android text-call preparation/readiness, dialogue backend factories i diagnostic probes.
4. RED: dodaj kontrakt pokazujący, że application-owned readiness ma rozróżniać co najmniej `MISSING / INVALID / READY` zanim powstanie LiteRT engine.
5. Zaprojektuj minimalny owner readiness oparty o `Gemma4ModelCatalog` i app-owned destination. Nie hashuj 2.6 GB na każdy turn; wykorzystaj wynik zweryfikowanego importu/cheap metadata state, a jeśli potrzebna jest pełna rewalidacja, zdefiniuj kiedy i dlaczego.
6. Podłącz readiness do UI/session preparation tak, aby brak lub invalid model fail-closed przed native inference.
7. Nie twórz nowego authority store i nie zmieniaj dialogue/output/call policy.
8. Nie wymyślaj arbitralnego model download URL. Przyszły reviewed downloader/source catalog ma tylko dostarczyć bytes do istniejącego proven `Gemma4ModelInstaller`.
9. Po minimalnym GREEN uruchom targeted tests, pełny `verify_host.sh`, Android package. Physical S22 no-call gate wykonaj tylko dla zmienionej granicy readiness/UI; nadal bez telefonii/media.

## Czego nie robić

- nie wykonuj live calla bez nowej jawnej autoryzacji konkretnego numeru/targetu i konkretnego zadania w tym samym oknie;
- nie ruszaj Samsung media/`privileged-helper/` bez nowego konkretnego root cause;
- nie dopisuj Orange phrase aliases jako głównej strategii;
- nie dawaj modelowi/importerowi/readiness nowych authority;
- nie uzależniaj produkcji od Edge Gallery, ADB ani innej aplikacji;
- nie omijaj output approval.
