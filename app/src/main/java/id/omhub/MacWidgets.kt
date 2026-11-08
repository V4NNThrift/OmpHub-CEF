package id.omphub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Komponen bersama gaya macOS Sequoia untuk redesign total v1.5.0.
 * Semua screen baru memakai komponen ini supaya tampilannya konsisten:
 * kartu frosted, segmented control, icon tile, title bar jendela, baris
 * pengaturan ala System Settings.
 */

/** Kartu frosted macOS: panel + border tipis, sudut 16dp. */
@Composable
fun FrostCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Mac.Panel),
        border = BorderStroke(1.dp, Mac.Border.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

/** Header section kecil ala System Settings (huruf kapital + tracking). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Mac.Secondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp, top = 6.dp)
    )
}

/**
 * Segmented control ala macOS: wadah gelap berisi segmen; segmen terpilih
 * menonjol dengan panel lebih terang. Dipakai untuk pilihan mesin server,
 * versi SA-MP, dan tema gelap/terang.
 */
@Composable
fun MacSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .background(Mac.PanelInner, RoundedCornerShape(10.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Mac.Panel else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSel) Mac.Text else Mac.Secondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/** Kotak ikon berwarna ala macOS (ikon di atas warna aksen 16%). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 30.dp, iconSize: Dp = 17.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * Title bar jendela macOS: traffic-light kiri, judul di tengah, aksi di
 * kanan. Kalau tidak ada aksi, diberi spacer penyeimbang supaya judul
 * tetap di tengah.
 */
@Composable
fun WindowTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = { Spacer(Modifier.width(52.dp)) }
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TrafficLights()
        Text(
            title,
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = Mac.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/**
 * Baris pengaturan ala System Settings: icon tile + judul + deskripsi +
 * kontrol di kanan (Switch, tombol, atau chevron).
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    desc: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Mac.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (desc != null) Text(desc, color = Mac.Secondary, fontSize = 12.sp)
        }
        trailing()
    }
}
