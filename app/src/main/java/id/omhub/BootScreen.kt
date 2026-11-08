package id.omphub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Layar boot ala macOS: latar hitam, logo di tengah, progress bar tipis.
 * Logika sama seperti sebelumnya (gate/step/error + tombol coba lagi /
 * buka panel saat gagal).
 */
@Composable
fun BootScreen() {
    val gate by Controller.bootGate.collectAsStateWithLifecycle()
    val step by Controller.bootStep.collectAsStateWithLifecycle()
    val error by Controller.bootError.collectAsStateWithLifecycle()
    val log by Controller.setupLog.lines.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + scaleIn(
            spring(dampingRatio = 0.8f, stiffness = 300f),
            initialScale = 0.92f
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrafficLights()
                Spacer(Modifier.height(28.dp))
                Text(
                    "OmpHub",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "open.mp + CEF 7058",
                    color = Color(0xFF98989D),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(36.dp))

                if (gate == BootGate.FAILED) {
                    Text(
                        "Persiapan belum berhasil",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        error ?: "Terjadi kesalahan yang tidak diketahui.",
                        color = Color(0xFFD7D7D7),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Button(onClick = { Controller.retryBootSetup() }) { Text("Coba lagi") }
                        OutlinedButton(onClick = { Controller.skipBootSetup() }) { Text("Buka panel") }
                    }
                    return@Column
                }

                LinearProgressIndicator(
                    modifier = Modifier.width(200.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                Text(
                    "Menyiapkan OmpHub",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    step,
                    color = Color(0xFFD7D7D7),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    log.lastOrNull().orEmpty().trim(),
                    color = Color(0xFF98989D),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Hanya perlu sekali. Jangan tutup app sampai selesai.",
                    color = Color(0xFF98989D),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
    }
}
