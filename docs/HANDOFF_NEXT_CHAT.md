# AI Calls — handoff do nowego czatu, 2026-11-04

**Jedyny start operacyjny:** [checkpoint gotowości S22](checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md). Nie kontynuować z przestarzałych sekwencji w dawnych checkpointach, nie rozpoczynać od ogólnego audytu ani od M1 od nowa. Odczytać **świeży** `main` HEAD przed działaniem.

## Stan do odziedziczenia

| Zakres | Potwierdzone | Nadal otwarte |
|---|---|---|
| Aplikacja | Na S22+ zainstalowany APK ze źródła `0d0fdfe22e9b002c8a9dc557ad5d95a882aa5976`; STORE 6/6, UI i Gemma 4 tury PASS off-call | Nowego APK nie instalowano; żywa rozmowa na nowych modelach niezweryfikowana |
| Host | `aicalls-20261104-hostonly-audio-qualitygate-final-postfix-v4` DONE/0; `2c2dac7`; Gradle unit/lint/build, 10 mutacji, 258 Python, anti-friction PASS | Nie powtarzać bez zmiany kodu / nowej przesłanki |
| Parakeet | M2: zweryfikowany model 614 588 752 B na S22, native prewarm + cisza PCM PASS | Polski TTS→ASR `AndroidParakeetPolishRoundTripTest` **NOT RUN** na S22; realny WER/CER, kolejność final segments i RAM/thermal OPEN |
| Piper | M3 adapter + Mac PASS; F-052 kompilacja zweryfikowana | `dev.ihorshevchuk.piper` i głos PL **ABSENT** na S22; native TTS OPEN |
| Gemma Audio | M4 syntetyczne `Content.AudioBytes` native S22 PASS | Profil M4 disabled, brak pełnego audio→dialogu, wspólnej pamięci i termiki |
| Autor. zadania | Istniejący model grantu/STOP i zamrożony Samsung/Shizuku bez zmian; F-054/055 VERIFIED (Mac) | F-045/F-047/F-048 OPEN; CLIR bez niezależnego dowodu; F-049 **WYCOFANY** |

Względem `0d0fdfe` ostatni potwierdzony `main` `e1d9d694b93d6700934806341ec0e85c46b6030e` miał **zero zmian w produkcyjnym Androidzie**; późniejsze commity tej konsolidacji to dokumentacja. Test `AndroidParakeetPolishRoundTripTest` przeszedł Mac QA (`aicalls-20261104-audio-polish-roundtrip-host-qa-v1` DONE/0), lecz AndroidTest APK **nie** jest jeszcze na S22.

## Wykonawca i nieprzekraczalne zasady

- Repo `MichalMatu/ai-calls`; praca na `main`, commity `[skip ci]`, bez nowego brancha, force-push, Codex, Work, GitHub Actions (również bez czytania ich statusu).
- Dokładny host `macbook-michal`, instalacja `cfea4cc3-b54c-4b6d-b9a6-758d12733e98`, Mac Local Agent 4.20.16 ostatnio zweryfikowany. **Host Ops** na `MichalMatu/local-agent:agent-control` w `.agent/hosts/macbook-michal/supervisor/cfea4cc3-b54c-4b6d-b9a6-758d12733e98/{tasks,task-results}/<id>.json`. Nie mylić ze starszą kolejką `ai-calls:agent-control`. Przed zadaniem przeczytać bieżący host `main` AGENTS + HOST_CONTROL_PLANE + CURRENT_HANDOFF oraz status/rezultaty, a nie wnioskować z historycznego heartbeatu.
- Dokładny telefon: Samsung S22+ `SM-S906B`, serial `RFCT70L7E8J`, pakiet `pl.michalmatu.aicallbridge`. ADB transport odkryć na żywo; nie korzystać ze starego portu/IP.
- Przed **każdą** instalacją/instrumentacją: 2× `IDLE`, rola dialera, dokładne device/model/serial, brak `AppOwnedCampaignService` w trakcie pracy i pending retry, zapisane prywatnie SHA-256 grantu / skillów / profilu audio; po sprawdzić to samo. Telefon jest obecnie **niedostępny** — nie wysyłać ADB, aż będzie dostępny.
- Żadnych outgoing calls bez konkretnie ważnego, aplikacyjnie zapisanego i odwoływalnego grantu na dokładny cel/number/SIM/efekt/ujawnienia. Nie ruszać Samsung/Shizuku PCM, VAD, ACK/TX, STOP, prompt-skills. F-049 to błędna diagnoza powitania, nie otwierać.
- `PROVEN_HOST` ≠ `PROVEN_S22_OFFCALL` ≠ `PROVEN_LIVE` ≠ potwierdzony skutek zewnętrzny. WAV, prawdziwy transcript i identyfikatory kont zostają **prywatnie na Macu/S22**, nie na GitHub, w wynikach Host Ops ani logcat.

## Następny sprint, bez zbędnych powtórek

1. Odczytaj świeży `main` i terminalne receipts z checkpointu; sprawdź diff produkcyjnego `app/src/main` od `0d0fdfe`. Jeśli tylko testy/dokumentacja, wytwórz **jedynie nowy AndroidTest APK**; nie przeinstalowuj stabilnej aplikacji.
2. Po powrocie S22 wykonaj wyłącznie read-only preflight; przy nie-`IDLE`, aktywnej kampanii/retry lub innym ID przerwij instalację, bez wymazywania danych/grantów.
3. Bez połączenia uruchom pojedynczy nowy `AndroidParakeetPolishRoundTripTest#recognizesSynthesizedPolishSpeechWithLexicalOverlap` na S22. To polski lokalny TTS→PCM16/16k→Parakeet, mierzy tylko overlap słów; **nie jest to korpusowy WER ani live test**. Zachowaj prywatnie metryki/dowód i bezpieczne PASS/FAIL.
4. Następnie zaplanuj rzeczywisty polski WER/CER + final segmenty oraz RAM/latencję/temperaturę współistnienia Parakeet+E2B; Piper najpierw wymaga sprawdzonego zewnętrznego silnika i głosu PL; M4 zostawić disabled aż do osobnej kwalifikacji.
5. Po testach zaktualizuj `BLOCKERS`, `ROADMAP`, checkpoint i handoff. Nie obiecuj produkcyjnego sukcesu rozmowy/CLIR na podstawie syntetycznej instrumentacji.

Przed pracą przeczytać też `AGENTS.md`, `README.md`, `BLOCKERS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/AUTONOMOUS_OPERATION_MODE.md`, `docs/SECURITY_PRIVACY.md`, `docs/GENERIC_PHONE_TASK_AUTHORITY.md`; `docs/PHASE2D_FREEZE_2026-09-18.md` tylko gdy potrzebna ingerencja w zamrożone media. Historia prób i numery tasków są w [checkpoincie](checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md) i Git.

## Tekst do wklejenia w nowym czacie

> **Wznów AI Calls** z `MichalMatu/ai-calls`, świeży `main`. Zacznij od `AGENTS.md`, `docs/HANDOFF_NEXT_CHAT.md` i `docs/checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md`. Korzystaj wyłącznie z GitHub Connector i dokładnego Mac Local Agent; nie używaj Codex/Work/Actions. Przywróć S22 dopiero po powrocie użytkownika; zaczynaj od bezpiecznego read-only preflight i nowego off-call testu polskiego Parakeet, bez powtarzania zamkniętych testów. Nie ruszaj stabilnego APK, grantu, Samsung/Shizuku ani nie wykonuj rozmów bez konkretnego aktywnego grantu. F-049 wycofane. Zapisz wyniki i kontynuuj sprint.
