# Narzędzia, wyniki i skutki zewnętrzne

`CallTask`, typowany `CallOutcome`, `ConversationToolExecutor`, `FactDisclosurePolicy`, `IdentityVault` i **jeden** `CallCommitmentGate` to istniejące kontrakty. Model i Skills dostarczają propozycję, aplikacja sprawdza grant, scope i stan realnego owned Call. Sam transkrypt i `WRITE_COMPLETE` nie dowodzą sukcesu u operatora.

## Rezerwacje
Docelowo: `availability` → opcjonalny hold z TTL/idempotency key → przeczytanie terminu i warunków → potwierdzenie w istniejącym grancie → jednokrotny commit → niezależny booking ID. Przy zmianie terminu/ceny/zakresu brak cichego poszerzenia autoryzacji. SyntheticBookingAdapter ma in-memory availability, hold z TTL i wyłącznością terminu niezależną od ceny/strefy, osobny idempotency key na symulowaną sesję, cancel niepotwierdzonego hold, exact effect confirm, idempotentny receipt i odczyt provider-side. BookAppointmentSimulator waliduje dokładne warunki w CallExternalEffectValidator przed pozwoleniem i nie orzeka BOOKED bez potwierdzenia. Nie wykonuje realnych rezerwacji; realny provider adapter i read-back nadal OPEN.

## Pozostałe skutki
CLIR/inne ustawienia usługi: requested/validated/dispatched/confirmed/unknown rozdzielone, dowód niezależny od modelu. Odczyt faktów: ScopedIdentityFactReader (późny odczyt, policy + live authority przed/po vault.get, capability bez logowania plaintext); podłączony do call-owned CLIR telefonu przez AuthorizedPhoneFactBackend z ponownym sprawdzeniem autoryzacji przed mową; inne typy faktów/model tools pozostają OPEN. Tylko exact target/task/generation. [Security](../architecture/SECURITY.md), [status](../PROJECT.md).
