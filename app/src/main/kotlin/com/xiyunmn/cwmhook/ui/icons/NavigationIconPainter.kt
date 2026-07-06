package com.xiyunmn.cwmhook.ui.icons

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

internal object NavigationIconPainter {
    fun drawHome(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        val path = Path()
        path.moveTo(cx - size * 0.62f, cy - size * 0.05f)
        path.lineTo(cx, cy - size * 0.62f)
        path.lineTo(cx + size * 0.62f, cy - size * 0.05f)
        path.lineTo(cx + size * 0.5f, cy + size * 0.62f)
        path.lineTo(cx - size * 0.5f, cy + size * 0.62f)
        path.close()
        canvas.drawPath(path, paint)
        rect.set(cx - size * 0.16f, cy + size * 0.18f, cx + size * 0.16f, cy + size * 0.62f)
        canvas.drawRect(rect, paint)
    }

    fun drawRank(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        paint.style = Paint.Style.FILL
        val barWidth = size * 0.24f
        rect.set(cx - size * 0.5f, cy + size * 0.05f, cx - size * 0.5f + barWidth, cy + size * 0.6f)
        canvas.drawRoundRect(rect, barWidth * 0.25f, barWidth * 0.25f, paint)
        rect.set(cx - barWidth / 2, cy - size * 0.45f, cx + barWidth / 2, cy + size * 0.6f)
        canvas.drawRoundRect(rect, barWidth * 0.25f, barWidth * 0.25f, paint)
        rect.set(cx + size * 0.5f - barWidth, cy - size * 0.1f, cx + size * 0.5f, cy + size * 0.6f)
        canvas.drawRoundRect(rect, barWidth * 0.25f, barWidth * 0.25f, paint)
        paint.style = Paint.Style.STROKE
    }

    fun drawBook(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        rect.set(cx - size * 0.68f, cy - size * 0.5f, cx - size * 0.04f, cy + size * 0.48f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
        rect.set(cx + size * 0.04f, cy - size * 0.5f, cx + size * 0.68f, cy + size * 0.48f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)
        canvas.drawLine(cx, cy - size * 0.42f, cx, cy + size * 0.5f, paint)
    }

    fun drawCompass(canvas: Canvas, paint: Paint, cx: Float, cy: Float, size: Float) {
        canvas.drawCircle(cx, cy, size * 0.58f, paint)
        val path = Path()
        path.moveTo(cx + size * 0.3f, cy - size * 0.34f)
        path.lineTo(cx + size * 0.02f, cy + size * 0.34f)
        path.lineTo(cx - size * 0.3f, cy + size * 0.1f)
        path.close()
        canvas.drawPath(path, paint)
    }

    fun drawUser(canvas: Canvas, paint: Paint, rect: RectF, cx: Float, cy: Float, size: Float) {
        canvas.drawCircle(cx, cy - size * 0.28f, size * 0.25f, paint)
        rect.set(cx - size * 0.5f, cy + size * 0.05f, cx + size * 0.5f, cy + size * 0.62f)
        canvas.drawRoundRect(rect, size * 0.22f, size * 0.22f, paint)
    }
}
