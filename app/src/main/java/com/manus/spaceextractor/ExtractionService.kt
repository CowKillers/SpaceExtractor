package com.manus.spaceextractor

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

class ExtractionService : Service() {
    companion object { const val ACTION_PROGRESS = "com.manus.spaceextractor.EXTRACTION_PROGRESS" }
    private val channel = "extraction"
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(7, notification("Preparando extração…")) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val a = intent?.getStringExtra("archive") ?: return START_NOT_STICKY
        val d = intent.getStringExtra("destination") ?: return START_NOT_STICKY
        Thread {
            try {
                val archiveUri = Uri.parse(a); val destUri = Uri.parse(d)
                val analysis = ZipEngine.analyze(contentResolver, archiveUri)
                val session = SessionState(a, d, analysis.entries.map { it.name }.toMutableList(), mutableListOf(), 0, 0, "running")
                SessionStore.save(session)
                ZipEngine.extractConventionalToTree(contentResolver, archiveUri, destUri) { e, done, index ->
                    val total = analysis.entries.size.coerceAtLeast(1)
                    val percent = (((index.toDouble() + (if (e.uncompressed > 0) done.toDouble() / e.uncompressed else 1.0)) / total) * 100.0).toInt().coerceIn(0, 99)
                    update("Extraindo ${index + 1} / $total\n${e.name}\n${done / 1_000_000} MB", percent, e.name)
                }
                session.state = "completed"; SessionStore.save(session); update("Extração concluída", 100)
                stopForeground(STOP_FOREGROUND_DETACH); stopSelf()
            } catch (t: Throwable) {
                update("Extração interrompida: ${t.message ?: "erro desconhecido"}")
                stopForeground(STOP_FOREGROUND_DETACH); stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }
    private fun createChannel() { if (android.os.Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Extração", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(text: String) = NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Space Extractor").setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOngoing(true).build()
    private fun update(text: String, percent: Int = 0, name: String = "") { getSystemService(NotificationManager::class.java).notify(7, notification(text)); sendBroadcast(Intent(ACTION_PROGRESS).putExtra("percent", percent).putExtra("name", name)) }
    override fun onBind(intent: Intent?): IBinder? = null
}
