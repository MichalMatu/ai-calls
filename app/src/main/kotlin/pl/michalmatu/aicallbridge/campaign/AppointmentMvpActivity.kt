package pl.michalmatu.aicallbridge.campaign

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.AudioEngineRegistry
import pl.michalmatu.aicallbridge.identity.AndroidIdentityVault
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.ui.*

/** Consent is bound to one exact target/SIM/task. Never books through the UI. */
class AppointmentMvpActivity : UiChromeActivity(AppTab.HOME) {
    override fun toolbarTitle() = "Umów wizytę — AI Calls"
    private val store by lazy { AppointmentMvpStore(this) }
    private val grants by lazy { CampaignAuthorizationStore(this) }
    private lateinit var number: EditText
    private lateinit var goal: EditText
    private lateinit var from: EditText
    private lateinit var until: EditText
    private lateinit var terms: EditText
    private lateinit var sim: Spinner
    private lateinit var feedback: TextView
    private var pendingAuthorize = false
    private var pendingAuthorizeHadSim = false
    private var restoredSimId: Int? = null
    private lateinit var review: TextView
    private val selectedFields = linkedMapOf<IdentityFieldId, CheckBox>()
    private val availableSubscriptions = mutableListOf<Pair<Int, String>>()
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() { showReview(); handler.postDelayed(this, 1500L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAuthorize = savedInstanceState?.getBoolean("pending_authorize") == true
        pendingAuthorizeHadSim = savedInstanceState?.getBoolean("pending_authorize_had_sim") == true
        restoredSimId = if (savedInstanceState?.containsKey("selected_sim_sub_id") == true)
            savedInstanceState.getInt("selected_sim_sub_id") else null
        val saved = store.load()
        val draft = AppointmentEditorDraftStore(this).consume()
        val tomorrow = ZonedDateTime.now(ZoneId.of("Europe/Warsaw")).plusDays(1)
            .withHour(9).withMinute(0).withSecond(0).withNano(0)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@AppointmentMvpActivity, 20),
                UiDesign.dp(this@AppointmentMvpActivity, 20),
                UiDesign.dp(this@AppointmentMvpActivity, 20),
                UiDesign.dp(this@AppointmentMvpActivity, 32))
        }
        fun field(title: String, placeholder: String, value: String, type: Int): EditText {
            body.addView(UiDesign.caption(this, title), UiDesign.spaceAfter(this, 5))
            return EditText(this).apply {
                hint = placeholder
                inputType = type
                setSingleLine(true)
                setSelectAllOnFocus(true)
                setText(value)
                tag = "appointment-input:" + title
                body.addView(this, UiDesign.spaceAfter(this@AppointmentMvpActivity, 12))
            }
        }
        body.addView(UiDesign.heading(this, "Zadzwoń w sprawie wizyty"),
            UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this,
            "AI pyta o termin i warunki. Nie rezerwuje wizyty. " +
                "Każda propozycja zostanie pokazana do Twojej weryfikacji."),
            UiDesign.spaceAfter(this, 12))
        number = field("Numer telefonu w formacie międzynarodowym",
            "+48...", draft?.target ?: saved?.target.orEmpty(), InputType.TYPE_CLASS_PHONE)
        body.addView(UiDesign.caption(this, "Wybierz SIM"), UiDesign.spaceAfter(this, 6))
        sim = Spinner(this).apply { tag = "appointment-sim-picker" }
        body.addView(sim, UiDesign.spaceAfter(this, 12))
        goal = field("Cel rozmowy", "Umów wizytę kontrolną", draft?.goal ?: saved?.goal.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        from = field("Termin od (rrrr-mm-dd gg:mm)", "2027-02-10 09:00",
            display(draft?.from ?: saved?.notBefore ?: tomorrow), InputType.TYPE_CLASS_TEXT)
        until = field("Termin do (rrrr-mm-dd gg:mm)", "2027-02-10 17:00",
            display(draft?.until ?: saved?.notAfter ?: tomorrow.plusHours(8)), InputType.TYPE_CLASS_TEXT)
        terms = field("Warunki", "Bez dodatkowych opłat", draft?.terms ?: saved?.conditions.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        body.addView(UiDesign.heading(this, "Dane IdentityVault").apply { textSize = 17f },
            UiDesign.spaceAfter(this, 5))
        body.addView(UiDesign.caption(this,
            "Zaznacz tylko pola, na których ujawnienie wyrażasz zgodę. " +
                "Wartości danych nie są wpisywane do polecenia Gemma."),
            UiDesign.spaceAfter(this, 7))
        for (id in AppointmentMvpTask.ALLOWED_FIELDS.sortedBy { it.name }) {
            val choice = CheckBox(this).apply {
                text = when (id) {
                    IdentityFieldId.FIRST_NAME -> "Imię"
                    IdentityFieldId.LAST_NAME -> "Nazwisko"
                    IdentityFieldId.PHONE -> "Telefon"
                    IdentityFieldId.EMAIL -> "E-mail"
                    else -> id.name
                }
                // A diagnostic draft never reuses a prior consent.
                isChecked = draft == null && id in saved?.disclosureFields.orEmpty()
                tag = "appointment-disclosure:" + id.name
            }
            selectedFields[id] = choice
            body.addView(choice, UiDesign.spaceAfter(this, 3))
        }
        feedback = UiDesign.caption(this, "").apply {
            tag = "appointment-feedback"
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(UiDesign.action(this, "Autoryzuj i rozpocznij jedną rozmowę",
            UiActionTone.PRIMARY) { authorizeAndStart() }.apply {
            tag = "appointment-authorize-call"
        })
        body.addView(UiDesign.action(this, "STOP AI — anuluj zadanie",
            UiActionTone.DANGER) {
            startService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_CANCEL_OUTGOING_TASK))
            feedback.text = "Zażądano zatrzymania zadania."
        })
        body.addView(UiDesign.action(this, "TAKE OVER — przejmij rozmowę") {
            val active = AndroidOwnedInCallService.currentCall()
            if (active != null && AppOwnedClirCampaignExecutor.takeOver(active)) {
                startService(Intent(this, AppOwnedCampaignService::class.java)
                    .setAction(AppOwnedCampaignService.ACTION_PAUSE_AFTER_TAKEOVER))
                feedback.text = "AI zatrzymane; rozmowa przekazana człowiekowi."
            } else feedback.text = "Brak własnej rozmowy do przejęcia."
        })
        body.addView(UiDesign.heading(this, "Propozycja wizyty").apply { textSize = 17f },
            UiDesign.spaceAfter(this, 8))
        review = UiDesign.caption(this, "").apply {
            tag = "appointment-review-result"
            textSize = 15f
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(review, UiDesign.spaceAfter(this, 8))
        body.addView(feedback, UiDesign.spaceAfter(this, 10))
        setContentView(ScrollView(this).apply {
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        refreshSim()
        showReview()
    }

    private fun refreshSim() {
        val selectedBefore = availableSubscriptions.getOrNull(sim.selectedItemPosition)?.first ?: restoredSimId
        availableSubscriptions.clear()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
            UiSelect.setOptions(sim, listOf("Brak uprawnienia READ_PHONE_STATE"))
            return
        }
        val manager = getSystemService(SubscriptionManager::class.java)
        val infos = runCatching { manager?.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())
        for (info in infos) CampaignAccountScope.forSubscription(this, info.subscriptionId)
            ?.let { availableSubscriptions.add(info.subscriptionId to it) }
        UiSelect.setOptions(sim, if (availableSubscriptions.isEmpty())
            listOf("Nie znaleziono aktywnej SIM")
        else availableSubscriptions.mapIndexed { index, pair ->
            val label = infos.firstOrNull { it.subscriptionId == pair.first }
                ?.displayName?.toString()?.take(45).orEmpty()
            "SIM " + (index + 1) + " — " + label +
                " (subId " + pair.first + ", slot " +
                pair.second.substringAfter(":slot:").substringBefore(":") + ")"
        })
        val index = AppointmentSimSelectionPolicy.index(
            availableSubscriptions.map { it.first }, selectedBefore, store.load()?.subscriptionId)
        if (index == null && selectedBefore != null) {
            availableSubscriptions.clear()
            UiSelect.setOptions(sim, listOf("Wybrana karta SIM nie jest już dostępna. Otwórz formularz ponownie."))
            feedback.text = "Wybrana karta SIM zmieniła się; połączenie nie zostanie rozpoczęte."
            return
        }
        sim.setSelection(index ?: 0)
        if (index != null) restoredSimId = null
    }

    private fun authorizeAndStart() {
        val missing = listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO).filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pendingAuthorize = true
            pendingAuthorizeHadSim = availableSubscriptions.getOrNull(sim.selectedItemPosition) != null
            requestPermissions(missing.toTypedArray(), 902)
            feedback.text = "Zatwierdź uprawnienia Androida. Przygotowanie rozmowy będzie kontynuowane."
            return
        }
        if (!AndroidTelecomOwnedCallController.canControl(this)) {
            val role = getSystemService(RoleManager::class.java)
            if (role?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                pendingAuthorize = true
                feedback.text = "Zatwierdź rolę dialera w Androidzie, aby kontynuować."
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_DIALER), 903)
            } else feedback.text = "Rola dialera niedostępna."
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        if (runCatching { telecom == null || telecom.isInCall }.getOrDefault(true) ||
            AppOwnedClirCampaignExecutor.isRunning() ||
            CampaignContinuationStore(this).load() != null) {
            feedback.text = "Trwa inne połączenie lub zadanie AI."
            return
        }
        val selected = availableSubscriptions.getOrNull(sim.selectedItemPosition)
        if (selected == null ||
            CampaignAccountScope.forSubscription(this, selected.first) != selected.second ||
            CampaignPhoneAccountResolver.resolveSelectedVoiceAccount(
                this, telecom!!, selected.first) == null) {
            feedback.text = "Nie można potwierdzić wybranej SIM."
            return
        }
        val profile = AudioEngineProfileStore(this).load()
        if (profile == null || AudioEngineRegistry.incompatibility(profile) != null) {
            feedback.text = "Wybierz gotowe do pracy Gemma, STT i TTS."
            return
        }
        val selectedIds = selectedFields.filterValues { it.isChecked }.keys.toSet()
        val available = AndroidIdentityVault.create(this).availableFields().getOrNull()
        if (available == null || !available.containsAll(selectedIds)) {
            feedback.text = "Wybrane pola nie są dostępne w IdentityVault."
            return
        }
        val task = runCatching {
            AppointmentMvpTask.create(number.text.toString(), selected.first, selected.second,
                goal.text.toString(), parse(from.text.toString()), parse(until.text.toString()),
                terms.text.toString(), selectedIds)
        }.getOrElse {
            feedback.text = "Sprawdź format +48..., cel i terminy rrrr-mm-dd gg:mm."
            return
        }
        if (task.notBefore.toInstant().isBefore(Instant.now())) {
            feedback.text = "Zakres wizyty musi być w przyszłości."
            return
        }
        if (CampaignAuthorizationPolicy.authorize(
                task.grant(System.currentTimeMillis()), task.request()
            ) !is CampaignAuthorizationDecision.Authorized) {
            feedback.text = "Nie udało się zatwierdzić zakresu zgody."
            return
        }
        val saved = runCatching {
            check(store.save(task)) { "appointment_store_failed" }
            grants.save(task.grant(System.currentTimeMillis()))
        }.isSuccess
        if (!saved) {
            feedback.text = "Błąd trwałego zapisu zadania."
            return
        }
        try {
            startForegroundService(Intent(this, AppOwnedCampaignService::class.java)
                .setAction(AppOwnedCampaignService.ACTION_START_BOOK_APPOINTMENT))
            feedback.text = "Zlecono przygotowanie modelu. Żadnej wizyty nie zarezerwowano."
        } catch (_: RuntimeException) {
            grants.revoke(System.currentTimeMillis())
            feedback.text = "Błąd startu usługi; zgoda unieważniona."
        }
    }

    private fun showReview() {
        if (!::review.isInitialized) return
        val task = store.load()
        val grant = grants.load()
        val proposal = if (task != null && grant?.grantId == task.grantId)
            store.review(task.grantId) else null
        review.text = if (proposal == null) "Brak propozycji do weryfikacji."
        else "REVIEW_REQUIRED — NIE REZERWACJA\n" +
            "Termin: " + display(proposal.at) + "\n" +
            "Warunki: " + task?.conditions.orEmpty() + "\n" +
            "Odpowiedź rozmówcy: " + proposal.observedTerms + "\n" +
            "Brak potwierdzenia terminu u dostawcy."
    }
    override fun onResume() {
        super.onResume()
        if (::sim.isInitialized) refreshSim()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }
    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }
    override fun onRequestPermissionsResult(code: Int, names: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, names, results)
        if (code == 902 && pendingAuthorize) {
            val hadReviewedSim = pendingAuthorizeHadSim
            pendingAuthorize = false
            pendingAuthorizeHadSim = false
            if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {
                refreshSim()
                if (hadReviewedSim) authorizeAndStart()
                else feedback.text = "Sprawdź teraz kartę SIM i rozpocznij rozmowę."
            } else feedback.text = "Bez wymaganych uprawnień nie rozpoczęto rozmowy."
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_authorize", pendingAuthorize)
        outState.putBoolean("pending_authorize_had_sim", pendingAuthorizeHadSim)
        availableSubscriptions.getOrNull(sim.selectedItemPosition)?.first?.let {
            outState.putInt("selected_sim_sub_id", it)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 903 && pendingAuthorize) {
            pendingAuthorize = false
            if (AndroidTelecomOwnedCallController.canControl(this)) authorizeAndStart()
            else feedback.text = "AI Calls nie otrzymało roli dialera. Nie rozpoczęto rozmowy."
        }
    }
    private companion object {
        val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        fun parse(value: String) =
            LocalDateTime.parse(value.trim(), format).atZone(ZoneId.of("Europe/Warsaw"))
        fun display(value: ZonedDateTime) = value.format(format)
    }
}
