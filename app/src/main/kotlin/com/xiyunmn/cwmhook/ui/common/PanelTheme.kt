package com.xiyunmn.cwmhook.ui.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.xiyunmn.cwmhook.ui.panel.IconType

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
) {
    fun iconAccent(icon: IconType): Int {
        return when (icon) {
            IconType.TAB -> accent
            IconType.UI -> 0xFF6554E8.toInt()
            IconType.AD -> 0xFFFFA31A.toInt()
            IconType.DOWNLOAD -> 0xFF4285F4.toInt()
            IconType.MORE -> 0xFF43C76D.toInt()
            else -> subIcon
        }
    }

    companion object {
        fun from(context: Context): PanelTheme {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return if (night) {
                PanelTheme(
                    dim = 0xAA000000.toInt(),
                    panelBackground = 0xFF1E2024.toInt(),
                    cardBackground = 0xFF2A2D33.toInt(),
                    text = 0xFFF0F2F5.toInt(),
                    subText = 0xFFB0B6C0.toInt(),
                    accent = 0xFFFF6FA2.toInt(),
                    chevron = 0xFFC2C7D0.toInt(),
                    separator = 0xFF3F4249.toInt(),
                    subIcon = 0xFFC8CDD6.toInt(),
                    subIconBackground = 0xFF2E3138.toInt(),
                    footerIcon = 0xFFC4C9D2.toInt(),
                    rowBackground = 0xFF24272D.toInt(),
                    mutedIcon = 0xFF9EA4AE.toInt(),
                    disabledIcon = 0xFF5F656F.toInt(),
                    tabIcon = 0xFFD2D6DE.toInt(),
                )
            } else {
                PanelTheme(
                    dim = 0x99000000.toInt(),
                    panelBackground = Color.WHITE,
                    cardBackground = 0xFFF8F9FB.toInt(),
                    text = 0xFF202124.toInt(),
                    subText = 0xFF70757A.toInt(),
                    accent = 0xFFE95B89.toInt(),
                    chevron = 0xFF5F6368.toInt(),
                    separator = 0xFFE8EAED.toInt(),
                    subIcon = 0xFF5F6368.toInt(),
                    subIconBackground = 0xFFFFFFFF.toInt(),
                    footerIcon = 0xFF6D737C.toInt(),
                    rowBackground = 0xFFFFFFFF.toInt(),
                    mutedIcon = 0xFFA8ADB5.toInt(),
                    disabledIcon = 0xFFC2C6CC.toInt(),
                    tabIcon = 0xFF9EA3AB.toInt(),
                )
            }
        }
    }
}
