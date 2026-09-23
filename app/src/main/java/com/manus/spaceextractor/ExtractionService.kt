package com.manus.spaceextractor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class ExtractionService : Service() {
    companion object {
        const val ACTION_PROGRESS = "com.manus.spaceextractor.EXTRACTION_PROGRESS"
        const val ACTION_CANCEL = "com.manus.spaceextractor.EXTRACTION_CANCEL"
        const val ACTION_CANCEL_REQUESTED = "com.manus.spaceextractor.EXTRACTION_CANCEL_REQUESTED"
        const val ACTION_CANCELLED = "com.manus.spaceextractor.EXTRACTION_CANCELLED"
        private const val NOTIFICATION_ID = 7
        private const val CHANNEL_ID = "extraction"
    }

    private val cancelRequested = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Preparando extração…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRequested.set(true)
            sendBroadcast(
                Intent(ACTION_CANCEL_REQUESTED).setPackage(packageName)
            )
            worker?.interrupt()
            update("Cancelando…", 0, allowWhenCancelling = true)
            return START_NOT_STICKY
        }

        val archiveValue = intent?.getStringExtra("archive")
        if (archiveValue.isNullOrBlank()) {
            update("Arquivo não selecionado", 0)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        cancelRequested.set(false)
        worker = Thread {
            try {
                val archiveUri = Uri.parse(archiveValue)
                val analysis = ZipEngine.analyze(contentResolver, archiveUri)
                val totalBytes = analysis.entries
                    .filterNot { it.directory }
                    .sumOf { it.uncompressed }
                if (totalBytes <= 0L) {
                    throw IllegalArgumentException(
                        "O ZIP não possui arquivos com conteúdo extraível."
                    )
                }

                val session = SessionState(
                    archive = archiveValue,
                    destination = PublicStorage.rootLabel(),
                    pending = analysis.entries.map { it.name }.toMutableList(),
                    done = mutableListOf(),
                    currentSize = 0L,
                    lastSafe = 0L,
                    state = "running"
                )
                SessionStore.save(session)
                update("Extraindo…", 0)

                var lastPercent = 0
                ZipEngine.extractToPublic(
                    resolver = contentResolver,
                    uri = archiveUri,
                    progress = { entry, entryDone, index, overallDone ->
                        val calculated = ((overallDone * 100L) / totalBytes)
                            .coerceIn(0L, 99L)
                            .toInt()
                        // Nunca permite que a porcentagem diminua quando a entrada muda.
                        lastPercent = maxOf(lastPercent, calculated)
                        val entryTotal = entry.uncompressed.takeIf { it > 0L } ?: entryDone
                        val detail = "Extraindo ${index + 1} / ${analysis.entries.size}\n" +
                            "${entry.name}\n" +
                            "${entryDone / 1_000_000} / ${entryTotal / 1_000_000} MB"
                        if (!cancelRequested.get()) {
                            update(detail, lastPercent, entry.name)
                        }
                    },
                    isCancelled = { cancelRequested.get() }
                )

                check(!cancelRequested.get()) { "Extração cancelada pelo usuário." }
                session.state = "completed"
                session.currentSize = totalBytes
                session.pending.clear()
                session.done.clear()
                session.done.addAll(analysis.entries.map { it.name })
                SessionStore.save(session)
                update("Extração concluída", 100)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            } catch (error: Throwable) {
                if (cancelRequested.get() || error is InterruptedException) {
                    sendBroadcast(
                        Intent(ACTION_CANCELLED).setPackage(packageName)
                    )
                    update("Extração cancelada", 0, allowWhenCancelling = true)
                } else {
                    update(
                        "Extração interrompida: " +
                            (error.message ?: error.javaClass.simpleName),
                        0
                    )
                }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            } finally {
                worker = null
            }
        }.also { it.start() }

        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Extração",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Space Extractor")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .build()

    private fun update(
        text: String,
        percent: Int,
        name: String = "",
        progressPercent: Int = percent,
        allowWhenCancelling: Boolean = false
    ) {
        if (cancelRequested.get() && !allowWhenCancelling) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
        sendBroadcast(
            Intent(ACTION_PROGRESS)
                .setPackage(packageName)
                .putExtra("percent", progressPercent)
                .putExtra("name", name)
        )
    }

    override fun onDestroy() {
        cancelRequested.set(true)
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
