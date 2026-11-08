package pl.michalmatu.aicallbridge

import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
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
            }
        }
    }
}
