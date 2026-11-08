package id.omphub

import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class OpenMpConfig(private val file: File) : ServerConfig {
    private var root = JSONObject()

    fun load(): OpenMpConfig {
        root = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject()
        return this
    }

    override fun save() {
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    private fun section(name: String): JSONObject {
        root.optJSONObject(name)?.let { return it }
        return JSONObject().also { root.put(name, it) }
    }

    override var hostName: String
        get() = root.optString("name", "open.mp server")
        set(v) { root.put("name", v) }

    override var maxPlayers: Int
        get() = root.optInt("max_players", 50)
        set(v) { root.put("max_players", v) }

    override var port: Int
        get() = section("network").optInt("port", 7058)
        set(v) { section("network").put("port", v) }

    var rconEnabled: Boolean
        get() = section("rcon").optBoolean("enable", false)
        set(v) { section("rcon").put("enable", v) }

    override var rconPassword: String
        get() = section("rcon").optString("password", "")
        set(v) { section("rcon").put("password", v) }

    var legacyPlugins: List<String>
        get() = root.optJSONObject("pawn")?.optJSONArray("legacy_plugins").toStringList()
        set(v) { section("pawn").put("legacy_plugins", JSONArray(v)) }

    fun addLegacyPlugin(name: String) {
        if (name !in legacyPlugins) legacyPlugins = legacyPlugins + name
    }

    override fun ensureStrongRcon(): Boolean {
        val p = rconPassword
        val weak = p.length < 8 || p.lowercase() in WEAK
        if (weak) rconPassword = randomToken(16)
        return weak
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { optString(it) }

    companion object {
        private val WEAK = setOf("", "changeme", "password", "1234", "12345678", "rcon", "admin")
    }
}

object OpenMpInstaller {
    private val KEEP_IF_EXISTS = listOf("config.json", "server.cfg", "gamemodes/", "filterscripts/", "scriptfiles/", "plugins/", "npcmodes/", "models/", "artconfig.txt", "components/Cef.so")

    internal fun keep(rel: String) = KEEP_IF_EXISTS.any { rel == it || rel.startsWith(it) }

    internal fun mergeCopy(src: File, dst: File, rel: String = "") {
        dst.mkdirs()
        src.listFiles()?.forEach { f ->
            val r = if (rel.isEmpty()) f.name else "$rel/${f.name}"
            val t = File(dst, f.name)
            if (f.isDirectory) {
                mergeCopy(f, t, r)
            } else if (!(t.exists() && keep(r))) {
                t.delete()
                f.copyTo(t, overwrite = true)
                try { Os.chmod(t.path, Os.stat(f.path).st_mode and 4095) } catch (_: Exception) { }
            }
        }
    }

    fun install(url: String, log: (String) -> Unit) {
        val tmp = File(Env.files, "tmp_openmp")
        deleteTree(tmp)
        tmp.mkdirs()

        val archive = File(Env.dlDir, archiveNameFor("openmp", url))
        log("Mengunduh open.mp dari $url")
        var lastPct = -1
        downloadFile(url, archive) { done, total ->
            if (total > 0) {
                val pct = (done * 100 / total).toInt() / 20 * 20
                if (pct != lastPct) { lastPct = pct; log("  unduh $pct%") }
            }
        }

        log("Mengekstrak...")
        Extractor.extractTar(archive, tmp)
        val bin = tmp.walkTopDown().firstOrNull { it.isFile && it.name == "omp-server" }
            ?: error("omp-server tidak ada di arsip. Cek URL rilis open.mp di Setup.")

        mergeCopy(bin.parentFile ?: error("Folder arsip tidak ditemukan"), Env.serverDir)
        listOf("gamemodes", "filterscripts", "scriptfiles", "plugins", "components").forEach {
            File(Env.serverDir, it).mkdirs()
        }
        fixPerms()

        val cfgFile = File(Env.serverDir, "config.json")
        val cfg = OpenMpConfig(cfgFile).load()
        if (!cfgFile.exists()) { cfg.hostName = "OmpHub Server"; cfg.maxPlayers = 50 }
        cfg.port = Prefs.port
        cfg.rconEnabled = true
        if (cfg.ensureStrongRcon()) log("Password RCON baru di-generate (lihat Files > config.json)")
        cfg.save()

        deleteTree(tmp)
        archive.delete()
        log("open.mp terpasang di ${Env.serverDir.absolutePath}")
    }

    fun fixPerms() {
        listOf("omp-server", "samp03svr", "announce", "samp-npc").forEach { n ->
            File(Env.serverDir, n).takeIf { it.exists() }?.setExecutable(true, false)
        }
        listOf("components", "plugins").forEach { d ->
            File(Env.serverDir, d).walkTopDown().filter { it.isFile && it.name.endsWith(".so") }
                .forEach { it.setExecutable(true, false) }
        }
    }
}
