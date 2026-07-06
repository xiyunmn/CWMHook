package com.xiyunmn.cwmhook.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.xiyunmn.cwmhook.host.CiweiMaoIds
import com.xiyunmn.cwmhook.host.CiweiMaoPackages

internal class ModuleSettingsEntryResolver {
    fun bookShelfAnchor(fragment: Any): View? {
        return readField(fragment, "moreLay") as? View ?: readField(fragment, "moreImg") as? View
    }

    fun readerMoreAnchor(activity: Activity): View? {
        val id = activity.resources.getIdentifier(CiweiMaoIds.READER_MORE_LAYOUT, "id", CiweiMaoPackages.NOVEL)
        return if (id != 0) activity.findViewById(id) else null
    }

    fun fragmentActivity(fragment: Any): Activity? {
        val methodActivity = runCatching {
            fragment.javaClass.methods.firstOrNull {
                it.name == "getActivity" && it.parameterTypes.isEmpty()
            }?.invoke(fragment) as? Activity
        }.getOrNull()
        return methodActivity ?: readField(fragment, "act") as? Activity
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        return current as? Activity
    }

    private fun readField(instance: Any, name: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val field = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(instance)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

}
