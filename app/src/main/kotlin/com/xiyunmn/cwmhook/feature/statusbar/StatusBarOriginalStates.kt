package com.xiyunmn.cwmhook.feature.statusbar

import android.view.View

internal data class PaddingState(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    var appliedTop: Int,
) {
    fun wasChangedOutside(view: View): Boolean {
        return view.paddingLeft != left ||
            view.paddingRight != right ||
            view.paddingBottom != bottom ||
            view.paddingTop != appliedTop
    }

    companion object {
        fun from(view: View): PaddingState {
            return PaddingState(
                left = view.paddingLeft,
                top = view.paddingTop,
                right = view.paddingRight,
                bottom = view.paddingBottom,
                appliedTop = view.paddingTop,
            )
        }
    }
}
