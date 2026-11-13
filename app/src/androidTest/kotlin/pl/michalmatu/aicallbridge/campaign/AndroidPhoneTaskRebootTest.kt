package pl.michalmatu.aicallbridge.campaign

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Two-stage emulator-only acceptance test. Host runs stage, opens the app to clear
 * instrumentation force-stop, verifies the alarm, reboots the emulator and runs verify.
 * All recipients use an impossible SIM; neither stage can authorize a dial.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPhoneTaskRebootTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun emulatorOnly() {
        check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) {
            "emulator_only_no_private_phone_instrumentation"
        }
    }

    @Test fun stageFutureTaskInEncryptedStore() {
        emulatorOnly()
        val now = System.currentTimeMillis()
        val start = now + 600_000L
        val task = PhoneTask(
            UUID.randomUUID().toString(), "EMULATOR_REBOOT_CONTRACT", "quote",
            "Compare synthetic service prices", "Ask about parts, labour and availability.",
            Int.MAX_VALUE, "synthetic-impossible-sim", start, start + 3_600_000L,
            "Europe/Warsaw", 300_000L, now,
            listOf(PhoneTaskRecipient(UUID.randomUUID().toString(), "Synthetic A", "+12025550101"),
                PhoneTaskRecipient(UUID.randomUUID().toString(), "Synthetic B", "+12025550102")),
        ).validated()
        assertTrue(PhoneTaskScheduler.allowed(context))
        assertNull(CampaignAuthorizationStore(context).load())
        PhoneTaskWork.io.submit {
            val store = PhoneTaskWork.store(context)
            store.updatePhoneTasks { listOf(task) }
            assertTrue(PhoneTaskScheduler.reconcile(context))
        }.get(15, TimeUnit.SECONDS)
        assertEquals(task, PhoneTaskWork.store(context).phoneTasks().single())
        assertNull(CampaignAuthorizationStore(context).load())
    }

    @Test fun verifyPersistedTaskAfterEmulatorRebootWithoutRedial() {
        emulatorOnly()
        val task = PhoneTaskWork.store(context).phoneTasks().single()
        assertEquals("EMULATOR_REBOOT_CONTRACT", task.title)
        assertEquals(PhoneTaskState.READY, task.state)
        assertEquals(2, task.recipients.size)
        assertTrue(task.recipients.all {
            it.state == PhoneRecipientState.QUEUED && it.attempts.isEmpty()
        })
        assertTrue(requireNotNull(PhoneTaskQueue.nextAt(task)) > System.currentTimeMillis())
        assertNull(CampaignAuthorizationStore(context).load())
        assertNull(PhoneTaskQueue.claim(listOf(task), System.currentTimeMillis(), UUID.randomUUID().toString()))
        PhoneTaskWork.io.submit {
            PhoneTaskWork.store(context).updatePhoneTasks { emptyList() }
            PhoneTaskScheduler.reconcile(context)
        }.get(15, TimeUnit.SECONDS)
    }
}
