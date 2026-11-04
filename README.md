# AI Calls

Lokalny agent Android do wykonywania **konkretnie autoryzowanych zadań telefonicznych**: własny Android Telecom `Call`, polskie STT → Gemma E2B → TTS, autoryzacja grantu/STOP, osobny dowód skutku i raport. CLIR jest scenariuszem testowym, nie ograniczeniem produktu.

## Stan na 2026-11-04 — nowy USB debug sprint

- **Zainstalowany na Samsung S22+ APK:** źródło `418c84e2a082930b58673a383969f7293ccd0168`. In-place install z zachowaniem danych, podpis zgodny, kontrola `2× IDLE`, dialera, kampanii i retry, grant/skills/audio unchanged. Mac host QA i S22 USB panel/UI smoke **PASS**.
- **Jeden debug bridge** (wspólny moduł i ekran) + jedna konsola Mac `scripts/aicall_tools/diagnostics/ai_calls_usb_debug.py`: odczyt stanu/błędów, readiness profilu, bezpieczny zapis/reset profilu, cofanie istniejącego grantu, wyłączenie nagrywania, prywatny eksport oraz off-call instrumentacje. Nie umożliwia nadpisywania surowych grantów lub obchodzenia STOP/zgód.
- **S22 off-call PASS:** Android STT, Android TTS, polski syntetyczny TTS→Parakeet lexical-overlap, profile Parakeet zapisz/odczytaj/reset, log/error counters, prywatny eksport 419 wpisów WAV/transkryptów lokalnie na Macu. To nie jest WER/CER ani realny test rozmowy. **Piper APK ABSENT**.
- **Gemma:** historyczny 4-turn syntetyczny S22 PASS sprzed aktualnego sprintu; nowe odrębne uruchomienie zostało zablokowane na poziomie zlecenia i nie zostało wykonane. Gemma Audio M4 nadal disabled; brak pełnego audio→dialogu, RAM/thermal i kwalifikacji. F-045/F-047/F-048 pozostają OPEN; zewnętrzny skutek CLIR niepotwierdzony; F-049 wycofany.
- Użytkownik zauważył chowanie aplikacji podczas ADB; odtwarzanie wykazało PID stable i brak FATAL. Ręczny odbiór wizualny panelu nadal do wykonania.

## Dokumentacja i przekazanie

- [Handoff do kolejnego czatu](docs/HANDOFF_NEXT_CHAT.md) · [Checkpoint USB/S22](docs/checkpoints/2026-11-04-usb-debug-s22-acceptance.md)
- [USB/ADB diagnostic bridge](docs/USB_DEBUG_BRIDGE.md) · [ROADMAP](docs/ROADMAP.md) · [ARCHITEKTURA](docs/ARCHITECTURE.md)
- [AGENTS](AGENTS.md) · [BLOCKERS](BLOCKERS.md) · [SECURITY_PRIVACY](docs/SECURITY_PRIVACY.md) · [Indeks dokumentacji](docs/README.md)

**Wykonanie:** GitHub Connector i dokładny Mac Local Agent; `main`, `[skip ci]`, bez Codex/Work/Actions/force-push. Udzielone uprawnienia deweloperskie nie są grantem na połączenia lub skutki zewnętrzne.
