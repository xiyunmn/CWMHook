package com.xiyunmn.cwmhook.feature.statusbar

import android.widget.RadioGroup
import com.xiyunmn.cwmhook.core.XposedCompat
import com.xiyunmn.cwmhook.host.CiweiMaoClasses
import io.github.libxposed.api.XposedModule

internal class StatusBarMainTabHookInstaller(
    private val windowRegistry: StatusBarWindowRegistry,
    private val applyWindow: (android.view.Window, String, Boolean, String?) -> Unit,
    private val logTag: String,
) {
    fun install(module: XposedModule) {
        XposedCompat.hookAfter(
            module,
            RadioGroup::class.java.getDeclaredMethod("check", Int::class.javaPrimitiveType),
            "$logTag.RadioGroup.check",
        ) { chain ->
            val group = chain.thisObject as? RadioGroup ?: return@hookAfter
            val activity = windowRegistry.findActivity(group.context) ?: return@hookAfter
            if (activity.javaClass.name != CiweiMaoClasses.MAIN_FRAME_ACTIVITY) return@hookAfter
            if (runCatching { group.resources.getResourceEntryName(group.id) }.getOrNull() != "tab") return@hookAfter
            val state = windowRegistry.state(activity.window)
            state.bumpGeneration("mainTab.check")
            group.post { applyWindow(activity.window, "MainTab.check", false, null) }
        }
    }
}
