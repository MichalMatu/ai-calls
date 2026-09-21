# Ready-to-paste prompt — maximum 10-hour Orange mapping session

Kontynuuj pracę nad repozytorium `MichalMatu/android-ai-call-bridge` przez Local Agent. Durable product branch: `main`. Control/evidence branch: `agent-control`.

## Autoryzacja tej przyszłej sesji

Autoryzuję w tym czacie iteracyjne połączenia testowe **wyłącznie** na dokładny allowlisted numer Orange `510100100`, potrzebne do discovery drzewa. Jedno aktywne połączenie naraz. Nie pytaj mnie o zgodę przed każdym pojedynczym bezpiecznym testem w ramach tej sesji. Ta autoryzacja nie obejmuje żadnego innego numeru.

Maksymalny czas całej pracy mappingowej: **10 godzin od startu tej sesji**. Zakończ wcześniej, jeśli cele zostaną osiągnięte albo wystąpi warunek STOP. Nie uruchamiaj jednego Local Agent taska ponad limity daemona; pracuj iteracyjnie kolejnymi bounded taskami.

## Najpierw świeży stan

Przeczytaj świeżo `AGENTS.md`, `README.md`, `docs/HANDOFF_NEXT_CHAT.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/PHASE2D_FREEZE_2026-09-18.md` i `service-packs/orange/service_tree.v1.json`.

Pobierz świeże SHA `main`, `agent-control:.agent/status/daemon.json`, najnowszy terminalny wynik Local Agenta, `git status`, worktrees i branche. Używaj wyłącznie świeżego `agent_binding`. Pracuj wyłącznie na tym repozytorium.

## Cel

Iteracyjnie rozkoduj możliwie dużo **low-risk** drzewa Orange, zwiększając evidence-backed service pack bez naruszania authority i bez modelowej improwizacji rozmowy.

```text
natural user request
 -> bounded intent resolver
 -> existing service_id only
 -> authoritative service registry
 -> VERIFIED deterministic service-pack route
 -> existing CallPlan/workflow/confirmation/commitment authority
 -> approved deterministic speech
 -> media transport
```

LLM nie prowadzi rozmowy z Orange. LLM nie tworzy runtime speech/action/service IDs.

## Pętla discovery

Dla każdego kolejnego bezpiecznego seeda:

1. wybierz jedną niezweryfikowaną usługę/gałąź o możliwie niskim ryzyku;
2. zachowaj istniejące evidence — nigdy nie nadpisuj poprzedniej próby;
3. zdefiniuj jeden exact reviewed, niecommitujący utterance/action ID;
4. RED test;
5. minimal GREEN przez istniejący `CallPlan + PhraseMatrix` / reviewed-action path;
6. targeted tests;
7. `bash scripts/verify_host.sh`;
8. jeśli produkcyjny Kotlin się zmienił, zainstaluj aktualny APK przed hardware testem;
9. wykonaj jeden bounded live call do dokładnego `510100100` z `OBSERVE_ONLY` follow-up;
10. wymagaj `backend_generate_calls=0`;
11. zapisz bounded transcript/outcome;
12. wymagaj cleanup i phone final state `IDLE`;
13. zaktualizuj `service-packs/orange/service_tree.v1.json` wyłącznie na podstawie fizycznego evidence;
14. dodaj/aktualizuj test service tree;
15. ponownie uruchom targeted tests + `verify_host.sh`;
16. commit/push małego checkpointu na `main`;
17. przejdź do kolejnej gałęzi.

## Evidence semantics

Ściśle odróżniaj `VERIFIED` node/observed edge od `VERIFIED` service route. Reprompt może tworzyć `VERIFIED` observed edge, ale nie promuje automatycznie service route. `DISCOVERED` oznacza, że istnieje seed/evidence, lecz kompletna trasa nie jest dowiedziona.

## Bariery i bezpieczeństwo

Nie wymyślaj ani nie podawaj PESEL, numeru klienta/umowy, kodu SMS, danych płatniczych ani innych credentials. Jeśli gałąź dochodzi do auth/customer-data request, payment, zakupu, aktywacji/dezaktywacji, zmiany taryfy/umowy/pakietu lub innego commitmentu, zapisz barrier, zakończ **tę gałąź** bez potwierdzania i przejdź do innej low-risk gałęzi.

Nigdy nie dzwoń pod inny numer. `OBSERVE_ONLY` nigdy nie mówi. Nie dodawaj arbitrary free-text speech surface.

## Resolver

Zachowaj aktualny generic `serviceintent` contract. Resolver może wyłącznie proponować existing `service_id` albo null + confidence/metadata. Validator ponownie sprawdza registry/pack/status/generation/authority. `DISCOVERED` może klasyfikować, ale wykonanie pozostaje `ROUTE_NOT_VERIFIED`.

Nie podłączaj resolvera tak, aby jego output automatycznie stawał się TTS/TX.

## Architektura i scope

Nie zmieniaj frozen Samsung media ani `privileged-helper/`. Nie rób szerokiego refactoru. Nie zamieniaj diagnostic runnera w product orchestrator. Nie wracaj do Edge Gallery/llama.cpp/ChatGPT relay eksperymentów bez bezpośredniej potrzeby.

Obecny mały `OrangeLiveAction` może pozostać enumem. Przejdź na data-driven reviewed action catalog tylko jeśli stanie się realnym bottleneckiem i zachowasz exact reviewed speech, typed IDs, fail-closed unknown entries, CallPlan validation, output approval, exact target allowlist i zero backend generation.

## Priorytety seedów

Preferuj informacyjne, low-risk gałęzie. Jeżeli obecny utterance tylko repromptuje, zachowaj edge i spróbuj **oddzielnego** reviewed operator-native wording zamiast nadpisywać stare evidence.

## Warunki STOP przed 10 godzinami

Przerwij mapping wcześniej i zostaw czysty checkpoint, jeśli pojawi się fundamentalny problem architektoniczny, utrata fail-closed invariantów, powtarzający się failure cleanup do `IDLE`, podejrzenie regresji frozen Samsung layer, niejasność target authorization, nierozwiązywalny konflikt/dirty workspace lub konieczność credential/commitment bypassu, aby kontynuować wszystkie pozostałe gałęzie.

## Końcowy raport

Na końcu podaj:

- liczbę wykonanych połączeń;
- sprawdzone service seeds/utterances;
- nowe nodes/edges;
- co osiągnęło `VERIFIED` i na jakim poziomie (edge vs service route);
- znalezione bariery;
- gałęzie nadal `DISCOVERED`;
- targeted test status;
- Orange service-tree tests status;
- final `bash scripts/verify_host.sh` status;
- clean `git status`;
- brak zmian w frozen media;
- phone final state = `IDLE`;
- końcowe SHA `main`;
- aktualny `docs/HANDOFF_NEXT_CHAT.md`.

Nie pracuj dłużej niż 10 godzin.
