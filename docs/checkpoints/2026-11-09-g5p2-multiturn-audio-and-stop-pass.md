# AI Calls — G5.2: trzy tury audio, Skills, pamięć i STOP — finalny checkpoint 2026-11-09

## Decyzja
**G5.2 OFFCALL_MULTITURN_AUDIO_AND_STOP_PASS — VERIFIED_SCOPED.** Wyniki nie awansują na realne Call, nie stanowią oceny naturalności przez człowieka i nie są pomiarem p95. Gemma E2B pozostaje domyślna; nie zmieniono profilu, systemowego STT/TTS, telefonii ani zamrożonej ścieżki Samsung RX/TX. Zweryfikowany kod przed checkpointem `main@bff9c46f294cce750c5b53cb6c530b1b2ba7c10c`; zawsze pobrać świeży HEAD.

## Dokładne dowody
- Mac v1 `aicalls-g5p2-multiturn-audio-stop-mac-qa-20261109-v1` **DONE/0**; kompilacja i pełne QA przeszły. Po statycznym preflighcie wykryto błędny matcher `"wtork"` nieuwzględniający `wtorek`, zapisano `F-160`, poprawiono na `"wtor"` w `main@bff9c46f294cce750c5b53cb6c530b1b2ba7c10c` bez zmian produkcyjnych.
- Mac v2 `aicalls-g5p2-multiturn-audio-stop-mac-qa-20261109-v2` **DONE/0**, 4/4 exit=0: exact HEAD clean, Android debug/AndroidTest Gradle compile, pełny `scripts/verify_host.sh`, **371 Python PASS**, lint/audio, anti-friction, clean postflight. Log zawiera ostrzeżenie nazwy parametru `pcm` w AndroidTest; nie ma błędu kompilacji. Nie interpretować diagnostyki `UI_CAPTURE_RECEIPT=FAIL; reason=run_id_mismatch` z wnętrza `verify_host.sh` jako końcowego czerwonego gate — skrypt zakończył `host_quality_gate_green=true`; odrębny szerszy audit UI można prowadzić osobno.
- Fizyczny S22 `aicalls-g5p2-three-turn-audio-stop-s22-20261109-v1` **DONE/0**, 4/4 exit=0, model telefonu `SM-S906B`, serial `RFCT70L7E8J`: oba debug APK zainstalowane `-r -t` bez wipe, Call IDLE przed/po, czysty Git. `AndroidG52SpeechStopGateTest` **5/5 PASS** (fazy STT/model/TTS, restart, podwójny callback), `AndroidG52MultiturnSpeechSkillsE2ETest#realPcmThreeTurnsApplyMessageAndClarificationSkillsAndRecallCorrection` **1/1 PASS** (rzeczywiste STT/TTS i natywna Gemma).

## Metryki S22 (każda wartość od początku odpowiedniej tury)
| Tura | STT final | Odpowiedź zatwierdzona | Koniec PCM |
|---|---:|---:|---:|
| 1 — wiadomość | 4340 ms | 21158 ms | 23042 ms |
| 2 — korekta | 5723 ms | 12699 ms | 14224 ms |
| 3 — odtworzenie faktu | 4905 ms | 7157 ms | 8694 ms |

`profile_input=ANDROID_STT`, `profile_output=ANDROID_TTS_PL`, `audio_turns=3`, `route_events=3`, `skills_activated=MessageCapture,Clarification`, `memory_day_correct=true`, `pcm_bytes_total=515894`, `tool_executed=false`, `stop=true`, `no_call=true`. Czasy obejmują wejście audio i przetwarzanie według testowego zegara, nie rozdzielają CPU/akceleratora; trzy tury nie wystarczają do statystycznego p95.

## Granice
Pięć testów STOP używa deterministycznych adapterów i sprawdza ignorowanie spóźnionych callbacków oraz brak PCM po anulowaniu. Natywny test 3 tur nie zatrzymywał generacji w środku; nie należy twierdzić, że realny przerwany STT/LLM/TTS na S22 został fizycznie potwierdzony. Przeprowadzić oddzielny hardware interrupt/race test, kiedy możliwe bez naruszania grantu. Rozmowa pozostaje syntetyczna, bez odtwarzania głośnikiem, mikrofonu i telefonii. Nie ma jeszcze odsłuchu jakości naturalności, odporności na hałas/akcenty, długich dialogów ani real-call UX.

## Następny zakres G5.3
Ocena jakości i naturalności pełnych odpowiedzi *z prawdziwych wieloturowych próbek* w prywatnej przestrzeni Mac/telefonu, bez umieszczania transkryptów i WAV w GitHub. Równolegle zdefiniować minimalny hardware STOP podczas aktywnej fazy oraz cold/warm N≥3, jeżeli dane rozstrzygają decyzję o UX. Dopiero osobna G6 weryfikuje INCOMING/OUTGOING, własność Call, grant i EOS→TX/odsłuch; nigdy nie nazywać G5.2 końcem całego produktu. G7 UX później.

Zasady: GitHub Connector + Mac Local Agent + ChatGPT Linux Sandbox; bez Codex, ChatGPT Work, GitHub Actions (także statusu), nowych branchy i force-push, commit `[skip ci]`.
