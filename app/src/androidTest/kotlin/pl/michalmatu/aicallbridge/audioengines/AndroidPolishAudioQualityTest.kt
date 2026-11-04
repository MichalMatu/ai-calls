package pl.michalmatu.aicallbridge.audioengines

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic speech only: no real recordings, account data or cellular session. */
@RunWith(AndroidJUnit4::class)
class AndroidPolishAudioQualityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val phrases = listOf(
        "Dzień dobry. Proszę o pomoc konsultanta.",
        "Chcę sprawdzić ustawienia prezentacji numeru telefonu.",
        "Proszę powtórzyć ostatnią informację.",
    )

    @Test fun gemmaAudioRecognizesPolishSpeech() = corpus(
        "GEMMA", { GemmaAudioDirectInputProvider(context) },
    )

    @Test fun parakeetRecognizesPolishSpeech() = corpus(
        "PARAKEET", { ParakeetSpeechInputProvider(context) },
    )

    @Test fun piperSynthesizesPolishSpeechForParakeet() {
        val ready = CountDownLatch(1)
        val holder = arrayOfNulls<TextToSpeech>(1)
        val init = TextToSpeech.OnInitListener { status ->
            context.mainExecutor.execute {
                val tts = holder[0]
                val voices = tts?.voices.orEmpty().filter {
                    it.locale.language == "pl" && !it.isNetworkConnectionRequired &&
                        "piperVoice" in it.features
                }.sortedBy { it.name }
                File(context.cacheDir, "synthetic-piper-voice-state.txt").writeText(
                    "init=$status\ndefault=${tts?.voice?.name}\n" +
                        "candidate=${voices.firstOrNull()?.name}\n",
                )
                ready.countDown()
            }
        }
        holder[0] = TextToSpeech(context, init, PiperServiceContract.PACKAGE)
        try {
            assertTrue("Piper voice inventory timed out", ready.await(30, TimeUnit.SECONDS))
        } finally { holder[0]?.shutdown() }
        corpus("PIPER", { ParakeetSpeechInputProvider(context) },
            { PiperAndroidTtsOutputProvider(context, "pl-PL", null) })
    }

    private fun corpus(
        engine: String,
        inputFactory: () -> SpeechInputProvider,
        outputFactory: () -> SpeechOutputProvider = {
            AndroidTtsOutputProvider(context, "pl-PL", null)
        },
    ) {
        phrases.forEachIndexed { index, reference ->
            val metrics = linkedMapOf<String, Long>()
            val synthStarted = System.nanoTime()
            val synthDone = CountDownLatch(1)
            val audio = AtomicReference<ByteArray?>(null)
            val error = AtomicReference<String?>(null)
            outputFactory().use { output ->
                output.synthesize(reference, object : SpeechOutputProvider.Listener {
                    override fun onPcm16Mono16k(pcm: ByteArray) {
                        audio.set(pcm)
                        synthDone.countDown()
                    }
                    override fun onError(reason: String) {
                        error.set(reason)
                        synthDone.countDown()
                    }
                })
                assertTrue("Synthetic Polish TTS timed out", synthDone.await(140, TimeUnit.SECONDS))
                assertNull("Synthetic Polish TTS error: ${error.get()}", error.get())
            }
            metrics["TTS_MS"] = elapsed(synthStarted)
            val pcm = checkNotNull(audio.get()) { "Synthetic TTS returned no PCM" }
            assertTrue("Synthetic TTS returned insufficient PCM", pcm.size >= 8_000)
            metrics["AUDIO_MS"] = pcm.size.toLong() * 1000L / 32_000L

            val ready = CountDownLatch(1)
            val done = CountDownLatch(1)
            val transcript = AtomicReference<String?>(null)
            val callbacks = AtomicInteger()
            inputFactory().use { input ->
                val initStarted = System.nanoTime()
                input.start(object : SpeechInputProvider.Listener {
                    override fun onReady() { ready.countDown() }
                    override fun onFinalTranscript(text: String) {
                        callbacks.incrementAndGet()
                        transcript.set(text)
                        done.countDown()
                    }
                    override fun onError(reason: String) {
                        error.set(reason)
                        ready.countDown()
                        done.countDown()
                    }
                })
                assertTrue("Native audio initialization timed out", ready.await(200, TimeUnit.SECONDS))
                metrics["INIT_MS"] = elapsed(initStarted)
                assertNull("Native audio initialization error: ${error.get()}", error.get())
                val inferStarted = System.nanoTime()
                for (offset in pcm.indices step 32_000) {
                    assertTrue("Native input rejected synthetic PCM",
                        input.writePcm(pcm, offset, minOf(32_000, pcm.size - offset)))
                }
                assertTrue("Native input rejected final silence", input.writePcm(ByteArray(64_000)))
                input.finishInput()
                assertTrue("Native Polish recognition timed out", done.await(200, TimeUnit.SECONDS))
                metrics["INFER_MS"] = elapsed(inferStarted)
                assertNull("Native Polish recognition error: ${error.get()}", error.get())
            }
            val expected = words(reference)
            // Only this test's synthetic speech: never real call material or logcat.
            File(context.cacheDir, "synthetic-audio-qa").apply { mkdirs() }
                .resolve("${engine.lowercase(Locale.ROOT)}-${index + 1}.txt")
                .writeText(transcript.get().orEmpty())
            val actual = words(transcript.get().orEmpty())
            val wordErrors = distance(expected, actual)
            metrics["WORD_ERRORS"] = wordErrors.toLong()
            metrics["REF_WORDS"] = expected.size.toLong()
            val expectedChars = expected.joinToString(" ").toList()
            metrics["CHAR_ERRORS"] = distance(expectedChars, actual.joinToString(" ").toList()).toLong()
            metrics["REF_CHARS"] = expectedChars.size.toLong()
            metrics["FINAL_CALLBACKS"] = callbacks.get().toLong()
            val stream = metrics.entries.joinToString("\n", prefix = "\n", postfix = "\n") {
                "AUDIO_QA_${engine}_CASE_${index + 1}_${it.key}=${it.value}"
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", stream)
            })
            assertTrue("Polish synthetic sample ${index + 1} returned no words", actual.isNotEmpty())
            assertTrue("Polish synthetic sample ${index + 1} WER exceeds 50 percent",
                wordErrors * 2 <= expected.size)
        }
    }

    private fun elapsed(started: Long) = (System.nanoTime() - started) / 1_000_000L
    private fun words(text: String) = Regex("[\\p{L}\\p{N}]+")
        .findAll(text.lowercase(Locale.forLanguageTag("pl-PL"))).map { it.value }.toList()

    private fun <T> distance(reference: List<T>, actual: List<T>): Int {
        var row = IntArray(actual.size + 1) { it }
        reference.forEachIndexed { i, expected ->
            val next = IntArray(actual.size + 1)
            next[0] = i + 1
            actual.forEachIndexed { j, observed ->
                next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1,
                    row[j] + if (expected == observed) 0 else 1)
            }
            row = next
        }
        return row.last()
    }
}
