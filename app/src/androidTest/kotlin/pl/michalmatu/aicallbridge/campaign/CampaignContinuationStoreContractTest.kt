package pl.michalmatu.aicallbridge.campaign

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Preferences-only test: does not start the service, dial or capture audio. */
@RunWith(AndroidJUnit4::class)
class CampaignContinuationStoreContractTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().context

    @After fun clear() {
        context.getSharedPreferences("campaign_continuation_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun authorizedRunAndRetryDueTimeSurviveStoreRecreation() {
        val first = CampaignContinuationStore(context)
        first.clear()
        first.begin("granted-run", "sim-a", 1_000L)
        assertEquals(1_000L, CampaignContinuationStore(context).load()!!.nextAttemptAtEpochMs)
        val prepared = first.prepareAttempt("granted-run", 1_000L)
        assertEquals(31_000L, prepared!!.nextAttemptAtEpochMs)
        val next = CampaignContinuationStore(context).planRetry("granted-run", 2_000L)
        assertNotNull(next)
        assertEquals(1, next!!.incompleteAttempts)
        assertEquals(32_000L, next.nextAttemptAtEpochMs)
        assertEquals("sim-a", CampaignContinuationStore(context).load()!!.accountScope)
        assertNull(first.planRetry("different-grant", 3_000L))
        first.clear()
        assertNull(CampaignContinuationStore(context).load())
    }
}
