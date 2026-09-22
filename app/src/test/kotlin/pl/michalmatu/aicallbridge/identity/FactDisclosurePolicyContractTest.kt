package pl.michalmatu.aicallbridge.identity

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallConstraints
import pl.michalmatu.aicallbridge.agent.CallPreferences
import pl.michalmatu.aicallbridge.agent.CallResolvedTarget
import pl.michalmatu.aicallbridge.agent.CallTask
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphStateId

class FactDisclosurePolicyContractTest {
    private val task = CallTask(
        "Example clinic",
        "book appointment",
        "appointment",
        CallConstraints.unconstrained(),
        CallPreferences.none(),
        emptyMap(),
    )
    private val target = CallResolvedTarget("Example clinic", "+48123456789")
    private val state = TaskGraphStateId("IDENTITY_REQUEST")
    private val policy = DefaultFactDisclosurePolicy()

    @Test
    fun `vault availability alone never authorizes disclosure`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.PHONE),
            authorized = emptySet(),
        )

        assertEquals(
            FactDisclosureDecision.ASK_USER,
            policy.decide(request(IdentityFieldId.PHONE), snapshot),
        )
    }

    @Test
    fun `missing field fails closed`() {
        val snapshot = snapshot(
            available = emptySet(),
            authorized = setOf(IdentityFieldId.PHONE),
        )

        assertEquals(
            FactDisclosureDecision.DENY,
            policy.decide(request(IdentityFieldId.PHONE), snapshot),
        )
    }

    @Test
    fun `authorized ordinary field may be disclosed for exact scope`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.FIRST_NAME),
            authorized = setOf(IdentityFieldId.FIRST_NAME),
        )

        assertEquals(
            FactDisclosureDecision.ALLOW,
            policy.decide(request(IdentityFieldId.FIRST_NAME), snapshot),
        )
    }

    @Test
    fun `authorized field in wrong taskgraph state fails closed`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.EMAIL),
            authorized = setOf(IdentityFieldId.EMAIL),
        )

        assertEquals(
            FactDisclosureDecision.DENY,
            policy.decide(
                request(IdentityFieldId.EMAIL).copy(currentState = TaskGraphStateId("WAITING_OFFER")),
                snapshot,
            ),
        )
    }

    @Test
    fun `high sensitivity field requires explicit per task approval`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.PESEL),
            authorized = setOf(IdentityFieldId.PESEL),
            highSensitivityApproved = emptySet(),
        )

        assertEquals(
            FactDisclosureDecision.ASK_USER,
            policy.decide(request(IdentityFieldId.PESEL), snapshot),
        )
    }

    @Test
    fun `explicitly approved high sensitivity field is allowed for genuine task`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.PESEL),
            authorized = setOf(IdentityFieldId.PESEL),
            highSensitivityApproved = setOf(IdentityFieldId.PESEL),
        )

        assertEquals(
            FactDisclosureDecision.ALLOW,
            policy.decide(request(IdentityFieldId.PESEL), snapshot),
        )
    }

    @Test
    fun `test mode denies high sensitivity disclosure even when approved`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.PESEL),
            authorized = setOf(IdentityFieldId.PESEL),
            highSensitivityApproved = setOf(IdentityFieldId.PESEL),
        )

        assertEquals(
            FactDisclosureDecision.DENY,
            policy.decide(request(IdentityFieldId.PESEL, CallTaskMode.TEST), snapshot),
        )
    }

    @Test
    fun `wrong task target or generation fails closed`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.EMAIL),
            authorized = setOf(IdentityFieldId.EMAIL),
        )
        val otherTask = CallTask(
            "Other clinic",
            "book appointment",
            "appointment",
            CallConstraints.unconstrained(),
            CallPreferences.none(),
            emptyMap(),
        )
        val otherTarget = CallResolvedTarget("Other clinic", "+48999999999")

        assertEquals(
            FactDisclosureDecision.DENY,
            policy.decide(request(IdentityFieldId.EMAIL).copy(task = otherTask), snapshot),
        )
        assertEquals(
            FactDisclosureDecision.DENY,
            policy.decide(request(IdentityFieldId.EMAIL).copy(target = otherTarget), snapshot),
        )
        assertEquals(
            FactDisclosureDecision.DENY,
            policy.decide(request(IdentityFieldId.EMAIL).copy(snapshotGeneration = 6L), snapshot),
        )
    }

    @Test
    fun `authorized fact snapshot contains field ids and state scope but no identity plaintext values`() {
        val snapshot = snapshot(
            available = setOf(IdentityFieldId.EMAIL, IdentityFieldId.PHONE),
            authorized = setOf(IdentityFieldId.EMAIL),
        )

        assertEquals(setOf(IdentityFieldId.EMAIL, IdentityFieldId.PHONE), snapshot.availableFields)
        assertEquals(setOf(IdentityFieldId.EMAIL), snapshot.authorizedFields)
        assertEquals(setOf(state), snapshot.allowedDisclosureStates.getValue(IdentityFieldId.EMAIL))
        assertEquals(7L, snapshot.generation)
    }

    private fun snapshot(
        available: Set<IdentityFieldId>,
        authorized: Set<IdentityFieldId>,
        highSensitivityApproved: Set<IdentityFieldId> = emptySet(),
    ) = AuthorizedFactSnapshot(
        task = task,
        target = target,
        generation = 7L,
        availableFields = available,
        authorizedFields = authorized,
        highSensitivityApprovedFields = highSensitivityApproved,
        allowedDisclosureStates = authorized.associateWith { setOf(state) },
    )

    private fun request(
        fieldId: IdentityFieldId,
        mode: CallTaskMode = CallTaskMode.GENUINE,
    ) = FactDisclosureRequest(
        task = task,
        target = target,
        currentState = state,
        fieldId = fieldId,
        mode = mode,
        snapshotGeneration = 7L,
    )
}
