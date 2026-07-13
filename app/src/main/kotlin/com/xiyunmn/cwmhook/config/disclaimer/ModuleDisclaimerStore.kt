package com.xiyunmn.cwmhook.config.disclaimer

import android.content.Context

object ModuleDisclaimerStore {
    private const val PREF = "cwmhook_disclaimer"
    private const val KEY_ACCEPTED = "accepted"

    fun isAccepted(context: Context): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPTED, false)
    }

    fun setAccepted(context: Context): Boolean {
        return appContext(context).getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPTED, true)
            .commit()
    }

    private fun appContext(context: Context): Context {
        return context.applicationContext ?: context
    }
}
