package pl.michalmatu.aicallbridge.textagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
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
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVaultBlobStorage
import pl.michalmatu.aicallbridge.identity.CallTaskMode
import pl.michalmatu.aicallbridge.identity.DefaultFactDisclosurePolicy
import pl.michalmatu.aicallbridge.identity.FactDisclosureDecision
import pl.michalmatu.aicallbridge.identity.FactDisclosurePolicy
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.IdentitySecretValue
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

@RunWith(AndroidJUnit4::class)
class AndroidAuthorizedPhoneFactBackendContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun syntheticPhoneFollowsProductionDisclosureAndOutputApprovalPath() {
        val alias = "aicb-phone-disclosure-" + UUID.randomUUID()
        val record = "identity-phone-disclosure-" + UUID.randomUUID() + ".bin"
        try {
            val vault = AndroidIdentityVault.create(context, alias, record)
            vault.put(
                IdentityFieldId.PHONE,
                IdentitySecretValue.of(SYNTHETIC_PHONE),
            ).getOrThrow()

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
            var disclosureDecision: FactDisclosureDecision? = null
            val defaultPolicy = DefaultFactDisclosurePolicy()
            val recordingPolicy = FactDisclosurePolicy { request, snapshot ->
                defaultPolicy.decide(request, snapshot).also {
                    disclosureDecision = it
                }
            }

            val backend = AuthorizedPhoneFactBackend(
                vault = vault,
                policy = recordingPolicy,
                scopeProvider = {
                    AuthorizedPhoneFactScope(
                        task = task,
                        target = target,
                        state = state,
                        generation = 9L,
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

            assertEquals(FactDisclosureDecision.ALLOW, disclosureDecision)
            assertFalse(dropped)
            assertNull(error)
            assertTrue(approved != null)
            assertTrue(approved!!.filter { it in '0'..'9' } == SYNTHETIC_PHONE)
        } finally {
            AndroidIdentityVaultBlobStorage(context, record).clear()
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }

    private companion object {
        const val SYNTHETIC_PHONE = "123456789"
    }
}
