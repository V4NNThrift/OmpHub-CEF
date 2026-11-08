package id.omphub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Locale

fun fmtMem(kb: Long): String = when {
    kb < 1024 -> "$kb KB"
    kb < 1024 * 1024 -> "${kb / 1024} MB"
    else -> String.format(Locale.US, "%.1f GB", kb / 1024.0 / 1024.0)
}

fun fmtBytes(b: Long): String = fmtMem(b / 1024)

fun fmtRate(bps: Long): String = when {
    bps < 1024 -> "$bps B/s"
    bps < 1024 * 1024 -> String.format(Locale.US, "%.1f KB/s", bps / 1024.0)
    else -> String.format(Locale.US, "%.2f MB/s", bps / 1024.0 / 1024.0)
}

fun fmtUptime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val d = s % 60
    return if (h > 0) String.format(Locale.US, "%dj %02dm %02dd", h, m, d)
    else String.format(Locale.US, "%dm %02dd", m, d)
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier, minMax: Float = 1f) {
    val line = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        if (values.size < 2) return@Canvas
        val top = maxOf(minMax, values.maxOrNull() ?: 0f)
        val step = size.width / 59f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width - (values.lastIndex - i) * step
            val y = size.height - (v / top) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProcBlock(title: String, u: ProcUsage, cores: Int, uptimeMs: Long?) {
    val ofTotal = u.cpuPct / (cores * 100f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Metric("CPU", String.format(Locale.US, "%.0f%%  (%.2f dari %d core)", u.cpuPct, u.cpuPct / 100f, cores))
        LinearProgressIndicator(progress = { ofTotal.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Sparkline(u.cpuHist, minMax = 100f)
        Metric("RAM", fmtMem(u.rssKb))
        Sparkline(u.ramHistMb, minMax = 64f)
        Metric("Thread / proses", "${u.threads} / ${u.procs}")
        if (uptimeMs != null) Metric("Uptime", fmtUptime(uptimeMs))
    }
}

@Composable
fun PerfContent(p: PerfSnapshot, queryMs: Long?) {
    val gameLabel = Prefs.serverType.label
    p.game?.let { ProcBlock(gameLabel, it, p.cores, p.gameUptimeMs) }
    p.db?.let { ProcBlock("MariaDB", it, p.cores, p.dbUptimeMs) }
    p.app?.let { ProcBlock("Panel (app)", it, p.cores, null) }
    if (p.game == null && p.db == null) Text("Server belum berjalan. Grafik CPU/RAM muncul setelah Start.")

    Text("Perangkat", style = MaterialTheme.typography.titleSmall)
    if (queryMs != null) Metric("Respons query", "$queryMs ms")
    if (p.memTotalKb > 0) {
        val used = p.memTotalKb - p.memAvailKb
        Metric("RAM HP terpakai", "${fmtMem(used)} / ${fmtMem(p.memTotalKb)}")
        LinearProgressIndicator(progress = { (used.toFloat() / p.memTotalKb).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        if (p.lowMemory) Text("Memori HP hampir habis, Android bisa mematikan server.", color = MaterialTheme.colorScheme.error)
    }
    p.thermal?.let { Metric("Status termal", it) }
    Metric("Jaringan ↓ / ↑", "${fmtRate(p.rxBps)} / ${fmtRate(p.txBps)}")
    if (p.diskTotalB > 0) Metric("Penyimpanan bebas", "${fmtBytes(p.diskFreeB)} / ${fmtBytes(p.diskTotalB)}")
    Text(
        "CPU dihitung dari proses server (proot + qemu), 100% = 1 core penuh. Jaringan loopback (127.0.0.1) tidak ikut terhitung.",
        style = MaterialTheme.typography.bodySmall
    )
}
