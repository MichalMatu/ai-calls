package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignCallMatchPolicyTest {
    private fun match(
        actual: String = "*100",
        sameAccount: Boolean = true,
        outgoing: Boolean = true,
        age: Long = 1000L,
        token: Boolean = true,
    ) = CampaignCallMatchPolicy.accept("*100", actual, sameAccount, outgoing, age, token)

    @Test fun acceptsMatchingOwnOutgoingCallOnly() {
        assertTrue(match())
        assertFalse(match(actual = "12345"))
        assertFalse(match(sameAccount = false))
        assertFalse(match(outgoing = false))
        assertFalse(match(token = false))
        assertFalse(match(age = -1L))
        assertFalse(match(age = 30_001L))
    }
}
