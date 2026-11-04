# AI Calls — aktualna roadmapa 2026-11-05

Obowiązujące dowody: [audio + Orange](checkpoints/2026-11-05-audio-quality-orange-acceptance.md), [handoff](HANDOFF_NEXT_CHAT.md), [BLOCKERS](../BLOCKERS.md). Wcześniejsze checkpointy są historią pomiarów.

| Etap | Stan | Kolejne kryterium |
|---|---|---|
| USB debug bridge, panel, lokalne ADB, diagnostyka błędów, profile i prywatny eksport | **DONE — Mac + S22 off-call PASS; właściciel potwierdził otwarcie panelu** | Kontynuacja lokalnym Codexem; osobne rozwijanie bez duplikatów |
| M1 — Android STT → Gemma E2B → Android TTS | **Orange: 5 pełnych tur**; powrót IDLE | Pierwsza odpowiedź 22,4 s; F-042 i efekt zadania OPEN |
| M2 — Parakeet TDT 0.6B v3 PL | **3 próbki syntetyczne: 0 błędów słów/znaków; Orange: 1 pełna tura** | Naturalny korpus, final segments, pierwsze EOS→TX 35,0 s, wspólne zasoby |
| M3 — Piper PL | **APK 1.1 + Gosia zainstalowane; native TTS FAIL** | F-057: poprawna walidacja nazw głosów w zewnętrznym silniku |
| M4 — Gemma Audio Direct | **F-056 naprawiony; 3 próbki PL: 0 błędów słów/znaków; profil disabled** | Pełny backend rozmowy audio, współpraca modeli, RAM i STOP |
| Alternatywny LLM | **E2B zintegrowany; E4B niezintegrowany** | Rzeczywisty provider i pomiary przed dopuszczeniem wyboru |
| Live effects/CLIR | **Nieudowodnione zewnętrzne wykonanie** | Dokładny grant, własny Call, ścisłe ujawnienia, niezależny dowód efektu |

## Następna kolejność

1. **Lokalny Codex:** świeży `main`, brak konkurencyjnych zadań, lokalny build/test i bezpośredni USB debug status. Panel został już otwarty i potwierdzony przez właściciela w przekazanej rozmowie; nawigację i diagnostykę wykonuj przez ADB.
2. Gemma 4-turn z nowego zestawu jest NOT RUN z powodu blokady narzędzia; nie próbować odmowy obchodzić. Nie powtarzać starszych PASS bez nowego powodu.
3. Rozwinąć pomiary z małego zestawu syntetycznego do naturalnego korpusu i rozdzielić latencję STT/Gemma/TTS z rzeczywistych rozmów.
4. Piper: naprawa kontraktu nazw głosów F-057 w silniku, potem ten sam test PCM/ASR i live. M4 wymaga pełnego backendu dialogu audio.
5. Każdy błąd techniczny oznaczyć OPEN/FIXED_IN_SOURCE/VERIFIED w [jedynym BLOCKERS](../BLOCKERS.md); F-045/F-047/F-048 OPEN, F-049 wycofany.

Wykonanie: lokalny Codex na wskazanym Macu; Git/Gradle/USB/ADB bezpośrednio. Local Agent wyłącznie dla ChatGPT, bez zleceń z Codexa. `main`, `[skip ci]`; bez Work/Actions/nowych branchy/force-push i konkurencyjnych mutacji. No outgoing calls without exact app-recorded revocable authorization.
