package com.xiyunmn.cwmhook.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

fun dp(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

fun roundRect(color: Int, radius: Float, strokeColor: Int? = null): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeColor != null) {
            setStroke(1, strokeColor)
        }
    }
}

fun blendColor(foreground: Int, background: Int, amount: Float): Int {
    val alpha = amount.coerceIn(0f, 1f)
    return Color.rgb(
        (Color.red(foreground) * alpha + Color.red(background) * (1f - alpha)).toInt(),
        (Color.green(foreground) * alpha + Color.green(background) * (1f - alpha)).toInt(),
        (Color.blue(foreground) * alpha + Color.blue(background) * (1f - alpha)).toInt(),
    )
}

fun edgeGlowBackground(context: Context, normalColor: Int, accentColor: Int): Drawable {
    val normal = GradientDrawable().apply {
        setColor(normalColor)
    }
    val active = GradientDrawable().apply {
        setColor(normalColor)
        setStroke(
            dp(context, 1).coerceAtLeast(1),
            blendColor(accentColor, normalColor, 0.32f),
        )
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), active)
        addState(intArrayOf(android.R.attr.state_focused), active)
        addState(intArrayOf(android.R.attr.state_selected), active)
        addState(intArrayOf(), normal)
    }
}

fun animatePress(view: View, action: () -> Unit) {
    view.animate().cancel()
    view.animate()
        .scaleX(0.94f)
        .scaleY(0.94f)
        .alpha(0.86f)
        .setDuration(70L)
        .withEndAction {
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(90L)
                .withEndAction { action() }
                .start()
        }
        .start()
}
