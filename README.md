# AI Calls

**Modułowa aplikacja testowa Android** do porównywania STT, modeli językowych, dialogu ze skryptem i bez, instrukcji oraz TTS. Docelowy autonomiczny flow powstanie z elementów sprawdzonych w tych testach. Wspólny tor: własny Android Telecom `Call`, wymienne STT → model/strategia → TTS, zakres zadania/STOP, osobny dowód skutku i raport. Orange/CLIR jest jednym scenariuszem.

## Zakres laboratorium i modułowość — decyzja 2026-11-05

[Ocena kodu, szersze testy i plan wspólnej instalacji](docs/TEST_LAB_SCOPE.md). Są już dwa START: **skrypt + model** oraz **Gemma Direct (model bez skryptu)**. STT/model/TTS/głos wybiera się niezależnie. Runtime ma teraz wspólny typowany snapshot próby (profil, strategia i instrukcje Direct), przekazywany od prewarm do rozmowy. Scenariusz CLIR jest wydzielony ze wspólnego runtime. Dalsze prace: zapisywalny preset całego eksperymentu, adapter odbierania oraz kanały tekstu/pliku/mikrofonu. Nie wszystkie kombinacje są zakwalifikowane.

LiteRT-LM działa wewnątrz AI Calls; **Google AI Edge Gallery nie jest wymaganą aplikacją**. Shizuku pozostaje zewnętrznym serwerem uprawnień. Docelowo jeden pakiet instalacyjny; teraz rozwijamy laboratorium. PackageInstaller jest API systemu, nie dodatkowym APK. [Zmiana runtime i QA](docs/checkpoints/2026-11-05-lab-run-configuration.md).

## Wspólny runtime, skills/dane i historia

[Warstwy runtime rozmów](docs/CONVERSATION_RUNTIME.md): wspólny silnik i osobne scenariusze, profile przygotowania wychodzące/przychodzące, szyfrowany kontekst i referencje do sejfu. Wejście „Skills i dane rozmów” nie wymaga uruchomionego zadania. „Historia” w panelu czatu odczytuje istniejące prywatne zapisy; ekran jest neutralny wobec operatora i modelu. Przygotowanie odbioru jest architektoniczne — automatyczny odbiór telefonu pozostaje następną funkcją. [QA](docs/checkpoints/2026-11-05-conversation-modules.md).

## Przyjmowanie zadań bez dodatkowych pozwoleń

Polecenie testu z Codexa trafia do jednego aplikacyjnego START zadania. Usunięte są osobne nadawanie/odnawianie grantów i TTL 24 h; telefon nie wymaga dodatkowego zatwierdzenia. Aplikacja zapisuje dokładny zakres i egzekwuje Android/SIM/własny Call/STOP/fakty/skutki. Runner ma jawny `--dialogue-mode SCRIPT_GEMMA|GEMMA_DIRECT` i zbiera metryki obu trybów. [Audyt i dowody](docs/checkpoints/2026-11-05-task-intake-and-dialogue-ab.md).

## Stan na 2026-11-05 — LiteRT-LM i modele

- **LiteRT-LM 0.19.0** i [katalog modeli w aplikacji](docs/checkpoints/2026-11-05-litert-model-catalog.md): Gemma 4 E2B/E4B oraz Qwen3 1.7B/0.6B. Pobieranie z postępem/anulowaniem, import pliku, weryfikacja SHA-256 i wybór na następną rozmowę, bez zmiany STT/TTS/głosu. Wejście: „Modele językowe” na ekranie głównym lub przycisk katalogu w ustawieniach silników audio.
- **S22 native:** E2B, E4B i Qwen3 1.7B — polska odpowiedź i schemat JSON PASS. Qwen3 0.6B zainstalowany; błędna odpowiedź kontrolna po polsku, model eksperymentalny (F-059), bez cichego fallbacku.

## Wcześniejszy dowód audio i Orange

- **Zainstalowany na Samsung S22+ APK:** źródło `da09ea23618ccfb29704f0f52088b1472a28b30c`. Podpis zgodny, instalacja z zachowaniem danych. Lokalnie 705 app JVM + 263 Python, build/lint/audyty PASS.
- **Jeden debug bridge** (wspólny moduł i ekran) + jedna konsola Mac `scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py`: odczyt stanu/błędów, readiness profilu, bezpieczny zapis/reset profilu, cofanie istniejącego grantu, wyłączenie nagrywania, prywatny eksport oraz off-call instrumentacje. Nie umożliwia nadpisywania surowych grantów lub obchodzenia STOP/zgód.
- **Polska mowa syntetyczna:** Parakeet i Gemma Audio po naprawie F-056 — po 3 próbki, 0/16 błędów słów i 0/127 błędów znaków. To wynik małego zestawu syntetycznego, nie naturalnego korpusu. M4 nadal nie ma pełnego backendu rozmowy audio.
- **Piper:** oficjalny APK 1.1 i polska Gosia zainstalowane; native TTS odtwarza błąd wyboru głosu w zewnętrznym silniku, F-057 OPEN.
- **Orange live:** 1 pełna tura z Parakeetem, 5 z Android STT; Gemma E2B i Android TTS. Pierwsze EOS→TX: 35,0 / 22,4 s. Powrót IDLE i prywatny eksport PASS; brak dowodu wykonania CLIR. F-045/F-047/F-048 OPEN, F-049 wycofany.
- Użytkownik zauważył chowanie aplikacji podczas ADB; odtwarzanie wykazało PID stable i brak FATAL. W przekazanej rozmowie właściciel potwierdził otwarcie panelu USB, a ADB jego obecność; nie wymagaj ponownego ręcznego otwierania.

## Dokumentacja i przekazanie

- [Handoff do kolejnego czatu](docs/HANDOFF_NEXT_CHAT.md) · [Najnowsze testy audio/Orange](docs/checkpoints/2026-11-05-audio-quality-orange-acceptance.md) · [Baseline USB/S22](docs/checkpoints/2026-11-04-usb-debug-s22-acceptance.md)
- [USB/ADB diagnostic bridge](docs/USB_DEBUG_BRIDGE.md) · [ROADMAP](docs/ROADMAP.md) · [ARCHITEKTURA](docs/ARCHITECTURE.md)
- [AGENTS](AGENTS.md) · [BLOCKERS](BLOCKERS.md) · [SECURITY_PRIVACY](docs/SECURITY_PRIVACY.md) · [Indeks dokumentacji](docs/README.md)

**Wykonanie:** zgodnie z decyzją właściciela z 2026-11-04 rozwój i testy prowadzi lokalny Codex na Macu, bezpośrednio przez Git, Gradle i USB/ADB. Codex nie zleca pracy Local Agentowi; ten tryb jest zarezerwowany dla ChatGPT. Bez konkurencyjnych mutacji. `main`, `[skip ci]`, bez Work/Actions/force-push. Udzielone uprawnienia deweloperskie nie są grantem na połączenia lub skutki zewnętrzne.

Lokalna bramka hosta: `bash scripts/verify_host.sh`. Konsola telefonu: `python3 scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py status`; pozostałe komendy i warunki testów opisuje [USB debug bridge](docs/USB_DEBUG_BRIDGE.md).
