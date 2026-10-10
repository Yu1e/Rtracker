package com.readingtracker.ui.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.readingtracker.Constants
import com.readingtracker.data.AppDatabase
import com.readingtracker.databinding.FragmentDiagBinding
import com.readingtracker.diag.Diag
import com.readingtracker.overlay.OverlayManager

class DiagFragment : Fragment() {

    private var _b: FragmentDiagBinding? = null
    private val b get() = _b!!
    private var overlay: OverlayManager? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDiagBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnRefresh.setOnClickListener { render() }

        b.btnCopy.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Отчёт трекера", b.tvReport.text))
            Toast.makeText(requireContext(), "Отчёт скопирован", Toast.LENGTH_SHORT).show()
        }

        b.btnTestBubble.setOnClickListener {
            val ctx = requireContext().applicationContext
            val o = overlay ?: OverlayManager(ctx).also { overlay = it }
            try {
                o.show(
                    appName = "Проверка пузырька",
                    rememberedBookId = null,
                    db = AppDatabase.getInstance(ctx),
                    onContinue = { o.hide() },
                    onNewBook = { _, _ -> o.hide() },
                    onDismiss = { o.hide() }
                )
            } catch (e: Exception) {
                Diag.set(ctx, Diag.ERROR, "${Diag.time()} тест пузырька: $e")
                render()
            }
        }

        b.btnClearError.setOnClickListener {
            Diag.clearError(requireContext())
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = requireContext()
        fun yes(v: Boolean) = if (v) "да" else "НЕТ"

        val detectedNow = Constants.detectTrackedApps(c)
        val detectedText = if (detectedNow.isEmpty()) "ни одной"
        else detectedNow.entries.joinToString("\n") { "  ${it.value} (${it.key})" }

        val report = buildString {
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("Доступ к статистике использования: ${yes(Diag.hasUsageAccess(c))}")
            appendLine("Поверх других приложений: ${yes(Settings.canDrawOverlays(c))}")
            appendLine()
            appendLine("Служба последний раз отзывалась: ${Diag.get(c, Diag.HEARTBEAT) ?: "никогда"}")
            appendLine("(должно быть несколько секунд назад)")
            appendLine()
            appendLine("Найденные читалки:")
            appendLine(detectedText)
            appendLine()
            appendLine("Последнее приложение на экране:")
            appendLine("  ${Diag.get(c, Diag.FOREGROUND) ?: "—"}")
            appendLine()
            appendLine("Последнее событие:")
            appendLine("  ${Diag.get(c, Diag.EVENT) ?: "—"}")
            appendLine()
            appendLine("Последняя ошибка:")
            appendLine("  ${Diag.get(c, Diag.ERROR) ?: "нет"}")
        }
        b.tvReport.text = report
    }

    override fun onDestroyView() {
        overlay?.hide()
        super.onDestroyView()
        _b = null
    }
}
