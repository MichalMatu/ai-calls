package pl.michalmatu.aicallbridge.textagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfileStore
import pl.michalmatu.aicallbridge.audioengines.DialogueModelId

/** Catalog, verified acquisition and next-call selection share one screen and one profile store. */
internal class LanguageModelActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingImport: DialogueModelId? = null
    private lateinit var feedback: TextView
    private var lastTransfer: TransferStatus? = null
    private val rows = mutableListOf<Row>()
    private val refresh = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 500) }
    }
    private data class Row(val model: LanguageModelEntry, val label: TextView,
        val progress: ProgressBar, val download: Button, val import: Button, val select: Button)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingImport = savedInstanceState?.getString("pending_import")?.let { name ->
            DialogueModelId.entries.firstOrNull { it.name == name }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 36) }
        fun label(value: String) = TextView(this).apply { text = value; textSize = 16f; setPadding(0, 16, 0, 12) }
        content.addView(label("Modele językowe · LiteRT-LM ${LanguageModelCatalog.RUNTIME_VERSION}"))
        content.addView(label("Pobierz model lub importuj plik .litertlm. Instalacja sprawdza rozmiar i SHA-256. " +
            "Wybór obowiązuje od następnej rozmowy; wejście STT i wyjście TTS zachowują swoje ustawienia."))
        feedback = label("")
        content.addView(feedback)
        content.addView(Button(this).apply {
            text = "Anuluj transfer"
            setOnClickListener {
                if (LanguageModelTransferService.status.busy) startService(
                    Intent(this@LanguageModelActivity, LanguageModelTransferService::class.java)
                        .setAction(LanguageModelTransferService.CANCEL))
            }
        })
        for (model in LanguageModelCatalog.entries) {
            val title = label("")
            content.addView(title)
            content.addView(label("${model.detail}\n${checkNotNull(model.spec.expectedBytes) / 1_000_000} MB · Apache 2.0 · ${model.source.repositoryId}"))
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            content.addView(progress)
            val download = Button(this).apply {
                text = "Pobierz ${model.title}"
                setOnClickListener { begin(model, null) }
            }
            val import = Button(this).apply {
                text = "Importuj ${model.title} z pliku"
                setOnClickListener {
                    pendingImport = model.id
                    try {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        }, IMPORT)
                    } catch (_: android.content.ActivityNotFoundException) {
                        pendingImport = null; feedback.text = "Brak aplikacji do wyboru pliku."
                    }
                }
            }
            val select = Button(this).apply {
                text = "Użyj ${model.title}"
                setOnClickListener {
                    val store = AudioEngineProfileStore(this@LanguageModelActivity)
                    val current = store.load()
                    if (current == null) feedback.text = "Napraw konfigurację silników audio przed wyborem modelu."
                    else if (AndroidGemma4ModelReadiness.check(this@LanguageModelActivity, model.id).state != Gemma4ModelReadinessState.READY)
                        feedback.text = "Najpierw zainstaluj model."
                    else feedback.text = if (store.save(current.copy(dialogue = model.id)))
                        "Wybrano ${model.title} na następną rozmowę." else "Błąd zapisu wyboru modelu."
                    update()
                }
            }
            content.addView(download); content.addView(import); content.addView(select)
            rows += Row(model, title, progress, download, import, select)
        }
        setContentView(ScrollView(this).apply { addView(content, ViewGroup.LayoutParams(-1, -2)) })
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
        val state = LanguageModelTransferService.status
        if (state != lastTransfer && state.message.isNotEmpty()) feedback.text = state.message
        lastTransfer = state
        val selected = AudioEngineProfileStore(this).load()?.dialogue
        for (row in rows) {
            val ready = AndroidGemma4ModelReadiness.check(this, row.model.id).state == Gemma4ModelReadinessState.READY
            row.label.text = "${row.model.title} · ${if (ready) "zainstalowany" else "brak modelu"}" +
                if (selected == row.model.id) " · WYBRANY" else ""
            row.download.isEnabled = !state.busy && !ready
            row.import.isEnabled = !state.busy && !ready
            row.select.isEnabled = ready && selected != row.model.id
            row.progress.progress = if (state.id == row.model.id)
                (state.bytes * 100 / checkNotNull(row.model.spec.expectedBytes)).toInt().coerceIn(0, 100) else 0
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
        outState.putString("pending_import", pendingImport?.name); super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    companion object { private const val IMPORT = 1 }
}
