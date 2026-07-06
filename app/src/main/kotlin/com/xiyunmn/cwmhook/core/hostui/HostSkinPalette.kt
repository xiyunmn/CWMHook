package com.xiyunmn.cwmhook.core.hostui

import android.content.Context
import android.graphics.Color

internal data class HostSkinPalette(
    val skinKey: String,
    val night: Boolean,
    val mainBackground: Int,
    val rowBackground: Int,
    val catalogBackground: Int,
    val titleBackground: Int,
    val titleText: Int,
    val titleRightText: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val tertiaryText: Int,
    val sectionText: Int,
    val accentText: Int,
    val accent: Int,
    val divider: Int,
    val buttonText: Int,
) {
    companion object {
        fun from(context: Context): HostSkinPalette {
            val skinKey = HostSkinResolver.currentSkinKey(context)
            val night = skinKey == "night"
            val neutralMain = if (night) 0xFF222222.toInt() else 0xFFF7F7F7.toInt()
            val neutralPanel = if (night) 0xFF353535.toInt() else Color.WHITE
            val title = HostSkinResolver.skinnedColor(
                context = context,
                name = "textTitle",
                skinKey = skinKey,
                fallback = if (night) 0xFFCCCCCC.toInt() else 0xFF202124.toInt(),
            )
            val secondary = HostSkinResolver.skinnedColor(
                context = context,
                name = "text_666",
                skinKey = skinKey,
                fallback = if (night) 0xFF999999.toInt() else 0xFF666666.toInt(),
            )
            val titleRight = HostSkinResolver.skinnedColor(context, "color_title_textright", skinKey, title)
            val accentText = HostSkinResolver.skinnedColor(context, "text_base_color", skinKey, titleRight)
            val accent = HostSkinResolver.skinnedColor(context, "color_base", skinKey, accentText)
            return HostSkinPalette(
                skinKey = skinKey,
                night = night,
                mainBackground = HostSkinResolver.skinnedColor(context, "color_bg_main", skinKey, neutralMain),
                rowBackground = HostSkinResolver.skinnedColor(context, "color_bg_1", skinKey, neutralPanel),
                catalogBackground = HostSkinResolver.skinnedColor(context, "color_bg_catalog", skinKey, neutralMain),
                titleBackground = HostSkinResolver.skinnedColor(context, "color_title_bg1", skinKey, neutralPanel),
                titleText = HostSkinResolver.skinnedColor(context, "color_title_text1", skinKey, title),
                titleRightText = titleRight,
                primaryText = title,
                secondaryText = secondary,
                tertiaryText = if (night) 0xFF777777.toInt() else 0xFF999999.toInt(),
                sectionText = HostSkinResolver.skinnedColor(context, "text_3797cc", skinKey, title),
                accentText = accentText,
                accent = accent,
                divider = HostSkinResolver.skinnedColor(
                    context = context,
                    name = "divider",
                    skinKey = skinKey,
                    fallback = if (night) 0xFF161616.toInt() else 0xFFE6E6E6.toInt(),
                ),
                buttonText = HostSkinResolver.skinnedColor(context, "btn_cumText", skinKey, readableTextOn(accent)),
            )
        }

        fun readableTextOn(color: Int): Int {
            val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
            return if (luminance > 150.0) Color.BLACK else Color.WHITE
        }
    }
}
