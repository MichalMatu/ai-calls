# AI Calls — pierwszy nadzorowany MVP rozmowy (2026-11-01)

## Zakres ukończony w kodzie

**Źródłowy commit MVP:** `5cb13fc40eaf4dfffba599dd803c7e9b3b22b5c4` (`main`).

Minimalny produkt nie udaje autonomicznego dialera. Użytkownik sam rozpoczyna i kończy połączenie w domyślnym Google Phone. AI Calls podłącza lokalny, neutralny skrypt do istniejącej rozmowy **jedynie po wyraźnym uruchomieniu i potwierdzeniu na ekranie**; na każdą sesję maksymalnie trzy wymiany.

- `MainActivity` udostępnia przycisk **„Pierwsze połączenie — test nadzorowany”**.
- `SupervisedCallPilotActivity`: otwiera Google Phone przez `ACTION_DIAL` (bez numeru), sprawdza `READ_PHONE_STATE` i `RECORD_AUDIO`, Shizuku Binder/uprawnienia, `TelecomManager.isInCall` **jako dowód istnienia rozmowy, nie własności**, oraz `AudioManager.MODE_IN_CALL`. Dopiero po potwierdzeniu operatora dołącza audio. Ma **STOP AI**, który zatrzymuje wyłącznie sesję mediów i nie kończy rozmowy.
- `ChatRelayLiveCallProbe`: zwraca `SessionControl.stopAudio()`, dopuszcza tylko jedną sesję mediów, usuwa ją po zakończeniu. W nadzorowanej ścieżce używa `SupervisedReadOnlyDialogueBackend`; bez modelu, host relay, danych osobowych ani zewnętrznych efektów. Workflow ma neutralny cel audio, a ciężkie zależności Gemmy i skarbca tożsamości są inicjalizowane leniwie tylko w wymagających ich ścieżkach.
- Wyłączenie lub opuszczenie aplikacji kończy audio AI, ale **nie wykonuje dial/hangup**. Skrypt odpowiada na odebraną wypowiedź, STT i TTS/TX przez istniejący, już przetestowany tor Shizuku.
- `SupervisedCallPilotPolicyTest`: bramki odmowy dla nieaktywnej rozmowy, niegotowej ścieżki audio, braku uprawnień i Shizuku. Nie usuwać dotychczasowej blokady autonomicznego `CampaignCallControllerRegistry`.

## Akceptacja hostowa — PASS

Zadanie `aicalls-supervised-mvp-build-20261101-v1` na `MichalMatu/local-agent:agent-control`, właściwy host `macbook-michal`, zakończyło się `done`, exit 0 na dokładnym `5cb13fc`:

- Testy Android/JVM — PASS;
- Android Lint — PASS;
- `assembleDebug` i `assembleDebugAndroidTest` — PASS;
- **199 testów Pythona — PASS**;
- `SUPERVISED_MVP_HOST_PASS=true`.

Nie jest to akceptacja rzeczywistej rozmowy RX/STT/TTS/TX.

## Wdrożenie na S22 — blokada transportu

Zadanie `aicalls-supervised-mvp-install-ui-20261101-v1` potwierdziło dokładny S22, `IDLE`, domyślny `com.google.android.dialer`; build APK był poprawny, ale **`adb install -r` nie dokończył instalacji**, więc MVP **nie jest potwierdzone jako wdrożone**.

- `aicalls-supervised-mvp-install-diagnose-20261101-v1`: kolejny preflight zwrócił `exact_s22_adb_transport_unavailable`.
- `aicalls-supervised-mvp-adb-recovery-check-20261101-v1`: `adb devices -l` zgłaszał poprzedni adres `192.168.0.100:41703` jako **offline**, a `adb mdns services` — brak usługi.
- `aicalls-supervised-mvp-refresh-stale-adb-20261101-v1`: ostatni port TCP nie odpowiadał, `adb disconnect` usunął jedynie stary transport; ponowne `adb connect` zakończyło się timeout; mDNS nadal puste, tożsamość sprzętowa niedostępna.

**Kolejny krok:** odblokować S22, w ustawieniach programisty ponownie włączyć **Wireless Debugging** w tej samej sieci Wi-Fi, uruchomić Shizuku jeśli przestał działać. Po odzyskaniu mDNS wznowić na Macu `s22_adb_discovery`; wykonać instalację `adb install -r` bez odinstalowania aplikacji, odczyt nowego ekranu UI i test **odmowy startu bez rozmowy**. Zachować zapisany IdentityVault i model. Nie zgadywać starego portu.

Do fizycznej walidacji skryptowego RX/STT/TTS/TX operator musi sam rozpocząć świadomie wybraną, uzgodnioną rozmowę testową. Nie inicjować realnego połączenia lub CLIR bez nowego zakresu autoryzacji; **nie używać globalnego rozłączania**.

## Niezamknięta funkcja product

Tryb autonomiczny (aplikacja sama wybiera numer i selektywnie rozłącza własną rozmowę) nadal wymaga prawdziwego backendu `CampaignOwnedCallController`. Nadzorowany MVP nie rozwiązuje tej niezależnej zależności; jest pierwszą ograniczoną ścieżką produktu bez przejmowania dialera.
