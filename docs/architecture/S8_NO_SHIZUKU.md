# S8 — audio bez Shizuku (eksperyment, bez merge)

Stan: implementacja S8.1/S8.2 w `feature/s8-no-shizuku`; stabilny `main` i checkpoint pozostają nietknięte.

## Dostępność

- Rola dialera (`ROLE_DIALER`) daje dostęp do cyklu życia własnego obiektu `Call`, ale **nie** do surowego RX/TX komórkowego.
- `RECORD_AUDIO` nie oznacza `VOICE_DOWNLINK`. `CAPTURE_AUDIO_OUTPUT` jest uprzywilejowane; zwykła aplikacja nie uzyska go przez dialog uprawnień.
- Zwykły Android SDK nie oferuje obsługi wstrzykiwania dowolnego PCM bezpośrednio do uplinku połączenia GSM.
- Samo wyłączenie opcji programistycznych nie rozwiązuje dostępu do PCM. Natywna ścieżka Samsung/OEM wymaga osobnego, rzeczywistego dowodu dostępności API.

## Zmiany kodu

`CallMediaSessionRuntime` przyjmuje `CallMediaSessionBackend` lub wybiera jawny tryb. Domyślny `SHIZUKU_LEGACY` nadal działa jak dotychczas. `ANDROID_PUBLIC_API_PROBE` **zawsze odmawia RX/TX**, bez udawania sukcesu czy audio z głośnika. `S8AndroidAudioCapabilityProbe` raportuje tylko role/granty; nie uruchamia połączeń ani nie przedstawia ich jako zweryfikowanego PCM.

## Następne prace

1. Gotowy jawny wybór LLM: `APP_PRIVATE_LITERT` wykorzystuje istniejący model LiteRT z katalogu aplikacji i nie potrzebuje Shizuku; `SHIZUKU_QWEN` pozostaje domyślny, bo to inny model. Nie przełączać bez testów jakości na S22.
2. Zweryfikować udokumentowane OEM API; bez takiego API uznać direct GSM za **BLOCKED**.
3. Alternatywnie SIP/VoIP z przekierowaniem operatorskim, kontrolą kosztu i consent.
4. Testy na Mac/emulatorze, potem osobno zatwierdzony S22: exact Call, RX, TX, STOP, BARGE-IN, reboot; brak testu = **PHYSICAL_OPEN**.

Nie przełączać produkcji, nie instalować eksperymentalnego APK i nie usuwać Shizuku przed pozytywnym odbiorem.
