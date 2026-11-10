package pl.michalmatu.aicallbridge.incoming

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.conversationtools.ConversationToolId
import pl.michalmatu.aicallbridge.tools.*

@RunWith(AndroidJUnit4::class)
class AndroidConversationToolsSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun isolatedToolConfigurationPersistsRevocationWithoutChangingReceivingPolicy() {
        val policy = IncomingAssistantPolicyStore(context).load()
        val profile = AudioEngineProfileStore(context).load()
        val isolatedName = "tool-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = context.getSharedPreferences(isolatedName, mode)
        }
        try {
            val store = ConversationToolSettingsStore(isolated)
            assertEquals(setOf(ConversationToolId.END_CALL, ConversationToolId.SAVE_NOTE), store.enabled())
            store.save(emptySet())
            assertTrue(ConversationToolSettingsStore(isolated).enabled().isEmpty())
            store.save(setOf(ConversationToolId.END_CALL, ConversationToolId.SAVE_NOTE))
            assertEquals(setOf(ConversationToolId.END_CALL, ConversationToolId.SAVE_NOTE), ConversationToolSettingsStore(isolated).enabled())
            assertEquals(policy, IncomingAssistantPolicyStore(context).load())
            assertEquals(profile, AudioEngineProfileStore(context).load())
        } finally { context.deleteSharedPreferences(isolatedName) }
    }

    @Test fun openingToolScreenDoesNotAcceptTaskOrAlterConfiguration() {
        val policy = IncomingAssistantPolicyStore(context).load()
        val enabled = ConversationToolSettingsStore(context).enabled()
        val profile = AudioEngineProfileStore(context).load()
        val activity = instrumentation.startActivitySync(Intent(context, ConversationToolsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            assertEquals(enabled, ConversationToolSettingsStore(context).enabled())
            assertEquals(policy, IncomingAssistantPolicyStore(context).load())
            assertEquals(profile, AudioEngineProfileStore(context).load())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
