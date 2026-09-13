package com.xiyunmn.cwmhook.ui.settings

import android.os.Build
import com.xiyunmn.cwmhook.config.glassbar.GlassBarsConfig
import com.xiyunmn.cwmhook.ui.icons.IconType

internal class ModuleSettingsGlassBarRows(
    private val rows: ModuleSettingsRows,
    private val config: GlassBarsConfig,
    private val changed: (GlassBarsConfig) -> Unit,
) {
    fun render() {
        rows.addSectionTitle("显示位置")
        toggle("主界面底栏 Tab", "保留 Tab 顺序、图标与点击行为", config.mainEnabled) {
            config.copy(mainEnabled = !config.mainEnabled)
        }
        toggle("书籍详情页", "订阅、阅读和加入书架使用悬浮底栏", config.detailEnabled) {
            config.copy(detailEnabled = !config.detailEnabled)
        }
        toggle("目录页", "独立目录与阅读页目录面板，兼容章节导出", config.catalogEnabled) {
            config.copy(catalogEnabled = !config.catalogEnabled)
        }
        rows.addSectionTitle("玻璃材质")
        toggle("背景模糊", "柔化底栏下方的页面内容", config.blur) { config.copy(blur = !config.blur) }
        toggle("边缘折射", "圆角边缘呈现透镜形变", config.refraction) { config.copy(refraction = !config.refraction) }
        toggle("边缘高光", "光线沿玻璃轮廓流动", config.highlight) { config.copy(highlight = !config.highlight) }
        toggle("悬浮阴影", "增强底栏与页面之间的层次", config.shadow) { config.copy(shadow = !config.shadow) }
        rows.addSectionTitle("交互")
        toggle("高光随设备倾斜", "轻微倾斜设备时改变光照方向", config.tilt) { config.copy(tilt = !config.tilt) }
        toggle("按压与切换动画", "按压回弹，Tab 选中胶囊平滑移动", config.press) { config.copy(press = !config.press) }
        if (Build.VERSION.SDK_INT < 33) {
            rows.addInfoRow("兼容显示", "当前系统使用半透明悬浮底栏，Android 13 及以上支持完整玻璃效果。")
        }
    }

    private fun toggle(title: String, subtitle: String, enabled: Boolean, update: () -> GlassBarsConfig) {
        rows.addOverviewRow(
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            onToggle = { changed(update()) },
            onOpen = null,
            icon = IconType.UI,
        )
    }
}
