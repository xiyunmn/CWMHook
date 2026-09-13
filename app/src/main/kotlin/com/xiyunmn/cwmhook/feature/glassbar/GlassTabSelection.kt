package com.xiyunmn.cwmhook.feature.glassbar

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.RadioButton
import android.widget.RadioGroup
import com.xiyunmn.cwmhook.core.glass.GlassBackdropView
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import kotlin.math.abs

/** Draggable glass selection using real RadioButton IDs and native click dispatch. */
internal class GlassTabSelection(
    private val bar: RadioGroup,
    private val glass: GlassBackdropView,
) : AutoCloseable {
    private val location = IntArray(2)
    private val rect = Rect()
    private val slop = ViewConfiguration.get(bar.context).scaledTouchSlop
    private val ownership = GlassViewOwnership()
    private val translation = ownership.own({ bar.translationX }, { bar.translationX = it })
    private var pickedUp: RadioButton? = null
    private var startX = 0f
    private var dragged = false
    private var consumingGesture = false
    private var hovered: RadioButton? = null
    private var preview: GlassViewOwnership? = null
    private var scaled: RadioButton? = null
    private var scale: GlassViewOwnership? = null
    private var scaleX: OwnedProperty<Float>? = null
    private var scaleY: OwnedProperty<Float>? = null
    private var progress = 0f
    private var closed = false

    init {
        glass.onInteractionProgress = ::scaleSelected
        glass.onInteractionOffset = { offset -> translation.update { it + offset } }
        glass.onSelectionPositionChanged = { if (dragged) preview(nearestToLens()) }
    }

    /** Ordinary taps stay native. A recognized drag sends one CANCEL before taking over the stream. */
    fun dispatchTouch(event: MotionEvent, dispatchNative: (MotionEvent) -> Boolean): Boolean {
        if (closed) return dispatchNative(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) consumingGesture = false
        val wasConsuming = consumingGesture
        observe(event.actionMasked, event.rawX, event.rawY)
        val takeOver = !wasConsuming && dragged
        consumingGesture = wasConsuming || dragged
        val consumed = consumingGesture
        if (takeOver) {
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            try { dispatchNative(cancel) } finally { cancel.recycle() }
            // CANCEL clears the native pressed state and can rewrite a compound icon's state.
            preview?.close()
            preview = null
            hovered = null
            preview(nearestToLens())
        }
        val result = if (consumed) true else dispatchNative(event)
        if (!closed) {
            // Native UP/performClick can change the checked ID. Release toward that final tab.
            update()
            glass.observeTouch(event.actionMasked, event.rawX, event.rawY)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            consumingGesture = false
        }
        return result
    }

    private fun observe(action: Int, x: Float, y: Float) {
        if (pickedUp != null && !validPickup()) clearDrag()
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                clearDrag()
                val selected = selected() ?: return
                if (!bar.isShown || !selected.isEnabled) return
                selected.getLocationOnScreen(location)
                val insideX = x >= location[0] && x < location[0] + selected.width
                bar.getLocationOnScreen(location)
                if (insideX && y >= location[1] && y < location[1] + bar.height && glass.beginSelectionDrag(x)) {
                    pickedUp = selected
                    startX = x
                }
            }
            MotionEvent.ACTION_MOVE -> if (pickedUp != null) {
                if (abs(x - startX) > slop) dragged = true
                if (dragged) {
                    glass.moveSelectionDrag(x)
                    preview(nearestToLens())
                }
            }
            MotionEvent.ACTION_UP -> {
                val target = if (dragged && validPickup()) nearestToLens() else null
                clearDrag(releaseVisual = false)
                if (target != null && target !== selected()) {
                    val previousId = bar.checkedRadioButtonId
                    target.performClick()
                    ModuleFileLogger.i("CWMHook.GlassBars", "Tab drag committed: from=$previousId to=${target.id}")
                }
                scaleSelected(progress)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> clearDrag()
        }
    }

    private fun selected(): RadioButton? = bar.findViewById<RadioButton>(bar.checkedRadioButtonId)
        ?.takeIf { it.parent === bar && it.visibility == View.VISIBLE && it.width > 0 }

    private fun validPickup(): Boolean = pickedUp?.let {
        bar.isShown && it.parent === bar && it.visibility == View.VISIBLE && it.isEnabled && it.id == bar.checkedRadioButtonId
    } == true

    private fun nearestToLens(): RadioButton? {
        val center = glass.selectionCenterX ?: return null
        var nearest: RadioButton? = null
        var distance = Float.MAX_VALUE
        for (index in 0 until bar.childCount) {
            val tab = bar.getChildAt(index) as? RadioButton ?: continue
            if (tab.visibility != View.VISIBLE || !tab.isEnabled || tab.width <= 0) continue
            val delta = abs(center - tab.left - tab.translationX + bar.scrollX - tab.width / 2f)
            if (delta < distance) { distance = delta; nearest = tab }
        }
        return nearest
    }

    private fun preview(target: RadioButton?) {
        if (hovered === target) return
        preview?.close()
        preview = null
        hovered = target
        val selected = selected()
        if (target != null && selected != null && target !== selected) {
            preview = GlassViewOwnership().also { ownership ->
                listOf(selected, target).forEach { tab ->
                    // Do not call setChecked: that would notify the host and navigate during a drag.
                    // State arrays can contain a trailing zero slot reserved for CompoundButton.
                    // Zero terminates a state set, so append checked only after removing it.
                    val state = tab.drawableState.filter { it != 0 && it != android.R.attr.state_checked }.toMutableList()
                    if (tab === target) state += android.R.attr.state_checked
                    val desired = state.toIntArray()
                    val color = tab.textColors.getColorForState(desired, tab.currentTextColor)
                    ownership.own({ tab.textColors }, { tab.setTextColor(it) }).set(ColorStateList.valueOf(color))
                    (tab.compoundDrawables.toList() + tab.compoundDrawablesRelative.toList()).filterNotNull().distinct().forEach { icon ->
                        ownership.own({ icon.state }, { icon.state = it }).set(desired)
                    }
                }
            }
        }
        scaleSelected(progress)
    }

    private fun scaleSelected(value: Float) {
        progress = value
        val target = hovered ?: selected()
        if (target !== scaled || value <= 0f) {
            scale?.close()
            scale = null
            scaled = null
            scaleX = null
            scaleY = null
        }
        if (closed || value <= 0f || target == null) return
        if (scale == null) {
            scaled = target
            scale = GlassViewOwnership().also {
                scaleX = it.own({ target.scaleX }, { target.scaleX = it })
                scaleY = it.own({ target.scaleY }, { target.scaleY = it })
            }
        }
        scaleX?.update { it * (1f + value * 0.20f) }
        scaleY?.update { it * (1f + value * 0.20f) }
    }

    fun update() {
        if (closed) return
        if (pickedUp != null && !validPickup()) clearDrag()
        val selected = selected()
        if (selected == null) {
            glass.clearSelection()
            return
        }
        selected.getDrawingRect(rect)
        bar.offsetDescendantRectToMyCoords(selected, rect)
        val inset = bar.dp(4).toFloat()
        glass.setSelection(rect.left + inset, inset, rect.right - inset, bar.height - inset)
    }

    private fun clearDrag(releaseVisual: Boolean = true) {
        preview?.close()
        preview = null
        hovered = null
        pickedUp = null
        dragged = false
        if (releaseVisual) glass.cancelSelectionDrag()
        scaleSelected(progress)
    }

    fun cancelTouch() {
        consumingGesture = false
        clearDrag()
        scaleSelected(0f)
        translation.update { it }
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelTouch()
        glass.onInteractionProgress = null
        glass.onInteractionOffset = null
        glass.onSelectionPositionChanged = null
        ownership.close()
    }
}
