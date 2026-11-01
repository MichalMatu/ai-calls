package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
        val startButton = Button(this).apply {
            text = "Start authorized CLIR enable"
            setOnClickListener {
                if (CampaignCallControllerRegistry.available() == null) {
                    statusView.text = "No verified call controller installed; dialing is unavailable"
                } else {
                    ensurePermissionsThen(PendingAction.START)
                }
            }
        }
        val supervisedAudioButton = Button(this).apply {
            text = "Attach read-only audio pilot to my existing call"
            setOnClickListener {
                AlertDialog.Builder(this@CampaignToolActivity)
                    .setTitle("Supervised audio test")
                    .setMessage(
                        "First start a normal call yourself in Google Phone. " +
                            "This two-turn test checks speech recognition and audio. " +
                            "It cannot dial, hang up, change CLIR or disclose your phone number. " +
                            "You remain responsible for ending the phone call.",
                    )
                    .setPositiveButton("Attach") { _, _ ->
                        ensurePermissionsThen(PendingAction.SUPERVISED_AUDIO)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        val stopSupervisedAudioButton = Button(this).apply {
            text = "Stop audio pilot (do not hang up)"
            setOnClickListener {
                startService(
                    Intent(this@CampaignToolActivity, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_STOP_SUPERVISED_AUDIO),
                )
                statusView.text = "Stopping app audio test; manage the phone call in Google Phone"
            }
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
            addView(startButton)
            addView(supervisedAudioButton)
            addView(stopSupervisedAudioButton)
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

    private fun showAuthorizationConfirmation() {
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
                    "Attempts: up to ${ClirCampaignSpec.MAX_ATTEMPTS}\n" +
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
            statusView.text = "Campaign authorization denied: ${decision.reason.name.lowercase()}"
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
                    "Attempts: ${grant.attemptsUsed}/${grant.maxAttempts}\n" +
                    "Expires: ${Instant.ofEpochMilli(grant.expiresAtEpochMs)}"
        }
        val runtimeStatus = runtimeStatusStore.load()
        statusView.text = listOfNotNull(runtimeStatus, grantStatus).joinToString("\n\n")
    }

    private enum class PendingAction {
        AUTHORIZE,
        START,
        SUPERVISED_AUDIO,
    }

    companion object {
        private const val REQUEST_CAMPAIGN_PERMISSIONS = 2001
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
    }
}
