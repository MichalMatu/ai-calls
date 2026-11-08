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

    /**
     * One compact touch target per entry; editing never reads the stored plaintext.
     * A separate 48dp remove affordance is shown only for fields that are present.
     */
    private fun fieldRow(
        title: String,
        subtitle: String,
        rowTag: String,
        saved: Boolean,
        onEdit: () -> Unit,
        onRemove: () -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        tag = rowTag
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        minimumHeight = UiDesign.dp(this@IdentityVaultManagerActivity, 62)
        background = UiDesign.touchSurface(this@IdentityVaultManagerActivity)
        setPadding(UiDesign.dp(this@IdentityVaultManagerActivity, 12),
            UiDesign.dp(this@IdentityVaultManagerActivity, 6),
            UiDesign.dp(this@IdentityVaultManagerActivity, 5),
            UiDesign.dp(this@IdentityVaultManagerActivity, 6))

        val labels = LinearLayout(this@IdentityVaultManagerActivity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = title + ", " + subtitle + ", " +
                getString(if (saved) R.string.ux10_vault_replace else R.string.ux10_vault_add)
            setOnClickListener { onEdit() }
            addView(UiDesign.heading(this@IdentityVaultManagerActivity, title).apply {
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(UiDesign.caption(this@IdentityVaultManagerActivity, subtitle).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(UiDesign.iconButton(this@IdentityVaultManagerActivity,
            android.R.drawable.ic_menu_edit,
            getString(R.string.ux11_vault_edit_field, title)).apply {
            tag = rowTag + ":edit"
            setOnClickListener { onEdit() }
        }, LinearLayout.LayoutParams(UiDesign.dp(this@IdentityVaultManagerActivity, 48),
            UiDesign.dp(this@IdentityVaultManagerActivity, 48)))
        if (saved) {
            addView(UiDesign.iconButton(this@IdentityVaultManagerActivity,
                android.R.drawable.ic_menu_delete,
                getString(R.string.ux11_vault_remove_field, title), UiDesign.danger).apply {
                tag = rowTag + ":remove"
                setOnClickListener { onRemove() }
            }, LinearLayout.LayoutParams(UiDesign.dp(this@IdentityVaultManagerActivity, 48),
                UiDesign.dp(this@IdentityVaultManagerActivity, 48)))
        }
    }

    private fun render() {
        val savedY = scroll.scrollY
        body.removeAllViews()
        body.addView(UiDesign.heading(this, getString(R.string.ux10_vault_title)),
            UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this, getString(R.string.ux10_vault_intro)),
            UiDesign.spaceAfter(this, 10))
        // Custom-field creation stays available without scrolling past the standard fields.
        body.addView(UiDesign.action(this, getString(R.string.ux10_vault_new_custom),
            UiActionTone.PRIMARY) { editCustom(null) }.apply {
            tag = "vault-add-custom"
        }, UiDesign.spaceAfter(this, 14))

        body.addView(UiDesign.heading(this, getString(R.string.ux10_vault_standard)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        val ids = predefined.availableFields().getOrElse {
            showReadError()
            return
        }
        for (field in IdentityFieldId.entries) {
            val present = field in ids
            body.addView(fieldRow(labelFor(field), getString(if (present)
                R.string.ux10_vault_saved else R.string.ux10_vault_empty),
                "vault-standard:" + field.name, present,
                onEdit = { editStandard(field) },
                onRemove = {
                    confirmRemoval(labelFor(field)) {
                        runCatching { predefined.remove(field).getOrThrow() }
                            .onSuccess { render() }.onFailure(::showError)
                    }
                }), UiDesign.spaceAfter(this, 6))
        }

        body.addView(UiDesign.heading(this, getString(R.string.ux10_vault_custom)).apply {
            textSize = 17f
        }, UiDesign.spaceAfter(this, 8))
        body.addView(UiDesign.caption(this, getString(R.string.ux10_vault_custom_note)),
            UiDesign.spaceAfter(this, 10))
        val own = runCatching { custom.list() }.getOrElse {
            showReadError()
            return
        }
        for (field in own) {
            body.addView(fieldRow(field.name,
                getString(R.string.ux10_vault_custom_summary,
                    kindName(field.kind), sensitivityName(field.sensitivity)),
                "vault-custom:" + field.id, saved = true,
                onEdit = { editCustom(field) },
                onRemove = {
                    confirmRemoval(field.name) {
                        runCatching { custom.remove(field.id) }
                            .onSuccess { render() }.onFailure(::showError)
                    }
                }), UiDesign.spaceAfter(this, 6))
        }
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
