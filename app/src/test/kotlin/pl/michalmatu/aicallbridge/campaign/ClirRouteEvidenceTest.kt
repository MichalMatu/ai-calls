package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClirRouteEvidenceTest {
    @Test fun requiresServiceContextNotKeywordOnly() {
        listOf(
            "Wybrano usługę CLIR.",
            "Zajmujemy się ustawieniami CLIR.",
            "Blokada prezentacji numeru.",
            "Zastrzeganie numeru telefonu.",
        ).forEach { assertTrue(it, ClirRouteEvidence.isExplicitServiceContext(it)) }
        listOf(
            "CLIR",
            "Nie wiem",
            "Poczta głosowa jest aktywna.",
            "clirrr",
        ).forEach { assertFalse(it, ClirRouteEvidence.isExplicitServiceContext(it)) }
    }
}
