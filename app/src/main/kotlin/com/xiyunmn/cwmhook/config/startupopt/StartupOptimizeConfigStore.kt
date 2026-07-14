package com.xiyunmn.cwmhook.config.startupopt

import android.content.Context
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

data class StartupOptimizeConfig(
    val enabled: Boolean,
    val skipSelfSplash: Boolean,
    val skipThirdPartySplash: Boolean,
    val disableStartPagePrefetch: Boolean,
    val skipAdvertisementActivity: Boolean,
    val disableNativeNetworkLog: Boolean,
    val delayAuxiliaryStartupTasks: Boolean,
    val version: Int,
)

object StartupOptimizeConfigStore {
    private const val TAG = "CWMHook.StartupOptimizeConfig"
    private const val PREF = "cwmhook_startup_optimize"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SKIP_SELF_SPLASH = "skip_self_splash"
    private const val KEY_SKIP_THIRD_PARTY_SPLASH = "skip_third_party_splash"
    private const val KEY_DISABLE_START_PAGE_PREFETCH = "disable_start_page_prefetch"
    private const val KEY_SKIP_ADVERTISEMENT_ACTIVITY = "skip_advertisement_activity"
    private const val KEY_DISABLE_NATIVE_NETWORK_LOG = "disable_native_network_log"
    private const val KEY_DELAY_AUXILIARY_STARTUP_TASKS = "delay_auxiliary_startup_tasks"
    private const val KEY_VERSION = "version"

    fun defaultConfig(): StartupOptimizeConfig {
        return StartupOptimizeConfig(
            enabled = false,
            skipSelfSplash = false,
            skipThirdPartySplash = false,
            disableStartPagePrefetch = false,
            skipAdvertisementActivity = false,
            disableNativeNetworkLog = false,
            delayAuxiliaryStartupTasks = false,
            version = 0,
        )
    }

    fun readLocal(context: Context): StartupOptimizeConfig {
        val defaults = defaultConfig()
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sanitize(
            StartupOptimizeConfig(
                enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
                skipSelfSplash = prefs.getBoolean(KEY_SKIP_SELF_SPLASH, defaults.skipSelfSplash),
                skipThirdPartySplash = prefs.getBoolean(KEY_SKIP_THIRD_PARTY_SPLASH, defaults.skipThirdPartySplash),
                disableStartPagePrefetch = prefs.getBoolean(
                    KEY_DISABLE_START_PAGE_PREFETCH,
                    defaults.disableStartPagePrefetch,
                ),
                skipAdvertisementActivity = prefs.getBoolean(
                    KEY_SKIP_ADVERTISEMENT_ACTIVITY,
                    defaults.skipAdvertisementActivity,
                ),
                disableNativeNetworkLog = prefs.getBoolean(
                    KEY_DISABLE_NATIVE_NETWORK_LOG,
                    defaults.disableNativeNetworkLog,
                ),
                delayAuxiliaryStartupTasks = prefs.getBoolean(
                    KEY_DELAY_AUXILIARY_STARTUP_TASKS,
                    defaults.delayAuxiliaryStartupTasks,
                ),
                version = prefs.getInt(KEY_VERSION, defaults.version),
            ),
        )
    }

    fun writeLocal(context: Context, config: StartupOptimizeConfig): Boolean {
        val sanitized = sanitize(config)
        val saved = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, sanitized.enabled)
            .putBoolean(KEY_SKIP_SELF_SPLASH, sanitized.skipSelfSplash)
            .putBoolean(KEY_SKIP_THIRD_PARTY_SPLASH, sanitized.skipThirdPartySplash)
            .putBoolean(KEY_DISABLE_START_PAGE_PREFETCH, sanitized.disableStartPagePrefetch)
            .putBoolean(KEY_SKIP_ADVERTISEMENT_ACTIVITY, sanitized.skipAdvertisementActivity)
            .putBoolean(KEY_DISABLE_NATIVE_NETWORK_LOG, sanitized.disableNativeNetworkLog)
            .putBoolean(KEY_DELAY_AUXILIARY_STARTUP_TASKS, sanitized.delayAuxiliaryStartupTasks)
            .putInt(KEY_VERSION, sanitized.version)
            .commit()
        if (saved) {
            ModuleFileLogger.i(
                TAG,
                "Startup optimize config saved: enabled=${sanitized.enabled}, " +
                    "skipSelfSplash=${sanitized.skipSelfSplash}, " +
                    "skipThirdPartySplash=${sanitized.skipThirdPartySplash}, " +
                    "disableStartPagePrefetch=${sanitized.disableStartPagePrefetch}, " +
                    "skipAdvertisementActivity=${sanitized.skipAdvertisementActivity}, " +
                    "disableNativeNetworkLog=${sanitized.disableNativeNetworkLog}, " +
                    "delayAuxiliaryStartupTasks=${sanitized.delayAuxiliaryStartupTasks}, " +
                    "version=${sanitized.version}",
            )
        }
        return saved
    }

    fun nextVersion(config: StartupOptimizeConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    private fun sanitize(config: StartupOptimizeConfig): StartupOptimizeConfig {
        return config.copy(version = config.version.coerceAtLeast(0))
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}
