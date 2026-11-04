# AI Calls — aktualny plan prac (2026-11-04)

**Bieżąca kolejność wynika z [handoffu](HANDOFF_NEXT_CHAT.md) i [phone-ready checkpointu](checkpoints/2026-11-04-ai-calls-phone-ready-checkpoint.md).** Historyczne checkpointy dokumentują dawny stan i nie są kolejnymi zleceniami.

| Etap | Status | Kryterium zamknięcia |
|---|---|---|
| M1 — rozdzielne Wejście / Model / Wyjście, profil, Android STT/E2B/Android TTS | **DONE — Mac + S22 off-call PASS** | Zakończone, nie powtarzać; live nowego wariantu pozostaje oddzielnym pomiarem |
| M2 — Parakeet TDT 0.6B v3 PL | **Native syntetyczne S22 PASS / jakość PL OPEN** | Lokalny polski TTS→ASR, później WER/CER, final segmenty, pomiary latencji i współistnienia z E2B; brak nieautoryzowanych połączeń |
| M3 — Piper PL | **Adapter + Mac PASS / native OPEN** | Sprawdzona zewnętrzna instalacja `dev.ihorshevchuk.piper` i offline głos PL, native PCM16/16k, brak fallbacku; dziś silnik ABSENT na S22 |
| M4 — Gemma Audio Direct | **Native syntetyczne PASS / eksperymentalny disabled** | Jedna spójna konwersacja audio→dialog, polska jakość, RAM/termika/latencja, STOP i testy S22 przed odblokowaniem |
| Zwykłe rozmowy i efekty | **Nie dotyczy bieżącego off-call sprintu** | Konkretny aplikacyjny grant, tylko własny `Call`, ścisłe ujawnienia i niezależny dowód efektu. Nie mylić WRITE_COMPLETE z CLIR |

## Kolejny sprint po odzyskaniu telefonu

1. Fresh HEAD / Mac receipts / diff runtime, bez ponownego uruchamiania zatwierdzonych testów.
2. Exact S22 `SM-S906B` + serial, 2× IDLE, dialer, brak aktywnej kampanii i retry, fingerprint grant/skills/audio before.
3. Użyj gotowego AndroidTest APK z Maca (source `9d56cc65e5b4e2f779302c655a501fa68386c912`, SHA-256 `8c6e052548df2f9dcdaec15f2b0ea7c89d4460e905da0d99581fd5359f02302c`) po sprawdzeniu źródeł/podpisu; buduj ponownie tylko przy zmianie kodu. Przy niezmienionym `app/src/main` instaluj **tylko test APK**, bez nadpisania produkcyjnej aplikacji. Run `AndroidParakeetPolishRoundTripTest`, porównaj fingerprint/IDLE after.
4. Zapisz PASS/FAIL; potem osobno PL ASR WER/CER, wydajność równoczesnych modeli i kwalifikacja Piper. M4 disabled do prawdziwego testu konwersacji i RAM.
5. Odśwież checkpoint/handoff i **jedyny** [BLOCKERS](../BLOCKERS.md).

## Równoległe znane luki

**F-048** brak typowanych dozwolonych faktów z IdentityVault; **F-047** końcowy RX przerwany hangup; **F-045** semantyczna walidacja swobodnej wypowiedzi RAW. Zewnętrzny skutek CLIR nadal bez dowodu. **F-049 WYCOFANY** — dwuczęściowe powitanie infolinii jest prawidłowe, nie zmieniać VAD/ACK.

Wspólny silnik docelowo obsługuje różne dozwolone zadania telefoniczne; API/model UI nie jest równoznaczny z wykonanym efektem. Zasady bezpieczeństwa: [SECURITY_PRIVACY](SECURITY_PRIVACY.md), [GENERIC_PHONE_TASK_AUTHORITY](GENERIC_PHONE_TASK_AUTHORITY.md), [AUTONOMOUS_OPERATION_MODE](AUTONOMOUS_OPERATION_MODE.md). Wykonanie tylko GitHub Connector + Mac Local Agent, `main` i `[skip ci]`, bez Codex/Work/Actions/new branches/force-push.
