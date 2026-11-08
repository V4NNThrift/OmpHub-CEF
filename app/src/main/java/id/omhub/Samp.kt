package id.omphub

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Samp {
    data class Info(val password: Boolean, val players: Int, val maxPlayers: Int, val name: String, val mode: String, val language: String)
    data class Player(val name: String, val score: Int, val ping: Int)
    data class DetailedPlayer(val id: Int, val name: String, val score: Int, val ping: Int)

    private val LOCAL = byteArrayOf(127, 0, 0, 1)

    private fun packet(port: Int, type: Char, extra: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteArrayOutputStream()
        b.write("SAMP".toByteArray(Charsets.US_ASCII))
        b.write(LOCAL)
        b.write(port and 0xFF)
        b.write((port shr 8) and 0xFF)
        b.write(type.code)
        b.write(extra)
        return b.toByteArray()
    }

    private fun payload(buf: ByteArray, len: Int): ByteBuffer? =
        if (len < 11) null else ByteBuffer.wrap(buf, 11, len - 11).slice().order(ByteOrder.LITTLE_ENDIAN)

    private fun exchange(port: Int, type: Char, extra: ByteArray = ByteArray(0), timeoutMs: Int = 1500): ByteBuffer? = try {
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            val out = packet(port, type, extra)
            s.send(DatagramPacket(out, out.size, InetAddress.getByAddress(LOCAL), port))
            val buf = ByteArray(4096)
            val r = DatagramPacket(buf, buf.size)
            s.receive(r)
            payload(buf, r.length)
        }
    } catch (_: Exception) { null }

    fun info(port: Int): Info? {
        val b = exchange(port, 'i') ?: return null
        return try {
            fun str(): String {
                val n = b.int
                val a = ByteArray(n)
                b.get(a)
                return String(a, Charsets.UTF_8)
            }
            val pw = b.get().toInt() != 0
            val pl = b.short.toInt() and 0xFFFF
            val mx = b.short.toInt() and 0xFFFF
            val name = str()
            val mode = str()
            val lang = str()
            Info(pw, pl, mx, name, mode, lang)
        } catch (_: Exception) { null }
    }

    fun players(port: Int): List<Player> {
        val b = exchange(port, 'c') ?: return emptyList()
        return try {
            val n = b.short.toInt() and 0xFFFF
            List(n) {
                val len = b.get().toInt() and 0xFF
                val a = ByteArray(len)
                b.get(a)
                Player(String(a, Charsets.UTF_8), b.int, b.int)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Daftar pemain lengkap dengan playerid (paket query 'd'). Dipakai untuk kick/ban via RCON. */
    fun playersDetailed(port: Int): List<DetailedPlayer> {
        val b = exchange(port, 'd') ?: return emptyList()
        return try {
            val n = b.short.toInt() and 0xFFFF
            List(n) {
                val id = b.get().toInt() and 0xFF
                val len = b.get().toInt() and 0xFF
                val a = ByteArray(len)
                b.get(a)
                DetailedPlayer(id, String(a, Charsets.UTF_8), b.int, b.int)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun rcon(port: Int, password: String, cmd: String): List<String> {
        val pw = password.toByteArray()
        val c = cmd.toByteArray()
        val extra = ByteBuffer.allocate(4 + pw.size + c.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(pw.size.toShort()).put(pw).putShort(c.size.toShort()).put(c).array()
        val lines = mutableListOf<String>()
        try {
            DatagramSocket().use { s ->
                val out = packet(port, 'x', extra)
                s.send(DatagramPacket(out, out.size, InetAddress.getByAddress(LOCAL), port))
                var timeout = 2000
                while (true) {
                    s.soTimeout = timeout
                    val buf = ByteArray(4096)
                    val r = DatagramPacket(buf, buf.size)
                    s.receive(r)
                    val b = payload(buf, r.length) ?: break
                    while (b.remaining() >= 2) {
                        val n = b.short.toInt() and 0xFFFF
                        if (n > b.remaining()) break
                        val a = ByteArray(n)
                        b.get(a)
                        lines.add(String(a, Charsets.UTF_8))
                    }
                    timeout = 600
                }
            }
        } catch (_: Exception) { /* timeout = selesai */ }
        return lines
    }
}
