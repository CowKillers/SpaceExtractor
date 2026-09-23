package com.manus.spaceextractor

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class EntryInfo(val name: String, val compressed: Long, val uncompressed: Long, val crc: Long, val method: Int, val localOffset: Long? = null, val directory: Boolean = false)
data class Analysis(val name: String, val compressed: Long, val uncompressed: Long, val entries: List<EntryInfo>, val free: Long, val destructivePossible: Boolean, val reason: String?)

object ZipEngine {
    private const val BUF = 1024 * 1024

    fun analyze(resolver: ContentResolver, uri: Uri): Analysis {
        val name = resolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else uri.lastPathSegment ?: "arquivo" } ?: uri.lastPathSegment ?: "arquivo"
        val entries = ArrayList<EntryInfo>(); var compressed = 0L; var uncompressed = 0L
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o arquivo." }
            ZipInputStream(BufferedInputStream(input, BUF)).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    val info = EntryInfo(e.name, e.compressedSize.coerceAtLeast(0), e.size.coerceAtLeast(0), e.crc, e.method, null, e.isDirectory)
                    entries += info; compressed += info.compressed; uncompressed += info.uncompressed; zis.closeEntry()
                }
            }
        }
        val free = StatFs(Environment.getDataDirectory().path).availableBytes
        return Analysis(name, compressed, uncompressed, entries, free, false, "O Android fornece este arquivo via SAF; o URI não garante truncamento físico seguro. O original será preservado.")
    }

    fun extractConventionalToTree(resolver: ContentResolver, uri: Uri, treeUri: Uri, progress: (EntryInfo, Long, Int) -> Unit) {
        val root = DocumentFile.fromTreeUri(App.context, treeUri) ?: error("Pasta de destino indisponível.")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input); ZipInputStream(BufferedInputStream(input, BUF)).use { zis ->
                var index = 0
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotBlank() }
                    if (parts.isEmpty()) { zis.closeEntry(); continue }
                    var parent = root
                    parts.dropLast(1).forEach { part -> parent = parent.findFile(part) ?: parent.createDirectory(part) ?: error("Não foi possível criar $part") }
                    if (entry.isDirectory) parent.createDirectory(parts.last()) else {
                        val out = parent.createFile("application/octet-stream", parts.last()) ?: error("Não foi possível criar ${entry.name}")
                        resolver.openOutputStream(out.uri).use { output ->
                            requireNotNull(output); val buf = ByteArray(BUF); var total = 0L; var n: Int
                            while (zis.read(buf).also { n = it } >= 0) { if (n == 0) continue; output.write(buf, 0, n); total += n; progress(EntryInfo(entry.name, entry.compressedSize.coerceAtLeast(0), entry.size.coerceAtLeast(0), entry.crc, entry.method), total, index) }
                            output.flush()
                            if (entry.size >= 0 && total != entry.size) throw IOException("Tamanho inválido para ${entry.name}")
                        }
                    }
                    zis.closeEntry(); index++
                }
            }
        }
    }

    /** Modo destrutivo somente para arquivo local gravável; indexa antes e trunca após fsync/validação. */
    fun extractDestructive(file: File, dest: File, session: SessionState, progress: (EntryInfo, Long, Int, Long) -> Unit) {
        require(file.isFile && file.canWrite()) { "O arquivo não é local e gravável." }
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList().map { e -> EntryInfo(e.name, e.compressedSize, e.size, e.crc, e.method, findLocalHeader(file, e), e.isDirectory) }.sortedByDescending { it.localOffset ?: -1L }
            RandomAccessFile(file, "rw").use { raf ->
                entries.forEachIndexed { index, meta ->
                    if (session.done.contains(meta.name)) return@forEachIndexed
                    val out = safeChild(dest, meta.name); val ze = zip.getEntry(meta.name) ?: error("Entrada desapareceu: ${meta.name}")
                    if (meta.directory) out.mkdirs() else {
                        out.parentFile?.mkdirs(); zip.getInputStream(ze).use { input -> FileOutputStream(out).use { output ->
                            val buf = ByteArray(BUF); var total = 0L; var n: Int
                            while (input.read(buf).also { n = it } >= 0) { if (n == 0) continue; output.write(buf, 0, n); total += n; progress(meta, total, index, raf.length()) }
                            output.fd.sync(); if (total != meta.uncompressed) throw IOException("Falha de tamanho: ${meta.name}")
                        }}
                    }
                    session.done += meta.name; session.pending.remove(meta.name); session.lastSafe = meta.localOffset ?: session.lastSafe; SessionStore.save(session)
                    val cut = meta.localOffset; if (cut != null && cut >= 0 && cut < raf.length()) { raf.fd.sync(); raf.setLength(cut); raf.fd.sync() }
                }
            }
        }
    }

    private fun safeChild(root: File, name: String): File { val out = File(root, name.replace('\\', '/').removePrefix("/")).canonicalFile; require(out.path == root.canonicalFile.path || out.path.startsWith(root.canonicalFile.path + File.separator)) { "Entrada ZIP insegura: $name" }; return out }
    private fun findLocalHeader(file: File, target: ZipEntry): Long? { RandomAccessFile(file, "r").use { r -> val sig = ByteArray(4); while (r.filePointer + 30 < r.length()) { val pos = r.filePointer; r.readFully(sig); if (sig.contentEquals(byteArrayOf(0x50,0x4b,0x03,0x04))) { r.skipBytes(22); val nl = readU16(r); val xl = readU16(r); val bytes = ByteArray(nl); r.readFully(bytes); r.skipBytes(xl); if (String(bytes, Charsets.UTF_8) == target.name) return pos; r.seek(r.filePointer + target.compressedSize.coerceAtLeast(0)) } else r.seek(pos + 1) } }; return null }
    private fun readU16(r: RandomAccessFile): Int = r.readUnsignedByte() or (r.readUnsignedByte() shl 8)
}

data class SessionState(val archive: String, val destination: String, val pending: MutableList<String>, val done: MutableList<String>, var currentSize: Long, var lastSafe: Long, var state: String)
object SessionStore { private fun file(context: android.content.Context) = File(context.filesDir, "extraction_session.json"); fun save(s: SessionState) { file(App.context).writeText("""{"archive":"${s.archive.replace("\"", "\\\"")}","destination":"${s.destination.replace("\"", "\\\"")}","done":${s.done.joinToString(prefix="[\"", postfix="\"]", separator="\",\"") { it.replace("\"", "\\\"") }},"state":"${s.state}","lastSafe":${s.lastSafe}}""") }; fun exists(context: android.content.Context) = file(context).exists() }
object App { lateinit var context: android.content.Context }
