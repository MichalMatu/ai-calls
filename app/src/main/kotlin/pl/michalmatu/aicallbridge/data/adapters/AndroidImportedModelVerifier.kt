package pl.michalmatu.aicallbridge.data.adapters

import android.content.Context
import java.io.File
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile
import pl.michalmatu.aicallbridge.audioengines.ModelCompatibilityProbe

/** Owns the native model probe; UI supplies explicit safety and selection gates. */
internal class AndroidImportedModelVerifier(context: Context) : AutoCloseable {
    private val nativeProbe = ModelCompatibilityProbe(context)

    fun check(file: File, profile: AudioEngineProfile, done: (String?) -> Unit) {
        nativeProbe.check(file, profile, audio = false, done = done)
    }

    override fun close() { nativeProbe.close() }
}
