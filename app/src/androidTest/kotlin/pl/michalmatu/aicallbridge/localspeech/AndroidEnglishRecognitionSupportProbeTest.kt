package pl.michalmatu.aicallbridge.localspeech

import android.content.Intent
import android.os.Bundle
import android.media.AudioFormat
import android.os.ParcelFileDescriptor
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only English language support evidence for base and PCM-pipe recognition requests.
 * Never starts listening, downloads models, captures audio or changes the phone profile.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEnglishRecognitionSupportProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }


    private fun report(key: String, value: Any) {
        instrumentation.sendStatus(2, Bundle().apply {
            putString("stream", "\n" + key + "=" + value.toString() + "\n")
        })
    }

    private fun status(tag: String, recognizer: SpeechRecognizer, request: Intent) {
        val done = CountDownLatch(1)
        val result = AtomicReference<RecognitionSupport?>()
        val error = AtomicReference<Int?>()
        instrumentation.runOnMainSync {
            recognizer.checkRecognitionSupport(request, context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        result.set(support)
                        done.countDown()
                    }
                    override fun onError(code: Int) {
                        error.set(code)
                        done.countDown()
                    }
                })
        }
        assertTrue("en_support_callback_timeout_" + tag, done.await(30, TimeUnit.SECONDS))
        val support = result.get()
        val installed = support?.installedOnDeviceLanguages.orEmpty()
        val supported = support?.supportedOnDeviceLanguages.orEmpty()
        val pending = support?.pendingOnDeviceLanguages.orEmpty()
        fun matches(list: List<String>) = list.any { it.equals("en-US", ignoreCase = true) }
        fun english(list: List<String>) = list.filter {
            Locale.forLanguageTag(it).language.equals("en", ignoreCase = true)
        }
        report("EN_SUPPORT_" + tag + "_ERROR", error.get() ?: "none")
        report("EN_SUPPORT_" + tag + "_INSTALLED_EXACT", matches(installed))
        report("EN_SUPPORT_" + tag + "_INSTALLED_ENGLISH_COUNT", english(installed).size)
        report("EN_SUPPORT_" + tag + "_SUPPORTED_EXACT", matches(supported))
        report("EN_SUPPORT_" + tag + "_PENDING_EXACT", matches(pending))
        report("EN_SUPPORT_" + tag + "_INSTALLED_TOTAL", installed.size)
        report("EN_SUPPORT_" + tag + "_AVAILABLE", true)
    }

    @Test fun englishSupportForActualPipeAndBaseIntentIsKnown() {
        assertFalse("en_support_phone_must_be_idle",
            context.getSystemService(TelecomManager::class.java).isInCall)
        assertTrue("en_support_service_missing",
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        var recognizer: SpeechRecognizer? = null
        instrumentation.runOnMainSync {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        val active = checkNotNull(recognizer)
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            status("BASE", active, request())
            val withPcm = request().apply {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE)
            }
            status("PIPE", active, withPcm)
        } finally {
            pipe.forEach { it.close() }
            instrumentation.runOnMainSync { active.destroy() }
        }
        assertFalse("en_support_phone_changed_state",
            context.getSystemService(TelecomManager::class.java).isInCall)
    }
}
