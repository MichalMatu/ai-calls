package pl.michalmatu.aicallbridge

import android.os.Build
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.conversation.AndroidConversationPreparationStore
import pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity
import pl.michalmatu.aicallbridge.conversation.LabCaseLibrary
import pl.michalmatu.aicallbridge.conversation.LabCaseRevision
import pl.michalmatu.aicallbridge.conversation.LabDialogueReport
import pl.michalmatu.aicallbridge.conversation.LabReportLibrary
import pl.michalmatu.aicallbridge.conversation.LabReportTurn
import pl.michalmatu.aicallbridge.conversation.LabReviewDimension
import pl.michalmatu.aicallbridge.conversation.LabReviewEvidence
import pl.michalmatu.aicallbridge.conversation.LabReviewVerdict
import pl.michalmatu.aicallbridge.conversation.LabRole
import pl.michalmatu.aicallbridge.conversation.SkillLabIsolatedTestStore
import pl.michalmatu.aicallbridge.ui.SettingsHubActivity

/** Isolated, no-call Android Keystore and Lab UI acceptance. Never touches owner's cases. */
@RunWith(AndroidJUnit4::class)
class AndroidLabDialogStorageAcceptanceTest {
    @Test fun encryptedCaseAndReportRoundTripAcrossFreshStoreInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nonce = UUID.randomUUID().toString()
        val directory = "lab-acceptance-" + nonce
        val alias = "pl.michalmatu.aicallbridge.lab.acceptance." + nonce
        val title = "Isolated case fixture"
        val secretFixture = "TEST_PRIVATE_FIXTURE_DO_NOT_LOG"
        fun open() = AndroidConversationPreparationStore(context, directoryName = directory,
            keyAlias = alias, legacyPreferencesName = "lab-acceptance-unused-" + nonce)
        try {
            val first = open()
            val candidate = LabCaseRevision(1, title, "Conversation continuity", LabRole.GENERAL,
                "Neutral opening", "State", secretFixture, "Keep context",
                "question => Clarify details", "Finish")
            first.saveLabCases(LabCaseLibrary().create(candidate))
            val id = open().loadLabCases().cases.single().id
            open().saveLabCases(open().loadLabCases().update(id, candidate.copy(
                title = "Updated isolated fixture")))
            assertEquals(2, open().loadLabCases().cases.single().versions.size)
            assertEquals("Updated isolated fixture",
                open().loadLabCases().cases.single().selected().title)
            val report = LabDialogueReport("test-report", id, 2, "fixture-model", "fixture-skills",
                listOf(LabReportTurn(1, "Neutral opening", "Reply", false, 35)),
                emptyList(), LabReviewDimension.entries.map {
                    LabReviewEvidence(it, LabReviewVerdict.UNASSESSED, null, "")
                }, false)
            open().saveLabReports(LabReportLibrary().add(report))
            assertEquals(1, open().loadLabReports().reports.single().turns.size)
            val folder = File(context.noBackupFilesDir, directory)
            val ciphertext = folder.listFiles().orEmpty().joinToString("") {
                it.readText()
            }
            assertFalse(ciphertext.contains(secretFixture))
            assertFalse(ciphertext.contains(title))
            open().saveLabCases(open().loadLabCases().delete(id))
            assertTrue(open().loadLabCases().cases.isEmpty())
        } finally {
            File(context.noBackupFilesDir, directory).deleteRecursively()
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
            }
            context.deleteSharedPreferences("lab-acceptance-unused-" + nonce)
        }
    }

    @Test fun labManualAndAdaptiveControlsPresentWithoutStartingModel() {
        ActivityScenario.launch(ConversationSkillLabActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                assertNotNull(decor.findViewWithTag<Spinner>("lab-preset-picker"))
                assertNotNull(decor.findViewWithTag<Spinner>("lab-case-picker"))
                assertNotNull(decor.findViewWithTag<EditText>("lab-manual-input"))
                assertNotNull(decor.findViewWithTag<Button>("lab-start"))
                assertNotNull(decor.findViewWithTag<Button>("lab-adaptive-start"))
                assertFalse(decor.findViewWithTag<Button>("lab-manual-send").isEnabled)
                assertNotNull(decor.findViewWithTag<android.widget.ScrollView>(
                    "lab-options-scroll"))
                val transcript = requireNotNull(decor.findViewWithTag<android.widget.ScrollView>(
                    "lab-transcript-scroll"))
                val composer = requireNotNull(decor.findViewWithTag<android.view.View>(
                    "lab-composer-holder"))
                assertFalse("empty_chat_must_not_take_setup_space", transcript.isShown)
                assertFalse("composer_is_only_for_the_session", composer.isShown)
                assertTrue("setup_must_own_the_viewport", decor.findViewWithTag<android.view.View>(
                    "lab-options-scroll").isShown)
            }
        }
    }

    @Test fun settingsOpensLabAndCaseEditorPersistsInstructions() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val isolated = "lab-editor-" + UUID.randomUUID()
        val store = AndroidConversationPreparationStore(context, directoryName = isolated,
            keyAlias = isolated, legacyPreferencesName = isolated)
        val monitor = instrument.addMonitor(ConversationSkillLabActivity::class.java.name, null, false)
        val title = "Synthetic case fixture"
        val instructions = "Use the fictional label Cypress."
        fun choose(label: String) {
            val matches = ArrayList<View>()
            WindowInspector.getGlobalWindowViews().last().findViewsWithText(
                matches, label, View.FIND_VIEWS_WITH_TEXT)
            val option = matches.filterIsInstance<TextView>().first { it.text.toString() == label }
            val list = generateSequence(option.parent) { it.parent }.filterIsInstance<ListView>().first()
            val index = list.getPositionForView(option)
            assertTrue(list.performItemClick(option, index, list.adapter.getItemId(index)))
        }
        SkillLabIsolatedTestStore.setForDebugTest(context, store)
        try {
            ActivityScenario.launch(SettingsHubActivity::class.java).use { scenario ->
                scenario.onActivity { settings ->
                    val entry = requireNotNull(settings.window.decorView.findViewWithTag<View>(
                        "settings-destination:ConversationSkillLabActivity"))
                    assertTrue(entry.isShown && entry.performClick())
                }
                val lab = requireNotNull(instrument.waitForMonitorWithTimeout(monitor, 12_000))
                try {
                    instrument.runOnMainSync {
                        val manage = lab.window.decorView.findViewWithTag<View>("lab-case-manage")
                        assertTrue(manage.performClick())
                        choose(lab.getString(R.string.lab_case_new))
                        val editor = WindowInspector.getGlobalWindowViews().last()
                        editor.findViewWithTag<EditText>("lab-case-field:title").setText(title)
                        editor.findViewWithTag<EditText>("lab-case-field:goal").setText("Check the label")
                        editor.findViewWithTag<EditText>("lab-case-field:opening").setText("Test opening")
                        editor.findViewWithTag<EditText>("lab-case-field:instructions").setText(instructions)
                        assertTrue(editor.findViewById<Button>(android.R.id.button1).performClick())
                    }
                    assertEquals(instructions, store.loadLabCases().cases.single().selected().assistantInstructions)
                    instrument.waitForIdleSync()
                    instrument.runOnMainSync {
                        lab.window.decorView.findViewWithTag<View>("lab-case-manage").performClick()
                        choose("$title · v1")
                        choose(lab.getString(R.string.lab_case_edit))
                        assertEquals(instructions, WindowInspector.getGlobalWindowViews().last()
                            .findViewWithTag<EditText>("lab-case-field:instructions").text.toString())
                    }
                } finally {
                    instrument.runOnMainSync { lab.finish() }
                }
            }
        } finally {
            instrument.removeMonitor(monitor)
            SkillLabIsolatedTestStore.setForDebugTest(context, null)
            File(context.noBackupFilesDir, isolated).deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(isolated)
        }
    }
}
