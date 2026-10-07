package pl.michalmatu.aicallbridge.identity

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

class PhoneEnrollmentActivity : Activity() {
    private lateinit var phoneInput: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)

        phoneInput = EditText(this).apply {
            hint = "Phone number"
            inputType = InputType.TYPE_CLASS_PHONE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            imeOptions =
                EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }

        statusView = TextView(this).apply {
            textSize = 15f
        }

        val saveButton = Button(this).apply {
            text = "Save locally"
            setOnClickListener { savePhone() }
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                clearInput()
                finish()
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 48, 32, 32)
                addView(TextView(this@PhoneEnrollmentActivity).apply {
                    text = "Phone / service number"
                    textSize = 24f
                })
                addView(
                    phoneInput,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(saveButton)
                addView(cancelButton)
                addView(statusView)
            },
        )
    }

    private fun savePhone() {
        val normalized = PhoneEnrollmentInputNormalizer.normalize(phoneInput.text)
        if (normalized == null) {
            clearInput()
            statusView.text = STATUS_INVALID
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
        statusView.text = if (stored) STATUS_SAVED else STATUS_STORAGE_FAILED
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

    private companion object {
        const val STATUS_SAVED = "Phone number saved locally"
        const val STATUS_INVALID = "Invalid phone number format"
        const val STATUS_STORAGE_FAILED = "Phone number save failed"
    }
}
