package pl.michalmatu.aicallbridge.localspeech

import android.content.Intent
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opt-in, one-shot request through Android's own offline recognition service.
 * No app settings, network endpoints, calls or speech content. Can require user UI approval.
 */
@RunWith(AndroidJUnit4::class)
class AndroidEnglishModelDownloadRequestTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun report(key: String, value: Any) {
        instrumentation.sendStatus(2, Bundle().apply {
            putString("stream", "\n" + key + "=" + value.toString() + "\n")
        })
    }

    private fun support(recognizer: SpeechRecognizer): RecognitionSupport? {
        val complete = CountDownLatch(1)
        val outcome = AtomicReference<RecognitionSupport?>()
        val error = AtomicInteger(-1)
        instrumentation.runOnMainSync {
            recognizer.checkRecognitionSupport(intent(), context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(result: RecognitionSupport) {
                        outcome.set(result); complete.countDown()
                    }
                    override fun onError(value: Int) {
                        error.set(value); complete.countDown()
                    }
                })
        }
        assertTrue("en_model_support_timeout", complete.await(30, TimeUnit.SECONDS))
        report("EN_MODEL_SUPPORT_ERROR", error.get())
        return outcome.get()
    }

    @Test fun requestMissingEnglishUsOfflineRecognitionOnce() {
        assumeTrue("explicit_download_request_required",
            InstrumentationRegistry.getArguments().getString("aicalls_download_en_us") == "approved")
        val phone = context.getSystemService(TelecomManager::class.java)
        assertFalse("model_download_requires_idle_phone", phone.isInCall)
        assertTrue("on_device_speech_service_unavailable",
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        var selected: SpeechRecognizer? = null
        instrumentation.runOnMainSync {
            selected = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        val recognizer = checkNotNull(selected)
        try {
            val before = checkNotNull(support(recognizer)) { "en_support_unknown" }
            val installed = before.installedOnDeviceLanguages.any {
                it.equals("en-US", ignoreCase = true)
            }
            val supported = before.supportedOnDeviceLanguages.any {
                it.equals("en-US", ignoreCase = true)
            }
            report("EN_MODEL_BEFORE_INSTALLED", installed)
            report("EN_MODEL_SUPPORTED", supported)
            assertTrue("en_us_not_supported_by_recognizer", installed || supported)
            if (!installed) {
                val done = CountDownLatch(1)
                val result = AtomicReference("NO_CALLBACK")
                val progress = AtomicInteger(-1)
                instrumentation.runOnMainSync {
                    recognizer.triggerModelDownload(intent(), context.mainExecutor,
                        object : ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {
                                progress.set(completedPercent)
                            }
                            override fun onSuccess() {
                                result.set("SUCCESS"); done.countDown()
                            }
                            override fun onScheduled() {
                                result.set("SCHEDULED"); done.countDown()
                            }
                            override fun onError(error: Int) {
                                result.set("ERROR_" + error); done.countDown()
                            }
                        })
                }
                done.await(90, TimeUnit.SECONDS)
                report("EN_MODEL_DOWNLOAD_RESULT", result.get())
                report("EN_MODEL_DOWNLOAD_PROGRESS", progress.get())
            } else report("EN_MODEL_DOWNLOAD_RESULT", "ALREADY_INSTALLED")
            val after = support(recognizer)
            report("EN_MODEL_AFTER_INSTALLED", after?.installedOnDeviceLanguages?.any {
                it.equals("en-US", ignoreCase = true)
            } == true)
        } finally {
            instrumentation.runOnMainSync { recognizer.destroy() }
        }
        assertFalse("en_model_download_changed_phone_state", phone.isInCall)
    }
}
