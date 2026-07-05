package com.xiyunmn.cwmhook.ui.panel

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

internal object PanelToolIconPainter {
    fun drawGrid(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        val gap = size * 0.2f
        val box = size * 0.36f
        for (row in 0..1) {
            for (col in 0..1) {
                val left = cx - gap / 2 - box + col * (box + gap)
                val top = cy - gap / 2 - box + row * (box + gap)
                rect.set(left, top, left + box, top + box)
                canvas.drawRoundRect(rect, box * 0.22f, box * 0.22f, paint)
            }
        }
    }

    fun drawMonitor(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.65f, cy - size * 0.45f, cx + size * 0.65f, cy + size * 0.28f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
        canvas.drawLine(cx, cy + size * 0.28f, cx, cy + size * 0.52f, paint)
        canvas.drawLine(cx - size * 0.34f, cy + size * 0.52f, cx + size * 0.34f, cy + size * 0.52f, paint)
    }

    fun drawShield(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        val path = Path()
        path.moveTo(cx, cy - size * 0.7f)
        path.lineTo(cx + size * 0.55f, cy - size * 0.42f)
        path.lineTo(cx + size * 0.45f, cy + size * 0.28f)
        path.quadTo(cx, cy + size * 0.68f, cx - size * 0.45f, cy + size * 0.28f)
        path.lineTo(cx - size * 0.55f, cy - size * 0.42f)
        path.close()
        canvas.drawPath(path, paint)
    }

    fun drawDownload(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx, cy - size * 0.62f, cx, cy + size * 0.12f, paint)
        canvas.drawLine(cx - size * 0.3f, cy - size * 0.15f, cx, cy + size * 0.15f, paint)
        canvas.drawLine(cx + size * 0.3f, cy - size * 0.15f, cx, cy + size * 0.15f, paint)
        rect.set(cx - size * 0.6f, cy + size * 0.18f, cx + size * 0.6f, cy + size * 0.62f)
        canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, paint)
    }

    fun drawSliders(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        val left = cx - size * 0.62f
        val right = cx + size * 0.62f
        val ys = floatArrayOf(cy - size * 0.42f, cy, cy + size * 0.42f)
        ys.forEachIndexed { index, y ->
            canvas.drawLine(left, y, right, y, paint)
            val knob = when (index) {
                0 -> cx + size * 0.24f
                1 -> cx - size * 0.2f
                else -> cx + size * 0.08f
            }
            canvas.drawCircle(knob, y, size * 0.09f, paint)
        }
    }

    fun drawReset(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.5f, cy - size * 0.5f, cx + size * 0.5f, cy + size * 0.5f)
        canvas.drawArc(rect, -210f, 280f, false, paint)
        canvas.drawLine(cx - size * 0.55f, cy - size * 0.12f, cx - size * 0.75f, cy - size * 0.14f, paint)
        canvas.drawLine(cx - size * 0.55f, cy - size * 0.12f, cx - size * 0.44f, cy - size * 0.32f, paint)
    }

    fun drawDrag(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        val left = cx - size * 0.48f
        val right = cx + size * 0.48f
        canvas.drawLine(left, cy - size * 0.32f, right, cy - size * 0.32f, paint)
        canvas.drawLine(left, cy, right, cy, paint)
        canvas.drawLine(left, cy + size * 0.32f, right, cy + size * 0.32f, paint)
    }

    fun drawEye(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float, off: Boolean) {
        val path = Path()
        path.moveTo(cx - size * 0.72f, cy)
        path.quadTo(cx, cy - size * 0.55f, cx + size * 0.72f, cy)
        path.quadTo(cx, cy + size * 0.55f, cx - size * 0.72f, cy)
        canvas.drawPath(path, paint)
        canvas.drawCircle(cx, cy, size * 0.18f, paint)
        if (off) {
            canvas.drawLine(cx - size * 0.72f, cy - size * 0.62f, cx + size * 0.72f, cy + size * 0.62f, paint)
        }
    }

    fun drawDelete(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx - size * 0.45f, cy - size * 0.42f, cx + size * 0.45f, cy - size * 0.42f, paint)
        rect.set(cx - size * 0.34f, cy - size * 0.28f, cx + size * 0.34f, cy + size * 0.58f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
        canvas.drawLine(cx - size * 0.16f, cy - size * 0.58f, cx + size * 0.16f, cy - size * 0.58f, paint)
        canvas.drawLine(cx - size * 0.12f, cy - size * 0.06f, cx - size * 0.12f, cy + size * 0.36f, paint)
        canvas.drawLine(cx + size * 0.12f, cy - size * 0.06f, cx + size * 0.12f, cy + size * 0.36f, paint)
    }

    fun drawPower(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx, cy - size * 0.65f, cx, cy - size * 0.08f, paint)
        rect.set(cx - size * 0.55f, cy - size * 0.38f, cx + size * 0.55f, cy + size * 0.72f)
        canvas.drawArc(rect, -135f, 270f, false, paint)
    }

    fun drawHelp(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.48f, cy - size * 0.52f, cx + size * 0.48f, cy + size * 0.52f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.78f
        canvas.drawText("?", cx, cy + size * 0.25f, paint)
    }
}
