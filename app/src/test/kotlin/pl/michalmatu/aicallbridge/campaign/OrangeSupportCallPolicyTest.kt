package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.*
import org.junit.Test

class OrangeSupportCallPolicyTest {
    @Test fun onlyApprovedExactOrangeServiceNumberMayBeDialed() {
        assertTrue(OrangeSupportCallPolicy.allowedTarget("510100100", true))
        for (wrong in listOf("", "*100", "510100101", "+48510100100",
            "510 100 100", "112", "911", "510100100#")) {
            assertFalse(OrangeSupportCallPolicy.allowedTarget(wrong, true))
        }
        assertFalse(OrangeSupportCallPolicy.allowedTarget("510100100", false))
        assertTrue(OrangeSupportCallPolicy.maxCallMs in 10_000L..60_000L)
    }
}
