# AI Calls — bieżący plan MVP

**Cel:** autonomiczny asystent telefoniczny na S22+: pojedyncze zadania i kampanie, rozmowa przez Gemma, harmonogram, wynik i bezpieczne STOP/TAKE OVER. Priorytet: działający produkt, nie dodatkowy hardening.

## Stan
`main@bfbb93b`: wspólny kreator, szyfrowany szkic, wielu odbiorców, trwała kolejka i próby, AlarmManager, retry BUSY/NO_ANSWER, STOP/pauza, wyniki STT per odbiorca. Mac Local Agent: preflight, kompilacja Kotlin/AndroidTest i celowane testy kolejki PASS. To **nie** jest fizyczny odbiór kampanii na S22. Panel: Start / Rozmowy / Agent AI / Ustawienia; Agent zawiera scenariusze, sejf, Skills i narzędzia.

## Otwarte
| Priorytet | Obszar | Warunek zamknięcia |
| --- | --- | --- |
| P0 | Kampanie i harmonogram G6.1 | Sprawdzenie integracyjne 3 odbiorców, kolejności, alarmu po restarcie, retry, STOP/takeover, wyniku UNKNOWN, dokładnego SIM i PL/EN; realny start w tle S22 **PHYSICAL_OPEN** |
| P0 | Rozmowa S22 | Kontrolowane INCOMING/OUTGOING, dwukierunkowe STT/Gemma/TTS, barge-in, STOP/TAKE OVER i resume na realnym Call; jakość audio i pełna historia **PHYSICAL_OPEN** |
| P0 | Wizyta | Wynik rozmowy i propozycja terminu do zatwierdzenia; bez udawania potwierdzonej rezerwacji. Realna próba **PHYSICAL_OPEN** |
| P1 | Wyniki kampanii | Zbiorcze porównanie ofert; obecnie cytaty STT są pokazane osobno dla odbiorców |
| P1 | Strategia kampanii | Opcjonalny tryb kończenia po pierwszym potwierdzonym sukcesie; obecnie zbieranie odpowiedzi bez rezerwacji |

## Następny sprint
Domknąć integrację kolejki i krytyczne regresje bez przepisywania runtime. Następnie za zgodą właściciela fizyczny test S22; dopiero po nim rozszerzać porównania i reguły kampanii. Nie powtarzać już zaliczonych testów bez zmiany kontraktu.

**MVP BLOCKED / PHYSICAL_OPEN** do kontrolowanego potwierdzenia na urządzeniu: INCOMING, OUTGOING, audio obu stron, STOP/TAKE OVER i scenariusz wizyty. Odbiór rzeczywistej rezerwacji wymaga niezależnego dowodu usługodawcy i osobnego zakresu zgody. [SECURITY](architecture/SECURITY.md) · [DEVICE](operations/DEVICE.md).
