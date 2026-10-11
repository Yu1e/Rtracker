package com.readingtracker.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.readingtracker.Constants
import com.readingtracker.IconSwitcher
import com.readingtracker.Prefs
import com.readingtracker.backup.BackupManager
import com.readingtracker.data.AppDatabase
import com.readingtracker.databinding.FragmentSettingsBinding
import com.readingtracker.diag.Diag
import com.readingtracker.diag.ScoutLog
import com.readingtracker.overlay.OverlayManager
import com.readingtracker.service.ActivitySignal
import com.readingtracker.service.ReaderAccessibilityService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private var overlay: OverlayManager? = null

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = requireContext()
        ctx.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        Prefs.setBackupFolder(ctx, uri)
        lifecycleScope.launch {
            val r = BackupManager.saveToFolder(ctx, auto = true)
            toast(r.fold({ "Папка выбрана, первая копия сохранена" }, { "Ошибка: ${it.message}" }))
            render()
        }
    }

    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val r = BackupManager.saveToUri(requireContext(), uri)
            toast(r.fold({ "Копия сохранена" }, { "Ошибка: ${it.message}" }))
        }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        confirmRestore(uri)
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnBackupFolder.setOnClickListener { pickFolder.launch(null) }
        b.btnBackupNow.setOnClickListener {
            if (Prefs.backupFolder(requireContext()) != null) {
                lifecycleScope.launch {
                    val r = BackupManager.saveToFolder(requireContext(), auto = false)
                    toast(r.fold({ "Сохранено: $it" }, { "Ошибка: ${it.message}" }))
                    render()
                }
            } else {
                val name = "rtracker-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
                createFile.launch(name)
            }
        }
        b.btnRestore.setOnClickListener {
            openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
        }

        b.btnA11y.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnAppInfo.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${requireContext().packageName}")))
        }
        b.btnShareScout.setOnClickListener { shareScout() }
        b.btnClearScout.setOnClickListener {
            ScoutLog.clear(requireContext()); render()
        }

        buildIconRow()
        buildShortcutButtons()

        b.btnTestBubble.setOnClickListener { testBubble() }
        b.btnCopy.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Отчёт трекера", b.tvReport.text))
            toast("Отчёт скопирован")
        }
        b.btnClearError.setOnClickListener { Diag.clearError(requireContext()); render() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun toast(s: String) = Toast.makeText(requireContext(), s, Toast.LENGTH_LONG).show()

    // ── Отрисовка состояния ───────────────────────────────────────────────────

    private fun render() {
        if (_b == null) return
        val c = requireContext()

        // Копия
        val folder = Prefs.backupFolder(c)
        val last = Prefs.lastBackupInfo(c)
        b.tvBackupStatus.text = if (folder == null) {
            "⚠ Папка не выбрана — автокопия не делается. Выберите папку (например, «Документы» или папку, которая синхронизируется с облаком)."
        } else {
            "Каждый день копия сохраняется сама, хранятся 7 последних.\nПапка: ${folder.lastPathSegment?.substringAfter(':') ?: folder}\n" +
                "Последняя: ${last ?: "ещё не было"}"
        }

        // Спецвозможности
        val on = ReaderAccessibilityService.isEnabled(c)
        b.tvA11yStatus.text = if (on) {
            "Служба включена. Таймер встаёт на паузу, если 3 минуты нет перелистываний. " +
                "Журнал разведки пишется."
        } else {
            "Служба выключена. Без неё таймер не встаёт на паузу, если телефон лежит с включённым экраном.\n\n" +
                "Как включить:\n" +
                "1. «Открыть специальные возможности» → найдите «Трекер чтения: перелистывания» (иногда в разделе «Скачанные приложения») → включите.\n" +
                "2. Если переключатель серый и написано «Ограниченная настройка»: «Открыть «О приложении»» → меню ⋮ в правом верхнем углу → «Разрешить ограниченные настройки». Потом снова шаг 1."
        }
        b.tvScout.text = "Журнал разведки: ${ScoutLog.sizeKb(c)} КБ. Почитайте несколько дней во всех трёх читалках " +
            "(в том числе выбирая книги в библиотеке), затем отправьте журнал мне — по нему я научу трекер узнавать книгу сам."

        // Иконка
        highlightIcon()

        // Проверка
        b.tvReport.text = report(c)
    }

    private fun report(c: Context): String = buildString {
        fun yes(v: Boolean) = if (v) "да" else "НЕТ"
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Версия трекера: ${c.packageManager.getPackageInfo(c.packageName, 0).versionName}")
        appendLine("Статистика использования: ${yes(Diag.hasUsageAccess(c))}")
        appendLine("Поверх других приложений: ${yes(Settings.canDrawOverlays(c))}")
        appendLine("Спецвозможности: ${yes(ReaderAccessibilityService.isEnabled(c))}, подключена: ${yes(ActivitySignal.accessibilityConnected)}")
        appendLine("Служба отзывалась: ${Diag.get(c, Diag.HEARTBEAT) ?: "никогда"}")
        appendLine()
        appendLine("Найденные читалки:")
        Constants.detectTrackedApps(c).forEach { (pkg, name) -> appendLine("  $name ($pkg)") }
        appendLine()
        appendLine("Последнее приложение: ${Diag.get(c, Diag.FOREGROUND) ?: "—"}")
        appendLine("Последнее событие: ${Diag.get(c, Diag.EVENT) ?: "—"}")
        val counts = ReaderAccessibilityService.eventTypeCounts
        if (counts.isNotEmpty()) {
            appendLine()
            appendLine("События спецвозможностей:")
            counts.entries.sortedBy { it.key }.forEach { appendLine("  ${it.key}: ${it.value}") }
        }
        appendLine()
        appendLine("Последняя ошибка:")
        appendLine("  ${Diag.get(c, Diag.ERROR) ?: "нет"}")
    }

    // ── Ярлыки читалок ────────────────────────────────────────────────────────

    private fun buildShortcutButtons() {
        val ctx = requireContext()
        b.shortcutButtons.removeAllViews()
        val readers = Constants.detectTrackedApps(ctx).entries.distinctBy { it.value }
        if (readers.isEmpty()) {
            b.shortcutButtons.addView(TextView(ctx).apply { text = "Читалки не найдены"; textSize = 14f })
            return
        }
        for ((pkg, name) in readers) {
            b.shortcutButtons.addView(com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "добавить ярлык: $name"
                isAllCaps = false
                setOnClickListener {
                    if (!com.readingtracker.launcher.ReaderLauncher.requestPin(ctx, pkg, name)) {
                        toast("Рабочий стол не поддерживает добавление ярлыков. Долгое нажатие на значок трекера покажет читалки в меню.")
                    }
                }
            })
        }
    }

    // ── Иконки ────────────────────────────────────────────────────────────────

    private val iconViews = mutableListOf<Pair<Int, LinearLayout>>()

    private fun buildIconRow() {
        val dp = resources.displayMetrics.density
        b.iconRow.removeAllViews()
        iconViews.clear()
        for ((n, name, res) in IconSwitcher.variants) {
            val cell = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { chooseIcon(n) }
            }
            cell.addView(ImageView(requireContext()).apply {
                setImageResource(res)
                layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            })
            cell.addView(TextView(requireContext()).apply {
                text = name; textSize = 11f; gravity = Gravity.CENTER
            })
            b.iconRow.addView(cell)
            iconViews += n to cell
        }
        highlightIcon()
    }

    private fun highlightIcon() {
        val cur = IconSwitcher.current(requireContext())
        for ((n, v) in iconViews) {
            v.setBackgroundColor(if (n == cur) Color.argb(50, 57, 135, 229) else Color.TRANSPARENT)
        }
    }

    private fun chooseIcon(n: Int) {
        if (n == IconSwitcher.current(requireContext())) return
        AlertDialog.Builder(requireContext())
            .setTitle("Сменить иконку?")
            .setMessage("Иконка обновится через несколько секунд. Ярлык на рабочем столе может исчезнуть — тогда перетащите его заново из списка приложений.")
            .setPositiveButton("Сменить") { _, _ ->
                IconSwitcher.apply(requireContext(), n)
                highlightIcon()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ── Копия: восстановление ─────────────────────────────────────────────────

    private fun confirmRestore(uri: Uri) {
        val parsed = BackupManager.readFile(requireContext(), uri).getOrElse {
            toast("Не удалось прочитать файл: ${it.message}")
            return
        }
        val date = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(parsed.created))
        AlertDialog.Builder(requireContext())
            .setTitle("Восстановить из копии?")
            .setMessage("Копия от $date: книг ${parsed.books.size}, подходов к чтению ${parsed.sessions.size}.\n\n" +
                "Текущие данные будут заменены данными из копии.")
            .setPositiveButton("Восстановить") { _, _ ->
                lifecycleScope.launch {
                    BackupManager.restore(requireContext(), parsed)
                    toast("Восстановлено")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ── Журнал разведки ───────────────────────────────────────────────────────

    private fun shareScout() {
        val c = requireContext()
        val f = ScoutLog.file(c)
        if (!f.exists() || f.length() == 0L) { toast("Журнал пока пуст"); return }
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Журнал разведки трекера")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Отправить журнал"))
    }

    private fun testBubble() {
        val ctx = requireContext().applicationContext
        val o = overlay ?: OverlayManager(ctx).also { overlay = it }
        try {
            o.show("Проверка пузырька", null, AppDatabase.getInstance(ctx),
                onContinue = { o.hide() }, onNewBook = { _, _ -> o.hide() }, onDismiss = { o.hide() })
        } catch (e: Exception) {
            Diag.set(ctx, Diag.ERROR, "${Diag.time()} тест пузырька: $e")
            render()
        }
    }

    override fun onDestroyView() {
        overlay?.hide()
        super.onDestroyView()
        _b = null
    }
}
