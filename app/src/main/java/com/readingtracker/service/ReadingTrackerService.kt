package com.readingtracker.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.readingtracker.Constants
import com.readingtracker.MainActivity
import com.readingtracker.Prefs
import com.readingtracker.R
import com.readingtracker.data.AppDatabase
import com.readingtracker.data.Book
import com.readingtracker.data.BookActions
import com.readingtracker.data.ReadingSession
import com.readingtracker.diag.Diag
import com.readingtracker.overlay.OverlayManager
import com.readingtracker.widget.WidgetUpdater
import kotlinx.coroutines.*

class ReadingTrackerService : Service() {

    companion object {
        /** Пауза таймера, если нет перелистываний столько времени. */
        const val IDLE_MS = 3 * 60_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var db: AppDatabase
    private lateinit var overlay: OverlayManager
    private val handler = Handler(Looper.getMainLooper())

    private var trackedApps: Map<String, String> = emptyMap()

    private var currentForegroundPkg: String? = null
    private var lastEventTime = 0L
    private var lastReportedPkg: String? = null

    // Читалка на переднем плане (null = нет)
    private var foregroundReadingApp: String? = null

    private var activeBookId: Long? = null
    @Volatile private var activeSessionId: Long? = null

    private var isScreenOn = true

    // Пауза по бездействию
    private var signalsAtEntry = 0
    private var sessionResumedAt = 0L
    private var idle = false
    private var idleSince = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    pauseSession()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    idle = false
                    val pkg = foregroundReadingApp
                    val bookId = activeBookId
                    if (pkg != null && bookId != null && activeSessionId == null) {
                        startSessionInternal(bookId, pkg)
                    }
                }
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                pollForegroundApp()
                checkIdle()
                Diag.set(this@ReadingTrackerService, Diag.HEARTBEAT, Diag.time())
            } catch (e: Exception) {
                Diag.set(this@ReadingTrackerService, Diag.ERROR, "${Diag.time()} опрос: $e")
            }
            handler.postDelayed(this, Constants.POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        overlay = OverlayManager(this)
        trackedApps = Constants.detectTrackedApps(this)
        Diag.set(this, Diag.TRACKED,
            if (trackedApps.isEmpty()) "ни одной"
            else trackedApps.entries.joinToString("\n") { "${it.value} (${it.key})" })
        lastEventTime = System.currentTimeMillis() - 60_000

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        // Закрываем сессию, оставшуюся открытой после падения процесса
        scope.launch {
            db.sessionDao().getOpenSession()?.let { orphan ->
                val end = System.currentTimeMillis()
                db.sessionDao().updateSession(orphan.copy(endTime = end))
                accumulateTime(orphan.bookId, orphan.startTime, end)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        unregisterReceiver(screenReceiver)
        pauseSession()
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Какое приложение на экране ────────────────────────────────────────────

    private fun pollForegroundApp() {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastEventTime, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentForegroundPkg = e.packageName
            }
            lastEventTime = maxOf(lastEventTime, e.timeStamp + 1)
        }

        val pkg = currentForegroundPkg
        if (pkg != null && pkg != lastReportedPkg) {
            lastReportedPkg = pkg
            Diag.set(this, Diag.FOREGROUND, "${Diag.time()} $pkg")
        }
        // Наш собственный пузырёк/приложение не прерывают чтение
        if (pkg == packageName) return

        val newReadingApp = if (pkg != null && pkg in trackedApps) pkg else null

        when {
            newReadingApp != null && newReadingApp != foregroundReadingApp -> {
                foregroundReadingApp = newReadingApp
                onReadingAppEntered(newReadingApp)
            }
            newReadingApp == null && foregroundReadingApp != null -> {
                foregroundReadingApp = null
                overlay.hide()
                pauseSession()
            }
        }
    }

    // ── Пауза, если 3 минуты нет перелистываний ───────────────────────────────

    private fun checkIdle() {
        val pkg = foregroundReadingApp ?: return
        val bookId = activeBookId ?: return
        if (!ActivitySignal.accessibilityConnected || !isScreenOn) return

        // Доверяем сигналу, только если читалка вообще присылает события
        val signals = ActivitySignal.count(pkg) - signalsAtEntry
        if (signals < 2) return

        val now = System.currentTimeMillis()
        val lastActivity = maxOf(ActivitySignal.last(pkg) ?: 0L, sessionResumedAt)

        if (activeSessionId != null && !idle && now - lastActivity > IDLE_MS) {
            idle = true
            idleSince = now
            // Последнюю страницу засчитываем: конец сессии через минуту после последнего действия
            pauseSession(endAt = minOf(now, lastActivity + 60_000))
            Diag.set(this, Diag.EVENT, "${Diag.time()} пауза: 3 мин без перелистываний")
        } else if (idle && (ActivitySignal.last(pkg) ?: 0L) > idleSince) {
            idle = false
            startSessionInternal(bookId, pkg)
            Diag.set(this, Diag.EVENT, "${Diag.time()} чтение продолжено")
        }
    }

    // ── Вход в читалку ────────────────────────────────────────────────────────

    private fun onReadingAppEntered(pkg: String) {
        if (!isScreenOn) return
        pauseSession()
        idle = false
        signalsAtEntry = ActivitySignal.count(pkg)
        val appName = trackedApps[pkg] ?: pkg
        Diag.set(this, Diag.EVENT, "${Diag.time()} вход в $appName")

        // Книга, только что открытая через трекер-посредник: пузырёк не нужен
        val pending = Prefs.takePendingBook(this, pkg)
        if (pending != null) {
            activeBookId = pending
            startSessionInternal(pending, pkg)
            return
        }

        scope.launch {
            val rememberedBookId = db.sessionDao().getLastBookIdForApp(pkg)
            withContext(Dispatchers.Main) {
                try {
                    overlay.show(
                        appName = appName,
                        rememberedBookId = rememberedBookId,
                        db = db,
                        onContinue = { bookId ->
                            overlay.hide()
                            activeBookId = bookId
                            startSessionInternal(bookId, pkg)
                        },
                        onNewBook = { title, author ->
                            overlay.hide()
                            scope.launch {
                                val bookId = saveNewBook(title, author, pkg, appName)
                                activeBookId = bookId
                                startSessionInternal(bookId, pkg)
                            }
                        },
                        onDismiss = { overlay.hide() }
                    )
                } catch (e: Exception) {
                    Diag.set(this@ReadingTrackerService, Diag.ERROR, "${Diag.time()} пузырёк: $e")
                }
            }
        }
    }

    private fun startSessionInternal(bookId: Long, pkg: String) {
        sessionResumedAt = System.currentTimeMillis()
        scope.launch {
            if (activeSessionId != null) return@launch
            val session = ReadingSession(
                bookId = bookId,
                startTime = System.currentTimeMillis(),
                appPackage = pkg
            )
            activeSessionId = db.sessionDao().insertSession(session)
        }
    }

    private fun pauseSession(endAt: Long = System.currentTimeMillis()) {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        val bookId = activeBookId ?: return

        scope.launch {
            val session = db.sessionDao().getSessionById(sessionId) ?: return@launch
            val endTime = maxOf(session.startTime, endAt)
            db.sessionDao().updateSession(session.copy(endTime = endTime))
            accumulateTime(bookId, session.startTime, endTime)
            WidgetUpdater.update(this@ReadingTrackerService)
        }
    }

    private suspend fun accumulateTime(bookId: Long, startTime: Long, endTime: Long) {
        val minutes = (endTime - startTime) / 60_000
        if (minutes <= 0) return
        val book = db.bookDao().getBookById(bookId) ?: return
        db.bookDao().updateBook(book.copy(totalMinutes = book.totalMinutes + minutes))
    }

    private suspend fun saveNewBook(title: String, author: String, pkg: String, appName: String): Long =
        BookActions.addNewBook(this, title, author, pkg, appName)

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Трекер чтения активен")
            .setContentText("Нажмите, чтобы открыть")
            .setSmallIcon(R.drawable.ic_book_notify)
            .setContentIntent(pi)
            .setSilent(true)
            .build()
    }
}
