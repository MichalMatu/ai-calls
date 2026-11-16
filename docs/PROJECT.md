# AI Calls — MVP

**Cel:** S22+ Android 16: Call → RX → STT → LLM → TTS → TX → STOP.
**Rollback:** `mvp-s22-2026-11-15`.

## Stan projektu

`main`: stabilne C1–C3/G6.1/EN E2E; GSM PCM blokuje Android/OEM bez Shizuku.

## Podział pracy

S8: `feature/s8-no-shizuku`, bez merge. `AI Calls S8 Lab` (`pl.michalmatu.aicallbridge.s8lab`, ARM64) zainstalowany obok stabilnej wersji. **S22+ PHYSICAL PASS**: 6 faz, 72 RX/72 TX, SDP, STOP i SHA stabilnej aplikacji bez zmian. Emulator ARM64: UI/RTP 3 testy PASS. Test syntetyczny bez rozmowy.

Nowy `S8VoipLocalSpeechSession`: 16k PCM ↔ lokalne STT→LLM→aprobata→TTS, 2 tury i natychmiastowy STOP **unit PASS**, bez modelu na S22+ (PHYSICAL_OPEN). Ręczny SIP/TLS REGISTER: 401→SHA-256→200, hasło tylko w RAM. REGISTER zmienia Contact na 300 s — wyłącznie konto testowe.

## Następny sprint — S8: bez Shizuku

Autoryzowany VoIP SIP INVITE/SDP/SRTP, podłączenie rzeczywistych mediów do nowego lokalnego speech owner, walidacja modelu i 4–6 tur.

## Otwarte

Brak działającej rozmowy i SRTP; operator SIP nieprzetestowany. Model `APP_PRIVATE_LITERT` niepotwierdzony; GSM PCM bez uprawnień systemowych niedostępny. Realny call wyłącznie po odrębnej zgodzie na numer/koszt/scenariusz. Bez PII/audio w Git.
