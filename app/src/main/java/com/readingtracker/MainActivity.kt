package com.readingtracker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.readingtracker.ui.books.BooksFragment
import com.readingtracker.ui.home.HomeFragment
import com.readingtracker.ui.settings.SettingsFragment
import com.readingtracker.ui.stats.StatsFragment
import com.readingtracker.databinding.ActivityMainBinding
import androidx.lifecycle.lifecycleScope
import com.readingtracker.backup.BackupManager
import com.readingtracker.reminder.StatusPrompt
import com.readingtracker.launcher.ReaderLauncher
import com.readingtracker.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TABS = 4
        private const val LOOP = TABS * 50_000
        private const val START = TABS * 25_000
    }

    private lateinit var binding: ActivityMainBinding
    private val statusPrompt = StatusPrompt(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.readingtracker.ui.home.Ribbons.restore(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Карусель: позиций много, экран = позиция по модулю 4, листается по кругу в обе стороны
        val pager = binding.pager
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = LOOP
            override fun createFragment(position: Int): Fragment = when (position % TABS) {
                0 -> HomeFragment()
                1 -> BooksFragment()
                2 -> StatsFragment()
                else -> SettingsFragment()
            }
        }
        if (savedInstanceState == null) pager.setCurrentItem(START, false)

        binding.bottomNav.setItems(listOf(
            0 to "главная",
            1 to "книги",
            2 to "статистика",
            3 to "настройки"
        ), iconIds = setOf(3))
        binding.bottomNav.onSelect = { tab -> goTo(tab) }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { binding.bottomNav.select(position % TABS) }
        })
        binding.bottomNav.select(pager.currentItem % TABS)

        // «Назад» с любого экрана возвращает на главную, с главной — выход
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pager.currentItem % TABS != 0) goTo(0)
                else moveTaskToBack(true)
            }
        })

        checkPermissions()
    }

    /** Переход к экрану кратчайшим путём по кругу, со сдвигом. */
    private fun goTo(tab: Int) {
        val pager = binding.pager
        val cur = pager.currentItem
        var delta = ((tab - cur % TABS) % TABS + TABS) % TABS
        if (delta > TABS / 2) delta -= TABS
        if (delta != 0) pager.setCurrentItem(cur + delta, true)
    }

    override fun onResume() {
        super.onResume()
        // Могли вернуться из настроек — проверяем разрешения снова
        checkPermissions()
        // Вопрос «дочитана?» о книгах без статуса
        statusPrompt.maybeShow()
        lifecycleScope.launch { try { BackupManager.autoBackupIfNeeded(this@MainActivity) } catch (_: Exception) {} }
        WidgetUpdater.update(this)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        when {
            !hasUsageStatsPermission() -> askUsageStats()
            !Settings.canDrawOverlays(this) -> askOverlay()
            else -> ReaderLauncher.updateDynamic(this)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun askUsageStats() {
        AlertDialog.Builder(this)
            .setTitle("Нужно разрешение")
            .setMessage(
                "Приложению нужен доступ к статистике использования, " +
                "чтобы понять, когда вы открываете читалку.\n\n" +
                "В следующем экране найдите «Трекер чтения» и включите переключатель."
            )
            .setPositiveButton("Открыть настройки") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setCancelable(false)
            .show()
    }

    private fun askOverlay() {
        AlertDialog.Builder(this)
            .setTitle("Нужно разрешение")
            .setMessage(
                "Приложению нужно разрешение «Поверх других приложений», " +
                "чтобы показывать пузырёк при открытии читалки.\n\n" +
                "Включите переключатель на следующем экране."
            )
            .setPositiveButton("Открыть настройки") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
            .setCancelable(false)
            .show()
    }
}
