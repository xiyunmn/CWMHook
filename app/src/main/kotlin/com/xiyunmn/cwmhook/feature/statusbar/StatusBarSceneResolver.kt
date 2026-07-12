package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.xiyunmn.cwmhook.host.CiweiMaoClasses

/**
 * Resolves only stable host-owned signals. ViewPager discovery is deliberately
 * not used: the host contains nested pagers which are content state, not page
 * ownership.
 */
internal class StatusBarSceneResolver(
    private val sceneRules: StatusBarSceneRules,
    private val viewTreeTools: StatusBarViewTreeTools,
    private val windowRegistry: StatusBarWindowRegistry,
    @Suppress("UNUSED_PARAMETER") pagePagerIds: Set<String>,
) {
    fun resolveWindowSceneKey(window: Window, decorView: View, state: StatusBarWindowState): String {
        val activity = windowRegistry.findActivityForWindow(window, decorView)
        val activityName = activity?.javaClass?.name ?: window.context.javaClass.name
        if (activityName == CiweiMaoClasses.MAIN_FRAME_ACTIVITY) {
            return mainTabScene(decorView) ?: state.activeSceneKey.takeIf { it.startsWith("main-tab:") }
            ?: "main-tab:store"
        }
        if (sceneRules.isReaderActivity(activityName)) {
            return "$activityName|reader"
        }
        if (activityName == CiweiMaoClasses.BOOK_DETAIL_ACTIVITY) {
            return "$activityName|detail"
        }
        if (activityName == CiweiMaoClasses.RECHARGE_ACTIVITY) {
            return "$activityName|recharge"
        }
        return "$activityName|activity"
    }

    fun resolvePagerSceneKey(window: Window, view: View, state: StatusBarWindowState): String {
        return resolveWindowSceneKey(window, window.decorView, state)
    }

    fun buildFragmentSceneKey(window: Window, fragment: Any?, fragmentView: View): String {
        val activityName = windowRegistry.findActivityForWindow(window, window.decorView)?.javaClass?.name
            ?: window.context.javaClass.name
        val fragmentName = fragment?.javaClass?.name.orEmpty()
        val scene = when (fragmentName) {
            CiweiMaoClasses.RECOMMEND_FRAGMENT -> "main-tab:store"
            CiweiMaoClasses.RANK_FRAGMENT -> "main-tab:rank"
            CiweiMaoClasses.BOOK_SHELF_FRAGMENT -> "main-tab:shelf"
            CiweiMaoClasses.FIND_FRAGMENT -> "main-tab:find"
            CiweiMaoClasses.MINE_FRAGMENT -> "main-tab:mine"
            else -> null
        }
        return scene ?: "$activityName|fragment:$fragmentName#${viewTreeTools.resourceEntryName(fragmentView)}"
    }

    fun isLikelyPagePager(view: View, root: View): Boolean {
        // Kept for binary/source compatibility with the old scheduler. A pager
        // is no longer a scene signal and therefore never schedules a refresh.
        return false
    }

    private fun mainTabScene(root: View): String? {
        val tab = viewTreeTools.findViewByResourceName(root, "tab") as? ViewGroup ?: return null
        val checked = (0 until tab.childCount)
            .asSequence()
            .map { tab.getChildAt(it) }
            .firstOrNull { it.isSelected || (it is android.widget.CompoundButton && it.isChecked) }
        val id = checked?.let { viewTreeTools.resourceEntryName(it) } ?: return null
        return when {
            id == "tabbtn0" -> "main-tab:store"
            id == "tabbtn1" -> "main-tab:rank"
            id == "tabbtn2" -> "main-tab:shelf"
            id == "tabbtn3" -> "main-tab:find"
            id == "tabbtn4" -> "main-tab:mine"
            else -> null
        }
    }
}
