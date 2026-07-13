package com.xiyunmn.cwmhook.config.statusbar

import android.content.Context
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

/**
 * 状态栏背景优化配置存储
 *
 * 使用宿主本地 SharedPreferences，不依赖 remote preferences
 */
object StatusBarConfigStore {
    private const val TAG = "CWMHook.StatusBarConfig"
    private const val PREF_NAME = "status_bar"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VERSION = "version"

    /**
     * 读取本地配置
     */
    fun readLocal(context: Context): StatusBarConfig {
        return runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            StatusBarConfig(
                enabled = prefs.getBoolean(KEY_ENABLED, StatusBarConfig.DEFAULT_ENABLED),
                version = prefs.getInt(KEY_VERSION, StatusBarConfig.DEFAULT_VERSION),
            )
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Failed to read status bar config", throwable)
            StatusBarConfig(
                enabled = StatusBarConfig.DEFAULT_ENABLED,
                version = StatusBarConfig.DEFAULT_VERSION,
            )
        }
    }

    /**
     * 写入本地配置
     */
    fun writeLocal(context: Context, config: StatusBarConfig): Boolean {
        return runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val saved = prefs.edit()
                .putBoolean(KEY_ENABLED, config.enabled)
                .putInt(KEY_VERSION, config.version)
                .commit()
            if (saved) {
                ModuleFileLogger.i(TAG, "Status bar config saved: enabled=${config.enabled}, version=${config.version}")
            }
            saved
        }.getOrElse { throwable ->
            ModuleFileLogger.e(TAG, "Failed to write status bar config", throwable)
            false
        }
    }

    /**
     * 获取默认配置
     */
    fun defaultConfig(): StatusBarConfig = StatusBarConfig(
        enabled = StatusBarConfig.DEFAULT_ENABLED,
        version = StatusBarConfig.DEFAULT_VERSION,
    )
}
