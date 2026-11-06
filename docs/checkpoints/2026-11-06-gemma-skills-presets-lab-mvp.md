# AI Calls — Gemma skills, presety, Laboratorium (2026-11-06)

Start HEAD main: 7a1216578edf8980e562770e4b91d324e9f917f6.
Zakres: wersjonowany katalog instrukcji, trzy fabryczne presety + kopie użytkownika, UI wyboru/edycji/rollbacku, integracja szyfrowanego magazynu przygotowania z immutable incoming-call snapshot i niezależne wieloturowe laboratorium modelu lokalnego.
Do publikacji wymagane: testy JVM katalogu i scoringu, AndroidTest Kotlin compile, bash scripts/verify_host.sh, audit_product_friction.py, git diff --check, czysty Git i zgodny origin/main. Publikacja możliwa tylko po GREEN tych warunków.
Nie uruchomiono telefonu, rzeczywistych połączeń, STT, TTS ani tool executor w Laboratorium. Naturalność oceniana ręcznie, dodatkowe pytania heurystyczne. Brak fizycznego QA nowego APK/S22 i brak real-model testów po edycji instrukcji. Nie zmieniono F-091 physical, F-080/F-048/F-045/F-047, uprawnień, stop ani zamrożonego PCM.
Kolejny etap: autoryzowana instalacja odpowiadającego źródłu APK na S22, porównanie presetów na tym samym korpusie off-call, ręczna ocena naturalności i odrębnie dozwolony test rozmowy.
