package pl.michalmatu.aicallbridge.textagent

import pl.michalmatu.aicallbridge.ui.AppTab
import pl.michalmatu.aicallbridge.ui.UiChromeActivity
import pl.michalmatu.aicallbridge.ui.UiDesign
import pl.michalmatu.aicallbridge.ui.UiActionTone
import pl.michalmatu.aicallbridge.R
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/**
 * Local-only model management. Cards present the current state first and show only
 * actions which are currently meaningful. Original transfer and selection gates stay intact.
 */
internal class LanguageModelActivity : UiChromeActivity(AppTab.SETTINGS) {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingImport: DialogueModelId? = null
    private lateinit var feedback: TextView
    private lateinit var cancelTransfer: Button
    private var lastTransfer: TransferStatus? = null
    private val rows = mutableListOf<Row>()
    private val refresh = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 500) }
    }
    private data class Row(
        val model: LanguageModelEntry,
        val card: LinearLayout,
        val state: TextView,
        val progress: ProgressBar,
        val download: Button,
        val import: Button,
        val select: Button,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingImport = savedInstanceState?.getString("pending_import")?.let { name ->
            DialogueModelId.entries.firstOrNull { it.name == name }
        }
        val pad = UiDesign.dp(this, UiDesign.CONTENT_INSET)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        content.addView(UiDesign.heading(this, "Modele językowe").apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this,
            "Pobierz lub zaimportuj model, a następnie wybierz go do kolejnych rozmów."
        ), UiDesign.spaceAfter(this, 16))

        feedback = UiDesign.caption(this, "").apply {
            tag = "model-transfer-feedback"
            visibility = View.GONE
        }
        content.addView(feedback, UiDesign.spaceAfter(this, 8))
        cancelTransfer = UiDesign.action(this, "Anuluj transfer", UiActionTone.DANGER) {
            if (LanguageModelTransferService.status.busy) startService(
                Intent(this@LanguageModelActivity, LanguageModelTransferService::class.java)
                    .setAction(LanguageModelTransferService.CANCEL))
        }.apply { tag = "model-transfer-cancel"; visibility = View.GONE }
        content.addView(cancelTransfer, UiDesign.spaceAfter(this, 12))

        for (model in LanguageModelCatalog.entries) {
            val card = UiDesign.card(this).apply {
                tag = "model-card:" + model.id.name
            }
            card.addView(UiDesign.heading(this, model.title).apply {
                textSize = 17f
            }, UiDesign.spaceAfter(this, 6))
            val stateLabel = UiDesign.caption(this, "").apply {
                tag = "model-state:" + model.id.name
            }
            card.addView(stateLabel, UiDesign.spaceAfter(this, 12))

            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = View.GONE
            }
            card.addView(progress, UiDesign.spaceAfter(this, 8))
            val download = UiDesign.action(this, "Pobierz", UiActionTone.PRIMARY) {
                begin(model, null)
            }.apply { tag = "model-download:" + model.id.name }
            val import = UiDesign.action(this, "Importuj") {
                pendingImport = model.id
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }, IMPORT)
                } catch (_: android.content.ActivityNotFoundException) {
                    pendingImport = null
                    feedback.text = "Brak aplikacji do wyboru pliku."
                    feedback.visibility = View.VISIBLE
                }
            }.apply { tag = "model-import:" + model.id.name }
            val select = UiDesign.action(this, "Użyj modelu", UiActionTone.PRIMARY) {
                val store = AudioEngineProfileStore(this@LanguageModelActivity)
                val current = store.load()
                if (current == null) {
                    feedback.text = "Napraw konfigurację silników audio przed wyborem modelu."
                } else if (AndroidGemma4ModelReadiness.check(this@LanguageModelActivity, model.id)
                        .state != Gemma4ModelReadinessState.READY) {
                    feedback.text = "Najpierw zainstaluj model."
                } else {
                    feedback.text = if (store.save(current.copy(dialogue = model.id)))
                        "Wybrano ${model.title} na następną rozmowę." else
                        "Błąd zapisu wyboru modelu."
                }
                feedback.visibility = View.VISIBLE
                update()
            }.apply { tag = "model-select:" + model.id.name; visibility = View.GONE }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(download, LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = UiDesign.dp(this@LanguageModelActivity, UiDesign.GAP_ACTION)
                })
                addView(import, LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            card.addView(actions, UiDesign.spaceAfter(this, 8))
            card.addView(select, UiDesign.spaceAfter(this, 8))
            val detail = UiDesign.caption(this,
                "${model.detail}\n${checkNotNull(model.spec.expectedBytes) / 1_000_000} MB · " +
                    "LiteRT-LM ${LanguageModelCatalog.RUNTIME_VERSION} · " +
                    "${model.source.repositoryId} · Apache 2.0"
            ).apply { visibility = View.GONE; tag = "model-details:" + model.id.name }
            val toggle = UiDesign.action(this, "Szczegóły techniczne") {
                val expanded = detail.visibility != View.VISIBLE
                detail.visibility = if (expanded) View.VISIBLE else View.GONE
            }.apply { tag = "model-details-toggle:" + model.id.name }
            card.addView(toggle, UiDesign.spaceAfter(this, 8))
            card.addView(detail)
            content.addView(card, UiDesign.spaceAfter(this, UiDesign.GAP_SECTION))
            rows += Row(model, card, stateLabel, progress, download, import, select)
        }
        setContentView(ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        update()
    }

    private fun begin(model: LanguageModelEntry, uri: android.net.Uri?) {
        if (LanguageModelTransferService.status.busy) return
        startForegroundService(Intent(this, LanguageModelTransferService::class.java)
            .putExtra(LanguageModelTransferService.MODEL, model.id.name)
            .putExtra(LanguageModelTransferService.URI, uri?.toString())
            .apply { if (uri != null) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
    }

    private fun update() {
        val transfer = LanguageModelTransferService.status
        if (transfer != lastTransfer && transfer.message.isNotEmpty()) {
            feedback.text = transfer.message
            feedback.visibility = View.VISIBLE
        }
        lastTransfer = transfer
        cancelTransfer.visibility = if (transfer.busy) View.VISIBLE else View.GONE
        val selected = AudioEngineProfileStore(this).load()?.dialogue
        for (row in rows) {
            val ready = AndroidGemma4ModelReadiness.check(this, row.model.id).state ==
                Gemma4ModelReadinessState.READY
            val isActive = ready && selected == row.model.id
            row.state.text = getString(when {
                isActive -> R.string.models_ui_active
                ready -> R.string.models_ui_ready
                else -> R.string.models_ui_not_installed
            })
            row.state.setTextColor(if (isActive) UiDesign.primary else UiDesign.secondary)
            row.state.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            row.card.background = UiDesign.surfaceDrawable(this, if (isActive) UiDesign.softAccent else UiDesign.surface, 16)
            row.card.isActivated = isActive
            row.download.visibility = if (ready) View.GONE else View.VISIBLE
            row.import.visibility = if (ready) View.GONE else View.VISIBLE
            row.download.isEnabled = !transfer.busy
            row.import.isEnabled = !transfer.busy
            row.select.visibility = if (ready && selected != row.model.id)
                View.VISIBLE else View.GONE
            row.select.isEnabled = !transfer.busy
            val active = transfer.busy && transfer.id == row.model.id
            row.progress.visibility = if (active) View.VISIBLE else View.GONE
            row.progress.progress = if (transfer.id == row.model.id)
                (transfer.bytes * 100 / checkNotNull(row.model.spec.expectedBytes))
                    .toInt().coerceIn(0, 100) else 0
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT) return
        val id = pendingImport
        pendingImport = null
        if (resultCode == RESULT_OK && id != null && data?.data != null) {
            val uri = checkNotNull(data.data)
            if (data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            begin(LanguageModelCatalog[id], uri)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_import", pendingImport?.name)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    companion object { private const val IMPORT = 1 }
}
