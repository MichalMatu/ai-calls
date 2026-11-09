package pl.michalmatu.aicallbridge.textagent

import android.app.ActivityManager
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/** G2 off-call CPU canary. No profile edit, dial, tool or contact data. */
@RunWith(AndroidJUnit4::class)
class AndroidQwen35CpuNativeSmokeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = DialogueModelId.QWEN_3_5_2B

    private data class Turn(val chars: Int, val ms: Long)

    private fun generate(backend: TextCallAgentBackend, prompt: String): Turn {
        val done = CountDownLatch(1)
        val answer = AtomicReference<String>()
        val error = AtomicReference<String>()
        val start = System.nanoTime()
        backend.generate(prompt, object : TextCallAgentBackend.Listener {
            override fun onComplete(text: String) { answer.set(text); done.countDown() }
            override fun onError(reason: String) { error.set(reason.substringBefore(':').take(80)); done.countDown() }
        })
        assertTrue("qwen35_timeout", done.await(240, TimeUnit.SECONDS))
        assertNull("qwen35_error", error.get())
        val value = checkNotNull(answer.get())
        assertTrue("qwen35_empty", value.isNotBlank())
        return Turn(value.length, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
    }

    private fun memory(): String {
        val process = Debug.MemoryInfo()
        Debug.getMemoryInfo(process)
        val system = ActivityManager.MemoryInfo()
        app.getSystemService(ActivityManager::class.java).getMemoryInfo(system)
        return "pss_mib=" + (process.totalPss / 1024) +
            ";ram_avail_mib=" + (system.availMem / 1048576L) +
            ";low_memory=" + system.lowMemory
    }

    @Test fun twoTurnsAndIndependentSessionOnCpu() {
        val profile = checkNotNull(AudioEngineProfileStore(app).load())
        assertEquals(Gemma4ModelReadinessState.READY, AndroidGemma4ModelReadiness.check(app, id).state)
        assertTrue(id.nativeExecutionPolicy().cpuOnly)
        assertTrue(id.nativeExecutionPolicy().newEngineAfterContextReset)
        val first: Turn
        val warm: Turn
        Gemma4LiteRtTextBackendFactory.createDirectDialogue(
            app, "Odpowiadaj krotko po polsku. Nie wykonuj narzedzi.", id
        ).use { backend ->
            first = generate(backend, "Jak nazywa sie stolica Polski? Jedno krotkie zdanie.")
            warm = generate(backend, "Potwierdz krotko, ze kontynuujesz dialog.")
        }
        val separate: Turn
        Gemma4LiteRtTextBackendFactory.createDirectDialogue(
            app, "Nowy niezalezny fikcyjny dialog. Odpowiadaj po polsku.", id
        ).use { backend ->
            separate = generate(backend, "Powiedz jedno krotkie powitanie.")
        }
        assertEquals("qwen35_profile_mutated", profile, AudioEngineProfileStore(app).load())
        Log.i("AiCallQwen35Smoke", "model=QWEN_3_5_2B;policy=CPU_ONLY;no_call=true" +
            ";cold_ms=" + first.ms + ";warm_ms=" + warm.ms +
            ";fresh_ms=" + separate.ms + ";cold_chars=" + first.chars +
            ";warm_chars=" + warm.chars + ";fresh_chars=" + separate.chars +
            ";" + memory())
        println("G2_QWEN35_CPU_SMOKE=3_NONCALL_TURNS;ENGINES=2;SEMANTIC_REVIEW_OPEN")
    }
}
