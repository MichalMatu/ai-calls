package pl.michalmatu.aicallbridge

import pl.michalmatu.aicallbridge.conversation.ConversationChatPanel
import pl.michalmatu.aicallbridge.ui.AppNavigation
import pl.michalmatu.aicallbridge.ui.AppTab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import pl.michalmatu.aicallbridge.campaign.CampaignToolActivity
import pl.michalmatu.aicallbridge.campaign.AppOwnedCampaignService
import pl.michalmatu.aicallbridge.developerrelay.SupervisedCallPilotFailure
import pl.michalmatu.aicallbridge.developerrelay.SupervisedCallReadinessDiagnostics
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity
import pl.michalmatu.aicallbridge.runtime.CallAudioMode
import pl.michalmatu.aicallbridge.runtime.CallRuntimePreferences
import pl.michalmatu.aicallbridge.runtime.TextLlmProvider

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var runtimePreferences: CallRuntimePreferences
    private lateinit var selectedAudioMode: CallAudioMode
    private lateinit var selectedTextLlmProvider: TextLlmProvider
    private lateinit var textLlmProviderSpinner: Spinner
    private var displayedModel: pl.michalmatu.aicallbridge.audioengines.DialogueModelId? = null
    private lateinit var developerProbes: MainActivityDeveloperProbes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtimePreferences = CallRuntimePreferences(this)
        val initialSelection = runtimePreferences.load()
        selectedAudioMode = initialSelection.audioMode
        selectedTextLlmProvider = initialSelection.textLlmProvider

        statusView = TextView(this).apply {
            text = runtimeSelectionSummary()
            textSize = 13f
            setTextColor(Color.rgb(39, 58, 83))
            background = roundedSurface(Color.rgb(234, 243, 253))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextIsSelectable(true)
        }
        developerProbes = MainActivityDeveloperProbes(this, statusView)

        val audioModeSpinner = Spinner(this).apply {
            tag = "settings-audio-mode-spinner"
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                CallAudioMode.entries.map { it.displayName },
            )
            setSelection(CallAudioMode.entries.indexOf(selectedAudioMode))
        }

        textLlmProviderSpinner = Spinner(this).apply {
            tag = "settings-provider-spinner"
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                TextLlmProvider.entries.map { it.displayName },
            )
            setSelection(TextLlmProvider.entries.indexOf(selectedTextLlmProvider))
            isEnabled = selectedAudioMode.usesTextLlm
        }

        audioModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedAudioMode = CallAudioMode.entries[position]
                runtimePreferences.saveAudioMode(selectedAudioMode)
                textLlmProviderSpinner.isEnabled = selectedAudioMode.usesTextLlm
                statusView.text = runtimeSelectionSummary()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        textLlmProviderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedTextLlmProvider = TextLlmProvider.entries[position]
                runtimePreferences.saveTextLlmProvider(selectedTextLlmProvider)
                statusView.text = runtimeSelectionSummary()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val requestMicButton = Button(this).apply {
            text = "Dostęp do mikrofonu"
            setOnClickListener { requestMicrophonePermissionIfNeeded() }
        }

        val enrollPhoneButton = Button(this).apply {
            text = "Numer telefonu i dane"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PhoneEnrollmentActivity::class.java))
            }
        }

        val oneTapButton = Button(this).apply {
            text = "Jeden przycisk: uruchom AI w trwającej rozmowie"
            setOnClickListener { startOneTapSupervisedTest() }
        }
        val stopAiButton = Button(this).apply {
            text = "STOP AI"
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, AppOwnedCampaignService::class.java)
                        .setAction(AppOwnedCampaignService.ACTION_STOP_ALL_AI),
                )
                statusView.text = "Wysłano STOP AI; wynik zapisze serwis."
            }
        }

        val startAuthorizedTaskButton = Button(this).apply {
            text = "Rozpocznij autoryzowane zadanie AI"
            setOnClickListener {
                // This tap enters the same existing app-owned START flow, without
                // requiring a second click in the campaign settings screen.
                startActivity(
                    Intent(this@MainActivity, CampaignToolActivity::class.java)
                        .putExtra(CampaignToolActivity.EXTRA_START_AUTHORIZED_CAMPAIGN, true),
                )
            }
        }

        val campaignToolsButton = Button(this).apply {
            text = "Kampanie"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CampaignToolActivity::class.java))
            }
        }

        // Primary navigation: conversation archive and offline lab are not settings.
        // These open read-only/history or off-call screens; no call authorization changes.
        val conversationHistoryButton = Button(this).apply {
            text = "Rozmowy i nagrania"
            setOnClickListener { startActivity(Intent(this@MainActivity,
                pl.michalmatu.aicallbridge.conversation.ConversationHistoryActivity::class.java)) }
        }
        val labShortcutButton = Button(this).apply {
            text = "Laboratorium offline"
            setOnClickListener { startActivity(Intent(this@MainActivity,
                pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity::class.java)) }
        }

        // UX-only grouping: the original controls and their callbacks are unchanged.
        // STOP and authorized-task actions remain immediately visible.
        val basicSection = UiAccordionSection(
            this, "basic", "Podstawowe",
            description = "Tryb rozmowy, model, silniki i uprawnienia.",
            initiallyExpanded = false,
        ).apply {
            addLabel("Tryb rozmowy")
            addControl(audioModeSpinner)
            addLabel("Model tekstowy (dla trybu STT / TTS)")
            addControl(textLlmProviderSpinner)
            addControl(Button(this@MainActivity).apply {
                text = "Modele: pobierz, importuj, wybierz"
                setOnClickListener {
                    startActivity(Intent(this@MainActivity,
                        pl.michalmatu.aicallbridge.textagent.LanguageModelActivity::class.java))
                }
            })
            addControl(Button(this@MainActivity).apply {
                text = "Silniki: STT / model / TTS"
                setOnClickListener {
                    startActivity(Intent(this@MainActivity,
                        pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity::class.java))
                }
            })
            addControl(requestMicButton)
            addControl(enrollPhoneButton)
        }
        val secretarySection = UiAccordionSection(
            this, "secretary", "Sekretarka",
            description = "Odbieranie połączeń i dane przygotowania rozmowy.",
        ).apply {
            addControl(Button(this@MainActivity).apply {
                text = "Odbieranie: AI / ręcznie"
                setOnClickListener { startActivity(Intent(this@MainActivity,
                    pl.michalmatu.aicallbridge.incoming.IncomingAssistantSettingsActivity::class.java)) }
            })
            addControl(Button(this@MainActivity).apply {
                text = "Skills i dane rozmów"
                setOnClickListener { startActivity(Intent(this@MainActivity,
                    pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity::class.java)) }
            })
        }
        val skillsSection = UiAccordionSection(
            this, "skills", "Skills i presety",
            description = "Wybór, kopie i wersjonowanie instrukcji Gemmy.",
        ).apply {
            addControl(Button(this@MainActivity).apply {
                text = "Skills i presety Gemmy"
                setOnClickListener { startActivity(Intent(this@MainActivity,
                    pl.michalmatu.aicallbridge.conversation.ConversationSkillsActivity::class.java)) }
            })
        }
        val laboratorySection = UiAccordionSection(
            this, "laboratory", "Laboratorium",
            description = "Testy wieloturowe offline bez połączenia telefonicznego.",
        ).apply {
            addControl(Button(this@MainActivity).apply {
                text = "Laboratorium skillsów — testy Gemmy offline"
                setOnClickListener { startActivity(Intent(this@MainActivity,
                    pl.michalmatu.aicallbridge.conversation.ConversationSkillLabActivity::class.java)) }
            })
        }
        val advancedSection = UiAccordionSection(
            this, "advanced", "Zaawansowane",
            description = "Narzędzia AI, diagnostyka i testy nadzorowane.",
        ).apply {
            addControl(Button(this@MainActivity).apply {
                text = "Narzędzia AI"
                setOnClickListener { startActivity(Intent(this@MainActivity,
                    pl.michalmatu.aicallbridge.tools.ConversationToolsActivity::class.java)) }
            })
            addLabel("Test nadzorowany aktywnej rozmowy — nie rozpoczyna połączenia")
            addControl(oneTapButton)
        }
        developerProbes.addControls(advancedSection.controlsContainer)
        advancedSection.styleExistingControls()

        // A short action area, two navigation shortcuts, and collapsed settings cards.
        fun styleAction(button: Button, color: Int, ink: Int) {
            button.isAllCaps = false
            button.textSize = 14f
            button.minHeight = dp(48)
            button.setPadding(dp(10), dp(10), dp(10), dp(10))
            button.setTextColor(ink)
            button.background = roundedSurface(color)
        }
        styleAction(startAuthorizedTaskButton, Color.rgb(26, 72, 137), Color.WHITE)
        styleAction(stopAiButton, Color.rgb(255, 232, 236), Color.rgb(151, 31, 51))
        styleAction(campaignToolsButton, Color.rgb(229, 237, 249), Color.rgb(27, 47, 75))
        styleAction(conversationHistoryButton, Color.rgb(237, 243, 251), Color.rgb(27, 47, 75))
        styleAction(labShortcutButton, Color.rgb(237, 243, 251), Color.rgb(27, 47, 75))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
            addView(TextView(this@MainActivity).apply {
                text = "AI Calls"
                textSize = 25f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(23, 39, 62))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Asystent połączeń"
                textSize = 13f
                setTextColor(Color.rgb(86, 100, 117))
                setPadding(0, 0, 0, dp(12))
            })
            addView(statusView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(14) })
            addView(startAuthorizedTaskButton)
            val actions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                campaignToolsButton.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                ).apply { rightMargin = dp(8) }
                stopAiButton.layoutParams = LinearLayout.LayoutParams(
                    dp(108), ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                addView(campaignToolsButton)
                addView(stopAiButton)
            }
            addView(actions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) })
            addView(TextView(this@MainActivity).apply {
                text = "Przejdź do"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(30, 47, 68))
                setPadding(0, dp(18), 0, dp(8))
            })
            val shortcuts = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                conversationHistoryButton.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                ).apply { rightMargin = dp(8) }
                labShortcutButton.layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
                )
                addView(conversationHistoryButton)
                addView(labShortcutButton)
            }
            addView(shortcuts)
            addView(TextView(this@MainActivity).apply {
                text = "Ustawienia"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(30, 47, 68))
                setPadding(0, dp(18), 0, dp(10))
            })
            listOf(basicSection, secretarySection, skillsSection, laboratorySection, advancedSection)
                .forEach { section ->
                    section.tag = "ui-settings-section:" + section.sectionId
                    addView(section, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(10) })
                }
        }

        // Pinned chat dock: visible without scrolling the settings above it.
        // Live text is only held in process memory, not in saved view state.
        val root = LinearLayout(this).apply {
            tag = "main-screen-root"
            setBackgroundColor(Color.rgb(247, 249, 253))
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(0, bars.top, 0, bars.bottom)
                } else {
                    view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                }
                insets
            }
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@MainActivity).apply {
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
            addView(ConversationChatPanel(this@MainActivity, initiallyExpanded = false))
            addView(AppNavigation.bar(this@MainActivity, AppTab.HOME))
        }
        window.statusBarColor = Color.rgb(247, 249, 253)
        window.navigationBarColor = Color.rgb(14, 23, 39)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(root)

        developerProbes.attach()

        if (intent.getBooleanExtra(EXTRA_RUN_CAPABILITY_PROBE, false)) {
            developerProbes.runCapabilityProbe()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundedSurface(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }

    override fun onResume() {
        super.onResume()
        val selected = pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore(this).load()?.dialogue
        if (selected != displayedModel) {
            displayedModel = selected
            statusView.text = runtimeSelectionSummary()
        }
    }

    override fun onDestroy() {
        if (::developerProbes.isInitialized) developerProbes.close()
        super.onDestroy()
    }

    private fun runtimeSelectionSummary(): String = buildString {
        append("Tryb: ").append(selectedAudioMode.displayName).append('\n')
        when (selectedAudioMode) {
            CallAudioMode.LOCAL_STT_TTS -> append("Model tekstowy: ").append(selectedTextLlmProvider.displayName)
            CallAudioMode.OPENAI_REALTIME_AUDIO -> append("OpenAI Realtime audio · wybór tekstowy zachowany")
            CallAudioMode.LOCAL_REALTIME_AUDIO -> append("Lokalny silnik realtime · wybór tekstowy zachowany")
        }
        append('\n')
        val profile = pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore(this@MainActivity).load()
        if (profile == null) {
            append("Nieprawidłowa konfiguracja silników audio")
            return@buildString
        }
        val model = pl.michalmatu.aicallbridge.textagent.LanguageModelCatalog[profile.dialogue]
        val readiness = pl.michalmatu.aicallbridge.textagent.AndroidGemma4ModelReadiness.check(this@MainActivity, model.id)
        append("Model: ").append(model.title).append(" · ").append(readiness.state)
    }

    /**
     * One explicit on-screen opt-in replaces multi-screen/manual attach clicks.
     * It never dials, hangs up, impersonates the dialer, or weakens the media gate.
     * The same user action continues after Android permission approval.
     */
    private fun startOneTapSupervisedTest() {
        val required = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
        )
        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            statusView.text = "Android wymaga zgody na mikrofon i stan telefonu."
            requestPermissions(missing.toTypedArray(), REQUEST_ONE_TAP_SUPERVISED)
            return
        }
        val snapshot = SupervisedCallReadinessDiagnostics.capture(this)
        val reportSaved = SupervisedCallReadinessDiagnostics.write(this, snapshot)
        val reportInfo = if (reportSaved) "Raport zapisany." else "Nie udało się zapisać raportu."
        when (snapshot.failure) {
            null -> {
                statusView.text = "Zgoda na test udzielona przyciskiem. Uruchamiam lokalny " +
                    "STT → Gemma/skrypt → TTS → Shizuku (maksymalnie 2 odpowiedzi)."
                runCatching {
                    startForegroundService(
                        Intent(this, AppOwnedCampaignService::class.java)
                            .setAction(AppOwnedCampaignService.ACTION_START_SUPERVISED_AUDIO),
                    )
                }.onFailure {
                    statusView.text = "Nie można uruchomić serwisu testowego: " +
                        it.javaClass.simpleName
                }
            }
            SupervisedCallPilotFailure.NO_ACTIVE_CELLULAR_CALL ->
                statusView.text = "Brak aktywnej rozmowy komórkowej. " +
                    "Rozpocznij ją w Google Phone i naciśnij ten sam przycisk. $reportInfo"
            SupervisedCallPilotFailure.AUDIO_ROUTE_NOT_READY ->
                statusView.text = "Rozmowa wykryta, ale Android zgłosił " +
                    "${snapshot.audioModeLabel}. AI nie zostało podłączone. $reportInfo"
            SupervisedCallPilotFailure.SHIZUKU_NOT_READY ->
                statusView.text = "Shizuku niedostępne — uruchom je i naciśnij ten sam przycisk. " +
                    reportInfo
            SupervisedCallPilotFailure.PERMISSION_REQUIRED ->
                statusView.text = "Brakuje zgód Androida. $reportInfo"
        }
    }

    private fun requestMicrophonePermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            statusView.text = "Microphone permission already granted"
            return
        }

        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_ONE_TAP_SUPERVISED) {
            if (permissions.isNotEmpty() && permissions.indices.all {
                    grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                startOneTapSupervisedTest()
            } else {
                statusView.text = "Android odmówił uprawnień. Nie uruchomiono audio AI."
            }
            return
        }

        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            statusView.text = if (granted) {
                "Microphone permission granted"
            } else {
                "Microphone permission denied"
            }
        }
    }

    private companion object {
        const val REQUEST_ONE_TAP_SUPERVISED = 1007
        const val REQUEST_RECORD_AUDIO = 1001
        const val EXTRA_RUN_CAPABILITY_PROBE = "run_probe"
    }
}
