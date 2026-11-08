package id.omphub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun LogView(lines: List<String>, modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    val listState = rememberLazyListState()
    // Auto-scroll halus ke baris terbaru (bukan lompat mendadak).
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) runCatching { listState.animateScrollToItem(lines.lastIndex) }
    }
    if (lines.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.Center) {
            Text(
                "Belum ada log. Jalankan server untuk melihat output di sini.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    LazyColumn(modifier, state = listState) {
        items(lines.size) { i ->
            Text(
                lines[i],
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = textColor
            )
        }
    }
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "$label disalin", Toast.LENGTH_SHORT).show()
}

suspend fun toast(ctx: Context, msg: String) = withContext(Dispatchers.Main) {
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

fun localIps(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filter { !it.isLoopbackAddress && it is Inet4Address }
        .mapNotNull { it.hostAddress }
}.getOrDefault(emptyList())

/**
 * IP Tailscale HP ini (100.64.0.0/10, interface "tailscale0").
 * Mengembalikan null kalau Tailscale tidak jalan. Dipakai supaya teman satu
 * Tailnet bisa join dari mana saja, bukan cuma satu WiFi.
 */
fun tailscaleIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().firstNotNullOfOrNull { iface ->
        val name = iface.name.lowercase()
        iface.inetAddresses.toList().firstNotNullOfOrNull { addr ->
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                val host = addr.hostAddress ?: return@firstNotNullOfOrNull null
                val octets = host.split(".")
                val inCgnat = octets.size == 4 && octets[0] == "100" &&
                    (octets[1].toIntOrNull() ?: -1) in 64..127
                if (name.contains("tailscale") || (name.startsWith("tun") && inCgnat)) host else null
            } else null
        }
    }
}.getOrNull()

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "${b / (1024 * 1024)} MB"
}
