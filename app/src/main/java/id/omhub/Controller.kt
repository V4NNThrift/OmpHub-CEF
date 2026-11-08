package id.omphub

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

enum class RunState { STOPPED, STARTING, RUNNING, STOPPING }

enum class BootGate { CHECKING, WORKING, FAILED, DONE }

data class SetupStatus(
    val binaries: Boolean = false,
    val gameRootfs: Boolean = false,
    val openMp: Boolean = false,
    val samp: Boolean = false,
    val db: Boolean = false,
    val engine: ServerType = ServerType.OMP
) {
    val serverInstalled get() = if (engine == ServerType.SAMP) samp else openMp
    val ready get() = binaries && gameRootfs && serverInstalled
}

object Controller {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val gameLog = LogBuffer(1000)
    val dbLog = LogBuffer(300)
    val setupLog = LogBuffer(600)

    val gameState = MutableStateFlow(RunState.STOPPED)
    val dbState = MutableStateFlow(RunState.STOPPED)
    val setupBusy = MutableStateFlow(false)
    val setupStatus = MutableStateFlow(SetupStatus())

    val bootGate = MutableStateFlow(BootGate.CHECKING)
    val bootStep = MutableStateFlow("Memeriksa komponen...")
    val bootError = MutableStateFlow<String?>(null)

    private val gameLock = Mutex()

    @Volatile private var gameProc: Process? = null
    @Volatile private var runningType = ServerType.OMP
    @Volatile private var gameStdin: BufferedWriter? = null

    @Volatile var gameStartedAt = 0L
    @Volatile var dbStartedAt = 0L

    fun gamePid(): Int? = pidOf(gameProc)

    fun refreshStatus() {
        setupStatus.value = SetupStatus(
            binaries = Env.binariesOk(),
            gameRootfs = File(Env.rootfsGame, "usr/bin/env").exists(),
            openMp = ServerType.OMP.installed,
            samp = ServerType.SAMP.installed,
            db = MariaDb.installed && MariaDb.initialized && MariaDb.phpMyAdminInstalled,
            engine = Prefs.serverType
        )
    }

    private const val ATOMIC_LIB = "usr/lib/i386-linux-gnu/libatomic.so.1"

    fun gameDepsOk() = File(Env.rootfsGame, ATOMIC_LIB).exists()

    fun ensureGameDeps(log: (String) -> Unit) {
        if (gameDepsOk()) return
        log("Memasang library tambahan di rootfs (libatomic1), butuh beberapa menit...")
        val script = "export DEBIAN_FRONTEND=noninteractive; " +
            "apt-get -o APT::Sandbox::User=root update && " +
            "apt-get -o APT::Sandbox::User=root install -y --no-install-recommends libatomic1"
        val r = Proot.build(Proot.Target.GAME, listOf("/bin/sh", "-c", script)).runAndCapture(900)
        log(r.output.takeLast(1500))
        check(r.ok && gameDepsOk()) { "Gagal memasang libatomic1 (exit ${r.exit})" }
    }

    suspend fun startGame(): Unit = withContext(Dispatchers.IO) { gameLock.withLock {
        if (gameProc?.isAlive == true) return@withLock
        refreshStatus()
        val st = setupStatus.value
        if (!st.binaries) {
            gameLog.add("[panel] Binary proot/qemu tidak ada di APK. Jalankan scripts/fetch-binaries.sh lalu build ulang.")
            return@withLock
        }
        val type = st.engine
        if (!st.gameRootfs || !st.serverInstalled) {
            gameLog.add(if (!st.gameRootfs) "[panel] Sistem dasar server belum siap. Selesaikan persiapan awal aplikasi." else "[panel] ${type.label} belum diunduh. Buka tab Setup.")
            return@withLock
        }
        gameState.value = RunState.STARTING
        try {
            if (type == ServerType.OMP) ensureGameDeps { gameLog.add(it) }

            syncServerConfig(type)
            OpenMpInstaller.fixPerms()

            val pb = Proot.build(
                Proot.Target.GAME, listOf("./${type.binary}"), "/server",
                listOf(Env.serverDir.absolutePath to "/server")
            )
            pb.redirectErrorStream(true)
            runningType = type
            gameLog.add("[panel] Menjalankan ${type.binary} (${type.label}) di port ${Prefs.port}...")
            val p = pb.start()
            gameProc = p
            gameStdin = p.outputStream.bufferedWriter()
            gameStartedAt = SystemClock.elapsedRealtime()
            gameState.value = RunState.RUNNING
            thread(name = "omp-log", isDaemon = true) {
                try { p.inputStream.bufferedReader().forEachLine { gameLog.add(it) } }
                catch (_: java.io.IOException) { }
            }
            thread(name = "omp-wait", isDaemon = true) {
                val code = runCatching { p.waitFor() }.getOrDefault(-1)
                gameLog.add("[panel] ${type.binary} berhenti (exit $code)")
                if (gameProc === p) {
                    gameProc = null
                    gameStdin = null
                    gameState.value = RunState.STOPPED
                }
            }
        } catch (e: Throwable) {
            gameLog.add("[panel] Gagal start: ${e.message}")
            gameState.value = RunState.STOPPED
        }
    } }

    suspend fun stopGame(): Unit = withContext(Dispatchers.IO) { gameLock.withLock {
        val p = gameProc ?: run { gameState.value = RunState.STOPPED; return@withLock }
        gameState.value = RunState.STOPPING
        val kids = pidOf(p)?.let { descendantsOf(it) }.orEmpty()
        try {
            if (runningType == ServerType.SAMP) runCatching { rcon("exit") }
            else runCatching { gameStdin?.apply { write("exit\n"); flush() } }
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroy()
                if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
            }
            p.waitFor(3, TimeUnit.SECONDS)
        } finally {
            kids.forEach { if (File("/proc/$it").exists()) android.os.Process.killProcess(it) }
            if (gameProc === p) { gameProc = null; gameStdin = null }
            gameState.value = RunState.STOPPED
        }
    } }

    private fun descendantsOf(root: Int): List<Int> {
        val kids = HashMap<Int, MutableList<Int>>()
        File("/proc").listFiles()?.forEach { f ->
            val pid = f.name.toIntOrNull() ?: return@forEach
            val ppid = runCatching {
                File(f, "stat").readText().substringAfterLast(')').trim().split(' ')[1].toInt()
            }.getOrNull() ?: return@forEach
            kids.getOrPut(ppid) { mutableListOf() }.add(pid)
        }
        val out = mutableListOf<Int>()
        val q = ArrayDeque(listOf(root))
        while (q.isNotEmpty()) {
            kids[q.removeFirst()]?.forEach { out.add(it); q.add(it) }
        }
        return out
    }

    fun sendStdin(line: String) {
        gameLog.add("> $line")
        if (runningType == ServerType.SAMP && gameProc?.isAlive == true) {
            val out = rcon(line)
            if (out.isEmpty()) gameLog.add("[rcon] tidak ada balasan (RCON mati / password salah / server mati)")
            out.forEach { gameLog.add(it) }
            return
        }
        try {
            gameStdin?.apply { write(line + "\n"); flush() }
                ?: gameLog.add("[panel] Server tidak berjalan")
        } catch (e: Exception) {
            gameLog.add("[panel] Gagal kirim: ${e.message}")
        }
    }

    fun rcon(cmd: String): List<String> {
        val type = if (gameProc?.isAlive == true) runningType else Prefs.serverType
        val pass = openServerConfig(type)?.rconPassword.orEmpty()
        return Samp.rcon(Prefs.port, pass, cmd)
    }

    private fun syncServerConfig(type: ServerType) {
        when (type) {
            ServerType.OMP -> {
                val cfg = OpenMpConfig(type.configFile).load()
                cfg.port = Prefs.port
                cfg.rconEnabled = true
                if (cfg.ensureStrongRcon()) gameLog.add("[panel] Password RCON lemah diganti otomatis.")
                cfg.save()
            }
            ServerType.SAMP -> {
                val cfg = SampConfig(type.configFile).load()
                cfg.port = Prefs.port
                cfg.ensureQuery()
                if (cfg.ensureStrongRcon()) gameLog.add("[panel] Password RCON lemah diganti otomatis.")
                cfg.save()
            }
        }
    }

    suspend fun startDb(): Unit = withContext(Dispatchers.IO) {
        if (dbState.value == RunState.RUNNING || dbState.value == RunState.STARTING) return@withContext
        refreshStatus()
        if (!setupStatus.value.db) {
            dbLog.add("[panel] MariaDB belum siap. Selesaikan persiapan awal aplikasi.")
            return@withContext
        }
        dbState.value = RunState.STARTING
        try {
            MariaDb.start(
                onLine = { dbLog.add(it) },
                onExit = { code ->
                    MariaDb.stopPhpMyAdmin()
                    dbLog.add("[panel] mariadbd berhenti (exit $code)")
                    dbState.value = RunState.STOPPED
                }
            )
            if (MariaDb.waitReady()) {
                dbStartedAt = SystemClock.elapsedRealtime()
                if (MariaDb.loadCredentials() == null) {
                    val c = MariaDb.provision()
                    dbLog.add("[panel] Database '${c.database}' dan user '${c.user}' dibuat.")
                }
                runCatching { MariaDb.startPhpMyAdmin { dbLog.add(it) } }
                    .onSuccess { dbLog.add("[panel] phpMyAdmin siap di http://127.0.0.1:8080/phpmyadmin/") }
                    .onFailure { dbLog.add("[panel] phpMyAdmin gagal dijalankan: ${it.message}") }
                dbState.value = RunState.RUNNING
            } else {
                dbLog.add("[panel] MariaDB tidak siap dalam waktu yang ditentukan.")
                MariaDb.stop()
                dbState.value = RunState.STOPPED
            }
        } catch (e: Throwable) {
            dbLog.add("[panel] Gagal start MariaDB: ${e.message}")
            dbState.value = RunState.STOPPED
        }
    }

    suspend fun stopDb(): Unit = withContext(Dispatchers.IO) {
        if (dbState.value == RunState.STOPPED) return@withContext
        dbState.value = RunState.STOPPING
        try {
            MariaDb.stopPhpMyAdmin()
            MariaDb.stop()
        } finally {
            dbState.value = RunState.STOPPED
        }
    }

    suspend fun startAll() {
        if (Prefs.dbEnabled) {
            refreshStatus()
            if (setupStatus.value.db) startDb()
        }
        startGame()
    }

    suspend fun stopAll() {
        try { stopGame() } finally { stopDb() }
    }

    private fun runSetup(title: String, onFinish: (Throwable?) -> Unit = {}, block: () -> Unit) {
        if (setupBusy.value) return
        setupBusy.value = true
        scope.launch {
            setupLog.add("( $title )")
            var error: Throwable? = null
            try {
                block()
                setupLog.add("[Sukses] $title selesai")
            } catch (e: Throwable) {
                error = e
                setupLog.add("[ERROR] ${e.message ?: e}")
            } finally {
                setupBusy.value = false
                refreshStatus()
                onFinish(error)
            }
        }
    }

    private fun doInstallGameRootfs() {
        check(gameState.value == RunState.STOPPED) { "Hentikan server dulu" }
        val staging = File(Env.rootfsGame.parentFile, "game.tmp")
        deleteTree(staging)
        val archive = File(Env.dlDir, "rootfs-game.tar.gz")

        var lastCount = 0
        val onEntry: (Int) -> Unit = { n ->
            if (n - lastCount >= 1000) { lastCount = n; setupLog.add("  $n entri") }
        }
        if (Env.copyAsset("rootfs-game.tar.gz", archive)) {
            setupLog.add("Memakai rootfs Debian i386 bawaan APK")
            setupLog.add("Mengekstrak (bisa 1-3 menit)...")
            Extractor.extractTar(archive, staging, onProgress = onEntry)
            archive.delete()
            val atomic = File(Env.dlDir, "libatomic1-i386.tar.xz")
            if (Env.copyAsset("libatomic1-i386.tar.xz", atomic)) {
                setupLog.add("Memasang libatomic1 bawaan APK")
                Extractor.extractTar(atomic, staging)
                atomic.delete()
            }
        } else {
            setupLog.add("Aset rootfs-game.tar.gz tidak ditemukan. Isi assets APK: " +
                (Env.ctx.assets.list("")?.joinToString() ?: "(kosong)"))
            val dl = File(Env.dlDir, "rootfs-game.tar.gz")
            setupLog.add("Mengunduh i386/debian:bookworm-slim dari Docker Hub")
            var lastPct = -1
            DockerHub.downloadRootfs("i386/debian", "bookworm-slim", dl) { done, total ->
                if (total > 0) {
                    val pct = (done * 100 / total).toInt() / 20 * 20
                    if (pct != lastPct) { lastPct = pct; setupLog.add("  unduh $pct%") }
                }
            }
            setupLog.add("Mengekstrak (bisa 1-3 menit)...")
            Extractor.extractTar(dl, staging, onProgress = onEntry)
            dl.delete()
        }
        Proot.writeNetConfig(staging)

        deleteTree(Env.rootfsGame)
        check(staging.renameTo(Env.rootfsGame)) { "Gagal memindahkan rootfs ke lokasi akhir" }
        ensureGameDeps { setupLog.add(it) }
    }

    private fun doInstallServer() {
        check(gameState.value == RunState.STOPPED) { "Hentikan server dulu" }
        when (Prefs.serverType) {
            ServerType.OMP -> OpenMpInstaller.install(Prefs.urlOpenMp) { setupLog.add(it) }
            ServerType.SAMP -> {
                val v = Prefs.sampVersion
                SampInstaller.install(Prefs.sampUrl(v), v) { setupLog.add(it) }
            }
        }
    }

    private fun doInstallDb() {
        check(dbState.value == RunState.STOPPED) { "Hentikan MariaDB dulu" }
        MariaDb.install(Prefs.urlAlpine) { setupLog.add(it) }
    }

    fun installServer() = runSetup("Pasang / update ${serverLabel()}") { doInstallServer() }

    fun installAll(onFinish: (Throwable?) -> Unit = {}) = runSetup("Pasang semua komponen", onFinish) {
        check(Env.binariesOk()) { "Binary proot/qemu belum ada di APK" }
        check(gameState.value == RunState.STOPPED && dbState.value == RunState.STOPPED) { "Hentikan server dulu" }
        refreshStatus()
        if (!setupStatus.value.gameRootfs) {
            bootStep.value = "Mengekstrak sistem dasar server (Debian i386)"
            doInstallGameRootfs()
        }
        refreshStatus()
        if (Prefs.dbEnabled && !setupStatus.value.db) {
            bootStep.value = "Menyiapkan MariaDB"
            doInstallDb()
        }
        refreshStatus()
        if (!setupStatus.value.serverInstalled) {
            setupLog.add("Server ${setupStatus.value.engine.label} belum ada. Unduh lewat tombol di tab Setup, atau unggah manual lewat tab Files.")
        }
    }

    @Volatile private var autoSetupStarted = false

    fun autoSetupOnFirstLaunch() {
        if (autoSetupStarted) return
        refreshStatus()
        val st = setupStatus.value
        if (!st.binaries || (st.gameRootfs && (!Prefs.dbEnabled || st.db))) {
            bootGate.value = BootGate.DONE
            return
        }
        autoSetupStarted = true
        runBootInstall()
    }

    fun retryBootSetup() {
        if (setupBusy.value) return
        runBootInstall()
    }
    
    fun skipBootSetup() {
        bootGate.value = BootGate.DONE
    }

    private fun runBootInstall() {
        bootError.value = null
        bootStep.value = "Menyiapkan komponen server"
        bootGate.value = BootGate.WORKING
        installAll { err ->
            if (err == null) {
                bootGate.value = BootGate.DONE
            } else {
                bootError.value = err.message ?: err.toString()
                bootGate.value = BootGate.FAILED
            }
        }
    }

    fun listBackups(): List<File> =
        Env.backupDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun createBackup(): File {
        Env.backupDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(Env.backupDir, "backup-$stamp.zip")
        var sql: File? = null
        if (dbState.value == RunState.RUNNING) {
            val f = File(Env.tmpDir, "database.sql")
            if (MariaDb.dumpTo(f).ok) sql = f
        }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            Zips.addDir(z, Env.serverDir, "server")
            sql?.let { Zips.addFile(z, it, "database.sql") }
        }
        sql?.delete()
        return out
    }

    fun restoreBackup(zip: File): String {
        check(gameState.value == RunState.STOPPED) { "Hentikan server dulu" }
        val sqlTmp = File(Env.tmpDir, "restore.sql")
        sqlTmp.delete()
        Zips.unzip(zip) { name ->
            when {
                name.startsWith("server/") -> Zips.safeResolve(Env.serverDir, name.removePrefix("server/"))
                name == "database.sql" -> sqlTmp
                else -> null
            }
        }
        OpenMpInstaller.fixPerms()
        var msg = "Server dipulihkan."
        if (sqlTmp.exists()) {
            msg += if (dbState.value == RunState.RUNNING) {
                val r = MariaDb.importFrom(sqlTmp)
                if (r.ok) " Database di-import." else " Import database gagal: ${r.output.take(200)}"
            } else " database.sql ada di backup; start MariaDB lalu import manual lewat tab Database."
        }
        return msg
    }
}