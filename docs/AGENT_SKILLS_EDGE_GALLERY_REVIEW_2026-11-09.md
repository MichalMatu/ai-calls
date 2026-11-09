# AI Calls — Google AI Edge Gallery Agent Skills vs obecne Skills (2026-11-09)

**Aktualizacja G4.3 (2026-11-09):** aktywne metadane `name/description/capability` są wersjonowane i wpływają na selekcję prompt Skills; Lab pokazuje zmiany per-turn. `LabToolProposalPolicy` waliduje tylko typowane propozycje `END_CALL`, nigdy nie wykonuje efektu. Mac i fizyczny S22 2/2 metadane/UI + 1/1 native E2B, routing 4/4, pamięć, STOP PASS. Nadal **nie** używa to ładowanych plików `SKILL.md` i nie wykonuje arbitrary JS lub dowolnych narzędzi z promptu. [Finalny checkpoint](checkpoints/2026-11-09-g4p3-metadata-trace-tools-final.md).

**Nowy fizyczny stan (2026-11-09):** po tej analizie G4.1/G4.2 rozwinęły statyczny selector do wersjonowanych propozycji per turn i Lab-only odświeżania natywnej konwersacji. E2B/GPU S22 **4/4 tur, 3 dynamiczne przełączenia, pamięć i STOP PASS**. To wciąż **prompt Skills**, nie pliki `SKILL.md`, execution JS lub pełna kontrola narzędzi; dawny opis „wyłącznie statyczny” poniżej ma charakter historyczny. [Finalny checkpoint](checkpoints/2026-11-09-g4-dynamic-skills-e2e-final.md).

## Co faktycznie robi obecny AI Calls

`ConversationSkillLibrary` przechowuje wersjonowane instrukcje i presety w szyfrowanym magazynie. `ConversationSkillSnapshot.instructions()` scala **wszystkie aktywowane** instrukcje wybranego presetu; `ConversationInstructionBuilder` dołącza je do jednego statycznego promptu sesji. Jest to mechanizm **prompt Skills**, a nie gotowy `Agent Skills` Google AI Edge Gallery. Wykonywanie proponowanych narzędzi pozostaje niezależne od promptu i podległe autoryzacji AI Calls.

F-156 pokazał na fizycznym S22, że model E2B/GPU przy długim prompcie potrafił ignorować instrukcję umieszczoną zbyt wcześnie. Przeniesienie instrukcji bliżej wymagań wyjścia, **po zasadach bezpieczeństwa**, dało scoped A/B PASS: aktywny znacznik pojawiał się, nieaktywny nie. To uzasadnia poprawę kompozycji promptu, nie dowodzi ogólnego przestrzegania dowolnej instrukcji.

## Co udostępnia Gallery

Źródło Google: [oficjalne instrukcje Agent Skills / SKILL.md](https://github.com/google-ai-edge/gallery/blob/main/skills/README.md), [Google AI Edge Gallery overview](https://gemma.ai/deployment/ai-edge-gallery/).

- **Text-only `SKILL.md`:** katalog z metadanymi `name` i `description` oraz treścią instrukcji. Nazwy i opisy są prezentowane modelowi; gdy wybierze odpowiedni Skill, aplikacja wprowadza pełne instrukcje do kontekstu. Nadal używa **promptowania**, lecz modułowo i selektywnie, zamiast stałego wstrzykiwania wszystkiego.
- **JavaScript Skills:** oprócz `SKILL.md` mają skrypt, który Gallery wykonuje w izolowanym WebView po narzędziowym żądaniu modelu. Nie oznacza to, że sama Gemma potrafi uruchamiać JavaScript.
- **Native Skills:** mapują deklaracje na zarejestrowane w aplikacji intencje/narzędzia. Akcje wykonuje **aplikacja**, nie wagi modelu. W Gallery przykłady obejmują wiadomości i e-mail; inne wymagają implementacji w kodzie aplikacji.
- Nie jest to ukryty „tryb Gemmy” ani bezpośredni zamiennik backendu LiteRT-LM. Skopiowanie formatu `SKILL.md` bez selektora i kontrolera narzędzi **nie** stworzy agentowych umiejętności.

## Decyzja architektoniczna do eksperymentu

**Nie wymieniać obecnej biblioteki z historią rewizji.** Dodać *warstwę odkrywania i selekcji umiejętności* zainspirowaną Gallery, korzystając z obecnego repozytorium `ConversationSkillLibrary` i jednego runtime.

1. **Stałe gwarancje** (rola, zakres zadania, STOP, zakres danych i ograniczenia działań) pozostają na poziomie aplikacji i nadrzędnej instrukcji. Skill nie rozszerza grantu, nie zmienia polityki ujawniania ani nie może obejść STOP.
2. **Opis modułu:** identyfikator, wersja, krótki opis/typ sprawy, pełna instrukcja, ewentualne typowane narzędzie i jego preconditions. Rozdzielić styl zawsze aktywny od umiejętności przywoływanej kontekstowo.
3. **Selekcja przed każdą relewantną turą:** porównać (a) obecny pełny prompt, (b) ładowanie tylko adekwatnych Skills. Wybór przez jawny stan scenariusza lub klasyfikator; nie uruchamiać dla każdego słowa osobnej kosztownej generacji bez uzasadnienia. Zapis zdarzeń selektora ma być syntetyczny i pozbawiony tekstu rozmowy w publicznych logach.
4. **Narzędzia:** model może jedynie zaproponować typowaną akcję; `CampaignAuthorizationGrant`, `FactDisclosurePolicy`, kontroler właściciela `Call` i `CallCommitmentGate` niezależnie weryfikują możliwość i skutek. Nie importować arbitralnego wykonywalnego kodu z URL do telefonii.
5. **Weryfikacja w Test Lab:** ten sam model, dokładnie ten sam prompt użytkownika i przybliżony budżet kontekstu, wersja aktywna/nieaktywna, osobna kontrola celu, korekt, zakończenia i izolacji danych. Mierzyć *cold vs warm*, długość wyjścia, koszt wyboru Skill oraz liczbę tur, w których instrukcja została wykonana. Minimum trzy niezależne powtórzenia na wariant dla oceny trwałej decyzji.

**Ocena:** moduły on-demand mogą ograniczyć przeładowanie promptu i pozwolą rozszerzać funkcje, lecz wybór umiejętności też ma koszt czasowy i może popełnić błąd. Korzyść wymaga fizycznego A/B, nie wynika automatycznie z nazwy „Agent Skills”.

## Rozmiar modelu i wydajność

Google dokumentuje `LiteRT-LM` dla Gemma 4 E2B i E4B oraz możliwość **Multi-Token Prediction** na GPU: [Gemma 4 LiteRT-LM](https://developers.google.com/edge/litert-lm/models/gemma-4). Publikowane wyniki S26 Ultra są **nieprzenoszalne liczbowo na S22**. Po E2B vs E4B na identycznych lokalnych promptach trzeba sprawdzić ewentualną obsługę MTP w rzeczywiście zainstalowanej wersji LiteRT-LM i osobno zmierzyć regresję CPU/GPU, pamięci i jakości. Nie włączać niezbadanych optymalizacji w ścieżce produkcyjnej telefonu.

## Granice i status

Pozycja dokumentacyjna: **DESIGN_REVIEW**, nie twierdzenie o wdrożeniu Google Agent Skills. Obecnie działa własny system promptów i istniejący framework tool proposals. Rozbudowa pełnego selektora i importu SKILL.md pozostaje oddzielnym eksperymentem, nie zatwierdzoną migracją. Nie dotykać osobistych presetów ani nie wykonywać realnych połączeń w eksperymencie.
