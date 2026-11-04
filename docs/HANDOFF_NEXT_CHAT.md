# AI Calls — aktualny handoff 2026-11-04, przejęcie przez lokalny Codex

**Najświeższy fizyczny dowód:** [USB debug S22 acceptance](checkpoints/2026-11-04-usb-debug-s22-acceptance.md). Zastępuje dawny phone-ready stan, w którym telefon był niedostępny, a Parakeet PL jeszcze nieprzetestowany. Przed kontynuacją sprawdź `main` HEAD oraz nowe receipts; nie odtwarzaj starszych planów.

**Bieżący tryb pracy:** [lokalne przejęcie Codex](checkpoints/2026-11-04-local-codex-takeover.md). Lokalny `main` został fast-forwardowany do `721073f6c9b0873028c19093a7c1951f09763ebc`; instalacja S22 nadal pochodzi z `418c84e2`. Bezpośredni lokalny USB status i pre/postcheck PASS: 2× IDLE, właściwy dialer, brak kampanii/retry, grant/skills/audio unchanged. Prywatny wynik: `~/Documents/AI-Calls/local-codex/2026-11-04-takeover-status.json`. W tym przejęciu nie uruchamiano instrumentacji ani połączeń.

**Lokalna bramka hosta PASS:** `bash scripts/verify_host.sh`, exit 0,
`host_quality_gate_green=true`; 766 JVM + 262 Python, lint wszystkich modułów,
build debug APK/AndroidTest APK i audyt jakości audio. Osobny anti-friction
PASS. Produktowe źródła nie były zmieniane; wynik kwalifikuje bezpośrednie
lokalne narzędzia, nie realny efekt rozmowy. Lokalne gałęzie: `main` +
`agent-control`; 19 starych gałęzi zachowano przed usunięciem w prywatnym,
zweryfikowanym Git bundle wskazanym w checkpoincie.

## Rzeczywiście ukończone

- Kod i aktualnie zainstalowany APK S22+ z `418c84e2a082930b58673a383969f7293ccd0168`. Podpisy APK zgodne ze starą instalacją; `aicalls-20261104-usb-debug-s22-install-smoke-v1` DONE/0, bez czyszczenia danych, panel test PASS, `2× IDLE` i grant/skills/audio unchanged. Mac source QA `aicalls-20261104-usb-debug-bridge-host-qa-v2` DONE/0 (Gradle unit/lint/assemble+test APK, Python, anti-friction, audio gate).
- Prywatna ścieżka USB/ADB Mac → aplikacja → nonce-bound JSON → Mac PASS; gotowość profilu Android STT/E2B/TTS i Parakeet oraz zliczanie błędów PASS, `aicalls-20261104-usb-debug-s22-readiness-v1`.
- Bez połączeń: `aicalls-20261104-usb-debug-s22-profile-mutability-v1` DONE/0 — negatywny test M4, Parakeet profil write/readback, reset stabilnego `ANDROID_STT → GEMMA_4_E2B → ANDROID_TTS_PL` i unchanged grant/skills.
- `aicalls-20261104-usb-debug-s22-private-export-e2e-v1` DONE/0 — 419 wpisów, timeline i WAV w prywatnym archiwum na Macu, integralność i uprawnienia PASS, **bez danych w Git/result/logcat**.
- `aicalls-20261104-usb-debug-s22-android-speech-probes-v1` DONE/0 — Android STT i TTS off-call PASS.
- `aicalls-20261104-usb-debug-s22-parakeet-polish-v1` DONE/0 — po raz pierwszy polski TTS→PCM16→Parakeet lexical overlap na S22 **PASS**, nie WER/CER.
- `aicalls-20261104-usb-piper-thermal-readonly-v1` DONE/0 — Piper APK ABSENT, MemAvailable ~2661 MiB, bateria 29,9°C. To nie jest pomiar współpracy modeli.
- Zgłoszone znikanie aplikacji: scenariusze uruchomienia i ADB nie ujawniły crasha (`PID stable`, `0 FATAL`); aplikacja przechodziła w tło podczas diagnostyki. Nie twierdzić, że każdy wariant UI został wizualnie przyjęty.

## Aktualne OPEN

- **Gemma E2B 4 synthetic turns:** historyczny S22 PASS na starszym kodzie bez zmiany backendu; nowy niezależny test w tym sprincie **NOT RUN**, zlecenie odrzucone przez system narzędzi. Nie obchodź odmowy przez inny transport. Jeśli będzie to potrzebne, użytkownik może sterować istniejącymi funkcjami na ekranie; wynik readiness nie zastępuje native 4-turn.
- Parakeet: rzeczywisty polski WER/CER, final segment ordering, latencja i joint RAM/thermal przy E2B.
- Piper: brak zewnętrznego `dev.ihorshevchuk.piper` i prawdziwego polskiego głosu offline.
- Gemma Audio Direct: syntetyczny native wcześniejszy PASS, profil disabled; pełne audio→dialog, RAM/thermal i polish QA OPEN.
- F-045 semantyczny RAW, F-047 końcowy RX na hangup, F-048 FactRequest/IdentityVault pozostają OPEN; brak zewnętrznego dowodu CLIR; F-049 **WYCOFANY**. `BLOCKERS.md` jest jedynym rejestrem statusów.

## Wykonanie i następny krok

- **Aktualna decyzja właściciela:** kontynuacja kodu i testów w lokalnym Codexie. Repo `MichalMatu/ai-calls`, checkout `/Users/michal/agent-workspace/repos/ai-calls/work`, `main`, commity `[skip ci]`. Git, Gradle i USB/ADB bezpośrednio; Codex nie zleca pracy Local Agentowi, którego tryb jest zarezerwowany dla ChatGPT. Bez konkurencyjnych mutacji, ChatGPT Work, GitHub Actions (także czytania statusu), nowych branchy i force-push.
- **Tylko dla sesji ChatGPT:** Mac host `macbook-michal`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, Local Agent 4.20.16. Host Ops: `MichalMatu/local-agent:agent-control`, `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/{tasks,task-results}/<id>.json`. Przeczytaj host `main` AGENTS/HOST_CONTROL_PLANE/CURRENT_HANDOFF, nie myl ze starą skrzynką `ai-calls:agent-control`. Codex używa narzędzi lokalnych.
- Dokładny S22+ `SM-S906B`, serial `RFCT70L7E8J`, pakiet `pl.michalmatu.aicallbridge`. Odkryj żywy ADB transport. Przed każdą instalacją/instrumentacją: **2× IDLE**, dialer role, nieaktywny AppOwnedCampaignService i brak retry, prywatne fingerprinty grant/skills/audio, po teście postcheck. Niczego nie kasuj.
- Wszystkie połączenia wymagają dokładnego, ważnego, odwoływalnego aplikacyjnego grantu cel/number/SIM/efekt/disclosure. Nigdy nie modyfikować zamrożonego Samsung/Shizuku PCM, VAD/ACK/TX, STOP. Prawdziwe WAV/transkrypty/identyfikatory prywatnie tylko na Macu i S22.
- **Panel już otwarty:** w przekazanej rozmowie właściciel odpowiedział „otwarte”, a poprzedni wykonawca potwierdził panel/PID/IDLE przez ADB. Nie wymagaj kolejnego ręcznego otwierania. Nie powtarzaj wykonanych STT/TTS/Parakeet/profil/eksport bez nowej przesłanki. Kontynuuj oddzielne pomiary jakości/termiki tylko po właściwym preflight.
- Wymagane dodatkowe lektury: [AGENTS](../AGENTS.md), [ROADMAP](ROADMAP.md), [ARCHITECTURE](ARCHITECTURE.md), [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md), [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md), [USB debug](USB_DEBUG_BRIDGE.md), [aktualny checkpoint](checkpoints/2026-11-04-usb-debug-s22-acceptance.md). Fizyczny media freeze przeczytać dopiero przed próbą jego zmiany.

## Przeniesienie

Wznów AI Calls z najświeższego `main` w lokalnym Codexie, sprawdź checkpoint USB/S22 oraz aktualne wyniki lokalne. Rozwijaj testy fizycznego S22 małymi krokami, bez ponownych testów już zakończonych. Używaj istniejącej konsoli USB i `bash scripts/verify_host.sh`; nie twórz drugiego runnera ani nie wymagaj ręcznej nawigacji dostępnej przez ADB. Nie obchodź odmowy narzędzia ani grantu/STOP.
