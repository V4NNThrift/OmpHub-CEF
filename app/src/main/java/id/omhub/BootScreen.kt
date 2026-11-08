package id.omphub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BootScreen() {
    val gate by Controller.bootGate.collectAsStateWithLifecycle()
    val step by Controller.bootStep.collectAsStateWithLifecycle()
    val error by Controller.bootError.collectAsStateWithLifecycle()
    val log by Controller.setupLog.lines.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (gate == BootGate.FAILED) {
            Text("Persiapan belum berhasil", style = MaterialTheme.typography.titleLarge)
            Text(
                error ?: "Terjadi kesalahan yang tidak diketahui.",
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

        CircularProgressIndicator(Modifier.size(48.dp))
        Text(
            "Menyiapkan OmpHub",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            step,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp))
        Text(
            log.lastOrNull().orEmpty().trim(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Hanya perlu sekali. Jangan tutup app sampai selesai.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}
