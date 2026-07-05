package com.xiyunmn.cwmhook.ui.bottomtab

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.xiyunmn.cwmhook.ui.common.dp

internal class BottomTabDragAutoScroller(
    private val row: View,
    private val container: LinearLayout,
) {
    fun scrollIfNeeded() {
        val scrollView = findScrollView(container) ?: return
        val location = IntArray(2)
        row.getLocationOnScreen(location)
        val rowScreenY = location[1]
        val rowScreenBottom = rowScreenY + row.height

        scrollView.getLocationOnScreen(location)
        val scrollTop = location[1]
        val scrollBottom = scrollTop + scrollView.height

        val autoScrollEdge = dp(row.context, 60)
        val scrollSpeed = dp(row.context, 8)

        when {
            rowScreenY < scrollTop + autoScrollEdge -> {
                scrollView.smoothScrollBy(0, -scrollSpeed)
            }
            rowScreenBottom > scrollBottom - autoScrollEdge -> {
                scrollView.smoothScrollBy(0, scrollSpeed)
            }
        }
    }

    private fun findScrollView(view: View): ScrollView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is ScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }
}
