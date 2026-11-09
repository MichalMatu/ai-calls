# AI Calls

Androidowe, modułowe laboratorium STT → model/Skills → TTS oraz kontrolowanych scenariuszy telefonicznych. Obecnie służy do weryfikowania komponentów i ich integracji; nie jest jeszcze dowiedzionym, kompletnym wykonawcą dowolnych zadań ani zamkniętym archiwum audio.

## Aktualny stan — 2026-11-09

- **Skills i dwa modele — F-156 VERIFIED_SCOPED, S22 18/18 tur:** po poprawie miejsca aktywnego Skill w promptcie Gemma E2B poprawnie wykonała instrukcję tylko gdy była włączona; fizyczny GUI edit/disable/revision/reopen działa w izolowanym, szyfrowanym magazynie. Na Samsungu S22 E2B i E4B (oba GPU, kontekst 2048) przeszły te same 18 syntetycznych tur i kryteria: marker on/off, pamięć i sprostowanie, cytat pożegnania vs prawdziwy `end_call`, JSON. E4B była około 3× wolniejsza cold/warm, bez wykazanej przewagi jakości. Domyślny profil E2B niezmieniony, brak połączeń. [Checkpoint](docs/checkpoints/2026-11-09-e2b-e4b-agent-skills-differential.md).
- **Agent Skills (Google Edge Gallery):** obecnie AI Calls ma własne wersjonowane *prompt Skills*, nie pełny mechanizm `SKILL.md` z selekcją na żądanie i narzędziami. [Analiza możliwości](docs/AGENT_SKILLS_EDGE_GALLERY_REVIEW_2026-11-09.md) zaleca oddzielny eksperyment nad selekcją kontekstową z zachowaniem własnej autoryzacji, nie bezpośrednią podmianę kontrolera telefonii.

- **Gemma i kontekst Laboratorium (F-155 VERIFIED):** S22 potwierdził rzeczywistą `GEMMA_4_E2B` na **GPU**. Przy identycznych krótkich promptach pierwszy cold turn 2048/4096 trwał **28,57/27,23 s**, ciepły kolejny **3,32/3,63 s**. Dlatego średnia 16–23 s z krótkich sesji nie reprezentuje rozgrzanej generacji. Dostępny jest edytowalny budżet kontekstu **tylko Lab** (domyślnie 4096) i live diagnostyka: backend, ładowanie silnika, utworzenie konwersacji, generowanie, średnia i RAM/PSS. Call nadal 2048. Mac full QA PASS, S22 nowy ekran + regresja Skills/STOP **4/4 PASS** bez wipe i bez Call. [Checkpoint](docs/checkpoints/2026-11-09-f155-lab-context-latency-diagnostics.md).

- **S22 nawigacja:** pełny Mac quality gate i fizyczna macierz **10/10 PASS** (kontrakt 5/5, tab flow 2/2, ekrany ustawień 3/3), F-151 VERIFIED. NAV-01 pozostaje PARTIAL dla długiego back-stack/restart.
- **USB Lab:** debug APK/AndroidTest na S22 bez wipe, `AndroidUsbLabControlContractTest` **3/3 PASS** oraz prawdziwe `status/config-get/ram` **3/3 PASS**, F-152 VERIFIED. USB Lab działa także z inną aplikacją na pierwszym planie (ADB receiver, bez UI): `aicalls-usb-lab-background-two-turn-20261109-v3` DONE/0, E2B 4096 **2/2 tury**, prywatny zapis na Mac oraz STOP, telefon IDLE. **F-153 VERIFIED**. Bez wymagania AI Calls/Launcher foreground przy off-call USB.
- **Jednoprzebiegowy odbiór GUI/USB i AI Skills:** S22: **6/6 AndroidTest PASS** po naprawie starego testu tekstowego (F-154 VERIFIED), **1/1 GUI/USB shared model lease PASS** (F-150 VERIFIED dla współdzielonego kontrolera), **3 presety × 2 tury Gemmy = 6/6**, przypomnienie losowego kodu **3/3**, prywatny zapis i STOP **3/3**, odmowy takeover/resume/owned-stop przy IDLE **3/3**. To nie jest pozytywny test w prawdziwym Call ani dowód wpływu niestandardowych Skills na wypowiedzi. [Checkpoint](docs/checkpoints/2026-11-09-single-pass-ui-usb-ai-skills-acceptance.md).
- **Następny etap:** faktyczne operacje na Skills w aplikacji — edycja, aktywacja, wyłączenie i wersjonowanie, trwałość oraz porównanie prawdziwych odpowiedzi Gemmy dla aktywnego/nieaktywnego Skill. F-148 semantyka długiego dialogu, pełne audio i pozytywne takeover/resume/owned STOP w osobno autoryzowanym Call nadal otwarte. [Handoff](docs/HANDOFF_NEXT_CHAT.md).

## Stan historyczny — 2026-11-08

**Aktualny kod sprzed zbiorczego checkpointu:** `main@af571f06a7a34b05a14a8e0b54b8642f40a89e57`. Od stanu UX-11 dodano rzeczywisty Lab i nowy kanał USB; przy wznowieniu należy odczytać świeży HEAD.

- **UX-12 (tail):** historia szczegółów z kontekstem, nowe prywatne i deterministyczne zbieranie screenshotów; targeted QA 14/14 PASS; pełny host gate 365/365 PASS, archiwalne zdjęcia S22 17/17 walidacji PASS; świeże screenshoty kodu UX12 nadal NOT_RUN. [Checkpoint](docs/checkpoints/2026-11-08-ux12-final-source-closeout.md).
- **UX:** sześć zakładek Start / Historia / Agent AI / Laboratorium / Modele / Ustawienia; UX-11 zakończony i zamrożony poza błędami funkcjonalnymi P0/P1. Czat icon-only tylko podczas aktywnego feedu; Sejf standardowy + własne pola, czytelny Lab.
- **Mac:** najnowszy `aicalls-usb-lab-shared-native-lease-mac-qa-20261108-v4` **DONE/0**, 370/370 Python, Gradle/JVM, lint, APK/AndroidTest build, audyt audio PASS. Starsze testy UX zachowane w ich checkpointach.
- **S22:** **28/28 UI** + **2/2 syntetyczny aktywny chat PASS**, **18 prywatnych screenshotów**; instalacja bez kasowania danych i potwierdzone IDLE przed/po.
- **Funkcje offline:** prawdziwy lokalny model zakończył **2 tury i STOP (PASS)**; polski TTS → PCM → Parakeet STT **PASS** na testowym tekście.
- **Bramki otwarte:** nowy odbiornik USB na S22, dodatnie interwencje Call nowym kanałem, pełne audio/manual takeover, rezultat zewnętrzny, pełna ocena semantyczna i F-148/F-150. Historyczne dowody audio/Call nie są odbiorem nowego kanału.

## Gemma i prywatny kanał USB — 2026-11-08

- **Gemma E2B 4096:** trzy realne sesje po 13 tur, **39/39 technicznie PASS** na S22; heurystyki 11/13, 13/13, 11/13; human semantic UNASSESSED. 2048 dwukrotnie zatrzymało się w turze 10, 8192 na S22 NOT_RUN.
- **Kanał USB/ADB:** prywatne START/SEND/STOP, RAM/status, konfiguracja audio i incoming, kontrolowane przejęcie/wznowienie/STOP własnego Call. Kod i Mac QA PASS, nowa APK S22 NOT_RUN. [Kompletny checkpoint](docs/checkpoints/2026-11-08-usb-lab-telephony-full-checkpoint.md).

## Co dalej

**Nowy etap to testy funkcjonalne ekran po ekranie i funkcja po funkcji — nie kolejny sprint kosmetyczny.** Zanim cokolwiek oznaczysz PASS, sprawdź działanie, efekt/stan, STOP, restart i prywatność w faktycznym dopuszczonym zakresie. Aktywna macierz to [FUNCTIONAL_ACCEPTANCE_MATRIX](docs/FUNCTIONAL_ACCEPTANCE_MATRIX.md).

[Kompletny checkpoint](docs/checkpoints/2026-11-08-pre-functional-full-checkpoint.md) · [Bieżący handoff](docs/HANDOFF_NEXT_CHAT.md) · [Prompt do nowego czatu](docs/NEXT_CHAT_PROMPT.md) · [Roadmapa](docs/ROADMAP.md) · [Rejestr blokad](BLOCKERS.md)

## Architektura i tryb pracy

Moduły: `app`, `conversation-core`, `conversation-tools`, `audio-bridge`, `realtime-client`, `privileged-helper`. Jeden `AndroidConversationRuntime` współdzieli porty STT/model/TTS między INCOMING i OUTGOING, przy zachowaniu rzeczywistej kontroli własnego Android Call, STOP, zgód oraz zasad udostępniania danych.

Pracować **GitHub Connector + Mac Local Agent**; dla izolowanych testów używać sandboxu. Bez Codex, ChatGPT Work, GitHub Actions (również odczytu statusów), tworzenia zbędnych gałęzi i force-push. Commity `[skip ci]`. Prywatne zdjęcia, WAV, numery i transkrypty zostają na urządzeniu/Macu. Źródła normatywne: [AGENTS](AGENTS.md), [Security](docs/SECURITY_PRIVACY.md), [Laboratorium](docs/TEST_LAB_SCOPE.md).
