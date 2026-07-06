package com.xiyunmn.cwmhook.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.blendColor
import kotlin.math.min

internal class ModuleSettingsToggleIconView(
    context: Context,
    private val theme: PanelTheme,
    private val checked: Boolean,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val switchWidth = width * 0.74f
        val switchHeight = min(height * 0.4f, switchWidth * 0.52f)
        val left = (width - switchWidth) / 2f
        val top = (height - switchHeight) / 2f
        val right = left + switchWidth
        val bottom = top + switchHeight
        val radius = switchHeight / 2f
        val fill = if (checked) {
            blendColor(theme.accent, theme.rowBackground, 0.18f)
        } else {
            blendColor(theme.subText, theme.rowBackground, 0.12f)
        }
        val stroke = if (checked) theme.accent else theme.disabledIcon
        rect.set(left, top, right, bottom)
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, switchHeight * 0.08f)
        paint.color = stroke
        canvas.drawRoundRect(rect, radius, radius, paint)

        val knobRadius = switchHeight * 0.34f
        val knobCx = if (checked) right - radius else left + radius
        paint.style = Paint.Style.FILL
        paint.color = if (checked) theme.accent else theme.disabledIcon
        canvas.drawCircle(knobCx, (top + bottom) / 2f, knobRadius, paint)
    }
}
