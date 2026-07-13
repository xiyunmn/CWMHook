package com.xiyunmn.cwmhook.core.runtime

import android.view.View
import android.view.ViewGroup

object ModuleOwnedUiCleaner {
    private const val TAG_PREFIX = "cwmhook_"

    fun clean(root: View) {
        if (root is ViewGroup) {
            for (index in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(index)
                val tag = child.tag as? String
                if (tag?.startsWith(TAG_PREFIX) == true) {
                    cleanListeners(child)
                    child.animate().cancel()
                    child.clearAnimation()
                    root.removeViewAt(index)
                } else {
                    clean(child)
                }
            }
        }
    }

    private fun cleanListeners(view: View) {
        view.animate().cancel()
        view.clearAnimation()
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
        view.setOnTouchListener(null)
        view.setOnKeyListener(null)
        view.setOnFocusChangeListener(null)
    }
}
