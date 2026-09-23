# Next-chat prompt — continue after Gemma 4 no-call + hybrid proof

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
8. `docs/ORANGE_LIVE_ACCEPTANCE_2026-09-23.md` tylko jako historyczny zapis live acceptance
9. `docs/PHASE2D_FREEZE_2026-09-18.md` przed jakąkolwiek zmianą media

## Stan wejściowy

Gate D `BOOK_APPOINTMENT` jest zakończony i fizycznie udowodniony na S22. Nie implementuj go ponownie.

Samsung media/STT/TTS path pozostaje `PROVEN_S22 / FROZEN`.

Aktywna architektura dialogu:

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM => deterministic existing owner path
 -> unresolved/ambiguous/cold => lokalna Gemma 4 jako bounded skill-classifier
 -> app-owned skill policy wybiera exact reviewed response
 -> Gemma failure / low confidence / TAKE_OVER => injected-response / ChatRelay fallback
 -> normal output approval
 -> TTS
```

`PhraseMatrix` ma `HOT/WARM/UNCERTAIN/COLD/AMBIGUOUS`; WARM toleruje drobne wariacje, ale ambiguity nadal fail-closed.

Skills są typed i bounded: model zwraca tylko `skill + confidence + reason`; aplikacja posiada allowed skills i dokładny reviewed response. Model nie dostaje authority do dialowania, disclosure, confirmation, commitment, completion ani bezpośredniego speech release.

## Gemma 4 — stan udowodniony

Na tym etapie **Gemma 4 jest jedynym modelem docelowym**. Nie porównuj ani nie rozwijaj Qwena, chyba że użytkownik jawnie otworzy ten temat ponownie.

Target:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
```

Stary `EdgeGalleryTextBackend` oparty o `127.0.0.1:8080` jest błędnym/starym eksperymentem i nie może być przywracany.

Aktualny runtime używa direct LiteRT-LM przez:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

oraz:

```text
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

Direct Gemma no-call path jest teraz **PROVEN_S22**. Fizyczny S22 zwrócił terminalnie:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

LiteRT JNI/native runtime i GPU delegate działały poprawnie. Test nie uruchamiał telefonii ani media path.

## Ważny provisioning root cause

Model źródłowy nadal istnieje w external storage Edge Gallery, m.in.:

```text
/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm
```

Development proof skopiował te same bajty do app-owned:

```text
<pl.michalmatu.aicallbridge external files>/models/gemma-4-E2B-it.litertlm
```

Zweryfikowany SHA-256:

```text
181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Zwykłe `adb shell cp` utworzyło plik, którego app/LiteRT nie mogło otworzyć (`PERMISSION_DENIED`). Poprawny development proof zapisał destination przez UID aplikacji (`run-as pl.michalmatu.aicallbridge`), co naprawiło ownership/SELinux labeling.

Nie uzależniaj produkcji od Edge Gallery ani ADB. Docelowo aplikacja ma jawnie importować/pobierać i posiadać własny model.

## Hybrid proof — HOST_GREEN

Synthetic hybrid contract jest udowodniony:

```text
bounded local skill -> exact app-owned response -> source=LOCAL_SKILL
low confidence -> injected fallback -> source=CHAT_RELAY
classifier error -> injected fallback -> source=CHAT_RELAY
```

Telemetry zachowuje `skill/confidence/reason`, local skill error i źródło finalnej odpowiedzi. HOT/WARM pozostają deterministic, ambiguity/unresolved nie są zgadywane.

ChatRelay pozostaje developer/injected-response fallback, nie product runtime transport.

## Pierwszy konkretny task następnego okna

**Nie wykonuj live calla bez nowej jawnej autoryzacji.**

Najpierw zajmij się kolejną ogólną luką produktu: application-owned lifecycle modelu Gemma 4.

1. Sprawdź świeży HEAD i daemon/binding.
2. Potwierdź targeted host tests + `bash scripts/verify_host.sh` na aktualnym HEAD przed zmianami.
3. Zrób preimplementation audit istniejących Android storage/settings/UI/runtime ownerów dla modelu — bez ruszania media.
4. Zaprojektuj minimalny jawny app-owned import/download path dla `gemma-4-E2B-it.litertlm`:
   - app-owned destination;
   - integrity/version/expected model identity check;
   - atomic activation/failure behavior;
   - brak runtime dependency od Edge Gallery.
5. Implementuj minimalnie zgodnie z istniejącą architekturą i policy ownership.
6. Po zmianie uruchom targeted tests, `verify_host.sh`, Android package i fizyczny S22 **no-call** Gemma proof ponownie.
7. Nie ruszaj Samsung media ani `privileged-helper/` bez konkretnego nowego root cause.

Jeżeli użytkownik zamiast tego jawnie autoryzuje konkretny bounded live acceptance call, sprawdź dokładny target/number + task w tym samym oknie i dopiero wtedy użyj istniejących live-call policy/owners. Handoff, poprzednie call’e i podłączony telefon nie są zgodą.

## Czego nie robić

- nie wracaj do HTTP `:8080` jako Gemma runtime;
- nie porównuj teraz Qwena i Gemmy;
- nie dopisuj kolejnych exact Orange phrase aliases jako głównej strategii;
- nie ruszaj Samsung media/`privileged-helper/` bez konkretnego root cause;
- nie dawaj Gemmie dial/target widening/plaintext disclosure/user-confirmation/commitment/completion authority;
- nie omijaj output approval;
- nie traktuj ChatRelay jako product runtime transport.

## Live-call rule

Ten prompt i handoff nie niosą żadnej zgody na telefonowanie. Każdy przyszły realny call wymaga świeżej jawnej autoryzacji konkretnego numeru/targetu i konkretnego zadania w tym samym oknie.
