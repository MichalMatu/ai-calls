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
 * Uses isolated target-app preferences, never the real task authorization.
 * No dialing or operator effect occurs.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCampaignAuthorizationStoreContractTest {
    private val testPreferencesName = "campaign_authorization_grant_instrumentation_v1"
    private lateinit var context: Context
    private lateinit var store: CampaignAuthorizationStore
    private val request = ClirCampaignSpec.enableRequest("test-voice")

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(testPreferencesName, Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = CampaignAuthorizationStore(context, testPreferencesName)
        store.save(ClirCampaignSpec.acceptEnableTask("test-voice", 1_000L, "test-grant"))
    }

    @After fun tearDown() {
        context.getSharedPreferences(testPreferencesName, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun acceptedTaskHasNoExpiryAndCanonicalSaveRemovesObsoleteExpiryData() {
        assertTrue(store.reserveAuthorizedAttempt(request).isSuccess)
        val prefs = context.getSharedPreferences(testPreferencesName, Context.MODE_PRIVATE)
        prefs.edit().putLong("expires_at", 2_000L).commit()
        val accepted = store.load()!!
        assertTrue(store.isAuthorizedInFlight(accepted.grantId, request))
        store.save(accepted)
        assertTrue(!prefs.contains("expires_at"))
        assertTrue(CampaignAuthorizationStore(context, testPreferencesName)
            .isAuthorizedInFlight(accepted.grantId, request))
        assertTrue(!store.isAuthorizedInFlight(accepted.grantId,
            request.copy(accountScope = "other-sim")))
        store.save(accepted.copy(createdAtEpochMs = Long.MAX_VALUE))
        assertTrue(store.isAuthorizedInFlight(accepted.grantId, request))
        store.revoke(1L)
        assertTrue(!store.isAuthorizedInFlight(accepted.grantId, request))
    }

    @Test fun revokedGrantCannotBeResurrectedByConcurrentAttemptReservation() {
        val other = CampaignAuthorizationStore(context, testPreferencesName)
        val barrier = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val reserve = executor.submit<Boolean> {
                barrier.await()
                store.reserveAuthorizedAttempt(request).isSuccess
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
            assertTrue(store.reserveAuthorizedAttempt(request).isFailure)
            assertTrue(!store.isAuthorizedInFlight("test-grant", request))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun recordingIsOffByDefaultAndExplicitCampaignSettingIsDurable() {
        assertTrue(store.load()!!.recordCallEvidence == false)
        store.save(store.load()!!.copy(recordCallEvidence = true))
        assertTrue(CampaignAuthorizationStore(context, testPreferencesName).load()!!.recordCallEvidence)
        store.disableEvidenceRecording()
        assertTrue(CampaignAuthorizationStore(context, testPreferencesName).load()!!.recordCallEvidence == false)
        assertTrue(store.reserveAuthorizedAttempt(request).isSuccess)
    }

    @Test fun simultaneousReservationsKeepExactScopeWithoutFiveCallCeiling() {
        val barrier = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(10)
        try {
            val futures = (1..10).map {
                val independentStore = CampaignAuthorizationStore(context, testPreferencesName)
                executor.submit<Boolean> {
                    barrier.await()
                    independentStore.reserveAuthorizedAttempt(request).isSuccess
                }
            }
            barrier.countDown()
            val successes = futures.count { it.get(10, TimeUnit.SECONDS) }
            assertEquals(10, successes)
            assertEquals(10, store.load()!!.attemptsUsed)
            assertTrue(store.isAuthorizedInFlight("test-grant", request))
        } finally {
            executor.shutdownNow()
        }
    }
}
