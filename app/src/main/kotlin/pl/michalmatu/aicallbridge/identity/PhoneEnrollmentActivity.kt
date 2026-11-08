package pl.michalmatu.aicallbridge.identity

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.R
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class PhoneEnrollmentActivity : UiChromeActivity(AppTab.AGENT) {
    private lateinit var phoneInput: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        phoneInput = EditText(this).apply {
            hint = getString(R.string.phone_ui_hint)
            contentDescription = getString(R.string.phone_ui_field)
            background = UiDesign.surfaceDrawable(this@PhoneEnrollmentActivity)
            minimumHeight = UiDesign.dp(this@PhoneEnrollmentActivity, 52)
            setPadding(UiDesign.dp(this@PhoneEnrollmentActivity, 14), UiDesign.dp(this@PhoneEnrollmentActivity, 12), UiDesign.dp(this@PhoneEnrollmentActivity, 14), UiDesign.dp(this@PhoneEnrollmentActivity, 12))
            inputType = InputType.TYPE_CLASS_PHONE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            imeOptions =
                EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(UiDesign.secondary)
            setPadding(0, UiDesign.dp(this@PhoneEnrollmentActivity, 12), 0, 0)
        }

        val saveButton = UiDesign.action(this, getString(R.string.phone_ui_save), UiActionTone.PRIMARY) { savePhone() }
        val cancelButton = UiDesign.action(this, getString(R.string.phone_ui_cancel)) {
            clearInput()
            finish()
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiDesign.dp(this@PhoneEnrollmentActivity, 20), UiDesign.dp(this@PhoneEnrollmentActivity, 24), UiDesign.dp(this@PhoneEnrollmentActivity, 20), UiDesign.dp(this@PhoneEnrollmentActivity, 24))
                addView(TextView(this@PhoneEnrollmentActivity).apply {
                    text = getString(R.string.phone_ui_title)
                    textSize = 22f
                    setTextColor(UiDesign.ink)
                })
                addView(UiDesign.caption(this@PhoneEnrollmentActivity, getString(R.string.phone_ui_description)), UiDesign.spaceAfter(this@PhoneEnrollmentActivity, 16))
                addView(UiDesign.heading(this@PhoneEnrollmentActivity, getString(R.string.phone_ui_field)).apply { textSize = 15f }, UiDesign.spaceAfter(this@PhoneEnrollmentActivity, 8))
                addView(
                    phoneInput,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(UiDesign.caption(this@PhoneEnrollmentActivity, getString(R.string.phone_ui_example)), UiDesign.spaceAfter(this@PhoneEnrollmentActivity, 20))
                addView(saveButton, UiDesign.spaceAfter(this@PhoneEnrollmentActivity, 12))
                addView(cancelButton, UiDesign.spaceAfter(this@PhoneEnrollmentActivity, 12))
                addView(statusView)
            },
        )
    }

    private fun savePhone() {
        val normalized = PhoneEnrollmentInputNormalizer.normalize(phoneInput.text)
        if (normalized == null) {
            clearInput()
            statusView.text = getString(R.string.phone_ui_invalid)
            return
        }

        val stored = runCatching {
            AndroidIdentityVault.create(this)
                .put(
                    IdentityFieldId.PHONE,
                    IdentitySecretValue.of(normalized),
                )
                .getOrThrow()
        }.isSuccess

        clearInput()
        statusView.text = getString(if (stored) R.string.phone_ui_saved else R.string.phone_ui_storage_failed)
    }

    override fun onPause() {
        clearInput()
        super.onPause()
    }

    override fun onDestroy() {
        clearInput()
        super.onDestroy()
    }

    private fun clearInput() {
        if (::phoneInput.isInitialized) {
            phoneInput.text.clear()
            phoneInput.clearFocus()
        }
    }

}
