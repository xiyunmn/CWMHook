package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.ui.bottomtab.BottomTabDragAutoScroller
import com.xiyunmn.cwmhook.ui.bottomtab.BottomTabDragTargetResolver
import com.xiyunmn.cwmhook.ui.bottomtab.bottomTabRowSlotHeight
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.edgeGlowBackground
import com.xiyunmn.cwmhook.ui.icons.IconType
import com.xiyunmn.cwmhook.ui.icons.InlineIconView
import java.io.File
import kotlin.math.abs

internal class ModuleSettingsReaderFontRows(
    private val activity: Activity,
    private val theme: PanelTheme,
    private val paths: MutableList<String>,
    private val currentPath: String,
    private val onSelect: (String) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onRenderRequested: () -> Unit,
) {
    fun createRow(path: String, container: LinearLayout): LinearLayout {
        val selected = File(path).absolutePath == File(currentPath).absolutePath
        val file = File(path)
        val slot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.rowBackground)
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = edgeGlowBackground(activity, theme.rowBackground, theme.accent)
            setPadding(dp(activity, 12), 0, dp(activity, 10), 0)
            isClickable = true
            setOnClickListener {
                if (file.isFile) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSelect(path)
                }
            }
        }
        val dragHandle = FrameLayout(activity).apply {
            addView(
                InlineIconView(activity, IconType.DRAG, if (selected) theme.accent else theme.mutedIcon),
                FrameLayout.LayoutParams(dp(activity, 38), dp(activity, 38), Gravity.CENTER),
            )
            setOnTouchListener(
                ReaderFontDragController(
                    row = slot,
                    container = container,
                    paths = paths,
                    path = path,
                    onOrderChanged = onOrderChanged,
                    onRenderRequested = onRenderRequested,
                ),
            )
        }
        row.addView(dragHandle, LinearLayout.LayoutParams(dp(activity, 46), ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(
            InlineIconView(activity, IconType.FONT, if (selected) theme.accent else theme.subText),
            LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36)).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), 0, dp(activity, 8), 0)
        }
        texts.addView(
            TextView(activity).apply {
                text = displayName(path)
                textSize = 15f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) theme.accent else theme.text)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        texts.addView(
            TextView(activity).apply {
                text = if (file.isFile) {
                    if (selected) "当前使用 · $path" else path
                } else {
                    "文件不存在"
                }
                textSize = 11f
                setTextColor(theme.subText)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                includeFontPadding = false
                setPadding(0, dp(activity, 5), 0, 0)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(
            FrameLayout(activity).apply {
                if (selected) {
                    addView(
                        InlineIconView(activity, IconType.RADIO_SELECTED, theme.accent),
                        FrameLayout.LayoutParams(dp(activity, 36), dp(activity, 36), Gravity.CENTER),
                    )
                }
            },
            LinearLayout.LayoutParams(dp(activity, 44), ViewGroup.LayoutParams.MATCH_PARENT),
        )
        row.addView(
            FrameLayout(activity).apply {
                isClickable = true
                addView(
                    InlineIconView(activity, IconType.DELETE, theme.accent),
                    FrameLayout.LayoutParams(dp(activity, 38), dp(activity, 38), Gravity.CENTER),
                )
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onDelete(path)
                }
            },
            LinearLayout.LayoutParams(dp(activity, 52), ViewGroup.LayoutParams.MATCH_PARENT),
        )

        slot.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        slot.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        return slot
    }

    fun rowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 66))
    }

    private fun separator(): View {
        return View(activity).apply {
            setBackgroundColor(theme.separator)
        }
    }

    private fun displayName(path: String): String {
        val name = File(path).name.ifBlank { path }
        return name.substringBeforeLast('.', name)
    }
}

private class ReaderFontDragController(
    private val row: View,
    private val container: LinearLayout,
    private val paths: MutableList<String>,
    private val path: String,
    private val onOrderChanged: (List<String>) -> Unit,
    private val onRenderRequested: () -> Unit,
) : View.OnTouchListener {
    private var dragging = false
    private var longPressTriggered = false
    private var touchSlop = 0
    private var downRawY = 0f
    private var startIndex = -1
    private var targetIndex = -1
    private var rowSlotHeight = 0
    private val autoScroller = BottomTabDragAutoScroller(row, container)
    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!dragging && !longPressTriggered) {
            longPressTriggered = true
            row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (dragging) {
                autoScroller.scrollIfNeeded()
                scrollHandler.postDelayed(this, 50L)
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
                startIndex = paths.indexOf(path)
                targetIndex = startIndex
                rowSlotHeight = row.bottomTabRowSlotHeight()
                scrollHandler.postDelayed(longPressRunnable, 300L)
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
                    row.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.96f).setDuration(90L).start()
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
                        lastIndex = paths.lastIndex,
                    )
                    if (newTargetIndex != targetIndex) {
                        targetIndex = newTargetIndex
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
                val moved = event.actionMasked == MotionEvent.ACTION_UP && movePath()
                resetDragVisuals()
                dragging = false
                longPressTriggered = false
                if (moved) {
                    onOrderChanged(paths.toList())
                    onRenderRequested()
                }
                return true
            }
        }
        return false
    }

    private fun movePath(): Boolean {
        if (startIndex !in paths.indices || targetIndex !in paths.indices || startIndex == targetIndex) {
            return false
        }
        val item = paths.removeAt(startIndex)
        paths.add(targetIndex.coerceIn(0, paths.size), item)
        return true
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

    private fun resetDragVisuals() {
        row.animate().cancel()
        row.translationY = 0f
        row.scaleX = 1f
        row.scaleY = 1f
        row.alpha = 1f
        row.elevation = 0f
        row.translationZ = 0f
        for (index in 0 until container.childCount) {
            container.getChildAt(index).translationY = 0f
        }
    }
}
