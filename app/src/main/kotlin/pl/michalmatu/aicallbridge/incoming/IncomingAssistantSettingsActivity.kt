package pl.michalmatu.aicallbridge.incoming

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.WindowManager
import android.widget.*
import pl.michalmatu.aicallbridge.audioengines.AudioEngineSettingsActivity
import pl.michalmatu.aicallbridge.campaign.AndroidOwnedInCallService
import pl.michalmatu.aicallbridge.conversation.ConversationPreparationActivity
import pl.michalmatu.aicallbridge.conversationcore.AnsweringMode

class IncomingAssistantSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(28,36,28,28) }
        val feedback=TextView(this)
        val store=IncomingAssistantPolicyStore(this)
        val policy=runCatching { store.load() }.getOrElse {
            feedback.text="Nie można odczytać polityki odbierania. Zapisz wybrany tryb ponownie."
            pl.michalmatu.aicallbridge.conversationcore.IncomingAssistantPolicy("unavailable",AnsweringMode.MANUAL,null,true)
        }
        fun text(value:String)=TextView(this).apply { text=value;textSize=16f }
        content.addView(text("Odbieranie połączeń — wirtualna asystentka"))
        content.addView(text("AI przyjmuje wiadomość. Przejmij rozmowę zatrzymuje AI i zostawia połączenie Tobie. Zapis polityki obowiązuje dla kolejnych połączeń na wybranej karcie SIM."))
        val mode=Spinner(this).apply {
            adapter=ArrayAdapter(this@IncomingAssistantSettingsActivity,android.R.layout.simple_spinner_dropdown_item,
                listOf("Ręcznie — samodzielnie odbieram","AI — odbieraj i przyjmuj wiadomości"))
            setSelection(if(policy.mode==AnsweringMode.AI) 1 else 0)
        }
        content.addView(mode)
        val telecom=getSystemService(TelecomManager::class.java)
        val accounts:List<PhoneAccountHandle> = if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED)
            runCatching { telecom.callCapablePhoneAccounts }.getOrDefault(emptyList()) else emptyList()
        val account=Spinner(this).apply {
            adapter=ArrayAdapter(this@IncomingAssistantSettingsActivity,android.R.layout.simple_spinner_dropdown_item,
                accounts.mapIndexed { index,handle -> telecom.getPhoneAccount(handle)?.label?.toString() ?: "SIM ${index+1}" }.ifEmpty { listOf("Brak dostępnej karty SIM — sprawdź uprawnienia Androida") })
            setSelection(accounts.indexOfFirst { IncomingAssistantPolicyStore.accountKey(it)==policy.accountKey }.coerceAtLeast(0))
        }
        content.addView(account)
        val recording=CheckBox(this).apply { text="Zapisuj transkrypt i dostępne fragmenty audio";isChecked=policy.recordEvidence }
        content.addView(recording)
        content.addView(text("Dialog: model bez skryptu. STT, model i TTS pochodzą ze wspólnych ustawień silników; instrukcje z profilu przychodzącego."))
        content.addView(Button(this).apply {
            text="Zapisz tryb odbierania"
            setOnClickListener {
                val selected=if(mode.selectedItemPosition==1) AnsweringMode.AI else AnsweringMode.MANUAL
                val key=accounts.getOrNull(account.selectedItemPosition)?.let(IncomingAssistantPolicyStore::accountKey)
                if(selected==AnsweringMode.AI && key==null) { feedback.text="Wybierz kartę SIM po nadaniu uprawnień Androida.";return@setOnClickListener }
                runCatching { store.save(selected,key,recording.isChecked); AndroidOwnedInCallService.incomingPolicyChanged() }
                    .onSuccess { feedback.text=if(selected==AnsweringMode.AI) "Zapisano: AI odbiera kolejne połączenia na wybranej karcie SIM, gdy telefon i silniki są gotowe." else "Zapisano tryb ręczny; AI oddaje aktywną rozmowę użytkownikowi." }
                    .onFailure { feedback.text="Nie zapisano polityki odbierania." }
            }
        })
        content.addView(Button(this).apply { text="Silniki: STT / model / TTS";setOnClickListener {startActivity(Intent(this@IncomingAssistantSettingsActivity,AudioEngineSettingsActivity::class.java))} })
        content.addView(Button(this).apply { text="Skills i dane rozmów";setOnClickListener {startActivity(Intent(this@IncomingAssistantSettingsActivity,ConversationPreparationActivity::class.java))} })
        content.addView(feedback)
        setContentView(ScrollView(this).apply {addView(content)})
    }
}
