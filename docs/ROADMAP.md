# AI Calls — aktualna roadmapa 2026-11-04

Obowiązujące dowody: [USB debug / S22](checkpoints/2026-11-04-usb-debug-s22-acceptance.md), [handoff](HANDOFF_NEXT_CHAT.md), [BLOCKERS](../BLOCKERS.md). Wcześniejszy [phone-ready checkpoint](checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md) jest historią sprzed instalacji nowego APK.

| Etap | Stan | Kolejne kryterium |
|---|---|---|
| USB debug bridge, panel, lokalne ADB, diagnostyka błędów, profile i prywatny eksport | **DONE — Mac + S22 off-call PASS; właściciel potwierdził otwarcie panelu** | Kontynuacja lokalnym Codexem; osobne rozwijanie bez duplikatów |
| M1 — Android STT → Gemma E2B → Android TTS | **S22 off-call STT/TTS PASS**; Gemma cztery tury PASS historycznie, nowy rerun NOT RUN | Nie mylić readiness i syntetycznego QA z live rozmową |
| M2 — Parakeet TDT 0.6B v3 PL | **S22 Polish TTS→ASR lexical-overlap PASS** | Prawdziwy WER/CER, final segments, latencja, wspólne RAM/thermal z E2B |
| M3 — Piper PL | **Adapter/Mac PASS; apk/głos S22 ABSENT** | Zweryfikować legalny pakiet i głos offline, native PCM16 |
| M4 — Gemma Audio Direct | **Syntetyczny S22 native PASS wcześniej; profil disabled** | Audio→dialog E2E, polska jakość, RAM/termika/latencja przed odblokowaniem |
| Live effects/CLIR | **Nieudowodnione zewnętrzne wykonanie** | Dokładny grant, własny Call, ścisłe ujawnienia, niezależny dowód efektu |

## Następna kolejność

1. **Lokalny Codex:** świeży `main`, brak konkurencyjnych zadań, lokalny build/test i bezpośredni USB debug status. Panel został już otwarty i potwierdzony przez właściciela w przekazanej rozmowie; nawigację i diagnostykę wykonuj przez ADB.
2. Gemma 4-turn z nowego zestawu jest NOT RUN z powodu blokady narzędzia; nie próbować odmowy obchodzić. Nie powtarzać starszych PASS bez nowego powodu.
3. Zaplanować metryki WER/CER na rzeczywistych polskich próbkach i monitorowanie RAM/temperatury; S22 exact/2× IDLE, grant/skills/audio postcheck.
4. Piper: zewnętrzny silnik i głos nadal nieobecne; nie instalować losowych APK. M4 nadal disabled.
5. Każdy błąd techniczny oznaczyć OPEN/FIXED_IN_SOURCE/VERIFIED w [jedynym BLOCKERS](../BLOCKERS.md); F-045/F-047/F-048 OPEN, F-049 wycofany.

Wykonanie: lokalny Codex na wskazanym Macu; Git/Gradle/USB/ADB bezpośrednio. Local Agent wyłącznie dla ChatGPT, bez zleceń z Codexa. `main`, `[skip ci]`; bez Work/Actions/nowych branchy/force-push i konkurencyjnych mutacji. No outgoing calls without exact app-recorded revocable authorization.
