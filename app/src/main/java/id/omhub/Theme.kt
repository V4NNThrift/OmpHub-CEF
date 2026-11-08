package id.omphub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Palet macOS dark (ala Big Sur / Sequoia dark mode).
 * Satu-satunya tempat warna UI didefinisikan — semua screen memakai objek ini
 * (atau MaterialTheme.colorScheme yang dibangun dari sini) supaya konsisten.
 */
object Mac {
    val Background = Color(0xFF1C1C1E)   // latar aplikasi
    val Panel = Color(0xFF2C2C2E)        // kartu / panel
    val PanelInner = Color(0xFF3A3A3C)   // strip / permukaan dalam kartu
    val Border = Color(0xFF3A3A3C)       // garis batas halus
    val Track = Color(0xFF48484A)        // trek gauge / progress
    val Text = Color(0xFFF5F5F7)         // teks utama
    val Secondary = Color(0xFF98989D)    // teks sekunder / label
    val Blue = Color(0xFF0A84FF)         // aksen utama macOS
    val Green = Color(0xFF30D158)        // sukses / berjalan
    val Yellow = Color(0xFFFF9F0A)       // peringatan
    val Red = Color(0xFFFF453A)          // bahaya / error

    // Warna traffic-light macOS (tombol jendela).
    val TrafficRed = Color(0xFFFF5F57)
    val TrafficYellow = Color(0xFFFEBC2E)
    val TrafficGreen = Color(0xFF28C840)
}

/** Skema warna Material 3 (dark) yang dipakai seluruh aplikasi. */
val OmpColors = darkColorScheme(
    primary = Mac.Blue,
    onPrimary = Color.White,
    secondary = Mac.Green,
    tertiary = Mac.Yellow,
    background = Mac.Background,
    surface = Mac.Panel,
    surfaceVariant = Mac.PanelInner,
    onSurface = Mac.Text,
    onSurfaceVariant = Mac.Secondary,
    outline = Mac.Border,
    error = Mac.Red,
    onError = Color.White
)

/** Bentuk macOS: kartu 12dp, tombol pill 20dp. */
val OmpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

/** Tiga dot traffic-light macOS (merah / kuning / hijau), motif jendela. */
@Composable
fun TrafficLights(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mac.TrafficRed))
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mac.TrafficYellow))
        Box(Modifier.size(12.dp).clip(CircleShape).background(Mac.TrafficGreen))
    }
}
