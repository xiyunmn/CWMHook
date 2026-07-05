package com.xiyunmn.cwmhook.ui.bottomtab

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.xiyunmn.cwmhook.config.bottomtab.BottomTabConfig
import com.xiyunmn.cwmhook.ui.common.dp
import com.xiyunmn.cwmhook.ui.common.roundRect
import com.xiyunmn.cwmhook.ui.common.PanelTheme

class BottomTabEditorPanel(
    private val activity: Activity,
    private val theme: PanelTheme,
    initialConfig: BottomTabConfig,
    private val onSave: (BottomTabConfig) -> Unit,
) {
    private val state = BottomTabPanelState.from(initialConfig)
    private val layouts = BottomTabEditorLayouts(activity, theme)
    private val resetDialog = BottomTabResetDialog(activity, theme)
    private val rowFactory = BottomTabRowFactory(activity, theme, state, layouts)
    private val chromeFactory = BottomTabEditorChromeFactory(activity, theme, state, layouts)
    private val actionFactory = BottomTabEditorActionFactory(activity, theme, state, layouts, resetDialog, onSave)

    fun create(): LinearLayout {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            elevation = dp(activity, 10).toFloat()
            background = roundRect(theme.panelBackground, dp(activity, 10).toFloat())
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 14))
        }
        val scrollView = ScrollView(activity).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun renderBody() {
            body.removeAllViews()
            body.addView(chromeFactory.createTitle(::renderBody), layouts.matchWrap())
            if (state.expanded) {
                body.addView(
                    TextView(activity).apply {
                        text = "拖动左侧图标可调整顺序，点击右侧眼睛可显示或隐藏"
                        textSize = 12f
                        setTextColor(theme.subText)
                        setPadding(0, dp(activity, 12), 0, dp(activity, 10))
                    },
                    layouts.matchWrap(),
                )
                body.addView(rowFactory.createRows(::renderBody), layouts.matchWrap())
                body.addView(chromeFactory.createMoreOptionsTitle(), layouts.matchWrap())
                body.addView(actionFactory.createResetRow(::renderBody), layouts.matchWrap())
                body.addView(chromeFactory.createHintCard(), layouts.hintParams())
            }
        }

        panel.addView(chromeFactory.createHeader(panel), layouts.matchWidthHeight(dp(activity, 58)))
        panel.addView(layouts.separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        panel.addView(scrollView, layouts.scrollBodyParams())
        panel.addView(layouts.separator(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        panel.addView(actionFactory.createBottomActions(::renderBody), layouts.matchWidthHeight(dp(activity, 54)))
        renderBody()
        return panel
    }

    /**
     * 创建内容区域（不包含标题和底部按钮）
     * 用于集成到多 section 面板
     */
    fun createContent(expanded: Boolean, onExpandedChanged: (Boolean) -> Unit): View {
        state.expanded = expanded

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun renderContent() {
            container.removeAllViews()
            container.addView(chromeFactory.createTitle {
                onExpandedChanged(state.expanded)
            }, layouts.matchWrap())

            if (state.expanded) {
                container.addView(
                    TextView(activity).apply {
                        text = "拖动左侧图标可调整顺序，点击右侧眼睛可显示或隐藏"
                        textSize = 12f
                        setTextColor(theme.subText)
                        setPadding(0, dp(activity, 12), 0, dp(activity, 10))
                    },
                    layouts.matchWrap(),
                )
                container.addView(rowFactory.createRows(::renderContent), layouts.matchWrap())
                container.addView(chromeFactory.createMoreOptionsTitle(), layouts.matchWrap())
                container.addView(actionFactory.createResetRow(::renderContent), layouts.matchWrap())
                container.addView(chromeFactory.createHintCard(), layouts.hintParams())
            }
        }

        renderContent()
        return container
    }

    /**
     * 创建底部保存按钮
     * 用于集成到多 section 面板
     */
    fun createFooter(
        onResetClick: () -> Unit,
        onSaveClick: () -> Unit,
    ): View {
        return actionFactory.createBottomActions(
            render = onResetClick,
            afterSave = onSaveClick,
        )
    }

    fun createFooter(onSaveClick: () -> Unit): View {
        return createFooter(onResetClick = {}, onSaveClick = onSaveClick)
    }
}
