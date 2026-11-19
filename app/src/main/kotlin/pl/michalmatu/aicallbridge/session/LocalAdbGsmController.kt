package pl.michalmatu.aicallbridge.session

import android.content.Context
import android.provider.Settings
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

    /** Read-only diagnostics: saved host identity is NOT proof of pairing. */
    fun inspect(): LocalAdbGsmStatus.Snapshot {
        val gsmReady = GsmMediaTransportReadiness.isAvailable(context)
        if (!allowed()) return LocalAdbGsmStatus.Snapshot(gsmReady,
            LocalAdbGsmStatus.AdbState.NOT_CONFIGURED)

        val key = File(context.noBackupFilesDir, "local_adb_host.pem")
        val keyStored = key.isFile && key.length() > 0L
        val wifi = runCatching {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", -1)
        }.getOrDefault(-1)
        if (!keyStored || wifi == 0) return LocalAdbGsmStatus.Snapshot(
            gsmReady, LocalAdbGsmStatus.resolve(keyStored, wifi, authenticated = false)
        )

        val authenticated = runCatching {
            identity()
            val port = localPort(LocalAdbPortDiscovery.CONNECT)
                ?: return@runCatching false
            Kadb.create(LocalAdbGsmPolicy.LOOPBACK, port,
                connectTimeout = 6_000, socketTimeout = 7_000).use { adb ->
                fun read(command: String): String {
                    val response = adb.shell(command)
                    if (response.exitCode != 0) error("read_only_adb_probe_failed")
                    return response.output
                }
                LocalAdbGsmPolicy.deviceApproved(
                    read("getprop ro.product.model"),
                    read("getprop ro.build.version.release"),
                    read("getprop sys.boot_completed"),
                    read("id -u"))
            }
        }.getOrDefault(false)
        return LocalAdbGsmStatus.Snapshot(gsmReady,
            LocalAdbGsmStatus.resolve(keyStored, wifi, authenticated))
    }

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
