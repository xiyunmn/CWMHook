package com.xiyunmn.cwmhook.feature.panel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.xiyunmn.cwmhook.host.CiweiMaoIds
import com.xiyunmn.cwmhook.host.CiweiMaoPackages
import kotlin.math.abs

internal class FloatingPanelEntryResolver {
    fun fragmentAnchor(fragment: Any): View? {
        return readField(fragment, "fenleiView") as? View ?: findAnimationView(fragment)
    }

    fun bookShelfAnchor(fragment: Any): View? {
        return readField(fragment, "moreLay") as? View ?: readField(fragment, "moreImg") as? View
    }

    fun mainFrameAnchor(activity: Activity): View? {
        val id = activity.resources.getIdentifier(CiweiMaoIds.FLOATING_ENTRY, "id", CiweiMaoPackages.NOVEL)
        if (id == 0) {
            return null
        }
        val view = activity.findViewById<View>(id) ?: return null
        if (view.javaClass.name != "com.airbnb.lottie.LottieAnimationView") {
            return null
        }
        val expectedWidth = dp(activity, 66)
        val expectedHeight = dp(activity, 44)
        val actualWidth = if (view.width > 0) view.width else view.layoutParams?.width ?: 0
        val actualHeight = if (view.height > 0) view.height else view.layoutParams?.height ?: 0
        val tolerance = dp(activity, 3)
        return if (abs(actualWidth - expectedWidth) <= tolerance && abs(actualHeight - expectedHeight) <= tolerance) {
            view
        } else {
            null
        }
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

    private fun findAnimationView(fragment: Any): View? {
        val root = readField(fragment, "rootV") as? View ?: return null
        val id = root.context.resources.getIdentifier(CiweiMaoIds.FLOATING_ENTRY, "id", CiweiMaoPackages.NOVEL)
        return if (id != 0) root.findViewById(id) else null
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

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }
}
