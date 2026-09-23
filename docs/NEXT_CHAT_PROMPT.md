# Next-chat prompt — continue Gemma 4 hybrid dialogue resilience

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

Orange exact-phrase scripting okazał się zbyt kruchy. Nowy kierunek jest dwutorowy:

```text
finalized STT
 -> PhraseMatrix + response temperature
 -> HOT/WARM => deterministic existing owner path
 -> unresolved/ambiguous/cold => lokalna Gemma 4 jako bounded skill-classifier
 -> app-owned skill policy wybiera exact reviewed response
 -> gdy Gemma zawiedzie / confidence za niskie / TAKE_OVER => istniejąca injected-response / ChatRelay ścieżka
 -> normal output approval
 -> TTS
```

`PhraseMatrix` ma już `HOT/WARM/UNCERTAIN/COLD/AMBIGUOUS`; `WARM` jest tolerancyjnym deterministycznym rozszerzeniem, ale ambiguity nadal fail-closed.

Skills są już typed i bounded: model zwraca `skill + confidence + reason`, ale nie posiada authority i nie wypuszcza arbitralnego tekstu bez polityki aplikacji.

## Ważna decyzja modelowa

Na tym etapie **Gemma 4 jest jedynym modelem docelowym**.

Nie porównuj ani nie optymalizuj Qwena, chyba że użytkownik jawnie otworzy ten temat ponownie.

Target:

```text
Gemma 4 E2B IT
file: gemma-4-E2B-it.litertlm
runtime: LiteRT-LM
```

Stary `EdgeGalleryTextBackend` zakładający `127.0.0.1:8080` jest błędnym/starym eksperymentem. Fizyczna diagnostyka wykazała, że zainstalowane AI Edge Gallery nie wystawia takiego API nawet po uruchomieniu `MainActivity`.

Aktualny `main` ma już bezpośrednią integrację LiteRT-LM:

```text
app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Gemma4LiteRtTextBackend.kt
```

oraz dependency:

```text
com.google.ai.edge.litertlm:litertlm-android:0.17.1
```

Implementation checkpoint przed handoff docs:

```text
e35152f78446e69df8b98f4f403943751eb1a130
Use direct Gemma 4 backend for Android text calls
```

Zawsze użyj świeżego HEAD zamiast zakładać, że ten SHA nadal jest tipem.

## Fizyczny stan modelu na S22

Model jest już pobrany przez Edge Gallery i widoczny w external/shared app storage, m.in.:

```text
/sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm
```

Nowy backend oczekuje app-owned path:

```text
<pl.michalmatu.aicallbridge external files>/models/gemma-4-E2B-it.litertlm
```

Nie uzależniaj produkcji od prywatnego sandboxu Edge Gallery. Na potrzeby development proof można przez Local Agent/ADB skopiować już pobrany model do app-owned external-files model directory, o ile urządzenie pozwala na tę operację. Docelowo aplikacja ma jawnie posiadać/importować/pobierać własny model.

## Pierwszy konkretny task

Nie wykonuj live calla.

1. Sprawdź świeży HEAD i daemon.
2. Uruchom targeted host tests + `bash scripts/verify_host.sh` dla aktualnych zmian Gemma/hybrid.
3. Zweryfikuj compile/package Android po dependency LiteRT-LM.
4. Provisionuj `gemma-4-E2B-it.litertlm` do app-owned model path.
5. Uruchom fizycznie na S22 **no-call** test Gemma dialogue-skill z syntetycznym tekstem.
6. Wymagaj terminalnego parsed `skill/confidence/reason`; jeśli test padnie, sklasyfikuj konkretnie: model path, Engine init, GPU/CPU backend, LiteRT native/runtime, JSON ResponseFormat, memory/timeout albo parser.
7. Po GREEN zweryfikuj offline/synthetic hybrid flow:
   - HOT/WARM deterministic;
   - unresolved -> Gemma skill;
   - Gemma low confidence/error -> injected-response fallback;
   - telemetry pokazuje decyzję Gemmy i źródło finalnej odpowiedzi.
8. Dopiero po tym rozważ kolejny bounded live acceptance call.

## Czego nie robić

- nie wracaj do HTTP `:8080` jako Gemma runtime;
- nie porównuj teraz Qwena i Gemmy;
- nie dopisuj kolejnych exact Orange phrase aliases jako głównej strategii;
- nie ruszaj Samsung media/`privileged-helper/` bez konkretnego root cause;
- nie dawaj Gemmie dial/target widening/plaintext disclosure/user-confirmation/commitment/completion authority;
- nie omijaj output approval;
- nie traktuj ChatRelay jako product runtime transport — to fallback/developer injection boundary.

## Live-call rule

Ten prompt i handoff nie niosą żadnej zgody na telefonowanie. Każdy przyszły realny call wymaga świeżej jawnej autoryzacji konkretnego numeru/targetu i konkretnego zadania w nowym oknie.

Pracuj autonomicznie w tym zakresie: najpierw no-call Gemma proof, potem hybrid fallback proof, bez ponownego pytania o decyzje, które są już zapisane w handoffie.
