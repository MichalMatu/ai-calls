# Laboratorium — konfiguracja pojedynczej próby, 2026-11-05

Kod `aed6b4ab27b3bf5661ec9cabb1956008cbd321b1`, baza `bf6eccd`. Rozwój bezpośrednio przez lokalny Codex, Git/Gradle/USB, bez zleceń Local Agent, Work i Actions. Docelowa dystrybucja jest jednym pakietem instalacyjnym; bieżący zakres to laboratorium.

## Wdrożona zmiana źródeł

`ConversationRunConfiguration` w audioengines jest niezmiennym kontraktem niezależnym od Telecom, scenariusza i magazynu grantu. ScriptAssisted zawiera profil silników; ModelDirect zawiera profil i obowiązkową instrukcję. Instrukcja jest redagowana w toString. Nie wprowadzono nowych magazynów, flag uprawnień, limitów ani dialogów.

AppOwnedCampaignService składa snapshot przed prewarm. Ten sam obiekt, uchwycony przez callback przygotowania, przechodzi przez launchPreparedAttempt, AppOwnedClirCampaignExecutor i ChatRelayLiveCallProbe. Nie przenosi się już osobno trybu/profilu/nullable promptu i nie odczytuje ponownie zmienionych preferencji jako warunku dial. Pole activeDirectSystemInstruction i jego reset usunięte. Zakres/cofnięcie/SIM/własny Call/STOP i polityki wypowiedzi pozostają aktywne.

Przy braku cache Direct wywołuje wybranego DialogueEngineProvider z tą samą instrukcją i profilem. GemmaDirectDialogueBackendFactory wymaga jawnego modelu, bez kontekstu i własnego domyślnego loadera E2B/promptu. SCRIPT używa tego samego createClassifier dla prewarm i tworzenia po braku cache. Wszystkie callery zmigrowane, również izolowane piloty, bez alternatywnej starej sygnatury.

Nie zmieniano parametrów Samsung RX/TX, PCM, Shizuku UserService, VAD/quick ACK ani transportu. Potok w ChatRelayLiveCallProbe zmienił sposób otrzymywania konfiguracji, nie sterowanie mediami. Ogólny scenariusz, zapisywalne presety i adaptery korpusu/pliku/mikrofonu nadal są następnymi pracami. Nie jest to ukończone uniwersalne laboratorium.

## RED / GREEN i bieżące QA

- RED nowego kontraktu: `/tmp/ai-calls-lab-config-red.log`, brak typu konfiguracji przed implementacją (compile RED, nie pomiar zachowania fizycznego).
- Pierwszy build migracji: `/tmp/ai-calls-lab-config-green.log` FAIL przez pozostawiony reset usuniętego pola. F-073 zachowuje zdarzenie; brak instalacji tego kodu.
- Focused GREEN: `/tmp/ai-calls-lab-config-green-fixed.log`, 5/5 testów cache miss/hit, wszystkich identyfikatorów modeli, spójności próby po zmianie preferencji, propagacji błędu wybranego modelu bez drugiego loadera i redakcji instrukcji.
- Pełna bramka hosta: `/tmp/ai-calls-lab-config-host.log` — `host_quality_gate_green=true`; 773 JVM (712 app, 51 realtime-client, 10 privileged-helper), 266 Python, lint wszystkich modułów i oba APK PASS.
- Anti-friction i audio-engine source audit PASS; diff --check PASS.
- Końcowa instalacja/native: PASS, 5/5 kontraktów przyjęcia zadania/podglądu/zakresu/cofnięcia/UI na S22; rzeczywiste preferences unchanged. Log `/tmp/ai-calls-lab-config-native-fixed.log`. Nie jest to inferencja modelu ani nowy live. Nowa rzeczywista rozmowa: NOT RUN; poprzednie pomiary nie dowodzą nowego live.
- Pierwszy wrapper native przerwał przed instalacją: brak staging kopii dotychczasowego APK do porównania podpisu (F-074), nie błąd certyfikatu. Przygotowano kopię przez pm path + adb pull i ponowiono kontrolę bez jej pomijania. Pierwszy log `/tmp/ai-calls-lab-config-native.log` zachowany.
- APK app SHA-256 `a4e48e6f3b2f9412d1b67160a1cf6df01617edaff3ffad922a3cba7fc2691267`; AndroidTest `02be7730c772014754add6b9f161a6a9229f6d38c66cd979899c5419d7f7e3d0`. Podpis zgodny ze zainstalowaną aplikacją, dane zachowane.
- Końcowy odczyt: 2× IDLE, kampania nieaktywna, pending_retry=false, ANDROID_STT / GEMMA_4_E2B / ANDROID_TTS_PL, voice auto. Prywatne receipts `~/Documents/AI-Calls/local-codex/2026-11-05-lab-configuration/`: task-intake-native-summary.json, task-intake-final-apk-sha256.json, final-device-status.json. F-071–F-074 mają w BLOCKERS opisane zakresy testów, bez deklaracji nowego live.

F-071/F-072/F-073/F-074 pozostają w jedynym [BLOCKERS](../../BLOCKERS.md). Nie nazywać hosta/native dowodem słyszalności, sukcesu CLIR lub naprawy pętli F-061.

## PackageInstaller i potrzebne części Shizuku

PackageInstaller jest systemowym API Androida, nie APK do doinstalowania. Shizuku API/provider są już bibliotekami w AI Calls. Potrzebny jest działający uprzywilejowany broker, nie zestaw samych ustawień. Docelowo można wykorzystać potrzebny kod startera/serwera/UserService i własny UI przygotowania, zachowując licencje i prawdziwą aktywację shell/root. W tym zakresie nie budowano instalatora i nie zastępowano fizycznie sprawdzonego Shizuku. [Szczegóły i oficjalne źródła](../TEST_LAB_SCOPE.md).
