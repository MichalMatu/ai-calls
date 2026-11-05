package pl.michalmatu.aicallbridge.campaign

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual app intake + real platform readiness, isolated data and intercepted service dispatch.
 * No call is placed; the production task record and recording choice are never changed.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTaskIntakeContractTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableSetOf<String>()
    private val isolated = object : ContextWrapper(base) {
        var dispatch: Intent? = null
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "task_intake_test_$name"
            names += isolatedName
            return base.getSharedPreferences(isolatedName, mode)
        }
        override fun startForegroundService(service: Intent): ComponentName? {
            dispatch = service
            return service.component
        }
    }
    @After fun clear() { names.forEach { base.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() } }

    @Test fun previewDoesNotCreateAnAuthorizationOrDispatch() {
        val store = CampaignAuthorizationStore(isolated)
        assertNull(store.load())
        val report = CampaignAgentControl.run(isolated, requestStart = false)
        assertTrue(report.contains("campaign_agent_start=NOT_REQUESTED"))
        assertNull(store.load())
        assertNull(isolated.dispatch)
    }

    @Test fun explicitCodexTaskNeedsNoExistingGrantOrMobileApprovalAndKeepsExactMode() {
        val store = CampaignAuthorizationStore(isolated)
        assertNull(store.load())
        val report = CampaignAgentControl.run(isolated, requestStart = true,
            dialogueMode = CampaignDialogueMode.GEMMA_DIRECT)
        assertTrue(report, report.contains("campaign_agent_start=DISPATCHED_TO_INTERNAL_SERVICE"))
        val accepted = checkNotNull(store.load())
        assertEquals(setOf(ClirCampaignSpec.TARGET), accepted.allowedTargets)
        assertEquals(setOf(true), accepted.allowedEffectValues)
        assertFalse(accepted.recordCallEvidence)
        assertEquals("GEMMA_DIRECT", isolated.dispatch?.getStringExtra(AppOwnedCampaignService.EXTRA_DIALOGUE_MODE))
        assertEquals(AppOwnedCampaignService.ACTION_START_CLIR_ENABLE, isolated.dispatch?.action)
    }
}
