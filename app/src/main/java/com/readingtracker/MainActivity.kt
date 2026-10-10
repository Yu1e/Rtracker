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
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.readingtracker.databinding.ActivityMainBinding
import com.readingtracker.service.ReadingTrackerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)

        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        // User may have just returned from Settings — re-check
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        when {
            !hasUsageStatsPermission() -> askUsageStats()
            !Settings.canDrawOverlays(this) -> askOverlay()
            else -> startService()
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

    private fun startService() {
        val intent = Intent(this, ReadingTrackerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
