# Zadania, rezerwacje i skutki

Model proponuje wynik; `CallCommitmentGate`, grant, `FactDisclosurePolicy` i `IdentityVault` w aplikacji decydują o ujawnieniu danych i skutkach. Wynik rozmowy nie jest wynikiem u usługodawcy.

Rezerwacja wymaga exact warunków, jednorazowego zatwierdzenia w zakresie zgody i niezależnego booking ID/read-back. `SyntheticBookingAdapter` jest symulatorem, nie prawdziwym providerem. CLIR i inne skutki rozróżniają requested/validated/dispatched/confirmed/UNKNOWN. [SECURITY](../architecture/SECURITY.md).
