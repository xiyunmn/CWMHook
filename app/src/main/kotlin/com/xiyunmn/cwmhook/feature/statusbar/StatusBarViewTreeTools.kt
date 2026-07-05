package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View
import android.view.ViewGroup

internal class StatusBarViewTreeTools(
    private val maxTraversedViews: Int,
) {
    fun findViewByResourceName(root: View, entryName: String): View? {
        var result: View? = null
        traverseViewTree(root) { view ->
            if (result == null && view.id != View.NO_ID && resourceEntryName(view) == entryName) {
                result = view
            }
        }
        return result
    }

    fun traverseViewTree(root: View, visitor: (View) -> Unit) {
        val queue = ArrayDeque<View>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxTraversedViews) {
            val view = queue.removeFirst()
            visited += 1
            visitor(view)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.add(view.getChildAt(index))
                }
            }
        }
    }

    fun traverseViewTreeWithDepth(root: View, limit: Int, visitor: (View, Int) -> Unit) {
        val queue = ArrayDeque<Pair<View, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < limit) {
            val (view, depth) = queue.removeFirst()
            visited += 1
            visitor(view, depth)
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.add(view.getChildAt(index) to depth + 1)
                }
            }
        }
    }

    fun topRelativeToRoot(view: View, root: View): Int {
        val viewLocation = IntArray(2)
        val rootLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        root.getLocationOnScreen(rootLocation)
        return viewLocation[1] - rootLocation[1]
    }

    fun resourceEntryName(view: View): String {
        if (view.id == View.NO_ID) {
            return "no-id"
        }
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("0x${view.id.toString(16)}")
    }
}
