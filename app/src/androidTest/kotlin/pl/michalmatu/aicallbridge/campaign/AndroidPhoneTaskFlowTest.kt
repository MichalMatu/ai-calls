package pl.michalmatu.aicallbridge.campaign

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import pl.michalmatu.aicallbridge.MainActivity
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore

/** Emulator only. Impossible SIM prevents Telecom dispatch even if a test accidentally approves. */
@RunWith(AndroidJUnit4::class)
class AndroidPhoneTaskFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val store get() = AndroidConversationPreparationStore(context)
    private var originalTasks = emptyList<PhoneTask>()
    private var originalDraft: PhoneTaskDraft? = null
    private fun id() = UUID.randomUUID().toString()

    @Before fun isolate() {
        check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) {
            "emulator_only_no_private_phone_instrumentation"
        }
        PhoneTaskWork.io.submit {
            originalTasks = store.phoneTasks()
            originalDraft = store.phoneTaskDraft()
            store.updatePhoneTasks { emptyList() }
            store.savePhoneTaskDraft(PhoneTaskDraft())
            PhoneTaskWork.recover(context)
        }.get(10, TimeUnit.SECONDS)
    }
    @After fun restore() {
        shell("input keyevent KEYCODE_WAKEUP")
        PhoneTaskWork.io.submit {
            store.updatePhoneTasks { originalTasks }
            store.savePhoneTaskDraft(originalDraft ?: PhoneTaskDraft())
            PhoneTaskScheduler.reconcile(context)
        }.get(10, TimeUnit.SECONDS)
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command)
        .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("expected_state_not_observed", condition())
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val directory = File(context.getExternalFilesDir(null), "phone-task-ui").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.jpg").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
    }
    private fun task(start: Long = System.currentTimeMillis() + 600_000) = PhoneTask(
        id(), "Oil service quotes", "quote", "Compare an oil service quote", "Ask about labour, parts and availability.",
        Int.MAX_VALUE, "synthetic-impossible-sim", start, start + 3_600_000,
        "Europe/Warsaw", 300_000, System.currentTimeMillis(),
        listOf(PhoneTaskRecipient(id(), "Workshop A", "+12025550101"),
            PhoneTaskRecipient(id(), "Workshop B", "+12025550102"),
            PhoneTaskRecipient(id(), "Workshop C", "+12025550103")),
    )

    @Test fun homeOpensSharedWizardWithoutCreatingGrantOrTask() {
        val before = CampaignAuthorizationStore(context).load()
        val monitor = instrumentation.addMonitor(PhoneTaskWizardActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                home.onActivity {
                    requireNotNull(it.window.decorView.findViewWithTag<View>("home-task-start")).performClick()
                }
                val next = instrumentation.waitForMonitorWithTimeout(monitor, 9_000)
                assertNotNull(next)
                await {
                    var present = false
                    instrumentation.runOnMainSync { present = next?.window?.decorView?.findViewWithTag<View>("task-goal") != null }
                    present
                }
                screenshot("wizard-goal")
                instrumentation.runOnMainSync { next?.finish() }
            }
        } finally { instrumentation.removeMonitor(monitor) }
        assertEquals(before, CampaignAuthorizationStore(context).load())
        assertTrue(store.phoneTasks().isEmpty())
    }

    @Test fun draftRotationRestoresPrivateTextAndDoesNotApprove() {
        store.savePhoneTaskDraft(PhoneTaskDraft(title = "Synthetic price enquiry", goal = "Compare synthetic workshop quotes"))
        ActivityScenario.launch(PhoneTaskWizardActivity::class.java).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.window.decorView.findViewWithTag<View>("task-goal") != null }; ready }
            scenario.onActivity {
                it.window.decorView.findViewWithTag<EditText>("task-goal").setText("Find the full service price including parts")
            }
            scenario.recreate()
            await { var ready = false; scenario.onActivity {
                ready = it.window.decorView.findViewWithTag<EditText>("task-goal")?.text?.toString() ==
                    "Find the full service price including parts"
            }; ready }
        }
        assertTrue(store.phoneTasks().isEmpty())
    }

    @Test fun reviewContainsAllRecipientsWindowAndBoundedScopeButDoesNotDial() {
        val now = java.time.LocalDateTime.now().plusHours(1)
        store.savePhoneTaskDraft(PhoneTaskDraft(step = 3, title = "Oil service quotes", scenarioId = "quote",
            goal = "Compare oil service prices", suppliedBrief = "Ask about labour and parts.",
            recipients = "Workshop A; +12025550101\nWorkshop B; +12025550102\n+1 (202) 555-0101",
            subscriptionId = Int.MAX_VALUE, accountScope = "synthetic-impossible-sim",
            scheduled = true, startLocal = now.toString(), endLocal = now.plusHours(1).toString()))
        val before = CampaignAuthorizationStore(context).load()
        ActivityScenario.launch(PhoneTaskWizardActivity::class.java).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.window.decorView.findViewWithTag<View>("task-review") != null }; ready }
            screenshot("wizard-review")
            scenario.onActivity { assertNotNull(it.window.decorView.findViewWithTag<View>("task-approve")) }
        }
        assertEquals(before, CampaignAuthorizationStore(context).load())
        assertTrue(store.phoneTasks().isEmpty())
    }

    @Test fun recipientsAndScheduleValidateAndNavigateWithoutApproval() {
        store.savePhoneTaskDraft(PhoneTaskDraft(step = 1, recipients = "Workshop A; invalid-number"))
        ActivityScenario.launch(PhoneTaskWizardActivity::class.java).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.window.decorView.findViewWithTag<View>("task-recipients") != null }; ready }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("task-next").performClick()
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("task-feedback").text.isNotBlank())
                assertNotNull(activity.window.decorView.findViewWithTag<View>("task-recipients"))
            }
            screenshot("wizard-recipients")
        }
        PhoneTaskWork.io.submit {
            store.savePhoneTaskDraft(PhoneTaskDraft(step = 2, title = "Oil service quotes", scenarioId = "quote",
                goal = "Compare oil service prices", suppliedBrief = "Ask about labour and parts.",
                recipients = "Workshop A; +12025550101", subscriptionId = Int.MAX_VALUE,
                accountScope = "synthetic-impossible-sim", retryMinutes = "0"))
        }.get(10, TimeUnit.SECONDS)
        ActivityScenario.launch(PhoneTaskWizardActivity::class.java).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.window.decorView.findViewWithTag<View>("task-retry-minutes") != null }; ready }
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("task-next").performClick()
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("task-feedback").text.isNotBlank())
                activity.window.decorView.findViewWithTag<EditText>("task-retry-minutes").setText("5")
            }
            screenshot("wizard-schedule")
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("task-next").performClick()
                assertNotNull(activity.window.decorView.findViewWithTag<View>("task-review"))
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals("5", activity.window.decorView.findViewWithTag<EditText>("task-retry-minutes").text.toString())
            }
        }
        assertTrue(store.phoneTasks().isEmpty())
    }

    @Test fun taskPayloadAndDraftUseExistingEncryptedNoBackupRecords() {
        val task = task().copy(title = "SYNTHETIC_PRIVATE_TASK_CONTENT", state = PhoneTaskState.PAUSED)
        store.updatePhoneTasks { listOf(task) }
        store.savePhoneTaskDraft(PhoneTaskDraft(goal = "SYNTHETIC_PRIVATE_DRAFT_CONTENT"))
        assertEquals(task, store.phoneTasks().single())
        val files = File(context.noBackupFilesDir, "conversation-preparation").listFiles().orEmpty()
        assertTrue(files.isNotEmpty())
        files.filter { it.extension == "json" }.forEach {
            val encrypted = it.readText()
            assertFalse(encrypted.contains(task.title))
            assertFalse(encrypted.contains("SYNTHETIC_PRIVATE_DRAFT_CONTENT"))
            assertFalse(encrypted.contains(task.recipients[0].number))
        }
    }

    @Test fun resultsPreserveFromPriceAndOfferExplicitRetryOfUnknownOnly() {
        val original = task(System.currentTimeMillis() - 10_000)
        val first = requireNotNull(PhoneTaskQueue.claim(listOf(original), System.currentTimeMillis() - 9_000, id()))
        val answered = PhoneTaskQueue.progress(first.task, first.attemptId, PhoneAttemptStage.TALKING,
            "synthetic-price-call", PhoneTaskAnswer(1, "Cena od 350 zł, bez części. Wolny termin w piątek."))
        val done = PhoneTaskQueue.complete(answered, first.attemptId, PhoneAttemptOutcome.ANSWERED, System.currentTimeMillis() - 8_000)
        val second = requireNotNull(PhoneTaskQueue.claim(listOf(done), System.currentTimeMillis() - 7_000, id()))
        val uncertain = PhoneTaskQueue.complete(second.task, second.attemptId, PhoneAttemptOutcome.UNKNOWN, System.currentTimeMillis() - 6_000)
            .copy(state = PhoneTaskState.PAUSED)
        store.updatePhoneTasks { listOf(uncertain) }
        ActivityScenario.launch<PhoneTasksActivity>(Intent(context, PhoneTasksActivity::class.java)
            .putExtra(AppOwnedCampaignService.EXTRA_PHONE_TASK_ID, original.id)).use { scenario ->
            await { var ready = false; scenario.onActivity { ready = it.window.decorView.findViewWithTag<View>("task-recipient:" + original.recipients[0].id) != null }; ready }
            screenshot("task-results")
            scenario.onActivity { activity ->
                val card = activity.window.decorView.findViewWithTag<android.view.ViewGroup>("task-recipient:" + original.recipients[0].id)
                assertTrue((0 until card.childCount).map { card.getChildAt(it) }.filterIsInstance<TextView>()
                    .any { it.text.contains("od 350 zł, bez części") })
            }
        }
        assertEquals(PhoneRecipientState.UNKNOWN, store.phoneTasks().single().recipients[1].state)
    }

    @Test fun restoredExactAlarmPermissionRearmsOnlyItsOwnPausedTasksWithoutDial() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 31)
        // Revocation force-stops the Android app. The emulator host revokes the
        // permission BEFORE instrumentation, then this test exercises the grant.
        org.junit.Assume.assumeFalse("emulator_test_requires_initial_alarm_denial",
            PhoneTaskScheduler.allowed(context))
        val packageName = context.packageName
        val originalGrant = CampaignAuthorizationStore(context).load()
        val future = System.currentTimeMillis() + 600_000L
        val waiting = task(future)
        val takeover = task(future).copy(state = PhoneTaskState.PAUSED,
            issue = PhoneTaskIssue.TAKEOVER)
        val cancelled = task(future).copy(state = PhoneTaskState.CANCELLED)

        PhoneTaskWork.io.submit {
            store.updatePhoneTasks { listOf(waiting, takeover, cancelled) }
            assertFalse(PhoneTaskScheduler.reconcile(context))
        }.get(10, TimeUnit.SECONDS)
        val paused = store.phoneTasks()
        assertEquals(PhoneTaskState.PAUSED, paused[0].state)
        assertEquals(PhoneTaskIssue.ALARM_PERMISSION, paused[0].issue)
        assertEquals(PhoneTaskIssue.TAKEOVER, paused[1].issue)
        assertEquals(PhoneTaskState.CANCELLED, paused[2].state)
        assertNull(PhoneTaskQueue.nextAt(paused[0]))

        // Grant does not force-stop the running instrumentation process.
        shell("appops set $packageName SCHEDULE_EXACT_ALARM allow")
        await { PhoneTaskScheduler.allowed(context) }
        PhoneTaskWork.io.submit {
            assertTrue(PhoneTaskScheduler.reconcile(context))
        }.get(10, TimeUnit.SECONDS)
        val restored = store.phoneTasks()
        assertEquals(PhoneTaskState.READY, restored[0].state)
        assertNull(restored[0].issue)
        assertNotNull(PhoneTaskQueue.nextAt(restored[0]))
        assertEquals(takeover, restored[1])
        assertEquals(cancelled, restored[2])
        assertTrue(restored.flatMap { it.recipients }.all { it.attempts.isEmpty() })
        assertEquals(originalGrant, CampaignAuthorizationStore(context).load())

        PhoneTaskWork.io.submit {
            assertTrue(PhoneTaskScheduler.reconcile(context))
        }.get(10, TimeUnit.SECONDS)
        assertEquals(restored, store.phoneTasks())
        assertEquals(originalGrant, CampaignAuthorizationStore(context).load())
    }

    @Test fun exactAlarmWakesBackgroundDialerAndRejectsChangedSimBeforeGrantOrDial() {
        val packageName = context.packageName
        shell("cmd role add-role-holder --user 0 android.app.role.DIALER $packageName")
        listOf("READ_PHONE_STATE", "CALL_PHONE", "RECORD_AUDIO", "POST_NOTIFICATIONS").forEach {
            shell("pm grant $packageName android.permission.$it")
        }
        shell("appops set $packageName SCHEDULE_EXACT_ALARM allow")
        assertTrue(AndroidTelecomOwnedCallController.canControl(context))
        assertTrue(PhoneTaskScheduler.allowed(context))
        val before = CampaignAuthorizationStore(context).load()
        val task = task(System.currentTimeMillis() + 2_500)
        PhoneTaskWork.io.submit { store.updatePhoneTasks { listOf(task) }; PhoneTaskScheduler.reconcile(context) }
            .get(10, TimeUnit.SECONDS)
        shell("input keyevent KEYCODE_HOME")
        shell("input keyevent KEYCODE_SLEEP")
        await { store.phoneTasks().single().state == PhoneTaskState.PAUSED }
        val result = store.phoneTasks().single()
        assertEquals(PhoneTaskIssue.SIM_CHANGED, result.issue)
        assertEquals(PhoneAttemptOutcome.BLOCKED, result.recipients[0].attempts.single().outcome)
        assertEquals(before, CampaignAuthorizationStore(context).load())
        assertEquals(1, result.recipients.sumOf { it.attempts.size })
        assertNull(PhoneTaskQueue.nextAt(result))
    }

    @Test fun globalStopPersistsCancellationForAllScheduledTasks() {
        val tasks = listOf(task(), task())
        store.updatePhoneTasks { tasks }
        ActivityScenario.launch(PhoneTasksActivity::class.java).use {
            instrumentation.runOnMainSync {
                context.startService(Intent(context, AppOwnedCampaignService::class.java)
                    .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI))
            }
            await { store.phoneTasks().all { task -> task.state == PhoneTaskState.CANCELLED } }
        }
        assertTrue(store.phoneTasks().all { PhoneTaskQueue.nextAt(it) == null })
    }
}
