package com.xiyunmn.cwmhook.ui.bottomtab

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import com.xiyunmn.cwmhook.ui.common.dp
import kotlin.math.abs

class BottomTabDragController(
    private val row: View,
    private val container: LinearLayout,
    private val state: BottomTabPanelState,
    private val key: String,
    private val render: () -> Unit,
) : View.OnTouchListener {
    private var dragging = false
    private var longPressTriggered = false
    private var touchSlop = 0
    private var downRawY = 0f
    private var startIndex = -1
    private var targetIndex = -1
    private var rowSlotHeight = 0
    private var lastTargetIndex = -1
    private val autoScroller = BottomTabDragAutoScroller(row, container)
    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val longPressTimeout = 300L
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (dragging) {
                autoScroller.scrollIfNeeded()
                scrollHandler.postDelayed(this, 50L)
            }
        }
    }
    private val longPressRunnable = Runnable {
        if (!dragging && !longPressTriggered) {
            longPressTriggered = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                row.performHapticFeedback(
                    android.view.HapticFeedbackConstants.GESTURE_START,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } else {
                row.performHapticFeedback(
                    android.view.HapticFeedbackConstants.LONG_PRESS,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop / 2
                downRawY = event.rawY
                dragging = false
                longPressTriggered = false
                startIndex = state.order.indexOf(key)
                targetIndex = startIndex
                lastTargetIndex = startIndex
                rowSlotHeight = row.bottomTabRowSlotHeight()
                scrollHandler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = abs(event.rawY - downRawY)

                if (!dragging && longPressTriggered && dy > touchSlop) {
                    dragging = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    container.parent?.requestDisallowInterceptTouchEvent(true)
                    row.elevation = dp(row.context, 12).toFloat()
                    row.translationZ = dp(row.context, 12).toFloat()
                    row.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .alpha(0.96f)
                        .setDuration(90L)
                        .start()
                    scrollHandler.post(autoScrollRunnable)
                }

                if (!longPressTriggered && dy > touchSlop) {
                    scrollHandler.removeCallbacks(longPressRunnable)
                }

                if (dragging) {
                    row.translationY = event.rawY - downRawY
                    val newTargetIndex = BottomTabDragTargetResolver.resolve(
                        container = container,
                        startIndex = startIndex,
                        rawY = event.rawY,
                        lastIndex = state.order.lastIndex,
                    )
                    if (newTargetIndex != targetIndex) {
                        targetIndex = newTargetIndex
                        if (targetIndex != lastTargetIndex) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                row.performHapticFeedback(android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                            } else {
                                row.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                            lastTargetIndex = targetIndex
                        }
                        offsetSiblings()
                    }
                }
                return dragging
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                scrollHandler.removeCallbacks(longPressRunnable)
                if (!dragging) {
                    return false
                }
                scrollHandler.removeCallbacks(autoScrollRunnable)
                view.parent?.requestDisallowInterceptTouchEvent(false)
                container.parent?.requestDisallowInterceptTouchEvent(false)
                val shouldMove = event.actionMasked == MotionEvent.ACTION_UP &&
                    dragging &&
                    startIndex in state.order.indices &&
                    targetIndex in state.order.indices &&
                    startIndex != targetIndex
                val moved = shouldMove && state.move(startIndex, targetIndex)
                resetDragVisuals()
                dragging = false
                longPressTriggered = false
                if (moved) {
                    render()
                }
                return true
            }
        }
        return false
    }

    private fun offsetSiblings() {
        if (startIndex < 0 || targetIndex < 0 || rowSlotHeight <= 0) {
            return
        }
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child === row) {
                continue
            }
            child.translationY = when {
                targetIndex > startIndex && index in (startIndex + 1)..targetIndex -> -rowSlotHeight.toFloat()
                targetIndex < startIndex && index in targetIndex until startIndex -> rowSlotHeight.toFloat()
                else -> 0f
            }
        }
    }

    private fun resetSiblingOffsets() {
        for (index in 0 until container.childCount) {
            container.getChildAt(index).translationY = 0f
        }
    }

    private fun resetDragVisuals() {
        row.animate().cancel()
        row.translationY = 0f
        row.scaleX = 1f
        row.scaleY = 1f
        row.alpha = 1f
        row.elevation = 0f
        row.translationZ = 0f
        resetSiblingOffsets()
    }
}
