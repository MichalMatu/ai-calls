# AI Calls — jedyny aktywny plan

**CEL TERAZ: GSM na Samsung S22+ BEZ aplikacji Shizuku.** Użytkownik potwierdził, że obecny tor GSM przez Shizuku działa. Zachować `main` i działający APK; kodować tylko na `feature/s8-no-shizuku`.

## Stan projektu

Własny kod już zawiera `SamsungVoiceDownlinkCapture`, `SamsungCallAssistantTrack`, `SamsungCallMediaSessionController`, PCM i STT → Gemma → TTS. Shizuku daje mu proces `shell` (UID 2000), a nie brakujące algorytmy. Zwykłe uprawnienia APK **nie** zastępują tożsamości `shell`.

## Otwarte — wyłącznie GSM

1. Usunąć zależność **runtime** GSM od Shizuku: własny minimalny host uruchamiany po jawnej autoryzacji ADB jako `shell`; ponownie wykorzystać istniejące klasy Samsunga, bez kopiowania toru audio.
2. Lokalny uwierzytelniony kanał aplikacja ↔ host: sprawdzanie UID/tożsamości, exact Call/generacja, transfer RX/TX PCM, STOP, rozłączenie hosta i timeout; brak publicznego nasłuchu i otwartego interfejsu uprzywilejowanego.
3. Podłączyć host do istniejącego `CallMediaSessionCoordinator` jako alternatywny backend; zachować stary Shizuku fallback do czasu sprawdzonych testów S22.
4. Testy bez telefonu: bramki uprawnień, błędne UID, stare generacje, STOP, wyciek FD, zamknięcie kanału. Następnie za zgodą instalacja izolowanego APK, IDLE, prawdziwe GSM RX → STT → Gemma → TTS → TX, 4–6 tur, barge-in, END_CALL, nagranie/odsłuch i rollback.

**STOP innych prac:** SIP, VoIP, PJSUA2, modele, ogólne UX, nowe funkcje i dodatkowe audyty nie są teraz zadaniami. Istniejący kod SIP można zostawić w repo, ale bez rozbudowy i bez instalacji. Nie scalać automatycznie z `main`.

**Tryb:** Existing Code First → mały patch → test celowany → wynik Local Agenta. Bez Codex/Work/Actions, nowych schedulerów, działań na S22 bez zgody, danych rozmów w Git. Nie usuwać Shizuku z produkcji przed działającym zamiennikiem.
