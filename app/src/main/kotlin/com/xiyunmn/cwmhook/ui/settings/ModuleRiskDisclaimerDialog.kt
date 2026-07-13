package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.edgeGlowBackground
import kotlin.math.min

internal class ModuleRiskDisclaimerDialog(
    private val activity: Activity,
    private val theme: PanelTheme,
) {
    fun show(onAccepted: () -> Unit) {
        var countdown = DISCLAIMER_COUNTDOWN_SECONDS
        val handler = Handler(Looper.getMainLooper())
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
        lateinit var confirmButton: TextView
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.rowBackground)
            isClickable = true
        }
        root.addView(
            TextView(activity).apply {
                text = DISCLAIMER_TITLE
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
                text = DISCLAIMER_MESSAGE
                textSize = 13f
                gravity = Gravity.START
                setTextColor(theme.subText)
                setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), dp(activity, 18))
                includeFontPadding = true
                setLineSpacing(dp(activity, 2).toFloat(), 1.0f)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        root.addView(separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        root.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    dialogButton("拒绝", theme.subText) {
                        dialog.dismiss()
                        Toast.makeText(activity, "已取消", Toast.LENGTH_SHORT).show()
                    },
                    LinearLayout.LayoutParams(0, dp(activity, 48), 1f),
                )
                addView(separator(), LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
                confirmButton = dialogButton(disclaimerCountdown(countdown), theme.disabledIcon) {
                    if (countdown <= 0) {
                        dialog.dismiss()
                        activity.window.decorView.post { onAccepted() }
                    }
                }.apply {
                    isEnabled = false
                }
                addView(confirmButton, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)),
        )
        dialog.setContentView(root)
        val countdownRunnable = object : Runnable {
            override fun run() {
                countdown -= 1
                if (countdown > 0) {
                    confirmButton.text = disclaimerCountdown(countdown)
                    handler.postDelayed(this, 1000L)
                } else {
                    confirmButton.text = "同意"
                    confirmButton.setTextColor(theme.accent)
                    confirmButton.isEnabled = true
                }
            }
        }
        dialog.setOnShowListener {
            handler.postDelayed(countdownRunnable, 1000L)
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(countdownRunnable)
        }
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(theme.rowBackground))
            window.setDimAmount(0.42f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val width = min(activity.resources.displayMetrics.widthPixels - dp(activity, 72), dp(activity, 320))
            window.setLayout(width.coerceAtLeast(dp(activity, 260)), ViewGroup.LayoutParams.WRAP_CONTENT)
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

    private companion object {
        const val DISCLAIMER_COUNTDOWN_SECONDS = 5
        const val DISCLAIMER_TITLE = "免责声明"
        const val DISCLAIMER_MESSAGE = """本模块仅供学习与技术研究使用。

使用本模块可能导致应用异常、功能不可用、账号风险或其它不可预期后果。

作者不对使用本模块造成的任何后果承担责任。

点击“同意”即表示您已阅读并接受以上声明。"""

        fun disclaimerCountdown(seconds: Int): String {
            return "同意 ($seconds)"
        }
    }
}
