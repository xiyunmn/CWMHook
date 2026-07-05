package com.xiyunmn.cwmhook.ui.panel

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

class FloatingPanelDragController(private val target: View) : View.OnTouchListener {
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var touchSlop = 0

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = target.translationX
                startY = target.translationY
                dragging = false
                touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
                view.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    target.translationX = clampX(startX + dx)
                    target.translationY = clampY(startY + dy)
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun clampX(value: Float): Float {
        val parent = target.parent as? ViewGroup ?: return value
        if (parent.width <= 0 || target.width <= 0) {
            return value
        }
        val min = -target.left.toFloat()
        val max = (parent.width - target.right).toFloat()
        return value.coerceIn(min, max)
    }

    private fun clampY(value: Float): Float {
        val parent = target.parent as? ViewGroup ?: return value
        if (parent.height <= 0 || target.height <= 0) {
            return value
        }
        val min = -target.top.toFloat()
        val max = (parent.height - target.bottom).toFloat()
        return value.coerceIn(min, max)
    }
}
