package pl.michalmatu.aicallbridge.localcall

import android.content.Context
import org.junit.Test
import pl.michalmatu.aicallbridge.agent.CallPlan
import pl.michalmatu.aicallbridge.agent.CallWorkflow
import pl.michalmatu.aicallbridge.identity.AuthorizedFactSnapshot
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider
import pl.michalmatu.aicallbridge.taskgraph.TaskGraphDefinition

class AndroidTextCallReadinessBindingContractTest {
    @Test
    fun `product readiness binding contract compiles`() = Unit

    @Suppress("unused")
    private fun compileBindingContract(
        context: Context,
        workflow: CallWorkflow,
        targetAuthorization: DialTargetAuthorization,
        callPlan: CallPlan,
        phraseMatrix: PhraseMatrix,
        taskGraph: TaskGraphDefinition,
        authorizedFacts: AuthorizedFactSnapshot,
    ) {
        AndroidTextCallReadiness.create(
            context = context,
            workflow = workflow,
            targetAuthorization = targetAuthorization,
            provider = TextLlmProvider.LOCAL_PHONE_LLM,
            callPlan = callPlan,
            phraseMatrix = phraseMatrix,
            taskGraph = taskGraph,
            authorizedFacts = authorizedFacts,
        )
        LocalPhoneTextCallReadiness.create(
            context = context,
            workflow = workflow,
            targetAuthorization = targetAuthorization,
            callPlan = callPlan,
            phraseMatrix = phraseMatrix,
            taskGraph = taskGraph,
            authorizedFacts = authorizedFacts,
        )
    }
}
