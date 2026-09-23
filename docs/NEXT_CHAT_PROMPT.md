# Next-chat prompt — physical S22 Gemma model-import proof

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
9. `docs/ORANGE_LIVE_ACCEPTANCE_2026-09-23.md` tylko jako historyczny zapis live acceptance

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

## Gemma 4

Jedyny target na tym etapie:

```text
Gemma 4 E2B IT
gemma-4-E2B-it.litertlm
LiteRT-LM
sha256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c
```

Nie porównuj Qwena. Nie wracaj do starego Edge Gallery HTTP `127.0.0.1:8080`.

Direct LiteRT no-call inference jest już `PROVEN_S22` na wcześniejszym app-readable provisioningu. Fizyczny wynik:

```text
skill=ACKNOWLEDGE_NEUTRAL
confidence=0.95
reason=Potwierdzenie odbioru telefonu
```

## Nowy model lifecycle — HOST_GREEN / PENDING_PHYSICAL

Aplikacja ma teraz jawny app-owned import:

```text
Android SAF source URI
 -> AndroidGemma4ModelImporter
 -> Gemma4ModelInstaller
 -> app-owned sibling staging file
 -> streaming pinned SHA-256
 -> flush + fsync
 -> atomic same-filesystem replacement
 -> <app external files>/models/gemma-4-E2B-it.litertlm
 -> existing direct LiteRT runtime
```

Hostowo udowodniono RED -> GREEN, targeted Gemma/dialogue/hybrid tests, pełny `bash scripts/verify_host.sh`, debug APK i AndroidTest APK.

Importer fail-closed:

- wrong/empty/unreadable source nie aktywuje modelu;
- poprzedni aktywny model przeżywa failed verification/activation;
- staging jest czyszczony po błędzie;
- nie ma silent non-atomic fallback;
- Edge Gallery/ADB nie są production runtime dependency;
- UI ma `Import Gemma 4 model` przez SAF.

## Pierwszy task — telefon jest teraz potrzebny

**Nie wykonuj live calla.**

1. Sprawdź świeży HEAD, daemon i bieżący binding.
2. Podłącz/zweryfikuj S22 przez ADB.
3. Zainstaluj aktualny debug APK + AndroidTest APK, jeśli potrzeba.
4. Zapewnij readable source copy dokładnego `gemma-4-E2B-it.litertlm` do development proof. ADB/Edge Gallery może tylko dostarczyć source bytes; nie kopiuj destination bezpośrednio do app-owned runtime path.
5. Uruchom nowy `Import Gemma 4 model` przez app UI/SAF, tak aby destination był tworzony przez proces aplikacji.
6. Wymagaj dokładnego SHA-256 i poprawnej aktywacji.
7. Zweryfikuj, że external-files filesystem obsługuje wymagany `ATOMIC_MOVE`. Jeżeli nie, zatrzymaj się na dokładnym root cause i zaprojektuj bezpieczną atomową alternatywę; nie dodawaj zwykłego overwrite fallbacku.
8. Po udanym imporcie uruchom istniejący fizyczny **no-call** Gemma dialogue-skill test z syntetycznym tekstem i wymagaj terminalnego `skill/confidence/reason`.
9. Dopiero po GREEN oznacz model lifecycle jako `PROVEN_S22`.

Nie uruchamiaj telefonii, mikrofonu, STT/TTS ani media path dla tego gate'u.

## Czego nie robić

- nie wykonuj live calla bez nowej jawnej autoryzacji konkretnego numeru/targetu i konkretnego zadania w tym samym oknie;
- nie ruszaj Samsung media/`privileged-helper/` bez nowego konkretnego root cause;
- nie dopisuj Orange phrase aliases jako głównej strategii;
- nie dawaj modelowi/model-importowi nowych authority;
- nie uzależniaj produkcji od Edge Gallery, ADB ani innej aplikacji;
- nie omijaj output approval.
