package com.xiyunmn.cwmhook.ui.icons

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
    STATUS_BAR,
    BOTTOM_TAB,
    CHAPTER_EXPORT,
    FONT,
    FONT_IMPORT,
    FONT_MANAGE,
    AUTO_SIGN_IN,
    STARTUP_TAB,
    NETWORK,
    TIMER,
    PLAY,
    PLAY_PAUSE,
    CHECK,
    RADIO_SELECTED,
    CHEVRON_RIGHT,
    FOLDER_OPEN,
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

class InlineIconView(
    context: Context,
    private val iconType: IconType,
    private val color: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        IconPainter.draw(
            canvas = canvas,
            paint = paint,
            rect = rect,
            iconType = iconType,
            color = color,
            strokeWidth = iconDp(context, 2).toFloat(),
            cx = width / 2f,
            cy = height / 2f,
            size = min(width, height) * 0.48f,
        )
    }
}

private fun iconDp(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
