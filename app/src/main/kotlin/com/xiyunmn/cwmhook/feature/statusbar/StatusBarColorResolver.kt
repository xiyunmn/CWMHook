package com.xiyunmn.cwmhook.feature.statusbar

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build

internal class StatusBarColorResolver(
    private val targetPackage: String,
    private val targetDefaultPref: String,
    private val sceneRules: StatusBarSceneRules,
) {
    fun currentSkinKey(context: Context): String {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val followSystem = settings.getBoolean("IsfollowNight", false)
        val isNight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && followSystem) {
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        } else {
            context.getSharedPreferences(targetDefaultPref, Context.MODE_PRIVATE).getBoolean("isNight", false)
        }
        return if (isNight) {
            "night"
        } else {
            settings.getString("skinType", "yellow") ?: "yellow"
        }
    }

    fun fallbackColor(context: Context, skinKey: String): Int {
        val names = if (skinKey == "night") {
            listOf("color_bg_main_night", "color_bg_1_night", "color_bg_2_night")
        } else {
            listOf("color_bg_1", "color_bg_main", "color_bg_2")
        }
        return resourceColor(context, names) ?: if (skinKey == "night") Color.rgb(34, 34, 34) else Color.WHITE
    }

    fun directSceneColor(context: Context, skinKey: String, sceneKey: String): Int? {
        return when {
            sceneRules.isBookStoreHomeScene(sceneKey) -> pageSurfaceColor(context, skinKey)
            sceneRules.isBookShelfTabScene(sceneKey) -> pageSurfaceColor(context, skinKey)
            sceneRules.isMainFrameThemeTopScene(sceneKey) -> fallbackColor(context, skinKey)
            else -> null
        }
    }

    private fun pageSurfaceColor(context: Context, skinKey: String): Int {
        val names = if (skinKey == "night") {
            listOf("color_bg_1_night", "color_bg_main_night", "color_bg_2_night")
        } else {
            listOf("color_bg_1", "color_bg_main", "color_bg_2")
        }
        return resourceColor(context, names) ?: if (skinKey == "night") Color.rgb(53, 53, 53) else Color.WHITE
    }

    private fun resourceColor(context: Context, names: List<String>): Int? {
        names.forEach { name ->
            val id = context.resources.getIdentifier(name, "color", targetPackage)
            if (id != 0) {
                return context.resources.getColor(id, context.theme)
            }
        }
        return null
    }
}
