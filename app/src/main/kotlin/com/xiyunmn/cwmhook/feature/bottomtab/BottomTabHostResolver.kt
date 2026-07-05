package com.xiyunmn.cwmhook.feature.bottomtab

import android.app.Activity
import android.widget.RadioButton
import android.widget.RadioGroup
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfigStore
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import com.xiyunmn.cwmhook.host.CiweiMaoIds
import com.xiyunmn.cwmhook.host.CiweiMaoPackages

object BottomTabHostResolver {
    fun isMainFrame(activity: Activity): Boolean {
        return activity.javaClass.name == CiweiMaoClasses.MAIN_FRAME_ACTIVITY
    }

    fun findMainTabGroup(activity: Activity): RadioGroup? {
        val id = resourceId(activity, CiweiMaoIds.MAIN_TAB_GROUP) ?: return null
        return activity.findViewById(id)
    }

    fun resolveButtons(activity: Activity): Map<String, RadioButton>? {
        val result = LinkedHashMap<String, RadioButton>()
        BottomTabConfigStore.TABS.forEach { spec ->
            val id = resourceId(activity, spec.buttonName) ?: return null
            val button = activity.findViewById<RadioButton>(id) ?: return null
            result[spec.key] = button
        }
        return result
    }

    private fun resourceId(activity: Activity, name: String): Int? {
        val id = activity.resources.getIdentifier(name, "id", CiweiMaoPackages.NOVEL)
        return id.takeIf { it != 0 }
    }
}
