package com.xiyunmn.cwmhook.ui.panel

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

internal object PanelIconPainter {
    fun draw(
        canvas: Canvas,
        paint: Paint,
        rect: RectF,
        iconType: IconType,
        color: Int,
        strokeWidth: Float,
        cx: Float,
        cy: Float,
        size: Float,
    ) {
        paint.color = color
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        when (iconType) {
            IconType.TAB,
            IconType.MORE,
            -> PanelToolIconPainter.drawGrid(canvas, paint, rect, cx, cy, size)
            IconType.UI -> PanelToolIconPainter.drawMonitor(canvas, paint, rect, cx, cy, size)
            IconType.AD -> PanelToolIconPainter.drawShield(canvas, paint, cx, cy, size)
            IconType.DOWNLOAD -> PanelToolIconPainter.drawDownload(canvas, paint, rect, cx, cy, size)
            IconType.VISIBLE,
            IconType.ORDER,
            -> PanelToolIconPainter.drawSliders(canvas, paint, cx, cy, size)
            IconType.RESET -> PanelToolIconPainter.drawReset(canvas, paint, rect, cx, cy, size)
            IconType.DRAG -> PanelToolIconPainter.drawDrag(canvas, paint, cx, cy, size)
            IconType.EYE -> PanelToolIconPainter.drawEye(canvas, paint, cx, cy, size, false)
            IconType.EYE_OFF -> PanelToolIconPainter.drawEye(canvas, paint, cx, cy, size, true)
            IconType.HOME -> PanelNavigationIconPainter.drawHome(canvas, paint, rect, cx, cy, size)
            IconType.RANK -> PanelNavigationIconPainter.drawRank(canvas, paint, rect, cx, cy, size)
            IconType.BOOK -> PanelNavigationIconPainter.drawBook(canvas, paint, rect, cx, cy, size)
            IconType.COMPASS -> PanelNavigationIconPainter.drawCompass(canvas, paint, cx, cy, size)
            IconType.USER -> PanelNavigationIconPainter.drawUser(canvas, paint, rect, cx, cy, size)
            IconType.DELETE -> PanelToolIconPainter.drawDelete(canvas, paint, rect, cx, cy, size)
            IconType.POWER -> PanelToolIconPainter.drawPower(canvas, paint, rect, cx, cy, size)
            IconType.HELP -> PanelToolIconPainter.drawHelp(canvas, paint, rect, cx, cy, size)
        }
    }
}
