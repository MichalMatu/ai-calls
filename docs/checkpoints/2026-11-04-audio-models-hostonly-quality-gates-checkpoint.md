# AI Calls — sprint bez telefonu: gate jakości M1–M4, 2026-11-04

## Granice wykonania

- Repo `MichalMatu/ai-calls`, bez nowych branchy, wyłącznie `main` / commity `[skip ci]`; GitHub Connector + Mac Local Agent `macbook-michal` / `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. **Bez ADB, telefonu, instalacji, połączeń, dostępu do prywatnego audio, GitHub Actions, Codex lub Work.**
- Fizyczny baseline **niezmieniony**: zainstalowany produkcyjny APK z `0d0fdfe22e9b002c8a9dc557ad5d95a882aa5976`, S22 STORE/UI/Gemma 4 syntetyczne tury PASS. Model Parakeet 614 588 752 B/SHA-256 i syntetyczne native Parakeet/Gemma Audio PASS z poprzedniego sprintu.
- Porównanie `0d0fdfe` → `48539406bfda602016c650ba55cefc7366f64172`: **0 plików produkcyjnych** (`app/src/main`, `privileged-helper/src/main`, `audio-bridge/src/main`, `realtime-client/src/main`, konfiguracja aplikacji). Zmieniono tylko host tooling/testy/dokumentację i 1 test instrumentacyjny. Fizyczny APK nie był wymieniany.

## Nowa obowiązkowa bramka bez urządzenia

- `scripts/aicall_tools/diagnostics/audit_audio_engine_quality.py` uruchamiany w `scripts/verify_host.sh`. Nie czyta modeli ani nagrań, nie używa sieci/ADB.
- Kontrakty: identyczny Android STT → Gemma E2B → Android TTS jako domyślna ścieżka; nierozkwalifikowane Gemma Audio/E4B nadal niewybieralne; odrębna preferencja od grantu; Parakeet dokładny rozmiar/SHA, bounded `offer` PCM, anulowanie i instalacja pobrania po sprawdzeniu; Piper jawny Android TTS engine + marker offline; brak Telecom/Shizuku/network/logowania treści w providerach; brak wywoływania połączeń/grantów w syntetycznych testach.
- `scripts/tests/test_audit_audio_engine_quality.py`: **10 testów**, w tym mutacje celowo wprowadzające bypass hasha, blokującą kolejkę, niedozwolone API Telecom/Shizuku, logowanie transkryptu, fallback Piper, aktywację niekwalifikowanych silników oraz start telefonu w instrumentacji. Weryfikacja, że gate reaguje `FAIL`, a aktualne prawidłowe źródła `PASS`.
- **RED F-054**: `aicalls-20261104-hostonly-audio-qualitygate-red-v1` FAILED/1 — niepoprawnie analizowany komentarz `Shizuku RX thread` w kodzie. **GREEN F-054**: `aicalls-20261104-hostonly-audio-qualitygate-full-qa-v2` DONE/0 PASS, pinned `8fa78f6b91ce0034aae31704008c255188e7303d`: testy 10/10, wszystkie 258 Python, pełny build/unit/lint/AndroidTest APK, anti-friction PASS, zero ADB. `F-054 VERIFIED (Mac host-only)`.
- `48539406bfda602016c650ba55cefc7366f64172`: kolejne testy Kotlin: podział na dowolne fragmenty PCM/odczyt `carry`, losowe deterministyczne chunking RX, pełna macierz zintegrowanych/niedostępnych profili, rozmiary/format RIFF/WAVE aż do 30 s. Dokładny Mac QA: `aicalls-20261104-hostonly-audio-qualitygate-full-qa-v3`, wynik należy odczytać z `task-results` przed ogłoszeniem PASS.

## Rzetelna macierz etapów

| Obszar | Mac źródło/build/gate | Fizyczny S22 bez połączenia | Gotowość do live / zakres nierozwiązany |
|---|---|---|---|
| M1 Android STT → E2B → Android TTS | PASS | PASS STORE/UI/4 syntetyczne tury | Nowa konfiguracja w live nie była testowana |
| M2 Parakeet PL | PASS model+adapter, nowy gate | SHA/model/native syntetyczna cisza PASS | Polskie realne WER, final segments, jednoczesny E2B, RAM/thermal OPEN |
| M3 Piper PL | PASS; F-052 błąd kompilacji zweryfikowany | Zewnętrzny pakiet `dev.ihorshevchuk.piper` ABSENT | Instalacja i lokalny głos PL, rzeczywisty PCM16/latencja OPEN |
| M4 Gemma Audio Direct | PASS eksperymentalny adapter | Syntetyczne LiteRT AudioBytes native PASS | Pełny dialog audio→text→Gemma, GPU/RAM, realna tura, STOP OPEN; nadal disabled |
| Polski TTS→Parakeet roundtrip | `aicalls-20261104-audio-polish-roundtrip-host-qa-v1` **DONE/0 PASS** pinned `7d1057f8e34a9e1ee4f80cd207983bc93132de33` | **NOT RUN**; nowy test APK nieinstalowany | Test lokalnego polskiego ASR przy następnej dostępności S22; nie jest to WER korpusu |
| Rzeczywiste połączenie i efekt CLIR | brak nowego uruchomienia | celowo nie wykonywano | Tylko konkretny ważny grant i niezależny dowód efektu; F-045/047/048 OPEN |

## Kryteria i gotowa kolejność kolejnego sprintu z S22

1. Odczytaj świeży `main` i przypięty finalny Mac QA; **nie instaluj**, jeżeli pełna bramka RED. Przed ADB sprawdź exact `SM-S906B`/`RFCT70L7E8J`, dwukrotnie `IDLE`, rolę dialera, brak aktywnej kampanii/retry i fingerprint grantu, skillów oraz profilu audio.
2. Ponieważ `app/src/main` jest nietknięte od `0d0fdfe`, możliwa wyłącznie aktualizacja `app-debug-androidTest.apk` bez reinstalacji produkcyjnej aplikacji. Uruchom `AndroidParakeetPolishRoundTripTest#recognizesSynthesizedPolishSpeechWithLexicalOverlap` w izolowanej instrumentacji. Zapewnij brak wycieku treści syntetycznej i brak połączenia. Zachowaj wynik `PASS/FAIL`, nie nazywaj syntetycznego testu `PROVEN_LIVE`.
3. Polski naturalny ASR: oddzielny prywatny zestaw zgód lub syntetycznych próbek, WER/CER, duplikaty/final segments, latency first/final transcript i porównanie do Android STT na tym samym urządzeniu. Bez surowego audio w GitHub.
4. Piper: najpierw rzeczywista dostępność zewnętrznego `dev.ihorshevchuk.piper` + polskiego offline głosu, potem wybór głosu, native PCM16/16k, brak fallbacku i liczby/CLIR/skróty. Nie ogłaszać PASS na podstawie samego hosta.
5. Gemma Audio: profil pozostaje disabled; oddzielna ocena polskiej wypowiedzi, max RSS/thermal i kosztu równoczesnych silników, skuteczności STOP, dopiero potem decyzja o integracji jednego dialogu na Call.
6. Dopiero po nowych wynikach off-call osobna decyzja o autoryzowanym live, z niezmienionym grantem i dowodem po stronie operatora. Nie powtarzaj wcześniej zakończonych M1/M2/M4 testów bez nowej przesłanki.

## Odczyt host QA i komendy bez urządzenia

```bash
bash scripts/verify_host.sh
python3 scripts/aicall_tools/diagnostics/audit_audio_engine_quality.py
PYTHONPATH=scripts python3 -m unittest scripts.tests.test_audit_audio_engine_quality -v
python3 scripts/aicall_tools/diagnostics/audit_product_friction.py
```

Wyniki hosta tylko z dokładnych `task-results` pod `MichalMatu/local-agent:agent-control` dla Mac instalacji `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`. `host_quality_gate_green` **nie** zastępuje testu S22 ani prawdziwego efektu CLIR. F-049 wycofane, nie przywracać zmian VAD/ACK.

## Host QA rozszerzenia testów PCM: RED fixture 2026-11-04

- `aicalls-20261104-hostonly-audio-qualitygate-full-qa-v3` pinned `48539406bfda602016c650ba55cefc7366f64172` FAILED/1: 10/10 Python mutation PASS, audio static PASS, **703 testy JVM / 1 FAIL** na `oddTotalBytesRetainOneIncompleteSampleAcrossEmptyCallbacks`; fixture oczekiwał max PCM16 dla `0x7f7f`, który nie jest `0x7fff`. Nie wykryto błędu runtime. F-055 rejestrowany w BLOCKERS.
- Poprawka fixture (carry `0xff` + MSB `0x7f`) w kolejnej zmianie na `main`; pełny Mac QA powtórzyć na nowym SHA. Brak kontaktu z telefonem.
