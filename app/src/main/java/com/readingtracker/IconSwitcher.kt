package com.readingtracker

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Смена иконки приложения: включается один из ярлыков-псевдонимов. */
object IconSwitcher {

    val variants = listOf(
        Triple(1, "Открытая книга", R.mipmap.ic_launcher_v1),
        Triple(2, "Закладка", R.mipmap.ic_launcher_v2),
        Triple(3, "Огонёк стрика", R.mipmap.ic_launcher_v3),
        Triple(4, "Стопка книг", R.mipmap.ic_launcher_v4)
    )

    private fun alias(c: Context, n: Int) = ComponentName(c.packageName, "com.readingtracker.LauncherV$n")

    fun current(c: Context): Int = Prefs.iconVariant(c).takeIf { it in 1..4 } ?: 1

    fun apply(c: Context, variant: Int) {
        val pm = c.packageManager
        // Сначала включаем новый, потом выключаем остальные — чтобы ярлык не пропал совсем
        pm.setComponentEnabledSetting(alias(c, variant),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (n in 1..4) if (n != variant) {
            pm.setComponentEnabledSetting(alias(c, n),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        Prefs.setIconVariant(c, variant)
    }
}
