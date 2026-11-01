package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClirCarrierEvidenceTest {
    @Test
    fun explicitPositiveCarrierStatementCanBeProvisionalEvidence() {
        listOf(
            "Usługa CLIR została włączona.",
            "Blokada prezentacji numeru została aktywowana.",
            "Zastrzeganie numeru zostało uruchomione.",
        ).forEach { assertTrue(it, ClirCarrierEvidence.isConfirmedCarrierStatement(it)) }
    }

    @Test
    fun denialNegationAndUnrelatedServiceMustNeverBeSuccess() {
        listOf(
            "Usługa CLIR nie została włączona.",
            "Nie udało się aktywować usługi CLIR.",
            "Usługa CLIR jest nieaktywna.",
            "Nie możemy uruchomić usługi CLIR.",
            "Usługa CLIR została włączona, ale zmiana się nie powiodła.",
            "Usługa poczty głosowej została włączona.",
            "Czy mamy włączyć usługę CLIR?",
            "Usługa CLIR zostanie aktywowana.",
            "CLIR aktywny?",
            "",
            "OK",
        ).forEach { assertFalse(it, ClirCarrierEvidence.isConfirmedCarrierStatement(it)) }
    }
}
