package pl.michalmatu.aicallbridge.textagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueActionTurnMemoryTest {
    @Test
    fun currentUtteranceIsNeverTruncatedAndEarlierActionIsVisible() {
        val memory = DialogueActionTurnMemory()
        val first = "W jakiej sprawie Pan dzwoni?"
        assertEquals(first, memory.classifierInput(first))
        memory.remember(first, DialogueActionId.STATE_TASK_SUBJECT)
        val next = "Czy to ma zastrzegać numer we wszystkich połączeniach wychodzących?"
        val input = memory.classifierInput(next)
        assertTrue(input.contains("STATE_TASK_SUBJECT"))
        assertTrue(input.contains(first))
        assertTrue(input.endsWith(next))
        assertTrue(input.contains("NOWĄ WYPOWIEDŹ"))
    }

    @Test
    fun onlyRecentTurnsRemainInPrivateOnDeviceContext() {
        val memory = DialogueActionTurnMemory()
        memory.remember("pierwsza unikatowa wypowiedz", DialogueActionId.STATE_TASK_SUBJECT)
        memory.remember("druga unikatowa wypowiedz", DialogueActionId.EXPLAIN_SERVICE_SCOPE)
        memory.remember("trzecia unikatowa wypowiedz", DialogueActionId.ASK_NEXT_SERVICE_STEP)
        memory.remember("czwarta unikatowa wypowiedz", DialogueActionId.REQUEST_SERVICE_TRANSFER)
        val input = memory.classifierInput("aktualna pelna wypowiedz")
        assertFalse(input.contains("pierwsza unikatowa wypowiedz"))
        assertTrue(input.contains("druga unikatowa wypowiedz"))
        assertTrue(input.contains("czwarta unikatowa wypowiedz"))
        assertTrue(input.endsWith("aktualna pelna wypowiedz"))
    }

    @Test
    fun classifierReceivesTurnContextOnlyAfterCompletedResponse() {
        val inputs = mutableListOf<String>()
        val classifier = object : TextCallAgentBackend {
            override fun generate(userText: String, listener: TextCallAgentBackend.Listener) {
                inputs += userText
                listener.onComplete(
                    if (inputs.size == 1)
                        """{"action":"STATE_TASK_SUBJECT","confidence":0.99}"""
                    else
                        """{"action":"ASK_NEXT_SERVICE_STEP","confidence":0.99}"""
                )
            }
            override fun cancel() = Unit
        }
        val responses = mutableListOf<DialogueActionId>()
        val memory = DialogueActionTurnMemory()
        val backend = DialogueActionBackend(
            classifierBackend = classifier,
            policy = DialogueActionPolicy(setOf(
                DialogueActionId.STATE_TASK_SUBJECT,
                DialogueActionId.ASK_NEXT_SERVICE_STEP,
            )),
            executor = DialogueActionExecutor { _, decision, listener ->
                responses += decision.actionId
                listener.onComplete("Approved procedural response")
            },
            turnMemory = memory,
        )
        val listener = object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) = Unit
            override fun onError(reason: String) = throw AssertionError(reason)
        }
        backend.generate("W jakiej sprawie?", listener)
        backend.generate("Jak przeprowadzic aktywacje?", listener)

        assertEquals(
            listOf(DialogueActionId.STATE_TASK_SUBJECT, DialogueActionId.ASK_NEXT_SERVICE_STEP),
            responses,
        )
        assertTrue(inputs[1].contains("STATE_TASK_SUBJECT"))
        assertTrue(inputs[1].contains("W jakiej sprawie?"))
        assertTrue(inputs[1].endsWith("Jak przeprowadzic aktywacje?"))
    }
}
