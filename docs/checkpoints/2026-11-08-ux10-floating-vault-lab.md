# AI Calls — UX-10: floating chat, pełny Sejf, Laboratorium Skills

Date: 2026-11-08. Baseline: `main@78c93c5f4910f55334c73f61679ace012b494274`. Tylko UI oraz nowy izolowany, szyfrowany magazyn custom, bez zmian runtime/permissions/grants/Telecom/PCM/Shizuku.

## Implementacja
- **Pływająca ikona:** `LiveConversationOverlay` wspólna dla Home i Campaign. Nie ma napisu na przycisku; dostępność ma nazwę. Pokazuje się jedynie gdy feed `active` i istnieje min. 1 realna linia, nie przy IDLE/rozmowie zakończonej. Panel ma 84% obszaru poniżej systemowej nawigacji i pozwala na przewijanie/transkrypt w procesie, bez uruchamiania nowych akcji. Dot sygnalizuje nieprzeczytane. STOP i autoryzacje zostają poza panelem.
- **Sejf:** `AgentAiHub` prowadzi do `IdentityVaultManagerActivity`, które edytuje i usuwa 7 już obsługiwanych, typowanych pól w `PersistentIdentityVault`. Wartości nie są odczytywane do listy; edycja tylko przez świadome zastąpienie, input bez Autofill i saved state; pojedyncze usuwanie wymaga potwierdzenia. Nowy `AndroidCustomIdentityVault` przechowuje metadane oraz wartości własnych pól w oddzielnym ciphertext `AtomicFile`/Keystore AEAD, no-backup. Nie dotknięto istniejącego v1 rekord/file/key. Custom fields **nie są jeszcze dostępne modelowi ani FactDisclosurePolicy**; ich zapisanie nie daje upoważnienia do ujawnienia. Błędy odczytu nie są zastępowane pustym sejfem.
- **Lab:** po tytule widać preset/tryb/Start; zaraz poniżej zbiorczy przebieg z dyskretnym stanem/progresem i kolejnymi turami. Usunięto intro, duży duplikat statusu, checkbox auto-follow i ponowne nagłówki nad timeline; wyniki/ocena znajdują się po rozmowie. Ręczne przewijanie w górę zatrzymuje autoscroll, powrót na dół go wznawia. STOP nadal w widocznym miejscu w trakcie testu.

## Testy i decyzja
- Nowe: `scripts/tests/test_ux10_source_contract.py`, `AndroidUx10OverlayVaultLabTest`; zachowane istniejące kontrakty UX09 po aktualizacji. Screenshot `vault` do kolekcji prywatnej.
- **Granice:** bez realnych rozmów, bez używania ani usuwania rzeczywistych danych sejfu w testach, bez zmiany schematu v1. Testy UI mogą czytać wyłącznie nazwy i metadane; nie logować ani wysyłać wartości. Testy Mac i S22 oczekują exact receipt.
- **Status na etapie publikacji:** SOURCE_ONLY / MAC_QA_PENDING / S22_QA_PENDING. Test realnego pojawienia się ikony w CALL pozostaje NOT_RUN, tak samo test fizyczny custom encryption na danych użytkownika (celowo bez dotykania prywatnej zawartości).

