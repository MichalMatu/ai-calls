# AI Calls — aktualny handoff 2026-11-05, LiteRT-LM i modele

**Aktualny zakres — LiteRT-LM 0.19.0 i katalog modeli:** [szczegóły / dowody](checkpoints/2026-11-05-litert-model-catalog.md). E4B i Qwen3 1.7B zainstalowane na S22 przez aplikacyjny import z SHA-256; Qwen3 0.6B pobrany rzeczywiście przyciskiem w aplikacji, również SHA-256 PASS i transfer po odtworzeniu Activity. E2B nie pobierano ponownie. E2B/E4B/Qwen3 1.7B: native polska odpowiedź i klasyfikacja JSON PASS. Qwen3 0.6B: loader/GPU działa; kontrolna odpowiedź PL błędna i niedomknięty JSON. Także aktualny eksport `f3f204ce` dla 0.19.0 (343490560 B / `bf1aba9c…`) odtwarza problem. F-059 OPEN dla tego wariantu w bieżącym pipeline; pozycja pozostaje eksperymentalna. Jeden katalog, jeden serwis transferu, jeden AudioEngineProfileStore, wybór modelu niezależny od STT/TTS/głosu. Nie uruchamiano historycznego odrzuconego probe 4-turn; nowe testy dotyczą integracji modeli i nowej wersji runtime.

**QA tego zakresu:** 765 JVM (704 app) + 264 Python, lint wszystkich modułów, APK app/AndroidTest i audyty PASS. Native: trzy większe modele PL+JSON PASS; dwa warianty Qwen3 0.6B PL/JSON FAIL (zachowane dowody); import/pobranie/hash, rekreacja ekranu, wybór i zachowanie STT/TTS/głosu PASS. F-060 RED→GREEN na S22: powrót z katalogu odświeża ustawienia i opis na ekranie głównym. Gemma Audio i Parakeet PL regresje PASS. Nie zmieniano granic Samsung/Shizuku. Prywatne receipts `~/Documents/AI-Calls/local-codex/2026-11-05-model-manager/`.

**Koniec zakresu:** 2× IDLE, kampania nieaktywna, retry false. Profil Android STT/E2B/Android TTS i fingerprints grant/skills/audio dokładnie jak na początku, brak nowych prób telefonicznych. Usunięto wyłącznie własne kopie staging importów; aktywne modele i prywatne dowody zachowane. Lokalne gałęzie main + agent-control. Ostatni raport Local Agent idle/null; Codex nie wysyłał zleceń.

## Poprzedni przebieg audio/live

**Poprzedni dowód audio/live:** [polskie audio + Orange](checkpoints/2026-11-05-audio-quality-orange-acceptance.md). Kod i zainstalowane APK: `da09ea23618ccfb29704f0f52088b1472a28b30c`. Dokumentacja może mieć późniejszy HEAD. Parakeet i Gemma Audio: po 3 próbki syntetyczne, 0/16 błędów słów i 0/127 błędów znaków. F-056 naprawiony: instrukcja przed AudioBytes. M4 nadal nie jest pełnym backendem rozmowy audio.

**Orange live:** Parakeet → Gemma E2B → Android TTS: około 77 s, 1 pełna tura. Android STT → Gemma E2B → Android TTS: około 165 s, 5 pełnych tur i `DISCLOSE_AUTHORIZED_FACT`. Pierwsze EOS→TX: 35 046 / 22 447 ms. Końcowy `RX_INTERRUPTED` w drugiej rozmowie. F-042/F-047 nadal OPEN; brak dowodu wykonania CLIR i nowej niezależnej weryfikacji sieci. F-048 RAW nie jest zamknięty przez fakt działający w SCRIPT.

**Piper:** oficjalny APK 1.1 `0295dce` i polska Gosia zainstalowane. Native TTS FAIL `tts_set_voice_failed`; default voice null mimo widocznego głosu. F-057 w zewnętrznym silniku, bez fallbacku w AI Calls. E4B jest obecnie zintegrowany w katalogu powyżej.

**QA i cleanup:** 705 testów app JVM + 263 Python, build debug/AndroidTest APK, lintDebug, audio audit i anti-friction PASS. Końcowe 2× IDLE, kampania nieaktywna, retry false. Profil przywrócony do Android STT/E2B/Android TTS; audio i skills fingerprint identyczne ze startem. Magazyn grantu zmienił hash przy rzeczywistych próbach (`attempts_used=11`); nie wystawiano nowych grantów, każdy start przeszedł app-owned preflight READY. Prywatne receipts: `~/Documents/AI-Calls/local-codex/2026-11-05-audio-engines/`. Eksport USB PASS, 29 sesji (przed testami 27). Lokalne gałęzie `main` + `agent-control`.

## Historyczny baseline USB 2026-11-04

- Baseline APK S22+ z `418c84e2a082930b58673a383969f7293ccd0168`, zastąpiony powyższym kodem. Podpisy APK zgodne ze starą instalacją; `aicalls-20261104-usb-debug-s22-install-smoke-v1` DONE/0, bez czyszczenia danych, panel test PASS, `2× IDLE` i grant/skills/audio unchanged. Mac source QA `aicalls-20261104-usb-debug-bridge-host-qa-v2` DONE/0 (Gradle unit/lint/assemble+test APK, Python, anti-friction, audio gate).
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
- Piper: silnik i polska Gosia są zainstalowane; F-057 blokuje rzeczywistą syntezę przez API Android TTS.
- Gemma Audio Direct: polskie syntetyczne ASR PASS po F-056; profil disabled, pełne audio→dialog i wspólne zasoby pozostają OPEN.
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
