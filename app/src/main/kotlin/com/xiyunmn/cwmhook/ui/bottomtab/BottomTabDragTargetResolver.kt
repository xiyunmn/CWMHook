package com.xiyunmn.cwmhook.ui.bottomtab

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

internal object BottomTabDragTargetResolver {
    fun resolve(
        container: LinearLayout,
        startIndex: Int,
        rawY: Float,
        lastIndex: Int,
    ): Int {
        if (startIndex < 0 || startIndex >= container.childCount) {
            return startIndex.coerceIn(0, lastIndex)
        }

        val location = IntArray(2)
        container.getLocationOnScreen(location)
        val containerTop = location[1]
        val draggedRowCenterY = rawY - containerTop

        var accumulatedTop = 0
        var bestIndex = startIndex

        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            val childHeight = child.bottomTabRowSlotHeight()
            val childMiddle = accumulatedTop + childHeight / 2

            val orderIndex = if (index < startIndex) {
                index
            } else if (index == startIndex) {
                accumulatedTop += childHeight
                continue
            } else {
                index - 1
            }

            if (draggedRowCenterY < childMiddle) {
                bestIndex = orderIndex
                break
            } else {
                bestIndex = orderIndex + 1
            }

            accumulatedTop += childHeight
        }

        return bestIndex.coerceIn(0, lastIndex)
    }
}

internal fun View.bottomTabRowSlotHeight(): Int {
    return height + (layoutParams as? ViewGroup.MarginLayoutParams).bottomMarginOrZero()
}

private fun ViewGroup.MarginLayoutParams?.bottomMarginOrZero(): Int {
    return this?.bottomMargin ?: 0
}
