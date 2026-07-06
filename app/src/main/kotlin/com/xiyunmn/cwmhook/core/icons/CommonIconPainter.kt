package com.xiyunmn.cwmhook.core.icons

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

internal object CommonIconPainter {
    fun drawChapterExport(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        val path = Path()
        path.moveTo(cx - size * 0.46f, cy - size * 0.62f)
        path.lineTo(cx + size * 0.2f, cy - size * 0.62f)
        path.lineTo(cx + size * 0.52f, cy - size * 0.3f)
        path.lineTo(cx + size * 0.52f, cy + size * 0.62f)
        path.lineTo(cx - size * 0.46f, cy + size * 0.62f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.drawLine(cx + size * 0.2f, cy - size * 0.62f, cx + size * 0.2f, cy - size * 0.3f, paint)
        canvas.drawLine(cx + size * 0.2f, cy - size * 0.3f, cx + size * 0.52f, cy - size * 0.3f, paint)
        canvas.drawLine(cx, cy - size * 0.18f, cx, cy + size * 0.28f, paint)
        canvas.drawLine(cx - size * 0.2f, cy + size * 0.1f, cx, cy + size * 0.3f, paint)
        canvas.drawLine(cx + size * 0.2f, cy + size * 0.1f, cx, cy + size * 0.3f, paint)
    }

    fun drawPlayPause(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        paint.style = Paint.Style.FILL
        val play = Path().apply {
            moveTo(cx - size * 0.64f, cy - size * 0.56f)
            lineTo(cx + size * 0.12f, cy)
            lineTo(cx - size * 0.64f, cy + size * 0.56f)
            close()
        }
        canvas.drawPath(play, paint)
        rect.set(cx + size * 0.28f, cy - size * 0.55f, cx + size * 0.44f, cy + size * 0.55f)
        canvas.drawRoundRect(rect, size * 0.04f, size * 0.04f, paint)
        rect.set(cx + size * 0.6f, cy - size * 0.55f, cx + size * 0.76f, cy + size * 0.55f)
        canvas.drawRoundRect(rect, size * 0.04f, size * 0.04f, paint)
        paint.style = Paint.Style.STROKE
    }

    fun drawDelete(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawLine(cx - size * 0.45f, cy - size * 0.42f, cx + size * 0.45f, cy - size * 0.42f, paint)
        rect.set(cx - size * 0.34f, cy - size * 0.28f, cx + size * 0.34f, cy + size * 0.58f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
        canvas.drawLine(cx - size * 0.16f, cy - size * 0.58f, cx + size * 0.16f, cy - size * 0.58f, paint)
        canvas.drawLine(cx - size * 0.12f, cy - size * 0.06f, cx - size * 0.12f, cy + size * 0.36f, paint)
        canvas.drawLine(cx + size * 0.12f, cy - size * 0.06f, cx + size * 0.12f, cy + size * 0.36f, paint)
    }

    fun drawDrag(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        paint.style = Paint.Style.FILL
        val radius = size * 0.07f
        val xs = floatArrayOf(cx - size * 0.18f, cx + size * 0.18f)
        val ys = floatArrayOf(cy - size * 0.32f, cy, cy + size * 0.32f)
        ys.forEach { y ->
            xs.forEach { x ->
                canvas.drawCircle(x, y, radius, paint)
            }
        }
        paint.style = Paint.Style.STROKE
    }

    fun drawRadioSelected(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        canvas.drawCircle(cx, cy, size * 0.54f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, size * 0.25f, paint)
        paint.style = Paint.Style.STROKE
    }
}
