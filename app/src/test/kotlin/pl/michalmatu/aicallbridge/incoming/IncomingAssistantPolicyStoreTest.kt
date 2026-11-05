package pl.michalmatu.aicallbridge.incoming

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode

class IncomingAssistantPolicyStoreTest {
    private class Preferences {
        val values=mutableMapOf<String,Any>()
        var commits=0
        var commitSucceeds=true
        private val editor: SharedPreferences.Editor get() {
            val changes=mutableMapOf<String,Any?>()
            return Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SharedPreferences.Editor::class.java)) { proxy,method,args ->
                when(method.name) {
                    "putString","putBoolean" -> {changes[args!![0] as String]=args[1];proxy}
                    "commit" -> {commits++;if(commitSucceeds) changes.forEach { (key,value) -> if(value==null) values.remove(key) else values[key]=value };commitSucceeds}
                    else -> error("unexpected_editor_method")
                }
            } as SharedPreferences.Editor
        }
        val port=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SharedPreferences::class.java)) { _,method,args ->
            val key=args?.firstOrNull() as? String
            when(method.name) {
                "contains" -> values.containsKey(key)
                "getString" -> if(values.containsKey(key)) values[key] as String? else args!![1]
                "getBoolean" -> if(values.containsKey(key)) values[key] as Boolean else args!![1]
                "edit" -> editor
                else -> error("unexpected_preferences_method")
            }
        } as SharedPreferences
    }
    @Test fun stopCanPersistManualEvenWhenOldPolicyCannotBeDecoded() {
        val fixture=Preferences()
        fixture.values.putAll(mapOf("id" to "old","mode" to "AI","account" to 42,"record" to true))
        val store=IncomingAssistantPolicyStore(fixture.port)
        assertNotNull(runCatching {store.load()}.exceptionOrNull())
        store.disable()
        assertEquals(AnsweringMode.MANUAL,store.load().mode)
        assertEquals(1,fixture.commits)
    }
    @Test fun repeatedStopDoesNotRewriteAnAlreadyDisabledPolicy() {
        val fixture=Preferences();val store=IncomingAssistantPolicyStore(fixture.port)
        store.save(AnsweringMode.AI,"sim",true)
        store.disable();val stopped=store.load()
        store.disable()
        assertEquals(stopped,store.load());assertEquals(2,fixture.commits)
    }
    @Test fun failedPersistenceIsObservableAndNeverReportedAsSaved() {
        val fixture=Preferences();val store=IncomingAssistantPolicyStore(fixture.port)
        store.save(AnsweringMode.AI,"sim",true)
        fixture.commitSucceeds=false
        assertTrue(runCatching {store.disable()}.exceptionOrNull() is IOException)
        assertEquals(AnsweringMode.AI,store.load().mode)
    }
}
