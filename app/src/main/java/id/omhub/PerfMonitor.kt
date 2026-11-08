package id.omphub

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ProcUsage(
    val cpuPct: Float = 0f,
    val rssKb: Long = 0,
    val threads: Int = 0,
    val procs: Int = 0,
    val cpuHist: List<Float> = emptyList(),
    val ramHistMb: List<Float> = emptyList()
)

data class PerfSnapshot(
    val cores: Int = Runtime.getRuntime().availableProcessors(),
    val game: ProcUsage? = null,
    val db: ProcUsage? = null,
    val app: ProcUsage? = null,
    val gameUptimeMs: Long = 0,
    val dbUptimeMs: Long = 0,
    val memTotalKb: Long = 0,
    val memAvailKb: Long = 0,
    val lowMemory: Boolean = false,
    val batteryPct: Int = -1,
    val batteryTempC: Float = Float.NaN,
    val charging: Boolean = false,
    val thermal: String? = null,
    val rxBps: Long = 0,
    val txBps: Long = 0,
    val diskFreeB: Long = 0,
    val diskTotalB: Long = 0
)

fun pidOf(p: Process?): Int? {
    if (p == null) return null
    runCatching {
        val m = p.javaClass.getMethod("pid")
        return (m.invoke(p) as Number).toInt()
    }
    return runCatching {
        val f = p.javaClass.getDeclaredField("pid")
        f.isAccessible = true
        f.getInt(p)
    }.getOrNull()
}

object PerfMonitor {
    private const val HIST = 60
    private const val ACTIVE_MS = 2000L
    private const val IDLE_MS = 5000L

    private val _snapshot = MutableStateFlow(PerfSnapshot())
    val snapshot: StateFlow<PerfSnapshot> get() = _snapshot

    @Volatile private var started = false

    private class Row(val pid: Int, val ppid: Int, val ticks: Long, val threads: Int, val rssKb: Long)

    private class Series {
        val cpu = ArrayDeque<Float>()
        val ram = ArrayDeque<Float>()
        fun push(c: Float, r: Float) {
            cpu.addLast(c); ram.addLast(r)
            while (cpu.size > HIST) cpu.removeFirst()
            while (ram.size > HIST) ram.removeFirst()
        }
        fun clear() { cpu.clear(); ram.clear() }
    }

    private val clkTck: Long = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1L)
    private val pageKb: Long = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) / 1024 }.getOrDefault(4L).coerceAtLeast(1L)

    fun ensureStarted() {
        if (started) return
        started = true
        Controller.scope.launch { loop() }
    }

    private fun scan(): Map<Int, Row> {
        val out = HashMap<Int, Row>()
        val dirs = File("/proc").listFiles() ?: return out
        for (d in dirs) {
            val pid = d.name.toIntOrNull() ?: continue
            val text = try { File(d, "stat").readText() } catch (_: Throwable) { continue }
            val close = text.lastIndexOf(')')
            if (close < 0) continue
            val f = text.substring(close + 2).split(' ')
            if (f.size < 22) continue
            val ppid = f[1].toIntOrNull() ?: continue
            val ticks = (f[11].toLongOrNull() ?: 0L) + (f[12].toLongOrNull() ?: 0L)
            val threads = f[17].toIntOrNull() ?: 1
            val rssPages = f[21].toLongOrNull() ?: 0L
            out[pid] = Row(pid, ppid, ticks, threads, rssPages * pageKb)
        }
        return out
    }

    private fun subtree(root: Int, rows: Map<Int, Row>, children: Map<Int, List<Row>>): List<Row> {
        val out = ArrayList<Row>()
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            if (!seen.add(p)) continue
            rows[p]?.let { out.add(it) }
            children[p]?.forEach { stack.addLast(it.pid) }
        }
        return out
    }

    private fun cpuPct(list: List<Row>, prev: Map<Int, Long>, dtMs: Long): Float {
        if (dtMs <= 0) return 0f
        var delta = 0L
        for (r in list) delta += (r.ticks - (prev[r.pid] ?: 0L)).coerceAtLeast(0L)
        return (delta * 100_000.0 / (clkTck * dtMs)).toFloat()
    }

    private fun usage(list: List<Row>, prev: Map<Int, Long>, dtMs: Long, s: Series): ProcUsage {
        val cpu = cpuPct(list, prev, dtMs)
        val rss = list.sumOf { it.rssKb }
        s.push(cpu, rss / 1024f)
        return ProcUsage(cpu, rss, list.sumOf { it.threads }, list.size, s.cpu.toList(), s.ram.toList())
    }

    private suspend fun loop() {
        var prev: Map<Int, Long> = emptyMap()
        var prevAt = 0L
        var prevRx = -1L
        var prevTx = -1L
        var prevNetAt = 0L
        val sGame = Series()
        val sDb = Series()
        val sApp = Series()
        val me = android.os.Process.myPid()
        val uid = android.os.Process.myUid()

        while (true) {
            var wait = IDLE_MS
            try {
                val now = SystemClock.elapsedRealtime()
                val gameUp = Controller.gameState.value == RunState.RUNNING
                val dbUp = Controller.dbState.value == RunState.RUNNING
                val any = gameUp || dbUp

                var game: ProcUsage? = null
                var db: ProcUsage? = null
                var app: ProcUsage? = null

                if (any) {
                    wait = ACTIVE_MS
                    val rows = scan()
                    val children = rows.values.groupBy { it.ppid }
                    val dt = if (prevAt == 0L) 0L else now - prevAt

                    val dbRoot = if (dbUp) MariaDb.pid() else null
                    val dbTree = if (dbRoot != null) subtree(dbRoot, rows, children) else emptyList()
                    val dbPids = dbTree.map { it.pid }.toSet()

                    val gameTree: List<Row> = if (!gameUp) emptyList() else {
                        val gr = Controller.gamePid()
                        if (gr != null) subtree(gr, rows, children)
                        else children[me].orEmpty().filter { it.pid !in dbPids }.flatMap { subtree(it.pid, rows, children) }
                    }
                    val appTree = listOfNotNull(rows[me])

                    if (gameUp) game = usage(gameTree, prev, dt, sGame) else sGame.clear()
                    if (dbUp) db = usage(dbTree, prev, dt, sDb) else sDb.clear()
                    app = usage(appTree, prev, dt, sApp)

                    val next = HashMap<Int, Long>()
                    for (r in gameTree) next[r.pid] = r.ticks
                    for (r in dbTree) next[r.pid] = r.ticks
                    for (r in appTree) next[r.pid] = r.ticks
                    prev = next
                    prevAt = now
                } else {
                    prev = emptyMap(); prevAt = 0L
                    sGame.clear(); sDb.clear(); sApp.clear()
                }

                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                var rxBps = 0L
                var txBps = 0L
                if (prevRx >= 0 && rx >= 0 && now > prevNetAt) {
                    val sec = (now - prevNetAt) / 1000.0
                    rxBps = ((rx - prevRx).coerceAtLeast(0) / sec).toLong()
                    txBps = ((tx - prevTx).coerceAtLeast(0) / sec).toLong()
                }
                prevRx = rx; prevTx = tx; prevNetAt = now

                val ctx = Env.ctx
                val mi = ActivityManager.MemoryInfo()
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)

                val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val temp = bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
                val plugged = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

                val thermal = if (Build.VERSION.SDK_INT >= 29) {
                    when ((ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus) {
                        0 -> "Normal"
                        1 -> "Hangat"
                        2 -> "Panas sedang"
                        3 -> "Panas parah"
                        4 -> "Kritis"
                        else -> "Darurat"
                    }
                } else null

                val dir = Env.serverDir

                _snapshot.value = PerfSnapshot(
                    game = game, db = db, app = app,
                    gameUptimeMs = if (gameUp) now - Controller.gameStartedAt else 0,
                    dbUptimeMs = if (dbUp) now - Controller.dbStartedAt else 0,
                    memTotalKb = mi.totalMem / 1024,
                    memAvailKb = mi.availMem / 1024,
                    lowMemory = mi.lowMemory,
                    batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                    batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else Float.NaN,
                    charging = plugged,
                    thermal = thermal,
                    rxBps = rxBps, txBps = txBps,
                    diskFreeB = dir.freeSpace, diskTotalB = dir.totalSpace
                )
            } catch (_: Throwable) {
            }
            delay(wait)
        }
    }
}
