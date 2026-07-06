package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.edgeGlowBackground
import kotlin.math.min

internal class ModuleSettingsConfirmDialog(
    private val activity: Activity,
    private val theme: PanelTheme,
) {
    fun show(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit,
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.rowBackground)
            isClickable = true
        }
        root.addView(
            TextView(activity).apply {
                text = title
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(theme.text)
                setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 8))
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            TextView(activity).apply {
                text = message
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(theme.subText)
                setPadding(dp(activity, 24), dp(activity, 6), dp(activity, 24), dp(activity, 18))
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        root.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    dialogButton("取消", theme.subText) { dialog.dismiss() },
                    LinearLayout.LayoutParams(0, dp(activity, 48), 1f),
                )
                addView(separator(), LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(
                    dialogButton(confirmText, theme.accent) {
                        onConfirm()
                        dialog.dismiss()
                    },
                    LinearLayout.LayoutParams(0, dp(activity, 48), 1f),
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)),
        )
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(theme.rowBackground))
            window.setDimAmount(0.42f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val width = min(activity.resources.displayMetrics.widthPixels - dp(activity, 72), dp(activity, 300))
            window.setLayout(width.coerceAtLeast(dp(activity, 240)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun dialogButton(text: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(color)
            background = edgeGlowBackground(activity, theme.rowBackground, theme.accent)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun separator(): View {
        return View(activity).apply {
            setBackgroundColor(theme.separator)
        }
    }
}
