package id.omphub

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Alamat LAN utama server (`<IP>:<port>`), atau null kalau tidak ada IP LAN. */
fun primaryLanAddress(): String? =
    localIps().firstOrNull()?.let { "$it:${Prefs.port}" }

/** Render teks menjadi bitmap QR hitam-putih. Null kalau gagal. */
fun qrBitmap(text: String, size: Int = 512): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    val black = android.graphics.Color.BLACK
    val white = android.graphics.Color.WHITE
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) black else white
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** Bagikan alamat server lewat Sharesheet sistem. */
fun shareServerAddress(ctx: Context, address: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Main di server OmpHub saya: $address")
    }
    ctx.startActivity(Intent.createChooser(intent, "Bagikan alamat server"))
}
