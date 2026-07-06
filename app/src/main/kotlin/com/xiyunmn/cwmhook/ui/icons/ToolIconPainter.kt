package com.xiyunmn.cwmhook.ui.icons

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.xiyunmn.cwmhook.core.icons.CommonIconPainter

internal object ToolIconPainter {
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

    fun drawStatusBar(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.68f, cy - size * 0.5f, cx + size * 0.68f, cy + size * 0.5f)
        canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, paint)
        canvas.drawLine(cx - size * 0.68f, cy - size * 0.2f, cx + size * 0.68f, cy - size * 0.2f, paint)
        canvas.drawCircle(cx - size * 0.42f, cy - size * 0.35f, size * 0.03f, paint)
        canvas.drawCircle(cx - size * 0.25f, cy - size * 0.35f, size * 0.03f, paint)
    }

    fun drawBottomTab(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.68f, cy - size * 0.5f, cx + size * 0.68f, cy + size * 0.5f)
        canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, paint)
        canvas.drawLine(cx - size * 0.68f, cy + size * 0.2f, cx + size * 0.68f, cy + size * 0.2f, paint)
        val y = cy + size * 0.35f
        listOf(cx - size * 0.34f, cx, cx + size * 0.34f).forEach { x ->
            canvas.drawCircle(x, y, size * 0.07f, paint)
        }
    }

    fun drawChapterExport(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        CommonIconPainter.drawChapterExport(canvas, paint, rect, cx, cy, size)
    }

    fun drawType(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx - size * 0.55f, cy - size * 0.52f, cx + size * 0.55f, cy - size * 0.52f, paint)
        canvas.drawLine(cx, cy - size * 0.5f, cx, cy + size * 0.54f, paint)
        canvas.drawLine(cx - size * 0.24f, cy + size * 0.54f, cx + size * 0.24f, cy + size * 0.54f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.64f
        canvas.drawText("A", cx + size * 0.38f, cy + size * 0.46f, paint)
        paint.style = Paint.Style.STROKE
    }

    fun drawFontImport(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        drawFontFileBase(canvas, paint, rect, cx, cy, size)
        canvas.drawLine(cx + size * 0.22f, cy + size * 0.38f, cx + size * 0.22f, cy - size * 0.12f, paint)
        canvas.drawLine(cx + size * 0.02f, cy + size * 0.08f, cx + size * 0.22f, cy - size * 0.14f, paint)
        canvas.drawLine(cx + size * 0.42f, cy + size * 0.08f, cx + size * 0.22f, cy - size * 0.14f, paint)
        canvas.drawLine(cx + size * 0.02f, cy + size * 0.48f, cx + size * 0.42f, cy + size * 0.48f, paint)
    }

    fun drawFontManage(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        drawFontFileBase(canvas, paint, rect, cx, cy, size)
        val gearCx = cx + size * 0.24f
        val gearCy = cy + size * 0.26f
        val outer = size * 0.24f
        val inner = size * 0.09f
        canvas.drawCircle(gearCx, gearCy, inner, paint)
        canvas.drawCircle(gearCx, gearCy, outer * 0.62f, paint)
        for (index in 0 until 6) {
            val angle = Math.toRadians((index * 60).toDouble())
            val sx = gearCx + kotlin.math.cos(angle).toFloat() * outer * 0.72f
            val sy = gearCy + kotlin.math.sin(angle).toFloat() * outer * 0.72f
            val ex = gearCx + kotlin.math.cos(angle).toFloat() * outer
            val ey = gearCy + kotlin.math.sin(angle).toFloat() * outer
            canvas.drawLine(sx, sy, ex, ey, paint)
        }
    }

    private fun drawFontFileBase(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        val path = Path()
        path.moveTo(cx - size * 0.52f, cy - size * 0.64f)
        path.lineTo(cx + size * 0.18f, cy - size * 0.64f)
        path.lineTo(cx + size * 0.48f, cy - size * 0.34f)
        path.lineTo(cx + size * 0.48f, cy + size * 0.64f)
        path.lineTo(cx - size * 0.52f, cy + size * 0.64f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawLine(cx + size * 0.18f, cy - size * 0.64f, cx + size * 0.18f, cy - size * 0.34f, paint)
        canvas.drawLine(cx + size * 0.18f, cy - size * 0.34f, cx + size * 0.48f, cy - size * 0.34f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = size * 0.62f
        canvas.drawText("A", cx - size * 0.16f, cy + size * 0.14f, paint)
        paint.style = Paint.Style.STROKE
    }

    fun drawCalendarCheck(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.62f, cy - size * 0.5f, cx + size * 0.62f, cy + size * 0.58f)
        canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, paint)
        canvas.drawLine(cx - size * 0.62f, cy - size * 0.18f, cx + size * 0.62f, cy - size * 0.18f, paint)
        canvas.drawLine(cx - size * 0.34f, cy - size * 0.66f, cx - size * 0.34f, cy - size * 0.38f, paint)
        canvas.drawLine(cx + size * 0.34f, cy - size * 0.66f, cx + size * 0.34f, cy - size * 0.38f, paint)
        drawCheck(canvas, paint, cx + size * 0.04f, cy + size * 0.18f, size * 0.58f)
    }

    fun drawRoute(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawCircle(cx - size * 0.42f, cy - size * 0.32f, size * 0.16f, paint)
        canvas.drawCircle(cx + size * 0.42f, cy + size * 0.34f, size * 0.16f, paint)
        rect.set(cx - size * 0.35f, cy - size * 0.22f, cx + size * 0.35f, cy + size * 0.26f)
        canvas.drawArc(rect, -170f, 250f, false, paint)
    }

    fun drawNetwork(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        val leftX = cx - size * 0.48f
        val rightX = cx + size * 0.48f
        val topY = cy - size * 0.32f
        val bottomY = cy + size * 0.34f
        canvas.drawLine(leftX, topY, cx, cy + size * 0.02f, paint)
        canvas.drawLine(rightX, topY, cx, cy + size * 0.02f, paint)
        canvas.drawLine(cx, cy + size * 0.02f, cx, bottomY, paint)
        canvas.drawCircle(leftX, topY, size * 0.14f, paint)
        canvas.drawCircle(rightX, topY, size * 0.14f, paint)
        canvas.drawCircle(cx, cy + size * 0.02f, size * 0.14f, paint)
        canvas.drawCircle(cx, bottomY, size * 0.14f, paint)
    }

    fun drawTimer(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawCircle(cx, cy + size * 0.08f, size * 0.54f, paint)
        canvas.drawLine(cx - size * 0.18f, cy - size * 0.58f, cx + size * 0.18f, cy - size * 0.58f, paint)
        canvas.drawLine(cx, cy - size * 0.58f, cx, cy - size * 0.44f, paint)
        canvas.drawLine(cx + size * 0.34f, cy - size * 0.38f, cx + size * 0.48f, cy - size * 0.52f, paint)
        canvas.drawLine(cx, cy + size * 0.08f, cx, cy - size * 0.2f, paint)
        canvas.drawLine(cx, cy + size * 0.08f, cx + size * 0.26f, cy + size * 0.28f, paint)
        rect.set(cx - size * 0.32f, cy - size * 0.24f, cx + size * 0.32f, cy + size * 0.4f)
        canvas.drawArc(rect, 205f, 105f, false, paint)
    }

    fun drawPlay(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        canvas.drawCircle(cx, cy, size * 0.62f, paint)
        val path = Path()
        path.moveTo(cx - size * 0.16f, cy - size * 0.28f)
        path.lineTo(cx + size * 0.32f, cy)
        path.lineTo(cx - size * 0.16f, cy + size * 0.28f)
        path.close()
        canvas.drawPath(path, paint)
    }

    fun drawPlayPause(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        CommonIconPainter.drawPlayPause(canvas, paint, rect, cx, cy, size)
    }

    fun drawCheck(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx - size * 0.42f, cy - size * 0.02f, cx - size * 0.12f, cy + size * 0.28f, paint)
        canvas.drawLine(cx - size * 0.12f, cy + size * 0.28f, cx + size * 0.46f, cy - size * 0.34f, paint)
    }

    fun drawRadioSelected(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        CommonIconPainter.drawRadioSelected(canvas, paint, cx, cy, size)
    }

    fun drawChevronRight(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx - size * 0.18f, cy - size * 0.34f, cx + size * 0.18f, cy, paint)
        canvas.drawLine(cx + size * 0.18f, cy, cx - size * 0.18f, cy + size * 0.34f, paint)
    }

    fun drawFolderOpen(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        val path = Path()
        path.moveTo(cx - size * 0.68f, cy - size * 0.32f)
        path.lineTo(cx - size * 0.18f, cy - size * 0.32f)
        path.lineTo(cx + size * 0.02f, cy - size * 0.12f)
        path.lineTo(cx + size * 0.66f, cy - size * 0.12f)
        path.lineTo(cx + size * 0.52f, cy + size * 0.58f)
        path.lineTo(cx - size * 0.62f, cy + size * 0.58f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawLine(cx - size * 0.62f, cy + size * 0.58f, cx - size * 0.46f, cy, paint)
        canvas.drawLine(cx - size * 0.46f, cy, cx + size * 0.7f, cy, paint)
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
        CommonIconPainter.drawDrag(canvas, paint, cx, cy, size)
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
        CommonIconPainter.drawDelete(canvas, paint, rect, cx, cy, size)
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
