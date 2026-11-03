package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.MediaPlayer
import android.telecom.TelecomManager
import java.io.File
import org.json.JSONObject
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceConsent
import pl.michalmatu.aicallbridge.developerrelay.CallSessionEvidenceRecorder
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant

class CampaignToolActivity : Activity() {
    private lateinit var authorizationStore: CampaignAuthorizationStore
    private lateinit var runtimeStatusStore: CampaignRuntimeStatusStore
    private lateinit var statusView: TextView
    private var pendingAction: PendingAction? = null
    private var continueStartAfterRole = false
    private var playback: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authorizationStore = CampaignAuthorizationStore(this)
        runtimeStatusStore = CampaignRuntimeStatusStore(this)

        statusView = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
        }

        val authorizeButton = Button(this).apply {
            text = "Authorize CLIR enable campaign"
            setOnClickListener {
                ensurePermissionsThen(PendingAction.AUTHORIZE)
            }
        }
        val callRoleButton = Button(this).apply {
            text = "Włącz autonomiczne połączenia (rola dialera Android)"
            setOnClickListener { requestCallRole() }
        }
        val startButton = Button(this).apply {
            text = "Start authorized CLIR enable"
            setOnClickListener {
                // Scope first: do not request OS dialer role for an expired or absent grant.
                ensurePermissionsThen(PendingAction.START)
            }
        }
        val supervisedAudioButton = Button(this).apply {
            text = "Uruchom AI w trwającej rozmowie"
            // The tap is the explicit request. Android permissions and actual
            // live-call/media gates are checked by the service; no redundant dialog.
            setOnClickListener {
                ensurePermissionsThen(PendingAction.SUPERVISED_AUDIO)
            }
        }
        val stopSupervisedAudioButton = Button(this).apply {
            text = "STOP AI"
            setOnClickListener {
                startService(
                    Intent(this@CampaignToolActivity, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI),
                )
                statusView.text = "Stopping AI; only an app-owned call may be disconnected"
            }
        }
        val evidenceButton = Button(this).apply {
            text = "Zapisz następną rozmowę: transkrypt + obie ścieżki WAV"
            setOnClickListener {
                AlertDialog.Builder(this@CampaignToolActivity)
                    .setTitle("Prywatna diagnostyka jednej rozmowy")
                    .setMessage(
                        "Tylko następna rozmowa w ciągu 10 minut. Zapis w pamięci prywatnej " +
                            "aplikacji: rozpoznana mowa rozmówcy, zatwierdzony tekst agenta, " +
                            "osobne WAV odebrane (RX) i wysłane (TX). " +
                            "Pliki mogą zawierać dane osobowe i poufne wypowiedzi. " +
                            "Poinformuj rozmówcę o nagrywaniu, jeżeli jest to wymagane. " +
                            "Brak automatycznego wysyłania do ChatGPT, GitHub ani chmury. " +
                            "Zachowujemy najwyżej trzy ostatnie sesje. Możesz anulować zgodę."
                    )
                    .setPositiveButton("Uzbrój jedną sesję") { _, _ ->
                        runCatching {
                            CallSessionEvidenceConsent.armNextCall(this@CampaignToolActivity)
                            statusView.text = "Prywatne RX/TX + transkrypt uzbrojone na jedną sesję (10 min)."
                        }.onFailure {
                            statusView.text = "Nie udało się uzbroić zapisu diagnostycznego."
                        }
                    }
                    .setNegativeButton("Anuluj", null)
                    .show()
            }
        }
        val cancelEvidenceButton = Button(this).apply {
            text = "Wyłącz zapis diagnostyczny następnej rozmowy"
            setOnClickListener {
                CallSessionEvidenceConsent.cancel(this@CampaignToolActivity)
                statusView.text = "Następna rozmowa nie będzie nagrywana."
            }
        }
        val playbackButton = Button(this).apply {
            text = "Odsłuchaj ostatnią odpowiedź AI (tylko poza rozmową)"
            setOnClickListener { playLastRecordedAudio("tx") }
        }
        val playbackRxButton = Button(this).apply {
            text = "Odsłuchaj ostatnią wypowiedź rozmówcy (tylko poza rozmową)"
            setOnClickListener { playLastRecordedAudio("rx") }
        }
        val transcriptButton = Button(this).apply {
            text = "Pokaż transkrypt obu stron (odśwież ręcznie)"
            setOnClickListener { showLatestTranscript() }
        }
        val revokeButton = Button(this).apply {
            text = "Revoke campaign grant"
            setOnClickListener {
                val revoked = authorizationStore.revoke(System.currentTimeMillis())
                statusView.text = if (revoked == null) {
                    "No campaign grant to revoke"
                } else {
                    "Campaign grant revoked"
                }
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(TextView(this@CampaignToolActivity).apply {
                text = "App-owned campaign tools"
                textSize = 22f
            })
            addView(TextView(this@CampaignToolActivity).apply {
                text =
                    "Authority, dial and call execution happen on-device. " +
                    "ChatGPT/ADB are not part of the execution path."
                textSize = 14f
            })
            addView(authorizeButton)
            addView(callRoleButton)
            addView(startButton)
            addView(supervisedAudioButton)
            addView(stopSupervisedAudioButton)
            addView(evidenceButton)
            addView(cancelEvidenceButton)
            addView(playbackButton)
            addView(playbackRxButton)
            addView(transcriptButton)
            addView(revokeButton)
            addView(
                statusView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 24 },
            )
        }

        setContentView(
            ScrollView(this).apply {
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
        refreshGrantStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshGrantStatus()
    }

    override fun onDestroy() {
        playback?.release()
        playback = null
        super.onDestroy()
    }

    private fun latestEvidenceDirectory(): File? =
        File(filesDir, CallSessionEvidenceRecorder.ROOT)
            .listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }

    private fun showLatestTranscript() {
        val timeline = latestEvidenceDirectory()?.resolve("timeline.jsonl")
        if (timeline?.isFile != true) {
            statusView.text = "Nie ma jeszcze prywatnego transkryptu. Uzbrój zapis przed rozmową."
            return
        }
        val lines = runCatching {
            timeline.useLines { input ->
                input.mapNotNull { line ->
                    runCatching {
                        val event = JSONObject(line)
                        val type = event.optString("type")
                        if (type != "COUNTERPART_STT" && type != "AGENT_APPROVED_TEXT") {
                            null
                        } else {
                            val name = if (type == "COUNTERPART_STT") "Rozmówca" else "AI"
                            val seconds = event.optLong("elapsed_ms") / 1_000L
                            "[$seconds s, tura ${event.optInt("turn")}] $name: " +
                                event.optString("text")
                        }
                    }.getOrNull()
                }.toList()
            }
        }.getOrElse {
            statusView.text = "Nie udało się odczytać prywatnego transkryptu."
            return
        }
        val transcript = lines.joinToString("\n\n")
            .ifEmpty { "Brak ukończonych wypowiedzi STT/AI. Odśwież po kolejnej turze." }
        val view = TextView(this).apply {
            text = transcript
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(32, 20, 32, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Prywatny transkrypt RX / AI")
            .setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton("Odśwież") { _, _ -> showLatestTranscript() }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun playLastRecordedAudio(direction: String) {
        require(direction == "rx" || direction == "tx")
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            statusView.text = "Odsłuch wymaga uprawnienia do sprawdzenia stanu telefonu."
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true)) {
            statusView.text = "Odsłuch tylko poza rozmową; bez ryzyka sprzężenia audio."
            return
        }
        val audio = latestEvidenceDirectory()?.listFiles()?.filter {
            it.name.matches(Regex("turn-[0-9]{2}-$direction\\.wav"))
        }?.maxByOrNull { it.name }
        if (audio == null) {
            statusView.text = "Brak zapisanego audio AI. Najpierw uzbrój prywatną diagnostykę."
            return
        }
        runCatching {
            playback?.release()
            val newPlayer = MediaPlayer()
            playback = newPlayer
            newPlayer.setDataSource(audio.absolutePath)
            newPlayer.setOnCompletionListener {
                it.release()
                if (playback === it) playback = null
            }
            newPlayer.prepare()
            newPlayer.start()
            statusView.text = "Odtwarzanie prywatnego pliku " +
                (if (direction == "rx") "rozmówcy" else "AI") + ": " + audio.name
        }.onFailure {
            playback?.release()
            playback = null
            statusView.text = "Nie udało się odtworzyć ostatniego pliku WAV."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMPAIGN_PERMISSIONS) return

        val allGranted = permissions.indices.all { index ->
            grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        }
        val action = pendingAction
        pendingAction = null
        if (!allGranted) {
            statusView.text = "Campaign permissions denied"
            return
        }
        when (action) {
            PendingAction.AUTHORIZE -> showAuthorizationConfirmation()
            PendingAction.START -> startAuthorizedCampaign()
            PendingAction.SUPERVISED_AUDIO -> startSupervisedAudio()
            null -> Unit
        }
    }

    private fun ensurePermissionsThen(action: PendingAction) {
        // Grant creation only needs SIM identity. Speech/dial permissions belong to execution.
        val required = when (action) {
            PendingAction.AUTHORIZE -> listOf(Manifest.permission.READ_PHONE_STATE)
            PendingAction.START -> REQUIRED_PERMISSIONS
            PendingAction.SUPERVISED_AUDIO -> listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
            )
        }
        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            when (action) {
                PendingAction.AUTHORIZE -> showAuthorizationConfirmation()
                PendingAction.START -> startAuthorizedCampaign()
                PendingAction.SUPERVISED_AUDIO -> startSupervisedAudio()
            }
            return
        }
        pendingAction = action
        requestPermissions(missing.toTypedArray(), REQUEST_CAMPAIGN_PERMISSIONS)
    }

    private fun requestCallRole(continueWithStart: Boolean = false) {
        continueStartAfterRole = continueWithStart
        // Android 13 notification permission is optional for placing a call.
        // Denial may hide our optional notification, never block the platform dialer role.
        requestPlatformCallRole()
    }

    private fun requestPlatformCallRole() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            statusView.text = "System nie udostępnia roli domyślnego dialera"
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            statusView.text = "AI Calls ma już rolę dialera"
            if (continueStartAfterRole) {
                continueStartAfterRole = false
                ensurePermissionsThen(PendingAction.START)
            }
            return
        }
        // Android alone grants/revokes this role. Never change Google Phone silently.
        statusView.text = "Android poprosi o świadomy wybór: AI Calls zastąpi Google Phone jako domyślny dialer."
        startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_DIALER_ROLE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DIALER_ROLE) {
            val roleActive = AndroidTelecomOwnedCallController.canControl(this)
            statusView.text = if (roleActive) {
                "Kontroler połączeń Android aktywny — można wykonać autoryzowane zadanie"
            } else "Rola dialera nie została przyznana; Google Phone pozostaje aktywny"
            val shouldStart = continueStartAfterRole
            continueStartAfterRole = false
            if (roleActive && shouldStart) ensurePermissionsThen(PendingAction.START)
        }
    }

    private fun showAuthorizationConfirmation(continueWithStart: Boolean = false) {
        val accountScope = CampaignAccountScope.currentVoiceSubscription(this)
        if (accountScope == null) {
            statusView.text = "Could not bind campaign to the current voice subscription"
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Authorize bounded CLIR campaign")
            .setMessage(
                "This local grant authorizes only:\n\n" +
                    "Target: ${ClirCampaignSpec.TARGET}\n" +
                    "Task: enable CLIR\n" +
                    "Account: current default voice subscription\n" +
                    "Disclosure: PHONE only when policy allows\n" +
                    "Expiry: 24 hours\n\n" +
                    "It does not authorize other services, targets, purchases, contracts, " +
                    "premium/emergency calls or CLIR disable.",
            )
            .setPositiveButton("Authorize locally") { _, _ ->
                val grant = ClirCampaignSpec.newEnableGrant(
                    accountScope = accountScope,
                    nowEpochMs = System.currentTimeMillis(),
                )
                authorizationStore.save(grant)
                refreshGrantStatus()
                if (continueWithStart) ensurePermissionsThen(PendingAction.START)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSupervisedAudio() {
        statusView.text = "Attaching read-only audio test to existing user-controlled call"
        startForegroundService(
            Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO),
        )
    }

    private fun startAuthorizedCampaign() {
        val accountScope = CampaignAccountScope.currentVoiceSubscription(this)
        if (accountScope == null) {
            statusView.text = "Could not bind execution to the current voice subscription"
            return
        }

        val request = ClirCampaignSpec.enableRequest(accountScope)
        val decision = CampaignAuthorizationPolicy.authorize(
            grant = authorizationStore.load(),
            request = request,
            nowEpochMs = System.currentTimeMillis(),
        )
        if (decision is CampaignAuthorizationDecision.Denied) {
            statusView.text = "Autoryzacja wymaga odnowienia: ${decision.reason.name.lowercase()}"
            // The owner must explicitly accept the exact target/effect/SIM once.
            // A revoked/expired grant is never silently revived by START or ADB.
            showAuthorizationConfirmation(continueWithStart = true)
            return
        }
        if (!AndroidTelecomOwnedCallController.canControl(this)) {
            // Android requires an explicit OS choice before exposing an exact Call handle.
            requestCallRole(continueWithStart = true)
            return
        }

        runtimeStatusStore.save("Starting app-owned campaign…")
        statusView.text = runtimeStatusStore.load()
        startForegroundService(
            Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_START_CLIR_ENABLE),
        )
    }

    private fun refreshGrantStatus() {
        val grant = authorizationStore.load()
        val grantStatus = when {
            grant == null -> "No local campaign grant"
            grant.revokedAtEpochMs != null -> "Campaign grant revoked"
            else ->
                "Campaign grant present\n" +
                    "Expires: ${Instant.ofEpochMilli(grant.expiresAtEpochMs)}"
        }
        val evidenceStatus = if (CallSessionEvidenceConsent.isArmed(this)) {
            "Diagnostyka następnej rozmowy: uzbrojona (jedna sesja, maks. 10 min)."
        } else null
        val runtimeStatus = runtimeStatusStore.load()
        statusView.text = listOfNotNull(evidenceStatus, runtimeStatus, grantStatus).joinToString("\n\n")
    }

    private enum class PendingAction {
        AUTHORIZE,
        START,
        SUPERVISED_AUDIO,
    }

    companion object {
        private const val REQUEST_CAMPAIGN_PERMISSIONS = 2001
        private const val REQUEST_DIALER_ROLE = 2002
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
    }
}
