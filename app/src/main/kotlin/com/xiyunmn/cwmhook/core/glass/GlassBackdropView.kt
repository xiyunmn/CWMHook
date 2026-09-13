package com.xiyunmn.cwmhook.core.glass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** A drawing-only sibling. the host keeps its own controls, hit targets, icons and badges. */
class GlassBackdropView(
    context: Context,
    private val source: View,
    private val failure: (Throwable) -> Unit,
) : View(context), AutoCloseable {
    var onInteractionProgress: ((Float) -> Unit)? = null
    var onInteractionOffset: ((Float) -> Unit)? = null
    var onSelectionPositionChanged: ((Float) -> Unit)? = null
    private val density = resources.displayMetrics.density
    val halo: Int = ceil(64f * density).toInt()
    private val renderer = if (Build.VERSION.SDK_INT >= 33) GlassLensRenderer(failure) else null
    private val selection = GlassSelectionMotion()
    private val interaction = GlassInteractionMotion()
    private val location = IntArray(2)
    private val selectionRect = RectF()
    private val hostSelection = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tilt: GlassTilt? = null
    private var spec = GlassEffectsSpec()
    private var night = false
    private var materialWidth = 0
    private var materialHeight = 0
    private var draggingSelection = false
    private var dragOffset = 0f
    private var gestureOriginX = 0f
    private var startRawX = 0f
    private var pressed = false
    private var touchX = 0f
    private var active = true
    private var closed = false
    private var frameScheduled = false
    private var lastFrame = 0L
    private var observer: ViewTreeObserver? = null
    private var lastProgress = Float.NaN
    private var lastOffset = Float.NaN
    private var lastCenter = Float.NaN
    private var hasBackdrop = false

    val selectionCenterX: Float? get() = if (selection.visible) selection.center * materialWidth else null

    private val animationFrame = Choreographer.FrameCallback { frameTime ->
        frameScheduled = false
        if (!closed && active && isShown) {
            val seconds = if (lastFrame == 0L) 1f / 60f else (frameTime - lastFrame) / 1_000_000_000f
            lastFrame = frameTime
            selection.advance(seconds)
            interaction.advance(seconds, selection.velocity, abs(selection.center - selection.targetCenter) < 0.025f)
            updateVisuals()
            if (selection.running || interaction.running) scheduleFrame() else lastFrame = 0L
        }
    }
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        updateTilt()
        if (active && !closed && isShown && windowVisibility == VISIBLE && source.isShown && isHardwareAccelerated) {
            getLocationOnScreen(location)
            val ready = renderer?.prepare(source, location[0] + halo, location[1] + halo) == true
            if (hasBackdrop != ready) {
                hasBackdrop = ready
                invalidate()
            }
        }
        true
    }

    init {
        isClickable = false
        isLongClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        setWillNotDraw(false)
    }

    fun configure(width: Int, height: Int, effects: GlassEffectsSpec, isNight: Boolean) {
        if (closed) return
        if (materialWidth == width && materialHeight == height && spec == effects && night == isNight) return
        materialWidth = width
        materialHeight = height
        spec = effects
        night = isNight
        renderer?.configure(width, height, density, effects, isNight)
        interaction.configure(effects.press && ValueAnimator.areAnimatorsEnabled())
        if (!effects.press || !ValueAnimator.areAnimatorsEnabled()) selection.settle()
        updateTilt()
        updateVisuals()
    }

    fun setActive(value: Boolean) {
        if (closed || active == value) return
        active = value
        if (!value) resetInteraction()
        // Freeze the last source display list while a non-dimming popup owns focus.
        updateTilt()
        invalidate()
    }

    fun setSelection(left: Float, top: Float, right: Float, bottom: Float) {
        if (closed || materialWidth <= 0 || materialHeight <= 0) return
        hostSelection.set(left, top, right, bottom)
        if (draggingSelection) return
        val hadSelection = selection.visible
        val previousTarget = selection.targetCenter
        targetSelection(left, top, right, bottom)
        if (hadSelection && abs(selection.targetCenter - previousTarget) > 0.0001f && !pressed && canAnimate()) {
            interaction.press()
            interaction.release()
            scheduleFrame()
        }
    }

    private fun targetSelection(left: Float, top: Float, right: Float, bottom: Float) {
        val changed = selection.target(left / materialWidth, top / materialHeight, right / materialWidth, bottom / materialHeight, canAnimate())
        if (changed) updateVisuals()
        if (selection.running) scheduleFrame()
    }

    fun clearSelection() {
        if (!selection.visible && hostSelection.isEmpty) return
        draggingSelection = false
        hostSelection.setEmpty()
        selection.clear()
        updateVisuals()
    }

    /** A single owner supplies drag coordinates; preview and release read this same rendered lens. */
    fun beginSelectionDrag(rawX: Float): Boolean {
        if (closed || !active || !selection.visible || materialWidth <= 0) return false
        getLocationOnScreen(location)
        gestureOriginX = location[0] + halo.toFloat()
        startRawX = rawX
        dragOffset = rawX - gestureOriginX - selection.center * materialWidth
        draggingSelection = true
        return true
    }

    fun moveSelectionDrag(rawX: Float) {
        if (!draggingSelection || hostSelection.isEmpty) return
        val half = hostSelection.width() / 2f
        val center = (rawX - gestureOriginX - dragOffset).coerceIn(half, materialWidth - half)
        targetSelection(center - half, hostSelection.top, center + half, hostSelection.bottom)
        interaction.drag(rawX - startRawX)
        if (interaction.running) scheduleFrame()
    }

    fun cancelSelectionDrag() {
        releaseSelection()
    }

    private fun releaseSelection() {
        if (!draggingSelection) return
        draggingSelection = false
        if (!hostSelection.isEmpty) targetSelection(hostSelection.left, hostSelection.top, hostSelection.right, hostSelection.bottom)
    }

    fun observeTouch(action: Int, rawX: Float, rawY: Float) {
        if (closed || !active || !isShown) return
        getLocationOnScreen(location)
        val x = rawX - location[0] - halo
        val y = rawY - location[1] - halo
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (x !in 0f..materialWidth.toFloat() || y !in 0f..materialHeight.toFloat()) return
                pressed = true
                touchX = x
                interaction.configure(spec.press && ValueAnimator.areAnimatorsEnabled())
                interaction.press()
            }
            MotionEvent.ACTION_MOVE -> if (pressed) {
                touchX = x.coerceIn(0f, materialWidth.toFloat())
            }
            MotionEvent.ACTION_UP -> if (pressed) {
                pressed = false
                releaseSelection()
                interaction.release()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> resetInteraction()
        }
        updateVisuals()
        if (interaction.running) scheduleFrame()
    }

    private fun canAnimate() = active && spec.press && ValueAnimator.areAnimatorsEnabled()

    private fun scheduleFrame() {
        if (frameScheduled || !active || closed || !isShown) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(animationFrame)
    }

    private fun stopFrames() {
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(animationFrame)
        frameScheduled = false
        lastFrame = 0L
    }

    private fun resetInteraction() {
        pressed = false
        releaseSelection()
        stopFrames()
        selection.settle()
        interaction.reset()
        updateVisuals()
    }

    private fun updateVisuals() {
        val progress = interaction.progress
        val center = selection.center * materialWidth
        val halfWidth = (selection.right - selection.left) * materialWidth * interaction.scaleX / 2f
        val centerY = (selection.top + selection.bottom) * materialHeight / 2f
        val halfHeight = (selection.bottom - selection.top) * materialHeight * interaction.scaleY / 2f
        selectionRect.set(center - halfWidth, centerY - halfHeight, center + halfWidth, centerY + halfHeight)
        renderer?.setPress(progress, if (selection.visible) center else touchX, materialHeight / 2f)
        renderer?.setSelection(selectionRect, selection.visible)
        if (lastProgress != progress) {
            lastProgress = progress
            onInteractionProgress?.invoke(progress)
        }
        val offset = if (selection.visible) GlassInteractionMotion.rubberBand(interaction.dragDistance, materialWidth.toFloat(), density) else 0f
        if (lastOffset != offset) {
            lastOffset = offset
            onInteractionOffset?.invoke(offset)
        }
        if (lastCenter != center) {
            lastCenter = center
            if (selection.visible) onSelectionPositionChanged?.invoke(center)
        }
        invalidate()
    }

    private fun updateTilt() {
        if (closed) return
        if (renderer != null && spec.tracksLight && tilt == null) {
            tilt = GlassTilt(context, { display?.rotation ?: 0 }, { angle ->
                renderer.setLightAngle(angle)
                invalidate()
            }, failure)
        }
        tilt?.setActive(spec.tracksLight && active && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hasWindowFocus())
        if (!spec.tracksLight) renderer?.setLightAngle(GlassTilt.REST_ANGLE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (closed || materialWidth <= 0 || materialHeight <= 0) return
        val save = canvas.save()
        canvas.translate(halo.toFloat(), halo.toFloat())
        if (renderer?.draw(canvas) != true) {
            paint.color = if (night) 0xD9262628.toInt() else 0xD9F2F2F7.toInt()
            val radius = min(materialWidth, materialHeight) / 2f
            canvas.drawRoundRect(0f, 0f, materialWidth.toFloat(), materialHeight.toFloat(), radius, radius, paint)
            if (selection.visible) {
                paint.color = if (night) 0x1AFFFFFF else 0x1A000000
                val sr = min(selectionRect.width(), selectionRect.height()) / 2f
                canvas.drawRoundRect(selectionRect, sr, sr, paint)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!closed) {
            observer = viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
            updateTilt()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isShown) resetInteraction()
        updateTilt()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) resetInteraction()
        updateTilt()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) resetInteraction()
        updateTilt()
    }

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
        tilt?.setActive(false)
        resetInteraction()
        renderer?.release()
        super.onDetachedFromWindow()
    }

    override fun close() {
        if (closed) return
        closed = true
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
        tilt?.setActive(false)
        tilt = null
        stopFrames()
        interaction.reset()
        onInteractionProgress?.invoke(0f)
        onInteractionOffset?.invoke(0f)
        onInteractionProgress = null
        onInteractionOffset = null
        onSelectionPositionChanged = null
        renderer?.release()
    }
}
