package id.omphub

import android.system.Os
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LogBuffer(private val max: Int = 800) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    fun add(line: String) { _lines.update { (it + line).takeLast(max) } }
    fun clear() { _lines.value = emptyList() }
}

data class CmdResult(val exit: Int, val output: String) {
    val ok get() = exit == 0
}

fun ProcessBuilder.runAndCapture(timeoutSec: Long = 120): CmdResult {
    redirectErrorStream(true)
    val p = start()
    val out = p.inputStream.bufferedReader().readText()
    if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        return CmdResult(-1, out + "\n[timeout]")
    }
    return CmdResult(p.exitValue(), out)
}

private const val TOKEN_CHARS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

fun randomToken(len: Int = 20): String {
    val r = SecureRandom()
    return buildString { repeat(len) { append(TOKEN_CHARS[r.nextInt(TOKEN_CHARS.length)]) } }
}

fun chmodSafe(path: String, mode: Int) {
    try { Os.chmod(path, mode) } catch (_: Exception) { }
}

fun deleteTree(root: File) {
    val p = root.toPath()
    if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            chmodSafe(dir.toString(), 448) // 0700
            return FileVisitResult.CONTINUE
        }
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.deleteIfExists(file)
            return FileVisitResult.CONTINUE
        }
        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
            chmodSafe(file.toString(), 448)
            try { Files.newDirectoryStream(file).close() } catch (_: IOException) { throw exc }
            deleteTree(file.toFile())
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            Files.deleteIfExists(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

fun downloadFile(url: String, dest: File, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }) {
    dest.parentFile?.mkdirs()
    val part = File(dest.path + ".part")
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 20_000
    c.readTimeout = 60_000
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "OmpHub/1.0")
    try {
        val code = c.responseCode
        if (code !in 200..299) throw IOException("HTTP $code untuk $url")
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            part.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
        dest.delete()
        if (!part.renameTo(dest)) throw IOException("Gagal memindahkan file unduhan")
    } finally {
        c.disconnect()
    }
}

fun archiveNameFor(base: String, url: String): String = when {
    url.endsWith(".xz") -> "$base.tar.xz"
    url.endsWith(".gz") || url.endsWith(".tgz") -> "$base.tar.gz"
    else -> "$base.tar"
}

object Extractor {
    private fun normName(raw: String, strip: Int): String? {
        var n = raw.removePrefix("./").trim('/')
        if (strip > 0) {
            val parts = n.split('/').filter { it.isNotEmpty() }
            if (parts.size <= strip) return null
            n = parts.drop(strip).joinToString("/")
        }
        return n.takeIf { it.isNotEmpty() && it != "." }
    }

    fun extractTar(archive: File, dest: File, strip: Int = 0, onProgress: (Int) -> Unit = {}) {
        dest.mkdirs()
        val base = dest.toPath().normalize()
        BufferedInputStream(FileInputStream(archive), 1 shl 16).use { raw ->
            raw.mark(8)
            val b0 = raw.read(); val b1 = raw.read(); val b2 = raw.read()
            raw.reset()
            val stream: InputStream = when {
                b0 == 0x1f && b1 == 0x8b -> GzipCompressorInputStream(raw)
                b0 == 0xfd && b1 == 0x37 && b2 == 0x7a -> XZCompressorInputStream(raw)
                else -> raw
            }
            TarArchiveInputStream(stream).use { tar ->
                var count = 0
                while (true) {
                    val e = (tar.nextEntry ?: break) as TarArchiveEntry
                    count++
                    onProgress(count)
                    val name = normName(e.name, strip) ?: continue
                    if (e.isCharacterDevice || e.isBlockDevice || e.isFIFO) continue
                    val outPath = base.resolve(name).normalize()
                    if (!outPath.startsWith(base)) continue
                    val out = outPath.toFile()
                    val mode = e.mode and 4095
                    when {
                        e.isDirectory -> {
                            out.mkdirs()
                            chmodSafe(out.path, mode or 448)
                        }
                        e.isSymbolicLink -> {
                            out.parentFile?.mkdirs()
                            out.delete()
                            try { Os.symlink(e.linkName, out.path) } catch (_: Exception) { }
                        }
                        e.isLink -> {
                            val tn = normName(e.linkName, strip)
                            val target = if (tn != null) File(dest, tn) else null
                            if (target != null && target.isFile) {
                                out.parentFile?.mkdirs()
                                out.delete()
                                target.copyTo(out, overwrite = true)
                                chmodSafe(out.path, mode or 384)
                            }
                        }
                        else -> {
                            out.parentFile?.mkdirs()
                            out.delete()
                            FileOutputStream(out).use { tar.copyTo(it) }
                            chmodSafe(out.path, mode or 384)
                        }
                    }
                }
            }
        }
    }
}

object Zips {
    fun addDir(z: ZipOutputStream, dir: File, prefix: String) {
        dir.walkTopDown().forEach { f ->
            if (f == dir) return@forEach
            val name = prefix + "/" + f.relativeTo(dir).path.replace('\\', '/')
            if (f.isDirectory) {
                z.putNextEntry(ZipEntry("$name/")); z.closeEntry()
            } else if (f.isFile) {
                z.putNextEntry(ZipEntry(name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    fun addFile(z: ZipOutputStream, file: File, name: String) {
        z.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(z) }
        z.closeEntry()
    }

    fun unzip(zip: File, resolve: (String) -> File?) {
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zi ->
            while (true) {
                val e = zi.nextEntry ?: break
                val out = resolve(e.name) ?: continue
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                FileOutputStream(out).use { zi.copyTo(it) }
            }
        }
    }

    fun safeResolve(base: File, rel: String): File? {
        val b = base.toPath().normalize()
        val p = b.resolve(rel).normalize()
        return if (p.startsWith(b)) p.toFile() else null
    }
}
