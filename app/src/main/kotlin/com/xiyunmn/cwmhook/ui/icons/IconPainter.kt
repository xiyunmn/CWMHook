package com.xiyunmn.cwmhook.ui.icons

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

internal object IconPainter {
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
            -> ToolIconPainter.drawGrid(canvas, paint, rect, cx, cy, size)
            IconType.UI -> ToolIconPainter.drawMonitor(canvas, paint, rect, cx, cy, size)
            IconType.AD -> ToolIconPainter.drawShield(canvas, paint, cx, cy, size)
            IconType.DOWNLOAD -> ToolIconPainter.drawDownload(canvas, paint, rect, cx, cy, size)
            IconType.STATUS_BAR -> ToolIconPainter.drawStatusBar(canvas, paint, rect, cx, cy, size)
            IconType.BOTTOM_TAB -> ToolIconPainter.drawBottomTab(canvas, paint, rect, cx, cy, size)
            IconType.CHAPTER_EXPORT -> ToolIconPainter.drawChapterExport(canvas, paint, rect, cx, cy, size)
            IconType.FONT -> ToolIconPainter.drawType(canvas, paint, rect, cx, cy, size)
            IconType.FONT_IMPORT -> ToolIconPainter.drawFontImport(canvas, paint, rect, cx, cy, size)
            IconType.FONT_MANAGE -> ToolIconPainter.drawFontManage(canvas, paint, rect, cx, cy, size)
            IconType.AUTO_SIGN_IN -> ToolIconPainter.drawCalendarCheck(canvas, paint, rect, cx, cy, size)
            IconType.STARTUP_TAB -> ToolIconPainter.drawRoute(canvas, paint, rect, cx, cy, size)
            IconType.NETWORK -> ToolIconPainter.drawNetwork(canvas, paint, cx, cy, size)
            IconType.TIMER -> ToolIconPainter.drawTimer(canvas, paint, rect, cx, cy, size)
            IconType.PLAY -> ToolIconPainter.drawPlay(canvas, paint, cx, cy, size)
            IconType.PLAY_PAUSE -> ToolIconPainter.drawPlayPause(canvas, paint, rect, cx, cy, size)
            IconType.CHECK -> ToolIconPainter.drawCheck(canvas, paint, cx, cy, size)
            IconType.RADIO_SELECTED -> ToolIconPainter.drawRadioSelected(canvas, paint, cx, cy, size)
            IconType.CHEVRON_RIGHT -> ToolIconPainter.drawChevronRight(canvas, paint, cx, cy, size)
            IconType.FOLDER_OPEN -> ToolIconPainter.drawFolderOpen(canvas, paint, rect, cx, cy, size)
            IconType.VISIBLE,
            IconType.ORDER,
            -> ToolIconPainter.drawSliders(canvas, paint, cx, cy, size)
            IconType.RESET -> ToolIconPainter.drawReset(canvas, paint, rect, cx, cy, size)
            IconType.DRAG -> ToolIconPainter.drawDrag(canvas, paint, cx, cy, size)
            IconType.EYE -> ToolIconPainter.drawEye(canvas, paint, cx, cy, size, false)
            IconType.EYE_OFF -> ToolIconPainter.drawEye(canvas, paint, cx, cy, size, true)
            IconType.HOME -> NavigationIconPainter.drawHome(canvas, paint, rect, cx, cy, size)
            IconType.RANK -> NavigationIconPainter.drawRank(canvas, paint, rect, cx, cy, size)
            IconType.BOOK -> NavigationIconPainter.drawBook(canvas, paint, rect, cx, cy, size)
            IconType.COMPASS -> NavigationIconPainter.drawCompass(canvas, paint, cx, cy, size)
            IconType.USER -> NavigationIconPainter.drawUser(canvas, paint, rect, cx, cy, size)
            IconType.DELETE -> ToolIconPainter.drawDelete(canvas, paint, rect, cx, cy, size)
            IconType.POWER -> ToolIconPainter.drawPower(canvas, paint, rect, cx, cy, size)
            IconType.HELP -> ToolIconPainter.drawHelp(canvas, paint, rect, cx, cy, size)
        }
    }
}
