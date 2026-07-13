package com.xiyunmn.cwmhook.config.rewardad

import android.content.Context
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

data class RewardAdSkipConfig(
    val enabled: Boolean,
    val version: Int,
)

object RewardAdSkipConfigStore {
    private const val TAG = "CWMHook.RewardAdSkipConfig"
    private const val PREF = "cwmhook_reward_ad_skip"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VERSION = "version"

    fun defaultConfig(): RewardAdSkipConfig {
        return RewardAdSkipConfig(enabled = false, version = 0)
    }

    fun readLocal(context: Context): RewardAdSkipConfig {
        val defaults = defaultConfig()
        val prefs = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return RewardAdSkipConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            version = prefs.getInt(KEY_VERSION, defaults.version).coerceAtLeast(0),
        )
    }

    fun writeLocal(context: Context, config: RewardAdSkipConfig): Boolean {
        val sanitized = config.copy(version = config.version.coerceAtLeast(0))
        val saved = appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, sanitized.enabled)
            .putInt(KEY_VERSION, sanitized.version)
            .commit()
        if (saved) {
            ModuleFileLogger.i(TAG, "Reward ad skip config saved: enabled=${sanitized.enabled}, version=${sanitized.version}")
        }
        return saved
    }

    fun nextVersion(config: RewardAdSkipConfig): Int {
        return if (config.version == Int.MAX_VALUE) 1 else config.version + 1
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}
