package pl.michalmatu.aicallbridge.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignOwnedCallLifecycleTest {
    @Test fun stopReleasesOnlyBoundSessionOnce() {
        val released = mutableListOf<String>()
        val lifecycle = CampaignOwnedCallLifecycle<String> {
            released += it
            "targeted_disconnect_requested"
        }
        assertTrue(lifecycle.bind("owned-call-A"))
        assertFalse(lifecycle.bind("unrelated-call-B"))
        assertEquals("already_released", lifecycle.release("unrelated-call-B"))
        assertEquals("targeted_disconnect_requested", lifecycle.releaseCurrent())
        assertEquals("no_owned_call", lifecycle.releaseCurrent())
        assertEquals("already_released", lifecycle.release("owned-call-A"))
        assertEquals(listOf("owned-call-A"), released)
    }

    @Test fun completedCallCannotBeStoppedAgainAndNextCallCanBeBound() {
        var count = 0
        val lifecycle = CampaignOwnedCallLifecycle<String> {
            count++
            "disconnected"
        }
        assertTrue(lifecycle.bind("first"))
        assertEquals("disconnected", lifecycle.release("first"))
        assertTrue(lifecycle.bind("second"))
        assertEquals("disconnected", lifecycle.releaseCurrent())
        assertEquals(2, count)
    }
    @Test fun takeoverTransfersOnlyMatchedOwnerWithoutDisconnectAndLateCleanupIsHarmless() {
        val released=mutableListOf<String>()
        val lifecycle=CampaignOwnedCallLifecycle<String> { released+=it; "disconnected" }
        assertTrue(lifecycle.bind("owned"))
        assertEquals(null,lifecycle.detachIf { it=="unrelated" })
        assertEquals("owned",lifecycle.detachIf { it=="owned" })
        assertEquals("already_released",lifecycle.release("owned"))
        assertEquals("no_owned_call",lifecycle.releaseCurrent())
        assertTrue(released.isEmpty())
        assertTrue(lifecycle.bind("next"))
        assertEquals("already_released",lifecycle.release("owned"))
        assertEquals("disconnected",lifecycle.releaseCurrent())
        assertEquals(listOf("next"),released)
    }

}
