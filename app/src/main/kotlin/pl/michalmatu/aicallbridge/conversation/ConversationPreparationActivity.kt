package pl.michalmatu.aicallbridge.conversation

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import pl.michalmatu.aicallbridge.identity.IdentityFieldId
import pl.michalmatu.aicallbridge.identity.PhoneEnrollmentActivity

/** Preparation is editable without a call, campaign or authorization ceremony. */
class ConversationPreparationActivity : Activity() {
    private lateinit var store: AndroidConversationPreparationStore
    private var profileId = ConversationPreparation.OUTGOING_ID
    private lateinit var notes: EditText
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private val skills = linkedMapOf<ConversationSkill, CheckBox>()
    private val fields = linkedMapOf<IdentityFieldId, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = try { AndroidConversationPreparationStore(this) } catch (failure: Exception) {
            setContentView(TextView(this).apply { text = "Nie udało się otworzyć przygotowania rozmów: ${failure.javaClass.simpleName}" })
            return
        }
        profileId = savedInstanceState?.getString("profile_id") ?: ConversationPreparation.OUTGOING_ID
        showProfile()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("profile_id", profileId)
        super.onSaveInstanceState(outState)
    }
    private fun showProfile() {
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32) }
        setContentView(ScrollView(this).apply { addView(content) })
        status = TextView(this)
        content.addView(TextView(this).apply { text = "Przygotowanie rozmowy"; textSize = 22f })
        val selector = RadioGroup(this)
        listOf(ConversationPreparation.OUTGOING_ID to "Wychodzące", ConversationPreparation.INCOMING_ID to "Przychodzące").forEachIndexed { index, (id, title) ->
            selector.addView(RadioButton(this).apply { this.id = 100 + index; text = title; isChecked = profileId == id
                setOnClickListener { profileId = id; showProfile() } })
        }
        content.addView(selector)
        val profile = try { store.load(profileId) } catch (failure: Exception) {
            status.text = "Nie udało się odczytać profilu: ${failure.javaClass.simpleName}"; content.addView(status); return
        }
        content.addView(TextView(this).apply { text = if (profile.direction == ConversationDirection.INCOMING)
            "Kontekst asystenta odbierającego. Edycja profilu nie uruchamia automatycznego odbierania."
            else "Kontekst asystenta dzwoniącego. Cel i dozwolone działania określa zlecone zadanie." })
        notes = EditText(this).apply { hint = "Informacje ogólne potrzebne w rozmowie"; minLines = 4; setText(profile.notes) }
        content.addView(notes)
        content.addView(TextView(this).apply { text = "Dane abonenta pozostają w sejfie. Tutaj wybierasz potrzebne pola, a aplikacja osobno sprawdza możliwość ujawnienia." })
        skills.clear(); fields.clear()
        ConversationSkill.entries.forEach { skill ->
            val choice = CheckBox(this).apply { text = skill.title; isChecked = skill in profile.skills }
            skills[skill] = choice; content.addView(choice)
        }
        content.addView(TextView(this).apply { text = "Referencje do danych z sejfu"; textSize = 17f })
        IdentityFieldId.entries.forEach { field ->
            val choice = CheckBox(this).apply { text = when (field) {
                IdentityFieldId.PHONE -> "Telefon"; IdentityFieldId.FIRST_NAME -> "Imię"; IdentityFieldId.LAST_NAME -> "Nazwisko"
                IdentityFieldId.EMAIL -> "E-mail"; IdentityFieldId.ADDRESS -> "Adres"; IdentityFieldId.DATE_OF_BIRTH -> "Data urodzenia"; IdentityFieldId.PESEL -> "PESEL"
            }; isChecked = field in profile.dataFields }
            fields[field] = choice; content.addView(choice)
        }
        content.addView(Button(this).apply { text = "Edytuj telefon w sejfie"; setOnClickListener { startActivity(Intent(this@ConversationPreparationActivity, PhoneEnrollmentActivity::class.java)) } })
        fun selection() = profile.withSettings(notes.text.toString(), skills.filterValues { it.isChecked }.keys, fields.filterValues { it.isChecked }.keys)
        content.addView(Button(this).apply { text = "Zapisz przygotowanie rozmowy"; setOnClickListener {
            try { store.save(selection()); status.text = "Zapisano. Aktywna rozmowa zachowuje swoją konfigurację." }
            catch (failure: Exception) { status.text = "Błąd zapisu: ${failure.javaClass.simpleName}" }
        } })
        content.addView(Button(this).apply { text = "Podgląd roli, skillsów i kontekstu"; setOnClickListener {
            AlertDialog.Builder(this@ConversationPreparationActivity).setTitle("Przygotowanie modelu")
                .setMessage(ConversationInstructionBuilder.build(selection(), "Cel pochodzi z konkretnego zlecenia rozmowy."))
                .setPositiveButton("OK", null).show()
        } })
        content.addView(status)
    }
}
