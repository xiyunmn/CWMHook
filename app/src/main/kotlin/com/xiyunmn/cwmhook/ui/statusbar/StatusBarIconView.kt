package com.xiyunmn.cwmhook.ui.statusbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.xiyunmn.cwmhook.ui.common.dp
import kotlin.math.min

/**
 * 状态栏图标视图
 *
 * 绘制状态栏/窗口类图标
 */
internal class StatusBarIconView(
    context: Context,
    private val accent: Int,
    private val backgroundColor: Int,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制圆角背景
        val radius = dp(context, 12).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = backgroundColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)

        // 绘制状态栏图标
        drawStatusBarIcon(canvas, width / 2f, height / 2f, min(width, height) * 0.42f)
    }

    private fun drawStatusBarIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        paint.color = accent
        paint.strokeWidth = dp(context, 2).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE

        // 绘制一个矩形框表示窗口
        rect.set(cx - size * 0.65f, cy - size * 0.5f, cx + size * 0.65f, cy + size * 0.5f)
        canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, paint)

        // 顶部状态栏区域
        canvas.drawLine(cx - size * 0.65f, cy - size * 0.2f, cx + size * 0.65f, cy - size * 0.2f, paint)
    }
}
