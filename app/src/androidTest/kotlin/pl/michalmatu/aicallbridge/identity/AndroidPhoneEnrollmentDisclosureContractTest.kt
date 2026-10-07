package pl.michalmatu.aicallbridge.identity

import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.agent.CallCommitmentGate
import pl.michalmatu.aicallbridge.agent.CallConfirmationPolicy
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId
import pl.michalmatu.aicallbridge.textagent.AuthorizedPhoneFactBackend
import pl.michalmatu.aicallbridge.textagent.AuthorizedPhoneFactScope
import pl.michalmatu.aicallbridge.textagent.CallTextAgentOutputApprovalPolicy
import pl.michalmatu.aicallbridge.textagent.TextCallTurnController

@RunWith(AndroidJUnit4::class)
class AndroidPhoneEnrollmentDisclosureContractTest {
    @Test
    fun localEnrollmentFlowsThroughVaultPolicyAndOutputApproval() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = AndroidIdentityVaultBlobStorage(context)
        val previousCiphertext = storage.read()?.copyOf()

        try {
            ActivityScenario.launch(PhoneEnrollmentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(
                        "enrollment activity must block screenshots",
                        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
                    )

                    val root = activity.window.decorView.rootView
                    val input = findFirst<EditText>(root)
                    val save = findButton(root, "Save locally")
                    checkNotNull(input) { "phone input missing" }
                    checkNotNull(save) { "save button missing" }

                    assertFalse("phone input must not save instance state", input.isSaveEnabled)
                    assertFalse(
                        "phone input parent state saving must be disabled",
                        input.isSaveFromParentEnabled,
                    )

                    input.setText(SYNTHETIC_PHONE)
                    save.performClick()
                    assertTrue("phone input must be cleared after save", input.text.isEmpty())
                }
            }

            val vault = AndroidIdentityVault.create(context)
            val task = CallTask(
                "Synthetic service",
                "read-only phone disclosure proof",
                "identity",
                CallConstraints.unconstrained(),
                CallPreferences.none(),
                emptyMap(),
            )
            val target = CallResolvedTarget("Synthetic service", "+48100000000")
            val state = TaskGraphStateId("SYNTHETIC_PHONE_REQUEST")
            var decision: FactDisclosureDecision? = null
            val defaultPolicy = DefaultFactDisclosurePolicy()
            val policy = FactDisclosurePolicy { request, snapshot ->
                defaultPolicy.decide(request, snapshot).also { decision = it }
            }
            val backend = AuthorizedPhoneFactBackend(
                vault = vault,
                policy = policy,
                scopeProvider = {
                    AuthorizedPhoneFactScope(
                        task = task,
                        target = target,
                        state = state,
                        generation = 11L,
                        mode = CallTaskMode.GENUINE,
                        authorized = true,
                    )
                },
            )

            val workflow = CallWorkflow(task, CallConfirmationPolicy()) { }
            workflow.resolveTarget(target)
            workflow.markDialing()
            workflow.markCallActive()

            val controller = TextCallTurnController(
                backend,
                CallTextAgentOutputApprovalPolicy(
                    workflow,
                    CallCommitmentGate { "synthetic-no-call" },
                ),
            )

            var approved: String? = null
            var dropped = false
            var error: String? = null
            controller.submitUserText(
                AuthorizedPhoneFactBackend.DISCLOSE_PHONE_CONTROL,
                object : TextCallTurnController.Listener {
                    override fun onApprovedResponse(text: String) {
                        approved = text
                    }

                    override fun onDroppedResponse() {
                        dropped = true
                    }

                    override fun onError(reason: String) {
                        error = reason
                    }
                },
            )

            assertEquals(FactDisclosureDecision.ALLOW, decision)
            assertFalse("approved disclosure must not be dropped", dropped)
            assertNull("approved disclosure must not error", error)
            assertTrue("approved disclosure speech missing", approved != null)
            assertTrue(
                "approved speech does not match synthetic enrollment",
                approved!!.filter { it in '0'..'9' } == SYNTHETIC_PHONE,
            )
        } finally {
            if (previousCiphertext == null) {
                storage.clear()
            } else {
                storage.write(previousCiphertext)
            }
        }
    }

    private inline fun <reified T> findFirst(root: android.view.View): T? {
        if (root is T) return root
        val group = root as? android.view.ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findFirst<T>(group.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun findButton(root: android.view.View, text: String): Button? {
        if (root is Button && root.text.toString() == text) return root
        val group = root as? android.view.ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findButton(group.getChildAt(index), text)?.let { return it }
        }
        return null
    }

    private companion object {
        const val SYNTHETIC_PHONE = "123456789"
    }
}
