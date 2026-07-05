package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import android.view.Window
import kotlin.math.max

internal class StatusBarSceneResolver(
    private val sceneRules: StatusBarSceneRules,
    private val viewTreeTools: StatusBarViewTreeTools,
    private val windowRegistry: StatusBarWindowRegistry,
    private val pagePagerIds: Set<String>,
) {
    fun resolveWindowSceneKey(window: Window, decorView: View, state: StatusBarWindowState): String {
        val sceneKey = buildWindowSceneKey(window, decorView)
        return when {
            sceneRules.isGenericMainFrameScene(sceneKey) &&
                state.activeSceneKey.contains("|fragment:") &&
                sameActivity(window, decorView, state.activeSceneKey) -> state.activeSceneKey
            shouldKeepFragmentScene(window, decorView, sceneKey, state.activeSceneKey) -> state.activeSceneKey
            isDecorScene(sceneKey) && !isDecorScene(state.activeSceneKey) &&
                sameActivity(window, decorView, state.activeSceneKey) -> state.activeSceneKey
            else -> sceneKey
        }
    }

    fun resolvePagerSceneKey(window: Window, view: View, state: StatusBarWindowState): String {
        val sceneKey = buildViewSceneKey(window, view)
        return if (
            sceneRules.isGenericMainFrameScene(sceneKey) &&
            state.activeSceneKey.contains("|fragment:") &&
            sameActivity(window, window.decorView, state.activeSceneKey)
        ) {
            state.activeSceneKey
        } else if (shouldKeepFragmentScene(window, window.decorView, sceneKey, state.activeSceneKey)) {
            state.activeSceneKey
        } else {
            sceneKey
        }
    }

    fun buildFragmentSceneKey(window: Window, fragment: Any?, fragmentView: View): String {
        val marker = findBestPagerMarker(fragmentView)
        return buildString {
            append(activityName(window, window.decorView))
            append("|fragment:")
            append(fragment?.javaClass?.name ?: "unknown")
            append('@')
            append(fragmentView.javaClass.name)
            append('#')
            append(viewTreeTools.resourceEntryName(fragmentView))
            if (marker != null) {
                append('|')
                append(marker)
            }
        }
    }

    fun isLikelyPagePager(view: View, root: View): Boolean {
        if (!view.isShown || !isViewPagerLike(view)) {
            return false
        }
        if (view.id != View.NO_ID && pagePagerIds.contains(viewTreeTools.resourceEntryName(view))) {
            return true
        }
        val rootWidth = positiveOrFallback(root.width, root.resources.displayMetrics.widthPixels)
        val rootHeight = positiveOrFallback(root.height, root.resources.displayMetrics.heightPixels)
        val width = positiveOrFallback(view.width, view.measuredWidth)
        val height = positiveOrFallback(view.height, view.measuredHeight)
        return width >= rootWidth * 0.72f && height >= rootHeight / 4 && viewTreeTools.topRelativeToRoot(view, root) <= rootHeight / 2
    }

    private fun buildWindowSceneKey(window: Window, decorView: View): String {
        val activityName = activityName(window, decorView)
        return "$activityName|${findBestPagerMarker(decorView) ?: "decor:${viewTreeTools.resourceEntryName(decorView)}"}"
    }

    private fun buildViewSceneKey(window: Window, view: View): String {
        val activityName = windowRegistry.findActivity(view.context)?.javaClass?.name ?: activityName(window, window.decorView)
        return "$activityName|pager:${view.javaClass.name}#${viewTreeTools.resourceEntryName(view)}@${viewPagerCurrentItem(view) ?: 0}"
    }

    private fun shouldKeepFragmentScene(
        window: Window,
        decorView: View,
        nextSceneKey: String,
        activeSceneKey: String,
    ): Boolean {
        val marker = nextSceneKey.substringAfter('|', "")
        return marker.isNotEmpty() &&
            activeSceneKey.contains("|fragment:") &&
            activeSceneKey.contains(marker) &&
            sameActivity(window, decorView, activeSceneKey)
    }

    private fun findBestPagerMarker(root: View): String? {
        var bestMarker: String? = null
        var bestScore = Int.MIN_VALUE
        viewTreeTools.traverseViewTree(root) { view ->
            if (!isLikelyPagePager(view, root)) {
                return@traverseViewTree
            }
            val score = pagerScore(view, root)
            if (score > bestScore) {
                bestScore = score
                bestMarker = "pager:${view.javaClass.name}#${viewTreeTools.resourceEntryName(view)}@${viewPagerCurrentItem(view) ?: 0}"
            }
        }
        return bestMarker
    }

    private fun pagerScore(view: View, root: View): Int {
        val rootHeight = positiveOrFallback(root.height, root.resources.displayMetrics.heightPixels)
        var score = if (view.id != View.NO_ID && pagePagerIds.contains(viewTreeTools.resourceEntryName(view))) 1000 else 0
        score += positiveOrFallback(view.height, view.measuredHeight) * 100 / max(1, rootHeight)
        score -= viewTreeTools.topRelativeToRoot(view, root) / 8
        return score
    }

    private fun viewPagerCurrentItem(view: View): Int? {
        return if (isViewPagerLike(view)) {
            runCatching { view.javaClass.getMethod("getCurrentItem").invoke(view) as? Int }.getOrNull()
        } else {
            null
        }
    }

    private fun isViewPagerLike(view: View): Boolean {
        var clazz: Class<*>? = view.javaClass
        while (clazz != null) {
            if (clazz.name == "androidx.viewpager.widget.ViewPager" || clazz.simpleName.contains("ViewPager", ignoreCase = true)) {
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }

    private fun sameActivity(window: Window, decorView: View, sceneKey: String): Boolean {
        return sceneKey.startsWith("${activityName(window, decorView)}|")
    }

    private fun isDecorScene(sceneKey: String): Boolean {
        return sceneKey.substringAfter('|', "").startsWith("decor:")
    }

    private fun activityName(window: Window, decorView: View): String {
        return windowRegistry.findActivityForWindow(window, decorView)?.javaClass?.name ?: window.context.javaClass.name
    }

    private fun positiveOrFallback(value: Int, fallback: Int): Int {
        return if (value > 0) value else max(fallback, 0)
    }
}
