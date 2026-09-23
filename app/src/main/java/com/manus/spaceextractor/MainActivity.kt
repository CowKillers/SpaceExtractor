package com.manus.spaceextractor

import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var archive: Uri? = null
    private var destination: Uri? = null
    private lateinit var status: TextView
    private lateinit var analyze: TextView
    private lateinit var extract: TextView
    private lateinit var progress: ProgressBar
    private lateinit var percent: TextView
    private lateinit var progressLabel: TextView
    private var receiverRegistered = false

    private val pickArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        archive = uri; contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        status.text = "Arquivo selecionado: ${uri.lastPathSegment}\nEscolha o destino e toque em ANALISAR."
        analyze.isEnabled = destination != null; refreshCard(analyze)
    }
    private val pickDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        destination = uri; contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        status.text = "Destino selecionado. Toque em ANALISAR."
        analyze.isEnabled = archive != null; refreshCard(analyze)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); App.context = applicationContext
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        val frame = FrameLayout(this)
        frame.addView(SpaceBackgroundView(this), FrameLayout.LayoutParams(-1, -1))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.setPadding(dp(18), dp(26), dp(18), dp(18))
        frame.addView(column, FrameLayout.LayoutParams(-1, -1)); setContentView(frame)

        val kicker = text("ARQUIVOS EM ÓRBITA  •  ESPAÇO MÍNIMO", 11f, 0xff78eaff.toInt()).apply { letterSpacing = .22f }
        val title = text("Space\nExtractor", 43f, Color.WHITE).apply { setTypeface(typeface, android.graphics.Typeface.BOLD); setShadowLayer(18f, 0f, 0f, 0xff00d9ff.toInt()) }
        val subtitle = text("Extração segura com uso\nmínimo de espaço", 19f, 0xffd5e8ff.toInt())
        column.addView(kicker); column.addView(title, lp(0, 4)); column.addView(subtitle, lp(0, 12))
        column.addView(text("MAIS ESPAÇO\nPARA O QUE IMPORTA", 11f, 0xff99b7da.toInt()).apply { letterSpacing = .27f }, lp(0, 16))

        val choose = card("▣", "SELECIONAR ZIP / ZIP64 / RAR", "Escolha o arquivo compactado", true) { pickArchive.launch(arrayOf("application/zip", "application/x-rar-compressed", "application/octet-stream", "*/*")) }
        val folder = card("□", "SELECIONAR PASTA DE DESTINO", "Defina onde os arquivos serão extraídos", true) { pickDestination.launch(null) }
        analyze = card("⌕", "ANALISAR", "Verifique o conteúdo do arquivo", false) { analyzeArchive() }
        extract = card("↓", "EXTRAIR", "Iniciar extração dos arquivos", false) { startExtraction() }
        analyze.isEnabled = false; extract.isEnabled = false
        column.addView(choose); column.addView(folder); column.addView(analyze); column.addView(extract)

        val progressBox = panel()
        progressLabel = text("PROGRESSO DA EXTRAÇÃO", 11f, 0xff8faed1.toInt()).apply { letterSpacing = .16f }
        percent = text("0%", 30f, 0xff61edff.toInt()).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; progressTintList = android.content.res.ColorStateList.valueOf(0xff25e7ff.toInt()); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xff263a5b.toInt()) }
        progressBox.addView(progressLabel); progressBox.addView(percent, lp(0, 2)); progressBox.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) })
        column.addView(progressBox)

        status = text("Selecione um arquivo compactado.", 15f, 0xffd9e8ff.toInt()); column.addView(status, lp(0, 12))
        column.addView(text("PEQUENOS ARQUIVOS\nMOVEM GRANDES POSSIBILIDADES", 10f, 0xff7797bb.toInt()).apply { letterSpacing = .2f }, lp(0, 24))
        if (SessionStore.exists(this)) status.text = "Extração interrompida encontrada.\nBytes já removidos não podem ser restaurados."
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(progressReceiver, IntentFilter(ExtractionService.ACTION_PROGRESS), Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(progressReceiver, IntentFilter(ExtractionService.ACTION_PROGRESS))
        receiverRegistered = true
    }

    private val progressReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) {
        val p = intent?.getIntExtra("percent", 0) ?: 0; val name = intent?.getStringExtra("name") ?: ""
        progress.progress = p; percent.text = "$p%"; progressLabel.text = if (name.isBlank()) "PROGRESSO DA EXTRAÇÃO" else "EXTRAINDO  •  $name"
        if (p >= 100) status.text = "✓ Extração concluída com sucesso."
    } }
    override fun onDestroy() { if (receiverRegistered) unregisterReceiver(progressReceiver); super.onDestroy() }

    private fun analyzeArchive() { val uri = archive ?: return; analyze.isEnabled = false; refreshCard(analyze); status.text = "Analisando índice completo…"; Thread { try { val a = ZipEngine.analyze(contentResolver, uri); val ok = a.uncompressed <= a.free + a.compressed; runOnUiThread { status.text = "Arquivo: ${a.name}\nCompactado: ${fmt(a.compressed)}  •  Conteúdo: ${fmt(a.uncompressed)}\nEspaço livre: ${fmt(a.free)}\nEntradas: ${a.entries.size}\n\n" + if (ok) "✓ EXTRAÇÃO POSSÍVEL\n${a.reason ?: "Modo progressivo disponível."}" else "ESPAÇO INSUFICIENTE\nO conteúdo exige mais espaço do que o sistema pode liberar com segurança."; extract.isEnabled = ok; refreshCard(extract) } } catch (e: Exception) { runOnUiThread { status.text = "Não foi possível analisar: ${e.message}" } } } .start() }
    private fun startExtraction() { val i = Intent(this, ExtractionService::class.java).putExtra("archive", archive.toString()).putExtra("destination", destination.toString()); progress.progress = 0; percent.text = "0%"; startForegroundService(i); status.text = "Extração iniciada em segundo plano. Você pode apagar a tela; o progresso continuará na notificação." }

    private fun card(icon: String, heading: String, detail: String, active: Boolean, action: () -> Unit): TextView { return TextView(this).apply { text = "$icon   $heading\n       $detail                                      ›"; textSize = 15f; setTextColor(if (active) Color.WHITE else 0xffc3d1e8.toInt()); setPadding(dp(20), dp(18), dp(14), dp(18)); gravity = Gravity.CENTER_VERTICAL; isAllCaps = false; background = rounded(if (active) 0x66102b48 else 0x6631415b, if (active) 0xff24dfff.toInt() else 0xff7385a7.toInt()); isEnabled = active; setOnClickListener { if (isEnabled) action() }; setShadowLayer(10f, 0f, 0f, 0xff00d9ff.toInt()) } }
    private fun panel() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(16)); background = rounded(0x66112640, 0xff237da5.toInt()); }
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(fill); setStroke(dp(1), stroke) }
    private fun refreshCard(v: TextView) { v.alpha = if (v.isEnabled) 1f else .48f }
    private fun text(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun lp(w: Int, top: Int) = LinearLayout.LayoutParams(if (w == 0) -1 else w, -2).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(v: Long) = String.format(Locale.US, "%.2f GB", v / 1_000_000_000.0)
}
