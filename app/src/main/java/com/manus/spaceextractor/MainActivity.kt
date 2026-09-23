package com.manus.spaceextractor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var archive: Uri? = null
    private lateinit var status: TextView
    private lateinit var analyze: TextView
    private lateinit var extract: TextView
    private lateinit var cancel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var percent: TextView
    private lateinit var progressLabel: TextView
    private var receiverRegistered = false

    private val pickArchive = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        archive = uri
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        status.text = "Arquivo selecionado: ${uri.lastPathSegment}\n" +
            "Toque em ANALISAR. Destino: ${PublicStorage.rootLabel()}"
        analyze.isEnabled = true
        refreshCard(analyze)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        App.context = applicationContext
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        val frame = FrameLayout(this)
        frame.addView(SpaceBackgroundView(this), FrameLayout.LayoutParams(-1, -1))
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(18))
            clipToPadding = false
        }
        frame.addView(column, FrameLayout.LayoutParams(-1, -1))
        frame.addView(
            NeonBorderView(this),
            FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = 0
                topMargin = 0
            }
        )
        setContentView(frame)

        val kicker = text(
            "ARQUIVOS EM ÓRBITA  •  ESPAÇO MÍNIMO",
            11f,
            0xff78eaff.toInt()
        ).apply { letterSpacing = .22f }
        column.addView(kicker)
        val appTitle = text("Space\nExtractor", 43f, Color.WHITE).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setShadowLayer(18f, 0f, 0f, 0xff00d9ff.toInt())
        }
        column.addView(
            text("Extração segura com uso\nmínimo de espaço", 19f, 0xffd5e8ff.toInt()),
            lp(0, 12)
        )
        column.addView(
            text("MAIS ESPAÇO\nPARA O QUE IMPORTA", 11f, 0xff99b7da.toInt())
                .apply { letterSpacing = .27f },
            lp(0, 16)
        )

        val choose = card(
            "▣",
            "SELECIONAR ZIP / ZIP64 / RAR",
            "Escolha o arquivo compactado",
            true
        ) {
            pickArchive.launch(
                arrayOf(
                    "application/zip",
                    "application/x-rar-compressed",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
        analyze = card(
            "⌕",
            "ANALISAR",
            "Verifique o conteúdo do arquivo",
            false
        ) { analyzeArchive() }
        extract = card(
            "↓",
            "EXTRAIR",
            "Iniciar extração dos arquivos",
            false
        ) { startExtraction() }
        cancel = iconButton("❌", "Cancelar extração") { cancelExtraction() }

        analyze.isEnabled = false
        extract.isEnabled = false
        cancel.isEnabled = false
        refreshCard(analyze)
        refreshCard(extract)
        refreshCard(cancel)

        val headerIcons = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        headerIcons.addView(cancel, LinearLayout.LayoutParams(dp(52), dp(44)))
        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        titleRow.addView(appTitle, LinearLayout.LayoutParams(0, -2, 1f).apply {
            topMargin = dp(4)
        })
        titleRow.addView(headerIcons, LinearLayout.LayoutParams(-2, -2))
        column.addView(titleRow, 1)

        column.addView(choose)
        column.addView(analyze)
        column.addView(extract)

        val progressBox = panel()
        progressLabel = text("PROGRESSO DA EXTRAÇÃO", 11f, 0xff8faed1.toInt())
            .apply { letterSpacing = .16f }
        percent = text("0%", 30f, 0xff61edff.toInt()).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(
                0xff25e7ff.toInt()
            )
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                0xff263a5b.toInt()
            )
        }
        progressBox.addView(progressLabel)
        progressBox.addView(percent, lp(0, 2))
        progressBox.addView(
            progress,
            LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) }
        )
        // Neste ponto a coluna contém cabeçalho, ZIP, análise e extração.
        // Inserir diretamente evita índice inválido após remover o diretório.
        column.addView(progressBox)

        status = text(
            "Selecione um arquivo compactado.",
            15f,
            0xffd9e8ff.toInt()
        )
        column.addView(status, lp(0, 12))
        column.addView(
            text("PEQUENOS ARQUIVOS\nMOVEM GRANDES POSSIBILIDADES", 10f, 0xff7797bb.toInt())
                .apply { letterSpacing = .2f },
            lp(0, 24)
        )

        if (SessionStore.exists(this)) {
            status.text = "Extração interrompida encontrada.\n" +
                "Bytes já removidos não podem ser restaurados."
        }

        val filter = IntentFilter(ExtractionService.ACTION_PROGRESS).apply {
            addAction(ExtractionService.ACTION_CANCEL_REQUESTED)
            addAction(ExtractionService.ACTION_CANCELLED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(progressReceiver, filter)
        }
        receiverRegistered = true
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ExtractionService.ACTION_CANCEL_REQUESTED) {
                cancel.isEnabled = false
                extract.isEnabled = false
                refreshCard(cancel)
                refreshCard(extract)
                status.text = "Cancelando extração…"
                return
            }
            if (intent?.action == ExtractionService.ACTION_CANCELLED) {
                cancel.isEnabled = false
                extract.isEnabled = archive != null
                refreshCard(cancel)
                refreshCard(extract)
                status.text = "Extração cancelada pelo usuário."
                return
            }

            val incoming = intent?.getIntExtra("percent", 0) ?: 0
            val current = progress.progress
            // A UI nunca volta para trás quando uma nova entrada do ZIP começa.
            val p = maxOf(current, incoming).coerceIn(0, 100)
            val name = intent?.getStringExtra("name") ?: ""
            progress.progress = p
            percent.text = "$p%"
            progressLabel.text = if (name.isBlank()) {
                "PROGRESSO DA EXTRAÇÃO"
            } else {
                "EXTRAINDO  •  $name"
            }

            if (p > 0 && p < 100) {
                cancel.isEnabled = true
                refreshCard(cancel)
            }
            if (p >= 100) {
                cancel.isEnabled = false
                extract.isEnabled = false
                refreshCard(cancel)
                refreshCard(extract)
                status.text = "✓ Extração concluída com sucesso.\n" +
                    "Arquivos salvos em ${PublicStorage.rootLabel()}."
            }
        }
    }

    private fun analyzeArchive() {
        val uri = archive ?: return
        analyze.isEnabled = false
        refreshCard(analyze)
        status.text = "Analisando índice completo…"
        Thread {
            try {
                val analysis = ZipEngine.analyze(contentResolver, uri)
                val ok = analysis.uncompressed <= analysis.free + analysis.compressed
                runOnUiThread {
                    status.text = if (ok) {
                        "✓ Arquivo pronto para extração."
                    } else {
                        "⚠ Arquivo com problemas. Não pode ser extraído."
                    }
                    extract.isEnabled = ok
                    refreshCard(extract)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    analyze.isEnabled = true
                    refreshCard(analyze)
                    status.text = "⚠ Arquivo com problemas. Não pode ser extraído."
                }
            }
        }.start()
    }

    private fun startExtraction() {
        val archiveUri = archive ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                410
            )
            status.text = "Autorize o armazenamento e toque em EXTRAIR novamente."
            return
        }
        val intent = Intent(this, ExtractionService::class.java)
            .putExtra("archive", archiveUri.toString())

        progress.progress = 0
        percent.text = "0%"
        progressLabel.text = "PROGRESSO DA EXTRAÇÃO"
        cancel.isEnabled = true
        extract.isEnabled = false
        refreshCard(cancel)
        refreshCard(extract)
        startForegroundService(intent)
        status.text = "Extração iniciada. Toque em CANCELAR para interromper."
    }

    private fun cancelExtraction() {
        status.text = "Cancelando extração…"
        cancel.isEnabled = false
        extract.isEnabled = false
        refreshCard(cancel)
        refreshCard(extract)
        startService(Intent(this, ExtractionService::class.java).apply {
            action = ExtractionService.ACTION_CANCEL
        })
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(progressReceiver)
        super.onDestroy()
    }

    private fun card(
        icon: String,
        heading: String,
        detail: String,
        active: Boolean,
        action: () -> Unit
    ): TextView = TextView(this).apply {
        text = "$icon   $heading\n       $detail                                      ›"
        textSize = 15f
        setTextColor(if (active) Color.WHITE else 0xffc3d1e8.toInt())
        setPadding(dp(20), dp(18), dp(14), dp(18))
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(
            if (active) 0x66102b48 else 0x6631415b,
            if (active) 0xff24dfff.toInt() else 0xff7385a7.toInt()
        )
        isEnabled = active
        setOnClickListener { if (isEnabled) action() }
        setShadowLayer(10f, 0f, 0f, 0xff00d9ff.toInt())
    }

    private fun iconButton(
        icon: String,
        description: String,
        action: () -> Unit
    ): TextView = TextView(this).apply {
        text = icon
        textSize = 25f
        gravity = Gravity.CENTER
        contentDescription = description
        setTextColor(Color.WHITE)
        background = rounded(0x66102b48, 0xff24dfff.toInt())
        isEnabled = false
        setOnClickListener { if (isEnabled) action() }
        setShadowLayer(10f, 0f, 0f, 0xff00d9ff.toInt())
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(16))
        background = rounded(0x66112640, 0xff237da5.toInt())
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun refreshCard(view: TextView) {
        view.alpha = if (view.isEnabled) 1f else .48f
    }

    private fun text(value: String, size: Float, color: Int) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }

    private fun lp(width: Int, top: Int) =
        LinearLayout.LayoutParams(if (width == 0) -1 else width, -2).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun fmt(value: Long) =
        String.format(Locale.US, "%.2f GB", value / 1_000_000_000.0)
}
