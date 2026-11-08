package pl.michalmatu.aicallbridge.identity

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.R

/** Real Android UI create/rename/delete, but with a test-only store, never the owner's vault. */
@RunWith(AndroidJUnit4::class)
class AndroidCustomVaultUiAcceptanceTest {
    private val instrument get() = InstrumentationRegistry.getInstrumentation()

    private fun allNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        result.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { allNodes(it, result) }
        }
    }

    private fun dialogEdits(): List<AccessibilityNodeInfo> {
        instrument.waitForIdleSync()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        allNodes(requireNotNull(instrument.uiAutomation.rootInActiveWindow), nodes)
        return nodes.filter { it.className?.toString() == EditText::class.java.name }
    }

    private fun setDialogField(index: Int, value: String) {
        val edits = dialogEdits()
        assertEquals("Unexpected dialog fields", 2, edits.size)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        assertTrue("Cannot type into test-only dialog field", edits[index].performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, args))
    }

    private fun confirmDialog(text: String) {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        allNodes(requireNotNull(instrument.uiAutomation.rootInActiveWindow), nodes)
        val buttons = nodes.filter {
            it.className?.toString() == "android.widget.Button" && it.text?.toString() == text
        }
        assertEquals("Dialog action missing", 1, buttons.size)
        assertTrue(buttons.single().performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrument.waitForIdleSync()
    }

    @Test fun createRenameAndDeleteThroughUiOnIsolatedCustomVault() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(app.cacheDir, "vault-ui-qa-" + UUID.randomUUID())
        assertTrue(dir.mkdirs())
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = dir
        }
        val vault = AndroidCustomIdentityVault(isolated)
        val marker = "UI QA " + UUID.randomUUID().toString().take(8)
        val secret = "SYNTHETIC-UI-ONLY-" + UUID.randomUUID()
        try {
            ActivityScenario.launch(IdentityVaultManagerActivity::class.java).use { scenario ->
                lateinit var saveText: String
                lateinit var removeText: String
                scenario.onActivity { activity ->
                    // The screen originally renders metadata only. Inject a separate backend
                    // and re-render before the first test interaction; do not write owner data.
                    val field = IdentityVaultManagerActivity::class.java.getDeclaredField("custom")
                    field.isAccessible = true
                    field.set(activity, vault)
                    val render = IdentityVaultManagerActivity::class.java.getDeclaredMethod("render")
                    render.isAccessible = true
                    render.invoke(activity)
                    saveText = activity.getString(R.string.ux10_vault_save)
                    removeText = activity.getString(R.string.ux10_vault_remove)
                    requireNotNull(activity.window.decorView.findViewWithTag<View>("vault-add-custom"))
                        .performClick()
                }

                setDialogField(0, marker)
                setDialogField(1, secret)
                confirmDialog(saveText)
                val created = vault.list().single()
                assertEquals(marker, created.name)
                scenario.onActivity { activity ->
                    assertNotNull(activity.window.decorView.findViewWithTag<View>(
                        "vault-custom:" + created.id))
                    requireNotNull(activity.window.decorView.findViewWithTag<View>(
                        "vault-custom:" + created.id + ":edit")).performClick()
                }
                setDialogField(0, marker + " edited")
                // The blank replacement field must preserve the existing encrypted value.
                confirmDialog(saveText)
                assertEquals(marker + " edited", vault.list().single().name)
                assertFalse(File(dir, "identity-custom-v1.json").readText().contains(secret))

                scenario.onActivity { activity ->
                    requireNotNull(activity.window.decorView.findViewWithTag<View>(
                        "vault-custom:" + created.id + ":remove")).performClick()
                }
                confirmDialog(removeText)
                assertTrue(vault.list().isEmpty())
                assertFalse(File(dir, "identity-custom-v1.json").exists())
                println("VAULT_CUSTOM_UI_ISOLATED_CEATE_EDIT_DELETE_PASS")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
