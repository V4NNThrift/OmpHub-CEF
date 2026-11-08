package id.omphub

import android.app.Application
import android.content.Context
import android.system.Os
import java.io.File

class OmpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Env.init(this)
    }
}

object Env {
    lateinit var ctx: Context
        private set

    fun init(c: Context) {
        ctx = c.applicationContext
        prepareDirs()
    }

    val files: File get() = ctx.filesDir
    val nativeDir: String get() = ctx.applicationInfo.nativeLibraryDir

    val rootfsGame: File get() = File(files, "rootfs/game")
    val rootfsDb: File get() = File(files, "rootfs/db")
    val serverDir: File get() = File(files, "server")
    val dbData: File get() = File(files, "db/data")
    val dbCredFile: File get() = File(files, "db/credentials.json")
    val backupDir: File get() = File(files, "backups")
    val tmpDir: File get() = File(files, "tmp")
    val dlDir: File get() = File(files, "downloads")
    val libDir: File get() = File(files, "lib")

    fun nativeLib(name: String) = File(nativeDir, name)

    fun copyAsset(name: String, dest: File): Boolean {
        val candidates = listOfNotNull(name, name.takeIf { it.endsWith(".gz") }?.removeSuffix(".gz"))
        for (n in candidates) {
            try {
                dest.parentFile?.mkdirs()
                ctx.assets.open(n).use { input -> dest.outputStream().use { input.copyTo(it) } }
                return true
            } catch (_: java.io.FileNotFoundException) { }
        }
        dest.delete()
        return false
    }

    fun binariesOk(): Boolean =
        listOf("libproot.so", "libproot-loader.so", "libqemu-i386.so", "libtalloc.so").all { nativeLib(it).exists() }

    fun prepareDirs() {
        listOf(tmpDir, libDir, serverDir, dbData, backupDir, dlDir).forEach { it.mkdirs() }
        dbCredFile.parentFile?.mkdirs()
        val src = nativeLib("libtalloc.so")
        val link = File(libDir, "libtalloc.so.2")
        if (src.exists()) {
            try {
                link.delete()
                Os.symlink(src.absolutePath, link.absolutePath)
            } catch (_: Exception) { }
        }
    }
}

object Prefs {
    const val DEF_OPENMP =
        "https://github.com/V4NNThrift/OmpHub-CEF/releases/download/cef7058-v1/cef7058-server.tar.gz"
    const val DEF_SAMP =
        "https://sa-mp.co.id/files/samp037svr_R2-2-1.tar.gz"
    const val DEF_SAMP_DL =
        "https://sa-mp.co.id/files/samp03DLsvr_R1.tar.gz"
    const val DEF_ALPINE =
        "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"

    private val sp get() = Env.ctx.getSharedPreferences("omphub", Context.MODE_PRIVATE)
    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var urlOpenMp: String
        get() = str("urlOpenMp", DEF_OPENMP)
        set(v) = put("urlOpenMp", v)
    var urlSamp: String
        get() = str("urlSamp", DEF_SAMP)
        set(v) = put("urlSamp", v)
    var urlSampDl: String
        get() = str("urlSampDl", DEF_SAMP_DL)
        set(v) = put("urlSampDl", v)
    var urlAlpine: String
        get() = str("urlAlpine", DEF_ALPINE)
        set(v) = put("urlAlpine", v)

    var port: Int
        get() = sp.getInt("port", 7058)
        set(v) = sp.edit().putInt("port", v).apply()
    var dbPort: Int
        get() = sp.getInt("dbPort", 3306)
        set(v) = sp.edit().putInt("dbPort", v).apply()
    var dbEnabled: Boolean
        get() = sp.getBoolean("dbEnabled", true)
        set(v) = sp.edit().putBoolean("dbEnabled", v).apply()
    var themeDark: Boolean
        get() = sp.getBoolean("themeDark", true)
        set(v) = sp.edit().putBoolean("themeDark", v).apply()
    /** Restart otomatis (watchdog) kalau game server / MariaDB mati sendiri. */
    var watchdogEnabled: Boolean
        get() = sp.getBoolean("watchdogEnabled", true)
        set(v) = sp.edit().putBoolean("watchdogEnabled", v).apply()
    /** Start server otomatis setelah HP selesai boot. */
    var bootAutoStart: Boolean
        get() = sp.getBoolean("bootAutoStart", false)
        set(v) = sp.edit().putBoolean("bootAutoStart", v).apply()
    /** Notifikasi event: pemain join/leave, server mati, restart watchdog. */
    var notifyEvents: Boolean
        get() = sp.getBoolean("notifyEvents", true)
        set(v) = sp.edit().putBoolean("notifyEvents", v).apply()
    /** Mode Desktop untuk WebView phpMyAdmin (UA desktop + wide viewport). */
    var phpMyAdminDesktopMode: Boolean
        get() = sp.getBoolean("phpMyAdminDesktopMode", false)
        set(v) = sp.edit().putBoolean("phpMyAdminDesktopMode", v).apply()
    var sampVersion: SampVersion
        get() = SampVersion.from(sp.getString("sampVersion", null))
        set(v) = sp.edit().putString("sampVersion", v.code).apply()
    fun sampUrl(v: SampVersion) = if (v == SampVersion.DL) urlSampDl else urlSamp
    var serverType: ServerType
        get() = ServerType.from(sp.getString("serverType", null))
        set(v) = sp.edit().putString("serverType", v.name).apply()
}

object Proot {
    enum class Target { GAME, DB }

    fun build(
        target: Target,
        cmd: List<String>,
        workDir: String = "/",
        binds: List<Pair<String, String>> = emptyList()
    ): ProcessBuilder {
        Env.prepareDirs()
        val rootfs = if (target == Target.GAME) Env.rootfsGame else Env.rootfsDb
        val args = mutableListOf(
            Env.nativeLib("libproot.so").absolutePath,
            "--kill-on-exit", "--link2symlink", "-0",
            "-r", rootfs.absolutePath,
            "-w", workDir
        )
        if (target == Target.GAME) {
            val extra = runCatching { File(Env.serverDir, "qemu-extra.txt").readText().trim() }.getOrDefault("")
            val q = Env.nativeLib("libqemu-i386.so").absolutePath + " -B 0x100000" +
                if (extra.isNotEmpty()) " $extra" else ""
            args += listOf("-q", q)
        }
        args += listOf("-b", "/dev", "-b", "/proc")
        binds.forEach { (host, guest) -> args += listOf("-b", "$host:$guest") }

        args += listOf(
            "/usr/bin/env",
            "-u", "LD_LIBRARY_PATH", "-u", "PROOT_LOADER", "-u", "PROOT_TMP_DIR", "-u", "PROOT_NO_SECCOMP",
            "HOME=/root",
            "TMPDIR=/tmp",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm",
            "LANG=C.UTF-8"
        )
        args += cmd

        return ProcessBuilder(args).apply {
            environment().apply {
                put("LD_LIBRARY_PATH", Env.libDir.absolutePath + ":" + Env.nativeDir)
                put("PROOT_LOADER", Env.nativeLib("libproot-loader.so").absolutePath)
                put("PROOT_TMP_DIR", Env.tmpDir.absolutePath)
                put("PROOT_NO_SECCOMP", "1")
            }
        }
    }

    fun writeNetConfig(rootfs: File) {
        val etc = File(rootfs, "etc").apply { mkdirs() }
        File(etc, "resolv.conf").apply { delete(); writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n") }
        File(etc, "hosts").apply { delete(); writeText("127.0.0.1 localhost\n::1 localhost\n") }
        File(rootfs, "tmp").apply { mkdirs(); chmodSafe(path, 511 or 512) }
        File(rootfs, "root").mkdirs()
    }
}
