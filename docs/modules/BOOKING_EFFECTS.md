# Narzędzia, wyniki i skutki zewnętrzne

`CallTask`, typowany `CallOutcome`, `ConversationToolExecutor`, `FactDisclosurePolicy`, `IdentityVault` i **jeden** `CallCommitmentGate` to istniejące kontrakty. Model i Skills dostarczają propozycję, aplikacja sprawdza grant, scope i stan realnego owned Call. Sam transkrypt i `WRITE_COMPLETE` nie dowodzą sukcesu u operatora.

## Rezerwacje
Docelowo: `availability` → opcjonalny hold z TTL/idempotency key → przeczytanie terminu i warunków → potwierdzenie w istniejącym grancie → jednokrotny commit → niezależny booking ID. Przy zmianie terminu/ceny/zakresu brak cichego poszerzenia autoryzacji. SyntheticBookingAdapter ma in-memory availability, hold z TTL i wyłącznością terminu, cancel niepotwierdzonego hold, exact effect confirm, idempotentny receipt i odczyt provider-side. BookAppointmentSimulator nie orzeka BOOKED bez potwierdzenia. Nie wykonuje realnych rezerwacji; realny provider adapter i read-back nadal OPEN.

## Pozostałe skutki
CLIR/inne ustawienia usługi: requested/validated/dispatched/confirmed/unknown rozdzielone, dowód niezależny od modelu. Odczyt faktów: ScopedIdentityFactReader (późny odczyt, policy + live authority przed/po vault.get, capability bez logowania plaintext); nie jest jeszcze podłączony do model-owned tool. Tylko exact target/task/generation. [Security](../architecture/SECURITY.md), [status](../PROJECT.md).
