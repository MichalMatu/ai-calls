package pl.michalmatu.aicallbridge.conversation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ConversationSessionRegistryTest {
    @Test fun rejectedStartDoesNotConstructAModelOrClearAnotherSessionsData() {
        val sessions = ConversationSessionRegistry<Any>()
        val first = sessions.tryCreate { Any() }!!
        assertNull(sessions.tryCreate { error("duplicate_constructor_called") })
        sessions.release(first)
        assertNotNull(sessions.tryCreate { Any() })
    }
    @Test fun lateReleaseCannotRemoveANewerSession() {
        val sessions = ConversationSessionRegistry<Any>()
        val first = sessions.tryCreate { Any() }!!
        sessions.release(first)
        val second = sessions.tryCreate { Any() }!!
        sessions.release(first)
        assertNull(sessions.tryCreate { error("stale_release_removed_new_owner") })
        sessions.release(second)
    }
    @Test fun concurrentIncomingAndOutgoingRequestsConstructOnlyOneOwner() {
        val sessions = ConversationSessionRegistry<Any>()
        val constructors = AtomicInteger()
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val requests = List(2) { pool.submit<Any?> { gate.await(); sessions.tryCreate { constructors.incrementAndGet(); Any() } } }
            gate.countDown()
            assertEquals(1, requests.count { it.get(5, TimeUnit.SECONDS) != null })
            assertEquals(1, constructors.get())
        } finally { pool.shutdownNow() }
    }
    @Test fun failedConstructionDoesNotLeaveTheRuntimeBusy() {
        val sessions = ConversationSessionRegistry<Any>()
        assertTrue(runCatching { sessions.tryCreate { error("create_failed") } }.isFailure)
        assertNotNull(sessions.tryCreate { Any() })
    }
}
