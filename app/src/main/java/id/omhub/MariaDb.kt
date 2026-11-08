package id.omphub

import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class DbCredentials(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val database: String
)

object MariaDb {
    private const val SOCK = "/run/mysqld/mysqld.sock"
    private val binds get() = listOf(Env.dbData.absolutePath to "/var/lib/mysql")

    @Volatile private var proc: Process? = null
    @Volatile private var phpMyAdminProc: Process? = null
    private val phpMyAdminPort = 8080

    fun pid(): Int? = pidOf(proc)

    val installed: Boolean get() = File(Env.rootfsDb, "usr/bin/mariadbd").exists()
    val initialized: Boolean get() = File(Env.dbData, "mysql").isDirectory
    val phpMyAdminInstalled: Boolean get() = File(Env.rootfsDb, "usr/share/webapps/phpmyadmin/index.php").exists()

    private fun sh(script: String, timeoutSec: Long = 300): CmdResult =
    Proot.build(
        Proot.Target.DB,
        listOf("/bin/sh", "-c", "export TMPDIR=/tmp; mkdir -p /tmp; $script"),
        "/", binds
    ).runAndCapture(timeoutSec)

    fun install(url: String, log: (String) -> Unit) {
        if (!File(Env.rootfsDb, "bin/busybox").exists()) {
            val staging = File(Env.rootfsDb.parentFile, "db.tmp")
            deleteTree(staging)
            val bundled = File(Env.dlDir, "mariadb-rootfs.tar.xz")
            if (Env.copyAsset("mariadb-rootfs.tar.xz", bundled)) {
                log("Memakai rootfs MariaDB bawaan APK, mengekstrak (bisa 1-3 menit)...")
                Extractor.extractTar(bundled, staging)
                bundled.delete()
            } else {
                val tgz = File(Env.dlDir, archiveNameFor("alpine", url))
                log("Mengunduh Alpine arm64...")
                var lastPct = -1
                downloadFile(url, tgz) { done, total ->
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt() / 25 * 25
                        if (pct != lastPct) { lastPct = pct; log("  unduh $pct%") }
                    }
                }
                log("Mengekstrak rootfs...")
                Extractor.extractTar(tgz, staging)
                tgz.delete()
            }
            deleteTree(Env.rootfsDb)
            check(staging.renameTo(Env.rootfsDb)) { "Gagal memindahkan rootfs DB ke lokasi akhir" }
        }
        Proot.writeNetConfig(Env.rootfsDb)

        if (!installed) {
            log("Memasang paket MariaDB (apk), butuh beberapa menit...")
            val r = sh("apk add --no-cache mariadb mariadb-client", 900)
            log(r.output.takeLast(600))
            check(r.ok && installed) { "Pemasangan MariaDB gagal (exit ${r.exit}). Cek koneksi internet." }
        }
        if (!phpMyAdminInstalled) {
            log("Memasang runtime PHP 8.3 dan ekstensi yang dibutuhkan phpMyAdmin...")
            val r = sh(
                "apk add --no-cache php83 php83-mysqli php83-bz2 php83-ctype php83-curl " +
                    "php83-gd php83-mbstring php83-openssl php83-session php83-zip",
                1200
            )
            log(r.output.takeLast(1200))
            check(r.ok) { "Pemasangan PHP gagal (exit ${r.exit}). Cek koneksi internet." }

            val archive = File(Env.dlDir, "phpMyAdmin-5.2.3-all-languages.tar.gz")
            val staging = File(Env.rootfsDb, "usr/share/webapps/phpmyadmin.tmp")
            val target = File(Env.rootfsDb, "usr/share/webapps/phpmyadmin")
            deleteTree(staging)
            log("Mengunduh phpMyAdmin 5.2.3...")
            downloadFile(
                "https://files.phpmyadmin.net/phpMyAdmin/5.2.3/phpMyAdmin-5.2.3-all-languages.tar.gz",
                archive
            ) { done, total ->
                if (total > 0) {
                    val pct = (done * 100 / total).toInt() / 25 * 25
                    log("  phpMyAdmin $pct%")
                }
            }
            log("Mengekstrak phpMyAdmin...")
            Extractor.extractTar(archive, staging, strip = 1)
            archive.delete()
            deleteTree(target)
            target.parentFile?.mkdirs()
            check(staging.renameTo(target)) { "Gagal memasang file phpMyAdmin." }
            check(phpMyAdminInstalled) { "File phpMyAdmin tidak lengkap setelah ekstraksi." }
        }
        initDataDir(log)
    }

    private fun initDataDir(log: (String) -> Unit) {
        Env.dbData.mkdirs()
        if (initialized) return
        Env.dbData.listFiles()?.forEach { deleteTree(it) }
        log("Inisialisasi database...")
        val r = sh(
            "mkdir -p /run/mysqld /tmp && mariadb-install-db --user=root --datadir=/var/lib/mysql " +
                "--skip-test-db --auth-root-authentication-method=normal --skip-name-resolve " +
                "--innodb-use-native-aio=0 --innodb-buffer-pool-size=32M --verbose"
        )
        log(r.output.takeLast(3000))
        if (!(r.ok && initialized)) {
            Env.dbData.listFiles()?.forEach { deleteTree(it) }
            error("mariadb-install-db gagal (exit ${r.exit}), lihat log di atas")
        }
    }

    fun isRunning(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", Prefs.dbPort), 400); true }
    } catch (_: Exception) { false }

    @Synchronized
    fun start(onLine: (String) -> Unit, onExit: (Int) -> Unit) {
        if (proc?.isAlive == true) return
        check(installed && initialized) { "MariaDB belum siap (persiapan awal belum selesai)" }
        val cmd = "mkdir -p /tmp /run/mysqld && " +
        "sed -i 's/^skip-networking/#skip-networking/' /etc/my.cnf.d/*.cnf 2>/dev/null; " +
        "exec mariadbd --user=root --datadir=/var/lib/mysql " +
        "--bind-address=127.0.0.1 --port=${Prefs.dbPort} --socket=$SOCK " +
        "--pid-file=/run/mysqld/mysqld.pid --skip-name-resolve --innodb-use-native-aio=0 " +
        "--performance-schema=OFF --innodb-buffer-pool-size=32M --max-connections=30"
        val pb = Proot.build(Proot.Target.DB, listOf("/bin/sh", "-c", cmd), "/", binds)
        pb.redirectErrorStream(true)
        val p = pb.start()
        proc = p
        thread(name = "mariadb-log", isDaemon = true) {
            try {
                p.inputStream.bufferedReader().forEachLine(onLine)
            } catch (_: java.io.IOException) {
            }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            proc = null
            onExit(code)
        }
    }

    fun startPhpMyAdmin(onLine: (String) -> Unit = {}) {
        if (phpMyAdminProc?.isAlive == true && isPhpMyAdminRunning()) return
        check(phpMyAdminInstalled) { "phpMyAdmin belum terpasang. Pasang ulang komponen MariaDB melalui Setup." }
        check(isRunning()) { "MariaDB harus berjalan sebelum phpMyAdmin dibuka." }

        val configFile = File(Env.rootfsDb, "usr/share/webapps/phpmyadmin/config.inc.php")
        configFile.parentFile?.mkdirs()
        val adminUser = "omphub_admin"
        val adminPass = randomToken(32)
        val sql = "CREATE USER IF NOT EXISTS '$adminUser'@'127.0.0.1' IDENTIFIED BY '$adminPass'; " +
            "ALTER USER '$adminUser'@'127.0.0.1' IDENTIFIED BY '$adminPass'; " +
            "GRANT ALL PRIVILEGES ON *.* TO '$adminUser'@'127.0.0.1' WITH GRANT OPTION; FLUSH PRIVILEGES;"
        val grant = exec(sql)
        check(grant.ok) { "Gagal menyiapkan akun phpMyAdmin: ${grant.output}" }

        val phpConfig = """<?php
            ${'$'}cfg['blowfish_secret'] = '$adminPass';
            ${'$'}i = 0;
            ${'$'}i++;
            ${'$'}cfg['Servers'][${'$'}i]['auth_type'] = 'config';
            ${'$'}cfg['Servers'][${'$'}i]['host'] = '127.0.0.1';
            ${'$'}cfg['Servers'][${'$'}i]['port'] = '${Prefs.dbPort}';
            ${'$'}cfg['Servers'][${'$'}i]['connect_type'] = 'tcp';
            ${'$'}cfg['Servers'][${'$'}i]['user'] = '$adminUser';
            ${'$'}cfg['Servers'][${'$'}i]['password'] = '$adminPass';
            ${'$'}cfg['Servers'][${'$'}i]['AllowNoPassword'] = false;
            ${'$'}cfg['TempDir'] = '/tmp/phpmyadmin';
            ${'$'}cfg['UploadDir'] = '';
            ${'$'}cfg['SaveDir'] = '';
        """.trimIndent()
        configFile.writeText(phpConfig)
        File(Env.rootfsDb, "tmp/phpmyadmin").apply { mkdirs(); chmodSafe(path, 511 or 512) }

        val pb = Proot.build(
            Proot.Target.DB,
            listOf("/bin/sh", "-c", "exec php83 -S 127.0.0.1:$phpMyAdminPort -t /usr/share/webapps"),
            "/",
            binds
        )
        pb.redirectErrorStream(true)
        val process = pb.start()
        phpMyAdminProc = process
        thread(name = "phpmyadmin-log", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().forEachLine { onLine("[phpMyAdmin] $it") }
            } catch (_: Exception) { }
            runCatching { process.waitFor() }
            if (phpMyAdminProc === process) phpMyAdminProc = null
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (isPhpMyAdminRunning()) return
            if (!process.isAlive) break
            Thread.sleep(250)
        }
        stopPhpMyAdmin()
        error("phpMyAdmin tidak dapat membuka port $phpMyAdminPort. Periksa log MariaDB.")
    }

    fun isPhpMyAdminRunning(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", phpMyAdminPort), 350); true }
    } catch (_: Exception) { false }

    @Synchronized
    fun stopPhpMyAdmin() {
        val p = phpMyAdminProc ?: return
        p.destroy()
        if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        phpMyAdminProc = null
    }

    fun waitReady(timeoutMs: Long = 90_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (isRunning()) return true
            if (proc?.isAlive != true) return false
            Thread.sleep(500)
        }
        return false
    }

    @Synchronized
    fun stop() {
        stopPhpMyAdmin()
        val p = proc ?: return
        if (isRunning()) sh("mariadb-admin -S $SOCK -uroot shutdown", 60)
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroy()
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        proc = null
    }

    fun loadCredentials(): DbCredentials? {
        val f = Env.dbCredFile
        if (!f.exists()) return null
        return runCatching {
            val j = JSONObject(f.readText())
            DbCredentials(j.getString("host"), j.getInt("port"), j.getString("user"), j.getString("password"), j.getString("database"))
        }.getOrNull()
    }

    fun provision(database: String = "samp", user: String = "samp"): DbCredentials {
        loadCredentials()?.let { return it }
        val pass = randomToken(20)
        val sql = "CREATE DATABASE IF NOT EXISTS `$database` CHARACTER SET utf8mb4; " +
            "CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$pass'; " +
            "CREATE USER IF NOT EXISTS '$user'@'localhost' IDENTIFIED BY '$pass'; " +
            "GRANT ALL PRIVILEGES ON `$database`.* TO '$user'@'127.0.0.1'; " +
            "GRANT ALL PRIVILEGES ON `$database`.* TO '$user'@'localhost'; FLUSH PRIVILEGES;"
        val r = exec(sql)
        check(r.ok) { "provision gagal: ${r.output}" }
        val c = DbCredentials("127.0.0.1", Prefs.dbPort, user, pass, database)
        Env.dbCredFile.writeText(
            JSONObject().put("host", c.host).put("port", c.port).put("user", c.user)
                .put("password", c.password).put("database", c.database).toString(2)
        )
        return c
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun exec(sql: String, database: String? = null): CmdResult {
        val db = database?.let { " ${q(it)}" } ?: ""
        return sh("mariadb -S $SOCK -uroot --table -e ${q(sql)}$db", 60)
    }

    fun query(sql: String, database: String? = null): CmdResult {
        val db = database?.let { " ${q(it)}" } ?: ""
        return sh("mariadb -S $SOCK -uroot --batch --raw --column-names -e ${q(sql)}$db", 60)
    }

    fun listDatabases(): List<String> {
        val r = query("SHOW DATABASES")
        check(r.ok) { r.output.ifBlank { "Gagal membaca database" } }
        return r.output.lineSequence().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    fun listTables(database: String): List<String> {
        val r = query("SHOW TABLES", database)
        check(r.ok) { r.output.ifBlank { "Gagal membaca tabel" } }
        return r.output.lineSequence().drop(1).map { it.substringAfter('\t', it).trim() }
            .filter { it.isNotEmpty() }.toList()
    }

    fun browseTable(database: String, table: String, limit: Int = 100): CmdResult {
        require(database.matches(Regex("[A-Za-z0-9_$-]+"))) { "Nama database tidak valid" }
        require(table.matches(Regex("[A-Za-z0-9_$-]+"))) { "Nama tabel tidak valid" }
        return query("SELECT * FROM `$table` LIMIT ${limit.coerceIn(1, 500)}", database)
    }

    fun createDatabase(name: String): CmdResult {
        if (!name.matches(Regex("[A-Za-z0-9_$-]{1,64}"))) return CmdResult(1, "Nama database tidak valid")
        return exec("CREATE DATABASE `$name` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    }

    fun dumpTo(dest: File): CmdResult {
        val dbName = loadCredentials()?.database ?: "samp"
        dest.parentFile?.mkdirs()
        val pb = Proot.build(
            Proot.Target.DB,
            listOf("mariadb-dump", "-S", SOCK, "-uroot", "--single-transaction", "--routines", dbName),
            "/", binds
        )
        pb.redirectOutput(dest)
        val p = pb.start()
        val err = p.errorStream.bufferedReader().readText()
        val code = if (p.waitFor(300, TimeUnit.SECONDS)) p.exitValue() else -1
        return CmdResult(code, err)
    }

    fun importFrom(src: File): CmdResult {
        val dbName = loadCredentials()?.database ?: "samp"
        val pb = Proot.build(Proot.Target.DB, listOf("mariadb", "-S", SOCK, "-uroot", dbName), "/", binds)
        pb.redirectInput(src)
        return pb.runAndCapture(600)
    }
}
