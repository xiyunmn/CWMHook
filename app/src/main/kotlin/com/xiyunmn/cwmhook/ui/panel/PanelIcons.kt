package com.xiyunmn.cwmhook.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

enum class IconType {
    TAB,
    UI,
    AD,
    DOWNLOAD,
    MORE,
    VISIBLE,
    ORDER,
    RESET,
    DRAG,
    EYE,
    EYE_OFF,
    HOME,
    RANK,
    BOOK,
    COMPASS,
    USER,
    DELETE,
    POWER,
    HELP,
}

class TileIconView(
    context: Context,
    private val iconType: IconType,
    private val accent: Int,
    private val backgroundColor: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = iconDp(context, 12).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = backgroundColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        PanelIconPainter.draw(
            canvas = canvas,
            paint = paint,
            rect = rect,
            iconType = iconType,
            color = accent,
            strokeWidth = iconDp(context, 2).toFloat(),
            cx = width / 2f,
            cy = height / 2f,
            size = min(width, height) * 0.42f,
        )
    }
}

class InlineIconView(
    context: Context,
    private val iconType: IconType,
    private val color: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        PanelIconPainter.draw(
            canvas = canvas,
            paint = paint,
            rect = rect,
            iconType = iconType,
            color = color,
            strokeWidth = iconDp(context, 2).toFloat(),
            cx = width / 2f,
            cy = height / 2f,
            size = min(width, height) * 0.42f,
        )
    }
}

private fun iconDp(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
