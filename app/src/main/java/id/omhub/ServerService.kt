package id.omphub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ServerService : Service() {
    companion object {
        const val ACTION_START = "id.omphub.START"
        const val ACTION_STOP = "id.omphub.STOP"
        const val ACTION_RESTART = "id.omphub.RESTART"
        const val ACTION_START_DB = "id.omphub.START_DB"
        const val ACTION_STOP_DB = "id.omphub.STOP_DB"
        private const val CHANNEL = "omphub"
        private const val NOTIF_ID = 1

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, ServerService::class.java).setAction(action)
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicInteger(0)
    private var wake: PowerManager.WakeLock? = null
    private var watching = false
    private var watchdogStarted = false
    private var playerWatchStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIF_ID, notification("OmpHub aktif"))
        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omphub:server").apply { acquire() }
        }
        watch()
        startWatchdog()
        startPlayerWatcher()
        PerfMonitor.ensureStarted()

        val action = intent?.action
        busy.incrementAndGet()
        Controller.scope.launch {
            try {
                when (action) {
                    ACTION_START -> Controller.startAll()
                    ACTION_STOP -> Controller.stopAll()
                    ACTION_RESTART -> { Controller.stopGame(); Controller.startGame() }
                    ACTION_START_DB -> Controller.startDb()
                    ACTION_STOP_DB -> Controller.stopDb()
                }
            } catch (e: Throwable) {
                Controller.gameLog.add("[panel] Error: ${e.message}")
            } finally {
                busy.decrementAndGet()
                maybeStop()
            }
        }
        return START_NOT_STICKY
    }

    private fun watch() {
        if (watching) return
        watching = true
        scope.launch {
            Controller.gameState.combine(Controller.dbState) { g, d -> g to d }.collect { (g, d) ->
                val text = "${Prefs.serverType.label}: ${g.name.lowercase()}  |  MariaDB: ${d.name.lowercase()}"
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
                maybeStop()
            }
        }
    }

    /**
     * Watchdog: kalau game server / MariaDB mati sendiri (bukan karena user pencet Stop),
     * nyalakan lagi otomatis. Backoff 5 detik, maks 5x restart dalam 10 menit lalu berhenti
     * + beri tahu user. Berhenti manual (wantGame/wantDb = false) tidak pernah di-restart.
     */
    private val gameRestarts = mutableListOf<Long>()
    private val dbRestarts = mutableListOf<Long>()

    private fun startWatchdog() {
        if (watchdogStarted) return
        watchdogStarted = true
        scope.launch {
            var prev = Controller.gameState.value
            Controller.gameState.collect { state ->
                if (prev == RunState.RUNNING && state == RunState.STOPPED) handleUnexpectedStop(isGame = true)
                prev = state
            }
        }
        scope.launch {
            var prev = Controller.dbState.value
            Controller.dbState.collect { state ->
                if (prev == RunState.RUNNING && state == RunState.STOPPED) handleUnexpectedStop(isGame = false)
                prev = state
            }
        }
    }

    private suspend fun handleUnexpectedStop(isGame: Boolean) {
        val want = if (isGame) Controller.wantGame else Controller.wantDb
        if (!want) return // user yang berhentiin — jangan restart, jangan notif
        val what = if (isGame) "Server game" else "MariaDB"
        if (!Prefs.watchdogEnabled) {
            Notifier.serverDied(this, "$what berhenti mendadak.")
            return
        }
        // Tunggu 5 detik lalu cek ulang: restart manual (tombol Restart) juga melewati
        // transisi RUNNING -> STOPPED, tapi sesudah itu state sudah RUNNING lagi.
        delay(5000)
        val state = if (isGame) Controller.gameState.value else Controller.dbState.value
        val stillWant = if (isGame) Controller.wantGame else Controller.wantDb
        if (state != RunState.STOPPED || !stillWant) return
        val restarts = if (isGame) gameRestarts else dbRestarts
        val now = SystemClock.elapsedRealtime()
        restarts.removeAll { now - it > 10 * 60 * 1000 }
        if (restarts.size >= 5) {
            if (isGame) Controller.wantGame = false else Controller.wantDb = false
            Notifier.watchdogGaveUp(this, what)
            return
        }
        restarts.add(now)
        Notifier.watchdogRestart(this, what)
        Controller.scope.launch { if (isGame) Controller.startGame() else Controller.startDb() }
    }

    /**
     * Pantau pemain lewat query UDP tiap 5 detik (di service, supaya jalan walau
     * Dashboard tidak dibuka). Join/leave dikirim ke Notifier (digabung, anti-spam).
     */
    private fun startPlayerWatcher() {
        if (playerWatchStarted) return
        playerWatchStarted = true
        scope.launch {
            var prev = emptySet<String>()
            var needSeed = true
            while (true) {
                val running = Controller.gameState.value == RunState.RUNNING
                if (running && Prefs.notifyEvents) {
                    val current = withContext(Dispatchers.IO) {
                        Samp.playersDetailed(Prefs.port).map { it.name }.toSet()
                    }
                    if (needSeed) {
                        // Poll pertama setelah server jalan: jadikan baseline, jangan notif.
                        prev = current
                        needSeed = false
                    } else {
                        (current - prev).forEach { Notifier.playerEvent(this@ServerService, "$it bergabung") }
                        (prev - current).forEach { Notifier.playerEvent(this@ServerService, "$it keluar") }
                        prev = current
                    }
                } else {
                    prev = emptySet()
                    needSeed = true
                }
                delay(5000)
            }
        }
    }

    private fun maybeStop() {
        // Jangan matikan service kalau watchdog masih berniat restart sesuatu.
        val watchdogHolding = Prefs.watchdogEnabled && (Controller.wantGame || Controller.wantDb)
        if (busy.get() == 0 &&
            Controller.gameState.value == RunState.STOPPED &&
            Controller.dbState.value == RunState.STOPPED &&
            !watchdogHolding
        ) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Server", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun actionIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, ServerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("OmpHub")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
        // Aksi cepat di notifikasi: Start saat berhenti, Restart + Stop saat jalan.
        when (Controller.gameState.value) {
            RunState.RUNNING -> {
                builder.addAction(0, "Restart", actionIntent(ACTION_RESTART, 3))
                builder.addAction(0, "Stop", actionIntent(ACTION_STOP, 1))
            }
            RunState.STOPPED -> builder.addAction(0, "Start", actionIntent(ACTION_START, 2))
            else -> builder.addAction(0, "Stop", actionIntent(ACTION_STOP, 1))
        }
        return builder.build()
    }

    override fun onDestroy() {
        wake?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }
}
