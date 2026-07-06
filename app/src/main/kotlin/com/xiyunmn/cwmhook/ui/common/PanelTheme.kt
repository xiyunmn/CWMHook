package com.xiyunmn.cwmhook.ui.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import com.xiyunmn.cwmhook.ui.icons.IconType

data class PanelTheme(
    val dim: Int,
    val panelBackground: Int,
    val cardBackground: Int,
    val text: Int,
    val subText: Int,
    val accent: Int,
    val chevron: Int,
    val separator: Int,
    val subIcon: Int,
    val subIconBackground: Int,
    val footerIcon: Int,
    val rowBackground: Int,
    val mutedIcon: Int,
    val disabledIcon: Int,
    val tabIcon: Int,
    val buttonText: Int,
) {
    fun iconAccent(icon: IconType): Int {
        return when (icon) {
            IconType.TAB,
            IconType.UI,
            IconType.DOWNLOAD,
            IconType.STATUS_BAR,
            IconType.BOTTOM_TAB,
            IconType.CHAPTER_EXPORT,
            IconType.FONT,
            IconType.FONT_IMPORT,
            IconType.FONT_MANAGE,
            IconType.AUTO_SIGN_IN,
            IconType.STARTUP_TAB,
            IconType.PLAY,
            IconType.PLAY_PAUSE,
            IconType.FOLDER_OPEN,
            IconType.RESET,
            IconType.DELETE,
            IconType.POWER -> accent
            else -> subIcon
        }
    }

    companion object {
        fun from(context: Context): PanelTheme {
            val skinKey = currentSkinKey(context)
            val night = skinKey == "night"
            val mainBackground = skinnedHostColor(context, "color_bg_main", skinKey, if (night) 0xFF222222.toInt() else 0xFFF7F7F7.toInt())
            val rowBackground = skinnedHostColor(context, "color_bg_1", skinKey, if (night) 0xFF353535.toInt() else Color.WHITE)
            val text = skinnedHostColor(context, "textTitle", skinKey, if (night) 0xFFCCCCCC.toInt() else 0xFF202124.toInt())
            val subText = skinnedHostColor(context, "text_666", skinKey, if (night) 0xFF999999.toInt() else 0xFF666666.toInt())
            val separator = skinnedHostColor(context, "divider", skinKey, if (night) 0xFF161616.toInt() else 0xFFE6E6E6.toInt())
            val accent = skinnedHostColor(
                context,
                "color_base",
                skinKey,
                skinnedHostColor(context, "text_base_color", skinKey, if (night) 0xFF585858.toInt() else 0xFFF9BE00.toInt()),
            )
            val buttonText = skinnedHostColor(context, "btn_cumText", skinKey, readableTextOn(accent))
            return if (night) {
                PanelTheme(
                    dim = 0xAA000000.toInt(),
                    panelBackground = mainBackground,
                    cardBackground = blendPanelColor(rowBackground, mainBackground, 0.72f),
                    text = text,
                    subText = subText,
                    accent = accent,
                    chevron = subText,
                    separator = separator,
                    subIcon = subText,
                    subIconBackground = blendPanelColor(rowBackground, mainBackground, 0.86f),
                    footerIcon = subText,
                    rowBackground = rowBackground,
                    mutedIcon = blendPanelColor(subText, rowBackground, 0.72f),
                    disabledIcon = blendPanelColor(subText, rowBackground, 0.42f),
                    tabIcon = subText,
                    buttonText = buttonText,
                )
            } else {
                PanelTheme(
                    dim = 0x99000000.toInt(),
                    panelBackground = mainBackground,
                    cardBackground = blendPanelColor(rowBackground, mainBackground, 0.72f),
                    text = text,
                    subText = subText,
                    accent = accent,
                    chevron = subText,
                    separator = separator,
                    subIcon = subText,
                    subIconBackground = rowBackground,
                    footerIcon = subText,
                    rowBackground = rowBackground,
                    mutedIcon = blendPanelColor(subText, rowBackground, 0.52f),
                    disabledIcon = blendPanelColor(subText, rowBackground, 0.34f),
                    tabIcon = subText,
                    buttonText = buttonText,
                )
            }
        }

        private fun currentSkinKey(context: Context): String {
            val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val followSystem = settings.getBoolean("IsfollowNight", false)
            val isNight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && followSystem) {
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            } else {
                context.getSharedPreferences(CiweiMaoPackages.DEFAULT_PREF, Context.MODE_PRIVATE)
                    .getBoolean("isNight", false)
            }
            return if (isNight) {
                "night"
            } else {
                settings.getString("skinType", "yellow") ?: "yellow"
            }
        }

        private fun skinnedHostColor(context: Context, name: String, skinKey: String, fallback: Int): Int {
            providerColor(context, name)?.let { return it }
            skinnedNames(name, skinKey).forEach { candidate ->
                plainColor(context, candidate)?.let { return it }
            }
            return fallback
        }

        private fun skinnedNames(name: String, skinKey: String): List<String> {
            return when (skinKey) {
                "night" -> listOf("${name}_night", name)
                "green", "pink" -> listOf("${name}_$skinKey", name)
                else -> listOf(name)
            }
        }

        private fun providerColor(context: Context, name: String): Int? {
            val id = context.resources.getIdentifier(name, "color", context.packageName)
            if (id == 0) {
                return null
            }
            val provider = runCatching {
                val managerClass = Class.forName("org.qcode.qskinloader.o.d", false, context.classLoader)
                val manager = managerClass.getMethod("h").invoke(null)
                manager.javaClass.getMethod("i").invoke(manager)
            }.getOrNull() ?: return null
            return runCatching {
                provider.javaClass
                    .getMethod("f", Int::class.javaPrimitiveType, String::class.java)
                    .invoke(provider, id, name) as Int
            }.getOrNull()
        }

        private fun plainColor(context: Context, name: String): Int? {
            val id = context.resources.getIdentifier(name, "color", context.packageName)
            if (id == 0) {
                return null
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.resources.getColor(id, context.theme)
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.getColor(id)
                }
            }.getOrNull()
        }

        private fun blendPanelColor(foreground: Int, background: Int, amount: Float): Int {
            val alpha = amount.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(foreground) * alpha + Color.red(background) * (1f - alpha)).toInt(),
                (Color.green(foreground) * alpha + Color.green(background) * (1f - alpha)).toInt(),
                (Color.blue(foreground) * alpha + Color.blue(background) * (1f - alpha)).toInt(),
            )
        }

        private fun readableTextOn(color: Int): Int {
            val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
            return if (luminance > 150.0) Color.BLACK else Color.WHITE
        }
    }
}
