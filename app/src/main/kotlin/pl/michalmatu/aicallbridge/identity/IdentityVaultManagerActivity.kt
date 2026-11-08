package pl.michalmatu.aicallbridge.identity

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import pl.michalmatu.aicallbridge.R
import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiSelect

/**
 * Owner-only editor. Values are never rendered or logged; editing a saved value requires replacement.
 * Saving a field never authorizes any AI use/disclosure or call.
 */
class IdentityVaultManagerActivity : UiChromeActivity(AppTab.AGENT) {
    override fun toolbarTitle(): String = getString(R.string.ux10_vault_title)

    private lateinit var predefined: PersistentIdentityVault
    private lateinit var custom: AndroidCustomIdentityVault
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        predefined = AndroidIdentityVault.create(this)
        custom = AndroidCustomIdentityVault(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiDesign.dp(this@IdentityVaultManagerActivity, 20),
                UiDesign.dp(this@IdentityVaultManagerActivity, 20),
                UiDesign.dp(this@IdentityVaultManagerActivity, 20),
                UiDesign.dp(this@IdentityVaultManagerActivity, 40))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::scroll.isInitialized) render()
    }

    private fun labelFor(field: IdentityFieldId): String = getString(when (field) {
        IdentityFieldId.FIRST_NAME -> R.string.prep_ui_first_name
        IdentityFieldId.LAST_NAME -> R.string.prep_ui_last_name
        IdentityFieldId.PHONE -> R.string.prep_ui_phone
        IdentityFieldId.EMAIL -> R.string.prep_ui_email
        IdentityFieldId.ADDRESS -> R.string.prep_ui_address
        IdentityFieldId.DATE_OF_BIRTH -> R.string.prep_ui_birth
        IdentityFieldId.PESEL -> R.string.ux09_agent_pesel
    })

    private fun kindName(kind: CustomVaultKind): String = getString(when (kind) {
        CustomVaultKind.TEXT -> R.string.ux10_vault_text
        CustomVaultKind.NUMBER -> R.string.ux10_vault_number
        CustomVaultKind.DATE -> R.string.ux10_vault_date
        CustomVaultKind.SECRET -> R.string.ux10_vault_secret
    })

    private fun sensitivityName(sensitivity: IdentitySensitivity): String = getString(when (sensitivity) {
        IdentitySensitivity.NORMAL -> R.string.ux10_vault_normal
        IdentitySensitivity.SENSITIVE -> R.string.ux10_vault_sensitive
        IdentitySensitivity.HIGH -> R.string.ux10_vault_high
    })

    private fun render() {
        val savedY = scroll.scrollY
        body.removeAllViews()
        body.addView(UiDesign.heading(this, getString(R.string.ux10_vault_title)),
            UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this, getString(R.string.ux10_vault_intro)),
            UiDesign.spaceAfter(this, 18))
        body.addView(UiDesign.heading(this, getString(R.string.ux10_vault_standard)),
            UiDesign.spaceAfter(this, 10))
        val ids = predefined.availableFields().getOrElse {
            showReadError()
            return
        }
        for (field in IdentityFieldId.entries) {
            val present = field in ids
            val card = UiDesign.card(this, 12).apply { tag = "vault-standard:" + field.name }
            card.addView(UiDesign.heading(this, labelFor(field)).apply { textSize = 16f },
                UiDesign.spaceAfter(this, 4))
            card.addView(UiDesign.caption(this, getString(
                if (present) R.string.ux10_vault_saved else R.string.ux10_vault_empty)),
                UiDesign.spaceAfter(this, 8))
            card.addView(UiDesign.action(this, getString(
                if (present) R.string.ux10_vault_replace else R.string.ux10_vault_add)) {
                editStandard(field)
            }, UiDesign.spaceAfter(this, 8))
            if (present) card.addView(UiDesign.action(this,
                getString(R.string.ux10_vault_remove), UiActionTone.REVOCATION) {
                confirmRemoval(labelFor(field)) {
                    runCatching { predefined.remove(field).getOrThrow() }
                        .onSuccess { render() }.onFailure(::showError)
                }
            })
            body.addView(card, UiDesign.spaceAfter(this, 10))
        }

        body.addView(UiDesign.heading(this, getString(R.string.ux10_vault_custom)),
            UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this, getString(R.string.ux10_vault_custom_note)),
            UiDesign.spaceAfter(this, 10))
        val own = runCatching { custom.list() }.getOrElse {
            showReadError()
            return
        }
        for (field in own) {
            val card = UiDesign.card(this, 12).apply { tag = "vault-custom:" + field.id }
            card.addView(UiDesign.heading(this, field.name).apply { textSize = 16f },
                UiDesign.spaceAfter(this, 4))
            card.addView(UiDesign.caption(this, getString(R.string.ux10_vault_custom_summary,
                kindName(field.kind), sensitivityName(field.sensitivity))), UiDesign.spaceAfter(this, 8))
            card.addView(UiDesign.action(this, getString(R.string.ux10_vault_replace)) {
                editCustom(field)
            }, UiDesign.spaceAfter(this, 8))
            card.addView(UiDesign.action(this, getString(R.string.ux10_vault_remove),
                UiActionTone.REVOCATION) {
                confirmRemoval(field.name) {
                    runCatching { custom.remove(field.id) }
                        .onSuccess { render() }.onFailure(::showError)
                }
            })
            body.addView(card, UiDesign.spaceAfter(this, 10))
        }
        body.addView(UiDesign.action(this, getString(R.string.ux10_vault_new_custom),
            UiActionTone.PRIMARY) { editCustom(null) },
            UiDesign.spaceAfter(this, 12))
        scroll.post { scroll.scrollTo(0, savedY) }
    }

    private fun showReadError() {
        body.addView(UiDesign.caption(this, getString(R.string.ux07_agent_load_error)))
    }

    private fun privateInput(hint: String, isNumeric: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        minHeight = UiDesign.dp(this@IdentityVaultManagerActivity, 50)
        inputType = if (isNumeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        isSaveFromParentEnabled = false
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }

    private fun editStandard(field: IdentityFieldId) {
        val value = privateInput(getString(R.string.ux10_vault_value_hint),
            field == IdentityFieldId.PESEL || field == IdentityFieldId.PHONE)
        val wrap = dialogBody().apply { addView(value) }
        AlertDialog.Builder(this).setTitle(labelFor(field)).setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ux10_vault_save) { _, _ ->
                val raw = value.text.toString().trim()
                runCatching {
                    val prepared = when (field) {
                        IdentityFieldId.PHONE ->
                            PhoneEnrollmentInputNormalizer.normalize(raw)
                                ?: throw IllegalArgumentException("invalid_phone")
                        IdentityFieldId.DATE_OF_BIRTH -> {
                            java.time.LocalDate.parse(raw).toString()
                        }
                        IdentityFieldId.PESEL -> {
                            require(raw.matches(Regex("\\d{11}"))) { "invalid_pesel" }
                            raw
                        }
                        else -> raw
                    }
                    predefined.put(field, IdentitySecretValue.of(prepared)).getOrThrow()
                }.onSuccess { render() }.onFailure(::showError)
            }.create().apply {
                setOnDismissListener { value.text.clear() }
                show()
            }
    }

    private fun editCustom(field: CustomVaultField?) {
        val name = EditText(this).apply {
            hint = getString(R.string.ux10_vault_custom_name)
            setSingleLine(true)
            setText(field?.name.orEmpty())
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val kinds = CustomVaultKind.entries.toList()
        val sensitivity = IdentitySensitivity.entries.toList()
        val kindPicker = Spinner(this).apply {
            UiSelect.setOptions(this, kinds.map {
                getString(when (it) {
                    CustomVaultKind.TEXT -> R.string.ux10_vault_text
                    CustomVaultKind.NUMBER -> R.string.ux10_vault_number
                    CustomVaultKind.DATE -> R.string.ux10_vault_date
                    CustomVaultKind.SECRET -> R.string.ux10_vault_secret
                })
            })
            setSelection(kinds.indexOf(field?.kind).coerceAtLeast(0))
        }
        val sensitivityPicker = Spinner(this).apply {
            UiSelect.setOptions(this, sensitivity.map {
                getString(when (it) {
                    IdentitySensitivity.NORMAL -> R.string.ux10_vault_normal
                    IdentitySensitivity.SENSITIVE -> R.string.ux10_vault_sensitive
                    IdentitySensitivity.HIGH -> R.string.ux10_vault_high
                })
            })
            setSelection(sensitivity.indexOf(field?.sensitivity).takeIf { it >= 0 } ?: 1)
        }
        val value = privateInput(getString(if (field == null) R.string.ux10_vault_value_hint
            else R.string.ux10_vault_keep_existing))
        val wrap = dialogBody().apply {
            addView(UiDesign.caption(this@IdentityVaultManagerActivity, getString(R.string.ux10_vault_custom_name)))
            addView(name)
            addView(UiDesign.caption(this@IdentityVaultManagerActivity, getString(R.string.ux10_vault_kind)))
            addView(kindPicker)
            addView(UiDesign.caption(this@IdentityVaultManagerActivity, getString(R.string.ux10_vault_sensitivity)))
            addView(sensitivityPicker)
            addView(UiDesign.caption(this@IdentityVaultManagerActivity, getString(R.string.ux10_vault_new_value)))
            addView(value)
        }
        AlertDialog.Builder(this).setTitle(getString(if (field == null)
            R.string.ux10_vault_new_custom else R.string.ux10_vault_replace))
            .setView(ScrollView(this).apply { addView(wrap) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ux10_vault_save) { _, _ ->
                runCatching {
                    custom.upsert(field?.id, name.text.toString(),
                        kinds[kindPicker.selectedItemPosition],
                        sensitivity[sensitivityPicker.selectedItemPosition],
                        value.text.toString().takeIf { it.isNotBlank() })
                }.onSuccess { render() }.onFailure(::showError)
            }.create().apply {
                setOnDismissListener { name.text.clear(); value.text.clear() }
                show()
            }
    }

    private fun dialogBody(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = UiDesign.dp(this@IdentityVaultManagerActivity, 20)
        setPadding(pad, pad, pad, pad)
    }

    private fun confirmRemoval(label: String, remove: () -> Unit) {
        AlertDialog.Builder(this).setTitle(getString(R.string.ux10_vault_remove))
            .setMessage(getString(R.string.ux10_vault_remove_confirm, label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ux10_vault_remove) { _, _ -> remove() }.show()
    }

    private fun showError(error: Throwable) {
        // No exception details or private value in a log or UI.
        Toast.makeText(this, getString(R.string.ux10_vault_error), Toast.LENGTH_LONG).show()
    }
}
