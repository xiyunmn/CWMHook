package com.xiyunmn.cwmhook.config.hidden

import android.content.Context

object HiddenFeatureUnlockStore {
    private const val PREF = "cwmhook_hidden_features"
    private const val KEY_UNLOCKED = "unlocked"

    fun isUnlocked(context: Context): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_UNLOCKED, false)
    }

    fun setUnlocked(context: Context): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UNLOCKED, true)
            .commit()
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}
