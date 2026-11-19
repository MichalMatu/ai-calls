package pl.michalmatu.aicallbridge.session

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File

/**
 * Owner-triggered local ADB bootstrap for the lab only.
 *
 * Runs only on a background worker after a button tap. Pairing is a separate
 * explicit action. Never enables debugging, requests root, starts a call, or
 * runs shell commands supplied by callers. All transport endpoints are loopback.
 */
internal class LocalAdbGsmController(private val context: Context) {
    enum class Outcome { PAIRED, READY, ALREADY_READY, INVALID_INPUT, WRONG_DEVICE,
        CALL_BUSY, BOOTSTRAP_FAILED, ADB_UNAVAILABLE, NOT_DISCOVERED }

    private fun allowed(): Boolean =
        context.packageName == GsmShellMediaProtocol.S8_PACKAGE

    private fun identity() {
        val privateKey = File(context.noBackupFilesDir, "local_adb_host.pem")
        KadbCert.configure(OkioFilePrivateKeyStore(privateKey.absolutePath.toPath()))
        KadbCert.ensureReady()
    }

    // Only the local Android NSD port is used; no remote IP or arbitrary host.
    private fun localPort(serviceType: String): Int? =
        LocalAdbPortDiscovery(context).discover(serviceType)

    fun pair(code: String): Outcome {
        if (!allowed()) return Outcome.WRONG_DEVICE
        if (!LocalAdbGsmPolicy.pairingCode(code)) return Outcome.INVALID_INPUT
        return try {
            identity()
            val port = localPort(LocalAdbPortDiscovery.PAIRING) ?: return Outcome.NOT_DISCOVERED
            runBlocking { Kadb.pair(LocalAdbGsmPolicy.LOOPBACK, port, code) }
            Outcome.PAIRED
        } catch (_: Exception) {
            // Never log pairing codes, keys, host descriptors or ADB output.
            Outcome.ADB_UNAVAILABLE
        }
    }

    fun bootstrap(): Outcome {
        if (!allowed()) return Outcome.WRONG_DEVICE
        return try {
            identity()
            val port = localPort(LocalAdbPortDiscovery.CONNECT) ?: return Outcome.NOT_DISCOVERED
            Kadb.create(LocalAdbGsmPolicy.LOOPBACK, port,
                connectTimeout = 10_000, socketTimeout = 12_000).use { adb ->
                fun checked(command: String): String {
                    val reply = adb.shell(command)
                    if (reply.exitCode != 0) throw IllegalStateException("local_adb_probe_failed")
                    return reply.output
                }
                if (!LocalAdbGsmPolicy.deviceApproved(
                        checked("getprop ro.product.model"),
                        checked("getprop ro.build.version.release"),
                        checked("getprop sys.boot_completed"),
                        checked("id -u"))) return Outcome.WRONG_DEVICE
                if (!LocalAdbGsmPolicy.callIdle(
                        checked("dumpsys telephony.registry"),
                        checked("dumpsys audio"))) return Outcome.CALL_BUSY
                val installed = checked("pm path pl.michalmatu.aicallbridge.s8lab")
                if (!installed.lineSequence().any { it.startsWith("package:") }) return Outcome.WRONG_DEVICE
                if (LocalAdbGsmPolicy.hostRegistered(checked(LocalAdbGsmPolicy.STATUS)))
                    return Outcome.ALREADY_READY
                checked(LocalAdbGsmPolicy.START)
                repeat(15) {
                    if (LocalAdbGsmPolicy.hostRegistered(checked(LocalAdbGsmPolicy.STATUS)))
                        return Outcome.READY
                    Thread.sleep(300L)
                }
                Outcome.BOOTSTRAP_FAILED
            }
        } catch (_: Exception) {
            Outcome.ADB_UNAVAILABLE
        }
    }
}
