package com.manus.spaceextractor

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * Pasta pública fixa do aplicativo.
 * Android 10+: MediaStore.Downloads/SpaceExtractor, acessível por gerenciadores.
 * Android 9 ou anterior: Download/SpaceExtractor, usando permissão de escrita.
 */
object PublicStorage {
    private const val ROOT = "SpaceExtractor"

    fun rootLabel(): String = "Download/$ROOT"

    fun createOutput(
        resolver: ContentResolver,
        relativeFile: String
    ): DestinationOutput {
        val normalized = relativeFile.replace('\\', '/').trimStart('/')
        val parent = normalized.substringBeforeLast('/', "")
        val name = normalized.substringAfterLast('/')
        require(name.isNotBlank()) { "Nome de arquivo vazio." }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (parent.isBlank()) {
                "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/"
            } else {
                "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/$parent/"
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("O Android não criou o arquivo público $name")
            val stream = resolver.openOutputStream(uri, "w")
                ?: error("Não foi possível abrir o arquivo público $name")
            return DestinationOutput(stream) {
                val done = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
        }

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).resolve(ROOT)
        val output = File(root, normalized).canonicalFile
        require(
            output.path == root.canonicalPath ||
                output.path.startsWith(root.canonicalPath + File.separator)
        ) { "Caminho de saída inválido." }
        output.parentFile?.mkdirs()
        return DestinationOutput(output.outputStream()) {}
    }

    class DestinationOutput(
        val stream: OutputStream,
        private val finish: () -> Unit
    ) : AutoCloseable {
        override fun close() {
            stream.close()
            finish()
        }
    }
}
