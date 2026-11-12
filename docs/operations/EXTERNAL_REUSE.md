# Zewnętrzny kod — selektywny reuse

**Existing Code First:** wskaż konkretną lukę i własny test. Porównaj najwyżej 2–3 istotnych kandydatów; `KEEP` gdy obecny kod wystarcza, `IMPROVE` gdy potrzeba poprawki, `ADAPT` małego zgodnie licencjonowanego fragmentu, `REPLACE` dopiero z dowodem wyraźnej przewagi.

Przed kopiowaniem sprawdź licencję konkretnego pliku, zależności, NOTICE i zgodność z dystrybucją. Brak licencji lub ograniczenia użycia oznaczają **reference-only**. Nie importuj alternatywnego toru połączeń, nie naruszaj exact Call, STOP, grantu ani prywatności. Nie utrzymuj w bieżących docs katalogu bibliotek i dawnych ocen: potrzebne źródła podaj w konkretnej zmianie kodu.
