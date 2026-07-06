package com.xiyunmn.cwmhook.feature.startuptab

import android.app.Activity
import android.content.Intent
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.config.startuptab.StartupTabConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoIntentExtras
import com.xiyunmn.cwmhook.host.CiweiMaoMembers

internal object StartupTabSelector {
    private const val TAG = "CWMHook.StartupTab"

    fun prepareBeforeInitWidgets(activity: Activity) {
        if (activity.javaClass.name != CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
            return
        }
        val startupConfig = StartupTabConfigStore.readLocal(activity)
        if (!startupConfig.enabled) {
            return
        }
        val intent = activity.intent
        if (hasExplicitDestination(intent)) {
            ModuleFileLogger.i(TAG, "Startup tab skipped, explicit host destination exists")
            return
        }
        val bottomTabConfig = BottomTabConfigStore.readLocal(activity)
        val targetKey = StartupTabConfigStore.effectiveTabKey(startupConfig, bottomTabConfig) ?: return
        val targetTab = BottomTabConfigStore.tabByKey(targetKey) ?: return
        if (!setStartPos(activity, targetTab.index)) {
            ModuleFileLogger.w(TAG, "Startup tab failed, DGFrameActivity.pos not found")
            return
        }
        ModuleFileLogger.i(TAG, "Startup tab applied: key=$targetKey, index=${targetTab.index}")
    }

    private fun hasExplicitDestination(intent: Intent?): Boolean {
        if (intent == null) {
            return false
        }
        if (intent.hasExtra(CiweiMaoIntentExtras.POS)) {
            return true
        }
        if (!intent.getStringExtra(CiweiMaoIntentExtras.FROM).isNullOrBlank()) {
            return true
        }
        if (intent.data != null) {
            return true
        }
        return intent.action == Intent.ACTION_VIEW
    }

    private fun setStartPos(activity: Activity, index: Int): Boolean {
        var clazz: Class<*>? = activity.javaClass
        while (clazz != null) {
            val field = runCatching {
                clazz.getDeclaredField(CiweiMaoMembers.DG_FRAME_START_POS).also { it.isAccessible = true }
            }.getOrNull()
            if (field != null) {
                field.setInt(activity, index)
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }
}
