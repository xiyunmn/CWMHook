package com.xiyunmn.cwmhook.ui.common

import android.content.Context
import android.graphics.Color
import com.xiyunmn.cwmhook.core.hostui.HostSkinPalette
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
            val host = HostSkinPalette.from(context)
            val night = host.night
            return if (night) {
                PanelTheme(
                    dim = 0xAA000000.toInt(),
                    panelBackground = host.mainBackground,
                    cardBackground = blendPanelColor(host.rowBackground, host.mainBackground, 0.72f),
                    text = host.primaryText,
                    subText = host.secondaryText,
                    accent = host.accent,
                    chevron = host.secondaryText,
                    separator = host.divider,
                    subIcon = host.secondaryText,
                    subIconBackground = blendPanelColor(host.rowBackground, host.mainBackground, 0.86f),
                    footerIcon = host.secondaryText,
                    rowBackground = host.rowBackground,
                    mutedIcon = blendPanelColor(host.secondaryText, host.rowBackground, 0.72f),
                    disabledIcon = blendPanelColor(host.secondaryText, host.rowBackground, 0.42f),
                    tabIcon = host.secondaryText,
                    buttonText = host.buttonText,
                )
            } else {
                PanelTheme(
                    dim = 0x99000000.toInt(),
                    panelBackground = host.mainBackground,
                    cardBackground = blendPanelColor(host.rowBackground, host.mainBackground, 0.72f),
                    text = host.primaryText,
                    subText = host.secondaryText,
                    accent = host.accent,
                    chevron = host.secondaryText,
                    separator = host.divider,
                    subIcon = host.secondaryText,
                    subIconBackground = host.rowBackground,
                    footerIcon = host.secondaryText,
                    rowBackground = host.rowBackground,
                    mutedIcon = blendPanelColor(host.secondaryText, host.rowBackground, 0.52f),
                    disabledIcon = blendPanelColor(host.secondaryText, host.rowBackground, 0.34f),
                    tabIcon = host.secondaryText,
                    buttonText = host.buttonText,
                )
            }
        }

        private fun blendPanelColor(foreground: Int, background: Int, amount: Float): Int {
            val alpha = amount.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(foreground) * alpha + Color.red(background) * (1f - alpha)).toInt(),
                (Color.green(foreground) * alpha + Color.green(background) * (1f - alpha)).toInt(),
                (Color.blue(foreground) * alpha + Color.blue(background) * (1f - alpha)).toInt(),
            )
        }
    }
}
