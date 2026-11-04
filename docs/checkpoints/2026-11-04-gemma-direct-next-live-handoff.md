# AI Calls — kompletny checkpoint: Gemma Direct przed realnym testem (2026-11-04)

> **Status checkpointu:** zapis źródeł i dowodów, bez wykonania następnego połączenia. Niezależny backend Gemma Direct jest zaimplementowany, Mac QA i 2-tury off-call na S22 **PASS**. Swobodna rozmowa telefoniczna Gemma Direct, jej słyszalność po stronie konsultanta, bezpieczeństwo dowolnych sformułowań i wynik CLIR — **OPEN**.

## 1. Reprodukowalny stan i źródła prawdy

- Repo: `MichalMatu/ai-calls`; gałąź produktu `main`, brak nowej gałęzi roboczej w tym sprincie.
- **Kod zbudowany, przetestowany i zainstalowany na S22:** `2424118318caaca03d6330129bddf4190715677c`.
- **HEAD bazowy checkpointu po dodatkowym przeglądzie i wpisach F-044/F-045:** `f9c8cdae0c93c2a3d548c723a82b224ed4c15cc8`; późniejsze commity dokumentacji zwiększą HEAD, lecz nie zmieniają wersji aplikacji. Zawsze ponownie odczytaj HEAD przed pracą.
- Poprzednie źródło/zapis: [Gemma Direct — niezależny silnik](2026-11-04-gemma-direct-independent-engine.md), [architektura](../ARCHITECTURE.md), [handoff](../HANDOFF_NEXT_CHAT.md), [jedyny rejestr F-xxx](../../BLOCKERS.md).
- Obowiązujące zasady `AGENTS.md`, `docs/GENERIC_PHONE_TASK_AUTHORITY.md`, `docs/SECURITY_PRIVACY.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/PHASE2D_FREEZE_2026-09-18.md` — szczególnie grant/scope, ograniczenie faktów/efektów, tylko własny Call, STOP i prywatność.
- Wymagany tryb prac: **GitHub Connector + Mac Local Agent**, bez Codex, ChatGPT Work i GitHub Actions (także sprawdzania statusu). Host: `macbook-michal`; instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. Control plane `MichalMatu/local-agent:agent-control`, katalog `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/`. Odczytywać prawdziwe `task-results`, nie historyczny heartbeat.

## 2. Dwa niezależne flow w produkcie

| Element | `SCRIPT_GEMMA` — dotychczasowy | `GEMMA_DIRECT` — nowy |
| --- | --- | --- |
| Wybór w `CampaignToolActivity` | `Start authorized CLIR enable` — **domyślny** | `Start authorized CLIR enable — Gemma Direct` — **jawny** |
| Backend dialogu | `ClirDeterministicDialogueBackend(GateCHybridDialogueBackend)` | `GemmaDirectDialogueBackend`, bez PhraseMatrix i skryptowego routera |
| Słowa wypowiadane przez AI | Odpowiedź wynikająca z deterministycznej akcji/skryptu | Własny, krótki polski tekst Gemmy dla `SPEAK` |
| Pamięć dialogu | F-043 — natywne `Conversation` dla klasyfikatora | Osobne natywne `Conversation` dla generatywnego modelu |
| Wspólne granice | Aplikacyjny grant, SIM/target, `CallTask`, własny Telecom Call, STT, TTS, audio RX/TX, `FactDisclosurePolicy`, `CallCommitmentGate`, output approval, STOP/revoke, prywatny recording | **Te same** granice, żadnego samodzielnego dial/ujawnienia/zakupu przez model |

Wybór zapisany per grant przez `CampaignDialogueModeStore` i przekazywany przez `AppOwnedCampaignService` → `AppOwnedClirCampaignExecutor` → `ChatRelayLiveCallProbe`. Kontynuacja dopasowanego grantu zachowuje tryb; brak konfiguracji wybiera stary script, obcy jawny identyfikator trybu jest odrzucany.

## 3. Jak Gemma Direct dostaje cel, prompt, dane i skillsy **teraz**

**Obecna implementacja to etap CLIR-specific, nie uniwersalny dynamiczny agent.**

1. `GemmaDirectTaskBrief.PROMPT` w `GemmaDirectDialogueBackend.kt` jest **stałą systemową instrukcją** w kodzie: język polski, prośba do konsultanta o włączenie stałego CLIR na obecnej usłudze głosowej, wyjaśnianie celu, dopytywanie o procedurę, zachowanie kontekstu, niewymyślanie danych i brak zgody na inne usługi/umowy/opłaty. Wprost zaznacza, że nie zna telefonu, PIN, numeru umowy, kosztów i wyniku aktywacji.
2. `AppOwnedCampaignService.startAuthorizedAttempt` wykonuje prewarm właściwego modelu, `CampaignGemmaDirectPreflight.prepare` inicjalizuje LiteRT-LM z **tym samym promptem**, wykonuje sztuczną pierwszą wypowiedź, po czym **czyści syntetyczną historię** przez `NativeConversationContextBackend.resetConversationContext`, zachowując rozgrzany silnik.
3. Prawdziwa wypowiedź konsultanta dostaje się do modelu przez Samsung RX → lokalne STT → `TextCallAgentBackend.generate`. `Gemma4LiteRtTextBackend` używa jednego natywnego `Conversation` na realną sesję. `automaticToolCalling=false`, `ThinkingConfig(enableThinking=false)`, format JSON zgodny ze schematem `ResponseFormat.json`. To nie jest dowolny dostęp modelu do API, Androida lub systemu.
4. Dostępny katalog **czterech typowanych propozycji**: `SPEAK {text}` — swobodna wypowiedź przechodząca przez `GemmaDirectSpeechPolicy` i istniejący finalny release; `ASK_CLARIFY` — bezpieczna prośba o dalszą informację; `DISCLOSE_PHONE` — wywołanie istniejącego `AuthorizedPhoneFactBackend` (dopiero jeśli `FactDisclosurePolicy`, bieżący grant, route i media na to pozwolą); `CONFIRM_EFFECT` — wyłącznie poprzez app-owned `controlAwareBackend`, `CallExternalEffectValidator` i jednorazowy commitment permit.
5. **Dane do dyspozycji:** bezpieczny opis CLIR w prompt, transkrypty STT kolejnych tur w kontekście modelu, typowane bieżące warunki i autoryzacja po stronie aplikacji. Numer telefonu i wartości osobowe **nie są wstrzykiwane do promptu**. System pobiera je dopiero przez oddzielny, zatwierdzony mechanizm, jeśli dozwolona akcja i zakres grantu tego wymagają. Model nie ma skillsów do samodzielnego wybierania numeru, edycji konta ani do potwierdzania zewnętrznego sukcesu.
6. `GemmaDirectSpeechPolicy` jest obecnie syntaktycznym filtrem treści `SPEAK`; nie zapewnia jeszcze pełnej semantycznej kontroli nowych zobowiązań. `CallTextAgentOutputApprovalPolicy` sprawdza stan workflow, a nie całe znaczenie wypowiedzi. **Nie przedstawiać tego jako pełnego dowodu bezpieczeństwa swobodnych odpowiedzi** — F-045 OPEN.

**Docelowa architektura dla każdego zadania:** `CallTask` + dokładny grant i target/SIM + dostępne fakty po identyfikatorach (bez wartości sekretów) + dozwolone actions/skills + ograniczenia i kryteria ukończenia → **zbudowany per-task `TaskBrief`/system prompt przed prewarm** → jeden natywny chat w rozmowie → model proponuje `SPEAK/typed action` → aplikacja waliduje/ujawnia/wykonuje → niezależny dowód skutku. To **PLAN**, nie aktualnie działający dynamiczny builder — F-044 OPEN. ServicePacks mogą dostarczać wiedzę proceduralną jako dane, nie kolejny magazyn uprawnień ani skrypty fraz.

## 4. Potwierdzone QA — nie powtarzać bez zmiany kodu

| Weryfikacja | Receipt / SHA | Wynik i zakres |
| --- | --- | --- |
| Mac focused (po RED i poprawce zegara) | `aicalls-20261104-gemma-direct-focus-qa-v2` / `56011259` | DONE/0; 676 Kotlin JVM unit tests + debug APK + anti-friction PASS |
| Mac full | `aicalls-20261104-gemma-direct-full-host-qa-v1` / `24241183` | DONE/0; 3 fazy Gradle unit/build/lint/debugAndroidTest + 248 Python, `host_quality_gate_green=true`, anti-friction PASS |
| Dokładny fizyczny S22, syntetyczne 2 tury | `aicalls-20261104-gemma-direct-s22-offcall-two-turn-v1` / APK `24241183` | DONE/0; `SM-S906B`, grant i continuation bez zmian, APP+test APK zainstalowane, test instrumentacyjny `OK (1 test)`, bez telefonu, mic, TTS i zewnętrznych efektów |
| Read-only telemetry modelu | `aicalls-20261104-gemma-direct-s22-perf-metadata-v1` | DONE/0; 2 generacje na GPU `complete`, ponowne użycie `Conversation` na turze 2 |

Wyniki na S22:
- Tura 1: `engine_ready_ms=6198`, `conversation_create_ms=3097`, `generate_ms=6525`, `conversation_reused=false`, `conversation_turn=1`, odpowiedź 145 znaków JSON.
- Tura 2: `engine_ready_ms=0`, `conversation_create_ms=0`, `generate_ms=1598`, `conversation_reused=true`, `conversation_turn=2`, odpowiedź 70 znaków JSON.
- Stan po teście: `IDLE`, rola dialera utrzymana, grant i continuity fingerprint bez zmian, brak połączenia. Pomiar dotyczy modelu, **nie** EOS→TTS→TX lub słyszalności.

## 5. Istniejące realne dowody RX/TX — NIE Gemma Direct

- Wcześniejsze fizyczne próby dotyczyły **dotychczasowego flow**. W archiwum prywatnym ostatniego potwierdzonego eksportu na Mac jest **17 sesji**, najnowsza ma `COMPLETE`, `timeline.jsonl` z STT i zatwierdzonym tekstem AI oraz trzy poprawne WAV: `turn-01-rx.wav` 3,78 s, `turn-01-tx-ack.wav` 1,51 s i `turn-01-tx.wav` 4,60 s. Mac receipt `aicalls-20261104-f042-private-audio-export-verify-v1`, DONE/0.
- Prywatny eksport: `~/Documents/AI-Calls/private-exports/call-evidence-20261104T005106Z-dxNqHkmz.tar.gz`; lokalny odtwarzacz: `~/Documents/AI-Calls/private-exports/review-20261104T005106Z-dxNqHkmz/index.html`. **Nie publikować** WAV, transkryptów, identyfikatorów kont lub telefonów w GitHub, checkpointach ani wynikach Local Agenta.
- Z tej sesji: `Gemma TAKE_OVER`, źródło `LOCAL_RECOVERY`, 14,93 s klasyfikacji, 1,8 s do pierwszej zapowiedzi, 18,27 s EOS→pełny TX; `turns_completed=0`, `tx_authority_revoked`. To ważny baseline problemu, ale **nie dowód odpowiedzi Gemma Direct**.
- Od urządzenia oczekujemy: STT obu stron, tekst wygenerowany/zatwierdzony, akcja i źródło z czasem inferencji, zapowiedź i pełny TX z bajtami, format i odsłuch prywatnego WAV; lokalny licznik TX nie dowodzi, że konsultant usłyszał dźwięk.

## 6. Otwarte ryzyka / uczciwe granice

1. **F-044 OPEN:** prompt jest zakodowany dla CLIR; brak dynamicznego TaskBrief zawierającego rzeczywisty zakres grantu, typowane dane i katalog skillsów dla przyszłych zadań. Aktualny CLIR-only pilot ma deklarowany cel, ale nie świadczy o generycznym agencie.
2. **F-045 OPEN:** niezależna kontrola semantyki wolnego `SPEAK` jest niekompletna; obecny regex nie stanowi pełnego dowodu, że parafraza nie wprowadzi dodatkowych zobowiązań. Wprowadzić adversarial fixture/negative tests i niezależny output validator dla typowanego zadania przed pierwszą oceną live.
3. **Gemma Direct live-call OPEN:** brak rzeczywistych wieloturowych odpowiedzi TTS nowego backendu, brak niezależnego far-end odsłuchu, brak porównania akustycznego script vs direct, brak potwierdzenia zewnętrznego CLIR. Nie nazywać off-call `PROVEN_LIVE`.
4. **Kontekst/latencja:** `MAX_CONTEXT_TOKENS=2048`, `MAX_OUTPUT_TOKENS=128`; model pamięta dotychczasowe tury tylko dopóki mieści się kontekst i nie zostanie anulowany/błąd. Nie ma jeszcze spójnej kompaktacji historii. Brak aktualnej metryki pełnego STT→Gemma→TTS dla Direct.
5. **Recording/obserwowalność:** pełne teksty i WAV tylko przy uprzednio uprawnionym `recordCallEvidence`; RX to okna nasłuchu STT, a nie nieprzerwany sygnał całego połączenia. Dane są na Macu lokalnie; nie ma automatycznego eksportu do chmury. Dokumentacja aktywna nadal zachowuje starszą historię F-042/F-043; bieżący status czytać z początku handoffu i niniejszego checkpointu.
6. **Zablokowane narzędzie zdalnego startu rozmowy** z poprzedniej próby pozostaje wiążącym ograniczeniem; żadnego obejścia przez alternatywną komendę, Local Agenta lub API. Samodzielne uruchomienie w aplikacji przez właściciela jest niezależną drogą do autoryzowanej, rzeczywistej próby, bez naszego zdalnego dial.

## 7. Konkretny plan kontynuacji — nie dzwonić przy samym checkpointowaniu

**Krok A — zamknąć F-045 minimalną jakościową bramką Direct**: oddzielny obiekt/propozycja wypowiedzi, walidacja zgodności z dokładnym `CallTask`, zakaz nowych zobowiązań nawet w parafrazach, testy kontraktu i adversarial STT/JSON, fallback nie udający sukcesu. Stary `SCRIPT_GEMMA` bez zmian. Nie dodać nowej zbędnej zgody, nowych whitelist operatorów ani sztucznego limitu tur. Jeśli pierwsza próba ma być wyłącznie badawcza, odróżnić ją od akceptacji bezpiecznego produkcyjnego Direct.
 
**Krok B — przygotować rzeczywisty brief danych:** przy exact CLIR można zacząć od obecnego promptu, ale jawnie wykazać zgodność treści z aktualnym grantem, SIM i dozwolonym skutkiem. Dla następnych zadań zaimplementować `TaskBriefBuilder` z typowanymi faktami/skillsami i testem, że prewarm i active model dostają dokładnie ten sam brief; brak możliwości wstrzyknięcia sekretu bez zatwierdzenia polityki. F-044 zostaje OPEN do implementacji.

**Krok C — pojedynczy realny test właściciela:** zweryfikować na S22 dokładną tożsamość urządzenia, `IDLE`, brak aktywnej kampanii/retry, rolę dialera, Shizuku/media i ważny aplikacyjny grant dla dokładnego CLIR/target/SIM. Upewnić się, że opt-in nagrywanie RX/TX i transkryptów jest dozwolone w grancie i prawa rozmówcy uwzględnione. Właściciel wybiera w aplikacji **`Kampanie i zadania AI Calls → Start authorized CLIR enable — Gemma Direct`**. Nie inicjować rozmowy zdalnie żadnym zablokowanym narzędziem.

**Krok D — metryki i acceptance:** w prywatnym raporcie sprawdzić kolejno telefon `OFFHOOK`/exact-owned → STT → `dialogue_mode=gemma_direct` → `SPEAK/ASK_CLARIFY/DISCLOSE_PHONE/CONFIRM_EFFECT` i źródła → oryginalne teksty AI po zatwierdzeniu → TTS PCM → quick ACK oraz full TX (bajty, błędy, timestamps) → kolejne tury w tym samym `Conversation` → przyczynę rozłączenia/STOP/IDLE. Policz EOS→ACK, EOS→full TX, Gemma generate i STT/TTS osobno. Odsłuchać lokalne WAV i, jeśli możliwe, niezależnie sprawdzić słyszalność po drugiej stronie. **Nie uznać CLIR za włączony bez osobnego dowodu sieciowego.**
 
**Krok E — porównać tryby na tym samym typie zadania i scenariuszach**, jeśli są odpowiednie autoryzacje: samodzielność wypowiedzi, powtórzenia, liczba odrzuceń, fallbacks, opóźnienie, stop/revoke/remote hangup, prawidłowość faktów. Przechować prywatny materiał na S22/Macu; do GitHub tylko anonimowe liczniki i wynik.

## 8. Kryterium ukończenia następnego sprintu

- `F-045` nie daje nieautoryzowanego tekstu w negatywnych przypadkach; kod ma powiązane tests i Mac QA.
- Direct faktycznie generuje i wysyła **własne** zatwierdzone wypowiedzi w rozmowie, a nie tylko skrypt/ACK; co najmniej dwie realne tury z zachowanym kontekstem (kryterium testu, **nie** limit produktu).
- Osiągnięte i zapisane wszystkie fazy STT/Gemma/TTS/TX, brak dowodu nieautoryzowanego skutku, brak wkraczania w obcy Call; STOP i grant revoke zachowują pierwszeństwo.
- Dokładny stan końcowy S22 i brak spontanicznego retry; cel biznesowy CLIR zweryfikowany osobno.
- Rejestr `BLOCKERS.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ARCHITECTURE.md` i checkpointy mają prawidłowy aktualny HEAD/QA, bez mylenia host PASS z realnym wynikiem.
