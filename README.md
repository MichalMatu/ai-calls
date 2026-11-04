# AI Calls

Lokalny agent Android do wykonywania **konkretnie autoryzowanych zadań telefonicznych**: połączenie własnym `Call`, polskie STT → Gemma 4 E2B → TTS, kontrola grantu/STOP, dowód skutku i raport. CLIR jest przypadkiem testowym, nie ograniczeniem produktu; rezerwacje i inne efekty wymagają osobnych prawdziwych wykonawców.

## Stan na 2026-11-04

- **Ostatni zainstalowany produkcyjny APK S22:** źródło `0d0fdfe22e9b002c8a9dc557ad5d95a882aa5976`. Testy off-call STORE **6/6**, ekran i **4 syntetyczne tury Gemma** PASS. Nowszy `main` zawiera tylko testy, narzędzia QA i dokumentację — nie instalowano nowego APK. **Testowy AndroidTest APK** przygotowany prywatnie na Macu (source `9d56cc65`, SHA-256 `8c6e0525…02302c`) i czeka na bezpieczny preflight telefonu.
- **M1** profil Wejście / Model / Wyjście: Mac + S22 off-call PASS. **M2 Parakeet PL:** model 614 588 752 B/SHA-256 na S22 i syntetyczny native PASS, jakości polskiej mowy jeszcze nie zmierzono. **M3 Piper PL:** adapter i Mac PASS; zewnętrzny silnik/głos **nieobecny na S22**. **M4 Gemma Audio Direct:** native syntetyczny PASS, eksperymentalne wejście audio→tekst; profil **nadal wyłączony**, pełny dialog i RAM/thermal OPEN.
- **Ostatni kompletny Mac QA:** `aicalls-20261104-hostonly-audio-qualitygate-final-postfix-v4` DONE/0 na `2c2dac7f6ea51f85d1dc3fc4db4a5f3ad41c4774`: Gradle unit/lint/build/AndroidTest APK, 10 mutacji QA, 258 Python, anti-friction PASS. Test polskiego TTS→Parakeet **kompiluje się na Macu**, ale **nie był wykonany na S22**.
- Historyczny prawdziwy RAW live: 8/8 i 7/7 tur STT/Gemma/lokalnego TX; **nie jest to** dowód skutku CLIR ani odsłuchu po stronie operatora. F-045/F-047/F-048 pozostają OPEN; F-049 **wycofany**.

## Przejdź do właściwego dokumentu

- **Nowy czat / następna akcja:** [HANDOFF_NEXT_CHAT](docs/HANDOFF_NEXT_CHAT.md).
- **Jednoznaczny checkpoint z dowodami i procedurą S22:** [phone-ready checkpoint](docs/checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md).
- [ROADMAP](docs/ROADMAP.md) · [ARCHITECTURE](docs/ARCHITECTURE.md) · [AGENTS](AGENTS.md) · [BLOCKERS](BLOCKERS.md) · [bezpieczeństwo](docs/SECURITY_PRIVACY.md).
- [Diagnostyka USB/ADB i lokalny panel](docs/USB_DEBUG_BRIDGE.md) · [Indeks dokumentacji](docs/README.md) oddziela aktualne reguły od historycznych pomiarów.

**Wykonanie:** GitHub Connector + dokładny Mac Local Agent, bez Codex/Work/GitHub Actions/force-push. Uprawnienia do pracy programistycznej **nie** zastępują aplikacyjnego grantu na połączenie, ujawnienie danych lub zmianę usługi.
