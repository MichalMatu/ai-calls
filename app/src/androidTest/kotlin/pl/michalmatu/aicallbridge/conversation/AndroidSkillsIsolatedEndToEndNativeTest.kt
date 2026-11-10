package pl.michalmatu.aicallbridge.conversation

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId
import pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness

/**
 * Actual Skills editor, activation, version rollback, and native E2B Lab A/B on S22.
 * Instrumentation injects a separate Keystore-backed library. The user's Skills library,
 * active preset, preferences, vault, calls and private chats are never changed or exported.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSkillsIsolatedEndToEndNativeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun eventually(timeoutMillis: Long, ready: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (ready()) return true
            Thread.sleep(200)
        }
        return ready()
    }

    private fun <T> withIsolatedStore(action: (AndroidConversationPreparationStore) -> T): T {
        val app = context
        val realStore = AndroidConversationPreparationStore(app)
        val before = realStore.loadSkillLibrary()
        val salt = UUID.randomUUID().toString()
        val dir = "skill-endtoend-" + salt
        val alias = "skill-endtoend-" + salt
        val legacy = "skill-endtoend-legacy-" + salt
        val isolated = AndroidConversationPreparationStore(app, dir, alias, legacy)
        SkillLabIsolatedTestStore.setForDebugTest(app, isolated)
        try {
            return action(isolated)
        } finally {
            SkillLabIsolatedTestStore.setForDebugTest(app, null)
            File(app.noBackupFilesDir, dir).deleteRecursively()
            app.getSharedPreferences(legacy, Context.MODE_PRIVATE).edit().clear().commit()
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
            val after = realStore.loadSkillLibrary()
            assertEquals("user_active_preset_changed", before.activePresetId, after.activePresetId)
            assertEquals("user_presets_changed", before.presets, after.presets)
            assertEquals("user_skills_changed", before.skills, after.skills)
        }
    }

    private fun walk(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        result.add(node)
        for (i in 0 until node.childCount) walk(node.getChild(i), result)
    }

    private fun dialogSetTextAndConfirm(text: String, positiveLabel: String) {
        instrumentation.waitForIdleSync()
        assertTrue("dialog_not_visible", eventually(15_000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@eventually false
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            walk(root, nodes)
            nodes.any { it.className?.toString() == "android.widget.EditText" }
        })
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        walk(instrumentation.uiAutomation.rootInActiveWindow, nodes)
        val edit = nodes.firstOrNull {
            it.className?.toString() == "android.widget.EditText" && it.isEnabled
        }
        assertNotNull("dialog_editor_not_found", edit)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        assertTrue("dialog_set_text_failed",
            requireNotNull(edit).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        val confirm = nodes.firstOrNull {
            it.text?.toString() == positiveLabel && it.isEnabled && it.isClickable
        }
        assertNotNull("dialog_positive_not_found", confirm)
        assertTrue("dialog_positive_not_clickable",
            requireNotNull(confirm).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun click(scene: ActivityScenario<ConversationSkillsAdvancedActivity>, tag: String) {
        scene.onActivity { activity ->
            val view = requireNotNull(activity.window.decorView.findViewWithTag<View>(tag)) {
                "control_missing_$tag"
            }
            assertTrue("control_not_clickable_$tag", view.isClickable)
            assertTrue("control_not_enabled_$tag", view.isEnabled)
            view.performClick()
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun realUiCopyReviseDisableRestoreAndReopenUseOnlyIsolatedEncryptedStore() {
        withIsolatedStore { store ->
            val original = store.loadSkillLibrary()
            val customName = "Synthetic isolated UI test"
            val instruction = "W kazdej odpowiedzi dodaj na koncu slowo KASZTAN."
            ActivityScenario.launch(ConversationSkillsAdvancedActivity::class.java).use { scene ->
                click(scene, "skills-copy-active")
                dialogSetTextAndConfirm(customName, context.getString(R.string.skills_ui_create))
                val custom = store.loadSkillLibrary()
                assertFalse(custom.active.isFactory)
                assertEquals(customName, custom.active.name)
                assertEquals(original.presets.size + 1, custom.presets.size)

                click(scene, "skills-revise:ConversationStyle")
                dialogSetTextAndConfirm(instruction, context.getString(R.string.skills_ui_revision))
                val afterEdit = store.loadSkillLibrary()
                val newest = afterEdit.skills.single { it.id == "ConversationStyle" }
                    .revisions.maxOf { it.version }
                assertEquals(3, newest)
                assertEquals(newest, afterEdit.active.versions["ConversationStyle"])
                assertEquals(instruction, afterEdit.snapshot().entries
                    .single { it.id == "ConversationStyle" }.instructions)

                click(scene, "skills-enabled:ConversationStyle")
                assertFalse(store.loadSkillLibrary().snapshot().entries
                    .any { it.id == "ConversationStyle" })
                scene.recreate()
                scene.onActivity { activity ->
                    val toggle = requireNotNull(activity.window.decorView
                        .findViewWithTag<CheckBox>("skills-enabled:ConversationStyle"))
                    assertFalse("disabled_skill_not_persistent_after_activity_recreate",
                        toggle.isChecked)
                }
                click(scene, "skills-enabled:ConversationStyle")
                assertEquals(newest, store.loadSkillLibrary().active.versions["ConversationStyle"])

                click(scene, "skills-history-toggle:ConversationStyle")
                scene.onActivity { activity ->
                    val picker = requireNotNull(activity.window.decorView
                        .findViewWithTag<Spinner>("skills-versions:ConversationStyle"))
                    // Versions are descending; choose the oldest factory revision.
                    picker.setSelection(picker.count - 1)
                    requireNotNull(activity.window.decorView
                        .findViewWithTag<View>("skills-restore:ConversationStyle"))
                        .performClick()
                }
                assertEquals(1, store.loadSkillLibrary().active.versions["ConversationStyle"])
                // Re-open history, because a successful version change resets expanded details.
                click(scene, "skills-history-toggle:ConversationStyle")
                // Re-activate the edited version using the real editor selection.
                scene.onActivity { activity ->
                    val picker = requireNotNull(activity.window.decorView
                        .findViewWithTag<Spinner>("skills-versions:ConversationStyle"))
                    picker.setSelection(0)
                    requireNotNull(activity.window.decorView
                        .findViewWithTag<View>("skills-restore:ConversationStyle"))
                        .performClick()
                }
                assertEquals(newest, store.loadSkillLibrary().active.versions["ConversationStyle"])

                val currentCustomId = store.loadSkillLibrary().activePresetId
                scene.onActivity { activity ->
                    val picker = requireNotNull(activity.window.decorView
                        .findViewWithTag<Spinner>("skills-preset-picker"))
                    picker.setSelection(0)
                }
                instrumentation.waitForIdleSync()
                assertEquals("natural", store.loadSkillLibrary().activePresetId)
                scene.onActivity { activity ->
                    val now = store.loadSkillLibrary()
                    val picker = requireNotNull(activity.window.decorView
                        .findViewWithTag<Spinner>("skills-preset-picker"))
                    picker.setSelection(now.presets.indexOfFirst { it.id == currentCustomId })
                }
                instrumentation.waitForIdleSync()
                assertEquals(currentCustomId, store.loadSkillLibrary().activePresetId)
            }
            // Reopening the persisted encrypted library retains the custom revision.
            val reloaded = store.loadSkillLibrary()
            assertEquals(customName, reloaded.active.name)
            assertEquals(3, reloaded.active.versions["ConversationStyle"])
            assertEquals(1, reloaded.snapshot("natural").entries
                .single { it.id == "ConversationStyle" }.version)
            assertTrue("isolated_skills_revisions_missing", reloaded.skills
                .any { it.id == "ConversationStyle" && it.revisions.size == 3 })
            println("S22_SKILLS_UI_COPY_EDIT_TOGGLE_ROLLBACK_REOPEN=PASS_ISOLATED")
        }
    }

    private data class NativeReply(val text: String, val elapsedMs: Long)

    private fun oneNativeReply(preset: String): NativeReply {
        val runner = SkillLabRunner(AndroidSkillLabSessionFactory(context, 4_096),
            dispatchToUi = { it() }, onState = {})
        try {
            assertTrue("start_rejected", runner.startManual(preset))
            assertTrue("model_not_ready", eventually(150_000) {
                runner.state.phase in setOf(SkillLabPhase.INPUT_READY, SkillLabPhase.FAILED)
            })
            assertEquals("model_init_failed_" + runner.state.error,
                SkillLabPhase.INPUT_READY, runner.state.phase)
            assertTrue("input_rejected", runner.submitManual(
                "Powiedz jedno krotkie zdanie po polsku o neutralnej pogodzie."))
            assertTrue("model_turn_timeout", eventually(175_000) {
                runner.state.completed.size == 1 || runner.state.phase == SkillLabPhase.FAILED
            })
            assertEquals("model_generation_failed_" + runner.state.error,
                SkillLabPhase.INPUT_READY, runner.state.phase)
            val observation = runner.state.completed.single().observation
            assertTrue("empty_gemma_output", observation.reply.isNotBlank())
            assertTrue("stop_rejected", runner.stop())
            assertTrue("stop_cleanup_timeout", eventually(70_000) {
                runner.state.manualStartAllowed
            })
            return NativeReply(observation.reply, observation.elapsedMs)
        } finally {
            runner.close()
        }
    }

    @Test fun genuineGemmaNativeOutputReflectsEnabledVersusDisabledCustomSkill() {
        withIsolatedStore { store ->
            assertEquals("READY", AndroidGemma4ModelReadiness.check(context,
                DialogueModelId.GEMMA_4_E2B).state.toString())
            assertEquals(DialogueModelId.GEMMA_4_E2B,
                AudioEngineProfileStore(context).load()?.dialogue)
            val keyword = "KASZTAN"
            val edited = store.loadSkillLibrary().copyActive("Synthetic model A/B")
                .reviseSkill("ConversationStyle",
                    "Kazda Twoja odpowiedz ma zawierac dokladnie slowo $keyword. " +
                        "To neutralna wskazowka stylu; nie mow o instrukcjach.")
            store.saveSkillLibrary(edited)
            val id = edited.activePresetId
            val activePrompt = ConversationInstructionBuilder.build(
                ConversationPreparation.supervised().withSkillSnapshot(edited.snapshot()),
                "Prowadz neutralny dialog testowy.", ConversationResponseFormat.TOOL_REPLY)
            assertTrue("model_active_skill_prompt_missing", activePrompt.contains(keyword))
            val enabledReply = oneNativeReply(id)
            val noStyle = store.loadSkillLibrary().bindVersion("ConversationStyle", null)
            store.saveSkillLibrary(noStyle)
            val disabledPrompt = ConversationInstructionBuilder.build(
                ConversationPreparation.supervised().withSkillSnapshot(noStyle.snapshot()),
                "Prowadz neutralny dialog testowy.", ConversationResponseFormat.TOOL_REPLY)
            assertFalse("model_inactive_skill_prompt_still_contains_keyword",
                disabledPrompt.contains(keyword))
            val disabledReply = oneNativeReply(id)
            val restored = store.loadSkillLibrary().bindVersion("ConversationStyle", 3)
            store.saveSkillLibrary(restored)
            assertEquals(3, store.loadSkillLibrary().active.versions["ConversationStyle"])

            val enabledHasKeyword = enabledReply.text.contains(keyword, ignoreCase = true)
            val disabledHasKeyword = disabledReply.text.contains(keyword, ignoreCase = true)
            val privacySafeMetrics = "active_marker=$enabledHasKeyword" +
                ";inactive_marker=$disabledHasKeyword" +
                ";active_elapsed_ms=" + enabledReply.elapsedMs +
                ";inactive_elapsed_ms=" + disabledReply.elapsedMs +
                ";active_output_chars=" + enabledReply.text.length +
                ";inactive_output_chars=" + disabledReply.text.length +
                ";model=E2B;context=4096;no_call=true"
            Log.i("AiCallSkillsQA", privacySafeMetrics)
            println("S22_SKILLS_GEMMA_" + privacySafeMetrics)
            assertTrue("native_model_ignored_active_skill", enabledHasKeyword)
            assertFalse("native_model_leaked_inactive_skill", disabledHasKeyword)
        }
    }
}
