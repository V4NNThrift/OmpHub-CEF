package id.omphub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater: cek release terbaru di repo publik V4NNThrift/OmpHub-CEF,
 * unduh APK-nya, lalu install menimpa aplikasi yang terpasang.
 *
 * Penamaan tag release PENTING: updater hanya mengenali tag berbentuk versi
 * `vX.Y.Z` (mis. `v1.3.0`). Tag lain (mis. `cef7058-v1`) diabaikan dan dianggap
 * sudah up-to-date. Jadi rilis APK ke depannya HARUS memakai tag `vX.Y.Z`.
 */
sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val version: String, val tag: String, val downloadUrl: String, val size: Long) : UpdateCheck
    data class Downloading(val progress: Int) : UpdateCheck
    data class ReadyToInstall(val file: File) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
}

object Updater {
    private const val API_LATEST = "https://api.github.com/repos/V4NNThrift/OmpHub-CEF/releases/latest"
    private const val APK_NAME = "OmpHub-CEF-release.apk"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val state: StateFlow<UpdateCheck> = _state
    @Volatile private var autoChecked = false

    fun currentVersion(ctx: Context): String = runCatching {
        PackageInfoCompat.getPackageInfo(ctx.packageManager, ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /** Cek update. `auto = true` hanya jalan sekali per proses (cek otomatis saat aplikasi dibuka). */
    fun check(ctx: Context, auto: Boolean = false) {
        if (auto && autoChecked) return
        autoChecked = true
        if (_state.value is UpdateCheck.Checking || _state.value is UpdateCheck.Downloading) return
        _state.value = UpdateCheck.Checking
        scope.launch {
            _state.value = runCatching { fetchLatest(ctx) }
                .getOrElse { UpdateCheck.Error(it.message ?: "Gagal memeriksa update") }
        }
    }

    fun reset() {
        if (_state.value !is UpdateCheck.Downloading) _state.value = UpdateCheck.Idle
    }

    private fun fetchLatest(ctx: Context): UpdateCheck {
        val current = parseVersion(currentVersion(ctx)) ?: return UpdateCheck.UpToDate
        val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "OmpHub/1.0")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode !in 200..299) return UpdateCheck.Error("GitHub HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            // Tag bukan vX.Y.Z (mis. cef7058-v1) -> bukan rilis versi, anggap up-to-date.
            val latest = parseVersionTag(tag) ?: return UpdateCheck.UpToDate
            if (compareVersions(latest, current) <= 0) return UpdateCheck.UpToDate
            val assets = json.optJSONArray("assets")
                ?: return UpdateCheck.Error("Tidak ada asset di release $tag")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == APK_NAME) {
                    return UpdateCheck.Available(
                        version = latest.joinToString("."),
                        tag = tag,
                        downloadUrl = a.optString("browser_download_url"),
                        size = a.optLong("size", 0)
                    )
                }
            }
            return UpdateCheck.Error("Asset $APK_NAME tidak ada di release $tag")
        } finally {
            conn.disconnect()
        }
    }

    fun downloadAndInstall(ctx: Context) {
        val avail = _state.value as? UpdateCheck.Available ?: return
        _state.value = UpdateCheck.Downloading(0)
        scope.launch {
            val dest = File(ctx.cacheDir, "update.apk")
            try {
                downloadFile(avail.downloadUrl, dest) { done, total ->
                    if (total > 0) _state.value = UpdateCheck.Downloading((done * 100 / total).toInt())
                }
                _state.value = UpdateCheck.ReadyToInstall(dest)
                withContext(Dispatchers.Main) { installApk(ctx, dest) }
            } catch (e: Exception) {
                _state.value = UpdateCheck.Error("Unduhan gagal: ${e.message}")
            }
        }
    }

    /** Buka installer sistem untuk [apk]. Kalau belum diizinkan, buka halaman izin install. */
    fun installApk(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                ctx,
                "Izinkan 'Install aplikasi tak dikenal' untuk OmpHub dulu (pengaturan akan dibuka).",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ctx.startActivity(intent)
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun parseVersionTag(tag: String): List<Int>? {
        val m = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(tag.trim()) ?: return null
        return m.groupValues.drop(1).map { it.toInt() }
    }

    private fun parseVersion(v: String): List<Int>? {
        val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(v.trim()) ?: return null
        return m.groupValues.drop(1).map { it.toInt() }
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until 3) {
            val d = a[i] - b[i]
            if (d != 0) return d
        }
        return 0
    }
}
