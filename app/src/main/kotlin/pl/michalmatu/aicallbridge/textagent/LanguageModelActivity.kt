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
    /** Read-only localization of stable model catalog descriptions. */
    private fun localizedModelDetail(model: LanguageModelEntry): String = getString(when (model.id.name) {
        "GEMMA_4_E2B" -> R.string.models_ux05_detail_e2b
        "GEMMA_4_E4B" -> R.string.models_ux05_detail_e4b
        "QWEN_3_1_7B" -> R.string.models_ux05_detail_qwen17
        "QWEN_3_0_6B" -> R.string.models_ux05_detail_qwen06
        else -> return model.detail
    })

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
        content.addView(UiDesign.heading(this, getString(R.string.models_ui_title)).apply {
            textSize = 23f
        }, UiDesign.spaceAfter(this, 8))
        content.addView(UiDesign.caption(this,
            getString(R.string.models_ui_intro)
        ), UiDesign.spaceAfter(this, 16))

        feedback = UiDesign.caption(this, "").apply {
            tag = "model-transfer-feedback"
            visibility = View.GONE
        }
        content.addView(feedback, UiDesign.spaceAfter(this, 8))
        cancelTransfer = UiDesign.action(this, getString(R.string.models_ui_cancel), UiActionTone.DANGER) {
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
            val download = UiDesign.action(this, getString(R.string.models_ui_download), UiActionTone.PRIMARY) {
                begin(model, null)
            }.apply { tag = "model-download:" + model.id.name }
            val import = UiDesign.action(this, getString(R.string.models_ui_import)) {
                pendingImport = model.id
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }, IMPORT)
                } catch (_: android.content.ActivityNotFoundException) {
                    pendingImport = null
                    feedback.text = getString(R.string.models_ui_file_unavailable)
                    feedback.visibility = View.VISIBLE
                }
            }.apply { tag = "model-import:" + model.id.name }
            val select = UiDesign.action(this, getString(R.string.models_ui_select), UiActionTone.SECONDARY) {
                val store = AudioEngineProfileStore(this@LanguageModelActivity)
                val current = store.load()
                if (current == null) {
                    feedback.text = getString(R.string.models_ui_fix_engines)
                } else if (AndroidGemma4ModelReadiness.check(this@LanguageModelActivity, model.id)
                        .state != Gemma4ModelReadinessState.READY) {
                    feedback.text = getString(R.string.models_ui_install_first)
                } else {
                    feedback.text = if (store.save(current.copy(dialogue = model.id)))
                        getString(R.string.models_ui_selected, model.title) else
                        getString(R.string.models_ui_save_failed)
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
                localizedModelDetail(model) + "\n\n" +
                    getString(R.string.models_ui_detail_size,
                        checkNotNull(model.spec.expectedBytes) / 1_000_000) + "\n" +
                    getString(R.string.models_ui_detail_runtime,
                        "LiteRT-LM " + LanguageModelCatalog.RUNTIME_VERSION) + "\n" +
                    getString(R.string.models_ui_detail_source) + ":\n" +
                    model.source.repositoryId.replace("/", "/\n") + "\n" +
                    getString(R.string.models_ui_detail_license)
            ).apply {
                visibility = View.GONE
                tag = "model-details:" + model.id.name
                textSize = 13f
                setLineSpacing(UiDesign.dp(this@LanguageModelActivity, 3).toFloat(), 1f)
                setTextIsSelectable(true)
            }
            val toggle = UiDesign.action(this, getString(R.string.models_ui_details)) {
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
        val selected = AudioEngineProfileStore(this).load()?.dialogue
        val readyIds = rows.asSequence().filter { row ->
            AndroidGemma4ModelReadiness.check(this, row.model.id).state ==
                Gemma4ModelReadinessState.READY
        }.map { it.model.id }.toSet()
        val screen = LanguageModelStatusPresenter.present(
            LanguageModelCatalog.entries, selected, readyIds, transfer, lastTransfer)
        screen.newMessage?.let {
            feedback.text = it
            feedback.visibility = View.VISIBLE
        }
        lastTransfer = transfer
        cancelTransfer.visibility = if (screen.showCancel) View.VISIBLE else View.GONE
        val stateById = screen.rows.associateBy { it.id }
        for (row in rows) {
            val modelState = checkNotNull(stateById[row.model.id])
            val isActive = modelState.active
            row.state.text = getString(when (modelState.meaning) {
                LanguageModelMeaning.ACTIVE -> R.string.models_ui_active
                LanguageModelMeaning.READY -> R.string.models_ui_ready
                LanguageModelMeaning.NOT_INSTALLED -> R.string.models_ui_not_installed
            })
            row.state.setTextColor(if (isActive) UiDesign.primary else UiDesign.secondary)
            row.state.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            row.card.background = UiDesign.surfaceDrawable(this, if (isActive) UiDesign.softAccent else UiDesign.surface, 16)
            row.card.isActivated = isActive
            row.download.visibility = if (modelState.showDownload) View.VISIBLE else View.GONE
            row.import.visibility = if (modelState.showImport) View.VISIBLE else View.GONE
            row.download.isEnabled = modelState.actionsEnabled
            row.import.isEnabled = modelState.actionsEnabled
            row.select.visibility = if (modelState.showSelect) View.VISIBLE else View.GONE
            row.select.isEnabled = modelState.actionsEnabled
            row.progress.visibility = if (modelState.showProgress) View.VISIBLE else View.GONE
            row.progress.progress = modelState.progressPercent
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
