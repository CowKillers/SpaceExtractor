package com.manus.spaceextractor

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class EntryInfo(
    val name: String,
    val compressed: Long,
    val uncompressed: Long,
    val crc: Long,
    val method: Int,
    val localOffset: Long? = null,
    val directory: Boolean = false
)

data class Analysis(
    val name: String,
    val compressed: Long,
    val uncompressed: Long,
    val entries: List<EntryInfo>,
    val free: Long,
    val destructivePossible: Boolean,
    val reason: String?
)

object ZipEngine {
    private const val BUF = 1024 * 1024

    fun analyze(resolver: ContentResolver, uri: Uri): Analysis {
        val name = resolver.query(
            uri,
            arrayOf("_display_name"),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else uri.lastPathSegment ?: "arquivo"
        } ?: (uri.lastPathSegment ?: "arquivo")

        val entries = ArrayList<EntryInfo>()
        var compressed = 0L
        var uncompressed = 0L

        resolver.openInputStream(uri)?.use { rawInput ->
            ZipInputStream(BufferedInputStream(rawInput, BUF)).use { zis ->
                val buffer = ByteArray(BUF)
                while (true) {
                    val zipEntry = zis.nextEntry ?: break
                    val actualSize = if (zipEntry.isDirectory) 0L
                    else countEntryBytes(zis, buffer)
                    val entrySize = actualSize.takeIf { it > 0L }
                        ?: zipEntry.size.coerceAtLeast(0L)
                    val info = EntryInfo(
                        name = zipEntry.name,
                        compressed = zipEntry.compressedSize.coerceAtLeast(0L),
                        uncompressed = entrySize,
                        crc = zipEntry.crc,
                        method = zipEntry.method,
                        directory = zipEntry.isDirectory
                    )
                    entries += info
                    compressed += info.compressed
                    uncompressed += info.uncompressed
                    zis.closeEntry()
                }
            }
        } ?: throw IOException("Não foi possível abrir o arquivo.")

        require(entries.isNotEmpty()) { "O arquivo ZIP não contém entradas extraíveis." }
        return Analysis(
            name,
            compressed,
            uncompressed,
            entries,
            StatFs(Environment.getDataDirectory().path).availableBytes,
            false,
            "O arquivo será extraído para a pasta privada do aplicativo."
        )
    }

    /** Extrai diretamente para Download/SpaceExtractor, sem seletor de pasta. */
    fun extractToPublic(
        resolver: ContentResolver,
        uri: Uri,
        progress: (entry: EntryInfo, entryDone: Long, index: Int, overallDone: Long) -> Unit,
        isCancelled: () -> Boolean = { false }
    ) {
        resolver.openInputStream(uri)?.use { rawInput ->
            ZipInputStream(BufferedInputStream(rawInput, BUF)).use { zis ->
                val buffer = ByteArray(BUF)
                var index = 0
                var overallDone = 0L
                while (true) {
                    check(!isCancelled()) { "Extração cancelada pelo usuário." }
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) {
                        // A pasta é criada implicitamente pelo RELATIVE_PATH do MediaStore.
                    } else {
                        PublicStorage.createOutput(resolver, entry.name).use { target ->
                            val fileOutput = target.stream
                            var entryDone = 0L
                            while (true) {
                                check(!isCancelled()) { "Extração cancelada pelo usuário." }
                                val read = zis.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                fileOutput.write(buffer, 0, read)
                                entryDone += read
                                overallDone += read
                                progress(
                                    EntryInfo(
                                        entry.name,
                                        entry.compressedSize.coerceAtLeast(0L),
                                        entry.size.takeIf { it >= 0L } ?: entryDone,
                                        entry.crc,
                                        entry.method
                                    ),
                                    entryDone,
                                    index,
                                    overallDone
                                )
                            }
                            if (entry.size >= 0L && entryDone != entry.size) {
                                throw IOException("Tamanho inválido para ${entry.name}")
                            }
                        }
                    }
                    zis.closeEntry()
                    index++
                }
            }
        } ?: throw IOException("Não foi possível abrir o arquivo.")
    }

    private fun countEntryBytes(input: ZipInputStream, buffer: ByteArray): Long {
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) total += read
        }
        return total
    }

    fun archiveFolderName(name: String): String {
        val clean = name.substringBeforeLast('.', name)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
        return clean.ifBlank { "arquivo_extraido" }
    }

    private fun safeChild(root: File, name: String): File {
        val normalized = name.replace('\\', '/').removePrefix("/")
        val out = File(root, normalized).canonicalFile
        val canonicalRoot = root.canonicalFile
        require(
            out.path == canonicalRoot.path ||
                out.path.startsWith(canonicalRoot.path + File.separator)
        ) { "Entrada ZIP insegura: $name" }
        return out
    }

    fun extractDestructive(
        file: File,
        dest: File,
        session: SessionState,
        progress: (EntryInfo, Long, Int, Long) -> Unit
    ) {
        require(file.isFile && file.canWrite()) { "O arquivo não é local e gravável." }
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList().map { entry ->
                EntryInfo(
                    entry.name,
                    entry.compressedSize,
                    entry.size,
                    entry.crc,
                    entry.method,
                    null,
                    entry.isDirectory
                )
            }
            var overallDone = 0L
            entries.forEachIndexed { index, meta ->
                val out = safeChild(dest, meta.name)
                if (meta.directory) out.mkdirs()
                else zip.getInputStream(zip.getEntry(meta.name)).use { input ->
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(BUF)
                        var entryDone = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            entryDone += read
                            overallDone += read
                            progress(meta, entryDone, index, overallDone)
                        }
                        output.fd.sync()
                    }
                }
            }
        }
    }
}

data class SessionState(
    val archive: String,
    val destination: String,
    val pending: MutableList<String>,
    val done: MutableList<String>,
    var currentSize: Long,
    var lastSafe: Long,
    var state: String
)

object SessionStore {
    private fun file(context: android.content.Context) =
        File(context.filesDir, "extraction_session.json")

    fun save(session: SessionState) {
        file(App.context).writeText(
            """{"archive":"${session.archive.replace("\"", "\\\"")}","destination":"${session.destination.replace("\"", "\\\"")}","state":"${session.state}","lastSafe":${session.lastSafe}}"""
        )
    }

    fun exists(context: android.content.Context): Boolean = file(context).exists()
}

object App {
    lateinit var context: android.content.Context
}
