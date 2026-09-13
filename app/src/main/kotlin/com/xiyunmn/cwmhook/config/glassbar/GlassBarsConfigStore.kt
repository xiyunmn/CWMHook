package com.xiyunmn.cwmhook.config.glassbar

import android.content.Context
import android.content.SharedPreferences

data class GlassBarsConfig(
    val mainEnabled: Boolean = false,
    val detailEnabled: Boolean = false,
    val catalogEnabled: Boolean = false,
    val blur: Boolean = true,
    val refraction: Boolean = true,
    val highlight: Boolean = true,
    val shadow: Boolean = true,
    val tilt: Boolean = true,
    val press: Boolean = true,
    val version: Int = 0,
) {
    val enabledCount: Int get() = listOf(mainEnabled, detailEnabled, catalogEnabled).count { it }
}

object GlassBarsConfigStore {
    private const val PREF = "cwmhook_glass_bars"

    fun defaultConfig() = GlassBarsConfig()

    fun readLocal(context: Context): GlassBarsConfig = read(preferences(context))

    internal fun read(prefs: SharedPreferences): GlassBarsConfig = GlassBarsConfig(
        mainEnabled = prefs.getBoolean("main_enabled", false),
        detailEnabled = prefs.getBoolean("detail_enabled", false),
        catalogEnabled = prefs.getBoolean("catalog_enabled", false),
        blur = prefs.getBoolean("blur", true),
        refraction = prefs.getBoolean("refraction", true),
        highlight = prefs.getBoolean("highlight", true),
        shadow = prefs.getBoolean("shadow", true),
        tilt = prefs.getBoolean("tilt", true),
        press = prefs.getBoolean("press", true),
        version = prefs.getInt("version", 0).coerceAtLeast(0),
    )

    fun writeLocal(context: Context, config: GlassBarsConfig): Boolean = preferences(context).edit()
        .putBoolean("main_enabled", config.mainEnabled)
        .putBoolean("detail_enabled", config.detailEnabled)
        .putBoolean("catalog_enabled", config.catalogEnabled)
        .putBoolean("blur", config.blur)
        .putBoolean("refraction", config.refraction)
        .putBoolean("highlight", config.highlight)
        .putBoolean("shadow", config.shadow)
        .putBoolean("tilt", config.tilt)
        .putBoolean("press", config.press)
        .putInt("version", config.version.coerceAtLeast(0))
        .commit()

    fun nextVersion(config: GlassBarsConfig): Int = if (config.version == Int.MAX_VALUE) 1 else config.version + 1

    private fun preferences(context: Context): SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
