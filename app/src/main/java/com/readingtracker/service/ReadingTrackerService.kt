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

        /** Если читалка не на экране (экран включён) столько времени — служба выгружается. */
        const val LEAVE_STOP_MS = 60_000L

        /** Без открытой книги форма раскрывается сама через столько времени в читалке. */
        const val ASK_AFTER_MS = 5 * 60_000L

        /** Ручной таймер на паузе дольше этого — останавливается. */
        const val MANUAL_GIVE_UP_MS = 20 * 60_000L

        const val EXTRA_READER_PKG = "reader_pkg"
        const val EXTRA_MANUAL_BOOK = "manual_book"
        const val ACTION_STOP_MANUAL = "com.readingtracker.STOP_MANUAL"

        /** Запуск службы на время чтения. Вызывается из ярлыка/кнопки читалки. */
        fun startForReader(context: Context, pkg: String) {
            val i = Intent(context, ReadingTrackerService::class.java).putExtra(EXTRA_READER_PKG, pkg)
            context.startForegroundService(i)
        }
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
    private var screenOffAt = 0L

    // Когда читалку покинули (экран включён); 0 — сейчас в читалке
    private var outsideSince = 0L
    private var startedFor: String? = null

    // Пауза по бездействию
    private var signalsAtEntry = 0
    private var sessionResumedAt = 0L
    private var idle = false
    private var idleSince = 0L

    // Читаем без открытой книги: время копится отрезками, пока человек не назовёт книгу
    private var unassignedPkg: String? = null
    private val unassigned = mutableListOf<LongArray>()
    private var unassignedStart = 0L
    private var askedAutomatically = false
    private var formOpen = false
    private val autoAsk = Runnable { if (unassignedPkg != null && !formOpen) openForm() }

    // Ручной таймер
    private var manual = false
    private var manualIdleSince = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenOffAt = System.currentTimeMillis()
                    pauseSession()
                    closeUnassigned()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    idle = false
                    if (manual) {
                        if (System.currentTimeMillis() - screenOffAt > MANUAL_GIVE_UP_MS) stopManual("экран был выключен долго")
                        else activeBookId?.let { startSessionInternal(it, manualPkg) }
                        return
                    }
                    val pkg = foregroundReadingApp
                    val bookId = activeBookId
                    if (pkg != null && bookId != null && activeSessionId == null) {
                        startSessionInternal(bookId, pkg)
                    } else if (pkg != null && pkg == unassignedPkg) {
                        unassignedStart = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                if (manual) checkManualIdle()
                else {
                    pollForegroundApp()
                    checkIdle()
                    checkScreen()
                }
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
        if (intent?.action == ACTION_STOP_MANUAL) {
            stopManual("остановлен вручную")
            return START_NOT_STICKY
        }
        val manualBook = intent?.getLongExtra(EXTRA_MANUAL_BOOK, -1L) ?: -1L
        val pkg = intent?.getStringExtra(EXTRA_READER_PKG)
        if (manualBook > 0) {
            startManual(manualBook)
        } else if (pkg != null) {
            // Открыли читалку: ручной таймер уступает обычному отслеживанию
            if (manual) endManualMode()
            startedFor = pkg
            // Свежий запуск читалки: даём ей время появиться на экране
            outsideSince = 0L
            lastEventTime = System.currentTimeMillis() - 5_000
            if (pkg !in trackedApps) trackedApps = trackedApps + (pkg to (Constants.detectTrackedApps(this)[pkg] ?: pkg))
        }
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        // Без ярлыка не перезапускаемся: служба живёт только вместе с чтением
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(autoAsk)
        unregisterReceiver(screenReceiver)
        pauseSession()
        if (manual) endManualMode(notify = false)
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Ручной таймер ─────────────────────────────────────────────────────────

    private var manualPkg = Book.PKG_OTHER

    private fun startManual(bookId: Long) {
        pauseSession()
        closeUnassigned(); clearUnassigned()
        overlay.hide()
        foregroundReadingApp = null
        manual = true
        manualIdleSince = 0L
        idle = false
        activeBookId = bookId
        ManualTimer.bookId = bookId
        ManualTimer.since = System.currentTimeMillis()
        ActivitySignal.manual = true
        ReaderAccessibilityService.widen(true)
        signalsAtEntry = ActivitySignal.countAny()
        scope.launch {
            manualPkg = db.bookDao().getBookById(bookId)?.appPackage ?: Book.PKG_OTHER
            withContext(Dispatchers.Main) {
                startSessionInternal(bookId, manualPkg)
                updateNotification()
            }
        }
        Diag.set(this, Diag.EVENT, "${Diag.time()} ручной таймер запущен")
        ManualTimer.changed()
    }

    /** Ручной режим выключается, сессия закрывается; служба продолжает работу. */
    private fun endManualMode(notify: Boolean = true) {
        pauseSession()
        manual = false
        activeBookId = null
        ManualTimer.bookId = null
        ActivitySignal.manual = false
        ReaderAccessibilityService.widen(false)
        ManualTimer.changed()
        if (notify) updateNotification()
    }

    private fun stopManual(reason: String) {
        if (manual) endManualMode(notify = foregroundReadingApp != null)
        Diag.set(this, Diag.EVENT, "${Diag.time()} ручной таймер: $reason")
        if (foregroundReadingApp == null) stopSelf()
    }

    /** Перелистываний нет 3 минуты — пауза; снова листают — продолжаем; тишина 20 минут — стоп. */
    private fun checkManualIdle() {
        val bookId = activeBookId ?: return
        if (!ActivitySignal.accessibilityConnected || !isScreenOn) return
        val now = System.currentTimeMillis()
        val lastActivity = maxOf(ActivitySignal.lastAny(), sessionResumedAt)
        if (activeSessionId != null && !idle && now - lastActivity > IDLE_MS) {
            idle = true
            idleSince = now
            pauseSession(endAt = minOf(now, lastActivity + 60_000))
            Diag.set(this, Diag.EVENT, "${Diag.time()} ручной таймер: пауза, нет перелистываний")
        } else if (idle && ActivitySignal.lastAny() > idleSince) {
            idle = false
            startSessionInternal(bookId, manualPkg)
        } else if (idle && now - idleSince > MANUAL_GIVE_UP_MS) {
            stopManual("долго нет перелистываний")
        }
    }

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

        // Читалка давно не на экране — выгружаемся
        if (newReadingApp == null && isScreenOn) {
            val now = System.currentTimeMillis()
            if (outsideSince == 0L) outsideSince = now
            else if (now - outsideSince > LEAVE_STOP_MS) {
                Diag.set(this, Diag.EVENT, "${Diag.time()} чтение закончено, трекер выгружен")
                stopSelf()
                return
            }
        } else if (newReadingApp != null) {
            outsideSince = 0L
        }

        when {
            newReadingApp != null && newReadingApp != foregroundReadingApp -> {
                foregroundReadingApp = newReadingApp
                onReadingAppEntered(newReadingApp)
            }
            newReadingApp == null && foregroundReadingApp != null -> {
                foregroundReadingApp = null
                overlay.hide()
                formOpen = false
                offeredBookId = null
                handler.removeCallbacks(autoAsk)
                pauseSession()
                closeUnassigned()
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
        enteredAt = System.currentTimeMillis()
        handledTitle = null
        offeredBookId = null
        val appName = trackedApps[pkg] ?: pkg
        Diag.set(this, Diag.EVENT, "${Diag.time()} вход в $appName")

        // Книга, только что открытая через трекер-посредник: пузырёк не нужен
        val pending = Prefs.takePendingBook(this, pkg)
        if (pending != null) {
            clearUnassigned()
            activeBookId = pending
            startSessionInternal(pending, pkg)
            return
        }

        scope.launch {
            val remembered = db.sessionDao().getLastBookIdForApp(pkg)?.let { db.bookDao().getBookById(it) }
            // Дочитанную или брошенную книгу не предлагаем: открытой книги нет
            val open = remembered?.takeIf { it.status == Book.STATUS_READING || it.status == Book.STATUS_PAUSED }
            withContext(Dispatchers.Main) {
                if (foregroundReadingApp != pkg) return@withContext
                try {
                    if (open == null) startUnassigned(pkg, appName)
                    else offerContinue(pkg, appName, open.id)
                } catch (e: Exception) {
                    Diag.set(this@ReadingTrackerService, Diag.ERROR, "${Diag.time()} пузырёк: $e")
                }
            }
        }
    }

    // ── Без открытой книги ────────────────────────────────────────────────────

    /** Книги нет: время пишется с этой минуты, на экране только значок «＋ книга». */
    private fun startUnassigned(pkg: String, appName: String) {
        if (unassignedPkg != pkg) { clearUnassigned(); unassignedPkg = pkg }
        activeBookId = null
        unassignedStart = System.currentTimeMillis()
        pendingAppName = appName
        showPill()
        if (!askedAutomatically) {
            handler.removeCallbacks(autoAsk)
            val already = unassigned.sumOf { it[1] - it[0] }
            handler.postDelayed(autoAsk, (ASK_AFTER_MS - already).coerceAtLeast(5_000))
        }
    }

    private var pendingAppName = ""

    private fun showPill() {
        formOpen = false
        overlay.showPill { openForm() }
    }

    private fun openForm() {
        val pkg = unassignedPkg ?: return
        if (foregroundReadingApp != pkg) return
        askedAutomatically = true
        formOpen = true
        // Название с экрана читалки, если оно есть
        val seen = ReaderScreen.title?.takeIf { ReaderScreen.titlePkg == pkg && ReaderScreen.titleAt >= enteredAt }
        overlay.showForm(pendingAppName,
            onNewBook = { title, author -> assignNewBook(title, author, pkg, pendingAppName) },
            onDismiss = { showPill() },
            title = seen ?: "",
            author = seen?.let { ReaderScreen.authorFor(this, it) } ?: "")
    }

    private fun closeUnassigned() {
        if (unassignedStart > 0) {
            val now = System.currentTimeMillis()
            if (now - unassignedStart > 1000) unassigned += longArrayOf(unassignedStart, now)
            unassignedStart = 0
        }
    }

    private fun clearUnassigned() {
        unassigned.clear()
        unassignedStart = 0
        unassignedPkg = null
        askedAutomatically = false
        handler.removeCallbacks(autoAsk)
    }

    /** Новая книга названа: накопленное время записывается на неё, дальше обычная сессия. */
    private fun assignNewBook(title: String, author: String, pkg: String, appName: String) {
        overlay.hide()
        formOpen = false
        offeredBookId = null
        // Книгу назвали сами: название на экране сейчас считается ею, второй раз не спрашиваем
        ReaderScreen.title?.takeIf { ReaderScreen.titlePkg == pkg && ReaderScreen.titleAt >= enteredAt }
            ?.let { handledTitle = it }
        // Шла другая книга — её подход закрывается
        pauseSession()
        handler.removeCallbacks(autoAsk)
        closeUnassigned()
        val pieces = if (unassignedPkg == pkg) unassigned.toList() else emptyList()
        clearUnassigned()
        scope.launch {
            val bookId = BookActions.addNewBook(this@ReadingTrackerService, title, author, pkg, appName)
            for (p in pieces) {
                db.sessionDao().insertSession(ReadingSession(bookId = bookId, startTime = p[0], endTime = p[1], appPackage = pkg))
                accumulateTime(bookId, p[0], p[1])
            }
            withContext(Dispatchers.Main) {
                activeBookId = bookId
                if (foregroundReadingApp == pkg && isScreenOn) startSessionInternal(bookId, pkg)
            }
        }
    }

    // ── Книга с экрана читалки (ЛитРес) ──────────────────────────────────────

    private var enteredAt = 0L
    /** Название, о котором уже спросили (или которое уже идёт) с этого входа в читалку. */
    private var handledTitle: String? = null
    /** Книга в пузырьке «продолжаем?», который сейчас на экране. */
    private var offeredBookId: Long? = null
    private var pagesSeenAt = 0L

    /** Пузырёк «продолжаем эту книгу?». */
    private fun offerContinue(pkg: String, appName: String, bookId: Long) {
        offeredBookId = bookId
        formOpen = false
        overlay.show(
            appName = appName,
            rememberedBookId = bookId,
            db = db,
            onContinue = { id -> continueBook(id, pkg) },
            onNewBook = { title, author -> assignNewBook(title, author, pkg, appName) },
            onDismiss = { dismissOffer(pkg) }
        )
    }

    private fun dismissOffer(pkg: String) {
        offeredBookId = null
        formOpen = false
        // Без открытой книги остаётся значок «＋ книга»
        if (activeBookId == null && unassignedPkg == pkg) showPill() else overlay.hide()
    }

    /** Продолжаем книгу: прежний подход закрыт, время без книги (если копилось) идёт ей. */
    private fun continueBook(bookId: Long, pkg: String) {
        overlay.hide()
        offeredBookId = null
        formOpen = false
        handler.removeCallbacks(autoAsk)
        if (activeBookId == bookId && activeSessionId != null) return
        pauseSession()
        closeUnassigned()
        val pieces = if (unassignedPkg == pkg) unassigned.toList() else emptyList()
        clearUnassigned()
        activeBookId = bookId
        if (pieces.isNotEmpty()) scope.launch {
            for (p in pieces) {
                db.sessionDao().insertSession(ReadingSession(bookId = bookId, startTime = p[0], endTime = p[1], appPackage = pkg))
                accumulateTime(bookId, p[0], p[1])
            }
        }
        if (foregroundReadingApp == pkg && isScreenOn) startSessionInternal(bookId, pkg)
    }

    /**
     * Название и страницы, прочитанные с экрана читалки. Новая книга на экране —
     * пузырёк «продолжаем «…»?» или форма с готовым названием; страницы идут в карточку книги.
     */
    private fun checkScreen() {
        val pkg = foregroundReadingApp ?: return
        val pagesAt = ReaderScreen.pagesAt
        if (pagesAt > pagesSeenAt && ReaderScreen.pagesPkg == pkg && pagesAt >= enteredAt) {
            pagesSeenAt = pagesAt
            val read = ReaderScreen.pagesRead; val total = ReaderScreen.pagesTotal
            val screenTitle = ReaderScreen.title
            activeBookId?.let { id ->
                scope.launch {
                    val bk = db.bookDao().getBookById(id) ?: return@launch
                    // Страницы — только той книге, что на экране
                    if (screenTitle == null || ReaderScreen.sameTitle(bk.title, screenTitle) || bk.title.isBlank())
                        db.bookDao().setPages(id, read, total)
                }
            }
        }

        val title = ReaderScreen.title ?: return
        if (ReaderScreen.titlePkg != pkg || ReaderScreen.titleAt < enteredAt) return
        if (formOpen || ReaderScreen.sameTitle(title, handledTitle)) return
        handledTitle = title
        val activeId = activeBookId
        val offered = offeredBookId
        scope.launch {
            val books = db.bookDao().getAllBooksSync()
            val active = activeId?.let { id -> books.firstOrNull { it.id == id } }
            val author = ReaderScreen.authorFor(this@ReadingTrackerService, title)
            if (active != null && ReaderScreen.sameTitle(active.title, title)) {
                // Читается та же книга: автор подтягивается из библиотеки, если его не было
                if (active.author.isBlank() && author != null) db.bookDao().fillAuthor(active.id, author)
                return@launch
            }
            val open = books.filter {
                ReaderScreen.sameTitle(it.title, title) &&
                    (it.status == Book.STATUS_READING || it.status == Book.STATUS_PAUSED)
            }
            val known = open.firstOrNull { it.appPackage == pkg } ?: open.firstOrNull()
            withContext(Dispatchers.Main) {
                if (foregroundReadingApp != pkg) return@withContext
                val appName = trackedApps[pkg] ?: pkg
                when {
                    known != null && known.id == offered -> {}
                    known != null -> offerContinue(pkg, appName, known.id)
                    else -> {
                        formOpen = true
                        offeredBookId = null
                        handler.removeCallbacks(autoAsk)
                        overlay.showForm(appName,
                            onNewBook = { t, a -> assignNewBook(t, a, pkg, appName) },
                            onDismiss = { dismissOffer(pkg) },
                            title = title, author = author ?: "")
                    }
                }
            }
        }
    }

    // ── Сессии ────────────────────────────────────────────────────────────────

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

    // ── Уведомление ───────────────────────────────────────────────────────────

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(Constants.NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_book_notify)
            .setContentIntent(pi)
            .setSilent(true)
        if (manual) {
            val stop = PendingIntent.getService(this, 1,
                Intent(this, ReadingTrackerService::class.java).setAction(ACTION_STOP_MANUAL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            b.setContentTitle("Таймер чтения идёт")
                .setContentText("Пауза сама, если 3 мин нет перелистываний")
                .setWhen(ManualTimer.since).setShowWhen(true).setUsesChronometer(true)
                .addAction(0, "стоп", stop)
        } else {
            b.setContentTitle("Идёт чтение")
                .setContentText("Трекер выгрузится сам, когда закроете читалку")
        }
        return b.build()
    }
}
