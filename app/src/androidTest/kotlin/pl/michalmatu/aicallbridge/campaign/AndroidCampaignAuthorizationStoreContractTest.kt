package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses the instrumentation APK's private preferences, never the target application's grant.
 * No dialing or operator effect occurs.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCampaignAuthorizationStoreContractTest {
    private lateinit var context: Context
    private lateinit var store: CampaignAuthorizationStore
    private val request = ClirCampaignSpec.enableRequest("test-voice")

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.getSharedPreferences("campaign_authorization_grant_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = CampaignAuthorizationStore(context)
        store.save(ClirCampaignSpec.newEnableGrant("test-voice", 1_000L, "test-grant"))
    }

    @After fun tearDown() {
        context.getSharedPreferences("campaign_authorization_grant_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun revokedGrantCannotBeResurrectedByConcurrentAttemptReservation() {
        val other = CampaignAuthorizationStore(context)
        val barrier = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val reserve = executor.submit<Boolean> {
                barrier.await()
                store.reserveAuthorizedAttempt(request, 2_000L).isSuccess
            }
            val revoke = executor.submit {
                barrier.await()
                other.revoke(2_000L)
            }
            barrier.countDown()
            reserve.get(10, TimeUnit.SECONDS)
            revoke.get(10, TimeUnit.SECONDS)
            val finalGrant = store.load()
            assertNotNull(finalGrant)
            assertNotNull(finalGrant!!.revokedAtEpochMs)
            assertTrue(finalGrant.attemptsUsed in 0..1)
            assertTrue(store.reserveAuthorizedAttempt(request, 2_001L).isFailure)
            assertTrue(!store.isAuthorizedInFlight("test-grant", request, 2_001L))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun simultaneousReservationsCannotExceedFiveAttempts() {
        val barrier = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(10)
        try {
            val futures = (1..10).map {
                val independentStore = CampaignAuthorizationStore(context)
                executor.submit<Boolean> {
                    barrier.await()
                    independentStore.reserveAuthorizedAttempt(request, 2_000L).isSuccess
                }
            }
            barrier.countDown()
            val successes = futures.count { it.get(10, TimeUnit.SECONDS) }
            assertEquals(5, successes)
            assertEquals(5, store.load()!!.attemptsUsed)
            assertTrue(store.isAuthorizedInFlight("test-grant", request, 2_001L))
        } finally {
            executor.shutdownNow()
        }
    }
}
